package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DatabaseKeyManager handles SQLCipher key rotation and database rekeying operations.
 * Integrates with MasterKeyManager for secure key wrapping.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val auditLogger: AuditLogger,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    companion object {
        private const val DB_PASSPHRASE_KEY = "db_passphrase_v3"
        private const val PASSPHRASE_VERSION_KEY = "db_passphrase_version"
        private const val CURRENT_VERSION = 3
        private const val PASSPHRASE_SIZE_BYTES = 32
        private const val TAG = "DatabaseKeyManager"
    }

    private val prefs = context.getSharedPreferences("db_key_manager", Context.MODE_PRIVATE)

    /**
     * Get or create SQLCipher passphrase
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        return try {
            val amk = sessionManager.getApplicationMasterKey()
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

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.READ,
                "Database passphrase accessed",
                "DATABASE", "PASSPHRASE",
                AuditOutcome.SUCCESS, null, "NORMAL"
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
        }
    }

    /**
     * Generate new database passphrase and wrap with AMK
     */
    private suspend fun generateAndWrapDatabasePassphrase(amk: ByteArray): ByteArray {
        val passphrase = secureMemoryUtils.generateSecureRandom(PASSPHRASE_SIZE_BYTES)

        try {
            val wrapped = wrapWithAMK(amk, passphrase)
            prefs.edit()
                .putString(DB_PASSPHRASE_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION)
                .apply()

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.CREATE,
                "New database passphrase generated",
                "DATABASE", "PASSPHRASE",
                AuditOutcome.SUCCESS, null, "NORMAL"
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
            try {
                val amk = sessionManager.getApplicationMasterKey()
                if (amk == null) {
                    return@withContext RekeyResult.NoActiveSession
                }

                auditLogger.logUserAction(
                    null, "SYSTEM", AuditEventType.KEY_ROTATION,
                    "Database key rotation started",
                    "DATABASE", "REKEY",
                    AuditOutcome.SUCCESS, null, "ELEVATED"
                )

                // Generate new passphrase
                val newPassphrase = secureMemoryUtils.generateSecureRandom(PASSPHRASE_SIZE_BYTES)

                try {
                    // Get current passphrase
                    val currentPassphrase = getOrCreateDatabasePassphrase()
                    if (currentPassphrase == null) {
                        return@withContext RekeyResult.CurrentPassphraseUnavailable
                    }

                    // Perform database rekey
                    val rekeySuccess = performDatabaseRekey(currentPassphrase, newPassphrase)

                    if (rekeySuccess) {
                        // Store new wrapped passphrase
                        val wrapped = wrapWithAMK(amk, newPassphrase)
                        prefs.edit()
                            .putString(DB_PASSPHRASE_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                            .putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION)
                            .apply()

                        auditLogger.logUserAction(
                            null, "SYSTEM", AuditEventType.KEY_ROTATION,
                            "Database key rotation completed successfully",
                            "DATABASE", "REKEY",
                            AuditOutcome.SUCCESS, null, "ELEVATED"
                        )

                        RekeyResult.Success
                    } else {
                        auditLogger.logUserAction(
                            null, "SYSTEM", AuditEventType.KEY_ROTATION,
                            "Database key rotation failed during rekey operation",
                            "DATABASE", "REKEY",
                            AuditOutcome.FAILURE, null, "CRITICAL"
                        )
                        RekeyResult.RekeyFailed
                    }
                } finally {
                    secureMemoryUtils.secureWipe(newPassphrase)
                    currentPassphrase?.let { secureMemoryUtils.secureWipe(it) }
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
            }
        }
    }

    /**
     * Perform the actual database rekey operation
     */
    private fun performDatabaseRekey(currentPassphrase: ByteArray, newPassphrase: ByteArray): Boolean {
        return try {
            // Get database path
            val dbPath = context.getDatabasePath("item_database").absolutePath

            // Open database with current passphrase
            val db = SQLiteDatabase.openDatabase(
                dbPath,
                currentPassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            try {
                // Perform rekey
                db.rawExecSQL("PRAGMA rekey = ?", arrayOf(newPassphrase))

                // Verify the rekey worked by executing a simple query
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0) // This will throw if the rekey failed
                }

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
        return try {
            val amk = sessionManager.getApplicationMasterKey()
            if (amk == null) {
                return false
            }

            // For now, just update version. In a real migration, you'd handle format changes
            prefs.edit().putInt(PASSPHRASE_VERSION_KEY, CURRENT_VERSION).apply()

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.UPDATE,
                "Database passphrase migrated to version $CURRENT_VERSION",
                "DATABASE", "PASSPHRASE",
                AuditOutcome.SUCCESS, null, "NORMAL"
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