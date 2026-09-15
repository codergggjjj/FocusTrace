# FocusTrace · 专迹

记录每一次专注，也看见每一次分心。

基于 [design.md](design.md) 的完全本地原生 Android 应用。已完成第一阶段基础框架，待办 CRUD、分类交互、番茄钟、正向计时、前后台分心检测及单次专注报告。

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
    local/entity/     四张实体表
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
app/schemas/         Room 版本化数据库结构
app/src/androidTest/ 导航、Room、DataStore 验证
docs/                阶段说明与验证结果
```

数据沿 `Room / DataStore → Repository → ViewModel / StateFlow → Compose` 流动。依赖容器使用应用上下文，数据库及 DataStore 为应用级单例，不在主线程访问数据库。

## 第一阶段范围

- 四个底部 Tab：待办、专注、统计、我的；支持重复点击、切换及 Activity 重建恢复。
- 四张设计实体表及 DAO，Room schema 导出，数据库首次创建时初始化五个分类。
- 分类删除将任务分类置空，任务删除保留专注历史，专注记录删除级联清理其分心记录。
- 默认设置：番茄 25 分钟、休息 5 分钟、分心阈值 3 秒、自动休息开启、自动下一轮关闭、声音开启、主题跟随系统。
- 基础页面读取真实本地数据，提供加载和错误状态，不插入演示任务或专注记录。
- `AppLifecycleObserver` 已注册到 ProcessLifecycleOwner；进入后台时持久化离开状态，返回时记录达到阈值的分心事件。

已支持创建、编辑、完成/恢复、确认删除待办，选择默认或自定义分类，并按分类筛选。标题不能为空，目标时长为 1–1440 分钟，编辑草稿支持 Activity 重建恢复。

番茄钟支持关联待办或自由专注、自定义专注/休息时长、暂停继续、提前结束、休息倒计时及本轮摘要。时间锚点持久化到 Room，数据库已升级为版本 5，提供 1→2→3→4→5 迁移。

正向计时从零开始，不设计划截止时间，支持暂停、恢复与结束保存，不自动进入休息。

分心检测默认阈值 3 秒，支持严格 0 秒、普通 3 秒、宽松 10 秒和自定义值。只有正在专注时记录，暂停、休息和空闲不记录；达到阈值的离开时间从有效专注中扣除。本轮可查看次数、总时间及离开/返回明细。

单次专注报告展示任务名称快照、专注模式、计划及有效时长、起止时间、分心次数/总时长/平均时长/首次分心、专注率和明细。结束后自动进入，专注首页可重新查看本轮报告；支持页面重建恢复。

日/周/月统计支持历史区间、专注与分心汇总、每日趋势柱形图和分类汇总，详见 [统计阶段说明](docs/statistics.md)。当前尚未实现热力图及完整设置编辑。应用不申请网络权限，不包含账号、服务器或云同步；关闭 Android 自动备份。

下一阶段按设计书开发专注热力图。重复待办和提醒仍留待后续阶段。

界面采用暖白与低饱和番茄红的简约主题，支持深色模式；详见 [视觉优化说明](docs/ui-polish.md)。

创建和编辑待办可选择番茄钟或正向计时；正向计时无需目标时长。在专注页选择待办时，会应用其计时模式，番茄钟同时应用目标分钟数。已有待办升级后保持番茄钟。
