package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PasswordCategory - Junction table for many-to-many Item-Category relationships
 *
 * ⚠️ NAMING CLARIFICATION:
 * - This table: User-created custom category assignments (many-to-many)
 * - Item.password_category: Predefined enum categorization (PasswordCategoryEnum)
 *
 * ARCHITECTURE:
 * - Allows multiple categories per item (Item #1 can be in Category #2 AND #5)
 * - Separate from PasswordCategoryEnum (different purpose entirely)
 *
 * Example:
 * - Item #1 (Facebook login) belongs to Category #2 (Social) AND Category #5 (Personal)
 * - Two rows: (item_id=1, category_id=2) and (item_id=1, category_id=5)
 */
@Entity(
    tableName = "password_category",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["category_id"]),
        Index(value = ["item_id", "category_id"], unique = true)
    ]
)
data class PasswordCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
