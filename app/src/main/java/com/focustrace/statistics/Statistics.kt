package com.focustrace.statistics

import com.focustrace.data.local.entity.StatisticsRecord
import java.math.BigInteger
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val MIN_VALID_FOCUS_SECONDS = 5 * 60L
fun isValidFocusSession(session: com.focustrace.data.local.entity.FocusSessionEntity) =
    session.focusSeconds > MIN_VALID_FOCUS_SECONDS

enum class StatisticsPeriod(val label: String, val unit: String) {
    DAY("日", "日"), WEEK("周", "周"), MONTH("月", "月"), YEAR("年", "年")
}
data class StatisticsRange(val start: LocalDate, val endExclusive: LocalDate, val zone: ZoneId) {
    val startMillis get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis get() = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}
fun statisticsRange(date: LocalDate, period: StatisticsPeriod, zone: ZoneId): StatisticsRange {
    val start = when (period) {
        StatisticsPeriod.DAY -> date
        StatisticsPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatisticsPeriod.MONTH -> date.withDayOfMonth(1)
        StatisticsPeriod.YEAR -> date.withDayOfYear(1)
    }
    val end = when (period) {
        StatisticsPeriod.DAY -> start.plusDays(1)
        StatisticsPeriod.WEEK -> start.plusWeeks(1)
        StatisticsPeriod.MONTH -> start.plusMonths(1)
        StatisticsPeriod.YEAR -> start.plusYears(1)
    }
    return StatisticsRange(start, end, zone)
}

fun previousStatisticsRange(range: StatisticsRange, period: StatisticsPeriod): StatisticsRange {
    val start = when (period) {
        StatisticsPeriod.DAY -> range.start.minusDays(1)
        StatisticsPeriod.WEEK -> range.start.minusWeeks(1)
        StatisticsPeriod.MONTH -> range.start.minusMonths(1)
        StatisticsPeriod.YEAR -> range.start.minusYears(1)
    }
    return StatisticsRange(start, range.start, range.zone)
}

data class StatisticsTotals(val focusSeconds: Long, val sessions: Int, val pomodoros: Int,
    val distractions: Int, val distractionSeconds: Long, val focusPercent: Int?,
    val averageDistractionSeconds: Long?, val averageFirstDistractionSeconds: Long?)
data class DailyStatistics(val date: LocalDate, val totals: StatisticsTotals)
data class TaskFocusDistribution(val taskId: Long?, val title: String, val focusSeconds: Long, val sessions: Int)
data class StatisticsSummary(val range: StatisticsRange, val totals: StatisticsTotals,
    val days: List<DailyStatistics>, val sessions: List<com.focustrace.data.local.entity.FocusSessionEntity>,
    val hours: List<StatisticsTotals> = emptyList(), val taskDistribution: List<TaskFocusDistribution> = emptyList())
data class StatisticsDashboard(val current: StatisticsSummary, val previous: StatisticsSummary)

data class HabitStatistics(val currentStreak: Int, val longestStreak: Int, val focusedDaysInMonth: Int, val monthDaysSoFar: Int)

/**
 * Splits each session's effective focus time across every local clock hour touched by
 * its [startTime, endTime) interval. Timer sessions may include pauses or distractions,
 * so their effective seconds are distributed in proportion to wall-clock overlap. The
 * integer remainder is assigned to the largest fractional overlaps to keep the sum exact.
 */
private fun hourlyFocusSeconds(records: List<StatisticsRecord>, zone: ZoneId): LongArray {
    val result = LongArray(24)
    records.forEach { row ->
        val session = row.session
        val endMillis = session.endTime ?: return@forEach
        val focusSeconds = session.focusSeconds.coerceAtLeast(0)
        if (focusSeconds == 0L || endMillis <= session.startTime) return@forEach

        val overlapMillis = LongArray(24)
        var cursor = Instant.ofEpochMilli(session.startTime)
        val end = Instant.ofEpochMilli(endMillis)
        while (cursor < end) {
            val localCursor = cursor.atZone(zone)
            val nextHour = localCursor.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
            val segmentEnd = minOf(end, nextHour)
            val segmentMillis = Duration.between(cursor, segmentEnd).toMillis().coerceAtLeast(0)
            overlapMillis[localCursor.hour] += segmentMillis
            if (segmentEnd <= cursor) break
            cursor = segmentEnd
        }

        val wallMillis = overlapMillis.sum()
        if (wallMillis <= 0L) return@forEach
        val divisor = BigInteger.valueOf(wallMillis)
        val target = BigInteger.valueOf(focusSeconds)
        val remainders = mutableListOf<Pair<Int, BigInteger>>()
        var allocated = 0L
        overlapMillis.forEachIndexed { hour, millis ->
            if (millis <= 0L) return@forEachIndexed
            val quotientAndRemainder = BigInteger.valueOf(millis).multiply(target).divideAndRemainder(divisor)
            val seconds = quotientAndRemainder[0].toLong()
            result[hour] += seconds
            allocated += seconds
            remainders += hour to quotientAndRemainder[1]
        }
        var remainderSeconds = focusSeconds - allocated
        val remainderOrder = remainders.sortedWith(
            compareByDescending<Pair<Int, BigInteger>> { it.second }.thenBy { it.first }
        )
        var index = 0
        while (remainderSeconds > 0 && remainderOrder.isNotEmpty()) {
            val hour = remainderOrder[index % remainderOrder.size].first
            result[hour] = result[hour] + 1
            remainderSeconds -= 1
            index += 1
        }
    }
    return result
}

