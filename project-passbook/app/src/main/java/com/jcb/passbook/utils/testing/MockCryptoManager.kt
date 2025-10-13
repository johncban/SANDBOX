package com.jcb.passbook.utils.testing

import com.jcb.passbook.security.crypto.CryptoManager

// Does NOT use keystore, produces non-secret deterministic output for test
class MockCryptoManager : CryptoManager() {
    override fun encrypt(plainText: String): ByteArray {
        return "[encrypted:$plainText]".toByteArray()
    }
    override fun decrypt(data: ByteArray): String {
        return String(data).removePrefix("[encrypted:").removeSuffix("]")
    }
}