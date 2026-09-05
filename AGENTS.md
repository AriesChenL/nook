# Nook 开发规范（Agent 必读）

本文件是 Nook 仓库的 Agent 开发规范。适用于仓库根目录及全部子目录；若子目录存在更具体的 `AGENTS.md`，以更具体的文件为补充。目标是让不同 Agent 产出的代码在架构、风格、测试和交付方式上保持一致。

## 1. 指令优先级与事实来源

按以下优先级执行：

1. 用户当前请求和明确的范围限制。
2. 本文件及更深目录中的 `AGENTS.md`。
3. 当前代码、测试、`pom.xml`、`package.json` 和运行配置。
4. `README.md`、`QUICKSTART.md` 与其他文档。
5. 历史实现或模型记忆。

代码与文档冲突时，先通过调用链、测试或官方文档确认真实行为，再同步修正文档。不要为了符合旧文档而破坏已经生效的代码契约。

除非用户明确要求，不为了流程本身单独创建 SDD。复杂或高风险改动应先简短说明范围、关键决策和验收方式，然后继续实施。

## 2. 开工前检查

开始任何非简单任务前必须：

1. 运行 `git status --short --branch`，确认仓库、分支和已有改动。
2. 查找适用的 `AGENTS.md`，读取涉及模块的入口、配置和测试。
3. 用 `rg` 搜索所有调用方、同义实现和已有测试，不能只改第一个搜索结果。
4. 明确此次涉及的模块和不涉及的模块，避免顺手重构。
5. 对第三方 SDK、云服务或版本敏感 API，先查当前官方文档或实际制品，不凭记忆猜包名、参数或行为。
6. 若工作区已有用户改动，保留并绕开它们。提交时只暂存本任务文件。

请求类型决定权限边界：

- “熟悉、分析、Review、排查”默认只读，不修改、不提交、不推送。
- “实现、修改、完善”允许在任务范围内编辑和验证，但不自动提交。
- 只有用户明确说“commit / push / 提交 / 推送”时才执行 Git 提交或远端写入。

## 3. 项目结构与模块边界

Nook 是一个 Maven 多模块后端加独立 Vue SPA：

| 模块 | 固定职责 | 不应放入 |
|---|---|---|
| `nook-common` | 统一响应、错误码、公共常量、JWT 工具、全局异常、Flyway 迁移 | 具体业务 Service、第三方业务 SDK |
| `nook-starter` | 业务服务共用依赖聚合 | 业务代码、启动类 |
| `nook-gateway` | WebFlux 路由、JWT/Redis 鉴权、身份 Header 注入、CORS | JDBC、MyBatis、阻塞式业务逻辑 |
| `nook-auth` | 注册、登录、登出、改密、Token 生命周期 | 用户资料和好友业务 |
| `nook-user` | 用户资料、好友关系、内部用户查询 | 会话、消息和 WebSocket |
| `nook-im` | 会话、消息、群管理、已读、在线状态、文件直传、WebSocket | AI 对话、支付权益 |
| `nook-ai` | Agent CRUD、AgentScope 运行时、SSE 对话、AI 免费额度 | IM WebSocket、支付事实写入 |
| `nook-pay` | Stripe 订阅、Checkout、Portal、Webhook、账单、权益事实 | AI 配额策略、银行卡数据 |
| `nook-web` | Vue 3 SPA、页面状态、HTTP/SSE/WebSocket 客户端 | 服务端密钥、业务事实判定 |

跨模块规则：

- 浏览器业务请求统一经过 `nook-gateway`。
- 服务间调用使用 OpenFeign 和 Nacos 服务发现，不通过公网网关绕一圈。
- 共享数据目前使用同一 PostgreSQL 数据库，但每个模块只能直接维护自己职责内的表。
- `nook-common` 中的 Flyway 脚本会被多个业务服务加载，迁移必须可重复部署并兼容并发启动。

## 4. 后端技术基线

- JDK 25。
- Spring Boot 4 / Spring Framework 7。
- Spring Cloud 2025.1、Spring Cloud Alibaba 2025.1。
- Jackson 3，包名使用 `tools.jackson.*`，不要重新引入 `com.fasterxml.jackson.*`。
- PostgreSQL + MyBatis-Flex，禁止在未讨论的情况下整体迁移到 JPA。
- 构建使用仓库内 `./mvnw`，不要假设系统 Maven 或其他 JDK。

