package top.archaiharness.gateway.auth;

import java.util.Objects;

import lombok.Builder;
import lombok.Value;

/**
 * 鉴权结果。承载一次成功鉴权后的身份与租户信息,供下游过滤器消费。
 *
 * <p>不可变(Value),线程安全。
 */
@Value
@Builder
public class AuthenticationResult {

    /** 用户 ID,鉴权成功必有,空值视为失败 */
    String userId;

    /** 当前业务租户 ID,可为 null(用户未指定) */
    String tenantId;

    /** 用户可访问的全部租户 ID(逗号分隔),可为 null */
    String tenantIds;

    /**
     * Token 失效时间戳(epoch millis),用于决定是否需要续期。
     * 当 token 不可解析时,值为 {@link Long#MAX_VALUE} 表示「未知,不续期」。
     */
    long expiresAt;

    public boolean isValid() {
        return userId != null && !userId.isBlank();
    }

    /**
     * 检查至给定时刻为止 token 是否仍然有效。
     */
    public boolean isLiveAt(long epochMillis) {
        return epochMillis <= expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthenticationResult other)) return false;
        return expiresAt == other.expiresAt
                && Objects.equals(userId, other.userId)
                && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(tenantIds, other.tenantIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId, tenantIds, expiresAt);
    }
}
