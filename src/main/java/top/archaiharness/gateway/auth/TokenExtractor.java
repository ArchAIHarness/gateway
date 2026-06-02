package top.archaiharness.gateway.auth;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Token 提取 SPI。
 *
 * <p>负责从入站请求中识别并提取原始 token。默认实现 {@code DefaultTokenExtractor}
 * 优先读取 {@code Authorization} 头,缺失时回退到 {@code token} 查询参数。
 *
 * <p>用户可通过定义自己的 {@code TokenExtractor} {@code @Bean} 来覆盖默认行为
 * (例如从 Cookie、自定义 Header 中提取)。
 */
@FunctionalInterface
public interface TokenExtractor {

    /**
     * 提取 token。
     *
     * @param request 入站请求
     * @return 含 {@code "Bearer "} 前缀的完整 token 字符串;无 token 时返回 {@code null}
     */
    String extract(ServerHttpRequest request);
}
