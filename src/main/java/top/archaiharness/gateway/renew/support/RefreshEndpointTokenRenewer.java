package top.archaiharness.gateway.renew.support;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import top.archaiharness.gateway.config.GatewayProperties;
import top.archaiharness.gateway.renew.TokenRenewer;

/**
 * 默认续期实现:POST 到 {@code authUrl + endpoint},把响应体作为新 Token 写入
 * 响应头({@link GatewayProperties.Header#getTokenRenewed()})。
 *
 * <p>续期是「best effort」语义:失败时记 warn 日志,主请求不受影响。
 */
@Slf4j
public class RefreshEndpointTokenRenewer implements TokenRenewer {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;
    private final GatewayProperties.Auth authProperties;
    private final GatewayProperties.Renew renewProperties;
    private final GatewayProperties.Header headerProperties;

    public RefreshEndpointTokenRenewer(WebClient webClient,
                                       GatewayProperties.Auth authProperties,
                                       GatewayProperties.Renew renewProperties,
                                       GatewayProperties.Header headerProperties) {
        this.webClient = webClient;
        this.authProperties = authProperties;
        this.renewProperties = renewProperties;
        this.headerProperties = headerProperties;
    }

    @Override
    public Mono<Void> renew(String cleanToken, ServerHttpResponse response) {
        if (!renewProperties.isEnabled()) {
            return Mono.empty();
        }
        return webClient.post()
                .uri(authProperties.getUrl() + renewProperties.getEndpoint())
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + cleanToken)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> {
                    response.getHeaders().add(headerProperties.getTokenRenewed(), body);
                    log.info("Token renewed");
                })
                .then()
                .onErrorResume(e -> {
                    log.warn("Token renewal failed (non-fatal): {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
