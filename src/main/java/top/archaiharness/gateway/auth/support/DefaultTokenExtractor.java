package top.archaiharness.gateway.auth.support;

import org.springframework.http.server.reactive.ServerHttpRequest;

import top.archaiharness.gateway.auth.TokenExtractor;

/**
 * 默认 Token 提取器。
 *
 * <p>提取顺序:
 * <ol>
 *   <li>{@code Authorization} 请求头(优先)</li>
 *   <li>{@code token} 查询参数(回退,便于 WebSocket 等无法设置 Header 的场景)</li>
 * </ol>
 *
 * <p>无论原始 token 是否带 {@code Bearer } 前缀,返回值都统一前缀化,简化下游处理。
 */
public class DefaultTokenExtractor implements TokenExtractor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TOKEN_QUERY_PARAM = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public String extract(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (token == null || token.isBlank()) {
            token = request.getQueryParams().getFirst(TOKEN_QUERY_PARAM);
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.startsWith(BEARER_PREFIX) ? token : BEARER_PREFIX + token;
    }
}
