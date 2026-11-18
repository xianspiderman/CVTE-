# 第一轮工程搭建验证（2026-11-17）

## 基线与实现解释

基线：main / d79a397，开始时工作区干净，origin/main 与本地一致。保留已有需求设计 PDF，未重新进行需求分析。

沿用设计的七个工程目录、八张表、业务模块和接口前缀。PDF 仅概括导航，没有逐项列明任务书的八个名称；本轮采用工作台、采购待办、库存异常、供应商协同、数据分析、数据接入、工作台配置、操作日志，后续如提供原任务书可调整标签。

新增 `/api/workbench/status` 和 `/api/data-sources/mock/preview` 作为骨架连通探测，不将尚未实现的正式接口伪装为可用。设计中的最终百条验收数据不属于本轮最小骨架，当前只有三条固定样例。数据库使用 IF NOT EXISTS，不做 DROP 或覆盖式初始化；未来表结构变更应单独增加迁移脚本。

为避免影响其他项目，复用已有 MySQL 服务，停止脚本不会停止共享数据库。数据库账号仅授权项目库。应用、Mock、Vite 均绑定 127.0.0.1。

## 实测环境

macOS 15.7.1 / arm64；OpenJDK 17.0.21.0.3；Maven 3.10.1；Node.js 24.22.3；npm 11.19.1；MySQL 8.0.46；Homebrew 7.0.14；Python 3.9.6。

核心开发依赖无缺失。PDF 提取工具不在 PATH，使用临时 Python 虚拟环境安装 pypdf / PyMuPDF 完成设计正文与嵌入表格读取；未修改系统 Python。Maven Central、npm 和 GitLab 访问成功。

## 检查记录

- Maven package / verify：MVC 非法参数与上游异常测试、真实 MySQL MyBatis 查询测试。
- 前端 npm install / npm ci、TypeScript 检查、Vite 生产构建；npm audit 无已知漏洞。
- Node test：真实 HTTP 样例读取及不存在路由响应。
- SQL 连续重复执行，不重复创建数据源；核心表均成功建立。
- 前端代理 → 后端 Controller / Service → MyBatis → MySQL，状态为 UP。
- 前端代理 → 后端 SourceAdapter → HTTP Mock，样例内容一致。
- OpenAPI 文档包含实际接口；非法 type 返回 400。
- Mock 进程停止后预览返回 502 / SOURCE_UNAVAILABLE，status 检测异常进程。
- 重复启动不会重复创建进程；端口冲突时拒绝启动；停止后可重新启动。
- 浏览器实际访问三栏布局，显示连接成功和固定采购样例；采购导航跳转未就绪页。

## 已修复与剩余限制

修复 MySQL ROW_NUMBER 保留字转义、Vite 配置语法和 Vite 8 分包配置兼容、Ant Design 5 Alert 属性类型问题。另修复运行中 Maven 重打包覆盖 JAR 导致的类加载异常：启动脚本改为复制独立运行 JAR 到 .runtime，构建不再改写正在运行的文件。最终重新验证通过，没有阻断性启动错误。OpenAPI 启用提示是本地开发预期配置。

前端主包仍有超过 500 kB 的构建提示（非失败），ECharts 已按组件导入并按需加载；后续可继续拆分 UI 依赖。业务功能和 AI 尚未实现，未连接生产服务。日志与密码仅存本地忽略目录/文件。

最终验证结果：后端 3 项测试全部通过（0 跳过），Mock 1 项测试通过；verify.sh 返回 0。上游停机返回 502、异常进程检测与端口冲突拒绝启动实测通过。
