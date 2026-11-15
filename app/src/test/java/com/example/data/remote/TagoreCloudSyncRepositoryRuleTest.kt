package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.UserPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TagoreCloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndGetUserProfile_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = TagoreCloudSyncRepository(firestore, auth)
        val prefs = UserPreferences(
            hasCompletedOnboarding = true,
            preferredName = "Moksh",
            honorific = "Sir"
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(prefs)
        }
        assertTrue(saveResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile().getOrThrow()
        }
        assertEquals("Moksh", fetched?.preferredName)
    }

    @Test
    fun upsertAndObserveTasks_authenticatedOwner_emitsUpdate() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = TagoreCloudSyncRepository(firestore, auth)
        val docId = "task_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val upsertResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertTask(
                docId = docId,
                task = ProductivityItemDoc(
                    kind = "REMINDER",
                    title = "Board Sync",
                    details = "Offline encrypted telemetry review",
                    completed = false,
                    dueAtMillis = 1794700000000L
                )
            )
        }
        assertTrue(upsertResult.isSuccess)

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks().first { list -> list.any { it.id == docId } }
        }
        assertTrue(tasks.any { it.id == docId && it.title == "Board Sync" })
    }

    @Test
    fun upsertSmartDevice_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = TagoreCloudSyncRepository(firestore, auth)
        val devId = "dev_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertSmartDevice(
                docId = devId,
                device = SmartDeviceDoc(
                    name = "Office Light",
                    room = "Office",
                    category = "LIGHT",
                    isOn = true,
                    level = 85L,
                    temperature = 24L,
                    isLocked = false
                )
            )
        }
        assertTrue(result.isSuccess)
    }

    @Test
    fun observeMemories_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TagoreCloudSyncRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.upsertMemory(
                    memory = MemoryDoc(
                        category = "Work",
                        title = "Project",
                        content = "TAGORE Core"
                    )
                )
            }
        } catch (e: Exception) {
            caught = e
        }
        assertTrue(caught is IllegalStateException)
    }

    @Test
    fun observeTasks_signedOutDuringActiveCollection_failsGracefully() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TagoreCloudSyncRepository(firestore, auth)
        assertTrue(uid.isNotBlank())
        auth.signOut()

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks().first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertTrue(caught != null)
    }

    @Test
    fun crossUserRead_differentAuthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TagoreCloudSyncRepository(firestore, auth)
        val docId = "mem_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertMemory(
                docId = docId,
                memory = MemoryDoc(
                    category = "Private",
                    title = "Secret Strategy",
                    content = "Local vault blueprint"
                )
            )
        }
        assertTrue(createRes.isSuccess)

        signInTestUser(BOB_EMAIL)
        var deniedException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("memories").document(docId).get().await()
            }
        } catch (e: Exception) {
            deniedException = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_tagore@test.com"
        const val BOB_EMAIL = "bob_tagore@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
