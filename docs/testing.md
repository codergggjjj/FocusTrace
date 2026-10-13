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

## e2e 技能使用约定

采用 [tester-army/e2e 的 e2e 技能](https://github.com/tester-army/e2e/tree/449fa93670ee4da77086cfe535af047f86f4755d/skills/e2e)，本机已安装到 Codex 用户级技能目录。安装内容为 `SKILL.md` 和配套参考文档；当前项目尚未配置 e2e 运行器、移动端引擎、模型认证或 MCP 服务，不能将技能安装视为 UI 测试已经执行。默认测试入口仍是 `scripts/test.ps1`。

后续确需使用时，先读取技能与本规范，再按 Android 移动端配置接入；运行器属于开发工具，不加入 App 运行依赖。不要使用向导默认的 Web 配置改变原生 Android 工程。

- 范围：先验证 2～3 条受影响的关键流程，例如待办排序、补录/删除后统计更新、日历日期切换。已有专项能够充分验证时复用它，不追加重复端到端检查。
- 执行：指定具体文件或用例，`workers: 1`、`retries: 0`，首个失败后停止并保留日志；总设备预算保持 180 秒，禁止以不断延长超时或重复运行代替定位。关闭空测试集通过选项，不默认进行全 App 探索、多设备或多模型重复执行。
- 断言：具体顺序、分钟数、日期、记录数使用确定性断言；计时边界、分心扣时、迁移和数据保留继续使用现有逻辑/数据库测试。缓存回放仍需核验结果，AI 判断不会因缓存而免除模型调用。
- 定位：复用 Compose 语义和 `testTag`。外部 Android 引擎需要根节点暴露 `testTagsAsResourceId`，接入运行器时再验证适配，不假定现有 Compose 测试标记已对外可见。
- 数据与产物：只使用专用设备及可识别的测试数据，不调用卸载、`app.clearState()` 或清空数据库来处理失败。运行结束保留模拟器和 App；报告、缓存、截图、认证与密钥不提交。实际接入时禁用工具遥测，未经用户明确授权不发送外部反馈。

## 当前限制

本次只规范测试流程和增加执行入口，不迁移现有测试、不修改应用功能，也不宣称修复全部旧测试。旧 FoundationTest 短会话报告断言与五分钟规则的冲突，以及组合运行时的 UiAutomation 连接问题，仍需独立修复。全量命令可能暴露这些已知问题，应原样报告失败。

后续新增纯逻辑测试优先放在 `src/test`；现有混合 Room / Android / Compose 测试应逐步拆分，不能仅移动目录就认为已经支持本机运行。
