package com.jcb.passbook.security.crypto

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyStore

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseKeyManagerTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var secureMemoryUtils: SecureMemoryUtils
    private lateinit var databaseKeyManager: DatabaseKeyManager
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        // Mock Android dependencies
        mockkStatic(KeyStore::class)
        mockkStatic(android.util.Base64::class)

        // Setup mocks with relaxed mode
        context = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        secureMemoryUtils = mockk(relaxed = true)
        mockSharedPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        // Default to an active session for the "happy path"
        every { sessionManager.isSessionActive() } returns true

        // Mock KeyStore
        val mockKeyStore = mockk<KeyStore>(relaxed = true)
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } just Runs
        every { mockKeyStore.containsAlias(any<String>()) } returns false

        // Mock SharedPreferences
        every { context.getSharedPreferences(any<String>(), any<Int>()) } returns mockSharedPrefs
        every { mockSharedPrefs.edit() } returns mockEditor

        // FIX: (Line 52) Use or(isNull(), any<String>()) to match nullable String?
        every { mockEditor.putString(any<String>(), or(isNull(), any<String>())) } returns mockEditor
        every { mockEditor.putBoolean(any<String>(), any<Boolean>()) } returns mockEditor
        every { mockEditor.remove(any<String>()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.apply() } just Runs
        every { mockEditor.commit() } returns true

        // FIX: (Line 58) Use or(isNull(), any<String>()) to match nullable String?
        every { mockSharedPrefs.getString(any<String>(), or(isNull(), any<String>())) } returns null
        every { mockSharedPrefs.getBoolean(any<String>(), any<Boolean>()) } returns false

        // Mock SecureMemoryUtils
        val mockRandomBytes = ByteArray(32) { it.toByte() }
        every { secureMemoryUtils.generateSecureRandom(any<Int>()) } returns mockRandomBytes
        every { secureMemoryUtils.secureCopy(any<ByteArray>()) } answers { (args[0] as ByteArray).copyOf() }

        // FIX: (Line 65) Use any<ByteArray>() for non-nullable param and or(...) for nullable
        // Assuming secureWipe takes a nullable ByteArray? based on previous context.
        // If it takes a non-nullable ByteArray, use: every { secureMemoryUtils.secureWipe(any<ByteArray>()) } just Runs
        every { secureMemoryUtils.secureWipe(or(isNull(), any<ByteArray>())) } just Runs

        // Mock Base64
        every { android.util.Base64.encodeToString(any<ByteArray>(), any<Int>()) } returns "encodedString"
        every { android.util.Base64.decode(any<String>(), any<Int>()) } returns ByteArray(32) { 0 }

        databaseKeyManager = DatabaseKeyManager(context, sessionManager, secureMemoryUtils)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test getOrCreateDatabasePassphrase creates new key on first run`() = runTest {
        // Given: No existing key
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns null
        every { mockSharedPrefs.getBoolean("key_initialized", false) } returns false

        // When
        val result = databaseKeyManager.getOrCreateDatabasePassphrase()

        // Then
        assertNotNull("Database passphrase should be created", result)
        assertEquals("Key size should be 32 bytes", 32, result?.size)
        verify { mockEditor.putBoolean("key_initialized", true) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `test getOrCreateDatabasePassphrase retrieves existing key`() = runTest {
        // Given: Existing key in storage
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "encodedKey"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "encodedIV"
        every { mockSharedPrefs.getBoolean("key_initialized", false) } returns true

        // When
        val result = databaseKeyManager.getOrCreateDatabasePassphrase()

        // Then
        assertNotNull("Should retrieve existing key", result)
        verify(exactly = 0) { secureMemoryUtils.generateSecureRandom(any<Int>()) }
    }

    @Test
    fun `test getCurrentDatabasePassphrase returns null when not initialized`() {
        // Given: No key exists
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns null

        // When
        val result = databaseKeyManager.getCurrentDatabasePassphrase()

        // Then
        assertNull("Should return null when key doesn't exist", result)
    }

    @Test
    fun `test generateNewDatabasePassphrase creates correct size key`() {
        // When
        val result = databaseKeyManager.generateNewDatabasePassphrase()

        // Then
        assertNotNull("Generated key should not be null", result)
        assertEquals("Generated key should be 32 bytes", 32, result.size)
        verify { secureMemoryUtils.generateSecureRandom(32) }
    }

    @Test
    fun `test backupCurrentDatabaseKey successfully backs up key`() {
        // Given: Existing key data
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "currentKey"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "currentIV"

        // When
        val result = databaseKeyManager.backupCurrentDatabaseKey()

        // Then
        assertTrue("Backup should succeed", result)
        verify { mockEditor.putString("encrypted_database_key_backup", "currentKey") }
        verify { mockEditor.putString("database_key_iv_backup", "currentIV") }
        verify { mockEditor.commit() }
    }

    @Test
    fun `test backupCurrentDatabaseKey returns false when no key exists`() {
        // Given: No current key
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns null
        every { mockSharedPrefs.getString("database_key_iv", null) } returns null

        // When
        val result = databaseKeyManager.backupCurrentDatabaseKey()

        // Then
        assertFalse("Backup should fail when no key exists", result)
        verify(exactly = 0) { mockEditor.commit() }
    }

    @Test
    fun `test commitNewDatabasePassphrase encrypts and stores key`() {
        // Given
        val newKey = ByteArray(32) { it.toByte() }
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "oldKey"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "oldIV"

        // When
        val result = databaseKeyManager.commitNewDatabasePassphrase(newKey)

        // Then
        assertTrue("Commit should succeed", result)
        verify { mockEditor.commit() }
    }

    @Test
    fun `test rollbackToBackup restores backup key`() {
        // Given: Backup exists
        every { mockSharedPrefs.getString("encrypted_database_key_backup", null) } returns "backupKey"
        every { mockSharedPrefs.getString("database_key_iv_backup", null) } returns "backupIV"

        // When
        val result = databaseKeyManager.rollbackToBackup()

        // Then
        assertTrue("Rollback should succeed", result)
        verify { mockEditor.putString("encrypted_database_key", "backupKey") }
        verify { mockEditor.putString("database_key_iv", "backupIV") }
        verify { mockEditor.commit() }
    }

    @Test
    fun `test rollbackToBackup fails when no backup exists`() {
        // Given: No backup
        every { mockSharedPrefs.getString("encrypted_database_key_backup", null) } returns null
        every { mockSharedPrefs.getString("database_key_iv_backup", null) } returns null

        // When
        val result = databaseKeyManager.rollbackToBackup()

        // Then
        assertFalse("Rollback should fail with no backup", result)
    }

    @Test
    fun `test clearBackup removes backup keys`() {
        // When
        databaseKeyManager.clearBackup()

        // Then
        verify { mockEditor.remove("encrypted_database_key_backup") }
        verify { mockEditor.remove("database_key_iv_backup") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `test requireActiveSession returns true when session is active`() {
        // Given
        every { sessionManager.isSessionActive() } returns true

        // When
        val result = databaseKeyManager.requireActiveSession()

        // Then
        assertTrue("Should return true when session active", result)
    }

    @Test
    fun `test requireActiveSession returns false when no active session`() {
        // Given
        every { sessionManager.isSessionActive() } returns false

        // When
        val result = databaseKeyManager.requireActiveSession()

        // Then
        assertFalse("Should return false when session inactive", result)
    }

    @Test
    fun `test clearDatabaseKey wipes all key data`() {
        // When
        databaseKeyManager.clearDatabaseKey()

        // Then
        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun `test isDatabaseKeyInitialized returns correct state`() {
        // Given
        every { mockSharedPrefs.getBoolean("key_initialized", false) } returns true

        // When
        val result = databaseKeyManager.isDatabaseKeyInitialized()

        // Then
        assertTrue("Should return true when initialized", result)
    }

    @Test
    fun `test key rotation workflow - backup commit rollback`() {
        // Setup existing key
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "oldKey"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "oldIV"
        val newKey = ByteArray(32) { it.toByte() }

        // Step 1: Backup
        val backupResult = databaseKeyManager.backupCurrentDatabaseKey()
        assertTrue("Backup should succeed", backupResult)

        // Step 2: Commit new key
        val commitResult = databaseKeyManager.commitNewDatabasePassphrase(newKey)
        assertTrue("Commit should succeed", commitResult)

        // Simulate failure - rollback
        every { mockSharedPrefs.getString("encrypted_database_key_backup", null) } returns "oldKey"
        every { mockSharedPrefs.getString("database_key_iv_backup", null) } returns "oldIV"
        val rollbackResult = databaseKeyManager.rollbackToBackup()
        assertTrue("Rollback should succeed", rollbackResult)
    }

    // ============================================================
    // ADDITIONAL TESTS - Memory Management & Edge Cases
    // ============================================================

    @Test
    fun `test key generation uses secure random bytes`() {
        // When
        val key1 = databaseKeyManager.generateNewDatabasePassphrase()
        val key2 = databaseKeyManager.generateNewDatabasePassphrase()

        // Then
        assertNotNull("First key should not be null", key1)
        assertNotNull("Second key should not be null", key2)
        assertEquals("Keys should be 32 bytes", 32, key1.size)
        assertEquals("Keys should be 32 bytes", 32, key2.size)
        verify(atLeast = 2) { secureMemoryUtils.generateSecureRandom(32) }
    }

    @Test
    fun `test multiple backup attempts overwrite previous backup`() {
        // Given: Initial key
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "key1"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "iv1"

        // When: First backup
        databaseKeyManager.backupCurrentDatabaseKey()

        // Change current key
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "key2"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "iv2"

        // Second backup
        databaseKeyManager.backupCurrentDatabaseKey()

        // Then: Second backup should overwrite first
        verify(exactly = 1) { mockEditor.putString("encrypted_database_key_backup", "key2") }
        verify(exactly = 1) { mockEditor.putString("database_key_iv_backup", "iv2") }
    }

    @Test
    fun `test memory is wiped on key rotation failure`() {
        // Given: Rotation will fail
        every { mockSharedPrefs.getString("encrypted_database_key", null) } returns "oldKey"
        every { mockSharedPrefs.getString("database_key_iv", null) } returns "oldIV"
        every { mockEditor.commit() } returns false // Force failure

        val newKey = ByteArray(32) { it.toByte() }

        // When: Attempt commit (will fail)
        val result = databaseKeyManager.commitNewDatabasePassphrase(newKey)

        // Then: Memory should still be wiped even on failure
        assertFalse("Commit should fail", result)
        // FIX: (Potential Line 331) Specified type for any()
        verify(atLeast = 1) { secureMemoryUtils.secureWipe(any<ByteArray>()) }
    }

    @Test
    fun `test session requirement checked before key operations`() = runTest {
        // Given: No active session
        every { sessionManager.isSessionActive() } returns false

        // When: Attempt to get passphrase
        val result = databaseKeyManager.getOrCreateDatabasePassphrase()

        // Then: Should fail gracefully
        assertNull("Should return null without active session", result)
    }
}