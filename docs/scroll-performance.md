# 滑动性能排查与优化（2026-09-16）

## 范围与判断

保持已有功能、文案、配色、卡片尺寸及阴影。没有升级 Compose 或新增图片库，没有为固定少量设置项强行引入复杂列表。
用户安装的 v1.0.0 是 debug 构建。调试开销是候选因素，不能仅凭此解释所有卡顿。

## 已定位的代码开销

| 文件 | 修改前 | 修改后与作用 |
| --- | --- | --- |
| data/repository/StatisticsRepository.kt | Room 查询本身异步，但后续 map 在 ViewModel 的 Main 上执行分组、统计与排序 | distinctUntilChanged 避免内容未变时重复汇总，flowOn(Default) 把 CPU 工作移出主线程；总算法仍约 O(N log N + E)，不再占用 UI 线程 |
| data/repository/FocusRepository.kt | 报告事件排序与统计在 Main 执行 | 报告计算移到 Default、相同数据不重复计算，O(E log E) 算法不变 |
| statistics/Statistics.kt | 过滤每条记录都重新计算带时区的区间边界 | 每次汇总仅计算一次，边界转换从 O(N) 次降为 O(1) 次，过滤仍 O(N) |
| ui/todo/TodoScreen.kt | 页面状态变动时重复筛选、统计；每个可见任务线性查找分类；所有分类一次性布局 | 按任务/筛选条件 remember；分类表一次 O(C) 建立，单任务查找 O(C)→平均 O(1)；LazyRow 只布局可见分类；TaskCard 接收稳定参数与缓存回调，允许未变化的卡片跳过重组 |
| ui/statistics/StatisticsScreen.kt | 每个可见记录创建 formatter；所有日期柱形一次性布局；动态栏目缺少明确身份/类型 | formatter 按时区复用；柱形 LazyRow，日期 key；结构区块和分类有稳定 key/contentType；最大值只在数据或指标变动时重算，选中柱形不再扫描全部日期 |
| ui/focus/FocusViewModel.kt | 每秒更新包含整页数据的状态；进入过专注后无论是否结束都每秒读库 | TimerUiState 单独发送；暂停/结束/空闲/切出冻结时由 Room 变化触发刷新，只有活跃专注和休息轮询；每次刷新只计算一次 elapsed |
| ui/focus/FocusHomeScreen.kt | 页面根部读取每秒变化值 | 每秒变化由 LiveTimer 消费，重组范围缩小到计时显示子树，其余控制与说明不再因 tick 失效 |
| app/build.gradle.kts | 仅默认 debug/release，手机使用 debug 包 | 增加 performance 构建：非 debuggable、R8 优化、资源收缩；沿用本地 debug 签名便于覆盖安装，不属于正式发布签名 |

N=专注/任务记录数，E=分心事件数，C=分类数。remember 对数据引用不变的常见重组直接复用；数据真正变化时仍重新计算，没有声称消除加载整份数据的成本。

## 未盲目修改的部分

- 待办、统计学习记录、报告与分心明细本来就有 LazyColumn；任务/记录已有稳定 ID，不是无 key 导致的全量重建。
- 设置页和专注页是少量固定内容；月历最多 31 个日期，保持原布局。
- DAO 使用 suspend/Flow，未发现主线程直接 SQL、文件读写、网络请求或 Thread.sleep。测试用例里的设备等待不在应用代码中。
- 无网络图片或大位图加载，无模糊与持续装饰动画。卡片阴影为 1–2dp，未取得 GPU 瓶颈证据，因此保持样式。
- 编辑草稿继续使用 rememberSaveable。未增加 derivedStateOf：筛选依赖不是高频滚动位置，用 remember 更直接。

## 验证方法

ScrollPerformanceTest 在模拟器建立 300 个待办、40 个新增分类、1000 条专注记录，分别上下滑动 6 次，使用 Window.FrameMetrics 采样 TOTAL_DURATION；finally 精确清理本测试插入的记录。
采样是自动化手势 + debug 模拟器诊断，并非 Macrobenchmark 真机测评；虚拟 GPU、冷启动/JIT、后台构建负载、测试框架合成手势与帧样本数量都会影响数值。不能据此宣称手机提升固定百分比。

最初版本采样：待办 p50/p95 = 86.02/254.10ms（33 帧）；统计 34.79/184.58ms（68 帧）。
第一轮优化采样：待办 44.33/75.97ms（31 帧）；统计 38.43/90.14ms（53 帧）。长帧峰值下降，但统计中位值未改善，说明结果有噪声，不能把采样当作性能保证。

可复现命令：

```powershell
.\gradlew.bat assembleDebug assemblePerformance lintDebug connectedDebugAndroidTest -PcomposeReports=true
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.focustrace.ScrollPerformanceTest'
```

Compose 编译器报告位于 app/build/compose-reports/，构建目录不提交。实际报告将 TaskCard 标为 `restartable skippable`，task、categoryName、busy 与四个回调全部为 stable；没有通过不真实的 @Stable 注解强行跳过。
performance APK 位于 app/build/outputs/apk/performance/app-performance.apk。没有更改已有 GitHub v1.0.0 附件。

## 后续边界

- 月统计与热力图仍分别订阅统计数据，按月展示时存在重复查询/汇总；本轮没有为此引入缓存生命周期复杂度。
- Room 仍整批加载所选时间段的记录，超大历史数据可能需要分页或数据库聚合；目前未过度设计。
- 活跃专注/休息仍保留 1 秒刷新以执行计时状态转换。设备启动次数读取仍使用系统 Settings API。
- 应在用户手机上用 performance 包与原版本对比，再结合 System Trace/Profiler 分离 UI、RenderThread 和 GPU 时间；本地模拟器不能证明该手机的卡顿已经完全消除。

参考：[Android Compose 性能最佳实践](https://developer.android.com/develop/ui/compose/performance/bestpractices)、[Lazy 列表性能说明](https://developer.android.com/develop/ui/compose/lists)。

## 最终验证结果

- assembleDebug、assemblePerformance、lintDebug、connectedDebugAndroidTest 全部通过，41 项设备测试无失败。
- 最终代码的 debug 采样：待办 p50/p95 36.67/69.31ms（32 帧），统计 36.28/78.57ms（53 帧）。仍受模拟器及并行构建负载影响；统计 p50 与最初采样接近，没有证明真机提升比例。
- performance APK 已签名验证、安装并冷启动成功；实际包标志不含 DEBUGGABLE，待办、统计汇总、设置入口均正常。
- 优化 APK 约 1.25 MiB；使用原有 Android Debug 证书，与已发布 v1.0.0 的签名一致。没有新增权限、依赖、数据库迁移或更改用户数据。
