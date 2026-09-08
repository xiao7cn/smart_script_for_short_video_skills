package com.shanchuang.common.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdUtil() {
    }

    /** 对外任务号：32 位无连字符，不暴露自增 id */
    public static String taskNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 充值订单号：可读的时间前缀 + 随机尾巴 */
    public static String orderNo() {
        return "R" + LocalDateTime.now().format(ORDER_TIME) + randomDigits(6);
    }

    public static String fileId() {
        return "f_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    public static String requestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String smsCode() {
        return randomDigits(6);
    }

    public static String shareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
