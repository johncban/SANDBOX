package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DatabaseKeyManager handles SQLCipher key rotation and database rekeying operations.
 * Integrates with MasterKeyManager for secure key wrapping.
 *
 * ✅ FIXED: Uses lazy AuditLogger provider to break circular dependencies
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val auditLoggerProvider: () -> AuditLogger,  // ✅ CHANGED: Lazy supplier
    private val secureMemoryUtils: SecureMemoryUtils
) {
    // ✅ ADDED: Lazy initialization of auditLogger
    private val auditLogger: AuditLogger by lazy { auditLoggerProvider() }

    companion object {
        private const val DB_PASSPHRASE_KEY = "db_passphrase_v3"
        private const val PASSPHRASE_VERSION_KEY = "db_passphrase_version"
        private const val CURRENT_VERSION = 3
        private const val PASSPHRASE_SIZE_BYTES = 32
    }

    private val prefs = context.getSharedPreferences("db_key_manager", Context.MODE_PRIVATE)

    /**
     * Get or create SQLCipher passphrase
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        var amk: ByteArray? = null
        return try {
            amk = sessionManager.getApplicationMasterKey()
            if (amk == null) {
                auditLogger.logSecurityEvent(
                    "Cannot get database passphrase - no active session",
                    "WARNING",
                    AuditOutcome.BLOCKED
                )
                return null
            }

            val wrappedPassphrase = prefs.getString(DB_PASSPHRASE_KEY, null)
            val passphrase = if (wrappedPassphrase != null) {
                unwrapDatabasePassphrase(amk, wrappedPassphrase)
            } else {
                generateAndWrapDatabasePassphrase(amk)
            }

            auditLogger.logDatabaseOperation(
                operation = "PASSPHRASE_ACCESS",
                tableName = "key_storage",
                recordId = "passphrase",
                outcome = AuditOutcome.SUCCESS
            )

            passphrase
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to get database passphrase: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            Timber.e(e, "Failed to get database passphrase")
            null
        } finally {
            amk?.let { secureMemoryUtils.secureWipe(it) }
        }
    }

    /**
     * Generate new database passphrase and wrap with AMK
     */
    private suspend fun generateAndWrapDatabasePassphrase(amk: ByteArray): ByteArray {
        val passphrase = secureMemoryUtils.generateSecureRandom(PASSPHRASE_SIZE_BYTES)
        try {
            val wrapped = wrapWithAMK(amk, passphrase)
            prefs.edit().apply {
                putString(DB_PASSPHRASE_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION)
                apply()
            }

            auditLogger.logDatabaseOperation(
                operation = "PASSPHRASE_GENERATE",
                tableName = "key_storage",
                recordId = "passphrase",
                outcome = AuditOutcome.SUCCESS
            )

            return passphrase
        } catch (e: Exception) {
            secureMemoryUtils.secureWipe(passphrase)
            throw e
        }
    }

    /**
     * Unwrap database passphrase using AMK
     */
    private fun unwrapDatabasePassphrase(amk: ByteArray, wrappedPassphrase: String): ByteArray? {
        return try {
            val wrapped = Base64.decode(wrappedPassphrase, Base64.NO_WRAP)
            unwrapWithAMK(amk, wrapped)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unwrap database passphrase")
            null
        }
    }

    /**
     * Rotate SQLCipher database key
     */
    suspend fun rotateDatabaseKey(): RekeyResult {
        return withContext(Dispatchers.IO) {
            var amk: ByteArray? = null
            var currentPassphrase: ByteArray? = null
            var newPassphrase: ByteArray? = null

            try {
                amk = sessionManager.getApplicationMasterKey()
                if (amk == null) {
                    return@withContext RekeyResult.NoActiveSession
                }

                auditLogger.logDatabaseOperation(
                    operation = "KEY_ROTATION_START",
                    tableName = "database",
                    recordId = "master_key",
                    outcome = AuditOutcome.SUCCESS
                )

                // Generate new passphrase
                newPassphrase = secureMemoryUtils.generateSecureRandom(PASSPHRASE_SIZE_BYTES)

                // Get current passphrase
                currentPassphrase = getOrCreateDatabasePassphrase()
                if (currentPassphrase == null) {
                    return@withContext RekeyResult.CurrentPassphraseUnavailable
                }

                // Perform database rekey
                val rekeySuccess = performDatabaseRekey(currentPassphrase, newPassphrase)
                if (rekeySuccess) {
                    // Store new wrapped passphrase
                    val wrapped = wrapWithAMK(amk, newPassphrase)
                    prefs.edit().apply {
                        putString(DB_PASSPHRASE_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                        putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION)
                        apply()
                    }

                    auditLogger.logDatabaseOperation(
                        operation = "KEY_ROTATION_SUCCESS",
                        tableName = "database",
                        recordId = "master_key",
                        outcome = AuditOutcome.SUCCESS
                    )

                    RekeyResult.Success
                } else {
                    auditLogger.logDatabaseOperation(
                        operation = "KEY_ROTATION_FAILED",
                        tableName = "database",
                        recordId = "master_key",
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Rekey operation failed"
                    )

                    RekeyResult.RekeyFailed
                }
            } catch (e: Exception) {
                auditLogger.logSecurityEvent(
                    "Database key rotation failed with exception: ${e.message}",
                    "CRITICAL",
                    AuditOutcome.FAILURE
                )
                Timber.e(e, "Database key rotation failed")
                RekeyResult.Error(e.message ?: "Unknown error")
            } finally {
                amk?.let { secureMemoryUtils.secureWipe(it) }
                currentPassphrase?.let { secureMemoryUtils.secureWipe(it) }
                newPassphrase?.let { secureMemoryUtils.secureWipe(it) }
            }
        }
    }

    /**
     * Perform the actual database rekey operation
     */
    private fun performDatabaseRekey(currentPassphrase: ByteArray, newPassphrase: ByteArray): Boolean {
        return try {
            val dbPath = context.getDatabasePath("passbook_encrypted.db").absolutePath
            val currentHex = currentPassphrase.joinToString("") { "%02x".format(it) }
            val newHex = newPassphrase.joinToString("") { "%02x".format(it) }

            val db = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                dbPath,
                "x'$currentHex'",
                null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE
            )

            try {
                db.execSQL("PRAGMA rekey = \"x'$newHex'\"")
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                Timber.d("Database rekey completed successfully")
                true
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Database rekey operation failed")
            false
        }
    }

    /**
     * Wrap data with AMK using AES-GCM
     */
    private fun wrapWithAMK(amk: ByteArray, data: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val key = javax.crypto.spec.SecretKeySpec(amk, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    /**
     * Unwrap data with AMK using AES-GCM
     */
    private fun unwrapWithAMK(amk: ByteArray, wrappedData: ByteArray): ByteArray? {
        return try {
            if (wrappedData.size <= 12) return null

            val iv = wrappedData.copyOfRange(0, 12)
            val encrypted = wrappedData.copyOfRange(12, wrappedData.size)

            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val key = javax.crypto.spec.SecretKeySpec(amk, "AES")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unwrap data with AMK")
            null
        }
    }

    /**
     * Check if database passphrase needs migration
     */
    fun needsPassphraseMigration(): Boolean {
        val currentVersion = prefs.getInt(PASSPHRASE_VERSION_KEY, 1)
        return currentVersion < CURRENT_VERSION
    }

    /**
     * Migrate database passphrase to current version
     */
    suspend fun migratePassphrase(): Boolean {
        var amk: ByteArray? = null
        return try {
            amk = sessionManager.getApplicationMasterKey()
            if (amk == null) {
                return false
            }

            prefs.edit().apply {
                putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION)
                apply()
            }

            auditLogger.logDatabaseOperation(
                operation = "PASSPHRASE_MIGRATION",
                tableName = "key_storage",
                recordId = "passphrase_v$CURRENT_VERSION",
                outcome = AuditOutcome.SUCCESS
            )

            true
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Database passphrase migration failed: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            false
        } finally {
            amk?.let { secureMemoryUtils.secureWipe(it) }
        }
    }

    /**
     * Rekey operation results
     */
    sealed class RekeyResult {
        object Success : RekeyResult()
        object NoActiveSession : RekeyResult()
        object CurrentPassphraseUnavailable : RekeyResult()
        object RekeyFailed : RekeyResult()
        data class Error(val message: String) : RekeyResult()
    }
}
