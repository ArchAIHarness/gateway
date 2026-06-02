package top.archaiharness.gateway.tenant.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import top.archaiharness.gateway.auth.AuthenticationResult;
import top.archaiharness.gateway.config.GatewayProperties;
import top.archaiharness.gateway.support.JsonResponseWriter;
import top.archaiharness.gateway.tenant.TenantAccessValidator;

/**
 * 默认多租户校验器。
 *
 * <p>规则:
 * <ol>
 *   <li>请求未声明 tenantId(为 null/blank) → 放行(由下游服务决定是否需要)</li>
 *   <li>tenantId 为通配符({@link GatewayProperties.Tenant#getWildcard()}) → 放行</li>
 *   <li>用户无任何租户权限({@code tenantIds} 为空) → 403 拒绝</li>
 *   <li>tenantId 在用户可访问列表中 → 放行</li>
 *   <li>tenantId 不在用户可访问列表中 → 403 拒绝</li>
 * </ol>
 */
@Slf4j
public class MultiTenantAccessValidator implements TenantAccessValidator {

    private final GatewayProperties.Tenant properties;

    public MultiTenantAccessValidator(GatewayProperties.Tenant properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> validate(ServerWebExchange exchange,
                               AuthenticationResult authentication,
                               String effectiveTenantId) {
        if (!properties.isEnabled()) {
            return null;
        }
        if (effectiveTenantId == null || effectiveTenantId.isBlank()) {
            return null;
        }
        if (properties.getWildcard().equals(effectiveTenantId)) {
            return null;
        }
        String accessible = authentication.getTenantIds();
        if (accessible == null || accessible.isBlank()) {
            log.warn("Tenant access denied: user={} has no tenant, requested={}",
                    authentication.getUserId(), effectiveTenantId);
            return JsonResponseWriter.write(exchange, HttpStatus.FORBIDDEN,
                    "Forbidden: tenant not accessible");
        }
        for (String id : accessible.split(",")) {
            if (id.trim().equals(effectiveTenantId)) {
                return null;
            }
        }
        log.warn("Tenant access denied: user={}, requested={}, allowed={}",
                authentication.getUserId(), effectiveTenantId, accessible);
        return JsonResponseWriter.write(exchange, HttpStatus.FORBIDDEN,
                "Forbidden: tenant not accessible");
    }
}
