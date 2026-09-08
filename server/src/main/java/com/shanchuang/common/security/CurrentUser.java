package com.shanchuang.common.security;

import com.shanchuang.common.exception.BizException;

/**
 * 当前登录用户。
 *
 * 用 ThreadLocal 而不是把 userId 塞进每个 Controller 方法签名：
 * 业务查询强制带 user_id 是防越权的底线，放在上下文里能保证 Service 层也随手拿得到。
 * 异步任务线程拿不到这个上下文，所以 TaskRunner 一律显式传 userId。
 */
public final class CurrentUser {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long id() {
        Long id = HOLDER.get();
        if (id == null) {
            throw BizException.of(401, "未登录或令牌过期");
        }
        return id;
    }

    public static Long idOrNull() {
        return HOLDER.get();
    }
}