版本号以根 `pom.xml` 和各模块 POM 为准。升级依赖时：

1. 使用最新稳定正式版，不使用 alpha、beta、RC 或浮动版本，除非用户明确要求。
2. 阅读跨大版本迁移说明。
3. 版本优先放在父 POM 的 properties / dependency management，避免子模块重复锁版。
4. 扫描旧包名和废弃 API，并执行干净构建。
5. 同步 README、Quick Start 和本文件中受影响的基线。

## 5. Java 编码风格

### 5.1 类型选择

- 不可变的 Request、Response、VO 和 Feign payload 优先使用 `record`。
- MyBatis-Flex Entity 保持普通可变类，提供无参构造和字段写入能力；不要改成 record。
- 需要分阶段组装的复杂 VO 可以保留普通类，但应说明原因。
- 标识、状态和时间字段使用明确类型：内部主键 `Long`，外部 ID `String`，时间使用 `OffsetDateTime`。
- 集合返回空集合，不返回 `null`；单对象确实可能不存在时按现有接口契约返回 `null` 或抛业务异常。

### 5.2 类与依赖

- 使用构造器注入；项目现有类可用 Lombok `@RequiredArgsConstructor`。
- 禁止字段注入和静态 Service Locator。
- Controller 只做协议适配、参数校验和响应封装；业务规则放 Service。
- Mapper 只负责持久化，不在 Controller 中直接调用 Mapper。
- 第三方 SDK 调用放在 gateway/adapter 类中，便于测试替换，禁止把 SDK 静态全局状态散落在 Service。
- 新增公共工具前先搜索现有实现；只有两个以上模块真正共用时才放入 `nook-common`。

### 5.3 命名规则

所有新代码和被本任务修改的代码必须遵守下表。历史文件存在不一致时，只整理本次触及的代码，不发起无关的全仓格式化。

| 对象 | 规则 | 正例 | 反例 |
|---|---|---|---|
| package | 全小写，按 `com.lynn.nook.<module>.<layer>` 分层 | `com.lynn.nook.pay.service` | `com.lynn.nook.Pay.Service` |
| class / interface / record / enum | `PascalCase` | `PaymentService`、`InvoiceVO` | `paymentService`、`InvoiceVo` |
| method | `lowerCamelCase`，使用动词或动宾结构 | `createSubscriptionCheckout()` | `subscriptionCheckout()`、`doIt()` |
| field / parameter / local variable | `lowerCamelCase`，使用名词或清晰语义 | `stripeCustomerId` | `stripe_customer_id`、`scid` |
| constant / enum 内常量 | `UPPER_SNAKE_CASE` | `RECALL_WINDOW` | `recallWindow`、`RecallWindow` |
| generic type parameter | 简单类型用单个大写字母，复杂语义用 PascalCase | `T`、`ID` | `tValue` |
| test class | 被测类名 + `Test` | `PaymentServiceTest` | `TestPaymentService` |
| test method | 行为 + 条件 + 结果，使用 lowerCamelCase | `duplicateEventDoesNotRunBusinessLogic()` | `test1()` |

具体约定：

- 包、类、方法、变量使用英文；业务 JavaDoc 和必要注释使用简洁中文。
- 类型后缀表达分层职责：`Controller`、`Service`、`Mapper`、`Client`、`Gateway`、`Config`、`Properties`、`Entity`、`Request`、`Response`、`VO`、`Event`、`Exception`。
- Mapper 接口使用业务实体名 + `Mapper`，不要增加无意义的 `I` 前缀。
- 方法名表达行为与结果，例如 `createSubscriptionCheckout`、`requireMember`、`syncInvoice`；查询方法使用 `get/find/list/count/exists` 区分返回语义。
- `getXxx` 表示应当存在或按当前契约允许返回 null；`findXxx` 表示可能不存在；集合查询使用 `listXxx`，不要用 `getXxxList`。
- 校验并在失败时抛异常的方法使用 `requireXxx`；只返回布尔值的校验使用 `is/has/can`。
- 布尔字段使用肯定语义，例如 `active`、`enabled`、`cancelAtPeriodEnd`；避免 `notDisabled`、`noError` 等双重否定。
- 常见缩写按单词处理：`JwtUtil`、`SseEmitter`、`apiKey`、`userId`、`avatarUrl`；协议或品牌已有固定拼写时保持项目现状，例如 `WebSocket`、`Stripe`、`S3Config`。
- 不使用含糊缩写；`req`、`vo`、`id` 只允许用于作用域很小且语义明确的局部变量。
- 不使用 `data`、`info`、`obj`、`tmp`、`handle()`、`process()` 等泛化命名，除非上下文已经唯一明确其含义。
- 集合变量使用复数名，例如 `memberIds`、`subscriptions`；Map 名称体现 key/value 关系，例如 `usersById`。
- 单位必须进入名称，例如 `expireMillis`、`timeoutSeconds`、`amountMinorUnits`，禁止只写 `timeout`、`amount` 后依赖注释猜单位。

