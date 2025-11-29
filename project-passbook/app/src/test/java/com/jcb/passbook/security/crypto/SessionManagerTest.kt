package com.jcb.passbook.security.crypto

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import io.mockk.*
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class SessionManagerTest {

    private lateinit var masterKeyManager: MasterKeyManager
    private lateinit var auditLogger: AuditLogger
    private lateinit var secureMemoryUtils: SecureMemoryUtils
    private lateinit var sessionManager: SessionManager
    private lateinit var mockActivity: FragmentActivity

    @Before
    fun setup() {
        // Mock dependencies - using relaxed mode for automatic type handling
        masterKeyManager = mockk(relaxed = true)
        auditLogger = mockk(relaxed = true)
        secureMemoryUtils = mockk(relaxed = true)
        mockActivity = mockk(relaxed = true)

        // Mock SecureMemoryUtils with explicit types for matchers
        val mockAMK = ByteArray(32) { it.toByte() }
        every { secureMemoryUtils.generateSecureRandom(any<Int>()) } returns ByteArray(16) { it.toByte() }
        every { secureMemoryUtils.secureCopy(any<ByteArray>()) } answers { (args[0] as ByteArray).copyOf() }
        every { secureMemoryUtils.secureWipe(any<ByteArray>()) } just Runs

        // Mock MasterKeyManager with explicit types for matchers
        coEvery { masterKeyManager.unwrapAMK(any<FragmentActivity>()) } returns mockAMK

        // Mock AuditLogger - relaxed mode handles all types automatically
        coEvery { auditLogger.logSecurityEvent(any(), any(), any()) } just Runs
        coEvery { auditLogger.logAuthentication(any(), any(), any(), any()) } just Runs
        coEvery { auditLogger.logUserAction(any(), any(), any(), any(), any(), any(), any(), any(), any()) } just Runs

        sessionManager = SessionManager(
            masterKeyManager,
            { auditLogger },
            secureMemoryUtils
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test startSession creates new session successfully`() = runTest {
        // When
        val result = sessionManager.startSession(mockActivity)

        // Then
        assertTrue("Session should start successfully", result is SessionManager.SessionResult.Success)
        assertTrue("Session should be active", sessionManager.isSessionActive())
        assertNotNull("Session ID should be set", sessionManager.getCurrentSessionId())
        coVerify { masterKeyManager.unwrapAMK(mockActivity) }
        coVerify {
            auditLogger.logUserAction(
                any(),
                any(),
                eq(AuditEventType.LOGIN),
                any(),
                any(),
                any(),
                eq(AuditOutcome.SUCCESS),
                any(),
                any()
            )
        }
    }

    @Test
    fun `test startSession fails when already active`() = runTest {
        // Given: Start a session first
        sessionManager.startSession(mockActivity)

        // When: Try to start another session
        val result = sessionManager.startSession(mockActivity)

        // Then
        assertTrue("Should return AlreadyActive", result is SessionManager.SessionResult.AlreadyActive)
    }

    @Test
    fun `test startSession fails when AMK unwrap fails`() = runTest {
        // Given: AMK unwrap returns null
        coEvery { masterKeyManager.unwrapAMK(any()) } returns null

        // When
        val result = sessionManager.startSession(mockActivity)

        // Then
        assertTrue("Should return AuthenticationFailed", result is SessionManager.SessionResult.AuthenticationFailed)
        assertFalse("Session should not be active", sessionManager.isSessionActive())
    }

    @Test
    fun `test getEphemeralSessionKey returns key when session active`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)

        // When
        val esk = sessionManager.getEphemeralSessionKey()

        // Then
        assertNotNull("ESK should not be null", esk)
        assertTrue("ESK should be SecretKeySpec", esk is SecretKeySpec)
        assertEquals("Key algorithm should be AES", "AES", esk?.algorithm)
    }

    @Test
    fun `test getEphemeralSessionKey returns null when session inactive`() {
        // Given: No active session

        // When
        val esk = sessionManager.getEphemeralSessionKey()

        // Then
        assertNull("ESK should be null when no session", esk)
    }

    @Test
    fun `test getApplicationMasterKey returns copy of AMK`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)

        // When
        val amk = sessionManager.getApplicationMasterKey()

        // Then
        assertNotNull("AMK should not be null", amk)
        assertEquals("AMK should be 32 bytes", 32, amk?.size)
        verify { secureMemoryUtils.secureCopy(any<ByteArray>()) }
    }

    @Test
    fun `test endSession clears sensitive data`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)
        val sessionId = sessionManager.getCurrentSessionId()
        assertNotNull("Session ID should not be null after starting a session", sessionId)


        // When
        sessionManager.endSession("Manual")

        // Then
        assertFalse("Session should not be active", sessionManager.isSessionActive())
        assertNull("Session ID should be null", sessionManager.getCurrentSessionId())
        assertNull("ESK should be null", sessionManager.getEphemeralSessionKey())
        verify(atLeast = 2) { secureMemoryUtils.secureWipe(any<ByteArray>()) }
        coVerify {
            auditLogger.logUserAction(
                any(),
                any(),
                eq(AuditEventType.LOGOUT),
                any(),
                any(),
                eq(sessionId!!), // FIX: Use non-null assertion for nullable type
                eq(AuditOutcome.SUCCESS),
                any(),
                any()
            )
        }
    }

    @Test
    fun `test endSession triggers logout callback on manual logout`() = runTest {
        // Given
        var callbackTriggered = false
        sessionManager.setOnLogoutCallback {
            callbackTriggered = true
        }
        sessionManager.startSession(mockActivity)

        // When
        sessionManager.endSession("Manual")
        advanceUntilIdle() // Ensure callback coroutine completes

        // Then
        assertTrue("Logout callback should be triggered", callbackTriggered)
    }

    @Test
    fun `test updateLastActivity extends session timeout`() = runTest {
        mockkStatic(System::class)
        try {
            // Given: Active session with a known start time
            every { System.currentTimeMillis() } returns 1000L
            sessionManager.startSession(mockActivity)
            val initialDuration = sessionManager.getSessionDuration()

            // When: Time passes and activity is updated
            every { System.currentTimeMillis() } returns 1100L // Simulate 100ms passing
            sessionManager.updateLastActivity()

            // Then: The session duration should reflect the new time
            val newDuration = sessionManager.getSessionDuration()
            assertTrue("Duration should have increased", newDuration > initialDuration)
            assertEquals("Duration should be exactly 100ms", 100L, newDuration)
        } finally {
            unmockkStatic(System::class) // Ensure static mock is cleared
        }
    }

    @Test
    fun `test getSessionDuration returns zero when no session`() {
        // When
        val duration = sessionManager.getSessionDuration()

        // Then
        assertEquals("Duration should be zero", 0L, duration)
    }

    @Test
    fun `test renewSession ends and starts new session`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)
        val originalSessionId = sessionManager.getCurrentSessionId()

        // When
        val result = sessionManager.renewSession(mockActivity)

        // Then
        assertTrue("Renewal should succeed", result is SessionManager.SessionResult.Success)
        val newSessionId = sessionManager.getCurrentSessionId()
        assertNotEquals("Session ID should be different", originalSessionId, newSessionId)
        coVerify(exactly = 2) { masterKeyManager.unwrapAMK(mockActivity) }
    }

    @Test
    fun `test isSessionActive returns correct state`() = runTest {
        // Initially no session
        assertFalse("Should be inactive initially", sessionManager.isSessionActive())

        // After starting session
        sessionManager.startSession(mockActivity)
        assertTrue("Should be active after start", sessionManager.isSessionActive())

        // After ending session
        sessionManager.endSession()
        assertFalse("Should be inactive after end", sessionManager.isSessionActive())
    }

    @Test
    fun `test lifecycle onStop ends session`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)
        val lifecycleOwner = mockk<LifecycleOwner>(relaxed = true)

        // When
        sessionManager.onStop(lifecycleOwner)
        // Ensure any launched coroutines within the test scope complete
        advanceUntilIdle()

        // Then
        assertFalse("Session should be ended", sessionManager.isSessionActive())
    }

    @Test
    fun `test setOnLogoutCallback sets callback successfully`() = runTest {
        // Given
        var callbackExecuted = false
        val callback: suspend () -> Unit = {
            callbackExecuted = true
        }

        // When
        sessionManager.setOnLogoutCallback(callback)
        sessionManager.startSession(mockActivity)
        sessionManager.endSession("Manual")
        advanceUntilIdle() // Ensure callback coroutine completes

        // Then - callback is executed
        assertTrue("Callback should be executed", callbackExecuted)
    }

    @Test
    fun `test lifecycle onDestroy ends session and cancels scope`() = runTest {
        // Given: Active session
        sessionManager.startSession(mockActivity)
        val lifecycleOwner = mockk<LifecycleOwner>(relaxed = true)

        // When
        sessionManager.onDestroy(lifecycleOwner)
        // Ensure any launched coroutines for cleanup are completed
        advanceUntilIdle()

        // Then
        assertFalse("Session should be ended", sessionManager.isSessionActive())
    }

    @Test
    fun `test getCurrentSessionId returns null when no session`() {
        // When
        val sessionId = sessionManager.getCurrentSessionId()

        // Then
        assertNull("Session ID should be null", sessionId)
    }

    @Test
    fun `test getApplicationMasterKey returns null when session inactive`() {
        // When
        val amk = sessionManager.getApplicationMasterKey()

        // Then
        assertNull("AMK should be null when no session", amk)
    }

    @Test
    fun `test endSession with user logout reason triggers callback`() = runTest {
        // Given
        var callbackExecuted = false
        sessionManager.setOnLogoutCallback {
            callbackExecuted = true
        }
        sessionManager.startSession(mockActivity)

        // When
        sessionManager.endSession("User logout")
        advanceUntilIdle() // Ensure callback coroutine completes

        // Then
        assertTrue("Logout callback should be triggered", callbackExecuted)
    }

    @Test
    fun `test endSession with timeout reason does not trigger callback`() = runTest {
        // Given
        var callbackExecuted = false
        sessionManager.setOnLogoutCallback {
            callbackExecuted = true
        }
        sessionManager.startSession(mockActivity)

        // When
        sessionManager.endSession("Inactivity timeout")
        advanceUntilIdle() // Ensure session-ending coroutine completes

        // Then
        assertFalse("Logout callback should NOT be triggered for timeout", callbackExecuted)
    }
}