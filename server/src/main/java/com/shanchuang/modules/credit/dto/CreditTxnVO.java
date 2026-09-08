package com.shanchuang.modules.credit.dto;

import java.time.LocalDateTime;

/** 额度流水，对应 docs/接口设计.md 10.4 */
public record CreditTxnVO(
        Long id,
        String type,
        int amount,
        int balanceAfter,
        String refType,
        String refId,
        String remark,
        LocalDateTime createdAt
) {
}
