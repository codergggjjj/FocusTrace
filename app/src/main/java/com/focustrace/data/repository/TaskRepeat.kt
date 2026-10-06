package com.focustrace.data.repository

import java.time.LocalDate

object TaskRepeat {
    fun valid(rule: String): Boolean = rule in listOf("NONE", "DAILY", "WEEKDAYS") ||
        (rule.startsWith("DAYS:") && rule.removePrefix("DAYS:").let { days ->
            days.isNotEmpty() && days.all { it in '1'..'7' } && days.toSet().size == days.length
        })
    fun due(rule: String, date: LocalDate): Boolean = when (rule) {
        "DAILY" -> true
        "WEEKDAYS" -> date.dayOfWeek.value <= 5
        else -> valid(rule) && rule.startsWith("DAYS:") && date.dayOfWeek.value.toString() in rule.removePrefix("DAYS:")
    }
    fun label(rule: String): String = when (rule) {
        "NONE" -> "不重复"
        "DAILY" -> "每天"
        "WEEKDAYS" -> "工作日（周一至周五）"
        else -> if (valid(rule)) "每周 " + rule.removePrefix("DAYS:").map { "一二三四五六日"[it - '1'] }.joinToString("、") else "不重复"
    }
}
