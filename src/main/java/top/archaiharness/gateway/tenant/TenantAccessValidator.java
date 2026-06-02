package top.archaiharness.gateway.tenant;

import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import top.archaiharness.gateway.auth.AuthenticationResult;

/**
 * 多租户访问校验 SPI。
 *
 * <p>判定当前请求是否被允许访问指定租户。默认实现 {@code MultiTenantAccessValidator}
 * 基于「请求声明的租户 ID 必须在用户可访问租户列表内」的规则;不存在该列表则视为
 * 没有任何租户权限,直接拒绝。
 *
 * <p>需要更复杂的策略(基于角色、组、外部 RBAC 服务等)时,提供自定义 {@code @Bean}
 * 即可覆盖。
 */
public interface TenantAccessValidator {

    /**
     * 校验访问。允许访问返回 {@code Mono.empty()},拒绝访问返回写入了 HTTP 响应的
     * {@code Mono<Void>}(由实现负责使用 {@code ServerHttpResponse} 输出错误)。
     *
     * @param exchange 当前请求上下文
     * @param authentication 已通过 Token 校验的鉴权结果
     * @param effectiveTenantId 经过覆盖逻辑后最终生效的租户 ID(可能来自 client header)
     * @return 校验通过返回 {@code null};拒绝返回带响应的 {@code Mono<Void>}
     */
    Mono<Void> validate(ServerWebExchange exchange,
                        AuthenticationResult authentication,
                        String effectiveTenantId);
}