### 5.4 Import 与全限定类名

- 正常类型必须在文件头通过 `import` 引入，业务代码中禁止直接书写全限定类名。
- 禁止通配符 import，包括 `import xxx.*` 和 `import static xxx.*`。新代码显式列出实际使用的类型和静态方法。
- 禁止保留未使用、重复或来自旧包名的 import。
- 同一文件只 import 一个同简单名类型。Java 不支持 import alias，两个类型重名时允许对较少使用的那个类型书写全限定类名，这是唯一常规例外。
- 全限定类名例外的典型场景是本地 `Subscription` 与 Stripe `Subscription`、Checkout `Session` 与 Billing Portal `Session` 同时出现。不要为了消除这一处全限定名而引入含糊的自定义别名类。
- 若同一个全限定类名在文件中反复出现三次以上，应优先重新划分 adapter、提取转换方法，或让方法返回项目自己的 DTO；确认无法改善后才保留。
- `java.util.UUID`、`java.util.Objects`、`BusinessException`、`ResultCode` 等没有名称冲突的类型必须 import，不能在方法体内写全类名。
- static import 只用于测试断言、Mockito DSL 或公认常量；生产业务方法不应依赖大量 static import 隐藏调用来源。

Import 分组顺序固定为：

1. 当前项目 `com.lynn.nook.*`。
2. 其他第三方包，如 `com.*`、`io.*`、`jakarta.*`、`lombok.*`、`org.*`、`software.*`。
3. JDK 包 `java.*` / `javax.*`。
4. static import。

组与组之间空一行；组内按字典序排列，不用空格手工对齐。

```java
// 推荐：普通类型通过 import 使用。
import java.util.Objects;
import java.util.UUID;

String publicId = UUID.randomUUID().toString();
boolean same = Objects.equals(leftId, rightId);
```

```java
// 禁止：没有重名冲突却在业务代码中写全限定类名。
String publicId = java.util.UUID.randomUUID().toString();
boolean same = java.util.Objects.equals(leftId, rightId);
```

```java
// 允许：两个 Session 类型确实重名，常用类型 import，低频类型使用全限定名。
import com.stripe.model.checkout.Session;

Session checkoutSession = stripe.retrieveCheckoutSession(sessionId);
com.stripe.model.billingportal.Session portalSession = stripe.createBillingPortal(customerId);
```

### 5.5 文件与格式

- 源文件使用 UTF-8、LF 换行和 4 个空格缩进，禁止 Tab。
- 一个 `.java` 文件只放一个 public 顶层类型，文件名与 public 类型名完全一致。
- 左花括号不单独换行；`else`、`catch`、`finally` 与前一个右花括号保持同一行。
- 一行只写一个语句；禁止省略会降低可读性的花括号。
- 每行目标不超过 120 个字符。链式调用、构造参数和条件表达式超长时按语义换行，不为了满足长度拆出无意义变量。
- 长参数列表每行一个参数或按稳定语义分组；续行缩进保持一致。
- 多个注解通常一行一个；同一字段上的短映射注解可以紧邻，但不能挤成难读的一行。
- 不用多余空格做纵向对齐，因为字段名变化会制造大面积无意义 diff。
- 方法之间空一行；同一逻辑块内部只在阶段切换时空行。
- 删除行尾空格、注释掉的废弃代码、IDE 自动生成的无意义 JavaDoc 和未解决的调试输出。
- 不在代码中使用 `System.out/err`；使用 SLF4J Logger。
- TODO 必须包含明确动作和解除条件；不能用 TODO 代替当前需求必须完成的逻辑。

