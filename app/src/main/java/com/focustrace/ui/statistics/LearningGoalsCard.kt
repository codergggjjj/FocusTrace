package com.focustrace.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focustrace.statistics.LearningGoalProgress
import com.focustrace.statistics.LearningGoalsSummary
import com.focustrace.ui.components.learningGoalDuration

@Composable
internal fun LearningGoalsCard(summary: LearningGoalsSummary, onEdit: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("learning-goals-section")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("学习目标", style = MaterialTheme.typography.titleMedium)
                    Text("今日 / 本周 · 全部待办", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit, modifier = Modifier.testTag("learning-goals-edit")) { Text("设置") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                GoalProgress("今日", "daily", summary.daily, Modifier.weight(1f))
                GoalProgress("本周", "weekly", summary.weekly, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoalProgress(label: String, tag: String, progress: LearningGoalProgress, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (progress.enabled) learningGoalDuration(progress.focusSeconds) else "未开启",
            modifier = Modifier.testTag("goal-$tag-progress"), style = MaterialTheme.typography.titleLarge)
        if (progress.enabled) {
            Text("目标 ${learningGoalDuration(progress.targetSeconds)}", Modifier.testTag("goal-$tag-target"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).semantics {
                    contentDescription = "$label 学习目标完成 ${progress.percent}%"
                }, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
            Text(if (progress.reached) "已达标" else "${progress.percent}% · 还差 ${learningGoalDuration(progress.remainingSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("设置一个小目标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
