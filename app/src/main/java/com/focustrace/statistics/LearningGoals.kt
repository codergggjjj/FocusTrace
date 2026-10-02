package com.focustrace.statistics

import java.time.LocalDate

data class LearningGoalProgress(val targetMinutes: Int, val focusSeconds: Long) {
    init { require(targetMinutes >= 0 && focusSeconds >= 0) }
    val enabled get() = targetMinutes > 0
    val targetSeconds get() = targetMinutes * 60L
    val reached get() = enabled && focusSeconds >= targetSeconds
    val remainingSeconds get() = if (enabled) (targetSeconds - focusSeconds).coerceAtLeast(0) else 0L
    val fraction get() = if (enabled) (focusSeconds.toDouble() / targetSeconds).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent get() = if (enabled) (focusSeconds.toDouble() / targetSeconds * 100).coerceIn(0.0, 100.0).toInt() else 0
}

data class LearningGoalsSummary(val today: LocalDate, val weekStart: LocalDate,
    val daily: LearningGoalProgress, val weekly: LearningGoalProgress)
