# FocusTrace 原生 Android APP 项目设计书

## 1. 项目概述

### 1.1 项目名称

**FocusTrace**

中文名建议：

**专迹**

产品标语：

> 记录每一次专注，也看见每一次分心。

## 1.2 项目定位

FocusTrace 是一款 Android 专注管理 APP。

产品功能整体参考番茄 ToDo 一类专注应用的设计思路，围绕：

- 待办事项
- 番茄钟
- 正向计时
- 专注记录
- 每日/每周/月度统计
- 专注热力图
- 个性化专注设置

进行设计。

FocusTrace 的核心特色是：

> 用户正在进行专注任务时，如果将 FocusTrace 切换到后台，则自动记录这次分心行为。

记录：

```
离开 APP 的时间
返回 APP 的时间
离开持续时间
分心次数
分心总时间
```

从而不仅统计：

> 我今天学习了多久？

还可以统计：

> 我今天专注过程中分心了多少次？

> 总共因为切屏浪费了多少时间？

> 一般专注多久以后开始第一次分心？

# 2. 第一版产品原则

第一版 FocusTrace 定位为：

> **完全本地运行的 Android APP**

不需要：

- Spring Boot
- Vue
- MySQL
- Web 服务器
- JWT
- 用户登录
- 云端同步
- AI
- 社交功能
- 自习室

用户的数据全部保存在：

```
Android 手机本地
```

这样可以优先完成 APP 最核心的功能。

未来如果需要：

```
账号登录
多设备同步
数据备份
好友系统
在线自习室
```

再添加后端。

# 3. 技术栈

## 3.1 开发语言

```
Kotlin
```

## 3.2 UI

使用：

```
Jetpack Compose
```

不采用传统：

```
XML Layout
```

界面全部使用 Compose 编写。

## 3.3 APP 架构

采用：

```
MVVM
```

整体结构：

```
Compose UI
      ↓
ViewModel
      ↓
Repository
      ↓
Room / DataStore
```

## 3.4 本地数据库

使用：

```
Room
```

保存：

- 待办
- 分类
- 专注记录
- 分心记录

Room 本质上是在 SQLite 之上提供更适合 Android 应用的数据访问层。

## 3.5 用户设置

使用：

```
DataStore
```

保存：

- 默认番茄时长
- 默认休息时长
- 分心判定阈值
- 自动开始休息
- 自动开始下一轮
- 主题设置
- 声音设置

## 3.6 异步处理

使用：

```
Kotlin Coroutines
+
Flow
```

## 3.7 页面导航

使用：

```
Navigation Compose
```

## 3.8 APP 前后台检测

使用：

```
Android Lifecycle

+

ProcessLifecycleOwner
```

监听整个应用：

```
进入前台
进入后台
```

而不是只监听某一个 Activity。

# 4. 总体架构

```
graph TD

A[FocusTrace Android APP]

A --> B[Jetpack Compose UI]

B --> C[ViewModel]

C --> D[Repository]

D --> E[Room Database]

D --> F[DataStore]

G[ProcessLifecycleOwner]
G --> H[AppLifecycleObserver]

H --> C

I[Notification]
I --> C
```

整个 APP 不依赖服务器。

# 5. 核心功能模块

```
graph TD

A[FocusTrace]

A --> B[待办]
A --> C[专注]
A --> D[分心检测]
A --> E[统计]
A --> F[设置]

B --> B1[创建待办]
B --> B2[编辑待办]
B --> B3[分类]
B --> B4[完成待办]
B --> B5[重复待办]

C --> C1[番茄钟]
C --> C2[正向计时]
C --> C3[休息]
C --> C4[专注记录]

D --> D1[进入后台]
D --> D2[返回前台]
D --> D3[分心次数]
D --> D4[分心时间]

E --> E1[今日统计]
E --> E2[周统计]
E --> E3[月统计]
E --> E4[热力图]
E --> E5[分心分析]

F --> F1[番茄时长]
F --> F2[休息时长]
F --> F3[分心阈值]
F --> F4[主题]
```

# 6. APP 页面结构

底部设置四个 Tab：

```
待办

专注

统计

我的
```

对应：

```
graph TD

A[MainScreen]

A --> B[TodoScreen]

A --> C[FocusHomeScreen]

A --> D[StatisticsScreen]

A --> E[ProfileScreen]
```

# 7. 待办模块

## 7.1 待办首页

例如：

