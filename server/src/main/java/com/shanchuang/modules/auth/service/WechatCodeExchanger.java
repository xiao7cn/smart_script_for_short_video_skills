package com.shanchuang.modules.auth.service;

import com.shanchuang.modules.auth.dto.WechatIdentity;

/**
 * 「code 换 openid」的接入点。
 *
 * 真实微信授权要打 jscode2session 与 getuserphonenumber，涉及 appid/secret 与 access_token 缓存；
 * 本项目暂不接，默认走演示实现。接真实授权时新增一个实现并标 @Primary 即可，AuthService 不用改。
 */
public interface WechatCodeExchanger {

    WechatIdentity exchange(String code, String phoneCode);
}
