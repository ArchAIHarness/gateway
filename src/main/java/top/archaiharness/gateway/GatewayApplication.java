package top.archaiharness.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ArchAIHarness Gateway 启动类。
 *
 * <p>基于 Spring Cloud Gateway + Spring Cloud Kubernetes,提供:
 * <ul>
 *   <li>K8s 服务发现驱动的动态路由(DiscoveryLocator + StripPrefix)</li>
 *   <li>可插拔的 Token 鉴权链(由 {@code GatewayAutoConfiguration} 装配默认实现)</li>
 *   <li>多租户访问校验</li>
 *   <li>Token 自动续期</li>
 * </ul>
 *
 * <p>所有鉴权与租户行为均通过 SPI({@code TokenExtractor}/{@code TokenIntrospector}/
 * {@code AuthenticationCache}/{@code TenantAccessValidator}/{@code HeaderEnricher}/
 * {@code TokenRenewer})对外开放,定义同类型 {@code @Bean} 即可覆盖默认实现。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