```
FocusTrace

今天
9月14日 星期一


今日专注

1h 42min


今日待办

□ 算法训练                 60 min

□ Android实验              45 min

✓ 英语单词                 30 min

□ 数据库复习               60 min


              +
```

# 8. 创建待办

用户点击：

```
+
```

进入：

```
创建待办


名称

[ 算法训练                 ]


分类

[ 学习                     ]


目标专注时间

[ 60 min                   ]


重复

[ 每天                     ]


提醒

[ 20:00                    ]


           保存
```

# 9. 待办数据

每个待办包含：

```
标题

分类

目标专注时间

已专注时间

完成状态

重复方式

提醒时间

创建时间
```

# 10. 分类功能

默认提供：

```
学习

工作

阅读

运动

其他
```

允许用户自定义：

```
算法

Android

数据库

英语
```

统计页面可以按分类统计：

```
本周

算法          420 min

Android       210 min

数据库        150 min

英语           90 min
```

# 11. 专注模式

点击一个待办：

```
算法训练
```

弹出：

```
开始专注


番茄钟
25 min


正向计时


取消
```

# 12. 番茄钟

默认：

```
专注

25 min


休息

5 min
```

用户可以修改为：

```
50 + 10

40 + 10

60 + 15
```

# 13. 专注页面

例如：

```
算法训练


        24:36


       正在专注


       ━━━━━━━


        暂停


今日已专注

1h 42min
```

页面尽量保持简单。

专注过程中不显示复杂数据，降低干扰。

# 14. 正向计时

正向计时从：

```
00:00
```

开始：

```
00:01

00:02

...

42:31
```

直到用户主动结束。

适合：

- 刷题
- 看书
- 写代码
- 长时间学习

# 15. 专注状态

定义：

```
enum class FocusStatus {
    IDLE,
    FOCUSING,
    PAUSED,
    RESTING,
    FINISHED
}
```

其中：

```
IDLE

未专注


FOCUSING

正在专注


PAUSED

主动暂停


RESTING

休息


FINISHED

完成
```

# 16. 核心特色：分心检测

这是 FocusTrace 最重要的功能。

假设：

```
10:00

开始算法训练
```

正在专注：

```
18:32
```

用户打开：

```
微信
```

FocusTrace 从前台进入后台。

记录：

```
backgroundTime = 10:18:32
```

用户之后重新打开 FocusTrace：

```
foregroundTime = 10:21:15
```

计算：

```
10:21:15
-
10:18:32

=

163 秒
```

如果：

```
163 秒 >= 分心判定阈值
```

则：

```
分心次数 +1

分心时间 +163秒
```

并保存一条：

```
DistractionEvent
```

# 17. 分心判定规则

默认：

```
APP进入后台 >= 3秒

=

一次分心
```

设计三个模式。

### 严格

```
0秒
```

只要离开 APP 就算。

### 普通

```
3秒
```

推荐默认。

### 宽松

```
10秒
```

用户也可以自定义。

# 18. 为什么不直接监听 Activity

不能简单认为：

```
MainActivity.onStop()

=

用户切到其他APP
```

因为 Activity 生命周期还会受到：

- 页面重建
- 配置变化
- Activity 切换

等影响。

FocusTrace 需要判断：

> 整个 FocusTrace APP 是否进入后台。

因此使用：

```
ProcessLifecycleOwner
```

监听页面整个应用进程的生命周期。

# 19. AppLifecycleObserver

设计：

```
AppLifecycleObserver
```

负责：

```
进入后台

↓

记录 backgroundTime


返回前台

↓

记录 foregroundTime

↓

计算后台时间

↓

通知 FocusManager
```

结构：

```
sequenceDiagram

participant User
participant Android
participant LifecycleObserver
participant FocusManager
participant Room

User->>Android: 切换到微信
Android->>LifecycleObserver: APP进入后台
LifecycleObserver->>FocusManager: onBackground()

FocusManager->>FocusManager: 保存backgroundTime

User->>Android: 返回FocusTrace
Android->>LifecycleObserver: APP进入前台

LifecycleObserver->>FocusManager: onForeground()

FocusManager->>FocusManager: 计算duration

FocusManager->>Room: 保存DistractionEvent
```

# 20. 分心记录条件

只有：

```
FocusStatus == FOCUSING
```

才进行记录。

如果用户：

```
正在休息

主动暂停

没有开始专注
```

切屏不算分心。

逻辑：

```
APP进入后台
       ↓
当前是否FOCUSING？
   ↓          ↓
  是          否
   ↓          ↓
记录时间     忽略
```