### 5.6 类结构与成员顺序

类内部按以下顺序组织：

1. `static final` 常量。
2. 其他 static 字段。
3. 注入依赖和实例字段。
4. 构造器（若未由 Lombok 生成）。
5. public API 方法。
6. package-private / protected 方法。
7. private helper。
8. 内部类型。

其他要求：

- 使用满足需求的最小可见性；默认 private，不为测试随意提升为 public。
- 无状态工具类声明为 `final` 并提供 private 构造器；优先把行为放在有明确职责的 Service，而不是不断扩充 Utils。
- Spring Bean 默认使用单例无状态设计。请求级数据放方法参数或明确的 request context，不能存入 Bean 可变字段。
- 注入字段声明为 `private final`，通过构造器注入。
- 不把 Entity、可变集合或第三方 SDK 对象保存在 singleton Bean 的共享字段中。
- 一个类只承担一个明确职责；当 Controller、Service 或 Gateway 同时承担协议解析、业务决策、持久化和第三方调用时，应拆分。

### 5.7 方法与控制流

- 一个方法只完成一个可描述的任务。出现多层阶段、多个外部副作用或难以命名的代码块时提取 private helper 或独立 Service。
- 优先使用 guard clause / early return 降低嵌套；正常主路径保持从上到下可读。
- 嵌套超过三层时重新评估结构，不能继续堆叠 `if/for/try`。
- 公开 Service 方法在入口校验关键参数和权限；深层 helper 不重复散落同一校验。
- 禁止用含义不明的多个 boolean 参数控制流程；使用 enum、配置对象或具名方法。
- 不在 getter、DTO 转换器、`toString()`、`equals()`、`hashCode()` 中执行数据库、网络或消息发送副作用。
- Stream 适用于清晰的映射、过滤、分组；包含异常处理、状态修改或复杂分支时使用普通循环。
- switch 处理 enum 或 sealed hierarchy 时尽量穷举分支；不要用无意义 default 吞掉未来状态。
- 批量数据避免循环内逐条远程调用或 SQL 查询。优先批量接口，并为无法避免的 N+1 写明原因。
- 不在循环中创建可复用的重型客户端、ObjectMapper、正则表达式或线程池。
- 外部调用明确设置超时、重试和幂等策略；重试只用于可安全重放的操作。

### 5.8 Lombok、不可变性与空值

- `record` 是不可变 DTO 的默认选择，不再叠加 Lombok `@Data`、`@Value` 或手写 getter。
- `@Data` 只用于当前需要 JavaBean 可变性的 Entity、ConfigurationProperties 或历史可变 VO；普通 Service/Controller 禁止使用。
- `@Builder` 用于字段较多且确实提升可读性的内部 Event/VO；简单两三个字段的 DTO 直接使用 record 构造器。
- 禁止使用 `@SneakyThrows` 隐藏受检异常；显式处理、转换或向上声明。
- 不使用 Lombok `val` / `var`；局部变量可以使用 JDK `var`，但右侧类型必须一眼可见且不能损失领域语义。
- 构造完成后不再变化的字段声明为 `final`；不要为了机械追求 final 给所有局部变量增加噪音。
- 集合字段优先使用接口类型 `List/Set/Map`，不要把 `ArrayList/HashMap` 暴露为 API 类型。
- 方法不返回 null 集合、数组或 Stream。返回 `List.of()`、`Set.of()` 或空 Stream。
- 不在 Entity、Request/Response 或配置字段中使用 `Optional`；Optional 只用于确实表达“查找结果可能不存在”的返回值，并保持同一层风格一致。
- 比较 boxed 数值或可空对象使用 `Objects.equals()`；不要依赖 `Long/Integer` 的 `==` 缓存行为。
- 字符串空白判断使用 `isBlank()` 或 Spring `StringUtils.hasText()`，不要只判断 `isEmpty()`。
- 对外返回集合时避免泄漏内部可变集合；根据所有权使用 `List.copyOf()` 或新集合。

### 5.9 数值、金额与时间

