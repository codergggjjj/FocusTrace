# 删除专注记录

入口：统计 → 查看专注记录、待办 → 编辑待办 → 学习详情的记录右侧删除图标，或单次专注报告底部“删除专注记录”。普通计时与手动补录均可删除；手动记录编辑窗口仍保留原来的删除入口。

统一入口先显示任务名称快照、开始日期时间和专注时长，点击“确认删除记录”才执行。取消保留原记录。确认状态在 Activity 重建后恢复；删除期间禁用重复操作，失败在确认窗口提示。

## 数据与保护

- 沿用 `focus_sessions` 表，无新字段、数据库升级或依赖。
- `FocusSessionDao.deleteFinished` 用一条受条件保护的 SQL 删除：匹配 ID、结束时间非空、`status = FINISHED(4)`。数据库执行时再次检查条件，避免页面状态过期而删掉活动计时。
- `FOCUSING(1)`、`PAUSED(2)`、`RESTING(3)` 均拒绝删除；正在休息的已完成专注可在报告中看到原因，结束休息后再删除。
- `FocusRepository.deleteRecord` 统一处理普通与手动记录，缺失或尚未结束时给出明确错误。
- 保留所属待办，沿用既有 `ON DELETE CASCADE` 清理该条记录的分心明细；其余记录不变。
- 原有 Room Flow 通知记录、报告、日/周/月/年统计、待办累计及专注习惯刷新，无独立缓存或额外统计表。不改变计时、分心判断和严格大于 5 分钟的有效记录规则。

## 相关文件

- `FocusSessionDao.kt`、`FocusRepository.kt`：受保护的删除查询及统一访问入口。
- `DeleteFocusRecordDialog.kt`：共用删除按钮与确认窗口。
- `StatisticsDetails.kt`、`StatisticsScreen.kt`、`StatisticsViewModel.kt`：历史列表删除、确认状态及操作。
- `TaskStudyScreen.kt`、`TaskStudyViewModel.kt`：学习详情删除及累计自动更新。
- `FocusResultScreen.kt`、`FocusResultViewModel.kt`：报告删除和返回。
- `FocusRecordDeletionRepositoryTest.kt`、`FocusRecordDeletionTest.kt`、`TaskStudyTest.kt`：级联、统计通知、计时保护、确认/取消、重建及三个入口的回归验证。

## 本次验证

`assembleDebug`、`lintDebug` 和 `assembleDebugAndroidTest` 通过。在 API 36 模拟器上运行删除数据测试 3 项、历史删除界面 1 项、学习详情数据/界面 2 项，以及既有手动记录 3 项和统计 9 项，共 18 项全部通过。界面验证包含浅色/深色、滚动、确认/取消、Activity 重建和报告删除后的返回。

本次未重跑全量仪器测试；既有全量测试中的 UI 自动化连接和过时短记录断言问题仍以 `screen-lock-exemption.md` 的验证说明为准。删除功能相关测试没有出现这些失败。