fun summarizeHabits(sessions: List<com.focustrace.data.local.entity.FocusSessionEntity>, today: LocalDate, zone: ZoneId,
    taskId: Long? = null): HabitStatistics {
    val focusedDays = sessions.asSequence().filter { taskId == null || it.taskId == taskId }.filter(::isValidFocusSession)
        .filter { it.endTime != null && it.status in listOf(3, 4) }
        .map { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        .filter { it <= today }.toSortedSet()
    var longest = 0
    var running = 0
    var previous: LocalDate? = null
    focusedDays.forEach { day ->
        running = if (previous?.plusDays(1) == day) running + 1 else 1
        longest = maxOf(longest, running)
        previous = day
    }
    val streakEnd = when {
        today in focusedDays -> today
        today.minusDays(1) in focusedDays -> today.minusDays(1)
        else -> null
    }
    var current = 0
    var cursor = streakEnd
    while (cursor != null && cursor in focusedDays) {
        current += 1
        cursor = cursor.minusDays(1)
    }
    val month = YearMonth.from(today)
    return HabitStatistics(
        currentStreak = current,
        longestStreak = longest,
        focusedDaysInMonth = focusedDays.count { YearMonth.from(it) == month },
        monthDaysSoFar = today.dayOfMonth
    )
}

fun summarizeStatistics(records: List<StatisticsRecord>, range: StatisticsRange, taskId: Long? = null): StatisticsSummary {
    val startMillis = range.startMillis
    val endMillis = range.endMillis
    val eligible = records.filter { (taskId == null || it.session.taskId == taskId) && it.session.endTime != null &&
        it.session.status in listOf(3, 4) && it.session.startTime >= startMillis && it.session.startTime < endMillis }
    val valid = eligible.filter { isValidFocusSession(it.session) }
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
    val byDay = valid.groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(range.zone).toLocalDate() }
    val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it < range.endExclusive }
        .map { DailyStatistics(it, totals(byDay[it].orEmpty())) }.toList()
    val byStartHour = valid.groupBy { Instant.ofEpochMilli(it.session.startTime).atZone(range.zone).hour }
    val focusByHour = hourlyFocusSeconds(valid, range.zone)
    val hours = (0..23).map { hour ->
        val startHourTotals = totals(byStartHour[hour].orEmpty())
        val focus = focusByHour[hour]
        val denominator = focus.toDouble() + startHourTotals.distractionSeconds.toDouble()
        startHourTotals.copy(
            focusSeconds = focus,
            focusPercent = if (denominator == 0.0) null else (100 * focus / denominator).roundToInt()
        )
    }
    val taskDistribution = valid.groupBy {
        it.session.taskId?.let { id -> "task:$id" } ?: "title:${it.session.taskTitleSnapshot ?: "自由专注"}"
    }.map { (_, rows) ->
        val latest = rows.maxBy { it.session.startTime }.session
        TaskFocusDistribution(latest.taskId, latest.taskTitleSnapshot ?: "自由专注",
            rows.sumOf { it.session.focusSeconds }, rows.size)
    }
        .sortedWith(compareByDescending<TaskFocusDistribution> { it.focusSeconds }.thenBy { it.title })
    return StatisticsSummary(range, totals(valid), days,
        valid.map { it.session }.sortedWith(compareByDescending<com.focustrace.data.local.entity.FocusSessionEntity> { it.startTime }.thenByDescending { it.id }),
        hours, taskDistribution)
}
