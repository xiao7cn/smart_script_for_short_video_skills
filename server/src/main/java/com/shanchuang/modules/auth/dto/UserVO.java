package com.shanchuang.modules.auth.dto;

/** account：手机号注册返回手机号，微信注册返回 openid */
public record UserVO(Long userId, String nickname, String account, String via, String avatar) {
}
