package top.archaiharness.gateway.renew;

import org.springframework.http.server.reactive.ServerHttpResponse;

import reactor.core.publisher.Mono;

/**
 * Token 续期 SPI。
 *
 * <p>当 Token 剩余有效期低于阈值时被调用。默认实现 {@code RefreshEndpointTokenRenewer}
 * 调用 auth 服务的 {@code /refresh_token} 端点,把新 Token 写入响应头供客户端读取。
 *
 * <p>实现应是「侧效应」,即使续期失败也不应中断主请求(由调用方决定如何处理错误)。
 */
@FunctionalInterface
public interface TokenRenewer {

    /**
     * 执行续期。
     *
     * @param cleanToken 不含 {@code "Bearer "} 前缀的纯 token
     * @param response 网关响应,实现可向其写入续期结果 Header
     * @return 续期完成的信号(无论成功失败,都应正常完成)
     */
    Mono<Void> renew(String cleanToken, ServerHttpResponse response);
}