# 21. 锁屏问题

锁屏也可能导致 FocusTrace 进入后台。

第一版可以直接规定：

> 专注期间使 APP 进入后台即视为离开 FocusTrace。

后续可以再进一步区分：

```
切换 APP

锁屏

系统页面
```

第一版不需要增加这种复杂度。

# 22. 分心数据示例

一次 50 分钟专注：

```
00:00

开始


12:31

进入后台


13:42

返回


27:15

进入后台


32:10

返回


44:26

进入后台


45:03

返回


50:00

完成
```

最终：

```
专注任务

算法训练


计划时间

50 min


分心次数

3


分心总时间

6 min 43 sec


平均分心时间

2 min 14 sec


第一次分心

开始后 12 min 31 sec
```

# 23. 单次专注报告

完成专注以后进入：

```
专注完成


算法训练


50 min


━━━━━━━


分心

3 次


分心时间

6m 43s


第一次分心

12m 31s


专注率

88%
```

下面显示：

```
分心记录


12:31 → 13:42

1m 11s


27:15 → 32:10

4m 55s


44:26 → 45:03

37s
```

# 24. 专注率

定义：

```
有效专注时间
────────────────
有效专注时间 + 分心时间
```

例如：

```
有效专注

50 min


分心

6 min 43 sec
```

计算：

```
50
───────
56.72

≈ 88%
```

显示：

```
专注率

88%
```

# 25. Room 数据库

数据库：

```
FocusTraceDatabase
```

包含四张核心表：

```
CategoryEntity

TaskEntity

FocusSessionEntity

DistractionEventEntity
```

第一版已经足够。

# 26. CategoryEntity

```
CategoryEntity
```

字段：

| 字段      | 类型   | 说明     |
| --------- | ------ | -------- |
| id        | Long   | ID       |
| name      | String | 分类名   |
| icon      | String | 图标     |
| sortOrder | Int    | 顺序     |
| createdAt | Long   | 创建时间 |

# 27. TaskEntity

```
TaskEntity
```

字段：

| 字段          | 类型    |
| ------------- | ------- |
| id            | Long    |
| categoryId    | Long?   |
| title         | String  |
| targetMinutes | Int     |
| completed     | Boolean |
| repeatType    | String  |
| reminderTime  | Long?   |
| createdAt     | Long    |
| updatedAt     | Long    |

# 28. FocusSessionEntity

整个项目最核心的表之一。

```
FocusSessionEntity
```

字段：

| 字段               | 类型  | 说明     |
| ------------------ | ----- | -------- |
| id                 | Long  | ID       |
| taskId             | Long? | 对应任务 |
| type               | Int   | 专注类型 |
| startTime          | Long  | 开始     |
| endTime            | Long? | 结束     |
| plannedSeconds     | Long  | 计划时间 |
| focusSeconds       | Long  | 有效专注 |
| distractionCount   | Int   | 分心次数 |
| distractionSeconds | Long  | 分心时间 |
| status             | Int   | 状态     |

其中：

```
type = 0

番茄钟


type = 1

正向计时
```

# 29. DistractionEventEntity

记录每次分心。

```
DistractionEventEntity
```

字段：

| 字段            | 类型 |
| --------------- | ---- |
| id              | Long |
| sessionId       | Long |
| backgroundTime  | Long |
| foregroundTime  | Long |
| durationSeconds | Long |

关系：

```
一个 FocusSession

↓

可以有多个 DistractionEvent
```

即：

```
1 : N
```

# 30. 数据库关系

```
erDiagram

CATEGORY ||--o{ TASK : contains

TASK ||--o{ FOCUS_SESSION : has

FOCUS_SESSION ||--o{ DISTRACTION_EVENT : contains
```

# 31. DataStore

DataStore 保存：

```
pomodoro_minutes

break_minutes

distraction_threshold

auto_start_break

auto_start_focus

sound_enabled

dark_mode
```

例如：

```
番茄：

25 min


休息：

5 min


分心阈值：

3 sec
```

这些设置没必要放 Room。

# 32. Repository

建议：

```
TaskRepository

FocusRepository

StatisticsRepository

SettingsRepository
```

例如：

```
FocusRepository
```

负责：

```
创建专注记录

完成专注

保存分心记录

读取专注记录

查询历史记录
```

# 33. ViewModel

主要建立：

```
TodoViewModel

FocusViewModel

StatisticsViewModel

SettingsViewModel
```

# 34. 数据流

