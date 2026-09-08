package com.shanchuang.modules.auth.dto;

/** devCode 只在 app.sms.echo-code=true 时有值，用于原型里「演示验证码」那一行 */
public record SmsCodeVO(boolean sent, int cooldown, String devCode) {
}
