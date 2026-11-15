package com.example.data.repository

import android.content.Context
import com.example.core.crypto.TagoreCryptoManager
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.EcosystemDeviceEntity
import com.example.data.local.EncryptedLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.ProductivityEntity
import com.example.data.local.SmartDeviceEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.TagoreDatabase
import com.example.data.local.UserPreferencesEntity
import com.example.data.remote.EcosystemDeviceDoc
import com.example.data.remote.MemoryDoc
import com.example.data.remote.ProductivityItemDoc
import com.example.data.remote.SmartDeviceDoc
import com.example.data.remote.TagoreCloudSyncRepository
import com.example.domain.model.ChatTurn
import com.example.domain.model.RingState
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Offline-first repository backed by Room (WAL mode + SQLite indices) + in-memory hot caches
 * + AES-256-GCM encrypted logs, with opportunistic synchronization to Firestore when online.
 */
class TagoreVaultRepository(
    private val db: TagoreDatabase,
    private val crypto: TagoreCryptoManager = TagoreCryptoManager()
) {
    constructor(context: Context) : this(TagoreDatabase.getInstance(context))

    val memories: Flow<List<MemoryEntity>> = db.memoryDao().observeMemories()
    val conversationHistory: Flow<List<ConversationMessageEntity>> = db.conversationDao().observeRecentMessages(60)
    val roomPreferences: Flow<UserPreferencesEntity?> = db.userPreferencesDao().observePreferences()
    val productivityItems: Flow<List<ProductivityEntity>> = db.productivityDao().observeItems()
    val smartDevices: Flow<List<SmartDeviceEntity>> = db.smartDeviceDao().observeDevices()
    val ecosystemDevices: Flow<List<EcosystemDeviceEntity>> = db.ecosystemDeviceDao().observeEcosystemDevices()
    val encryptedLogs: Flow<List<EncryptedLogEntity>> = db.encryptedLogDao().observeLogs()
    val pendingSyncCount: Flow<Int> = db.syncQueueDao().observePendingCount()

    @Volatile
    private var hotMemoriesCache: List<MemoryEntity>? = null

    @Volatile
    private var hotSmartDevicesCache: List<SmartDeviceEntity>? = null

    suspend fun ensureInitialized() {
        TagoreDatabase.seedHardwareDefaultsIfEmpty(db)
        warmHotCaches()
    }

    suspend fun warmHotCaches() {
        hotMemoriesCache = db.memoryDao().getAllMemories()
        hotSmartDevicesCache = db.smartDeviceDao().getAllDevices()
    }

    // --- Offline Conversation History (Room) ---

    suspend fun saveConversationTurn(
        turn: ChatTurn,
        ringState: RingState = RingState.IDLE,
        latencyMs: Long = 0L,
        sessionId: String = "default"
    ): Long {
        return db.conversationDao().insertMessage(
            ConversationMessageEntity(
                messageUid = turn.id,
                sessionId = sessionId,
                author = turn.author.name,
                content = turn.text,
                timestampMillis = turn.timestampMillis,
                isOfflineResponse = turn.isOfflineResponse,
                ringState = ringState.name,
                deepKnowledgeTopic = turn.deepKnowledgeTopic,
                latencyMs = latencyMs
            )
        )
    }

    suspend fun getRecentConversationSnapshot(limit: Int = 30): List<ConversationMessageEntity> {
        return db.conversationDao().getRecentMessagesSnapshot(limit)
    }

    suspend fun searchConversationHistory(query: String, limit: Int = 25): List<ConversationMessageEntity> {
        return db.conversationDao().searchMessages(query.trim(), limit)
    }

    suspend fun clearConversationHistory() {
        db.conversationDao().clearAllHistory()
    }

    // --- User Preferences (Room) ---

    suspend fun getRoomUserPreferences(): UserPreferences? {
        return db.userPreferencesDao().getPreferences()?.toDomain()
    }

    suspend fun saveRoomUserPreferences(prefs: UserPreferences) {
        db.userPreferencesDao().upsertPreferences(UserPreferencesEntity.fromDomain(prefs))
    }

    // --- AES-256-GCM Encrypted Audit Logs ---

    suspend fun logSecureEvent(
        category: String,
        actionSummary: String,
        detailedPayload: String,
        isOffline: Boolean
    ) {
        val encrypted = crypto.encrypt(detailedPayload)
        db.encryptedLogDao().insertLog(
            EncryptedLogEntity(
                timestampMillis = System.currentTimeMillis(),
                category = category,
                actionSummary = actionSummary,
                encryptedPayloadBase64 = encrypted.cipherTextBase64,
                ivBase64 = encrypted.ivBase64,
                isOfflineEvent = isOffline
            )
        )
    }

    fun decryptLog(log: EncryptedLogEntity): String {
        return crypto.decrypt(log.encryptedPayloadBase64, log.ivBase64)
    }

    suspend fun clearLogs() {
        db.encryptedLogDao().clearAllLogs()
    }

    // --- Personal Memories (Room + Hot Cache) ---

    suspend fun saveMemory(
        category: String,
        title: String,
        content: String,
        pinned: Boolean = false,
        tags: String = "",
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ): Long {
        val remoteId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val entity = MemoryEntity(
            remoteId = remoteId,
            category = category.ifBlank { "General" },
            title = title.ifBlank { "Saved Memory" },
            content = content,
            tags = tags,
            pinned = pinned,
            accessCount = 0,
            updatedAtMillis = System.currentTimeMillis(),
            pendingSync = true
        )
        val localId = db.memoryDao().insertMemory(entity)
        hotMemoriesCache = db.memoryDao().getAllMemories()

        val payload = JSONObject().apply {
            put("category", entity.category)
            put("title", entity.title)
            put("content", entity.content)
            put("pinned", entity.pinned)
        }.toString()

        if (isOnline && cloudRepo != null) {
            val res = cloudRepo.upsertMemory(
                remoteId,
                MemoryDoc(
                    category = entity.category,
                    title = entity.title,
                    content = entity.content,
                    pinned = entity.pinned
                )
            )
            if (res.isSuccess) {
                db.memoryDao().updateMemory(entity.copy(id = localId, pendingSync = false))
                hotMemoriesCache = db.memoryDao().getAllMemories()
            } else {
                enqueueSync("memories", remoteId, "UPSERT", payload)
            }
        } else {
            enqueueSync("memories", remoteId, "UPSERT", payload)
        }
        return localId
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return db.memoryDao().searchMemories(query)
    }

    suspend fun searchMemoriesFast(query: String, limit: Int = 15): List<MemoryEntity> {
        val q = query.trim().lowercase(Locale.ROOT)
        val cached = hotMemoriesCache
        if (cached != null && q.isNotEmpty()) {
            val hits = cached.filter { m ->
                m.title.lowercase(Locale.ROOT).contains(q) ||
                    m.content.lowercase(Locale.ROOT).contains(q) ||
                    m.category.lowercase(Locale.ROOT).contains(q) ||
                    m.tags.lowercase(Locale.ROOT).contains(q)
            }.take(limit)
            if (hits.isNotEmpty()) {
                return hits
            }
        }
        return db.memoryDao().searchMemoriesFast(query, limit)
    }

    suspend fun recordMemoryAccess(id: Long) {
        db.memoryDao().incrementAccessCount(id)
        hotMemoriesCache = db.memoryDao().getAllMemories()
    }

    suspend fun getAllMemories(): List<MemoryEntity> {
        return hotMemoriesCache ?: db.memoryDao().getAllMemories().also { hotMemoriesCache = it }
    }

    suspend fun deleteMemory(
        entity: MemoryEntity,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ) {
        db.memoryDao().deleteMemoryById(entity.id)
        hotMemoriesCache = db.memoryDao().getAllMemories()
        if (entity.remoteId.isNotBlank()) {
            if (isOnline && cloudRepo != null) {
                val res = cloudRepo.deleteMemory(entity.remoteId)
                if (res.isFailure) {
                    enqueueSync("memories", entity.remoteId, "DELETE", "{}")
                }
            } else {
                enqueueSync("memories", entity.remoteId, "DELETE", "{}")
            }
        }
    }

    suspend fun exportMemoriesJson(): String {
        val all = getAllMemories()
        val arr = JSONArray()
        all.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("category", m.category)
                    put("title", m.title)
                    put("content", m.content)
                    put("pinned", m.pinned)
                    put("tags", m.tags)
                }
            )
        }
        return JSONObject().apply {
            put("tagoreMemoryVersion", 2)
            put("exportedAtMillis", System.currentTimeMillis())
            put("memories", arr)
        }.toString(2)
    }

    suspend fun importMemoriesJson(jsonText: String): Int {
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("memories")
        var count = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            saveMemory(
                category = obj.optString("category", "Imported"),
                title = obj.optString("title", "Memory ${i + 1}"),
                content = obj.optString("content", ""),
                pinned = obj.optBoolean("pinned", false),
                tags = obj.optString("tags", "")
            )
            count++
        }
        return count
    }

    // --- Productivity ---

    suspend fun addProductivityItem(
        kind: String,
        title: String,
        details: String = "",
        dueAtMillis: Long = 0L,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ): Long {
        val remoteId = "task_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val entity = ProductivityEntity(
            remoteId = remoteId,
            kind = kind,
            title = title,
            details = details,
            completed = false,
            dueAtMillis = dueAtMillis,
            updatedAtMillis = System.currentTimeMillis(),
            pendingSync = true
        )
        val localId = db.productivityDao().insertItem(entity)
        val payload = JSONObject().apply {
            put("kind", entity.kind)
            put("title", entity.title)
            put("details", entity.details)
            put("completed", entity.completed)
            put("dueAtMillis", entity.dueAtMillis)
        }.toString()

        if (isOnline && cloudRepo != null) {
            val res = cloudRepo.upsertTask(
                remoteId,
                ProductivityItemDoc(
                    kind = entity.kind,
                    title = entity.title,
                    details = entity.details,
                    completed = entity.completed,
                    dueAtMillis = entity.dueAtMillis
                )
            )
            if (res.isSuccess) {
                db.productivityDao().updateItem(entity.copy(id = localId, pendingSync = false))
            } else {
                enqueueSync("tasks", remoteId, "UPSERT", payload)
            }
        } else {
            enqueueSync("tasks", remoteId, "UPSERT", payload)
        }
        return localId
    }

    suspend fun toggleProductivityCompleted(
        item: ProductivityEntity,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ) {
        val updated = item.copy(
            completed = !item.completed,
            updatedAtMillis = System.currentTimeMillis(),
            pendingSync = true
        )
        db.productivityDao().updateItem(updated)
        if (updated.remoteId.isNotBlank()) {
            val payload = JSONObject().apply {
                put("kind", updated.kind)
                put("title", updated.title)
                put("details", updated.details)
                put("completed", updated.completed)
                put("dueAtMillis", updated.dueAtMillis)
            }.toString()
            if (isOnline && cloudRepo != null) {
                val res = cloudRepo.upsertTask(
                    updated.remoteId,
                    ProductivityItemDoc(
                        kind = updated.kind,
                        title = updated.title,
                        details = updated.details,
                        completed = updated.completed,
                        dueAtMillis = updated.dueAtMillis
                    )
                )
                if (res.isSuccess) {
                    db.productivityDao().updateItem(updated.copy(pendingSync = false))
                } else {
                    enqueueSync("tasks", updated.remoteId, "UPSERT", payload)
                }
            } else {
                enqueueSync("tasks", updated.remoteId, "UPSERT", payload)
            }
        }
    }

    suspend fun deleteProductivityItem(id: Long) {
        db.productivityDao().deleteItemById(id)
    }

    // --- Smart Home & Ecosystem ---

    suspend fun getAllSmartDevices(): List<SmartDeviceEntity> {
        return hotSmartDevicesCache ?: db.smartDeviceDao().getAllDevices().also { hotSmartDevicesCache = it }
    }

    suspend fun updateSmartDevice(
        device: SmartDeviceEntity,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ) {
        val updated = device.copy(
            updatedAtMillis = System.currentTimeMillis(),
            pendingSync = true
        )
        db.smartDeviceDao().updateDevice(updated)
        hotSmartDevicesCache = db.smartDeviceDao().getAllDevices()

        val docId = updated.remoteId.ifBlank { "dev_${updated.id}" }
        val payload = JSONObject().apply {
            put("name", updated.name)
            put("room", updated.room)
            put("category", updated.category)
            put("isOn", updated.isOn)
            put("level", updated.level)
            put("temperature", updated.temperature)
            put("isLocked", updated.isLocked)
        }.toString()

        if (isOnline && cloudRepo != null) {
            val res = cloudRepo.upsertSmartDevice(
                docId,
                SmartDeviceDoc(
                    name = updated.name,
                    room = updated.room,
                    category = updated.category,
                    isOn = updated.isOn,
                    level = updated.level.toLong(),
                    temperature = updated.temperature.toLong(),
                    isLocked = updated.isLocked
                )
            )
            if (res.isSuccess) {
                db.smartDeviceDao().updateDevice(updated.copy(remoteId = docId, pendingSync = false))
                hotSmartDevicesCache = db.smartDeviceDao().getAllDevices()
            } else {
                enqueueSync("smart_devices", docId, "UPSERT", payload)
            }
        } else {
            enqueueSync("smart_devices", docId, "UPSERT", payload)
        }
    }

    suspend fun broadcastEcosystemCommand(
        targetFormFactor: String,
        commandSummary: String,
        cloudRepo: TagoreCloudSyncRepository? = null,
        isOnline: Boolean = false
    ) {
        val devices = db.ecosystemDeviceDao().getAllEcosystemDevices()
        devices.filter { targetFormFactor == "ALL" || it.formFactor == targetFormFactor }.forEach { dev ->
            val updated = dev.copy(
                status = "ONLINE",
                lastCommand = commandSummary.take(280),
                updatedAtMillis = System.currentTimeMillis()
            )
            db.ecosystemDeviceDao().updateDevice(updated)
            if (isOnline && cloudRepo != null && updated.remoteId.isNotBlank()) {
                cloudRepo.upsertEcosystemDevice(
                    updated.remoteId,
                    EcosystemDeviceDoc(
                        name = updated.name,
                        formFactor = updated.formFactor,
                        status = updated.status,
                        batteryPercent = updated.batteryPercent.toLong(),
                        lastCommand = updated.lastCommand
                    )
                )
            }
        }
    }

    suspend fun flushPendingSyncQueue(
        cloudRepo: TagoreCloudSyncRepository,
        prefs: UserPreferences
    ): Int {
        var syncedCount = 0
        cloudRepo.upsertUserProfile(prefs)
        val queue = db.syncQueueDao().getPendingQueue()
        for (item in queue) {
            val result = runCatching {
                when (item.collectionName) {
                    "memories" -> {
                        if (item.operationType == "DELETE") {
                            cloudRepo.deleteMemory(item.docId)
                        } else {
                            val obj = JSONObject(item.payloadJson)
                            cloudRepo.upsertMemory(
                                item.docId,
                                MemoryDoc(
                                    category = obj.optString("category", "General"),
                                    title = obj.optString("title", "Memory"),
                                    content = obj.optString("content", "-"),
                                    pinned = obj.optBoolean("pinned", false)
                                )
                            )
                        }
                    }
                    "tasks" -> {
                        val obj = JSONObject(item.payloadJson)
                        cloudRepo.upsertTask(
                            item.docId,
                            ProductivityItemDoc(
                                kind = obj.optString("kind", "NOTE"),
                                title = obj.optString("title", "Task"),
                                details = obj.optString("details", ""),
                                completed = obj.optBoolean("completed", false),
                                dueAtMillis = obj.optLong("dueAtMillis", 0L)
                            )
                        )
                    }
                    "smart_devices" -> {
                        val obj = JSONObject(item.payloadJson)
                        cloudRepo.upsertSmartDevice(
                            item.docId,
                            SmartDeviceDoc(
                                name = obj.optString("name", "Device"),
                                room = obj.optString("room", "Room"),
                                category = obj.optString("category", "LIGHT"),
                                isOn = obj.optBoolean("isOn", false),
                                level = obj.optLong("level", 75L),
                                temperature = obj.optLong("temperature", 24L),
                                isLocked = obj.optBoolean("isLocked", true)
                            )
                        )
                    }
                    else -> Result.success(Unit)
                }
            }.getOrElse { Result.failure(it) }

            if (result.isSuccess) {
                db.syncQueueDao().remove(item.id)
                syncedCount++
            }
        }
        return syncedCount
    }

    private suspend fun enqueueSync(
        collectionName: String,
        docId: String,
        operationType: String,
        payloadJson: String
    ) {
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                collectionName = collectionName,
                docId = docId,
                operationType = operationType,
                payloadJson = payloadJson
            )
        )
    }
}
