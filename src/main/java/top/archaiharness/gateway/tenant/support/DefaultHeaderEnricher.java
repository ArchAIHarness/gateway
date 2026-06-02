package top.archaiharness.gateway.tenant.support;

import org.springframework.http.server.reactive.ServerHttpRequest;

import top.archaiharness.gateway.auth.AuthenticationResult;
import top.archaiharness.gateway.config.GatewayProperties;
import top.archaiharness.gateway.tenant.HeaderEnricher;

/**
 * 默认 Header 透传:写入 {@code x-user-id} / {@code x-tenant-id} / {@code x-tenant-ids}。
 *
 * <p>Header 名全部从 {@link GatewayProperties.Header} 取得,业务方可改名以匹配既有约定。
 */
public class DefaultHeaderEnricher implements HeaderEnricher {

    private final GatewayProperties.Header properties;

    public DefaultHeaderEnricher(GatewayProperties.Header properties) {
        this.properties = properties;
    }

    @Override
    public void enrich(ServerHttpRequest.Builder builder,
                       AuthenticationResult authentication,
                       String effectiveTenantId) {
        builder.header(properties.getUserId(), authentication.getUserId());
        if (effectiveTenantId != null && !effectiveTenantId.isBlank()) {
            builder.header(properties.getTenantId(), effectiveTenantId);
        }
        if (authentication.getTenantIds() != null && !authentication.getTenantIds().isBlank()) {
            builder.header(properties.getTenantIds(), authentication.getTenantIds());
        }
    }
}
