package com.example.core.ai

import android.provider.Settings
import android.view.KeyEvent
import com.example.data.local.SmartDeviceEntity
import com.example.data.remote.TagoreCloudSyncRepository
import com.example.data.repository.TagoreVaultRepository
import com.example.domain.model.ConfirmationActionType
import com.example.domain.model.PendingConfirmation
import com.example.domain.model.RingState
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoiceGender
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class OfflineExecutionResult(
    val handledLocally: Boolean,
    val replyText: String,
    val nextRingState: RingState = RingState.SPEAKING,
    val deepKnowledgeTopic: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val deviceAction: LocalDeviceAction? = null,
    val updatedVoiceGender: VoiceGender? = null
)

enum class SearchTarget { GOOGLE, YOUTUBE, MAPS }

sealed interface LocalDeviceAction {
    data class AdjustVolume(val deltaPercent: Int) : LocalDeviceAction
    data class SetVolumePercent(val percent: Int) : LocalDeviceAction
    data class SetMute(val mute: Boolean) : LocalDeviceAction
    data class MediaControl(val keyCode: Int) : LocalDeviceAction
    data class AdjustBrightness(val percent: Int) : LocalDeviceAction
    data class SetFlashlight(val enable: Boolean?) : LocalDeviceAction
    data class OpenSystemSettings(val settingsAction: String) : LocalDeviceAction
    data class LaunchApp(val appQuery: String) : LocalDeviceAction
    data class OpenCamera(val videoMode: Boolean = false) : LocalDeviceAction
    data class OpenDialer(val number: String = "") : LocalDeviceAction
    data class OpenSmsDraft(val message: String = "") : LocalDeviceAction
    data class SetSystemAlarm(val hour: Int, val minute: Int, val label: String = "TAGORE Alarm") : LocalDeviceAction
    data class SetSystemTimer(val seconds: Int, val label: String = "TAGORE Timer") : LocalDeviceAction
    data class WebOrMapSearch(val query: String, val target: SearchTarget) : LocalDeviceAction
    data object SpeakDeviceTelemetry : LocalDeviceAction
}

/**
 * Ultra-fast deterministic Offline-First AI, Conversational, and Phone-Control Engine for TAGORE.
 * Pre-compiled matchers + Room hot-cached lookup deliver <5ms human-like replies and native
 * Android phone control across Hinglish, Hindi, and Indian English.
 */
