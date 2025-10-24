package com.jcb.passbook.security.biometric

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredToken(val iv: ByteArray, val cipherText: ByteArray)

object BiometricTokenStore {
    private const val PREF_NAME = "pb_biometric_prefs"
    private fun keyIv(userId: Int) = "token_iv_$userId"
    private fun keyCt(userId: Int) = "token_ct_$userId"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun save(context: Context, userId: Int, iv: ByteArray, ct: ByteArray) {
        prefs(context).edit()
            .putString(keyIv(userId), iv.encodeBase64())
            .putString(keyCt(userId), ct.encodeBase64())
            .apply()
    }

    fun load(context: Context, userId: Int): StoredToken? {
        val p = prefs(context)
        val ivB64 = p.getString(keyIv(userId), null) ?: return null
        val ctB64 = p.getString(keyCt(userId), null) ?: return null
        return StoredToken(ivB64.decodeBase64(), ctB64.decodeBase64())
    }

    fun clear(context: Context, userId: Int) {
        prefs(context).edit()
            .remove(keyIv(userId))
            .remove(keyCt(userId))
            .apply()
    }

    private fun ByteArray.encodeBase64() = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.decodeBase64() = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}