package com.jcb.passbook.utils.common

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Arrays
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AccountManager /* (private val activity: Activity) */ {


    // New function to generate secure passphrase
    private fun generateSecurePassphrase(password: String, salt: ByteArray): String {
        val iterations = 65536 // Recommended number of iterations
        val keyLength = 256 // Key length
        val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = secretKeyFactory.generateSecret(keySpec)
        val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        // Clear sensitive data from memory
        Arrays.fill(password.toCharArray(), ' ')
        return encoded
    }
    private fun getSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

}