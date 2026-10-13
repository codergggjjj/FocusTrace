package com.focustrace.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ManualTimeSelector(hour: Int, minute: Int, enabled: Boolean, onChange: (Int, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("开始时间", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimePartDropdown(hour, 24, "小时", "时", "manual-start-hour", enabled,
                Modifier.weight(1f)) { onChange(it, minute) }
            TimePartDropdown(minute, 60, "分钟", "分", "manual-start-minute", enabled,
                Modifier.weight(1f)) { onChange(hour, it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePartDropdown(value: Int, count: Int, label: String, unit: String, tag: String,
    enabled: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (48.dp * (value - 2).coerceAtLeast(0)).roundToPx() })
    ExposedDropdownMenuBox(expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(value = value.toString().padStart(2, '0'), onValueChange = {},
            readOnly = true, enabled = enabled, singleLine = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.menuAnchor().fillMaxWidth().testTag(tag))
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            scrollState = scroll, modifier = Modifier.heightIn(max = 288.dp)) {
            repeat(count) { option ->
                key(option) {
                    DropdownMenuItem(text = { Text(option.toString().padStart(2, '0') + " " + unit) },
                        onClick = { onSelect(option); expanded = false },
                        trailingIcon = {
                            if (option == value) Icon(Icons.Outlined.Check, contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary)
                        }, modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-option-$option"))
                }
            }
        }
    }
}
