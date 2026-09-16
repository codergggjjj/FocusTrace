package com.focustrace.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focustrace.data.local.dao.*
import com.focustrace.data.local.entity.*
@Database(entities = [CategoryEntity::class, TaskEntity::class, FocusSessionEntity::class, DistractionEventEntity::class], version = 6, exportSchema = true)
abstract class FocusTraceDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun distractionDao(): DistractionDao
    companion object {
        val Migration1To2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                mapOf("elapsedMillis" to "0", "anchorWall" to "0", "anchorElapsed" to "0", "bootCount" to "-1", "restSeconds" to "300", "autoBreak" to "1", "autoFocus" to "0").forEach { (name, value) ->
                    db.execSQL("ALTER TABLE focus_sessions ADD COLUMN $name INTEGER NOT NULL DEFAULT $value")
                }
            }
        }
        val Migration2To3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN backgroundWall INTEGER DEFAULT NULL")
                mapOf("backgroundElapsed" to "0", "backgroundBoot" to "-1", "backgroundThresholdMillis" to "3000").forEach { (name, value) ->
                    db.execSQL("ALTER TABLE focus_sessions ADD COLUMN $name INTEGER NOT NULL DEFAULT $value")
                }
            }
        }
        val Migration3To4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN taskTitleSnapshot TEXT DEFAULT NULL")
                db.execSQL("UPDATE focus_sessions SET taskTitleSnapshot = (SELECT title FROM tasks WHERE tasks.id = focus_sessions.taskId) WHERE taskId IS NOT NULL")
            }
        }
        val Migration4To5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN timerType INTEGER NOT NULL DEFAULT 0")
            }
        }
        val Migration5To6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE tasks SET categoryId = NULL")
                db.execSQL("DELETE FROM categories")
            }
        }
    }
}
