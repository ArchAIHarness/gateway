package top.archaiharness.gateway.auth.support;

import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import top.archaiharness.gateway.auth.AuthenticationResult;
import top.archaiharness.gateway.auth.TokenIntrospector;
import top.archaiharness.gateway.config.GatewayProperties;

/**
 * 默认 Token Introspector:委托给远程 auth 服务校验 token,并从响应 Header 中
 * 抽取身份与租户信息。
 *
 * <h3>JWT exp 解析</h3>
 * <p>本实现额外解析 JWT payload 中的 {@code exp} 字段,目的<b>仅</b>是供续期
 * 模块决定是否需要刷新。<b>不</b>使用 exp 做安全决策(放行/拦截)。这一处理
 * 即使在 token 被恶意伪造时也不会带来安全损失,因为 token 的合法性由 auth
 * 服务而非网关认定。
 */
@Slf4j
public class RemoteAuthTokenIntrospector implements TokenIntrospector {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_ID_HEADER = "x-user-id";
    private static final String TENANT_ID_HEADER = "x-tenant-id";
    private static final String TENANT_IDS_HEADER = "x-tenant-ids";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int HTTP_UNAUTHORIZED = 401;

    private final WebClient webClient;
    private final GatewayProperties.Auth authProperties;

    public RemoteAuthTokenIntrospector(WebClient webClient, GatewayProperties.Auth authProperties) {
        this.webClient = webClient;
        this.authProperties = authProperties;
    }

    @Override
    public Mono<AuthenticationResult> introspect(String bearerToken) {
        return webClient.get()
                .uri(authProperties.getUrl())
                .header(AUTHORIZATION_HEADER, bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    int status = response.getStatusCode().value();
                    if (status == HTTP_UNAUTHORIZED) {
                        return invalid();
                    }
                    HttpHeaders headers = response.getHeaders();
                    String userId = headers.getFirst(USER_ID_HEADER);
                    if (userId == null) {
                        log.warn("Auth response missing {} header", USER_ID_HEADER);
                        return invalid();
                    }
                    String cleanToken = bearerToken.startsWith(BEARER_PREFIX)
                            ? bearerToken.substring(BEARER_PREFIX.length())
                            : bearerToken;
                    return AuthenticationResult.builder()
                            .userId(userId)
                            .tenantId(headers.getFirst(TENANT_ID_HEADER))
                            .tenantIds(headers.getFirst(TENANT_IDS_HEADER))
                            .expiresAt(parseJwtExpiresAt(cleanToken))
                            .build();
                });
    }

    private AuthenticationResult invalid() {
        return AuthenticationResult.builder()
                .userId(null)
                .expiresAt(0L)
                .build();
    }

    /**
     * 解析 JWT exp,仅用于续期决策,不参与安全校验。
     */
    private long parseJwtExpiresAt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Long.MAX_VALUE;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            Claims claims = Jwts.parser()
                    .unsecured()
                    .build()
                    .parseUnsecuredClaims(payload)
                    .getPayload();
            return claims.getExpiration().getTime();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Cannot parse JWT exp (treated as no-renew): {}", e.getMessage());
            return Long.MAX_VALUE;
        }
    }
}
