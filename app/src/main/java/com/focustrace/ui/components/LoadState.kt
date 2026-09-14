package com.focustrace.ui.components
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> = map<T, LoadState<T>> { LoadState.Ready(it) }
    .catch { emit(LoadState.Error("暂时无法读取本地数据，请重新打开应用。")) }
