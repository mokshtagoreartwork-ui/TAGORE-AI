package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE pinned = 1 ORDER BY updatedAtMillis DESC")
    fun observePinnedMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAtMillis DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY pinned DESC, updatedAtMillis DESC")
    suspend fun getMemoriesByCategory(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY pinned DESC, accessCount DESC, updatedAtMillis DESC")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY pinned DESC, accessCount DESC, updatedAtMillis DESC LIMIT :limit")
    suspend fun searchMemoriesFast(query: String, limit: Int = 15): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entity: MemoryEntity): Long

    @Update
    suspend fun updateMemory(entity: MemoryEntity)

    @Query("UPDATE memories SET accessCount = accessCount + 1, updatedAtMillis = :nowMillis WHERE id = :id")
    suspend fun incrementAccessCount(id: Long, nowMillis: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)
}

@Dao
interface ConversationDao {
    @Query(
        "SELECT * FROM (SELECT * FROM conversation_history ORDER BY timestampMillis DESC, id DESC LIMIT :limit) " +
            "ORDER BY timestampMillis ASC, id ASC"
    )
    fun observeRecentMessages(limit: Int = 50): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_history WHERE sessionId = :sessionId ORDER BY timestampMillis ASC, id ASC")
    fun observeSessionMessages(sessionId: String): Flow<List<ConversationMessageEntity>>

    @Query(
        "SELECT * FROM (SELECT * FROM conversation_history ORDER BY timestampMillis DESC, id DESC LIMIT :limit) " +
            "ORDER BY timestampMillis ASC, id ASC"
    )
    suspend fun getRecentMessagesSnapshot(limit: Int = 30): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversation_history WHERE content LIKE '%' || :query || '%' ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 25): List<ConversationMessageEntity>

    @Query("SELECT COUNT(*) FROM conversation_history")
    suspend fun getTotalMessageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ConversationMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(entities: List<ConversationMessageEntity>)

    @Query("DELETE FROM conversation_history WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM conversation_history")
    suspend fun clearAllHistory()
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: UserPreferencesEntity)

    @Query(
        "UPDATE user_preferences SET voicePersonality = :personality, voicePitch = :pitch, " +
            "voiceSpeed = :speed, voiceTone = :tone, updatedAtMillis = :updatedAtMillis WHERE id = 1"
    )
    suspend fun updateVoiceSettings(
        personality: String,
        pitch: Float,
        speed: Float,
        tone: String,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE user_preferences SET darkMode = :darkMode, ringBrightness = :ringBrightness, " +
            "animationSpeed = :animationSpeed, updatedAtMillis = :updatedAtMillis WHERE id = 1"
    )
    suspend fun updateThemeAndRing(
        darkMode: Boolean,
        ringBrightness: Float,
        animationSpeed: Float,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM user_preferences")
    suspend fun clearPreferences()
}

@Dao
interface ProductivityDao {
    @Query("SELECT * FROM productivity_items ORDER BY completed ASC, updatedAtMillis DESC")
    fun observeItems(): Flow<List<ProductivityEntity>>

    @Query("SELECT * FROM productivity_items ORDER BY updatedAtMillis DESC")
    suspend fun getAllItems(): List<ProductivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(entity: ProductivityEntity): Long

    @Update
    suspend fun updateItem(entity: ProductivityEntity)

    @Query("DELETE FROM productivity_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
}

@Dao
interface SmartDeviceDao {
    @Query("SELECT * FROM smart_devices ORDER BY room ASC, name ASC")
    fun observeDevices(): Flow<List<SmartDeviceEntity>>

    @Query("SELECT * FROM smart_devices")
    suspend fun getAllDevices(): List<SmartDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(entity: SmartDeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SmartDeviceEntity>)

    @Update
    suspend fun updateDevice(entity: SmartDeviceEntity)
}

@Dao
interface EcosystemDeviceDao {
    @Query("SELECT * FROM ecosystem_devices ORDER BY id ASC")
    fun observeEcosystemDevices(): Flow<List<EcosystemDeviceEntity>>

    @Query("SELECT * FROM ecosystem_devices")
    suspend fun getAllEcosystemDevices(): List<EcosystemDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EcosystemDeviceEntity>)

    @Update
    suspend fun updateDevice(entity: EcosystemDeviceEntity)
}

@Dao
interface EncryptedLogDao {
    @Query("SELECT * FROM encrypted_logs ORDER BY timestampMillis DESC LIMIT 200")
    fun observeLogs(): Flow<List<EncryptedLogEntity>>

    @Query("SELECT * FROM encrypted_logs ORDER BY timestampMillis DESC")
    suspend fun getAllLogs(): List<EncryptedLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: EncryptedLogEntity): Long

    @Query("DELETE FROM encrypted_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAtMillis ASC")
    suspend fun getPendingQueue(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}
