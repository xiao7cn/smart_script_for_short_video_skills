package com.shanchuang.modules.auth.service;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.modules.auth.dto.WechatIdentity;
import org.springframework.stereotype.Component;

/**
 * 演示实现：把 code 本身当 openid 用（含 wx_demo_ 前缀的演示 code）。
 * phoneCode 传 11 位手机号时视为「拿到了手机号授权」，用来验证账号归并链路。
 */
@Component
public class DemoWechatCodeExchanger implements WechatCodeExchanger {

    private static final String PHONE_PATTERN = "^1\\d{10}$";

    @Override
    public WechatIdentity exchange(String code, String phoneCode) {
        if (TextUtil.isBlank(code)) {
            throw BizException.of(1005, "微信授权失败");
        }
        String openId = code.trim();
        String phone = phoneCode != null && phoneCode.trim().matches(PHONE_PATTERN) ? phoneCode.trim() : null;
        return new WechatIdentity(openId, null, phone, "微信用户", null);
    }
}
