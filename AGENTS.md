# SCM 统一工作台

本地单体 SCM 工作台，使用脱敏样例演示数据接入、待办和指标、工作台配置、深链接及 AI 工具。答辩基线与后续评审见 [交接记录](docs/后续开发交接.md)。优化从 GitHub 主分支创建独立分支。

- 后端在 `backend/`，Java 17、Spring Boot 3.5、Maven、MySQL 8.0。`integration` 负责 HTTP/文件接入，`todo` 负责统一待办和时间规则，`metric` 负责聚合，`config`、`systemlink`、`audit` 分别负责配置、链接、审计。`ai` 通过 LangChain4j Responses 调用同一套业务服务。
- `TodoMapper` 使用原生 MyBatis 注解 SQL。来源、导入批次、配置、模板和日志主要使用 JdbcTemplate。前端在 `frontend/`，采用 React、TypeScript、Vite、Ant Design、ECharts、Axios、Zustand。`mock-service/` 无第三方 npm 依赖。
- 将输入校验、业务规则和数据库访问留在各自边界。复用待办与指标口径。使用参数绑定，保持现有 Java 和 TypeScript 风格。更新依赖时同步维护 `frontend/package-lock.json`。
- 只在本地专用库和脱敏数据上验证。保留来源键和导入幂等语义、四条物理外键、AI 写配置显式确认和审计。数据库演进使用可验证的增量迁移。HTTP 数据源限本地 Mock。服务默认绑定回环地址。
- `.env.example` 仅放变量和示例。真实密钥、连接配置、数据导出、`.runtime/` 和 Codex 会话均留在本地。真实模型验证需要获准的 Responses 服务。

按 [README](README.md) 创建本地库和 `.env`。运行 `./scripts/launch.sh` 后，`./scripts/verify.sh` 会执行 MySQL 集成、前端测试与构建、Mock 测试及健康检查。修改导入、待办或指标后在专用样例库跑 `python3 scripts/acceptance.py`，先启动固定业务时钟并运行 verify。修改 AI 链路时补做受控场景回归，真实调用使用 `SCM_AI_LIVE_TEST=true ./scripts/verify.sh` 和 `python3 scripts/ai-acceptance.py`。提交前看 `git diff --check`、测试失败/跳过数和待提交文件。
