package com.shanchuang.modules.credit.dto;

/** 额度概览，对应 docs/接口设计.md 10.1 */
public record CreditAccountVO(
        int balance,
        int hold,
        int totalGranted,
        int totalRecharged,
        int totalConsumed
) {
}
