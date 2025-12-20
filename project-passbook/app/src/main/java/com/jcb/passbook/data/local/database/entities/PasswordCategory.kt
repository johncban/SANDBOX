package com.jcb.passbook.data.local.database.entities

enum class PasswordCategory(val displayName: String, val icon: String) {
    SOCIAL_MEDIA("Social Media", "👥"),
    ENTERTAINMENT("Entertainment", "🎬"),
    BANKING("Banking", "🏦"),
    EMAIL("Email", "📧"),
    WORK("Work", "💼"),
    SHOPPING("Shopping", "🛒"),
    GAMING("Gaming", "🎮"),
    HEALTHCARE("Healthcare", "⚕️"),
    EDUCATION("Education", "📚"),
    TRAVEL("Travel", "✈️"),
    UTILITIES("Utilities", "⚡"),
    OTHER("Other", "📁");

    companion object {
        /**
         * Convert string to enum safely, default to OTHER if unknown
         */
        fun fromString(value: String?): PasswordCategory {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                        it.displayName.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}