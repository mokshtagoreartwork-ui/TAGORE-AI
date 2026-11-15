package com.example

import android.content.Context
import android.view.KeyEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.LocalDeviceAction
import com.example.core.ai.OfflineAssistantEngine
import com.example.core.ai.SearchTarget
import com.example.core.crypto.TagoreCryptoManager
import com.example.core.voice.TagoreVoiceEngine
import com.example.data.local.TagoreDatabase
import com.example.data.local.UserPreferencesEntity
import com.example.data.repository.TagoreVaultRepository
import com.example.domain.model.ChatAuthor
import com.example.domain.model.ChatTurn
import com.example.domain.model.ConfirmationActionType
import com.example.domain.model.LanguageMode
import com.example.domain.model.RingState
import com.example.domain.model.UserPreferences
import com.example.domain.model.VoicePersonality
import com.example.domain.model.VoiceTone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TagoreOfflineEngineTest {

    private lateinit var db: TagoreDatabase
    private lateinit var vault: TagoreVaultRepository
    private lateinit var engine: OfflineAssistantEngine

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TagoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        TagoreDatabase.seedHardwareDefaultsIfEmpty(db)
        vault = TagoreVaultRepository(db)
        vault.warmHotCaches()
        engine = OfflineAssistantEngine(vault)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun wakeWord_returnsYesSirAndWakeState() = runBlocking {
        val res = engine.processInput("Tagore", UserPreferences(), null)
        assertTrue(res.handledLocally)
        assertEquals(RingState.WAKE, res.nextRingState)
        assertTrue(res.replyText.contains("Yes Sir"))
    }

    @Test
    fun offlineCalculator_evaluatesMath() = runBlocking {
        val res = engine.processInput("calculate 250 * 4", UserPreferences(), null)
        assertTrue(res.handledLocally)
        assertTrue(res.replyText.contains("1000"))
        assertEquals(RingState.SUCCESS, res.nextRingState)
    }

    @Test
    fun doorUnlock_requiresExplicitConfirmation() = runBlocking {
        val res = engine.processInput("unlock main door", UserPreferences(), null)
        assertTrue(res.handledLocally)
        assertNotNull(res.pendingConfirmation)
        assertEquals(ConfirmationActionType.UNLOCK_DOOR, res.pendingConfirmation?.actionType)
        assertEquals(RingState.WAITING, res.nextRingState)
    }

    @Test
    fun deepKnowledgeMode_offersAndExpandsOnAffirmative() = runBlocking {
        val prefs = UserPreferences(honorific = "Sir")
        val initial = engine.processInput("Quantum ke baare mein batao", prefs, null)
        assertTrue(initial.handledLocally)
        assertEquals("quantum", initial.deepKnowledgeTopic)
        assertTrue(initial.replyText.contains("deep knowledge chahiye"))

        val expanded = engine.processInput("haan batao", prefs, initial.deepKnowledgeTopic)
        assertTrue(expanded.handledLocally)
        assertTrue(expanded.replyText.contains("Superposition"))
    }

    @Test
    fun cryptoManager_encryptsAndDecryptsAuditLogs() {
        val crypto = TagoreCryptoManager()
        val secret = "Approved Night Security Mode at 23:10 IST"
        val enc = crypto.encrypt(secret)
        assertNotEquals(secret, enc.cipherTextBase64)
        val dec = crypto.decrypt(enc.cipherTextBase64, enc.ivBase64)
        assertEquals(secret, dec)
    }

    @Test
    fun roomConversationHistory_persistsSearchesAndRecallsOffline() = runBlocking {
        vault.saveConversationTurn(
            ChatTurn(
                id = "turn-1",
                author = ChatAuthor.USER,
                text = "Living room light on karo",
                isOfflineResponse = true
            ),
            ringState = RingState.THINKING,
            latencyMs = 0L
        )
        vault.saveConversationTurn(
            ChatTurn(
                id = "turn-2",
                author = ChatAuthor.TAGORE,
                text = "Done Sir. Living Room Main Light ab ON hai.",
                isOfflineResponse = true
            ),
            ringState = RingState.SUCCESS,
            latencyMs = 4L
        )

        val snapshot = vault.getRecentConversationSnapshot(10)
        assertEquals(2, snapshot.size)
        assertEquals(4L, snapshot[1].latencyMs)

        val searchHits = vault.searchConversationHistory("Living Room")
        assertEquals(2, searchHits.size)

        val recall = engine.processInput("last conversation", UserPreferences(), null)
        assertTrue(recall.handledLocally)
        assertTrue(recall.replyText.contains("Living Room Main Light"))
    }

    @Test
    fun roomUserPreferencesDao_persistsAndUpdatesFast() = runBlocking {
        val custom = UserPreferences(
            hasCompletedOnboarding = true,
            preferredName = "Moksh",
            honorific = "Boss",
            voicePersonality = VoicePersonality.FEMALE_INDIAN,
            voicePitch = 1.15f,
            voiceSpeed = 1.1f,
            voiceTone = VoiceTone.FRIENDLY,
            languageMode = LanguageMode.HINGLISH,
            darkMode = false
        )
        vault.saveRoomUserPreferences(custom)
        val loaded = vault.getRoomUserPreferences()
        assertNotNull(loaded)
        assertEquals("Boss", loaded?.honorific)
        assertEquals(VoicePersonality.FEMALE_INDIAN, loaded?.voicePersonality)
        assertEquals(false, loaded?.darkMode)

        db.userPreferencesDao().updateVoiceSettings(
            personality = VoicePersonality.BIGG_BOSS.name,
            pitch = 0.72f,
            speed = 0.90f,
            tone = VoiceTone.EXECUTIVE.name
        )
        val updated = db.userPreferencesDao().getPreferences()?.toDomain()
        assertEquals(VoicePersonality.BIGG_BOSS, updated?.voicePersonality)
        assertEquals(0.72f, updated?.voicePitch ?: 0f, 0.001f)
    }

    @Test
    fun roomMemoryDao_savesFastIndexedSearchAndTracksAccess() = runBlocking {
        val id = vault.saveMemory(
            category = "Project",
            title = "TAGORE Core Architecture",
            content = "Offline-first Room WAL database with Gemini Cloud sync",
            pinned = true,
            tags = "tagore,offline,fast"
        )
        val hits = vault.searchMemoriesFast("TAGORE")
        assertTrue(hits.isNotEmpty())
        assertEquals(id, hits.first().id)

        vault.recordMemoryAccess(id)
        val updated = vault.getAllMemories().first { it.id == id }
        assertEquals(1, updated.accessCount)
    }

    @Test
    fun defaultPreferences_haveAutoListenOnLaunchEnabled() {
        val prefs = UserPreferences()
        assertTrue(prefs.autoListenOnLaunch)
        assertTrue(UserPreferencesEntity().toDomain().autoListenOnLaunch)
    }

    @Test
    fun humanConversation_respondsInstantlyAndWarmlyOffline() = runBlocking {
        val prefs = UserPreferences(preferredName = "Moksh", honorific = "Sir")
        val checkIn = engine.processInput("Tagore kaise ho", prefs, null)
        assertTrue(checkIn.handledLocally)
        assertTrue(checkIn.replyText.contains("badhiya", ignoreCase = true))

        val thanks = engine.processInput("thank you", prefs, null)
        assertTrue(thanks.handledLocally)
        assertEquals(RingState.SUCCESS, thanks.nextRingState)
    }

    @Test
    fun maleAndFemaleVoice_defaultsAndVoiceCommandsWork() = runBlocking {
        val prefs = UserPreferences()
        assertEquals(com.example.domain.model.VoiceGender.MALE, prefs.voiceGender)
        assertTrue(prefs.voicePersonality.preferMale)

        val switchToFemale = engine.processInput("female voice lagao", prefs, null)
        assertTrue(switchToFemale.handledLocally)
        assertEquals(com.example.domain.model.VoiceGender.FEMALE, switchToFemale.updatedVoiceGender)

        val switchToMale = engine.processInput("male voice chalu karo", prefs, null)
        assertTrue(switchToMale.handledLocally)
        assertEquals(com.example.domain.model.VoiceGender.MALE, switchToMale.updatedVoiceGender)
    }

    @Test
    fun speechSanitizer_normalizesSymbolsAcronymsAndEmojisForClearPronunciation() {
        val cleaned = TagoreVoiceEngine.sanitizeForSpeech("**TAGORE** status: 23°C & 85% WiFi ✅")
        assertTrue(cleaned.contains("Tagore"))
        assertTrue(cleaned.contains("23 degrees Celsius"))
        assertTrue(cleaned.contains("85 percent"))
        assertTrue(cleaned.contains("Wi-Fi"))
        assertTrue(cleaned.contains("and"))
        assertTrue(!cleaned.contains("**"))
        assertTrue(!cleaned.contains("✅"))
    }

    @Test
    fun phoneControlCommands_parseAppsFlashlightVolumeMediaAlarmTimerAndSearch() = runBlocking {
        val prefs = UserPreferences()

        val appCmd = engine.processInput("WhatsApp kholo", prefs, null)
        assertEquals(LocalDeviceAction.LaunchApp("whatsapp"), appCmd.deviceAction)

        val torchOn = engine.processInput("flashlight on karo", prefs, null)
        assertEquals(LocalDeviceAction.SetFlashlight(true), torchOn.deviceAction)

        val torchOff = engine.processInput("torch band karo", prefs, null)
        assertEquals(LocalDeviceAction.SetFlashlight(false), torchOff.deviceAction)

        val muteCmd = engine.processInput("phone silent kar do", prefs, null)
        assertEquals(LocalDeviceAction.SetMute(true), muteCmd.deviceAction)

        val volPct = engine.processInput("volume 60 percent kar do", prefs, null)
        assertEquals(LocalDeviceAction.SetVolumePercent(60), volPct.deviceAction)

        val nextSong = engine.processInput("next song", prefs, null)
        assertEquals(LocalDeviceAction.MediaControl(KeyEvent.KEYCODE_MEDIA_NEXT), nextSong.deviceAction)

        val timerCmd = engine.processInput("set timer for 5 minutes", prefs, null)
        assertEquals(LocalDeviceAction.SetSystemTimer(300, "Tagore Timer"), timerCmd.deviceAction)

        val alarmCmd = engine.processInput("alarm lagao 6:30 am", prefs, null)
        assertEquals(LocalDeviceAction.SetSystemAlarm(6, 30, "Tagore Alarm"), alarmCmd.deviceAction)

        val dialCmd = engine.processInput("call 9876543210", prefs, null)
        assertEquals(LocalDeviceAction.OpenDialer("9876543210"), dialCmd.deviceAction)

        val ytCmd = engine.processInput("YouTube par lofi music search karo", prefs, null)
        assertTrue(ytCmd.deviceAction is LocalDeviceAction.WebOrMapSearch)
        assertEquals(SearchTarget.YOUTUBE, (ytCmd.deviceAction as LocalDeviceAction.WebOrMapSearch).target)

        val batteryCmd = engine.processInput("battery kitni hai", prefs, null)
        assertEquals(LocalDeviceAction.SpeakDeviceTelemetry, batteryCmd.deviceAction)
    }
}
