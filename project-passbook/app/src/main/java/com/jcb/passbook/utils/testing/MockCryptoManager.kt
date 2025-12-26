package com.jcb.passbook.utils.testing

import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils

/**
 * ✅ FIXED: MockCryptoManager properly extends CryptoManager with required dependency
 * Does NOT use keystore, produces non-secret deterministic output for tests
 */
class MockCryptoManager(
    secureMemoryUtils: SecureMemoryUtils = SecureMemoryUtils()
) : CryptoManager(secureMemoryUtils) {

    fun encrypt(plainText: String): ByteArray {
        return "[encrypted:$plainText]".toByteArray()
    }

    fun decrypt(data: ByteArray): String {
        return String(data).removePrefix("[encrypted:").removeSuffix("]")
    }
}
