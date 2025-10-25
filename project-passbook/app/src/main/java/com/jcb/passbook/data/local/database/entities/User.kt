package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // NEW: Biometric token fields
    @ColumnInfo(name = "biometric_token_salt")
    val biometricTokenSalt: ByteArray? = null,

    @ColumnInfo(name = "biometric_token_hash")
    val biometricTokenHash: ByteArray? = null,

    @ColumnInfo(name = "biometric_enabled")
    val biometricEnabled: Boolean = false,

    @ColumnInfo(name = "biometric_setup_at")
    val biometricSetupAt: Long? = null
) {
    // Override equals and hashCode to handle ByteArray properly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (username != other.username) return false
        if (passwordHash != other.passwordHash) return false
        if (createdAt != other.createdAt) return false
        if (biometricTokenSalt != null) {
            if (other.biometricTokenSalt == null) return false
            if (!biometricTokenSalt.contentEquals(other.biometricTokenSalt)) return false
        } else if (other.biometricTokenSalt != null) return false
        if (biometricTokenHash != null) {
            if (other.biometricTokenHash == null) return false
            if (!biometricTokenHash.contentEquals(other.biometricTokenHash)) return false
        } else if (other.biometricTokenHash != null) return false
        if (biometricEnabled != other.biometricEnabled) return false
        if (biometricSetupAt != other.biometricSetupAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordHash.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (biometricTokenSalt?.contentHashCode() ?: 0)
        result = 31 * result + (biometricTokenHash?.contentHashCode() ?: 0)
        result = 31 * result + biometricEnabled.hashCode()
        result = 31 * result + (biometricSetupAt?.hashCode() ?: 0)
        return result
    }
}
