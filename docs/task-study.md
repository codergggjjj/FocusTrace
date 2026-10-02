# 待办学习详情

- 入口：待办卡片 → 编辑待办 → 学习详情。已完成待办也可以查看；新建但尚未保存的待办不显示入口。
- 顶部展示当前待办名称和完成状态，突出累计学习时长；专注次数、学习天数、平均单次时长为次级数据。
- 趋势支持含今天的近 7 天和近 30 天，复用统计页柱状图，点击柱子查看日期与时长。趋势范围只影响图表，累计指标和下方记录始终覆盖全部历史。
- 学习记录按当地日期倒序分组，显示起止时间、时长、历史任务名称快照、来源和备注摘要。普通记录打开原有报告，返回后回到学习详情；手动记录打开原有编辑窗口。列表右侧可确认删除已结束的普通或手动记录，正在休息的记录不可删除。
- “补录”默认绑定当前待办，允许调整关联；修改、删除或改绑后，详情及原有统计通过 Room Flow 自动更新。

## 数据与状态

沿用 `focus_sessions` 和已有统计口径：`taskId` 匹配、已有结束时间、状态为休息或结束、`focusSeconds > 300`。正常计时和手动记录同等计入。不改变数据库结构、计时或分心规则。

`FocusSessionDao.observeTaskStatistics` 按待办查询；`StatisticsRepository.taskStudy` 在后台聚合累计指标、日期分组，并调用原有 `summarizeStatistics` 生成趋势。没有单独保存统计结果。

新增路由 `task-study/{taskId}`，由 `TaskStudyViewModel` 通过 StateFlow 提供数据。趋势选择保存到 SavedStateHandle，列表使用带稳定 key 的 LazyColumn；日期/时区变化会刷新统计。待办被删除时显示明确提示，历史记录仍从统计页访问。

## 相关文件

- `TaskStudySummary.kt`：累计数据和趋势结果模型。
- `TaskStudyViewModel.kt`：范围选择、数据观察和手动记录操作。
- `TaskStudyScreen.kt`：详情页、记录列表、加载/空数据/错误状态。
- `FocusSessionDao.kt`、`StatisticsRepository.kt`：查询和聚合。
- `TaskEditScreen.kt`、`TodoScreen.kt`、`AppNavigation.kt`：入口、待办 ID 传递及报告返回。
- `TaskStudyRepositoryTest.kt`、`TaskStudyTest.kt`：数据口径、手动编辑、导航和重建回归。

## 验证

`assembleDebug`、`lintDebug`、`assembleDebugAndroidTest` 通过。API 36 模拟器上学习详情数据和界面两项测试通过，覆盖 7/30 天选择、重建恢复、手动编辑及改绑、普通报告返回、两类记录删除、累计更新和浅色/深色展示。与删除、手动记录和统计一起运行的 18 项相关测试全部通过。
