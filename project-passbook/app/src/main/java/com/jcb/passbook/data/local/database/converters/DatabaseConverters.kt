package com.jcb.passbook.data.local.database.converters

import androidx.room.TypeConverter
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DatabaseConverters {

    private val gson = Gson()

    // ByteArray conversion for salts, passwords, etc.
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    // List<String> conversion for tags field in Item entity
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return if (value.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val listType = object : TypeToken<List<String>>() {}.type
                gson.fromJson(value, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}