- 金额禁止使用 `float` / `double`。
- Stripe 金额沿用最小货币单位 `Long` 并同时保留 currency；需要小数运算的业务金额使用 `BigDecimal`，显式指定舍入规则。
- 分页 limit、文件大小、重试次数等外部输入必须设上下限，不能只信任前端校验。
- 数据库存储和后端传输时间优先使用 `OffsetDateTime`；只表示日期时使用 `LocalDate`。
- Unix 时间戳必须在名称或 JavaDoc 中写明 seconds / millis，不能只写 `timestamp`。
- 到期、每日额度、重试窗口等时间敏感逻辑较复杂时注入 `Clock`，让测试不依赖真实当前时间。
- 跨时区业务先明确业务时区；不能隐式依赖服务器默认时区后又与 UTC 事件比较。

### 5.10 错误与日志

- 可预期业务失败使用 `BusinessException(ResultCode)`。
- 新业务错误在 `ResultCode` 中分配所属模块号段，不复用语义不符的错误码。
- 不吞异常；选择 fail-open 或 fail-closed 时必须在代码和测试中明确理由。
- 日志使用参数化占位符，不字符串拼接。
- 日志必须包含定位所需的稳定标识，但不得记录 Token、密码、API Key、Webhook secret、完整请求体或银行卡数据。
- 对外错误信息不能暴露堆栈、SQL、第三方密钥或内部网络地址。

### 5.11 Java 修改自检

每次新增或修改 Java 文件后检查：

- [ ] package 全小写且位于正确模块/分层。
- [ ] 类型 PascalCase，方法/字段 lowerCamelCase，常量 UPPER_SNAKE_CASE。
- [ ] 没有 wildcard import、未使用 import 和无必要的全限定类名。
- [ ] Controller 只做协议适配，Service 承担业务，Mapper 只做持久化。
- [ ] 注入字段为 `private final`，没有字段注入和 singleton 可变请求状态。
- [ ] DTO/Entity 类型选择符合 record 与 MyBatis-Flex 约束。
- [ ] null、集合、金额、时间和单位表达明确。
- [ ] 权限、事务、并发、幂等、重试和外部副作用边界已覆盖。
- [ ] 没有 Secret、完整 payload、`System.out` 或吞异常。
- [ ] 相关测试、目标模块构建和 `git diff --check` 已通过。

## 6. API 与身份规范

- 普通业务接口沿用 `Result<T>`：`{code,message,data}`。
- 鉴权失败由网关返回 HTTP 401；Webhook 等第三方回调按协议返回真实 2xx/4xx/5xx，不套业务 HTTP 200。
- 新 Request 使用 Jakarta Validation 注解，并在 Controller 使用 `@Valid`。
- 外部 API 不暴露数据库自增 ID。用户、会话、消息、Agent 等对外使用 `public_id`；内部服务调用才能使用数字 ID。
- 下游服务只信任网关注入的 `X-User-Id` / `X-Username`，部署时不得直接公开业务服务端口。
- `/pay/internal/**` 等内部接口不得经公网网关暴露；新增内部接口时同步检查网关边界。
- 修改接口字段或路径前搜索后端调用方、Feign Client、前端 API 封装和文档，保持一次变更闭环。

传输边界：

- AI 流式响应使用 HTTP POST + SSE。前端使用 `fetch` + `ReadableStream`，不要改成原生 `EventSource`，因为请求需要 Authorization Header 和 POST body。
- WebSocket 只用于 `nook-im` 实时事件。业务消息仍由 HTTP 写入，WebSocket 负责推送。
- 文件通过 S3 兼容预签名 URL 由客户端直传 RustFS，不让大文件经过 Java 服务中转。

## 7. 数据库与事务规范

