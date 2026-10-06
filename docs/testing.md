# 分层测试规范

目标：每次只执行能够验证当前改动的最小测试集合。验证通过后停止，不为了“更保险”反复全量运行。

## 按改动选择范围

| 改动 | 默认验证 | 设备需求 |
| --- | --- | --- |
| 文档 / 注释 / 测试规范 | 内容、链接、脚本语法 | 无 |
| 文案 / 色彩 / 间距 | 增量构建 + lint，受影响页面检查 | 必要时一次 |
| 统计、时长计算 | 对应逻辑类 / 方法，必要的边界 | 当前部分逻辑仍在 androidTest |
| 页面操作 / 导航 | 构建 + lint + 对应 UI 用例 | 已连接专用设备 |
| Room / 备份 | 对应迁移、DAO、备份一致性测试 | 已连接专用设备 |
| 计时 / 息屏 / 通知 | 对应真实设备状态专项 | 已连接专用设备 |
| 测试框架 / 跨模块基础设施 | 先专项，必要时完整组合验证 | 明确扩大范围 |

不改变严格大于五分钟规则，不删除失败用例或降低业务断言来获得绿色结果。

## 统一入口

从仓库根目录执行，需要既有 JDK 17、Android SDK 配置。脚本不写入本机路径。

```powershell
# 默认仅增量构建和 lint；不会连接、启动模拟器
./scripts/test.ps1

# 先看计划，不执行构建或设备操作
./scripts/test.ps1 -Scope Device -Tests com.focustrace.StatisticsHourLabelTest -Plan

# 单个测试方法（优先）
./scripts/test.ps1 -Scope Device -Tests 'com.focustrace.StatisticsTest#calendarRangesUseMondayLeapMonthAndDst'

# 相关类；多个设备时指定序列号
./scripts/test.ps1 -Scope Device -Tests 'com.focustrace.FocusCalendarUiTest,com.focustrace.StatisticsHourLabelTest' -Serial emulator-5554

# 仅在明确需要完整回归时使用，单独设置总预算
./scripts/test.ps1 -Scope Full -TimeoutSeconds 900
```

Device 不接受空测试范围。设备检查在构建前完成，没有就绪设备立即停止，不自动启动新的模拟器。安装使用覆盖安装，不卸载、不清空数据；仪器测试本身可能创建、编辑、删除数据，务必使用专用测试设备。

一次调用合并所需构建任务。设备专项默认总预算 180 秒（不含构建），超时停止本次测试并保留日志，不自动重试。构建进度由 Gradle 输出；设备测试每约 10 秒显示耗时，完整日志在 `app/build/test-runs/`。adb 返回码为 0 不代表测试成功，脚本还要求非空的 JUnit 成功结果，拒绝崩溃、失败及空测试集。结束后打开 App，不关闭虚拟机。

## 选择用例参考

- 日历布局：`FocusCalendarUiTest`；小时刻度：`StatisticsHourLabelTest`。
- 统计聚合：`StatisticsTest`；报告计算：`FocusReportTest`。
- 计时：`PomodoroTest`；分心：`DistractionTest`。
- 实际切屏 / 息屏：`LifecycleIntegrationTest`、`ScreenFocusMonitorTest` 的相关方法。
- 通知操作：`NotificationTest`。
- 备份：`BackupFormatTest`、`BackupRepositoryTest`。
- 学习目标：`LearningGoalsTest`、`LearningGoalsUiTest`。
- 删除记录：`FocusRecordDeletionRepositoryTest`、`FocusRecordDeletionTest`。
- 待办学习详情：`TaskStudyRepositoryTest`、`TaskStudyTest`。
- 数据迁移：`TaskTimerMigrationTest`，按具体 Migration 选择方法。

这是一份选用参考，不是每次都要执行的清单。UI 截图与大量滚动性能测试只在相应变更时使用。

## 失败与停止规则

1. 先保存首次失败日志，区分产品缺陷、过期断言、设备未唤醒或测试框架连接错误。
2. 修复后只跑失败方法及直接受影响的回归用例；没有新增证据时不重复扩大范围。
3. 禁止靠无限延时、自动多次重试或忽略用例掩盖失败。
4. 验证通过即结束；交付说明实际范围、结果与未验证项。

## 当前限制

本次只规范测试流程和增加执行入口，不迁移现有测试、不修改应用功能，也不宣称修复全部旧测试。旧 FoundationTest 短会话报告断言与五分钟规则的冲突，以及组合运行时的 UiAutomation 连接问题，仍需独立修复。全量命令可能暴露这些已知问题，应原样报告失败。

后续新增纯逻辑测试优先放在 `src/test`；现有混合 Room / Android / Compose 测试应逐步拆分，不能仅移动目录就认为已经支持本机运行。
