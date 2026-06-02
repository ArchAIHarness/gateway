package top.archaiharness.gateway.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import top.archaiharness.gateway.auth.TokenExtractor;
import top.archaiharness.gateway.auth.TokenIntrospector;
import top.archaiharness.gateway.auth.support.DefaultTokenExtractor;
import top.archaiharness.gateway.auth.support.RemoteAuthTokenIntrospector;
import top.archaiharness.gateway.cache.AuthenticationCache;
import top.archaiharness.gateway.cache.support.InMemoryAuthenticationCache;
import top.archaiharness.gateway.filter.AuthenticationGlobalFilter;
import top.archaiharness.gateway.renew.TokenRenewer;
import top.archaiharness.gateway.renew.support.RefreshEndpointTokenRenewer;
import top.archaiharness.gateway.tenant.HeaderEnricher;
import top.archaiharness.gateway.tenant.TenantAccessValidator;
import top.archaiharness.gateway.tenant.support.DefaultHeaderEnricher;
import top.archaiharness.gateway.tenant.support.MultiTenantAccessValidator;

/**
 * 网关默认自动配置。
 *
 * <p>所有 Bean 都标注 {@link ConditionalOnMissingBean},用户只需在自己工程中
 * 提供同类型 {@code @Bean} 即可完全覆盖,实现「约定优于配置 + 完全可替换」。
 *
 * <h3>典型扩展场景</h3>
 * <pre>{@code
 * @Bean
 * TokenIntrospector myTokenIntrospector() {
 *     return bearerToken -> Mono.just(...);  // 例如本地 JWT 验签
 * }
 *
 * @Bean
 * AuthenticationCache redisAuthCache(RedisTemplate<...> redis) {
 *     return new RedisAuthenticationCache(redis);  // 集群级共享缓存
 * }
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient gatewayWebClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenExtractor tokenExtractor() {
        return new DefaultTokenExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenIntrospector tokenIntrospector(WebClient gatewayWebClient,
                                               GatewayProperties properties) {
        return new RemoteAuthTokenIntrospector(gatewayWebClient, properties.getAuth());
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AuthenticationCache authenticationCache(GatewayProperties properties) {
        InMemoryAuthenticationCache cache = new InMemoryAuthenticationCache(properties.getCache());
        cache.start();
        return cache;
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantAccessValidator tenantAccessValidator(GatewayProperties properties) {
        return new MultiTenantAccessValidator(properties.getTenant());
    }

    @Bean
    @ConditionalOnMissingBean
    public HeaderEnricher headerEnricher(GatewayProperties properties) {
        return new DefaultHeaderEnricher(properties.getHeader());
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenRenewer tokenRenewer(WebClient gatewayWebClient, GatewayProperties properties) {
        return new RefreshEndpointTokenRenewer(gatewayWebClient,
                properties.getAuth(),
                properties.getRenew(),
                properties.getHeader());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationGlobalFilter authenticationGlobalFilter(TokenExtractor tokenExtractor,
                                                                 AuthenticationCache cache,
                                                                 TokenIntrospector tokenIntrospector,
                                                                 TenantAccessValidator tenantValidator,
                                                                 HeaderEnricher headerEnricher,
                                                                 TokenRenewer tokenRenewer,
                                                                 GatewayProperties properties) {
        return new AuthenticationGlobalFilter(tokenExtractor, cache, tokenIntrospector,
                tenantValidator, headerEnricher, tokenRenewer, properties);
    }
}
