package com.focustrace.data.repository

import com.focustrace.data.datastore.FocusBackgrounds
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.datastore.MAX_DAILY_GOAL_MINUTES
import com.focustrace.data.datastore.MAX_WEEKLY_GOAL_MINUTES
import com.focustrace.data.local.entity.CategoryEntity
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class BackupData(
    val createdAt: Long,
    val categories: List<CategoryEntity>,
    val tasks: List<TaskEntity>,
    val sessions: List<FocusSessionEntity>,
    val distractions: List<DistractionEventEntity>,
    val settings: UserSettings,
    val customBackground: ByteArray?
)

internal object BackupFormat {
    const val VERSION = 1
    const val MANIFEST = "backup.json"
    const val IMAGE = "focus_background.jpg"

    fun encode(data: BackupData): String = JSONObject().apply {
        put("format", "FocusTrace")
        put("version", VERSION)
        put("createdAt", data.createdAt)
        put("hasCustomBackground", data.customBackground != null)
        put("categories", JSONArray().apply { data.categories.forEach { value -> put(JSONObject().apply {
            put("id", value.id); put("name", value.name); put("icon", value.icon)
            put("sortOrder", value.sortOrder); put("createdAt", value.createdAt)
        }) } })
        put("tasks", JSONArray().apply { data.tasks.forEach { value -> put(JSONObject().apply {
            put("id", value.id); put("categoryId", value.categoryId); put("title", value.title)
            put("targetMinutes", value.targetMinutes); put("completed", value.completed)
            put("repeatType", value.repeatType); put("reminderTime", value.reminderTime)
            put("createdAt", value.createdAt); put("updatedAt", value.updatedAt)
            put("timerType", value.timerType)
        }) } })
        put("sessions", JSONArray().apply { data.sessions.forEach { value -> put(JSONObject().apply {
            put("id", value.id); put("taskId", value.taskId); put("type", value.type)
            put("startTime", value.startTime); put("endTime", value.endTime)
            put("plannedSeconds", value.plannedSeconds); put("focusSeconds", value.focusSeconds)
            put("distractionCount", value.distractionCount); put("distractionSeconds", value.distractionSeconds)
            put("status", value.status); put("elapsedMillis", value.elapsedMillis)
            put("anchorWall", value.anchorWall); put("anchorElapsed", value.anchorElapsed)
            put("bootCount", value.bootCount); put("restSeconds", value.restSeconds)
            put("autoBreak", value.autoBreak); put("autoFocus", value.autoFocus)
            put("backgroundWall", value.backgroundWall); put("backgroundElapsed", value.backgroundElapsed)
            put("backgroundBoot", value.backgroundBoot)
            put("backgroundThresholdMillis", value.backgroundThresholdMillis)
            put("taskTitleSnapshot", value.taskTitleSnapshot)
            put("source", value.source); put("note", value.note)
        }) } })
        put("distractions", JSONArray().apply { data.distractions.forEach { value -> put(JSONObject().apply {
            put("id", value.id); put("sessionId", value.sessionId)
            put("backgroundTime", value.backgroundTime); put("foregroundTime", value.foregroundTime)
            put("durationSeconds", value.durationSeconds)
        }) } })
        put("settings", JSONObject().apply { val value = data.settings
            put("pomodoroMinutes", value.pomodoroMinutes); put("breakMinutes", value.breakMinutes)
            put("distractionThreshold", value.distractionThreshold)
            put("autoStartBreak", value.autoStartBreak); put("autoStartFocus", value.autoStartFocus)
            put("soundEnabled", value.soundEnabled); put("darkMode", value.darkMode.name)
            put("focusBackgroundEnabled", value.focusBackgroundEnabled)
            put("focusBackgroundRandom", value.focusBackgroundRandom)
            put("focusBackgroundIds", JSONArray(value.focusBackgroundIds.toList()))
            put("focusBackgroundSelectedId", value.focusBackgroundSelectedId)
            put("dailyGoalMinutes", value.dailyGoalMinutes)
            put("weeklyGoalMinutes", value.weeklyGoalMinutes)
        })
    }.toString()

