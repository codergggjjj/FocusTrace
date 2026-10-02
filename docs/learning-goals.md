# 每日 / 每周学习目标

入口：“我的 → 学习目标”，或统计总览下方“学习目标 → 设置”。沿用粉色 Material 3 风格，设置窗口支持 Activity 重建恢复草稿、取消、保存中禁用及失败提示。

- 每日、每周目标独立开启和关闭，默认均未开启，不强行给用户分配目标。
- 自定义输入分钟数；每日目标 1–1440 分钟，每周 1–10080 分钟。开启时不允许保存空值、零、负数或越界值；关闭以 0 表示。
- 每日快捷选项为 1/2/3/4 小时，每周为 5/10/15/20 小时，仍可输入任意合法分钟数。
- 统计页显示今日、本周的实际学习时长、目标、进度、剩余时长和“已达标”。超额时保留真实学习时长，进度条最多 100%，剩余时长最低为零。
- 目标针对全部待办及自由专注，不受统计页历史周期、日期或待办筛选影响；卡片明确标注“今日 / 本周 · 全部待办”。当前不保存历史目标快照、不发送额外提醒。

## 数据与口径

仅在现有 Preferences DataStore 中增加 `daily_goal_minutes`、`weekly_goal_minutes`，不改变 Room schema。目标修改只原子更新这两个键，保留计时设置、主题和背景图。

进度由现有 `StatisticsRepository.statistics` 聚合结果转换得出，不另存累计或达标状态。普通计时和手动记录都参与；仅计入已有结束时间、状态为休息或结束、有效专注严格大于 300 秒的记录。分心和暂停已由原计时逻辑从有效专注扣除。

日期使用设备时区，本周从周一零点至下周一零点，采用半开区间。跨日整轮仍按开始日归属，保持现有统计口径。`StatisticsViewModel` 复用日历 Flow，在日期或时区变化后刷新；增删改记录由 Room Flow 推动更新。两个目标都关闭时跳过新增周查询；无关设置变化和历史筛选不触发目标查询重建。

本地备份包含目标设置；旧备份缺少字段时默认关闭。校验目标范围后才能恢复，不改变备份格式版本或已有数据关系。

## 修改文件

- `SettingsDataStore.kt`、`SettingsRepository.kt`：默认值、范围校验和目标持久化。
- `LearningGoals.kt`：目标进度、剩余量、百分比和达标状态模型。
- `StatisticsRepository.kt`：从原统计结果生成今日 / 本周目标进度。
- `LearningGoalsEditor.kt`：共用设置窗口及简洁时长显示。
- `ProfileScreen.kt`、`SettingsViewModel.kt`：设置入口与保存。
- `LearningGoalsCard.kt`、`StatisticsScreen.kt`、`StatisticsViewModel.kt`：进度卡片、观察状态和页内设置。
- `AppNavigation.kt`：向原统计 ViewModel 提供已有设置 Repository，路由不变。
- `BackupFormat.kt`：目标备份和旧文件兼容。
- `LearningGoalsTest.kt`、`LearningGoalsUiTest.kt`、`BackupFormatTest.kt`、`BackupRepositoryTest.kt`：边界、统一统计、设置与草稿恢复、记录删除更新及备份回归。

## 本次验证

`assembleDebug`、`lintDebug`、`assembleDebugAndroidTest` 通过；Lint 0 错误、20 项既有提示。在 API 36 模拟器上，目标逻辑与加载重试 5 项、目标界面 1 项、备份格式 4 项、备份恢复 1 项、统计 9 项、手动记录 3 项、删除保护 3 项、原设置编辑 1 项，共 27 项相关测试分别运行通过。

界面验证覆盖无效输入、保存、Activity 重建草稿/已保存值恢复、历史月份切换不影响目标、记录删除后的进度刷新、分别关闭目标和浅色/深色展示。最初混合运行的两个界面用例因模拟器自动息屏无法建立 Compose 页面，唤醒并保持充电亮屏后专项重跑通过。新增读取失败注入测试先复现重试超时，修复 Flow 重建后通过。

本次没有重跑全量仪器测试；原计时、分心和统计口径没有改变，验证限定上述相关范围。模拟器保持开启，并安装、打开本次构建的 App。