例如待办页面：

```
Room

↓

Repository

↓

Flow<List<Task>>

↓

TodoViewModel

↓

StateFlow<TodoUiState>

↓

Compose

↓

TodoScreen
```

# 35. FocusViewModel

这是整个项目最重要的 ViewModel。

负责：

```
开始专注

暂停

继续

结束

倒计时

正向计时

记录分心

处理APP前后台变化
```

内部维护：

```
currentSession

focusStatus

remainingSeconds

elapsedSeconds

distractionCount

distractionSeconds
```

# 36. 计时器设计

一个重要原则：

> 不要依赖每秒 `-1` 来作为真实时间。

例如：

```
remainingSeconds--
```

如果 APP：

```
卡顿

进入后台

系统暂停线程
```

可能导致计时产生偏差。

应该记录：

```
startTimestamp
```

通过：

```
当前时间 - startTimestamp
```

计算真实经过时间。

UI 每秒刷新只是为了：

```
显示计时器
```

而不是作为最终时间依据。

# 37. 专注计时流程

```
flowchart TD

A[点击开始]

A --> B[创建FocusSession]

B --> C[记录startTime]

C --> D[FOCUSING]

D --> E{用户操作}

E -->|暂停| F[PAUSED]

E -->|进入后台| G[记录backgroundTime]

G --> H[返回APP]

H --> I[计算后台时间]

I --> J{超过阈值?}

J -->|是| K[保存DistractionEvent]

J -->|否| D

K --> D

E -->|完成| L[保存FocusSession]

L --> M[专注报告]
```

# 38. APP 被系统杀死

需要考虑：

```
正在专注
↓
APP进入后台
↓
Android回收进程
```

因此当前专注状态不能只存在：

```
ViewModel内存
```

需要将重要状态保存。

例如：

```
sessionId

startTime

plannedTime

focusStatus
```

可以写入：

```
Room / DataStore
```

重新启动 APP 后恢复。

# 39. 通知

专注完成时发送：

```
FocusTrace

本轮专注完成 🎯
```

休息完成：

```
休息结束

准备开始下一轮专注
```

使用：

```
Notification
```

# 40. 是否需要 Foreground Service

第一版可以先让：

```
时间计算
```

基于时间戳恢复。

如果之后希望：

> APP 长时间在后台依然保持明显的持续专注通知和更强的运行可靠性

可以增加：

```
Foreground Service
```

显示：

```
FocusTrace

算法训练

还剩 18:32
```

第一阶段可以暂缓。

# 41. 统计模块

提供：

```
今日

本周

本月
```

三个维度。

# 42. 今日统计

例如：

```
今天


专注时间

3h 26min


专注次数

8


完成番茄

6


分心次数

12


分心时间

24min


专注率

89%
```

# 43. 周统计

显示：

```
本周专注

12h 42min
```

每天：

```
周一      120min

周二      180min

周三       60min

周四      210min

周五      150min
```

使用：

```
柱状图 / 折线图
```

# 44. 分心趋势

FocusTrace 特有图表：

```
分心次数


10 ┤           ●
 8 ┤
 6 ┤      ●
 4 ┤ ●              ●
 2 ┤
   └────────────────

    一 二 三 四 五
```

可以快速看到：

> 哪一天自己的注意力状态最差。

# 45. 分心时间趋势

还可以展示：

```
每天后台时间


周一

12min


周二

38min


周三

8min
```

# 46. 第一次分心时间

统计：

```
平均首次分心时间

27 min
```

例如用户长期数据：

```
第1次    25min

第2次    31min

第3次    22min

第4次    30min
```

平均：

```
约27分钟
```

可以帮助用户认识：

> 自己连续专注大约多久开始容易分心。

# 47. 专注热力图

类似 GitHub Contribution：

```
      一 二 三 四 五 六 日

第一周 ■ ■ □ ■ ■ □ □

第二周 ■ ■ ■ ■ □ □ ■

第三周 □ ■ ■ ■ ■ ■ □

第四周 ■ ■ ■ ■ ■ ■ ■
```

颜色深度表示：

```
当天专注时间
```

点击一天：

```
2026-09-14


专注

186 min


番茄

6


分心

8次


分心时间

21min
```

# 48. 设置页面

例如：

```
专注设置


番茄时长

25 min


休息时长

5 min


自动开始休息

ON


自动开始下一轮

OFF


分心判定时间

3 sec
```

# 49. 外观设置

```
跟随系统

浅色

深色
```

