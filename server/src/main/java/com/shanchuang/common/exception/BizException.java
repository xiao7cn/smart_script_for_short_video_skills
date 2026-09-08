package com.shanchuang.common.exception;

/** 业务异常。code 对应 docs/接口设计.md 1.2 的错误码表 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    /* 高频错误的快捷构造 */

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }
}
