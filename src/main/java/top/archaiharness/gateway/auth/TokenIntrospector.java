package top.archaiharness.gateway.auth;

import reactor.core.publisher.Mono;

/**
 * Token 校验与身份解析 SPI。
 *
 * <p>本接口将「网关如何确认 token 合法性」与「网关编排逻辑」解耦,允许多种实现:
 * <ul>
 *   <li>{@code RemoteAuthTokenIntrospector}(默认):调用远程 auth 服务验证</li>
 *   <li>本地 JWT 验签:配置签名密钥后纯本地校验,零跨服务调用</li>
 *   <li>OAuth2 Introspection:对接 RFC 7662 标准端点</li>
 * </ul>
 *
 * <h3>关于 JWT 验签</h3>
 * <p>默认实现 <b>不在网关本地验签</b>,而是委托给 auth 服务进行权威校验。这是经过
 * 权衡的设计:网关定位于「策略编排与流量调度」,身份证明的权威性留给鉴权中心。
 * 这一前提下,网关读取 JWT 仅用于获取 {@code exp} 字段以判断是否需要续期,不应
 * 用于安全决策。需要本地零调用验签的场景,请提供自定义 {@code TokenIntrospector}。
 */
@FunctionalInterface
public interface TokenIntrospector {

    /**
     * 校验 token 并解析出鉴权结果。
     *
     * @param bearerToken 含 {@code "Bearer "} 前缀的完整 token,由 {@link TokenExtractor} 提供
     * @return 鉴权结果。校验失败时返回的 {@link AuthenticationResult#isValid()} 为 false。
     *         Auth 服务不可用等基础设施异常,以 {@code Mono.error} 形式抛出。
     */
    Mono<AuthenticationResult> introspect(String bearerToken);
}
