package com.shanchuang.modules.auth;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.modules.auth.dto.LoginVO;
import com.shanchuang.modules.auth.dto.MeVO;
import com.shanchuang.modules.auth.service.AuthService;
import com.shanchuang.modules.testsupport.ModuleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthLoginTest extends ModuleTestBase {

    private static final String PHONE = "13800002222";
    private static final String IP = "127.0.0.1";
    private static final String WX_CODE = "wx_demo_ab12cd";

    @Autowired
    private AuthService authService;

    @Test
    void 首登即注册并完成全部副作用() {
        LoginVO vo = smsLogin(PHONE);

        assertTrue(vo.firstLogin());
        assertNotNull(vo.token());
        assertEquals(7200, vo.expiresIn());
        assertEquals(20, vo.credits());
        assertEquals("138****2222", vo.user().nickname());
        assertEquals(PHONE, vo.user().account());
        assertEquals("phone", vo.user().via());

        Long userId = vo.user().userId();
        assertEquals(1, count("sv_user"));
        assertEquals(1, intOf("SELECT free_granted FROM sv_user WHERE id = ?", userId));
        assertNotNull(jdbc.queryForObject("SELECT last_login_at FROM sv_user WHERE id = ?",
                LocalDateTime.class, userId));
        assertEquals(1, count("sv_persona WHERE user_id = ?", userId));
        assertEquals("靠谱顾问", jdbc.queryForObject("SELECT model FROM sv_persona WHERE user_id = ?",
                String.class, userId));
        assertEquals(20, intOf("SELECT balance FROM sv_credit_account WHERE user_id = ?", userId));
        assertEquals(20, intOf("SELECT total_granted FROM sv_credit_account WHERE user_id = ?", userId));
        assertEquals(1, count("sv_credit_txn WHERE user_id = ? AND type = 'GRANT' AND amount = 20", userId));
    }

    @Test
    void 二次登录不再注册也不再赠额() {
        Long userId = smsLogin(PHONE).user().userId();

        LoginVO again = smsLogin(PHONE);

        assertFalse(again.firstLogin());
        assertEquals(userId, again.user().userId());
        assertEquals(20, again.credits());
        assertEquals(1, count("sv_user"));
        assertEquals(1, count("sv_persona"));
        assertEquals(1, count("sv_credit_txn WHERE user_id = ? AND type = 'GRANT'", userId));
    }

    @Test
    void 停用账号返回1006() {
        Long userId = smsLogin(PHONE).user().userId();
        jdbc.update("UPDATE sv_user SET status = 0 WHERE id = ?", userId);

        BizException e = assertThrows(BizException.class, () -> smsLogin(PHONE));
        assertEquals(1006, e.getCode());
    }

    @Test
    void 微信首登即注册() {
        LoginVO vo = authService.wechatLogin(WX_CODE, null);

        assertTrue(vo.firstLogin());
        assertEquals("wechat", vo.user().via());
        assertEquals(WX_CODE, vo.user().account());
        assertEquals("微信用户", vo.user().nickname());
        assertEquals(20, vo.credits());
        assertNull(jdbc.queryForObject("SELECT phone FROM sv_user WHERE id = ?",
                String.class, vo.user().userId()));
    }

    @Test
    void 微信授权失败返回1005() {
        BizException e = assertThrows(BizException.class, () -> authService.wechatLogin("  ", null));
        assertEquals(1005, e.getCode());
        assertEquals(0, count("sv_user"));
    }

    @Test
    void 先短信后微信落到同一个用户() {
        Long userId = smsLogin(PHONE).user().userId();

        LoginVO wx = authService.wechatLogin(WX_CODE, PHONE);

        assertFalse(wx.firstLogin(), "认领已有账号不算首登");
        assertEquals(userId, wx.user().userId());
        assertEquals(1, count("sv_user"));
        assertEquals(WX_CODE, jdbc.queryForObject("SELECT open_id FROM sv_user WHERE id = ?", String.class, userId));
        // via 记的是注册渠道，归并不改写
        assertEquals("phone", wx.user().via());
        assertEquals(1, count("sv_credit_account"));
        assertEquals(1, count("sv_credit_txn WHERE type = 'GRANT'"));
    }

    @Test
    void 先微信后短信落到同一个用户() {
        Long userId = authService.wechatLogin(WX_CODE, PHONE).user().userId();
        assertEquals(PHONE, jdbc.queryForObject("SELECT phone FROM sv_user WHERE id = ?", String.class, userId));

        LoginVO sms = smsLogin(PHONE);

        assertFalse(sms.firstLogin());
        assertEquals(userId, sms.user().userId());
        assertEquals(1, count("sv_user"));
        assertEquals(1, count("sv_persona"));
        assertEquals("wechat", sms.user().via());
        assertEquals(WX_CODE, sms.user().account());
    }

    @Test
    void 微信登录后补授权手机号会回填() {
        Long userId = authService.wechatLogin(WX_CODE, null).user().userId();
        assertNull(jdbc.queryForObject("SELECT phone FROM sv_user WHERE id = ?", String.class, userId));

        LoginVO again = authService.wechatLogin(WX_CODE, PHONE);

        assertEquals(userId, again.user().userId());
        assertEquals(PHONE, jdbc.queryForObject("SELECT phone FROM sv_user WHERE id = ?", String.class, userId));
    }

    @Test
    void 当前用户带额度与文案统计() {
        Long userId = smsLogin(PHONE).user().userId();
        jdbc.update("INSERT INTO sv_script (user_id, seq_no, title, `generated`) VALUES (?, 1, 'a', 1)", userId);
        jdbc.update("INSERT INTO sv_script (user_id, seq_no, title, `generated`) VALUES (?, 2, 'b', 0)", userId);
        jdbc.update("INSERT INTO sv_script (user_id, seq_no, title, `generated`, deleted) VALUES (?, 3, 'c', 1, 1)", userId);
        jdbc.update("INSERT INTO sv_script (user_id, seq_no, title, `generated`) VALUES (?, 1, 'other', 1)", 999999L);

        MeVO me = authService.me(userId);

        assertEquals(userId, me.user().userId());
        assertEquals(20, me.credits());
        assertEquals(2, me.stats().total(), "软删的不算");
        assertEquals(1, me.stats().generated());
    }

    @Test
    void 登出无副作用() {
        Long userId = smsLogin(PHONE).user().userId();

        authService.logout(userId);

        assertEquals(1, count("sv_user WHERE id = ?", userId));
    }

    /** 每次登录都重新下发验证码，绕开 60 秒重发间隔 */
    private LoginVO smsLogin(String phone) {
        jdbc.update("DELETE FROM sv_sms_code WHERE phone = ?", phone);
        String devCode = authService.sendSmsCode(phone, IP).devCode();
        return authService.smsLogin(phone, devCode);
    }
}
