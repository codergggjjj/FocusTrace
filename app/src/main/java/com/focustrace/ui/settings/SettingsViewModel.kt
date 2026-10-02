package com.focustrace.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.focustrace.data.repository.SettingsRepository
import com.focustrace.data.repository.BackupRepository
import com.focustrace.data.repository.BackupPreview
import com.focustrace.data.datastore.UserSettings
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class SettingsViewModel(private val repository: SettingsRepository, private val backup: BackupRepository) : ViewModel() {
    val uiState = repository.settings.asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var importUri: Uri? = null
    private val _backupPreview = MutableStateFlow<BackupPreview?>(null)
    val backupPreview = _backupPreview.asStateFlow()
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage = _backupMessage.asStateFlow()

    fun exportBackup(uri: Uri) = backupAction {
        val summary = backup.export(uri)
        _backupMessage.value = "备份已保存：${summary.taskCount} 个待办、${summary.sessionCount} 条专注记录。"
    }

    fun inspectBackup(uri: Uri) = backupAction {
        _backupPreview.value = backup.inspect(uri)
        importUri = uri
    }

    fun restoreBackup() {
        val uri = importUri ?: return
        backupAction {
            val summary = backup.restore(uri)
            importUri = null
            _backupPreview.value = null
            _backupMessage.value = "恢复完成：${summary.taskCount} 个待办、${summary.sessionCount} 条专注记录。"
        }
    }

    fun cancelBackupImport() {
        if (_busy.value) return
        importUri = null
        _backupPreview.value = null
    }

    private fun backupAction(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _backupMessage.value = null
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _backupMessage.value = when (e) {
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "备份文件无效"
                    else -> "操作失败，请检查文件并重试。"
                }
            } finally { _busy.value = false }
        }
    }
    fun save(value: UserSettings, done: () -> Unit) = changeSettings(done) { repository.update(value) }
    fun saveLearningGoals(daily: Int, weekly: Int, done: () -> Unit) =
        changeSettings(done) { repository.setLearningGoals(daily, weekly) }
    fun clearError() { _error.value = null }
    private fun changeSettings(done: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { action(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "保存失败，请重试。" }
            finally { _busy.value = false }
        }
    }
    fun importBackground(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { repository.importBackground(uri) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "图片导入失败，请选择其他图片重试。" }
            finally { _busy.value = false }
        }
    }
}
