package top.archaiharness.gateway.cache;

import top.archaiharness.gateway.auth.AuthenticationResult;

/**
 * 鉴权结果缓存 SPI。
 *
 * <p>用于避免对每个请求都调用 {@link top.archaiharness.gateway.auth.TokenIntrospector}。
 * 缓存的 key 应为 token 的某种摘要(默认实现使用 SHA-256),而非原始 token 字符串,
 * 既避免内存中明文留存,又使日志/堆转储更安全。
 *
 * <h3>淘汰策略</h3>
 * <p>实现应同时考虑两类失效条件:
 * <ul>
 *   <li>token 本身的 {@code exp}(过期即失效)</li>
 *   <li>访问时间(长期未使用即过期,即 LRU-by-time)</li>
 * </ul>
 */
public interface AuthenticationCache {

    /**
     * 获取缓存项。命中且仍然有效时返回结果,否则返回 {@code null}。
     *
     * <p>实现应在命中时刷新「最后访问时间」,以便 LRU 策略生效。
     *
     * @param token 完整 token(实现内部应做摘要)
     */
    AuthenticationResult get(String token);

    /**
     * 写入缓存。容量已满时实现可选择不写入(避免内存膨胀)。
     *
     * @param token 完整 token
     * @param result 待缓存的鉴权结果
     */
    void put(String token, AuthenticationResult result);

    /**
     * 清空缓存(主要用于运维/测试场景)。
     */
    void clear();
}