- Schema 只通过 `nook-common/src/main/resources/db/migration/` 下的 Flyway 迁移修改。
- 已发布迁移不可重写；新增下一个连续版本，例如 `V6__description.sql`。
- 迁移必须同时适用于全新数据库和已运行数据库。新增对象优先使用安全的 `IF NOT EXISTS`，但不能用它掩盖不兼容结构。
- 不删除历史数据或表，除非用户明确确认数据策略并提供回滚方案。
- 表和字段使用 `snake_case`；Java 字段使用 `camelCase` 并通过 `@Column` 显式映射特殊字段。
- 为唯一业务约束添加数据库唯一索引，不能只靠“先查再写”。
- 高频过滤、排序和关联字段添加有依据的索引，不为每个字段盲目建索引。
- SQL 参数化，禁止拼接用户输入。
- 多表业务写入使用 `@Transactional`；外部副作用需要考虑事务提交前后边界。
- 消息推送、MQ 发布等只有在数据库事务提交成功后才能对外可见。
- 幂等和并发必须依靠数据库约束、原子 SQL、事务锁或可证明的协议保证，不能只用 JVM 内存锁应付多实例。

## 8. 配置与 Secret 规范

配置按职责放置：

- 跨服务或敏感配置放 `docs/nacos/nook-shared.yml`，由各服务 `shared-configs` 加载。
- 端口、路由、模块业务开关和非敏感默认值放各模块 `application.yml`。
- 环境差异通过 `${ENV_VAR:dev-default}` 表达。
- 新增共享配置时同步更新 Nacos 模板、导入脚本说明和 Quick Start。

Secret 硬性要求：

- 禁止提交真实密码、Token、API Key、Cookie、私钥和生产连接串。
- 只允许仓库中存在明确标注的本地开发默认值；生产 Secret 必须来自受控 Secret 系统或部署环境。
- `.env` 只能作为 gitignored 本地文件，并提供不含真实值的 `.env.example`。
- 不读取、打印或持久化真实 Secret 来“验证配置”。
- Stripe 推荐使用最小权限 restricted key；Webhook secret 与 API key 分离。

## 9. 模块专项规范

### 9.1 `nook-gateway`

- 保持纯 WebFlux/Reactor 链路，禁止 `block()`、JDBC 和阻塞式远程调用。
- 路由显式配置，服务发现 locator 保持关闭，除非有完整安全评估。
- 白名单新增必须最小化，并说明其自身认证方式。
- CORS 生产域名必须由配置提供，不能把 `*` 与 credentials 同时开放。
- WebSocket 可以用 `access_token` query 兼容浏览器限制，普通 HTTP 仍使用 Bearer Header。

### 9.2 `nook-auth` / `nook-user`

- 密码只存 BCrypt 哈希，不记录明文。
- 登录 Token 既要校验 JWT 签名，也要校验 Redis 有效键。
- 改密和踢出逻辑必须覆盖全部已有 Token。
- 好友关系保持双向记录的一致性，接受/删除操作使用事务。

### 9.3 `nook-im`

- 会话成员和角色权限必须在 Service 层校验，不能相信前端隐藏按钮。
- 消息入库和会话最后消息更新处于同一事务。
- 新消息、撤回和在线状态通过 `MessageEventPublisher` 抽象发布。
- 单实例可使用本地 Publisher，多实例使用 RabbitMQ broadcasting；不能用普通竞争消费队列替代广播语义。
- RabbitMQ 每实例匿名队列只承载实时广播，不作为离线消息持久化来源。
- Redis presence 是跨实例状态，进程内 WebSocket Session 只代表当前实例。

### 9.4 `nook-ai`

- 使用 AgentScope 官方稳定 API。升级前检查实际下载 JAR 或官方版本文档，禁止依据旧版包名猜测。
- 保持单例 `HarnessAgent` + `ChatUiChannel`，动态人格通过 `PersonaMiddleware` 按请求注入；不要为每个用户 Agent 创建内部 Gateway。
- 工作区和 AgentState 使用官方 PostgreSQL Store，不能回退到本地文件或 SQLite。
- 同一 owner 的多个 Agent 共享长期记忆命名空间，人格和会话保持隔离。
- 用户可见消息和 AgentScope 内部状态是不同持久化层，修改时分别验证。
- 模型调用失败不能写入伪造 assistant 成功消息。
- 免费额度只由 `QuotaService` 执行；权益来源是 `nook-pay`，不要在 AI 模块复制支付状态机。

### 9.5 `nook-pay`

