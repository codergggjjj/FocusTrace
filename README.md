# FocusTrace · 专迹

记录每一次专注，也看见每一次分心。

[下载 FocusTrace v1.0.9 APK](https://github.com/codergggjjj/FocusTrace/releases/download/v1.0.9/FocusTrace-v1.0.9-performance.apk) · [发布范围与说明](docs/release-v1.0.9.md)

此版本使用 debug 签名，面向 Android 8.0 及以上，按当前已实现功能发布。

基于 [design.md](design.md) 的完全本地原生 Android 应用。已完成待办 CRUD、番茄钟、正向计时、前后台分心检测、常驻计时与完成通知、暂停时长提示、单次专注报告和日/周/月/年统计。仅严格超过 5 分钟的单次专注会生成用户可见记录并计入统计，5 分钟整及更短的会话不显示。

## 在 Android Studio 中运行

1. 使用 Android Studio 打开项目根目录，等待 Gradle Sync 完成。
2. 使用 JDK 17，安装 Android SDK Platform 34 和 Build Tools 34.0.0。
3. 由 Android Studio 创建本机 `local.properties`，不要提交 SDK 绝对路径。
4. 选择 Android 8.0 / API 26 或更高版本的模拟器或真机，运行 `app`。

Windows 命令行验证：

```powershell
.\gradlew.bat assembleDebug lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。发布签名和应用商店发布不属于此阶段。

## 技术栈与版本

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin / Gradle | 8.5.2 / 8.7 |
| Kotlin / Compose Compiler | 1.9.24 / 1.5.14 |
| KSP | 1.9.24-1.0.20 |
| Compose BOM（含 Material 3） | 2024.06.00 |
| Core KTX / Activity Compose | 1.13.1 / 1.9.0 |
| Lifecycle（ViewModel、Compose、Process） | 2.8.3 |
| Navigation Compose | 2.7.7 |
| Room | 2.6.1 |
| Preferences DataStore | 1.1.1 |
| Coroutines | 1.8.1 |
| AndroidX Test Runner / JUnit 扩展 | 1.6.1 / 1.2.1 |

使用固定版本以便复现构建。Compose 编译器与 Kotlin 的组合参考 [官方兼容表](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)。compileSdk / targetSdk 为 34，minSdk 为 26；后续面向应用商店发布前单独升级和验证目标 SDK。

## 目录与数据流

```text
app/src/main/java/com/focustrace/
  data/
    local/entity/     本地实体（分类表仅保留升级兼容结构）
    local/dao/        异步读写与 Flow 查询
    datastore/        七项用户设置
    repository/       Task、Focus、Statistics、Settings
    AppContainer.kt   应用级依赖容器
  ui/
    todo/ focus/ statistics/ settings/  页面及独立 ViewModel
    components/      加载、错误和基础页面组件
    theme/           Material 3 浅色、深色主题
  navigation/        四个 Tab、状态恢复
  lifecycle/         进程生命周期观察者和有序事件协调器
  notification/      前台计时服务、常驻通知与完成提醒
app/schemas/         Room 版本化数据库结构
app/src/androidTest/ 导航、Room、DataStore 验证
docs/                阶段说明与验证结果
```

数据沿 `Room / DataStore → Repository → ViewModel / StateFlow → Compose` 流动。依赖容器使用应用上下文，数据库及 DataStore 为应用级单例，不在主线程访问数据库。

## 第一阶段范围

- 四个底部 Tab：待办、专注、统计、我的；支持重复点击、切换及 Activity 重建恢复。
- Room schema 导出并提供完整版本迁移；v1.0.1 升级会清空旧分类及任务分类关联，同时保留任务和专注历史。
- 任务删除保留专注历史，专注记录删除级联清理其分心记录。
- 默认设置：番茄 25 分钟、休息 5 分钟、分心阈值 3 秒、自动休息开启、自动下一轮关闭、声音开启、主题跟随系统。
- 基础页面读取真实本地数据，提供加载和错误状态，不插入演示任务或专注记录。
- `AppLifecycleObserver` 已注册到 ProcessLifecycleOwner；进入后台时持久化离开状态，返回时记录达到阈值的分心事件。

已支持创建、编辑、完成/恢复、确认删除待办。待办页提供今日概览、待完成/已完成筛选、今日专注次数和紧凑报告入口；标题不能为空，目标时长为 1–1440 分钟，编辑草稿支持 Activity 重建恢复。

番茄钟支持关联待办或自由专注、自定义专注/休息时长、暂停继续、提前结束、休息倒计时及本轮摘要。时间锚点持久化到 Room，数据库已升级为版本 6，提供 1→2→3→4→5→6 迁移。

正向计时从零开始，不设计划截止时间，支持暂停、恢复与结束保存，不自动进入休息。

专注中的页面可显示沉浸式背景，内置湖畔、森林与海边三张风景插画；“我的 → 专注背景”可关闭背景、选择固定图片、勾选随机图片或导入自己的照片。自定义照片优先于内置图片，复制并缩放到应用私有目录；随机图片每轮选定一次，暂停、返回页面或重建后保持不变。详见 [专注背景说明](docs/focus-background.md)。

专注或休息期间显示常驻通知，可直接暂停、继续和结束；番茄自然完成及休息结束会发送完成提醒。Android 13 及以上首次运行会申请通知权限，提示音可在“我的”中关闭，详见 [通知说明](docs/notifications.md)。

分心检测默认阈值 3 秒，支持严格 0 秒、普通 3 秒、宽松 10 秒和自定义值。只有正在专注时记录，暂停、休息和空闲不记录；达到阈值的离开时间从有效专注中扣除。本轮可查看次数、总时间及离开/返回明细。

单次专注报告突出任务、专注时长、专注率、分心次数/时长和起止时间；分心明细按需打开。结束后自动进入，专注首页可重新查看本轮报告；支持页面重建恢复。

日/周/月/年统计支持前后周期对比、按待办筛选、专注趋势、待办分布、按实际覆盖小时统计的时间分布、连续专注、月度热力图和页内学习记录，详见 [统计阶段说明](docs/statistics.md)。所有汇总均来自同一专注记录数据源，手动补录与计时记录使用相同口径。应用不申请网络权限，不包含账号、服务器或云同步；关闭 Android 自动备份。

下一阶段建议开发重复待办、提醒以及本地备份恢复。

界面采用粉白与浅蓝搭配的简约主题，支持深色模式；详见 [视觉优化说明](docs/ui-polish.md)。

创建和编辑待办可选择番茄钟或正向计时；正向计时无需目标时长。在专注页选择待办时，会应用其计时模式，番茄钟同时应用目标分钟数。已有待办升级后保持番茄钟。

待办卡片支持一键「开始」：按任务的计时模式与目标时长启动并跳转专注页；休息时长和自动轮转使用当前设置。已完成待办不显示开始按钮，当前有专注、暂停或休息时会阻止新建计时。

导航已调整为待办、统计、我的三个入口。专注模式仅通过任务开始或恢复已有计时进入，计时和报告隐藏底部导航。返回待办不结束计时，正在进行的计时可通过待办页「返回计时」恢复；再次开始不会覆盖当前计时。最近一次报告也可从待办页重新打开。

统计页支持指定日期或月份查询累计学习时间，并查看每次学习的开始、结束时间及详细报告。

热力图随统计区间显示对应月份，点击日期会直接切换到该日并同步刷新整页统计。我的→编辑设置可修改默认番茄/休息时长、分心阈值、自动休息、自动下一轮、完成提示音及浅色/深色/跟随系统；保存后持久化，默认番茄时长用于新建待办。
