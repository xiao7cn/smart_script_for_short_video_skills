package com.shanchuang.modules.auth;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.modules.auth.dto.SmsCodeVO;
import com.shanchuang.modules.auth.service.AuthService;
import com.shanchuang.modules.testsupport.ModuleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsCodeFlowTest extends ModuleTestBase {

    private static final String PHONE = "13800001111";
    private static final String IP = "127.0.0.1";

    @Autowired
    private AuthService authService;

    @Test
    void 手机号不合规返回1001() {
        assertEquals(1001, code(() -> authService.sendSmsCode("1380000", IP)));
        assertEquals(1001, code(() -> authService.sendSmsCode("23800001111", IP)));
        assertEquals(1001, code(() -> authService.sendSmsCode(null, IP)));
        assertEquals(0, count("sv_sms_code"));
    }

    @Test
    void 正常下发并回显演示验证码() {
        SmsCodeVO vo = authService.sendSmsCode(PHONE, IP);

        assertTrue(vo.sent());
        assertEquals(60, vo.cooldown());
        assertNotNull(vo.devCode(), "app.sms.echo-code=true 时要回显");
        assertEquals(6, vo.devCode().length());
        assertEquals(1, count("sv_sms_code WHERE phone = ? AND used = 0", PHONE));
        // expire_at = now + code-ttl-seconds(300)
        assertTrue(jdbc.queryForObject("SELECT expire_at FROM sv_sms_code WHERE phone = ?",
                LocalDateTime.class, PHONE).isAfter(LocalDateTime.now().plusSeconds(240)));
    }

    @Test
    void 六十秒内重发返回1003() {
        authService.sendSmsCode(PHONE, IP);

        assertEquals(1003, code(() -> authService.sendSmsCode(PHONE, IP)));
        assertEquals(1, count("sv_sms_code WHERE phone = ?", PHONE));
    }

    @Test
    void 超过间隔可以再发() {
        authService.sendSmsCode(PHONE, IP);
        agePreviousCodes(PHONE, 61);

        assertTrue(authService.sendSmsCode(PHONE, IP).sent());
        assertEquals(2, count("sv_sms_code WHERE phone = ?", PHONE));
    }

    @Test
    void 当日超限返回1004() {
        for (int i = 0; i < 10; i++) {
            authService.sendSmsCode(PHONE, IP);
            agePreviousCodes(PHONE, 61);
        }

        assertEquals(1004, code(() -> authService.sendSmsCode(PHONE, IP)));
        assertEquals(10, count("sv_sms_code WHERE phone = ?", PHONE));
    }

    @Test
    void 验证码错误返回1002() {
        authService.sendSmsCode(PHONE, IP);

        assertEquals(1002, code(() -> authService.smsLogin(PHONE, "000000")));
        assertEquals(1002, code(() -> authService.smsLogin(PHONE, null)));
        assertEquals(0, count("sv_user"));
        assertEquals(0, count("sv_sms_code WHERE phone = ? AND used = 1", PHONE));
    }

    @Test
    void 验证码过期返回1002() {
        String devCode = authService.sendSmsCode(PHONE, IP).devCode();
        jdbc.update("UPDATE sv_sms_code SET expire_at = ? WHERE phone = ?",
                LocalDateTime.now().minusSeconds(1), PHONE);

        assertEquals(1002, code(() -> authService.smsLogin(PHONE, devCode)));
    }

    @Test
    void 验证码一次性() {
        String devCode = authService.sendSmsCode(PHONE, IP).devCode();

        assertTrue(authService.smsLogin(PHONE, devCode).firstLogin());
        assertEquals(1, count("sv_sms_code WHERE phone = ? AND used = 1", PHONE));
        assertEquals(1002, code(() -> authService.smsLogin(PHONE, devCode)));
    }

    @Test
    void 只比对最新一条未使用的验证码() {
        String stale = authService.sendSmsCode(PHONE, IP).devCode();
        agePreviousCodes(PHONE, 61);
        String latest = authService.sendSmsCode(PHONE, IP).devCode();

        if (!stale.equals(latest)) {
            assertEquals(1002, code(() -> authService.smsLogin(PHONE, stale)));
        }
        assertTrue(authService.smsLogin(PHONE, latest).firstLogin());
    }

    /** 把已有验证码的 created_at 往前挪，用来跨过 60 秒重发间隔 */
    private void agePreviousCodes(String phone, int seconds) {
        jdbc.update("UPDATE sv_sms_code SET created_at = ? WHERE phone = ?",
                LocalDateTime.now().minusSeconds(seconds), phone);
    }

    private int code(Runnable action) {
        return assertThrows(BizException.class, action::run).getCode();
    }
}
