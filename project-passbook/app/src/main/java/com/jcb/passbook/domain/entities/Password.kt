package com.jcb.passbook.domain.entities

data class Password(
    val id: Long = 0,
    val name: String,
    val username: String? = null,
    val password: String, // Decrypted password for domain operations
    val website: String? = null,
    val notes: String? = null,
    val categoryId: String? = null,
    val userId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val strength: PasswordStrength = PasswordStrength.UNKNOWN
) {
    fun isValid(): Boolean {
        return name.isNotBlank() && password.isNotBlank() && userId > 0
    }

    fun calculateStrength(): PasswordStrength {
        return when {
            password.length < 8 -> PasswordStrength.WEAK
            password.length < 12 -> PasswordStrength.MEDIUM
            password.matches(Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&].*")) ->
                PasswordStrength.STRONG
            else -> PasswordStrength.MEDIUM
        }
    }
}

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG, UNKNOWN
}
