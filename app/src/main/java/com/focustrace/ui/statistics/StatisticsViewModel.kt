package com.focustrace.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.statistics.*
import com.focustrace.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class StatisticsSelection(val period: StatisticsPeriod, val range: StatisticsRange, val canNext: Boolean)
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(repository: StatisticsRepository, private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val period = savedState.getStateFlow("statisticsPeriod", StatisticsPeriod.DAY.name)
    private val anchor = savedState.getStateFlow<Long?>("statisticsAnchor", null)
    private val reload = MutableStateFlow(0)
    private val calendar = flow {
        while (currentCoroutineContext().isActive) { val zone = ZoneId.systemDefault(); emit(LocalDate.now(zone) to zone); delay(30000) }
    }.distinctUntilChanged()
    private fun selection(periodName: String, day: Long?, today: LocalDate, zone: ZoneId): StatisticsSelection {
        val selected = StatisticsPeriod.valueOf(periodName)
        val range = statisticsRange(day?.let(LocalDate::ofEpochDay) ?: today, selected, zone)
        return StatisticsSelection(selected, range, range.start < statisticsRange(today, selected, zone).start)
    }
    val selection = combine(period, anchor, calendar) { p, a, c -> selection(p, a, c.first, c.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), selection(period.value, anchor.value, LocalDate.now(), ZoneId.systemDefault()))
    val uiState = combine(selection, reload) { selected, _ -> selected.range }.flatMapLatest { range ->
        repository.statistics(range).asLoadState().onStart { emit(LoadState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    val heatmap = combine(selection, reload) { selected, attempt ->
        statisticsRange(selected.range.start, StatisticsPeriod.MONTH, selected.range.zone) to attempt
    }.distinctUntilChanged().flatMapLatest { (range, _) ->
        repository.statistics(range).asLoadState().onStart { emit(LoadState.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    fun viewDay(date: LocalDate) {
        savedState["statisticsAnchor"] = date.toEpochDay()
        savedState["statisticsPeriod"] = StatisticsPeriod.DAY.name
    }
    fun choose(period: StatisticsPeriod) { savedState["statisticsAnchor"] = null; savedState["statisticsPeriod"] = period.name }
    fun selectDate(date: LocalDate) {
        if (date <= LocalDate.now()) savedState["statisticsAnchor"] = date.toEpochDay()
    }
    fun current() { savedState["statisticsAnchor"] = null }
    fun shift(direction: Int) {
        if (direction > 0 && !selection.value.canNext) return
        val selected = selection.value
        val next = when (selected.period) {
            StatisticsPeriod.DAY -> selected.range.start.plusDays(direction.toLong())
            StatisticsPeriod.WEEK -> selected.range.start.plusWeeks(direction.toLong())
            StatisticsPeriod.MONTH -> selected.range.start.plusMonths(direction.toLong())
        }
        savedState["statisticsAnchor"] = next.toEpochDay()
    }
    fun retry() { reload.value += 1 }
}
