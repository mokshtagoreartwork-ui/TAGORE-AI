package com.example.core.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.model.LanguageMode
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline-capable Indian-accent TextToSpeech & Streaming Audio Engine.
 * Normalizes Hinglish/English/Hindi text before synthesis so acronyms, numbers,
 * temperatures, and mixed-script words are pronounced accurately and naturally.
 */
class TagoreVoiceEngine(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val activeUtterances = ConcurrentHashMap.newKeySet<String>()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsAvailable = MutableStateFlow(false)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable.asStateFlow()

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    _ttsAvailable.value = true
                    tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId != null) activeUtterances.add(utteranceId)
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null) activeUtterances.remove(utteranceId)
                            _isSpeaking.value = activeUtterances.isNotEmpty()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId != null) activeUtterances.remove(utteranceId)
                            _isSpeaking.value = activeUtterances.isNotEmpty()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId != null) activeUtterances.remove(utteranceId)
                            _isSpeaking.value = activeUtterances.isNotEmpty()
                        }
                    })
                } else {
                    Log.w("TagoreVoiceEngine", "TTS initialization status: $status")
                }
            }
        } catch (e: Exception) {
            Log.w("TagoreVoiceEngine", "TTS unavailable in current environment", e)
        }
    }

    fun speak(text: String, prefs: UserPreferences) {
        speakInternal(text, prefs, flushQueue = true)
    }

    /**
     * Queues a streamed sentence for instant low-latency voice output without cutting off prior chunks.
     */
    fun speakQueued(text: String, prefs: UserPreferences, flushFirst: Boolean) {
        speakInternal(text, prefs, flushQueue = flushFirst)
    }

    private fun speakInternal(text: String, prefs: UserPreferences, flushQueue: Boolean) {
        if (!prefs.voiceOutputEnabled || !isTtsReady) return
        val engine = tts ?: return

        val cleanText = sanitizeForSpeech(text).take(900)
        if (cleanText.isBlank()) return

        val hasDevanagari = DEVANAGARI_REGEX.containsMatchIn(cleanText)
        val locale = when {
            hasDevanagari || prefs.languageMode == LanguageMode.HINDI ->
                Locale.Builder().setLanguage("hi").setRegion("IN").build()
            else ->
                Locale.Builder().setLanguage("en").setRegion("IN").build()
        }
        val langRes = engine.setLanguage(locale)
        if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
        }

        val selectedMale = applyVoicePersonality(engine, prefs, locale)
        val effectivePitch = if (prefs.voiceGender == VoiceGender.MALE && !selectedMale) {
            minOf(prefs.voicePitch, 0.78f)
        } else {
            prefs.voicePitch
        }
        engine.setPitch(effectivePitch.coerceIn(0.5f, 1.8f))
        engine.setSpeechRate(prefs.voiceSpeed.coerceIn(0.5f, 1.8f))

        val utteranceId = UUID.randomUUID().toString()
        if (flushQueue) {
            activeUtterances.clear()
        }
        activeUtterances.add(utteranceId)
        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(cleanText, queueMode, null, utteranceId)
    }

    private fun applyVoicePersonality(
        engine: TextToSpeech,
        prefs: UserPreferences,
        targetLocale: Locale
    ): Boolean {
        val wantMale = prefs.voiceGender == VoiceGender.MALE
        try {
            val voices = engine.voices ?: return false
            val indianVoices = voices.filter {
                it.locale.country.equals("IN", ignoreCase = true) ||
                    it.locale.language.equals(targetLocale.language, ignoreCase = true)
            }
            val pool = indianVoices.ifEmpty { voices.toList() }

            val matched: Voice? = if (wantMale) {
                pool.firstOrNull { v -> isMaleVoice(v) }
                    ?: voices.firstOrNull { v ->
                        v.locale.language.equals("en", ignoreCase = true) && isMaleVoice(v)
                    }
            } else {
                pool.firstOrNull { v -> isFemaleVoice(v) }
                    ?: voices.firstOrNull { v ->
                        v.locale.language.equals("en", ignoreCase = true) && isFemaleVoice(v)
                    }
            }

            if (matched != null) {
                engine.voice = matched
                return true
            }
            pool.firstOrNull()?.let { engine.voice = it }
        } catch (_: Exception) {
        }
        return false
    }

    private fun isMaleVoice(voice: Voice): Boolean {
        val n = voice.name.lowercase(Locale.ROOT)
        val features = voice.features?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
        if (n.contains("female") || features.contains("female")) return false
        return n.contains("#male") ||
            n.contains("-male") ||
            n.contains("_male") ||
            n.contains("male") ||
            n.contains("-iom") ||
            n.contains("en-in-x-end") ||
            n.contains("en-in-x-ene") ||
            n.contains("en-in-x-ahp") ||
            n.contains("hi-in-x-hic") ||
            n.contains("hi-in-x-hid") ||
            n.contains("hi-in-x-hie") ||
            features.contains("male")
    }

    private fun isFemaleVoice(voice: Voice): Boolean {
        val n = voice.name.lowercase(Locale.ROOT)
        val features = voice.features?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
        if (n.contains("#male") || n.contains("-male")) return false
        return n.contains("female") ||
            features.contains("female") ||
            n.contains("-iog") ||
            n.contains("en-in-x-ena") ||
            n.contains("en-in-x-enc") ||
            n.contains("hi-in-x-hia") ||
            n.contains("hi-in-x-hib")
    }

    fun stopSpeaking() {
        activeUtterances.clear()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun playConfirmationTone() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 90)
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        activeUtterances.clear()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        private val DEVANAGARI_REGEX = Regex("[\\u0900-\\u097F]")
        private val URL_REGEX = Regex("https?://\\S+")
        private val MARKDOWN_SYMBOLS_REGEX = Regex("[*_`~#|>]+")
        private val EMOJI_AND_PICTOGRAPH_REGEX = Regex("[\\p{So}\\p{Cn}]+")
        private val DEGREE_C_REGEX = Regex("(\\d+)\\s*°\\s*C\\b", RegexOption.IGNORE_CASE)
        private val DEGREE_F_REGEX = Regex("(\\d+)\\s*°\\s*F\\b", RegexOption.IGNORE_CASE)
        private val PERCENT_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*%")
        private val TIME_COLON_REGEX = Regex("\\b(\\d{1,2}):00\\b")
        private val TAGORE_CAPS_REGEX = Regex("\\bTAGORE\\b")
        private val WIFI_REGEX = Regex("\\bwi-?fi\\b", RegexOption.IGNORE_CASE)
        private val HINGLISH_CLEANUP_REGEX = Regex("\\s{2,}")

        /**
         * Normalizes text for crystal-clear TTS pronunciation across Hinglish, Hindi, and English.
         * Prevents spelling out ALL-CAPS words, removes emojis/markdown noise, and expands symbols.
         */
        fun sanitizeForSpeech(rawText: String): String {
            var s = rawText
            s = URL_REGEX.replace(s, " ")
            s = MARKDOWN_SYMBOLS_REGEX.replace(s, " ")
            s = s.replace("•", ", ")
                .replace("—", ", ")
                .replace("–", ", ")
                .replace("->", " to ")
                .replace("&", " and ")
                .replace("/", " or ")
            s = DEGREE_C_REGEX.replace(s, "$1 degrees Celsius")
            s = DEGREE_F_REGEX.replace(s, "$1 degrees Fahrenheit")
            s = PERCENT_REGEX.replace(s, "$1 percent")
            s = TIME_COLON_REGEX.replace(s, "$1 o'clock")
            s = TAGORE_CAPS_REGEX.replace(s, "Tagore")
            s = WIFI_REGEX.replace(s, "Wi-Fi")
            s = EMOJI_AND_PICTOGRAPH_REGEX.replace(s, " ")
            s = s.replace('\n', ' ')
            return HINGLISH_CLEANUP_REGEX.replace(s, " ").trim()
        }
    }
}
