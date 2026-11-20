# CVTE-

## AI 驱动的可配置 SCM 统一工作台

CVTE 实习项目，Java 17 / Spring Boot / MyBatis / MySQL + React / TypeScript / Ant Design / ECharts。单体分层、本地部署，仅使用脱敏固定数据。包含数据接入、待办与指标、可配置工作台以及基于公司模型和真实业务工具的 AI 助手。

## 目录与架构

`frontend/` 页面与组件、Axios 请求、Zustand 配置；`backend/` 按 integration、todo、metric、config、systemlink、audit、ai 分层；`database/` 八张表和增量迁移；`mock-service/` 独立 HTTP 服务；`test-data/` 固定 JSON/CSV；`scripts/` 启停、初始化、验证；`docs/` 设计和验证报告。

页面 → REST → 统一业务服务 → MyBatis/JdbcTemplate → MySQL。HTTP 与文件适配器共用转换、校验和批次入库服务。工作台和 AI 工具共用待办、指标、链接及配置服务。

## 新 Mac 从零启动

### 1. 安装开发工具并克隆

使用 Java 17、Maven 3.9+、Node.js 24（含 npm）、Python 3.9+ 和 MySQL 8.0。答辩环境实际使用 MySQL 8.0.46。先安装 [Homebrew](https://brew.sh/) 并按其安装提示配置 shell；以下命令适用于 Homebrew 可用的终端：

```sh
brew install openjdk@17 maven node@24 mysql@8.0
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$(brew --prefix node@24)/bin:$(brew --prefix mysql@8.0)/bin:$PATH"
java -version
mvn -version
node -v
npm -v
python3 --version
mysql --version
git clone https://github.com/xianspiderman/CVTE-.git
cd CVTE-
```

`mvn -version` 应显示 Java 17。新终端可重新执行两行 `export` 或将它们放入个人 shell 配置。`mysql@8.0` 已被 [Homebrew 标记为弃用](https://formulae.brew.sh/formula/mysql@8.0)。如果新机已有可用的本地 MySQL 8.0，也可以直接复用该实例与其客户端。旧版 macOS 或 Intel 机型以 Homebrew 实际提供的安装支持为准；此处的复现基线是 MySQL 8.0，并未验证其他 MySQL 大版本。

Maven 依赖版本由 `backend/pom.xml` 和 Spring Boot BOM 管理。前端使用 `frontend/package-lock.json`，启动脚本通过 `npm ci` 安装。Mock 服务仅使用 Node.js 内置模块。

### 2. 创建独立本地数据库和 `.env`

如果本机还没有运行 MySQL，执行 `brew services start mysql@8.0`。以下两种方式任选其一：

- **全新本机，root 可以通过本地 Unix socket 无密码登录**：在仓库根目录运行 `python3 scripts/provision-local-db.py`。它创建 `scm_workbench` 专用库、专用 TCP 账号和随机密码，并生成权限为 `0600` 的 `.env`。脚本要求默认 MySQL socket 对应本机 3306 实例，且项目库、账号、`.env` 均不存在。
- **root 需要密码或手工管理本地 MySQL**：通过 `mysql -u root -p` 进入本机实例，使用已有项目库和专用账号，或在库和账号不存在时执行以下 SQL。把 MySQL 返回的随机密码填入本机 `.env`。

```sql
CREATE DATABASE scm_workbench CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'scm_workbench'@'127.0.0.1' IDENTIFIED BY RANDOM PASSWORD;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON scm_workbench.* TO 'scm_workbench'@'127.0.0.1';
```

手工方式在没有 `.env` 时执行 `cp -n .env.example .env`，随后 `chmod 600 .env`，编辑 `DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`，必要时修改 `DB_PORT`。一般保留 `DB_HOST=127.0.0.1`。`.env` 使用纯 `KEY=value` 格式，值无需加引号；进程环境优先。不要把 `.env` 当成 shell 脚本执行。验证项目账号能连接 TCP 后再启动，例如 `mysql -h 127.0.0.1 -P 3306 -u scm_workbench -p scm_workbench`。

首次启动自动执行 `database/init.sql` 和 `database/migrations/`。初始化创建八张表和默认来源（不是 100 条待办）；固定演示数据按下节命令导入。复用本地 MySQL 时，保持各项目库和权限隔离。

### 3. 可选配置 AI 并启动

在 `.env` 中填写获准使用的 `SCM_AI_BASE_URL`、`SCM_AI_MODEL`、`SCM_AI_API_KEY`。需要后端可直接访问的 HTTPS、兼容 OpenAI Responses API 的服务，且模型支持函数工具调用。基址填到 API 版本前缀，例如 `https://models.example.com/v1`，LangChain4j 调用其 `/responses`。模型名以该网关配置为准。留空基址或 Key 时仍能运行和测试业务功能。详见 [AI 接口、工具与验收](docs/ai-assistant.md)。

```sh
./scripts/launch.sh --open
./scripts/status.sh
```

启动脚本会创建 `.runtime/` 并构建前后端。项目目前使用的端口可按 `.env.example` 配置，默认工作台 5173、API 8080、Mock 4010、MySQL 3306。改 Mock 端口时，运行固定验收脚本会按实际端口配置样例来源。修改 `.env` 后先停止再启动，进程才会读取新值。

## 一键启停

Mac 上直接使用仓库中的 `./scripts/launch.sh`；加 `--open` 会在通过健康检查后打开浏览器。

```sh
./scripts/start.sh
./scripts/status.sh
./scripts/stop.sh
```

start 检查重复进程、端口、数据库连接，执行初始化与迁移、后端构建、npm ci、前端构建，再启动 Mock、后端、Vite 并检查健康。后端使用 `.runtime/` 独立 JAR，验证重构建不会覆盖运行文件。失败清理本次启动的项目进程并返回非零状态。重复 start 不创建新进程；部分服务状态或端口冲突返回错误，日志定位后 stop/start 恢复。

stop 只结束身份匹配的项目进程，**保留共享 MySQL**，避免影响其他项目。MySQL 未启动时 start 尝试 `brew services start mysql@8.0`。日志在 `.runtime/build.log`、`backend.log`、`frontend.log`、`mock.log`。服务均监听 127.0.0.1，端口可从 `.env` 配置。

## 访问

- 工作台：[http://127.0.0.1:5173](http://127.0.0.1:5173)
- 后端健康：[http://127.0.0.1:8080/actuator/health](http://127.0.0.1:8080/actuator/health)
- OpenAPI：[http://127.0.0.1:8080/swagger-ui/index.html](http://127.0.0.1:8080/swagger-ui/index.html)
- Mock 验收数据：[http://127.0.0.1:4010/api/acceptance](http://127.0.0.1:4010/api/acceptance)

八个导航已连接实际页面。工作台提供列表/统计两种视图、十项核心指标、风险关注、图表下钻和快捷入口；数据源页配置与同步/上传；配置页保存导航、指标、快捷入口、布局及链接模板；日志页查看业务操作。右侧助手支持多轮查询、系统深链接、业务摘要和确认式卡片修改。

## 固定验收与复现

业务固定时间与系统运行时间分开。以下方式**不修改 `.env`**，适合与其他会话共享本地凭据：

```sh
./scripts/stop.sh
SCM_FIXED_NOW=2026-11-17T04:00:00Z ./scripts/start.sh
./scripts/verify.sh
python3 scripts/acceptance.py
```

如果 `.env` 自身设置了 SCM_FIXED_NOW，以启动时显式环境变量为先；恢复真实时钟：`SCM_FIXED_NOW= ./scripts/start.sh`（先 stop）。验收时间固定为上海 2026-11-17 12:00:00，不受跨天影响；页面显示实际使用的业务时间。

`generate-fixtures.py` 可重复生成脱敏 JSON/CSV。`acceptance.py` 将原有本地采购来源配置到 Mock，HTTP 同步 60 条，文件导入 40 条合法记录和 3 条错误行；不会直接写入待办或统计值。它建立 10 个本地链接模板，验证重复导入、更新口径、接口与独立数据库聚合。重复运行不增加待办数量，会保留新的导入批次与审计记录。

验收预期：100 条 / 2 来源 / 3 业务；80 未完成、20 完成；20 逾期、15 今日到期、8 高风险；待审核和待回复各 10；平均处理 106 小时、按时完成率 50%。所有数量由真实存储数据计算。错误 CSV 行为 42、43、44。

`verify.sh` 运行真实 MySQL 集成及审计故障回滚测试、启停拒绝路径测试、前端配置策略测试、规则测试、转换/CSV/XLS/XLSX 测试、TypeScript、构建、Mock HTTP 测试、双次迁移及服务连通。测试生成的 `.xlsx` 位于 `backend/target/test-fixtures/`，供 HTTP 上传验收使用；先 verify 再 acceptance。单独 `mvn -f backend/pom.xml verify` 默认跳过依赖本地 DB 的测试，完整验证必须用 verify.sh。

固定验收脚本要求项目库只包含本项目固定样例；已有额外业务数据时会明确数量断言失败，不会自动清空用户数据。配置好模型后，可用 `SCM_AI_LIVE_TEST=true ./scripts/verify.sh` 增加真实对话和工具往返验证。导入固定数据后再运行 `python3 scripts/ai-acceptance.py` 验证六类工具，脚本会操作样例来源和配置，请使用专用样例库。

## 手工演示

1. 进入“数据源配置”，同步采购来源，或向文件来源上传 `test-data/acceptance.csv`。查看批次结果和错误行；再次导入应计为重复。
2. 在左侧“我的工作台”进入首页，点击中间“已逾期”指标卡的数字 20，页面自动定位到筛选栏，提示已选择“已逾期”，下方“统一业务记录”显示 20 条。点击记录标题右侧的“统计分析”，再点击业务类型图下方“采购: 7”、风险图下方“高风险: 3”，依次查看 7 条和 3 条。筛选栏显示中文条件；“重置筛选”可恢复默认范围。
3. 点击表格“任务”列的蓝色任务名称，右侧打开详情。先点击详情顶部“生成业务深链接”，再点击出现的“打开业务页面 ↗”；新标签页进入项目 Mock 的只读模拟页面，关闭该标签可返回。操作日志记录解析动作。固定样例在常用页面简称“任务 006”等，来源原名在详情底部“查看来源原文与扩展信息”保留；数据库和源文件未改名。
4. 配置页修改卡片名称/指标来源/顺序/显示和默认条件，保存后刷新确认保留。可设置即将逾期阈值、时间范围、每卡刷新频率及 AI 面板展开。导航上级按层级显示，隐藏父级同时隐藏子级。
5. 在右侧助手询问“我今天有多少条待办”，查看待办卡片、来源及更新时间。输入“采购类、已逾期且高风险的任务”，接着问“按刚才的筛选条件，换成库存”。
6. 询问“打开待审核询价页面”，从调用结果选择一条单据。输入“把已逾期卡片放到第一位”，核对预览后分别尝试取消和确认；确认后刷新页面查看排序及 AI 调用日志。
7. 将 HTTP 来源临时改为 `/api/failure` 后同步，返回 502、来源标记 FAILED，已有待办保留，工作台提示数据不完整；保存恢复原来源地址（固定验收来源为 `/api/acceptance`）并再次同步，确认来源恢复正常后回工作台刷新。不要用故障地址覆盖其他来源。

## 口径、接口与限制

迁机基线和后续评审见 [后续开发交接](docs/后续开发交接.md)。详见 [业务 API](docs/business-api.md) 与 [本轮审查及验证记录](docs/round-2-audit-and-validation.md)，后者包含实际部署架构、八表 ER 图、模块职责、入库时序、配置核对矩阵、主项目集成测试与前端/脚本测试结果、静态检查及周四验收风险清单。

AI 会话在后端进程中保留近 4 轮和 30 分钟空闲时间。刷新浏览器后从新会话开始。配置提案有效期为 5 分钟。新增趋势使用创建日期；AI 的 OVERDUE_TREND 使用最近 N 天到期且当前未完成的记录，按到期日期分布（不等同于历史每日逾期存量）。正式个人总结和汇报材料见下方交付索引。

当前为单用户本地演示，未实现登录；查询在内存统一过滤，未做大数据压力测试。HTTP 只允许项目 Mock，不连接生产系统。图表包有非阻断体积警告。构建通过不等于全部 P0 验收完成。

集成验证需要先在主项目安全配置模型凭据；Worktree 的进程环境不会随 Git 合并迁移。未配置时 `/api/ai/status` 返回 configured=false，对话返回503，其余业务继续运行。不能将独立 Worktree 的模型测试视为 main 的验收结果。

## 正式提交核对（2026-11-19）

完整复测后端41项零跳过、前端11项、Mock1项和启停2项通过。完整验证命令`SCM_AI_LIVE_TEST=true ./scripts/verify.sh`会安全加载本地.env；默认不设开关时真实模型用例跳过，不能宣称零跳过。本轮网关对gpt-6.1-sol请求返回gpt-6.2-sol标识，真实对话及工具往返通过，精确版本需网关方确认。

工程交付清单、最终含AI架构和ER、七场景证据、静态检查、npm历史5个high与本次官方审计0告警的差异，均集中在[最终验收记录](docs/round-2-audit-and-validation.md)。本轮未升级依赖；0告警不代表历史问题已由本轮修复。正式汇报PPTX和个人总结PDF已纳入仓库下方交付目录，包含真实首页和AI工具查询截图。Word编辑底稿仅保留本地；现场代码走查和本人测试复现由本人完成。

## 答辩交付索引

| 交付项 | 仓库路径 |
| --- | --- |
| 前后端源码与自动化测试 | `frontend/`、`backend/`（测试位于 `frontend/tests/`、`backend/src/test/`） |
| 数据库、Mock 与脱敏验收数据 | `database/init.sql`、`database/migrations/`、`mock-service/`、`test-data/` |
| 一键启动、停止与验证 | `scripts/launch.sh`、`scripts/start.sh`、`scripts/stop.sh`、`scripts/status.sh`、`scripts/verify.sh`、`scripts/acceptance.py`、`scripts/ai-acceptance.py` |
| 需求与开发前工程设计 | [导师走查合订稿](docs/SCM%20智能工作台——需求与工程设计.pdf) |
| 实际架构、ER、模块职责、核心调用链 | [最终验收记录](docs/round-2-audit-and-validation.md)中的架构、模块与入库链路章节 |
| 业务 API、AI 工具定义 | [业务 API](docs/business-api.md)、[AI 接口与工具](docs/ai-assistant.md)；完整 OpenAPI 见运行时 `/v3/api-docs` |
| 测试与回归、静态检查、已知问题 | [最终验收记录](docs/round-2-audit-and-validation.md)；[首轮环境记录](docs/round-1-validation.md)保留阶段依据 |
| 正式个人总结（5页，含工程设计与ER图） | [个人项目总结 PDF](docs/delivery/SCM统一工作台-个人项目总结（最终版）.pdf) |
| 正式汇报（10页，含演讲者备注） | [正式汇报 PPTX](docs/delivery/SCM统一工作台｜正式汇报终稿.pptx) |

`docs/delivery/`仅保存上述两份唯一最终版本。公司原始实习资料、实习日志、Word底稿、独立截图、运行日志与测试中间产物不纳入仓库。开发前需求与设计合订稿共7页，设计部分跨第3～7页，与任务书1～2页篇幅要求的差异保留在已知问题中，需导师确认接受；本次不改写已走查原稿。
