package top.archaiharness.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 网关配置中心。
 * <p>
 * 集中托管 ArchAIHarness Gateway 的全部可调参数,所有默认值与生产经验一致。
 * 用户可通过 {@code application.yml} 中的 {@code archai.gateway.*} 键覆盖。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>无业务硬编码</b>:Header 名、Auth URL、TTL 全部可配</li>
 *   <li><b>合理默认</b>:零配置即可启动,适用于多租户 SaaS 默认场景</li>
 *   <li><b>SPI 友好</b>:配置只承载参数,行为切换通过 SPI Bean 覆盖</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "archai.gateway")
public class GatewayProperties {

    /** 鉴权相关配置 */
    private Auth auth = new Auth();

    /** 鉴权结果缓存配置 */
    private Cache cache = new Cache();

    /** 多租户校验配置 */
    private Tenant tenant = new Tenant();

    /** Token 续期配置 */
    private Renew renew = new Renew();

    /** 请求头透传配置 */
    private Header header = new Header();

    @Data
    public static class Auth {
        /** 远程 auth 服务的基础 URL,默认 {@code http://auth},由 K8s 服务发现解析 */
        private String url = "http://auth";

        /** Auth 服务超时(毫秒) */
        private long timeoutMillis = 5000L;
    }

    @Data
    public static class Cache {
        /** 缓存条目最大数量,达到上限不再写入新条目以避免内存膨胀 */
        private int maxSize = 10_000;

        /** 缓存条目 TTL(秒),最后访问时间超过此值会被清理 */
        private long ttlSeconds = 300L;

        /** 后台清理任务执行周期(秒) */
        private long cleanupIntervalSeconds = 60L;
    }

    @Data
    public static class Tenant {
        /** 是否启用多租户校验 */
        private boolean enabled = true;

        /** 通配符值,匹配时跳过校验,代表「访问所有可访问租户」 */
        private String wildcard = "*";
    }

    @Data
    public static class Renew {
        /** 是否启用 Token 自动续期 */
        private boolean enabled = true;

        /** Token 剩余有效期低于此阈值(秒)时触发续期 */
        private long thresholdSeconds = 600L;

        /** 续期端点路径,会拼接到 {@link Auth#getUrl()} 之后 */
        private String endpoint = "/refresh_token";
    }

    @Data
    public static class Header {
        /** 用户 ID 透传 Header 名 */
        private String userId = "x-user-id";

        /** 当前业务租户 ID Header 名 */
        private String tenantId = "x-tenant-id";

        /** 可访问租户 ID 列表 Header 名(逗号分隔) */
        private String tenantIds = "x-tenant-ids";

        /** Token 已续期响应 Header 名(网关返回给客户端) */
        private String tokenRenewed = "x-token-renewed";
    }
}
