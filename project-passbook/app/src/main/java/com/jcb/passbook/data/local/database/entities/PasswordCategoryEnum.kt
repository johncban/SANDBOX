package com.jcb.passbook.data.local.database.entities

/**
 * PasswordCategoryEnum - Predefined password categories for Item classification
 *
 * SECURITY ARCHITECTURE:
 * - Stored as String (enum.name) in Item.password_category column
 * - Separate from user-created Category table (different purpose)
 * - Type-safe categorization with emoji icons for UX
 *
 * USAGE:
 * - Item.password_category = PasswordCategoryEnum.BANKING.name (saves "BANKING")
 * - Retrieve: PasswordCategoryEnum.fromString(item.password_category)
 */
enum class PasswordCategoryEnum(val displayName: String, val icon: String) {
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
         * Convert database string back to enum (case-insensitive with fallback)
         *
         * @param value String from Item.password_category column
         * @return Matching enum or OTHER if invalid/null
         */
        fun fromString(value: String?): PasswordCategoryEnum {
            if (value.isNullOrBlank()) return OTHER

            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                        it.displayName.equals(value, ignoreCase = true)
            } ?: OTHER
        }

        /**
         * Get all valid enum names for validation/filtering
         */
        fun getAllNames(): List<String> = entries.map { it.name }

        /**
         * Get all display names for UI dropdowns
         */
        fun getAllDisplayNames(): List<String> = entries.map { it.displayName }
    }
}