- 当前产品只支持 `pro_monthly` 订阅。没有积分账本和交付逻辑时，禁止重新开放一次性付款。
- 前端只传产品编码，Price ID 必须由服务端配置映射，不能信任客户端金额或 Price。
- 使用 Stripe Checkout + Billing，不自行处理银行卡数据，不使用 Charges API。
- 不设置 `payment_method_types`，支付方式由 Stripe Dashboard 动态配置。
- 所有创建类 Stripe POST 请求必须使用幂等键；幂等键需要按用户隔离且不得包含敏感信息。
- 同一用户并发 Checkout 必须跨实例串行化，并复用仍有效的开放 Session。
- 成功回跳不是支付成功事实，只能触发向 Stripe 主动对账。
- Webhook 必须基于原始 body 验签，先原子 claim event，再在同一事务同步业务状态。
- 重复事件返回 2xx；签名错误返回 400；临时处理失败返回 5xx 触发 Stripe 重试。
- 订阅和账单事件可能乱序。同步时读取 Stripe 权威对象，并使用事件时间防止旧状态覆盖新状态。
- 权益只授予服务端明确配置的产品，并要求状态为 `active/trialing` 且周期结束时间仍在未来。
- 订阅取消、付款失败、需验证、暂停、坏账等状态必须保留，用户通过 Billing Portal 处理支付方式和取消操作。
- Stripe SDK 跨大版本升级后必须在 sandbox 重放 Webhook；单元测试不能替代真实 Stripe + PostgreSQL 验证。

## 10. 前端规范（`nook-web`）

- Vue 3 Composition API + `<script setup lang="ts">`。
- 页面不直接创建 Axios 实例；所有请求放 `src/api/`，统一走 `src/api/http.ts`。
- 跨页面共享状态使用 Pinia Store；只在单页使用的状态留在页面组件。
- Router 页面使用动态 import，保持按路由拆包。
- TypeScript 新代码避免 `any`，优先 `unknown` + 类型收窄。
- API 类型应与后端 DTO 同步，nullable 字段明确写成 `T | null`。
- 业务是否成功由后端事实决定；前端 query、按钮状态和本地缓存不能直接授予权益或权限。
- 用户输入或 Markdown 渲染必须经过现有安全处理链，禁止直接 `v-html` 未清洗内容。
- 样式复用现有 CSS 变量、间距、圆角和颜色 token，不在单个页面硬编码另一套视觉系统。
- 新交互至少覆盖 loading、empty、error、disabled 和成功状态。
- Mock 只用于显式 `VITE_USE_MOCK=true` 的演示，默认路径必须连接真实 API。

## 11. 测试与验证

### 11.1 命令

后端：

```bash
./mvnw -pl <module> -am test
./mvnw test
./mvnw -pl <module> -am package -DskipTests
```

前端：

```bash
cd nook-web
pnpm build
```

基础检查：

```bash
git diff --check
```

根 POM 已为 JDK 25 配置 Mockito javaagent。不要再要求调用者手工拼接本机 `.m2` 中的 Byte Buddy 路径。

### 11.2 测试规则

- 修 Bug 先写能证明触发条件的回归测试，再修实现。
- 新 Service 逻辑至少覆盖正常、权限/校验失败、第三方失败、重复请求和边界状态。
- 事务或并发行为不能只断言同一个 Java 对象被修改；应验证 Mapper/JDBC 调用、唯一约束或集成后的持久化结果。
- Webhook 测试必须覆盖签名失败、重复事件、已知事件处理失败返回 5xx、乱序和未知映射。
- 前端改动至少通过 `vue-tsc` 和 Vite build；仅通过其中一个不算完成。
- 测试日志中的预期异常可以出现 ERROR，但最终必须以测试进程 exit code 和汇总为准。
- 依赖 PostgreSQL、Nacos、RabbitMQ、RustFS、DeepSeek 或 Stripe 的真实行为若未验证，交付时明确写出限制，不能把单测表述为 E2E。

验证范围与风险匹配：目标模块测试通过后，修改公共模块、父 POM、迁移或 API 契约时必须再跑全量后端测试；涉及前端协议时还要跑前端构建。

## 12. 可观测性

- Actuator 至少保留 health/info；新增业务指标时同步开放相应 endpoint 或导出方式。
- 指标命名使用 `nook.<module>.<business>`，tag 只能使用低基数字段，禁止 userId、sessionId、eventId。
- 第三方调用日志记录渠道 request ID、业务对象 ID 和结果，不记录 Secret 或完整 payload。
- 重试、fail-open、幂等跳过和降级必须有可检索日志或指标，避免静默失败。
- 健康检查区分“应用存活”和“关键外部配置/依赖可用”，不要用一个永远 UP 的假检查。