使用：

```
Material 3
```

# 50. 白噪音

可以作为第二阶段功能。

内置：

```
雨声

森林

海浪

咖啡馆

白噪声
```

音频直接放：

```
res/raw
```

不需要服务器。

# 51. APP 页面

第一版建议：

```
MainScreen

TodoScreen

TaskEditScreen

FocusHomeScreen

FocusScreen

FocusResultScreen

StatisticsScreen

FocusHistoryScreen

SettingsScreen

AboutScreen
```

约：

```
10个主要页面
```

# 52. 项目目录

推荐：

```
FocusTrace/
│
├── app/
│   └── src/main/
│       │
│       ├── java/com/focustrace/
│       │   │
│       │   ├── data/
│       │   │   │
│       │   │   ├── local/
│       │   │   │   ├── dao/
│       │   │   │   │   ├── TaskDao.kt
│       │   │   │   │   ├── CategoryDao.kt
│       │   │   │   │   ├── FocusSessionDao.kt
│       │   │   │   │   └── DistractionDao.kt
│       │   │   │   │
│       │   │   │   ├── entity/
│       │   │   │   │   ├── TaskEntity.kt
│       │   │   │   │   ├── CategoryEntity.kt
│       │   │   │   │   ├── FocusSessionEntity.kt
│       │   │   │   │   └── DistractionEventEntity.kt
│       │   │   │   │
│       │   │   │   └── FocusTraceDatabase.kt
│       │   │   │
│       │   │   ├── datastore/
│       │   │   │   └── SettingsDataStore.kt
│       │   │   │
│       │   │   └── repository/
│       │   │       ├── TaskRepository.kt
│       │   │       ├── FocusRepository.kt
│       │   │       └── StatisticsRepository.kt
│       │   │
│       │   ├── ui/
│       │   │   │
│       │   │   ├── todo/
│       │   │   │   ├── TodoScreen.kt
│       │   │   │   └── TodoViewModel.kt
│       │   │   │
│       │   │   ├── focus/
│       │   │   │   ├── FocusScreen.kt
│       │   │   │   ├── FocusResultScreen.kt
│       │   │   │   └── FocusViewModel.kt
│       │   │   │
│       │   │   ├── statistics/
│       │   │   │   ├── StatisticsScreen.kt
│       │   │   │   └── StatisticsViewModel.kt
│       │   │   │
│       │   │   ├── settings/
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   │
│       │   │   ├── components/
│       │   │   │
│       │   │   └── theme/
│       │   │
│       │   ├── lifecycle/
│       │   │   └── AppLifecycleObserver.kt
│       │   │
│       │   ├── navigation/
│       │   │   └── AppNavigation.kt
│       │   │
│       │   ├── notification/
│       │   │
│       │   ├── FocusTraceApplication.kt
│       │   │
│       │   └── MainActivity.kt
│       │
│       └── res/
│
├── docs/
│
├── README.md
│
└── .gitignore
```

# 53. AppLifecycleObserver 设计

基本结构：

```
class AppLifecycleObserver(
    private val onBackground: () -> Unit,
    private val onForeground: () -> Unit
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        onBackground()
    }

    override fun onStart(owner: LifecycleOwner) {
        onForeground()
    }
}
```

然后注册到：

```
ProcessLifecycleOwner
```

用于监控：

```
整个 FocusTrace

前台
↕
后台
```

具体分心业务逻辑不要全部写在 Observer 中。

Observer 只负责：

```
通知生命周期变化
```

实际业务由：

```
FocusManager / FocusRepository
```

处理。

# 54. 业务分层

例如：

```
AppLifecycleObserver

↓

FocusSessionManager

↓

FocusRepository

↓

Room
```

不要写成：

```
AppLifecycleObserver

↓

直接写SQL
```

这样生命周期代码和数据库代码耦合太严重。

# 55. DAO

例如：

```
TaskDao
```

包含：

```
insertTask()

updateTask()

deleteTask()

getTodayTasks()

getAllTasks()
FocusSessionDao
```

包含：

```
insertSession()

updateSession()

getSession()

getTodaySessions()

getSessionsBetween()
DistractionDao
```

包含：

```
insertDistraction()

getBySession()

getTodayDistractions()

getDistractionsBetween()
```

# 56. APP 主流程

