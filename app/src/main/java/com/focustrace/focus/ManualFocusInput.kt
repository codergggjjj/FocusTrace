package com.focustrace.focus

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ManualFocusInput(val date: LocalDate, val startMillis: Long, val endMillis: Long) {
    val seconds: Long get() = (endMillis - startMillis) / 1000
}

fun manualEndTime(startText: String, minutesText: String): String {
    val minutes = minutesText.trim().toLongOrNull()
    require(minutes != null && minutes in 1..1439) { "专注时长请输入 1–1439 分钟" }
    val start = try { LocalTime.parse(startText.trim(), DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)) }
        catch (_: Exception) { throw IllegalArgumentException("请输入有效开始时间，格式为 HH:mm") }
    require(start.toSecondOfDay() / 60 + minutes < 1440) { "结束时间必须在当天 24:00 前，暂不支持跨天记录" }
    return start.plusMinutes(minutes).format(DateTimeFormatter.ofPattern("HH:mm"))
}

fun parseManualFocusInput(dateText: String, startText: String, endText: String,
    zone: ZoneId = ZoneId.systemDefault()): ManualFocusInput {
    val date = try { LocalDate.parse(dateText.trim()) }
        catch (_: Exception) { throw IllegalArgumentException("请输入有效日期，格式为 yyyy-MM-dd") }
    require(date <= LocalDate.now(zone)) { "日期不能晚于今天" }
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun time(text: String): LocalTime = try { LocalTime.parse(text.trim(), formatter) }
        catch (_: Exception) { throw IllegalArgumentException("请输入有效时间，格式为 HH:mm（如 14:00）") }
    val start = time(startText)
    val end = time(endText)
    require(end > start) { "结束时间必须晚于开始时间，暂不支持跨天记录" }
    fun instant(time: LocalTime): Long {
        val local = date.atTime(time)
        require(zone.rules.getValidOffsets(local).isNotEmpty()) { "该时间在当前时区不存在，请调整时间" }
        return local.atZone(zone).toInstant().toEpochMilli()
    }
    val result = ManualFocusInput(date, instant(start), instant(end))
    require(result.seconds > 0) { "专注时长必须大于 0" }
    return result
}
