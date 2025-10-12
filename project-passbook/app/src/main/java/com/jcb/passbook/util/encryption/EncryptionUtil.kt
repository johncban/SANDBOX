package com.jcb.passbook.util.encryption

interface EncryptionUtil {
    fun encrypt(plaintext: String): ByteArray
    fun decrypt(ciphertext: ByteArray): String
}