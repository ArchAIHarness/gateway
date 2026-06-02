package top.archaiharness.gateway.cache.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import top.archaiharness.gateway.auth.AuthenticationResult;
import top.archaiharness.gateway.cache.AuthenticationCache;
import top.archaiharness.gateway.config.GatewayProperties;

/**
 * 默认的进程内鉴权缓存。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储,key 为 token 的 SHA-256 摘要(Base64-URL 编码),
 * 单实例满足绝大多数中小规模网关流量。集群级共享缓存请实现自己的 {@link AuthenticationCache}
 * (Redis/Hazelcast 等)并注册为 {@code @Bean} 覆盖默认实现。
 *
 * <p>淘汰由后台定时任务驱动,同时考虑 token exp 与最后访问时间。
 */
@Slf4j
public class InMemoryAuthenticationCache implements AuthenticationCache {

    private final GatewayProperties.Cache properties;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>(16);
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gateway-auth-cache-cleanup");
                t.setDaemon(true);
                return t;
            });

    public InMemoryAuthenticationCache(GatewayProperties.Cache properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        long period = properties.getCleanupIntervalSeconds();
        cleanupExecutor.scheduleAtFixedRate(this::evictExpired, period, period, TimeUnit.SECONDS);
        log.info("InMemoryAuthenticationCache started, maxSize={}, ttlSeconds={}, cleanupInterval={}s",
                properties.getMaxSize(), properties.getTtlSeconds(), period);
    }

    @PreDestroy
    public void stop() {
        cleanupExecutor.shutdownNow();
    }

    @Override
    public AuthenticationResult get(String token) {
        Entry entry = store.get(hash(token));
        if (entry == null) {
            return null;
        }
        long now = Instant.now().toEpochMilli();
        if (!entry.result.isLiveAt(now)) {
            store.remove(hash(token));
            return null;
        }
        entry.lastAccess = now;
        return entry.result;
    }

    @Override
    public void put(String token, AuthenticationResult result) {
        if (store.size() >= properties.getMaxSize()) {
            log.debug("Auth cache full ({}), skipping put", properties.getMaxSize());
            return;
        }
        store.put(hash(token), new Entry(result));
    }

    @Override
    public void clear() {
        store.clear();
    }

    private void evictExpired() {
        long now = Instant.now().toEpochMilli();
        long idleLimitMillis = properties.getTtlSeconds() * 1000L;
        int before = store.size();
        store.entrySet().removeIf(e -> !e.getValue().result.isLiveAt(now)
                || now - e.getValue().lastAccess > idleLimitMillis);
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("Auth cache evicted {} entries, current size={}", removed, store.size());
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 unavailable, falling back to raw token as cache key: {}", e.getMessage());
            return token;
        }
    }

    private static final class Entry {
        final AuthenticationResult result;
        volatile long lastAccess;

        Entry(AuthenticationResult result) {
            this.result = result;
            this.lastAccess = Instant.now().toEpochMilli();
        }
    }
}
