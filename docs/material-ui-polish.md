# Material 3 界面统一（2026-09-17）

本次只调整 Compose 展示层、系统栏外观和 UI 测试；未修改数据库、Repository、ViewModel、计时状态机、统计公式、分心判定或导航关系。没有新增依赖。

## 文件和改动

| 文件（相对 app/src/main/java/com/focustrace） | 改动 |
| --- | --- |
| MainActivity.kt | 状态栏和导航栏图标跟随应用的浅色/深色主题 |
| ui/theme/Theme.kt | 主色 #D6336C、浅粉容器、灰白背景；28sp 页面标题、32sp 核心数字、18sp 待办标题、16sp 小节标题；统一圆角 |
| ui/components/BasePage.kt | 简化标题装饰；统一间距，卡片无边框、无投影 |
| ui/todo/TodoScreen.kt | 官方 FAB；任务行使用 Checkbox 和 48dp FilledIconButton；稳定 key 的 LazyColumn；为 FAB 预留空间，避免覆盖任务 |
| ui/todo/TaskEditScreen.kt | 保存使用 Button，补录入口使用 FilledTonalButton |
| ui/statistics/StatisticsScreen.kt | 日/周/月使用 SegmentedButton；普通屏幕提供官方 DatePicker，小屏、横屏或大字体复用日期输入框 |
| ui/statistics/StatisticsCards.kt | 放大专注时长，弱化辅助指标；图表切换使用 SegmentedButton；自动定位延后一帧，避免嵌套懒列表重复测量 |
| ui/statistics/StatisticsDetails.kt | 补录按钮使用 FilledTonalButton，时长使用普通文字色 |
| ui/statistics/ManualRecordDialog.kt | 保存按钮统一为 Button，补录计算与校验不变 |
| ui/settings/ProfileScreen.kt | Section + ListItem + Divider 的标准设置列表，自动化使用 Switch 并调用原有保存方法 |
| ui/settings/SettingsEditor.kt | 保存按钮统一为 Button |

底部保留原有 NavigationBar/NavigationBarItem，自动继承新的主题色。页面间距约 20dp，区块间距 24dp，卡片内边距 16dp。长待办标题允许换行。

## 测试文件

- `app/src/androidTest/java/com/focustrace/FoundationTest.kt`：适配 FAB 的无障碍标签和日历的手动输入入口，保留原来的业务断言。
- `app/src/androidTest/java/com/focustrace/UiPolishTest.kt`：新增浅色/深色页面、待办编辑补录入口、统计切换、日期弹窗、设置开关持久化验证；支持通过 capturePolish 参数保存模拟器截图。

## 验证

- API 36 模拟器全量 49 项测试通过，覆盖待办、番茄钟/正向计时、手动补录增删改、统计、报告、分心及数据库迁移。
- 最后的配色、系统栏和紧凑布局调整后，补测 320×560dp、1.3 倍字体的浅色/深色交互通过；截图确认任务开始按钮与 FAB 不重叠，日期输入可用。
- 560×320dp 横屏浅色/深色交互通过，截图确认日期弹窗和任务操作可用；测试结束后恢复模拟器原有尺寸及字体倍率。
- 最终 `assembleDebug`、`lintDebug` 通过。Lint 0 错误、20 项警告，涉及已有 SDK/依赖版本及图标资源提示；本次未升级依赖。
- 截图和测试产物保存在忽略的 `app/build`，不纳入源码。
- UI 验证完成后按用户要求打包为 v1.0.4（versionCode 6），发布信息见 release-v1.0.4.md。模拟器验证不能代替所有厂商真机适配。
