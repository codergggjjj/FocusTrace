package com.focustrace.statistics

import com.focustrace.data.local.entity.StatisticsRecord
import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class StatisticsPeriod(val label: String, val unit: String) { DAY("今日", "日"), WEEK("本周", "周"), MONTH("本月", "月") }
data class StatisticsRange(val start: LocalDate, val endExclusive: LocalDate, val zone: ZoneId) {
    val startMillis get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis get() = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}
fun statisticsRange(date: LocalDate, period: StatisticsPeriod, zone: ZoneId): StatisticsRange {
    val start = when (period) {
        StatisticsPeriod.DAY -> date
        StatisticsPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatisticsPeriod.MONTH -> date.withDayOfMonth(1)
    }
    val end = when (period) { StatisticsPeriod.DAY -> start.plusDays(1); StatisticsPeriod.WEEK -> start.plusWeeks(1); StatisticsPeriod.MONTH -> start.plusMonths(1) }
    return StatisticsRange(start, end, zone)
}

data class StatisticsTotals(val focusSeconds: Long, val sessions: Int, val pomodoros: Int,
    val distractions: Int, val distractionSeconds: Long, val focusPercent: Int?,
    val averageDistractionSeconds: Long?, val averageFirstDistractionSeconds: Long?)
data class DailyStatistics(val date: LocalDate, val totals: StatisticsTotals)
data class CategoryStatistics(val id: Long?, val name: String, val focusSeconds: Long, val sessions: Int)
data class StatisticsSummary(val range: StatisticsRange, val totals: StatisticsTotals,
    val days: List<DailyStatistics>, val categories: List<CategoryStatistics>, val sessions: List<com.focustrace.data.local.entity.FocusSessionEntity>)

fun summarizeStatistics(records: List<StatisticsRecord>, range: StatisticsRange): StatisticsSummary {
    val startMillis = range.startMillis
    val endMillis = range.endMillis
    val eligible = records.filter { it.session.endTime != null && it.session.status in listOf(3, 4) && it.session.startTime >= startMillis && it.session.startTime < endMillis }
    fun totals(rows: List<StatisticsRecord>): StatisticsTotals {
        val focus = rows.sumOf { it.session.focusSeconds.coerceAtLeast(0) }
        val events = rows.flatMap { it.events }
        val distraction = events.sumOf { it.durationSeconds.coerceAtLeast(0) }
        val firstTimes = rows.mapNotNull { row -> row.events.minByOrNull { it.id }?.let { event ->
            (event.backgroundTime - row.session.startTime).takeIf { it >= 0 }?.div(1000)
        } }
        val denominator = focus.toDouble() + distraction.toDouble()
        return StatisticsTotals(focus, rows.size,
            rows.count { it.session.type == 0 && it.session.plannedSeconds > 0 && it.session.focusSeconds >= it.session.plannedSeconds },
            events.size, distraction, if (denominator == 0.0) null else (100 * focus / denominator).roundToInt(),
            if (events.isEmpty()) null else (distraction.toDouble() / events.size).roundToLong(),
            if (firstTimes.isEmpty()) null else firstTimes.average().roundToLong())
    }
    val byDay = eligible.groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(range.zone).toLocalDate() }
    val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it < range.endExclusive }
        .map { DailyStatistics(it, totals(byDay[it].orEmpty())) }.toList()
    val categories = eligible.groupBy { it.task?.category?.id }.map { (id, rows) ->
        CategoryStatistics(id, rows.first().task?.category?.name ?: "未分类 / 自由专注", rows.sumOf { it.session.focusSeconds.coerceAtLeast(0) }, rows.size)
    }.sortedWith(compareByDescending<CategoryStatistics> { it.focusSeconds }.thenBy { it.name })
    return StatisticsSummary(range, totals(eligible), days, categories, eligible.map { it.session }.sortedWith(compareByDescending<com.focustrace.data.local.entity.FocusSessionEntity> { it.startTime }.thenByDescending { it.id }))
}
