package com.shanchuang.modules.credit.dto;

/** 充值套餐，对应 docs/接口设计.md 10.2 */
public record CreditPackVO(String packId, int priceFen, int base, int bonus) {
}
