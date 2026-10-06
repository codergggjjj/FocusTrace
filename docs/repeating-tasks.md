# 重复待办

创建或编辑原待办时，可选择不重复、每天、工作日（周一至周五，不判断法定节假日）或指定星期。指定星期至少选择一天，编辑草稿支持 Activity 重建。

创建当天仍是用户手动创建的这条待办；从之后符合规则的日期起，打开待办页时生成当天的新待办。待办页保持打开时每分钟检查一次。应用关闭时不运行后台生成任务，不补建错过的日期，不发送提醒。

每天的待办独立完成、计时和保存记录。原待办完成后仍会继续重复；在原待办中选择“不重复”或删除原待办可以停止。生成的待办显示日期，不再自动生成下一条；删除当天生成的待办不会在同一天重新出现。原待办改名、改变计时模式或时长只影响以后生成的待办，不改写已有任务和专注报告。

## 数据与升级

复用 TaskEntity.repeatType：NONE、DAILY、WEEKDAYS、DAYS: 后接 1–7 的无重复数字（周一至周日）。Room 升级到 8，显式 7→8 迁移增加 repeatLastDate、repeatParentId、repeatDate 和父待办 / 日期唯一索引，旧数据保留，新增字段默认为 NULL。

生成与更新最后生成日期在同一个 Room 事务中完成。唯一索引防止重复生成；更新原待办时保留数据库中最新生成日期，防止编辑页旧快照覆盖。时钟回拨到已处理日期不再生成。重复来源 ID 不使用级联外键，删除原待办后已生成的任务和历史专注仍保留。

备份保留规则、生成日期和来源关联；旧备份缺失字段时按 NULL 恢复。导入校验规则、日期和重复来源 / 日期冲突。每次生成是独立任务 ID，因此待办学习详情按各次任务分别统计，整体学习统计口径保持不变。

## 验证范围

仅运行 RepeatingTaskTest（规则、并发防重、迁移、备份兼容）及 RepeatingTaskUiTest（选择、重建与关闭规则），不进行全量回归。构建、lint 和 Room schema 导出随本次检查执行。

本次实际结果：assembleDebug、lintDebug、assembleDebugAndroidTest 通过，Lint 0 错误、20 项既有提示；API 36 模拟器中上述 5 项专项测试全部通过（9.5 秒）。未运行全量仪器测试，未修改计时核心或专注统计规则。

## 修改文件

- TaskEntity.kt、TaskDao.kt、FocusTraceDatabase.kt、AppContainer.kt、schemas/.../8.json：重复来源、日期、唯一索引、事务生成与迁移。
- TaskRepeat.kt、TaskRepository.kt：规则校验、日期判断与数据访问。
- TodoViewModel.kt、TaskEditScreen.kt、TodoScreen.kt：周期检查、规则编辑及列表标识。
- BackupFormat.kt：规则备份与旧格式兼容、输入校验。
- RepeatingTaskTest.kt、RepeatingTaskUiTest.kt：5 项针对性验证。
- README.md、本说明：功能边界和验证结果。
