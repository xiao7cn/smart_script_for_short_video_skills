package com.shanchuang.modules.auth.dto;

/** 对应 docs/接口设计.md 2.2 / 2.3 */
public record LoginVO(String token, long expiresIn, boolean firstLogin, UserVO user, int credits) {
}
