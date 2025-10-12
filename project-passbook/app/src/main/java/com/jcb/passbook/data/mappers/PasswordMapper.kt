package com.jcb.passbook.data.mappers

import com.jcb.passbook.data.local.entities.Item
import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.entities.PasswordStrength
import com.jcb.passbook.util.encryption.EncryptionUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

class PasswordMapper @Inject constructor(
    private val encryptionUtil: EncryptionUtil
) {

    fun toDomain(entity: Item): Password {
        val decryptedPassword = encryptionUtil.decrypt(entity.encryptedPasswordData)
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
        ).let { password ->
            password.copy(strength = password.calculateStrength())
        }
    }

    fun toEntity(domain: Password): Item {
        val encryptedPassword = encryptionUtil.encrypt(domain.password)
        val tagsJson = if (domain.tags.isNotEmpty()) {
            Gson().toJson(domain.tags)
        } else null

        return Item(
            id = domain.id,
            name = domain.name,
            username = domain.username,
            encryptedPasswordData = encryptedPassword,
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