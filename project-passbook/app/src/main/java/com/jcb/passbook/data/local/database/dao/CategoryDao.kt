// File: app/src/main/java/com/jcb/passbook/data/local/database/dao/CategoryDao.kt
package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.Category
import kotlinx.coroutines.flow.Flow

/**
 * CategoryDao - Data Access Object for Category operations
 *
 * REFACTORED: Removed duplicate method signatures
 * Provides CRUD operations with hierarchical organization support
 */
@Dao
interface CategoryDao {

    // ============================================
    // CREATE Operations
    // ============================================

    /**
     * Insert a new category
     * @return The ID of the inserted category
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: Category): Long

    /**
     * Insert multiple categories
     * @return List of inserted category IDs
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<Category>): List<Long>

    /**
     * Insert or update a category (upsert)
     */
    @Upsert
    suspend fun upsert(category: Category): Long

    // ============================================
    // READ Operations
    // ============================================

    /**
     * Get all visible categories as reactive Flow
     */
    @Query("SELECT * FROM categories WHERE is_visible = 1 ORDER BY sort_order ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    /**
     * Get all visible categories (one-time query)
     */
    @Query("SELECT * FROM categories WHERE is_visible = 1 ORDER BY sort_order ASC, name ASC")
    suspend fun getAllCategoriesOnce(): List<Category>

    /**
     * Get category by ID (one-time query)
     */
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): Category?

    /**
     * Get category by ID as reactive Flow
     */
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryByIdFlow(categoryId: Long): Flow<Category?>

    /**
     * Get category by name
     */
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    /**
     * Get all root categories (no parent)
     */
    @Query("SELECT * FROM categories WHERE parent_id IS NULL AND is_visible = 1 ORDER BY sort_order ASC, name ASC")
    fun getRootCategories(): Flow<List<Category>>

    /**
     * Get child categories of a parent
     */
    @Query("SELECT * FROM categories WHERE parent_id = :parentId AND is_visible = 1 ORDER BY sort_order ASC, name ASC")
    fun getChildCategories(parentId: Long): Flow<List<Category>>

    /**
     * Get categories for a specific user (including shared/NULL user_id categories)
     */
    @Query("SELECT * FROM categories WHERE (user_id = :userId OR user_id IS NULL) AND is_visible = 1 ORDER BY sort_order ASC, name ASC")
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    /**
     * Get system categories only
     */
    @Query("SELECT * FROM categories WHERE is_system = 1 ORDER BY sort_order ASC")
    fun getSystemCategories(): Flow<List<Category>>

    /**
     * Search categories by name or description
     */
    @Query("""
        SELECT * FROM categories 
        WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        AND is_visible = 1
        ORDER BY sort_order ASC, name ASC
    """)
    fun searchCategories(query: String): Flow<List<Category>>

    /**
     * Get categories with item counts greater than zero
     */
    @Query("SELECT * FROM categories WHERE item_count > 0 AND is_visible = 1 ORDER BY item_count DESC")
    fun getCategoriesWithItems(): Flow<List<Category>>

    /**
     * Check if a category has children
     */
    @Query("SELECT COUNT(*) > 0 FROM categories WHERE parent_id = :categoryId")
    suspend fun hasChildren(categoryId: Long): Boolean

    /**
     * Get total category count
     */
    @Query("SELECT COUNT(*) FROM categories WHERE is_visible = 1")
    suspend fun getCategoryCount(): Int

    /**
     * Get total item count across all categories
     */
    @Query("SELECT COALESCE(SUM(item_count), 0) FROM categories WHERE is_visible = 1")
    suspend fun getTotalItemCount(): Int

    // ============================================
    // UPDATE Operations
    // ============================================

    /**
     * Update a category entity
     */
    @Update
    suspend fun update(category: Category)

    /**
     * Update multiple categories
     */
    @Update
    suspend fun updateAll(categories: List<Category>)

    /**
     * Update category name
     */
    @Query("UPDATE categories SET name = :name, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun updateName(categoryId: Long, name: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update category icon
     */
    @Query("UPDATE categories SET icon = :icon, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun updateIcon(categoryId: Long, icon: String?, timestamp: Long = System.currentTimeMillis())

    /**
     * Update category color
     */
    @Query("UPDATE categories SET color = :color, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun updateColor(categoryId: Long, color: String?, timestamp: Long = System.currentTimeMillis())

    /**
     * Set item count for a category
     */
    @Query("UPDATE categories SET item_count = :count, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun updateItemCount(categoryId: Long, count: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Increment item count by 1
     */
    @Query("UPDATE categories SET item_count = item_count + 1, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun incrementItemCount(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Decrement item count by 1 (prevents negative values)
     */
    @Query("UPDATE categories SET item_count = CASE WHEN item_count > 0 THEN item_count - 1 ELSE 0 END, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun decrementItemCount(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Update sort order
     */
    @Query("UPDATE categories SET sort_order = :sortOrder, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun updateSortOrder(categoryId: Long, sortOrder: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Toggle visibility (show/hide)
     */
    @Query("UPDATE categories SET is_visible = NOT is_visible, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun toggleVisibility(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    // ============================================
    // DELETE Operations
    // ============================================

    /**
     * Delete a category entity
     * WARNING: Will fail if category has items due to foreign key constraint
     */
    @Delete
    suspend fun delete(category: Category)

    /**
     * Delete category by ID (only non-system categories)
     */
    @Query("DELETE FROM categories WHERE id = :categoryId AND is_system = 0")
    suspend fun deleteById(categoryId: Long)

    /**
     * Delete all user-created (non-system) categories
     */
    @Query("DELETE FROM categories WHERE is_system = 0")
    suspend fun deleteAllUserCategories()

    /**
     * Soft delete - hide category instead of deleting
     */
    @Query("UPDATE categories SET is_visible = 0, updated_at = :timestamp WHERE id = :categoryId")
    suspend fun softDelete(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    // ============================================
    // UTILITY Operations
    // ============================================

    /**
     * Recalculate item counts for all categories
     * Use after bulk item operations to ensure accuracy
     */
    @Query("""
        UPDATE categories 
        SET item_count = (
            SELECT COUNT(*) FROM items 
            WHERE items.category_id = categories.id
        ),
        updated_at = :timestamp
    """)
    suspend fun recalculateAllItemCounts(timestamp: Long = System.currentTimeMillis())

    /**
     * Recalculate item count for a specific category
     */
    @Query("""
        UPDATE categories 
        SET item_count = (
            SELECT COUNT(*) FROM items 
            WHERE items.category_id = :categoryId
        ),
        updated_at = :timestamp
        WHERE id = :categoryId
    """)
    suspend fun recalculateItemCount(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Get the maximum sort order value
     * Use when adding new categories to position them at the end
     */
    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories")
    suspend fun getMaxSortOrder(): Int
}
