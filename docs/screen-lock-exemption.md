# 锁屏与息屏豁免

- 专注期间锁屏或息屏继续累计有效专注，不增加分心次数和分心时间。
- 亮屏但尚未解锁仍然豁免；解锁返回 FocusTrace 后正常计时。
- 先切换其他应用再锁屏，只结算锁屏之前的离开；解锁后若仍在其他应用，重新按阈值计算离开。
- 暂停仍不累计，休息仍按原规则计时；锁屏期间不自动开始新一轮专注。
- 使用应用级动态屏幕广播、DisplayManager 显示状态监听、PowerManager.isInteractive 和 KeyguardManager.isKeyguardLocked，结合 ProcessLifecycleOwner。显示关闭和 Doze 状态同样豁免，无需新增权限、依赖或数据库迁移。
- 常驻计时服务沿用每秒的计时刷新顺便校验屏幕状态，避免后台屏幕广播漏收后一直停留在离开状态；只在前后台/豁免状态发生变化时入队，不增加重复数据库操作。
- 广播和生命周期事件沿用有序队列、捕获时间戳与数据库事务，避免重复结算。
- 系统回调有延迟；若广播和显示状态回调同时延迟，服务在下一次计时刷新时纠正状态，不反推未观测到的精确息屏时刻。进程被系统终止后无法继续接收屏幕状态变化，跨越该期间的切屏分类不能保证完整。

## 验证
- assembleDebug、lintDebug 成功；无 Lint 错误，8 项既有版本提示。
- 40 项设备测试全部通过，其中包含真实息屏/唤醒测试、离开与锁屏分段测试、锁屏番茄完成测试。

## 2026-10-01 后台息屏回归

- 回归测试先验证了旧实现的缺口：屏幕广播未送达时，即使设备已息屏也不会切换到豁免状态。补充显示状态监听后，同一测试通过。
- 新增实际切换系统设置应用、息屏、解锁和返回的设备测试：息屏期间有效专注增加，分心次数/时长不增加；息屏前和解锁后的离开分别结算。
- `assembleDebug`、`lintDebug` 通过（Lint 0 错误、20 项版本/资源等提示）；单独运行的 20 项屏幕状态、生命周期、分心、计时和通知设备测试全部通过。
- 同时尝试了完整 62 项设备测试，50 项返回通过状态、12 项失败，最后测试框架因 UiAutomation 连接状态异常结束，不能宣称全量通过。失败项如下：
  - `FoundationTest.pomodoroUiPauseResumeAndFinish`、`stopwatchUiModePauseAndFinish` 仍等待短会话生成结束报告，和当前严格大于 5 分钟的规则不一致。
  - `FoundationTest.naturalCompletionOpensReportAndKeepsRestRunning` 使用 8 秒专注期待报告，同时出现 Compose 页面定位错误。
  - `FoundationTest.todoStartsStopwatchDirectlyAndProtectsPausedSession`、`todoStartsPomodoroDirectlyAndProtectsPausedSession`、`reportDisplaysPersistedMetricsAndSurvivesRecreation` 出现 UI 定位/等待失败。
  - `FoundationTest.realScreenOffDoesNotCreateDistraction`、`LifecycleIntegrationTest.sleepingInAnotherAppCountsOnlyUnlockedAbsenceAsDistraction`、`actualHomeReturnRecordsOnlyWhileFocusing`、`NotificationTest.ongoingNotificationSupportsPauseResumeAndFinish`、`naturalPomodoroCompletionPostsCompletionNotification`、`ScreenFocusMonitorTest.screenOffOutsideAppIsDetectedEvenWhenScreenBroadcastIsMissing` 出现 UiAutomation 重复注册或连接错误；后五项所属类单独复核均通过。
