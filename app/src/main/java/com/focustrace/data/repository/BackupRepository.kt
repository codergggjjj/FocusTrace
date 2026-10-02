package com.focustrace.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.local.FocusTraceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupPreview(
    val createdAt: Long,
    val taskCount: Int,
    val sessionCount: Int,
    val distractionCount: Int,
    val hasCustomBackground: Boolean
)

/** User-initiated, offline backup. No Room schema changes or raw database files. */
class BackupRepository(
    private val context: Context,
    private val database: FocusTraceDatabase,
    private val settingsStore: SettingsDataStore,
    private val images: FocusBackgroundImageStore
) {
    private val mutex = Mutex()
    private val dao = database.backupDao()

    suspend fun export(uri: Uri): BackupPreview = mutex.withLock { withContext(Dispatchers.IO) {
        val snapshot = database.withTransaction {
            check(dao.activeTimerCount() == 0) { "请先结束当前专注或休息，再创建备份" }
            BackupData(System.currentTimeMillis(), dao.categories(), dao.tasks(), dao.sessions(),
                dao.distractions(), settingsStore.settings.first(), null)
        }
        val image = images.readForBackup(snapshot.settings.focusBackgroundCustomPath)
        val data = snapshot.copy(
            settings = snapshot.settings.copy(focusBackgroundCustomPath = null), customBackground = image)
        val manifestBytes = BackupFormat.encode(data).toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= 32_000_000) { "记录过多，备份文件超出支持大小" }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入选定文件")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
            zip.write(manifestBytes)
            zip.closeEntry()
            if (image != null) {
                zip.putNextEntry(ZipEntry(BackupFormat.IMAGE))
                zip.write(image)
                zip.closeEntry()
            }
        }
        data.preview()
    } }

    suspend fun inspect(uri: Uri): BackupPreview = mutex.withLock { withContext(Dispatchers.IO) {
        read(uri).preview()
    } }

    suspend fun restore(uri: Uri): BackupPreview = mutex.withLock { withContext(Dispatchers.IO) {
        val data = read(uri)
        val previousSettings = settingsStore.settings.first()
        val previousImage = previousSettings.focusBackgroundCustomPath
        val restoredImage = data.customBackground?.let { images.restoreFromBackup(it) }
        var settingsApplied = false
        try {
            database.withTransaction {
                check(dao.activeTimerCount() == 0) { "请先结束当前专注或休息，再恢复备份" }
                dao.clearDistractions()
                dao.clearSessions()
                dao.clearTasks()
                dao.clearCategories()
                dao.insertCategories(data.categories)
                dao.insertTasks(data.tasks)
                dao.insertSessions(data.sessions)
                dao.insertDistractions(data.distractions)
                settingsStore.update(data.settings.copy(focusBackgroundCustomPath = restoredImage))
                settingsApplied = true
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                if (settingsApplied) try { settingsStore.update(previousSettings) }
                catch (rollback: Exception) { e.addSuppressed(rollback) }
                try { images.deleteOwned(restoredImage) }
                catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            }
            throw e
        }
        if (previousImage != restoredImage) images.deleteOwned(previousImage)
        data.preview()
    } }

    private fun read(uri: Uri): BackupData {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取选定文件")
        var manifest: ByteArray? = null
        var image: ByteArray? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "备份文件包含无效目录" }
                when (entry.name) {
                    BackupFormat.MANIFEST -> {
                        require(manifest == null) { "备份清单重复" }
                        manifest = zip.readLimited(32_000_000)
                    }
                    BackupFormat.IMAGE -> {
                        require(image == null) { "备份图片重复" }
                        image = zip.readLimited(8_000_000)
                    }
                    else -> throw IllegalArgumentException("备份包含未知文件")
                }
                zip.closeEntry()
            }
        }
        val json = manifest?.toString(Charsets.UTF_8) ?: throw IllegalArgumentException("未找到备份清单")
        return BackupFormat.decode(json, image)
    }

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { "备份文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun BackupData.preview() = BackupPreview(createdAt, tasks.size, sessions.size,
        distractions.size, customBackground != null)
}
