package com.example.core.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.MemoryEntity
import com.example.domain.model.ChatAuthor
import com.example.domain.model.ChatTurn
import com.example.domain.model.LanguageMode
import com.example.domain.model.ResponseLength
import com.example.domain.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "CloudGeminiEngine"

sealed interface CloudStreamEvent {
    data class Chunk(val accumulatedText: String) : CloudStreamEvent
    data class Completed(val finalText: String, val offersDeepKnowledge: Boolean) : CloudStreamEvent
    data class Failed(val errorMessage: String) : CloudStreamEvent
}

/**
 * Low-latency online AI intelligence backed by Firebase AI Logic:
 * - `gemini-3.5-flash-lite` for ultra-fast, human-like voice conversations
 * - `gemini-3.8-flash` for multimodal vision & deep reasoning tasks
 * Relies on App Check attestation (no client-side API key).
 */
class CloudGeminiEngine {

    private val fastChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.8f
                topP = 0.92f
                maxOutputTokens = 320
            }
        )
    }

    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.65f
                maxOutputTokens = 650
            }
        )
    }

    fun streamConversation(
        userPrompt: String,
        prefs: UserPreferences,
        recentHistory: List<ChatTurn>,
        memories: List<MemoryEntity>,
        attachedImage: Bitmap? = null
    ): Flow<CloudStreamEvent> = flow {
        val systemPrompt = buildSystemInstruction(prefs, memories)
        val historySummary = recentHistory.takeLast(6).joinToString("\n") { turn ->
            val role = if (turn.author == ChatAuthor.USER) prefs.preferredName else "TAGORE"
            "$role: ${turn.text}"
        }
        val composedPrompt = buildString {
            append(systemPrompt)
            if (historySummary.isNotBlank()) {
                append("\n\nRecent Conversation:\n")
                append(historySummary)
            }
            append("\n\n${prefs.preferredName} (${prefs.honorific}): ")
            append(userPrompt)
            append("\nTAGORE:")
        }

        val activeModel = if (attachedImage != null || prefs.responseLength == ResponseLength.DETAILED) {
            visionModel
        } else {
            fastChatModel
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(45_000L) {
                val inputContent = content {
                    if (attachedImage != null) {
                        image(attachedImage)
                    }
                    text(composedPrompt)
                }
                activeModel.generateContentStream(inputContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(CloudStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val finalStr = accumulated.toString().trim()
            if (finalStr.isNotBlank()) {
                val offersDeep = finalStr.contains("deep knowledge", ignoreCase = true)
                emit(CloudStreamEvent.Completed(finalStr, offersDeep))
            } else {
                emit(CloudStreamEvent.Failed("Model returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            emit(CloudStreamEvent.Failed("Cloud response timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic generation failed", e)
            emit(CloudStreamEvent.Failed(e.localizedMessage ?: "Cloud AI request failed."))
        }
    }

    private fun buildSystemInstruction(
        prefs: UserPreferences,
        memories: List<MemoryEntity>
    ): String {
        val langGuide = when (prefs.languageMode) {
            LanguageMode.HINGLISH ->
                "Speak naturally like a close, respectful Indian human friend in smooth Hinglish (mix of everyday Hindi and Indian English), warm, crisp, and effortless."
            LanguageMode.ENGLISH_IN ->
                "Speak naturally in warm, conversational Indian English with human flow and warmth."
            LanguageMode.HINDI ->
                "Speak naturally in warm, respectful conversational Hindi."
        }
        val lengthGuide = when (prefs.responseLength) {
            ResponseLength.CONCISE ->
                "Answer fast and directly in 1-3 short, spoken-friendly sentences. Do not drag or over-explain unless asked."
            ResponseLength.BALANCED ->
                "Keep replies natural, crisp, and conversational in 2-4 sentences."
            ResponseLength.DETAILED ->
                "Provide thoughtful, structured, human explanations with clear depth."
        }
        val memorySnippet = if (memories.isNotEmpty()) {
            memories.take(5).joinToString("; ") { "${it.title}: ${it.content}" }
        } else {
            "None saved yet."
        }
        return """
            You are Tagore, a fast, emotionally intelligent personal AI companion created by Mr. Moksh Tagore.
            Talk like a real human — warm, expressive, attentive, and natural — never stiff or robotic.
            Address the user respectfully as "${prefs.honorific}" (name: "${prefs.preferredName}"), always using respectful "Aap".
            Voice Persona: ${prefs.voicePersonality.label} (${prefs.voiceTone.stylePrompt}).
            Language: $langGuide
            Pronunciation & Clarity: Spell every English, Hindi, and Hinglish word cleanly and accurately using standard phonetic spelling (e.g., "Tagore", "Wi-Fi", "degrees Celsius", "percent"). Never use emojis, markdown stars (**), hashtags, or shorthand texting slang like "kr/kya/h" so voice synthesis pronounces every word clearly.
            Speed & Flow: $lengthGuide Start with the direct answer right away so voice output begins immediately.
            If discussing a deep technical or scientific topic, give a crisp summary first and gently ask: "${prefs.honorific}, kya aapko iski deep knowledge chahiye?"
            Saved Context: $memorySnippet
        """.trimIndent()
    }
}
