package com.shanchuang.modules.auth.controller;

import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.modules.auth.dto.LoginVO;
import com.shanchuang.modules.auth.dto.MeVO;
import com.shanchuang.modules.auth.dto.SmsCodeReq;
import com.shanchuang.modules.auth.dto.SmsCodeVO;
import com.shanchuang.modules.auth.dto.SmsLoginReq;
import com.shanchuang.modules.auth.dto.WechatLoginReq;
import com.shanchuang.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账号，对应 docs/接口设计.md 第 2 章 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sms/code")
    public R<SmsCodeVO> smsCode(@RequestBody SmsCodeReq req, HttpServletRequest http) {
        String phone = req == null ? null : req.phone();
        return R.ok(authService.sendSmsCode(phone, clientIp(http)));
    }

    @PostMapping("/sms/login")
    public R<LoginVO> smsLogin(@RequestBody SmsLoginReq req) {
        String phone = req == null ? null : req.phone();
        String code = req == null ? null : req.code();
        return R.ok(authService.smsLogin(phone, code));
    }

    @PostMapping("/wechat/login")
    public R<LoginVO> wechatLogin(@RequestBody WechatLoginReq req) {
        String code = req == null ? null : req.code();
        String phoneCode = req == null ? null : req.phoneCode();
        return R.ok(authService.wechatLogin(code, phoneCode));
    }

    @GetMapping("/me")
    public R<MeVO> me() {
        return R.ok(authService.me(CurrentUser.id()));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout(CurrentUser.id());
        return R.ok();
    }

    private String clientIp(HttpServletRequest http) {
        if (http == null) {
            return null;
        }
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