class OfflineAssistantEngine(
    private val vault: TagoreVaultRepository
) {
    private val offlineKnowledgeBase: Map<String, Pair<String, String>> = mapOf(
        "quantum" to (
            "Quantum computing superposition aur entanglement ka use karke qubits par parallel calculations karta hai, jo classical computers se kahin zyada tez hoti hain." to
                "Deep Knowledge — Quantum Computing:\n1. Superposition: Ek qubit ek saath 0 aur 1 ke combination mein reh sakta hai.\n2. Entanglement: Do qubits state share karte hain, ek ko measure karte hi doosra correlate ho jata hai.\n3. Quantum Interference: Shor's aur Grover's jaise algorithms sahi answer ko amplify karte hain."
            ),
        "robotics" to (
            "Robotics sensors, embedded control aur AI perception ko jodta hai taaki machines real world mein smartly kaam kar sakein." to
                "Deep Knowledge — Robotics Architecture:\n1. Perception Layer: LiDAR, IMU aur camera sensor fusion.\n2. Planning Layer: SLAM mapping aur path planning.\n3. Actuation: PID control loops aur servo telemetry."
            ),
        "ai" to (
            "Artificial Intelligence machines ko samajhne aur seekhne ki shakti deta hai, aur offline AI aapki privacy aur instant speed dono bachata hai." to
                "Deep Knowledge — Edge & Offline AI:\n1. Quantization (INT4/INT8) models ko compact aur ultra-fast banata hai.\n2. Local Memory: Context aapke device par hi safe rehta hai.\n3. Hybrid Routing: Instant commands offline chalte hain aur complex reasoning cloud par."
            ),
        "encryption" to (
            "AES-256-GCM bank-grade encryption hai jo Android hardware keystore ke zariye aapka data aur audit logs bilkul surakshit rakhta hai." to
                "Deep Knowledge — AES-256-GCM Security:\n1. 256-bit key hardware enclave mein locked rehti hai.\n2. Har entry ke liye unique 96-bit IV generate hota hai.\n3. GCM tag kisi bhi tampering ko turant pakad leta hai."
            ),
        "tagore" to (
            "Main Tagore hoon — Mr. Moksh Tagore ka banaya hua aapka personal AI companion, jo offline aur online har device par aapke saath kaam karta hai." to
                "Deep Knowledge — Tagore Ecosystem:\n1. Core Intelligence: Instant offline engine aur cloud Gemini reasoning.\n2. Holographic Ring UI: 15 real-time visual states.\n3. Cross-Device Continuity: Phone, Laptop, Watch aur Tablet sync."
            )
    )

    suspend fun processInput(
        rawInput: String,
        prefs: UserPreferences,
        pendingDeepTopic: String?,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ): OfflineExecutionResult {
        val input = rawInput.trim()
        val lower = input.lowercase(Locale.ROOT)
        val honorific = prefs.honorific.ifBlank { "Sir" }
        val name = prefs.preferredName.ifBlank { honorific }

        // 0. Check if user accepted Deep Knowledge offer
        if (pendingDeepTopic != null && isAffirmative(lower)) {
            val deep = offlineKnowledgeBase[pendingDeepTopic]?.second
            if (deep != null) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Bilkul $honorific, yeh rahi poori detail:\n\n$deep",
                    nextRingState = RingState.SPEAKING
                )
            }
        }

        // 1. Wake word / Natural Greetings
        if (lower in WAKE_PHRASES) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Yes $honorific! Main bilkul taiyar hoon, bataiye kya karein?",
                nextRingState = RingState.WAKE
            )
        }

        // 1b. Voice Gender Switch via Voice Command ("male voice", "female voice")
        if (lower.contains("female voice") || lower.contains("ladki ki aawaz") || lower.contains("woman voice")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Bilkul $honorific! Maine apni aawaz Female Voice par set kar di hai. Ab main aise hi baat karungi.",
                nextRingState = RingState.SUCCESS,
                updatedVoiceGender = VoiceGender.FEMALE
            )
        }
        if (lower.contains("male voice") || lower.contains("ladke ki aawaz") || lower.contains("man voice") || lower.contains("jarvis voice")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific! Maine apni aawaz Male Voice par set kar di hai. Ab main isi tarah baat karunga.",
                nextRingState = RingState.SUCCESS,
                updatedVoiceGender = VoiceGender.MALE
            )
        }

        // 1c. Instant Human-Like Conversational Small Talk & Mood Support
        val humanReply = tryNaturalHumanChat(lower, honorific, name)
        if (humanReply != null) return humanReply

        // 2. Emergency SOS (Always requires confirmation!)
        if (lower.contains("sos") || lower.contains("emergency") || lower.contains("help me")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, Emergency Mode active hai. Kya main abhi 112 dialer open karoon?",
                nextRingState = RingState.EMERGENCY,
                pendingConfirmation = PendingConfirmation(
                    id = "sos_${System.currentTimeMillis()}",
                    title = "Confirm Emergency Action",
                    promptSpeech = "$honorific, kya main emergency dialer open karoon?",
                    actionType = ConfirmationActionType.EMERGENCY_CALL
                )
            )
        }

        // 3. Social media / Reel posting confirmation pattern
        if (lower.contains("reel post") || lower.contains("post kar do") || lower.contains("post this")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, kya main is reel ko post kar doon? Bas ek yes bol dijiye.",
                nextRingState = RingState.WAITING,
                pendingConfirmation = PendingConfirmation(
                    id = "social_${System.currentTimeMillis()}",
                    title = "Confirm Social Post",
                    promptSpeech = "$honorific, kya main is reel ko post kar doon?",
                    actionType = ConfirmationActionType.POST_SOCIAL,
                    payload = input
                )
            )
        }

        // 4. Time & Date offline
        if (lower.contains("time") && !lower.contains("timer") || lower.contains("samay") || lower.contains("kitne baje") || lower.contains("date") || lower.contains("tarikh")) {
            val timeStr = SimpleDateFormat("hh:mm a, EEEE dd MMM yyyy", Locale.getDefault()).format(Date())
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, abhi $timeStr ho rahe hain.",
                nextRingState = RingState.SPEAKING
            )
        }

        // 5. Offline Calculator
        val calcResult = tryEvaluateMath(lower)
        if (calcResult != null) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, iska answer $calcResult hai.",
                nextRingState = RingState.SUCCESS
            )
        }

        // 6. Comprehensive Android Phone Controls (Volume, Mute, Media, Flashlight, Apps, Camera, Alarm, Timer, Calls, Settings, Search)
        val phoneControl = tryHandlePhoneControl(input, lower, honorific)
        if (phoneControl != null) return phoneControl

        // 7. Smart Home Voice Commands
        val smartResult = tryHandleSmartHome(lower, honorific, cloudRepo, isOnline)
        if (smartResult != null) return smartResult

        // 8. Personal Memory Save & Fast Indexed Recall
        if (lower.startsWith("yaad rakh") || lower.startsWith("remember ") || lower.contains("save memory")) {
            val fact = MEM_STRIP_REGEX.replace(input, "").trim().ifBlank { input }
            vault.saveMemory(
                category = if (lower.contains("project")) "Project" else "Personal",
                title = fact.take(40),
                content = fact,
                pinned = true,
                tags = if (lower.contains("project")) "project,pinned" else "personal,pinned",
                cloudRepo = cloudRepo,
                isOnline = isOnline
            )
            vault.logSecureEvent("MEMORY", "Saved personal memory", fact, isOffline = !isOnline)
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific! Maine yaad rakh liya hai, jab bhi poochenge turant bata dunga.",
                nextRingState = RingState.SUCCESS
            )
        }

        if (lower.contains("project kholo") || lower.contains("open project") || lower.contains("mera project") || lower.contains("my memory")) {
            val memories = vault.searchMemoriesFast("project").ifEmpty { vault.getAllMemories() }
            return if (memories.isNotEmpty()) {
                val top = memories.first()
                vault.recordMemoryAccess(top.id)
                vault.broadcastEcosystemCommand("ALL", "Opened memory: ${top.title}", cloudRepo, isOnline)
                OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Bilkul $honorific, aapka saved project \"${top.title}\" yeh raha: ${top.content}",
                    nextRingState = RingState.SUCCESS
                )
            } else {
                OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "$honorific, abhi koi saved project memory nahi mili. Bas boliye 'Remember my project is...' aur main save kar lunga.",
                    nextRingState = RingState.WAITING
                )
            }
        }

        // 8b. Offline Conversation History Recall (Room ConversationDao)
        if (lower.contains("last conversation") || lower.contains("chat history") || lower.contains("pichli baat") || lower.contains("what did we talk")) {
            val recent = vault.getRecentConversationSnapshot(6)
            return if (recent.isNotEmpty()) {
                val summary = recent.takeLast(4).joinToString("\n") { msg ->
                    val who = if (msg.author == "USER") name else "Tagore"
                    "$who: ${msg.content.take(70)}"
                }
                OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Zaroor $honorific, humari pichli baat yeh thi:\n$summary",
                    nextRingState = RingState.SPEAKING
                )
            } else {
                OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "$honorific, abhi humari nayi shuruat hai. Bataiye kis topic par baat karein?",
                    nextRingState = RingState.SPEAKING
                )
            }
        }

        // 9. Productivity: Notes, Reminders, Todos, Alarms
        if (lower.startsWith("note ") || lower.contains("note save") || lower.contains("likh lo")) {
            val noteText = NOTE_STRIP_REGEX.replace(input, "").trim().ifBlank { input }
            vault.addProductivityItem("NOTE", noteText.take(60), noteText, 0L, cloudRepo, isOnline)
            vault.logSecureEvent("PRODUCTIVITY", "Created offline note", noteText, isOffline = !isOnline)
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Likh liya $honorific! Note safe hai.",
                nextRingState = RingState.SUCCESS
            )
        }

        if (lower.contains("remind") || lower.contains("yaad dila") || lower.contains("todo") || lower.contains("task")) {
            val kind = when {
                lower.contains("todo") || lower.contains("task") -> "TODO"
                else -> "REMINDER"
            }
            val cleaned = TASK_STRIP_REGEX.replace(input, "").trim().ifBlank { input }
            val dueMillis = System.currentTimeMillis() + 3600_000L
            vault.addProductivityItem(kind, cleaned.take(70), cleaned, dueMillis, cloudRepo, isOnline)
            vault.logSecureEvent("PRODUCTIVITY", "Created $kind", cleaned, isOffline = !isOnline)
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, maine \"$cleaned\" set kar diya hai.",
                nextRingState = RingState.SUCCESS
            )
        }

        // 10. Built-in Offline Knowledge Base + Deep Knowledge Prompt
        for ((topic, pair) in offlineKnowledgeBase) {
            if (lower.contains(topic)) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "${pair.first}\n\n$honorific, kya aapko iski deep knowledge chahiye?",
                    nextRingState = RingState.SPEAKING,
                    deepKnowledgeTopic = topic
                )
            }
        }

        // 11. Business Productivity Brief (Offline Summary)
        if (lower.contains("brief") || lower.contains("summary") || lower.contains("status")) {
            val devices = vault.getAllSmartDevices()
            val activeCount = devices.count { it.isOn }
            val memories = vault.getAllMemories().size
            val historyCount = vault.getRecentConversationSnapshot(50).size
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Quick status $honorific: $activeCount out of ${devices.size} smart devices on hain, $memories memories aur $historyCount chats local vault mein safe hain. Sab smooth chal raha hai!",
                nextRingState = RingState.SPEAKING
            )
        }

        // 12. Warm human offline fallback when disconnected
        return OfflineExecutionResult(
            handledLocally = false,
            replyText = "Ji $honorific, abhi hum offline mode mein hain, toh main phone control, apps open karna, smart home, notes, memory ya calculation turant kar sakta hoon. Online aate hi main har open sawaal ka bhi jawab dunga!",
            nextRingState = RingState.SPEAKING
        )
    }

    private fun tryHandlePhoneControl(
        rawInput: String,
        lower: String,
        honorific: String
    ): OfflineExecutionResult? {
        // A. Battery & Storage check
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("storage")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, phone ka battery aur storage check kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SpeakDeviceTelemetry
            )
        }

        // B. Mute / Silent / Unmute
        if (lower.contains("unmute") || lower.contains("ring mode") || lower.contains("awaaz chalu")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, phone ko normal ring aur sound mode par kar diya hai.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SetMute(false)
            )
        }
        if (lower.contains("mute") || lower.contains("silent mode") || lower.contains("phone silent")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, phone ko silent aur mute mode par kar diya hai.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SetMute(true)
            )
        }

        // C. Volume Controls (Full / Exact % / Up / Down)
        if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("sound")) {
            val pct = LEVEL_REGEX.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: NUMBER_REGEX.find(lower)?.value?.toIntOrNull()?.takeIf { it in 0..100 }
            if (pct != null) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Done $honorific, volume $pct percent par set kar di hai.",
                    nextRingState = RingState.SUCCESS,
                    deviceAction = LocalDeviceAction.SetVolumePercent(pct)
                )
            }
            if (lower.contains("full") || lower.contains("max") || lower.contains("100")) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Done $honorific, phone volume full kar di hai.",
                    nextRingState = RingState.SUCCESS,
                    deviceAction = LocalDeviceAction.SetVolumePercent(100)
                )
            }
            if (lower.contains("up") || lower.contains("badhao") || lower.contains("tez") || lower.contains("increase")) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Lo $honorific, volume badha di hai.",
                    nextRingState = RingState.SUCCESS,
                    deviceAction = LocalDeviceAction.AdjustVolume(15)
                )
            }
            if (lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("slow")) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Theek hai $honorific, volume kam kar di hai.",
                    nextRingState = RingState.SUCCESS,
                    deviceAction = LocalDeviceAction.AdjustVolume(-15)
                )
            }
        }

        // D. Media Playback Controls (Play / Pause / Next / Previous)
        if (lower.contains("pause music") || lower.contains("pause song") || lower.contains("gaana roko") || lower == "pause" || lower == "stop music") {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, music pause kar diya hai.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.MediaControl(KeyEvent.KEYCODE_MEDIA_PAUSE)
            )
        }
        if (lower.contains("play music") || lower.contains("play song") || lower.contains("gaana chalao") || lower == "resume music") {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Bilkul $honorific, music play kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.MediaControl(KeyEvent.KEYCODE_MEDIA_PLAY)
            )
        }
        if (lower.contains("next song") || lower.contains("next track") || lower.contains("agla gaana")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, next song play kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.MediaControl(KeyEvent.KEYCODE_MEDIA_NEXT)
            )
        }
        if (lower.contains("previous song") || lower.contains("previous track") || lower.contains("pichla gaana")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, pichla gaana chala raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.MediaControl(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            )
        }

        // E. Explicit Flashlight ON / OFF / Toggle
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash light")) {
            val enable: Boolean? = when {
                ON_WORD_REGEX.containsMatchIn(lower) || lower.contains("chalu") || lower.contains("jalao") -> true
                OFF_WORD_REGEX.containsMatchIn(lower) || lower.contains("band") || lower.contains("bujhao") -> false
                else -> null
            }
            val statusWord = when (enable) {
                true -> "on kar di"
                false -> "off kar di"
                null -> "toggle kar di"
            }
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, phone ki flashlight $statusWord hai.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SetFlashlight(enable)
            )
        }

        // F. System Timer & System Alarm
        if (lower.contains("timer")) {
            val qty = NUMBER_REGEX.find(lower)?.value?.toIntOrNull()?.coerceAtLeast(1) ?: 5
            val seconds = if (lower.contains("sec")) qty else qty * 60
            val unitLabel = if (lower.contains("sec")) "$qty seconds" else "$qty minutes"
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, $unitLabel ka timer set kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SetSystemTimer(seconds, "Tagore Timer")
            )
        }
        if (lower.contains("alarm")) {
            val timeMatch = ALARM_TIME_REGEX.find(lower)
            var hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            if ((lower.contains("pm") || lower.contains("shaam") || lower.contains("raat")) && hour in 1..11) {
                hour += 12
            }
            hour = hour.coerceIn(0, 23)
            val minStr = minute.toString().padStart(2, '0')
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, $hour:$minStr ka alarm set kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.SetSystemAlarm(hour, minute, "Tagore Alarm")
            )
        }

        // G. Phone Calls (Dialer) & SMS Drafts
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.contains("phone lagao") || lower.contains("call karo")) {
            val digits = PHONE_DIGITS_REGEX.find(rawInput)?.value.orEmpty()
            val reply = if (digits.isNotBlank()) {
                "Bilkul $honorific, $digits ke liye phone dialer khol raha hoon."
            } else {
                "Ji $honorific, phone dialer open kar raha hoon."
            }
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = reply,
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenDialer(digits)
            )
        }
        if (lower.startsWith("send sms") || lower.startsWith("send message") || lower.contains("message bhejo")) {
            val msg = rawInput.replace(SMS_STRIP_REGEX, "").trim()
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Ji $honorific, SMS app open kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSmsDraft(msg)
            )
        }

        // H. System Camera
        if (lower.contains("record video") || lower.contains("video banao")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, video camera open kar raha hoon.",
                nextRingState = RingState.CAMERA_VISION,
                deviceAction = LocalDeviceAction.OpenCamera(videoMode = true)
            )
        }
        if (lower.contains("take photo") || lower.contains("take a photo") || lower.contains("photo khicho") || lower.contains("open camera") || lower.contains("camera kholo") || lower.contains("selfie")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Ready $honorific! Phone ka camera open kar raha hoon.",
                nextRingState = RingState.CAMERA_VISION,
                deviceAction = LocalDeviceAction.OpenCamera(videoMode = false)
            )
        }

        // I. System Settings Panels (Brightness, Wi-Fi, Bluetooth, Hotspot, Airplane, Location, Sound, Battery, General)
        if (lower.contains("brightness")) {
            val pct = NUMBER_REGEX.find(lower)?.value?.toIntOrNull()?.coerceIn(10, 100) ?: 70
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific, brightness control ke liye display settings khol raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.AdjustBrightness(pct)
            )
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Haan $honorific, Wi-Fi settings open kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_WIFI_SETTINGS)
            )
        }
        if (lower.contains("bluetooth")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Bilkul $honorific, Bluetooth settings open kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
        }
        if (lower.contains("airplane") || lower.contains("flight mode")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Ji $honorific, Airplane mode settings khol raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            )
        }
        if (lower.contains("location") || lower.contains("gps")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Haan $honorific, Location aur GPS settings open kar raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
        }
        if (lower.contains("hotspot") || lower.contains("tethering")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Ji $honorific, Hotspot aur network settings khol raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_WIRELESS_SETTINGS)
            )
        }
        if (lower.contains("device settings") || lower.contains("open settings") || lower.contains("phone settings") || lower.contains("settings kholo")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Haan $honorific, phone ki settings khol raha hoon.",
                nextRingState = RingState.SUCCESS,
                deviceAction = LocalDeviceAction.OpenSystemSettings(Settings.ACTION_SETTINGS)
            )
        }

        // J. YouTube, Google Maps, and Web Search
        if ((lower.contains("youtube") && (lower.contains("search") || lower.contains("par") || lower.contains("play") || lower.contains("chalao"))) &&
            !lower.endsWith("youtube")
        ) {
            val query = YOUTUBE_SEARCH_STRIP.replace(rawInput, "").trim()
            if (query.isNotBlank()) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Bilkul $honorific, YouTube par \"$query\" search kar raha hoon.",
                    nextRingState = RingState.SEARCHING,
                    deviceAction = LocalDeviceAction.WebOrMapSearch(query, SearchTarget.YOUTUBE)
                )
            }
        }
        if (lower.contains("navigate to") || lower.contains("directions to") || lower.contains("maps par") || lower.contains("rasta batao")) {
            val query = MAPS_STRIP.replace(rawInput, "").trim()
            if (query.isNotBlank()) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Done $honorific, Google Maps par \"$query\" dikha raha hoon.",
                    nextRingState = RingState.SEARCHING,
                    deviceAction = LocalDeviceAction.WebOrMapSearch(query, SearchTarget.MAPS)
                )
            }
        }
        if (lower.startsWith("search ") || lower.contains("google par") || lower.contains("search on google")) {
            val query = GOOGLE_STRIP.replace(rawInput, "").trim()
            if (query.isNotBlank()) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Ji $honorific, \"$query\" search kar raha hoon.",
                    nextRingState = RingState.SEARCHING,
                    deviceAction = LocalDeviceAction.WebOrMapSearch(query, SearchTarget.GOOGLE)
                )
            }
        }

        // K. Launch Installed Phone Apps ("open whatsapp", "youtube kholo", "chrome chalu karo", etc.)
        val appMatch = OPEN_APP_PREFIX_REGEX.find(lower) ?: OPEN_APP_SUFFIX_REGEX.find(lower)
        if (appMatch != null) {
            val appName = appMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            if (appName.isNotBlank() &&
                !appName.contains("project") &&
                !appName.contains("door") &&
                !appName.contains("darwaza")
            ) {
                return OfflineExecutionResult(
                    handledLocally = true,
                    replyText = "Done $honorific, $appName open kar raha hoon.",
                    nextRingState = RingState.SUCCESS,
                    deviceAction = LocalDeviceAction.LaunchApp(appName)
                )
            }
        }

        return null
    }

    private fun tryNaturalHumanChat(lower: String, honorific: String, name: String): OfflineExecutionResult? {
        if (lower.contains("kaise ho") || lower.contains("kya haal") || lower.contains("how are you") || lower.contains("sab theek")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Main bilkul badhiya hoon $honorific! Aap sunaiye $name, aaj aapka din kaisa chal raha hai? Kya kaam karein?",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower.contains("good morning") || lower.contains("suprabhat")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Good morning $name! Umeed hai aaj ka din energetic rahega. Bataiye $honorific, din ki shuruat kis kaam se karein?",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower.contains("good afternoon") || lower.contains("good evening")) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greet = if (hour < 17) "Good afternoon" else "Good evening"
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$greet $honorific! Bataiye, main aapke kaam ko kaise aasan bana sakta hoon?",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower.contains("good night") || lower.contains("so ja") || lower.contains("bye")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Good night $honorific, aaram kijiye. Agar chahein toh main Night Mode chalu karke lights off aur door lock check kar sakta hoon!",
                nextRingState = RingState.SLEEP
            )
        }
        if (lower.contains("thank") || lower.contains("shukriya") || lower.contains("dhanyawad") || lower == "badhiya" || lower == "nice") {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Aapka welcome hai $honorific! Aapke liye hamesha hazir hoon.",
                nextRingState = RingState.SUCCESS
            )
        }
        if (lower.contains("who are you") || lower.contains("tum kaun") || lower.contains("aap kaun") || lower.contains("who made you") || lower.contains("kisne banaya")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Main Tagore hoon $honorific — Mr. Moksh Tagore dwara design kiya gaya aapka personal AI companion. Main aapke phone, watch, laptop aur smart home sabko ek saath sambhalta hoon.",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower.contains("bore") || lower.contains("tired") || lower.contains("thak gaya") || lower.contains("mood off")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Arre $name, thoda break le lijiye $honorific! Chai ya paani pi lijiye, aur chahein toh hum kisi interesting topic ya naye idea par baat kar sakte hain.",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower.contains("kya kar rahe") || lower.contains("what are you doing")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Bas aapki aawaz sunne ka intezar kar raha tha $honorific! Bataiye aaj kya plan hai?",
                nextRingState = RingState.SPEAKING
            )
        }
        if (lower in listOf("hello", "hi", "hey", "hlo", "haan", "suno", "listen")) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Ji $honorific, bataiye, main dhyan se sun raha hoon.",
                nextRingState = RingState.WAKE
            )
        }
        return null
    }

    private suspend fun tryHandleSmartHome(
        lower: String,
        honorific: String,
        cloudRepo: TagoreCloudSyncRepository?,
        isOnline: Boolean
    ): OfflineExecutionResult? {
        val turnOn = lower.contains(" on") || lower.contains("chalu") || lower.contains("jalao") || lower.contains("start")
        val turnOff = lower.contains(" off") || lower.contains("band") || lower.contains("stop")
        val unlockCmd = lower.contains("unlock") || lower.contains("khol")
        val lockCmd = !unlockCmd && LOCK_WORD_REGEX.containsMatchIn(lower)

        val devices = vault.getAllSmartDevices()

        if (lower.contains("night mode") || lower.contains("sleep mode")) {
            devices.forEach { dev ->
                val updated = when (dev.category) {
                    "LIGHT", "TV" -> dev.copy(isOn = false)
                    "LOCK" -> dev.copy(isLocked = true, isOn = true)
                    "AC" -> dev.copy(isOn = true, temperature = 23)
                    else -> dev
                }
                vault.updateSmartDevice(updated, cloudRepo, isOnline)
            }
            vault.logSecureEvent("SMART_HOME", "Activated Night Security Routine", "Lights off, Lock secured, AC 23C", !isOnline)
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific! Night Mode laga diya — lights band hain, AC 23 degree par hai aur main door lock hai.",
                nextRingState = RingState.SUCCESS
            )
        }

        if (lower.contains("work mode") || lower.contains("focus mode") || lower.contains("office mode")) {
            devices.forEach { dev ->
                val updated = when {
                    dev.room.equals("Office", ignoreCase = true) -> dev.copy(isOn = true, level = 95)
                    else -> dev
                }
                vault.updateSmartDevice(updated, cloudRepo, isOnline)
            }
            vault.logSecureEvent("SMART_HOME", "Activated Workstation Focus Mode", "Office devices set to 95%", !isOnline)
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "Done $honorific! Focus Mode active hai, office setup ready kar diya.",
                nextRingState = RingState.SUCCESS
            )
        }

        val targetDevice: SmartDeviceEntity? = devices.firstOrNull { dev ->
            val nameMatch = lower.contains(dev.name.lowercase(Locale.ROOT))
            val catMatch = when (dev.category) {
                "LIGHT" -> lower.contains("light") || lower.contains("lamp") || lower.contains("batti")
                "AC" -> lower.contains("ac") || lower.contains("air conditioner") || lower.contains("temp")
                "FAN" -> lower.contains("fan") || lower.contains("pankha")
                "LOCK" -> lower.contains("lock") || lower.contains("door") || lower.contains("darwaza")
                "TV" -> lower.contains("tv") || lower.contains("television")
                else -> false
            }
            val roomMatch = lower.contains(dev.room.lowercase(Locale.ROOT))
            nameMatch || (catMatch && (roomMatch || !lower.contains("bedroom") && !lower.contains("office") && !lower.contains("living")))
        } ?: devices.firstOrNull { dev ->
            when (dev.category) {
                "LIGHT" -> lower.contains("light") || lower.contains("lamp")
                "AC" -> lower.contains("ac")
                "FAN" -> lower.contains("fan") || lower.contains("pankha")
                "LOCK" -> lower.contains("lock") || lower.contains("door")
                "TV" -> lower.contains("tv")
                else -> false
            }
        }

        if (targetDevice == null) return null

        if (targetDevice.category == "LOCK" && unlockCmd && !lockCmd) {
            return OfflineExecutionResult(
                handledLocally = true,
                replyText = "$honorific, security check: kya main ${targetDevice.name} unlock kar doon?",
                nextRingState = RingState.WAITING,
                pendingConfirmation = PendingConfirmation(
                    id = "unlock_${targetDevice.id}",
                    title = "Confirm Door Unlock",
                    promptSpeech = "$honorific, kya main ${targetDevice.name} unlock kar doon?",
                    actionType = ConfirmationActionType.UNLOCK_DOOR,
                    targetId = targetDevice.id.toString()
                )
            )
        }

        val tempMatch = TEMP_REGEX.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val levelMatch = LEVEL_REGEX.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val updated = when {
            targetDevice.category == "LOCK" && lockCmd -> targetDevice.copy(isLocked = true, isOn = true)
            targetDevice.category == "AC" && tempMatch != null ->
                targetDevice.copy(isOn = true, temperature = tempMatch.coerceIn(16, 30))
            levelMatch != null ->
                targetDevice.copy(isOn = levelMatch > 0, level = levelMatch.coerceIn(0, 100))
            turnOn -> targetDevice.copy(isOn = true)
            turnOff -> targetDevice.copy(isOn = false)
            else -> targetDevice.copy(isOn = !targetDevice.isOn)
        }

        vault.updateSmartDevice(updated, cloudRepo, isOnline)
        val statusText = when {
            updated.category == "LOCK" -> if (updated.isLocked) "locked" else "unlocked"
            updated.category == "AC" && updated.isOn -> "ON at ${updated.temperature}°C"
            updated.isOn -> "ON (${updated.level}%)"
            else -> "OFF"
        }
        vault.logSecureEvent("SMART_HOME", "${updated.name} -> $statusText", "Room: ${updated.room}", !isOnline)
        return OfflineExecutionResult(
            handledLocally = true,
            replyText = "Ho gaya $honorific, ${updated.name} ab $statusText hai.",
            nextRingState = RingState.SUCCESS
        )
    }

    private fun tryEvaluateMath(lower: String): String? {
        val cleaned = lower
            .replace("calculate", "")
            .replace("kitna hota hai", "")
            .replace("what is", "")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("multiply by", "*")
            .replace("into", "*")
            .replace("divided by", "/")
            .replace("x", "*")
            .trim()
        val match = MATH_REGEX.find(cleaned) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return "Undefined (division by zero)"
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
    }

    private fun isAffirmative(lower: String): Boolean {
        return AFFIRMATIVE_TOKENS.any { lower == it || lower.contains(it) }
    }

    companion object {
        private val WAKE_PHRASES = setOf("tagore", "hey tagore", "hi tagore", "hello tagore", "namaste tagore", "namaste")
        private val AFFIRMATIVE_TOKENS = listOf("yes", "haan", "ha", "han", "sure", "batao", "explain", "deep knowledge", "chahiye", "ok", "bilkul")
        private val MEM_STRIP_REGEX = Regex("(?i)^(yaad rakhna|yaad rakh|remember that|remember|save memory)\\s*")
        private val NOTE_STRIP_REGEX = Regex("(?i)^(note save karo|note likho|note|likh lo)\\s*")
        private val TASK_STRIP_REGEX = Regex("(?i)(set a reminder to|set reminder|remind me to|reminder set karo|add todo|add task)\\s*")
        private val NUMBER_REGEX = Regex("\\d+")
        private val ALARM_TIME_REGEX = Regex("(\\d{1,2})(?::(\\d{2}))?")
        private val PHONE_DIGITS_REGEX = Regex("[+]?\\d[\\d\\s-]{4,}")
        private val SMS_STRIP_REGEX = Regex("(?i)^(send sms|send message|message bhejo)\\s*")
        private val YOUTUBE_SEARCH_STRIP = Regex("(?i)(search youtube for|youtube par|youtube pe|on youtube|play on youtube|chalao)\\s*")
        private val MAPS_STRIP = Regex("(?i)(navigate to|directions to|maps par|dikhao|rasta batao)\\s*")
        private val GOOGLE_STRIP = Regex("(?i)(search on google|google par|search)\\s*")
        private val OPEN_APP_PREFIX_REGEX = Regex("^(?:open|launch|start|kholo|chalu karo)\\s+(.+)$")
        private val OPEN_APP_SUFFIX_REGEX = Regex("^(.+?)\\s+(?:kholo|khol do|open karo|chalu karo|app kholo)$")
        private val LOCK_WORD_REGEX = Regex("\\block\\b")
        private val ON_WORD_REGEX = Regex("\\bon\\b")
        private val OFF_WORD_REGEX = Regex("\\boff\\b")
        private val TEMP_REGEX = Regex("(\\d{2})\\s*(degree|deg|c)?")
        private val LEVEL_REGEX = Regex("(\\d{1,3})\\s*%")
        private val MATH_REGEX = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)$")
    }
}
