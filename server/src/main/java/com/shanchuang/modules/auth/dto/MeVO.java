package com.shanchuang.modules.auth.dto;

/** 对应 docs/接口设计.md 2.4 */
public record MeVO(UserVO user, int credits, StatsVO stats) {
}
