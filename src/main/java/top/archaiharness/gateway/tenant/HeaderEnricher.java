package top.archaiharness.gateway.tenant;

import org.springframework.http.server.reactive.ServerHttpRequest;

import top.archaiharness.gateway.auth.AuthenticationResult;

/**
 * 请求头透传 SPI。
 *
 * <p>负责将 {@link AuthenticationResult} 转化为下游可识别的请求头。默认实现写入
 * {@code x-user-id}、{@code x-tenant-id}、{@code x-tenant-ids}(名称可配)。
 *
 * <p>用户可通过自定义 {@code @Bean} 加入更多透传字段(如签名时间戳、租户类型、
 * 用户类型),或写入网关签名以供下游验证调用链。
 */
@FunctionalInterface
public interface HeaderEnricher {

    /**
     * 在请求 mutate 阶段写入所需的 Header。
     *
     * @param builder 请求构造器
     * @param authentication 已通过 Token 校验的鉴权结果
     * @param effectiveTenantId 经过覆盖逻辑后最终生效的租户 ID
     */
    void enrich(ServerHttpRequest.Builder builder,
                AuthenticationResult authentication,
                String effectiveTenantId);
}
