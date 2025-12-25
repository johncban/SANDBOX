package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jcb.passbook.data.local.database.entities.PasswordCategory

/**
 * Data Access Object for PasswordCategory junction table
 *
 * This DAO manages the many-to-many relationship between Items and Categories.
 * Each PasswordCategory entry represents an association between one item and one category.
 *
 * ⚠️ NOTE: This is DIFFERENT from password_category enum field in Item table
 * This manages category_id foreign key relationships for custom user categories.
 */
@Dao
interface PasswordCategoryDao {

    /**
     * Insert a single item-category association
     *
     * @param passwordCategory The association to insert
     * @return The row ID of the newly inserted association
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passwordCategory: PasswordCategory): Long

    /**
     * Insert multiple item-category associations in a single transaction
     * Used when saving an item with multiple categories
     *
     * @param passwordCategories List of associations to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passwordCategories: List<PasswordCategory>)

    /**
     * Delete all category associations for a specific item
     * Used before updating item categories or when deleting an item
     *
     * @param itemId The item ID whose associations should be deleted
     */
    @Query("DELETE FROM password_category WHERE item_id = :itemId")
    suspend fun deleteByItemId(itemId: Long)

    /**
     * Get all category associations for a specific item
     *
     * @param itemId The item ID
     * @return List of PasswordCategory associations
     */
    @Query("SELECT * FROM password_category WHERE item_id = :itemId")
    suspend fun getCategoriesByItemId(itemId: Long): List<PasswordCategory>

    /**
     * Delete a specific item-category association
     * Used when removing a single category from an item
     *
     * @param itemId The item ID
     * @param categoryId The category ID
     */
    @Query("DELETE FROM password_category WHERE item_id = :itemId AND category_id = :categoryId")
    suspend fun deleteSpecificAssociation(itemId: Long, categoryId: Long)

    /**
     * Get all items in a specific category
     *
     * @param categoryId The category ID
     * @return List of PasswordCategory associations
     */
    @Query("SELECT * FROM password_category WHERE category_id = :categoryId")
    suspend fun getItemsByCategory(categoryId: Long): List<PasswordCategory>

    /**
     * Delete a single association
     *
     * @param passwordCategory The association to delete
     */
    @Delete
    suspend fun delete(passwordCategory: PasswordCategory)

    /**
     * Check if an item-category association exists
     *
     * @param itemId The item ID
     * @param categoryId The category ID
     * @return The count (0 or 1)
     */
    @Query("SELECT COUNT(*) FROM password_category WHERE item_id = :itemId AND category_id = :categoryId")
    suspend fun associationExists(itemId: Long, categoryId: Long): Int
}
