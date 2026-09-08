package com.shanchuang.modules.credit.dto;

/** 对应 docs/接口设计.md 10.3。credited = base + bonus */
public record RechargeVO(String orderNo, String status, int credited, int balance) {
}
