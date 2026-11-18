# 核心业务 API

响应统一 `{code,message,data}`。非法参数 400；记录不存在 404；上游不可用 502；文件过大 413；内部故障 500（不向客户端泄露内部异常）。完整字段定义见 `/v3/api-docs`。

| 方法与路径                       | 用途                                                    |
| -------------------------------- | ------------------------------------------------------- |
| GET /api/data-sources            | 配置与同步状态                                          |
| POST /api/data-sources           | 新建 HTTP/FILE 来源                                     |
| PUT /api/data-sources/{id}       | 修改配置，类型不可变                                    |
| POST /api/data-sources/{id}/sync | 立即 HTTP 同步，返回批次统计                            |
| POST /api/imports                | multipart：sourceId、file；返回批次与错误行             |
| GET /api/imports                 | 最近 100 个批次                                         |
| GET /api/imports/{id}            | 批次统计、错误行及原因                                  |
| GET /api/todos                   | 组合筛选与分页                                          |
| GET /api/todos/{id}              | 完整记录及派生逾期信息                                  |
| GET /api/metrics/summary         | 十项核心指标及今日到期、完成总数                        |
| GET /api/metrics/charts          | 新增趋势、业务和风险分布                                |
| GET /api/workbench/config        | 演示用户配置（无存储时使用默认）                        |
| PUT /api/workbench/config        | 人工保存完整配置                                        |
| GET /api/system-links            | 模板列表                                                |
| POST /api/system-links           | sourceId、pageCode、urlTemplate；创建或更新             |
| GET /api/system-links/resolve    | taskId 必填，pageCode 可选；编码替换并记录解析/跳转意图 |
| GET /api/logs                    | 最近 200 条业务操作日志                                 |

待办参数：`view`（ALL、TOTAL、MINE、TODAY_NEW、TODAY_DUE、OVERDUE、DUE_SOON、HIGH_RISK、COMPLETED、PENDING_REVIEW、PENDING_REPLY、WEEK_COMPLETED、AVG_HOURS、ON_TIME_RATE）、`businessType`、`sourceSystem`（来源名称）、`riskLevel`、`owner`、`keyword`（标题/单号/描述）、`status`、`days`（创建时间窗口）、`createdDate`、`page`、`size`（1～200）。`MINE` 对应本地“演示用户”。图表额外接受 `trendDays=7|30`。

数据字段与示例见 `test-data/acceptance.csv`。HTTP 接受 JSON 数组或 `{data:[...]}`；映射为 `{目标字段:源字段}`，当前支持顶层字段。HTTP 数组错误行从 1 开始；CSV 包含表头从 2 开始；Excel 使用实际行号。

状态：PENDING、PENDING_REVIEW、PENDING_REPLY、COMPLETED。业务类型：PROCUREMENT、INVENTORY、SUPPLIER。风险/优先级：LOW、MEDIUM、HIGH。日期接受 ISO-8601，有时区偏移则转换为 Asia/Shanghai。

所有写操作仅作用于项目本地数据库；模板解析只生成 URL，不执行外部业务写操作。系统日志的 RESOLVED 表示生成链接/跳转意图，不宣称外部页面已成功访问。

## 配置约束与兼容

`PUT /api/workbench/config` 保存完整配置，非局部PATCH。`cards[].key` 是稳定标识，新增可选 `metric` 为十项核心指标之一；旧JSON未提供metric时使用key。可改名称与指标来源，明细跳转使用metric。`days` 为1～365整数、`refreshSeconds` 为10～3600整数；按卡片自己的间隔轮询，同窗口同间隔合并请求；首次及手工刷新立即读取。

导航parent为空表示根节点，其他值必须指向存在的key且不能形成循环；同级按order排序，隐藏父级不渲染其子树。`icon` 仅允许app/chart/link。名称1～80字符、visible布尔、order整数。快捷入口 aiPrompt 为不超过 2000 字符的文本。首页的“问 AI”按钮将其填入助手输入框，用户可编辑后发送。

布局dueSoonHours为1～168整数、defaultDays为1～365整数、defaultView为LIST/ANALYTICS、defaultOwner为不超过100字符的字符串、aiExpanded为布尔。URL筛选`owner=`显式清空默认负责人；缺失owner时前端使用默认负责人。配置、来源配置和链接模板的保存与审计同事务。

## 来源错误与操作日志

HTTP超时、重定向、畸形JSON或非成功响应明确失败，来源标记FAILED且保留已入库数据；行错误返回PARTIAL、批次和具体原因。固定CSV含3条故意错误，因此验收结束文件来源PARTIAL为预期。

日志操作类型：SOURCE_CONFIG、CONFIG_UPDATE、LINK_CONFIG、HTTP_SYNC、FILE_IMPORT、SYSTEM_JUMP。成功同步/导入target是批次ID，整体失败target是来源ID；跳转为任务ID和页面代码。配置校验失败不创建成功日志，日志存储失败使配置回滚。`GET /api/logs`仅返回最近200条；尚无归档与复杂检索。

`GET /api/metrics/overdue-trend?trendDays=7|30`：按到期日期分布的当前逾期记录。忽略创建窗口days，保留负责人/来源/业务等条件，返回total、trend和口径说明。明细使用`view=OVERDUE&dueDays=N`，单日可用`dueDate=YYYY-MM-DD`。这不是每日历史逾期存量快照。
