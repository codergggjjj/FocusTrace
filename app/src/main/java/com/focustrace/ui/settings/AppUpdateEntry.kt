package com.focustrace.ui.settings

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private const val LATEST_RELEASE_URL = "https://github.com/codergggjjj/FocusTrace/releases/latest"

@Composable
internal fun AppUpdateEntry() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val version = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?.let { "v$it" } ?: "未知"
        } catch (_: PackageManager.NameNotFoundException) { "未知" }
    }
    var showing by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    SettingRow(Icons.Outlined.SystemUpdate, "应用更新", "当前版本 $version", true,
        modifier = Modifier.testTag("profile-app-update")) {
        error = null
        showing = true
    }
    if (showing) AlertDialog(
        modifier = Modifier.testTag("app-update-dialog"),
        onDismissRequest = { showing = false },
        icon = { androidx.compose.material3.Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text("应用更新") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本：$version", style = MaterialTheme.typography.titleSmall)
                Text("在 GitHub 查看最新版本、更新说明并下载 APK。")
                Text("下载后直接覆盖安装，保留学习数据。请勿卸载旧版。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("app-update-open-release"), onClick = {
                error = null
                try {
                    uriHandler.openUri(LATEST_RELEASE_URL)
                    showing = false
                } catch (_: ActivityNotFoundException) {
                    error = "无法打开浏览器，请安装或启用浏览器后重试。"
                } catch (_: SecurityException) {
                    error = "浏览器打开受限，请检查设备设置后重试。"
                }
            }) { Text("查看更新与下载") }
        },
        dismissButton = { TextButton(onClick = { showing = false }) { Text("关闭") } }
    )
}
