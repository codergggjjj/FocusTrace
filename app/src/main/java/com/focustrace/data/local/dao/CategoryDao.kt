package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun getAll(): Flow<List<CategoryEntity>>
    @Insert suspend fun insert(value: CategoryEntity): Long
    @Update suspend fun update(value: CategoryEntity)
    @Delete suspend fun delete(value: CategoryEntity)
}