## 13. AWS 任务规范

当前仓库以本地 Compose/Nacos 为主要运行说明。若任务涉及 AWS：

- 开始前检查并加载适用的 AWS skill；其版本化指引优先于模型常识。
- 优先使用 AWS MCP Server；不可用时再使用 AWS CLI。
- 基础设施优先用 CDK 或 CloudFormation，不用零散 CLI 命令创建不可追踪资源。
- 遵循 AWS Well-Architected 原则，说明安全、可靠性、成本和可运维性取舍。
- AWS 资源名和描述中不要使用 em dash。
- 涉及 Secret、凭据、API Key、Token 或密码时，必须先加载 `aws-secrets-manager` skill。
- 禁止调用 `secretsmanager get-secret-value` / `batch-get-secret-value`，禁止直接访问 Secrets Manager Agent daemon。
- 使用 `{{resolve:secretsmanager:secret-id:SecretString:json-key}}` 配合 `asm-exec`，让 Secret 只在运行时解析且不进入 Agent 上下文。

## 14. Git 与交付规范

- 默认目标分支是 `main`，但修改前仍需确认当前分支。
- 不使用 `git reset --hard`、`git checkout --` 或清理整个工作区来处理用户改动。
- 提交前检查 `git diff --cached --name-status`，确保没有 `.env`、Agent 配置、本地观测包、构建产物或无关文件。
- Commit 使用 Conventional Commits，subject 简洁描述业务结果，例如：

```text
feat(pay): 完善 Stripe 订阅支付闭环
fix(im): 保证消息提交后再广播
refactor(ai): 使用官方 PostgreSQL 状态存储
docs: 补充本地启动说明
```

- 用户要求以 lynn 提交时，Author 和 Committer 都使用：`lynn <3153764534@qq.com>`。
- Agent 参与提交时使用仓库历史一致的 `Co-Authored-By` trailer，不使用 `Assisted-by`：

```text
Co-Authored-By: Codex <noreply@openai.com>
```

- Claude 等其他 Agent 使用其明确身份和对应 noreply 邮箱，不伪造具体模型版本。
- 推送前 fetch 远端并比较 SHA。普通更新使用 fast-forward push；只有用户明确允许改写历史时才使用带精确期望 SHA 的 `--force-with-lease`。
- 交付说明包含：完成内容、验证命令与结果、未验证的外部边界、提交 SHA，以及保留的用户改动。

## 15. 禁止事项

- 不在 Controller 写复杂业务或直接访问数据库。
- 不把网关注入的内部数字 ID返回前端。
- 不让前端成功提示替代后端支付、权限或持久化事实。
- 不用本地内存状态冒充多实例一致性。
- 不在事务提交前向 MQ/WebSocket 发布成功事件。
- 不静默捕获异常后返回成功。
- 不为了“代码更现代”把可变 ORM Entity 改成 record。
- 不同时保留两套功能等价的新旧实现。
- 不提交生成目录、依赖缓存、Agent 下载包或真实 Secret。
- 不宣称“已完整验证”而省略未运行的数据库、Nacos、消息队列、模型或 Stripe E2E。

## 16. Definition of Done

完成改动前逐项检查：

- [ ] 只修改了需求范围内的模块和文档。
- [ ] 所有调用方、DTO、Feign Client、前端类型和文档已同步。
- [ ] 权限、ID 脱敏、并发、失败和重试路径已考虑。
- [ ] 数据库变更使用新的 Flyway 迁移且有唯一约束/索引依据。
- [ ] 目标模块测试通过。
- [ ] 公共契约变化后全量后端测试通过。
- [ ] 前端变化后 `pnpm build` 通过。
- [ ] `git diff --check` 通过。
- [ ] 没有 Secret、构建产物或用户无关改动进入 diff/commit。
- [ ] README、Quick Start、本文件和上线说明与代码一致。
- [ ] 外部环境未验证项在交付说明中明确列出。

若此次改动改变了长期架构或开发约定，必须在同一个提交中更新本文件；不要让规范落后于代码。
