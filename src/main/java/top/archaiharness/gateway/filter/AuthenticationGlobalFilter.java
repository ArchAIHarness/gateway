package top.archaiharness.gateway.filter;

import java.time.Instant;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import top.archaiharness.gateway.auth.AuthenticationResult;
import top.archaiharness.gateway.auth.TokenExtractor;
import top.archaiharness.gateway.auth.TokenIntrospector;
import top.archaiharness.gateway.cache.AuthenticationCache;
import top.archaiharness.gateway.config.GatewayProperties;
import top.archaiharness.gateway.renew.TokenRenewer;
import top.archaiharness.gateway.support.JsonResponseWriter;
import top.archaiharness.gateway.tenant.HeaderEnricher;
import top.archaiharness.gateway.tenant.TenantAccessValidator;

/**
 * 网关核心过滤器:编排鉴权全流程。
 *
 * <p>本类<b>不</b>承担任何具体策略,所有职责委托给 SPI:
 * <ul>
 *   <li>{@link TokenExtractor} 提取 token</li>
 *   <li>{@link AuthenticationCache} 查/写缓存</li>
 *   <li>{@link TokenIntrospector} 远程或本地校验 token</li>
 *   <li>{@link TenantAccessValidator} 多租户校验</li>
 *   <li>{@link HeaderEnricher} 身份信息透传</li>
 *   <li>{@link TokenRenewer} 临期续期</li>
 * </ul>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>OPTIONS 请求 → 直接放行</li>
 *   <li>无 token → 直接放行,由下游决定是否需要认证</li>
 *   <li>缓存命中 → 走「校验租户 → 透传 Header → 续期(可选)→ 转发」</li>
 *   <li>缓存未命中 → 调用 introspector → 缓存 → 同上</li>
 * </ol>
 */
@Slf4j
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenExtractor tokenExtractor;
    private final AuthenticationCache cache;
    private final TokenIntrospector tokenIntrospector;
    private final TenantAccessValidator tenantValidator;
    private final HeaderEnricher headerEnricher;
    private final TokenRenewer tokenRenewer;
    private final GatewayProperties properties;

    public AuthenticationGlobalFilter(TokenExtractor tokenExtractor,
                                      AuthenticationCache cache,
                                      TokenIntrospector tokenIntrospector,
                                      TenantAccessValidator tenantValidator,
                                      HeaderEnricher headerEnricher,
                                      TokenRenewer tokenRenewer,
                                      GatewayProperties properties) {
        this.tokenExtractor = tokenExtractor;
        this.cache = cache;
        this.tokenIntrospector = tokenIntrospector;
        this.tenantValidator = tenantValidator;
        this.headerEnricher = headerEnricher;
        this.tokenRenewer = tokenRenewer;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }

        String bearerToken = tokenExtractor.extract(request);
        if (bearerToken == null) {
            return chain.filter(exchange);
        }
        String cleanToken = bearerToken.substring(BEARER_PREFIX.length());

        AuthenticationResult cached = cache.get(cleanToken);
        if (cached != null) {
            log.debug("Auth cache hit, userId={}", cached.getUserId());
            return proceed(exchange, chain, cached, cleanToken);
        }

        return tokenIntrospector.introspect(bearerToken)
                .flatMap(result -> {
                    if (!result.isValid()) {
                        log.warn("Invalid token, path={}", request.getPath().value());
                        return JsonResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid token");
                    }
                    cache.put(cleanToken, result);
                    log.info("Auth ok, path={}, userId={}, tenantId={}",
                            request.getPath().value(), result.getUserId(), result.getTenantId());
                    return proceed(exchange, chain, result, cleanToken);
                })
                .onErrorResume(e -> {
                    log.error("Auth service unavailable: {}", e.getMessage());
                    return JsonResponseWriter.write(exchange,
                            HttpStatus.SERVICE_UNAVAILABLE, "Auth service unavailable");
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange,
                               GatewayFilterChain chain,
                               AuthenticationResult auth,
                               String cleanToken) {
        ServerHttpRequest request = exchange.getRequest();
        String clientTenantId = request.getHeaders().getFirst(properties.getHeader().getTenantId());
        String effectiveTenantId = (clientTenantId != null && !clientTenantId.isBlank())
                ? clientTenantId : auth.getTenantId();

        Mono<Void> denied = tenantValidator.validate(exchange, auth, effectiveTenantId);
        if (denied != null) {
            return denied;
        }

        ServerHttpRequest.Builder builder = request.mutate();
        headerEnricher.enrich(builder, auth, effectiveTenantId);
        Mono<Void> forwarded = chain.filter(exchange.mutate().request(builder.build()).build());

        if (shouldRenew(auth)) {
            return forwarded.then(tokenRenewer.renew(cleanToken, exchange.getResponse()));
        }
        return forwarded;
    }

    private boolean shouldRenew(AuthenticationResult auth) {
        if (!properties.getRenew().isEnabled()) {
            return false;
        }
        if (auth.getExpiresAt() == Long.MAX_VALUE) {
            return false;
        }
        long remaining = (auth.getExpiresAt() - Instant.now().toEpochMilli()) / 1000L;
        return remaining > 0 && remaining <= properties.getRenew().getThresholdSeconds();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
