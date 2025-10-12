package com.jcb.passbook.data.mappers

import com.jcb.passbook.data.local.entities.Item
import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.entities.PasswordStrength
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

class PasswordMapper @Inject constructor(
    private val encryptionUtil: EncryptionUtil // Inject encryption utility
) {

    fun toDomain(entity: Item): Password {
        val decryptedPassword = encryptionUtil.decrypt(entity.encryptedPassword)
        val tags = entity.tags?.let {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(it, type)
        } ?: emptyList()

        return Password(
            id = entity.id,
            name = entity.name,
            username = entity.username,
            password = decryptedPassword,
            website = entity.website,
            notes = entity.notes,
            categoryId = entity.categoryId,
            userId = entity.userId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isFavorite = entity.isFavorite,
            tags = tags
        ).copy(strength = Password(
            id = entity.id,
            name = entity.name,
            username = entity.username,
            password = decryptedPassword,
            website = entity.website,
            notes = entity.notes,
            categoryId = entity.categoryId,
            userId = entity.userId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isFavorite = entity.isFavorite,
            tags = tags
        ).calculateStrength())
    }

    fun toEntity(domain: Password): PasswordEntity {
        val encryptedPassword = encryptionUtil.encrypt(domain.password)
        val tagsJson = if (domain.tags.isNotEmpty()) {
            Gson().toJson(domain.tags)
        } else null

        return PasswordEntity(
            id = domain.id,
            name = domain.name,
            username = domain.username,
            encryptedPassword = encryptedPassword,
            website = domain.website,
            notes = domain.notes,
            categoryId = domain.categoryId,
            userId = domain.userId,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isFavorite = domain.isFavorite,
            tags = tagsJson
        )
    }
}

// Encryption utility interface (implement separately)
interface EncryptionUtil {
    fun encrypt(plaintext: String): ByteArray
    fun decrypt(ciphertext: ByteArray): String
}
