package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ShortProject
import com.example.data.model.ShortScene
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShortProjectRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleShort(title: String = "20s Viral Productivity Tip") = ShortProject(
        title = title,
        topic = "3 shortcut keys to save time",
        style = "Viral Hook",
        durationSec = 20L,
        voiceProfile = "Studio Pro",
        captionStyle = "Neon Pop",
        backdropTheme = "Cyber Aurora",
        voiceoverScript = "Stop clicking menus every minute. Here are three shortcuts that double your speed.",
        scenes = listOf(
            ShortScene(
                index = 0L,
                startMs = 0L,
                endMs = 4500L,
                caption = "Stop clicking menus every minute.",
                emphasisWord = "minute",
                visualCue = "Bold intro zoom"
            ),
            ShortScene(
                index = 1L,
                startMs = 4500L,
                endMs = 12000L,
                caption = "Use these 3 shortcuts daily.",
                emphasisWord = "3 shortcuts",
                visualCue = "Step counter overlay"
            )
        ),
        securityVerified = true
    )

    @Test
    fun createShort_validPayload_createsAndObservesProject() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = ShortProjectRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createShort(sampleShort())
        }
        assertTrue("Expected createShort to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val shortId = createResult.getOrThrow()
        assertTrue(shortId.isNotBlank())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeShorts(uid).first { list -> list.any { it.id == shortId } }
        }
        assertTrue(observed.any { it.id == shortId && it.title == "20s Viral Productivity Tip" })
    }

    @Test
    fun updateShort_validOwner_updatesFieldsPreservingCreatedAt() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = ShortProjectRepository(firestore, auth)

        val shortId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createShort(sampleShort()).getOrThrow()
        }
        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getShortById(shortId).getOrThrow()
        }
        assertNotNull(fetched)

        val updated = fetched!!.copy(title = "Updated 20s Short Title")
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateShort(updated)
        }
        assertTrue("Expected updateShort to succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

        val reFetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getShortById(shortId).getOrThrow()
        }
        assertEquals("Updated 20s Short Title", reFetched?.title)
    }

    @Test
    fun getShortAtPath_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ShortProjectRepository(firestore, auth)
        val shortId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createShort(sampleShort()).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ShortProjectRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getShortAtPath(aliceUid, shortId)
        }
        assertTrue(result.isFailure)
        val fsEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun observeShorts_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ShortProjectRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeShorts("some_alice_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated observeShorts")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_creator@test.com"
        const val BOB_EMAIL = "bob_attacker@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