    fun decode(json: String, image: ByteArray?): BackupData {
        val root = JSONObject(json)
        require(root.getString("format") == "FocusTrace" && root.getInt("version") == VERSION) {
            "不支持的备份格式或版本"
        }
        require(root.getBoolean("hasCustomBackground") == (image != null)) { "备份图片缺失或不匹配" }
        val categories = root.getJSONArray("categories").read { value -> CategoryEntity(
            id = value.getLong("id"), name = value.getString("name"), icon = value.getString("icon"),
            sortOrder = value.getInt("sortOrder"), createdAt = value.getLong("createdAt")) }
        val tasks = root.getJSONArray("tasks").read { value -> TaskEntity(
            id = value.getLong("id"), categoryId = value.nullableLong("categoryId"),
            title = value.getString("title"), targetMinutes = value.getInt("targetMinutes"),
            completed = value.getBoolean("completed"), repeatType = value.getString("repeatType"),
            reminderTime = value.nullableLong("reminderTime"), createdAt = value.getLong("createdAt"),
            updatedAt = value.getLong("updatedAt"), timerType = value.getInt("timerType")) }
        val sessions = root.getJSONArray("sessions").read { value -> FocusSessionEntity(
            id = value.getLong("id"), taskId = value.nullableLong("taskId"), type = value.getInt("type"),
            startTime = value.getLong("startTime"), endTime = value.nullableLong("endTime"),
            plannedSeconds = value.getLong("plannedSeconds"), focusSeconds = value.getLong("focusSeconds"),
            distractionCount = value.getInt("distractionCount"), distractionSeconds = value.getLong("distractionSeconds"),
            status = value.getInt("status"), elapsedMillis = value.getLong("elapsedMillis"),
            anchorWall = value.getLong("anchorWall"), anchorElapsed = value.getLong("anchorElapsed"),
            bootCount = value.getInt("bootCount"), restSeconds = value.getLong("restSeconds"),
            autoBreak = value.getBoolean("autoBreak"), autoFocus = value.getBoolean("autoFocus"),
            backgroundWall = value.nullableLong("backgroundWall"),
            backgroundElapsed = value.getLong("backgroundElapsed"), backgroundBoot = value.getInt("backgroundBoot"),
            backgroundThresholdMillis = value.getLong("backgroundThresholdMillis"),
            taskTitleSnapshot = value.nullableString("taskTitleSnapshot"),
            source = value.getString("source"), note = value.nullableString("note")) }
        val distractions = root.getJSONArray("distractions").read { value -> DistractionEventEntity(
            id = value.getLong("id"), sessionId = value.getLong("sessionId"),
            backgroundTime = value.getLong("backgroundTime"), foregroundTime = value.getLong("foregroundTime"),
            durationSeconds = value.getLong("durationSeconds")) }
        val prefs = root.getJSONObject("settings")
        // JSONArray strings are handled directly, unlike entity arrays.
        val backgroundIds = (0 until prefs.getJSONArray("focusBackgroundIds").length())
            .map { prefs.getJSONArray("focusBackgroundIds").getString(it) }.toSet()
        val settings = UserSettings(
            pomodoroMinutes = prefs.getInt("pomodoroMinutes"), breakMinutes = prefs.getInt("breakMinutes"),
            distractionThreshold = prefs.getInt("distractionThreshold"),
            autoStartBreak = prefs.getBoolean("autoStartBreak"), autoStartFocus = prefs.getBoolean("autoStartFocus"),
            soundEnabled = prefs.getBoolean("soundEnabled"), darkMode = ThemeMode.valueOf(prefs.getString("darkMode")),
            focusBackgroundEnabled = prefs.getBoolean("focusBackgroundEnabled"),
            focusBackgroundRandom = prefs.getBoolean("focusBackgroundRandom"),
            focusBackgroundIds = backgroundIds,
            focusBackgroundSelectedId = prefs.getString("focusBackgroundSelectedId"),
            focusBackgroundCustomPath = null,
            dailyGoalMinutes = if (prefs.has("dailyGoalMinutes")) prefs.getInt("dailyGoalMinutes") else 0,
            weeklyGoalMinutes = if (prefs.has("weeklyGoalMinutes")) prefs.getInt("weeklyGoalMinutes") else 0)
        return BackupData(root.getLong("createdAt"), categories, tasks, sessions, distractions, settings, image)
            .also(::validate)
    }

    private fun validate(data: BackupData) {
        fun uniquePositive(ids: List<Long>) = ids.all { it > 0 } && ids.size == ids.toSet().size
        require(uniquePositive(data.categories.map { it.id }) && uniquePositive(data.tasks.map { it.id }) &&
            uniquePositive(data.sessions.map { it.id }) && uniquePositive(data.distractions.map { it.id })) {
            "备份含有无效或重复的记录编号"
        }
        val categoryIds = data.categories.mapTo(HashSet()) { it.id }
        val taskIds = data.tasks.mapTo(HashSet()) { it.id }
        val sessionIds = data.sessions.mapTo(HashSet()) { it.id }
        require(data.tasks.all { it.categoryId == null || it.categoryId in categoryIds }) { "待办分类关联无效" }
        require(data.sessions.all { it.taskId == null || it.taskId in taskIds }) { "专注待办关联无效" }
        require(data.distractions.all { it.sessionId in sessionIds && it.durationSeconds >= 0 && it.foregroundTime >= it.backgroundTime }) {
            "分心记录关联或时间无效"
        }
        require(data.tasks.all { it.title.isNotBlank() && it.targetMinutes in 0..1440 && it.timerType in 0..1 }) {
            "备份含有无效待办"
        }
        require(data.sessions.all { it.type in 0..1 && it.status !in 1..3 && it.status in 0..4 &&
            it.source in setOf("TIMER", "MANUAL") && it.focusSeconds >= 0 && it.plannedSeconds >= 0 &&
            it.distractionCount >= 0 && it.distractionSeconds >= 0 &&
            (it.endTime == null || it.endTime >= it.startTime) }) { "备份含有进行中的专注或无效时间" }
        require(data.settings.pomodoroMinutes in 1..1440 && data.settings.breakMinutes in 1..1440 &&
            data.settings.distractionThreshold in 0..86400 &&
            data.settings.dailyGoalMinutes in 0..MAX_DAILY_GOAL_MINUTES &&
            data.settings.weeklyGoalMinutes in 0..MAX_WEEKLY_GOAL_MINUTES &&
            data.settings.focusBackgroundSelectedId in FocusBackgrounds.ids &&
            data.settings.focusBackgroundIds.all { it in FocusBackgrounds.ids }) { "备份设置无效" }
    }

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private inline fun <T> JSONArray.read(block: (JSONObject) -> T): List<T> =
        (0 until length()).map { block(getJSONObject(it)) }
}
