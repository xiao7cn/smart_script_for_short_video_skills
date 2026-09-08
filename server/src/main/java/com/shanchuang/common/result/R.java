package com.shanchuang.common.result;

/**
 * 统一响应体。HTTP 状态码一律 200（鉴权与服务异常除外），业务结果看 code。
 * 对应 docs/接口设计.md 1.1。
 */
public record R<T>(int code, String message, T data, String requestId) {

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data, null);
    }

    public static <T> R<T> ok() {
        return new R<>(0, "ok", null, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, null);
    }

    public R<T> withRequestId(String id) {
        return new R<>(code, message, data, id);
    }
}
