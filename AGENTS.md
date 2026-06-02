# AGENTS.md · ArchAIHarness Gateway

> AI 协作开发的强约束文档。本仓库内任何由 AI 完成的代码编辑都必须遵守以下条款,**不得以"任务简单"为由跳过**。

---

## 0. 项目本质

`gateway` 是 **ArchAIHarness 体系下的反应式 API 网关**:

- 单模块 Spring Boot 应用,不可拆为多模块
- 全反应式(WebFlux + Reactor),不可引入任何阻塞 API
- 所有鉴权策略基于 SPI,默认实现 + `@ConditionalOnMissingBean`,**不允许把业务策略硬编码进编排过滤器**

---

## 1. 强制技术栈

| 维度 | 锁定 | 说明 |
|------|------|------|
| Java | **17** | 不允许提升至 21 / 降级到 11 |
| Spring Boot | **3.2.5** | 与 Spring Cloud 2023.0.3 严格兼容 |
| Spring Cloud Gateway | **2023.0.3** | 不允许跨大版本 |
| 服务发现 | **Spring Cloud Kubernetes (Fabric8)** | 不允许引入 Eureka/Nacos |
| 日志 | **SLF4J + Lombok @Slf4j** | 严禁 `System.out` / `printStackTrace` |
| JSON 序列化 | **手写或 Jackson** | 不引入额外 JSON 库 |
| HTTP 客户端 | **WebClient** | 严禁 `RestTemplate` / `HttpClient` 同步调用 |

---

## 2. 反应式纪律(P0)

- ❌ `Thread.sleep`、`Object.wait`、`CompletableFuture.get`、`Mono.block()`
- ❌ 任何 JDBC/JPA 同步操作
- ❌ `RestTemplate`
- ✅ `Mono` / `Flux` 链式表达
- ✅ `WebClient` 发起所有外部调用
- ✅ 需要并行时使用 `Mono.zip` / `Flux.merge`,不是新建线程

违反任一条 = 整段代码作废重写。

---

## 3. SPI 设计纪律

主过滤器 `AuthenticationGlobalFilter` **只做编排**:
- 不写 `if (token.startsWith("Bearer"))`
- 不写具体的 token 校验逻辑
- 不写具体的缓存实现
- 不写具体的租户规则

所有上述职责必须分别落到对应 SPI:

| 职责 | SPI | 默认实现 |
|------|-----|----------|
| 提取 token | `TokenExtractor` | `DefaultTokenExtractor` |
| 校验 token | `TokenIntrospector` | `RemoteAuthTokenIntrospector` |
| 缓存鉴权结果 | `AuthenticationCache` | `InMemoryAuthenticationCache` |
| 多租户校验 | `TenantAccessValidator` | `MultiTenantAccessValidator` |
| Header 透传 | `HeaderEnricher` | `DefaultHeaderEnricher` |
| Token 续期 | `TokenRenewer` | `RefreshEndpointTokenRenewer` |

**新增任何业务策略,首选方案是新增/扩展 SPI,而不是塞进 `AuthenticationGlobalFilter`。**

所有默认 `@Bean` 必须标 `@ConditionalOnMissingBean`,让用户可零侵入覆盖。

---

## 4. 配置纪律

- 所有可调参数 → `GatewayProperties`(`archai.gateway.*`)
- 严禁在代码中硬编码 URL、Header 名、TTL、阈值
- 新增配置项必须:
  1. 在 `GatewayProperties` 中加字段 + Javadoc 说明
  2. 在 `application.yml` 中写出默认值与注释
  3. 在 `readme.md` 配置参考表中登记

---

## 5. JWT 安全说明(必读)

`RemoteAuthTokenIntrospector` 中使用 `Jwts.parser().unsecured()` 读取 JWT exp,**这是有意为之**:
- 网关本身不验签,token 合法性完全由 auth 服务认定
- 读取 exp 仅用于「是否需要续期」的非安全决策
- 即使 exp 被伪造,也无法绕过 auth 服务的权威校验

**任何修改此段代码的尝试必须先证明:不会因为引入本地验签而让网关与 auth 服务对 token 合法性的判定不一致。**

---

## 6. Header 与请求规范

- Header 名 **必须全小写**(`x-user-id`,不是 `X-User-Id`)
- Header 名禁止硬编码,统一从 `GatewayProperties.Header` 读取
- 路由前缀策略固定 `StripPrefix(3)`,不允许在过滤器里再做路径手术

---

## 7. K8s 与部署

- 不允许引入 `k8s/` 目录(K8s 描述符由外部仓库管理)
- 不允许修改 `Dockerfile`(已固化最佳实践:多阶段、非 root 用户、Asia/Shanghai 时区)
- 探针端点必须保持 `/actuator/health/{liveness,readiness}` 可用
- 端口必须保持 8080(容器) / 80(Service)

---

## 8. 代码风格

- Lombok 允许:`@Slf4j`、`@Data`、`@Value`、`@Builder`
- Lombok 禁止:`@SneakyThrows`(隐藏检查异常)
- 公开类必须有 Javadoc(类级别);SPI 接口的每个方法必须有 Javadoc
- 不允许 `@SuppressWarnings("null")` 这类掩盖问题的注解
- import 按字母排序,按 java/javax/jakarta/第三方/本项目分组

---

## 9. 测试

- 单元测试用 JUnit 5 + Reactor `StepVerifier`
- 不允许使用真实网络(用 `MockWebServer` 或 WireMock)
- 测试覆盖率不是硬指标,**关键路径必须有测试**(主过滤器编排、SPI 默认实现)

---

## 10. Git 与提交

- Commit message 中文,首行 ≤ 50 字符,正文每行 ≤ 72 字符
- 必须能用一句话讲清这次提交的本质改变
- 严禁 `git push --force` 到 main
- AI 代理在编辑后:`mvn clean compile` 必须通过,否则不允许提交

---

> 本文档优先级高于个人偏好。如发现条款与实际需要冲突,请发起 PR 修订本文档,而不是绕过条款。
