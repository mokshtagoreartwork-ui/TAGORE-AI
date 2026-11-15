package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TagoreCloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun upsertUserProfile(prefs: UserPreferences): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "preferredName" to prefs.preferredName.ifBlank { "Moksh" }.take(60),
                    "honorific" to prefs.honorific.ifBlank { "Sir" }.take(30),
                    "voicePersonality" to prefs.voicePersonality.name,
                    "voicePitch" to prefs.voicePitch.toDouble().coerceIn(0.5, 2.0),
                    "voiceSpeed" to prefs.voiceSpeed.toDouble().coerceIn(0.5, 2.0),
                    "voiceTone" to prefs.voiceTone.name.take(40),
                    "responseLength" to prefs.responseLength.name,
                    "languageMode" to prefs.languageMode.name,
                    "darkMode" to prefs.darkMode,
                    "ringBrightness" to prefs.ringBrightness.toDouble().coerceIn(0.2, 1.0),
                    "animationSpeed" to prefs.animationSpeed.toDouble().coerceIn(0.4, 2.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "preferredName" to prefs.preferredName.ifBlank { "Moksh" }.take(60),
                    "honorific" to prefs.honorific.ifBlank { "Sir" }.take(30),
                    "voicePersonality" to prefs.voicePersonality.name,
                    "voicePitch" to prefs.voicePitch.toDouble().coerceIn(0.5, 2.0),
                    "voiceSpeed" to prefs.voiceSpeed.toDouble().coerceIn(0.5, 2.0),
                    "voiceTone" to prefs.voiceTone.name.take(40),
                    "responseLength" to prefs.responseLength.name,
                    "languageMode" to prefs.languageMode.name,
                    "darkMode" to prefs.darkMode,
                    "ringBrightness" to prefs.ringBrightness.toDouble().coerceIn(0.2, 1.0),
                    "animationSpeed" to prefs.animationSpeed.toDouble().coerceIn(0.4, 2.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(): Result<UserProfileDoc?> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            val doc = snapshot.toObject(
                UserProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertMemory(docId: String? = null, memory: MemoryDoc): Result<String> {
        val uid = requireUserId()
        val id = (docId?.takeIf { it.isNotBlank() } ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        val docRef = db.collection("users").document(uid).collection("memories").document(id)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "category" to memory.category.ifBlank { "General" }.take(40),
                    "title" to memory.title.ifBlank { "Memory" }.take(120),
                    "content" to memory.content.ifBlank { "-" }.take(2000),
                    "pinned" to memory.pinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "category" to memory.category.ifBlank { "General" }.take(40),
                    "title" to memory.title.ifBlank { "Memory" }.take(120),
                    "content" to memory.content.ifBlank { "-" }.take(2000),
                    "pinned" to memory.pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeMemories(): Flow<List<MemoryDoc>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("memories")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun deleteMemory(docId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertTask(docId: String? = null, task: ProductivityItemDoc): Result<String> {
        val uid = requireUserId()
        val id = (docId?.takeIf { it.isNotBlank() } ?: "task_${UUID.randomUUID().toString().replace("-", "").take(12)}")
        val docRef = db.collection("users").document(uid).collection("tasks").document(id)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "kind" to task.kind,
                    "title" to task.title.ifBlank { "Task" }.take(160),
                    "details" to task.details.take(3000),
                    "completed" to task.completed,
                    "dueAtMillis" to task.dueAtMillis.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "kind" to task.kind,
                    "title" to task.title.ifBlank { "Task" }.take(160),
                    "details" to task.details.take(3000),
                    "completed" to task.completed,
                    "dueAtMillis" to task.dueAtMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeTasks(): Flow<List<ProductivityItemDoc>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProductivityItemDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSmartDevice(docId: String, device: SmartDeviceDoc): Result<String> {
        val uid = requireUserId()
        val id = docId.ifBlank { "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val docRef = db.collection("users").document(uid).collection("smart_devices").document(id)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "name" to device.name.ifBlank { "Device" }.take(80),
                    "room" to device.room.ifBlank { "Room" }.take(60),
                    "category" to device.category,
                    "isOn" to device.isOn,
                    "level" to device.level.coerceIn(0L, 100L),
                    "temperature" to device.temperature.coerceIn(16L, 32L),
                    "isLocked" to device.isLocked,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "name" to device.name.ifBlank { "Device" }.take(80),
                    "room" to device.room.ifBlank { "Room" }.take(60),
                    "category" to device.category,
                    "isOn" to device.isOn,
                    "level" to device.level.coerceIn(0L, 100L),
                    "temperature" to device.temperature.coerceIn(16L, 32L),
                    "isLocked" to device.isLocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeSmartDevices(): Flow<List<SmartDeviceDoc>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("smart_devices")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SmartDeviceDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertEcosystemDevice(docId: String, device: EcosystemDeviceDoc): Result<String> {
        val uid = requireUserId()
        val id = docId.ifBlank { "eco_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val docRef = db.collection("users").document(uid).collection("ecosystem_devices").document(id)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "name" to device.name.ifBlank { "Device" }.take(80),
                    "formFactor" to device.formFactor,
                    "status" to device.status,
                    "batteryPercent" to device.batteryPercent.coerceIn(0L, 100L),
                    "lastCommand" to device.lastCommand.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "name" to device.name.ifBlank { "Device" }.take(80),
                    "formFactor" to device.formFactor,
                    "status" to device.status,
                    "batteryPercent" to device.batteryPercent.coerceIn(0L, 100L),
                    "lastCommand" to device.lastCommand.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