```
flowchart TD

A[打开FocusTrace]

A --> B[Todo]

B --> C[选择任务]

C --> D[选择专注模式]

D --> E[开始专注]

E --> F{切出APP?}

F -->|否| G[继续]

F -->|是| H[记录后台时间]

H --> I[返回APP]

I --> J[计算离开时间]

J --> K{超过阈值?}

K -->|是| L[记录分心]

K -->|否| G

L --> G

G --> M{专注完成?}

M -->|否| F

M -->|是| N[保存专注记录]

N --> O[显示专注报告]

O --> P[更新统计]
```

# 57. 第一版 MVP

第一版必须完成：

### 待办

```
创建

修改

删除

完成

分类
```

### 专注

```
番茄钟

正向计时

暂停

继续

结束
```

### 分心检测

```
APP后台检测

后台时间记录

分心次数

分心时间

分心详情
```

### 数据

```
Room存储

DataStore设置
```

### 统计

```
今日

本周

本月

热力图

分心统计
```

### 设置

```
番茄时间

休息时间

分心阈值
```

# 58. 第二阶段

完成第一版以后增加：

```
白噪音

重复待办

系统通知

桌面小组件

连续专注天数

详细历史记录

数据导出

数据备份
```

# 59. 第三阶段

未来如果需要：

```
账号

云同步

多设备

好友

排行榜

在线自习室
```

再设计：

```
Android APP
       ↓
Retrofit
       ↓
Spring Boot
       ↓
MySQL
```

第一版不提前增加这些复杂度。

# 60. UI 设计原则

FocusTrace 可以借鉴番茄 ToDo 这类 APP 的产品思路，但不需要像素级模仿其 UI。

推荐：

```
Material 3

+

极简

+

低刺激

+

大面积留白
```

专注界面尤其减少：

```
复杂按钮

大量文字

动画

信息流
```

# 61. 产品核心指标

FocusTrace 不只是统计：

```
专注时间
```

重点增加：

```
分心次数

分心时间

平均分心时间

首次分心时间

专注率
```

最终让用户看到：

```
今日


专注

3h 06min


分心

8次


离开APP

21min


平均每次分心

2m 37s


平均首次分心

31min


专注率

91%
```

# 62. FocusTrace 与普通番茄钟的区别

普通番茄钟：

```
开始

↓

25分钟

↓

结束
```

FocusTrace：

```
制定待办
     ↓
开始专注
     ↓
监控APP前后台
     ↓
记录分心行为
     ↓
完成专注
     ↓
生成专注报告
     ↓
长期统计
     ↓
了解自己的专注习惯
```

因此项目核心可以概括为：

> **基于 Android 应用生命周期监测的专注行为分析 APP。**

# 63. 项目开发顺序

不要一开始同时开发全部功能。

推荐严格按照以下顺序：

```
① 创建Android项目

↓

② Jetpack Compose基础页面

↓

③ Navigation底部导航

↓

④ Room数据库

↓

⑤ 待办CRUD

↓

⑥ 番茄钟

↓

⑦ 正向计时

↓

⑧ ProcessLifecycleOwner前后台检测

↓

⑨ 分心记录

↓

⑩ 单次专注报告

↓

⑪ 每日/周/月统计

↓

⑫ 热力图

↓

⑬ DataStore设置

↓

⑭ 通知

↓

⑮ UI优化

↓

⑯ APK测试
```

# 64. 第一阶段目标

AI 第一次开始开发时，不应该直接完成全部 APP。

第一阶段只完成：

```
1. 初始化Kotlin Android项目

2. 配置Jetpack Compose

3. 建立MVVM目录

4. 配置Navigation Compose

5. 配置Room

6. 配置DataStore

7. 建立4个Entity

8. 建立DAO

9. 建立Repository基础结构

10. 创建底部四个Tab

11. 创建基础页面

12. 确保项目能够编译运行
```

然后提交 Git。

下一阶段再实现：

```
待办 CRUD
```

之后：

```
专注计时
```

再之后才实现：

```
后台分心检测
```

# 65. 最终技术方案

FocusTrace 第一版最终确定为：

```
平台

Android


语言

Kotlin


UI

Jetpack Compose
Material 3


架构

MVVM


状态

ViewModel
StateFlow


数据库

Room


配置

DataStore


异步

Coroutines
Flow


导航

Navigation Compose


前后台检测

ProcessLifecycleOwner


本地通知

Android Notification
```

不使用：

```
Vue

uni-app

Flutter

React Native

Spring Boot

MySQL

服务器
```

第一版就是一款：

> **完全由原生 Android 技术开发、可以安装成 APK 并独立运行的手机 APP。**