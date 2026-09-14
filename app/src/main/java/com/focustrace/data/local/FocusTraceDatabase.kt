package com.focustrace.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focustrace.data.local.dao.*
import com.focustrace.data.local.entity.*
@Database(entities = [CategoryEntity::class, TaskEntity::class, FocusSessionEntity::class, DistractionEventEntity::class], version = 1, exportSchema = true)
abstract class FocusTraceDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun distractionDao(): DistractionDao
    companion object {
        val SeedCategories = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                listOf("学习", "工作", "阅读", "运动", "其他").forEachIndexed { index, name ->
                    db.execSQL("INSERT INTO categories (name, icon, sortOrder, createdAt) VALUES (?, ?, ?, ?)", arrayOf(name, listOf("school", "work", "book", "fitness", "label")[index], index, System.currentTimeMillis()))
                }
            }
        }
    }
}
