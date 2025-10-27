package com.jcb.passbook.utils.testing

import com.jcb.passbook.security.crypto.CryptoManager

class MockCryptoManager : CryptoManager() {

    override fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        return plaintext // No encryption in mock
    }

    override fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        return data // No decryption in mock
    }
}