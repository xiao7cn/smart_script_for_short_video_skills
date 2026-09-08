package com.shanchuang.modules.auth.dto;

/** code 换取到的微信身份。phone 只有在拿到手机号授权时才有值，用于账号归并 */
public record WechatIdentity(String openId, String unionId, String phone, String nickname, String avatar) {
}
