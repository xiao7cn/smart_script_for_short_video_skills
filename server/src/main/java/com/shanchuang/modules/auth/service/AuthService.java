package com.shanchuang.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.security.JwtUtil;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.common.util.TextUtil;
import com.shanchuang.modules.auth.dto.LoginVO;
import com.shanchuang.modules.auth.dto.MeVO;
import com.shanchuang.modules.auth.dto.SmsCodeVO;
import com.shanchuang.modules.auth.dto.StatsVO;
import com.shanchuang.modules.auth.dto.UserVO;
import com.shanchuang.modules.auth.dto.WechatIdentity;
import com.shanchuang.modules.auth.entity.SmsCode;
import com.shanchuang.modules.auth.entity.User;
import com.shanchuang.modules.auth.mapper.SmsCodeMapper;
import com.shanchuang.modules.auth.mapper.UserMapper;
import com.shanchuang.modules.auth.mapper.UserStatsMapper;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.persona.service.PersonaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 账号，对应 docs/接口设计.md 第 2 章 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String PHONE_PATTERN = "^1\\d{10}$";
    private static final String VIA_PHONE = "phone";
    private static final String VIA_WECHAT = "wechat";

    private final UserMapper userMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final UserStatsMapper userStatsMapper;
    private final PersonaService personaService;
    private final CreditService creditService;
    private final WechatCodeExchanger wechatCodeExchanger;
    private final JwtUtil jwtUtil;

    private final boolean echoCode;
    private final int codeTtlSeconds;
    private final int resendIntervalSeconds;
    private final int dailyLimit;

    public AuthService(UserMapper userMapper, SmsCodeMapper smsCodeMapper, UserStatsMapper userStatsMapper,
                       PersonaService personaService, CreditService creditService,
                       WechatCodeExchanger wechatCodeExchanger, JwtUtil jwtUtil,
                       @Value("${app.sms.echo-code:true}") boolean echoCode,
                       @Value("${app.sms.code-ttl-seconds:300}") int codeTtlSeconds,
                       @Value("${app.sms.resend-interval-seconds:60}") int resendIntervalSeconds,
                       @Value("${app.sms.daily-limit:10}") int dailyLimit) {
        this.userMapper = userMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.userStatsMapper = userStatsMapper;
        this.personaService = personaService;
        this.creditService = creditService;
        this.wechatCodeExchanger = wechatCodeExchanger;
        this.jwtUtil = jwtUtil;
        this.echoCode = echoCode;
        this.codeTtlSeconds = codeTtlSeconds;
        this.resendIntervalSeconds = resendIntervalSeconds;
        this.dailyLimit = dailyLimit;
    }

    public SmsCodeVO sendSmsCode(String phone, String sendIp) {
        String p = requirePhone(phone);
        LocalDateTime now = LocalDateTime.now();

        Long recent = smsCodeMapper.selectCount(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, p)
                .ge(SmsCode::getCreatedAt, now.minusSeconds(resendIntervalSeconds)));
        if (recent != null && recent > 0) {
            throw BizException.of(1003, "验证码发送过于频繁，请 " + resendIntervalSeconds + " 秒后重试");
        }
        Long today = smsCodeMapper.selectCount(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, p)
                .ge(SmsCode::getCreatedAt, now.toLocalDate().atStartOfDay()));
        if (today != null && today >= dailyLimit) {
            throw BizException.of(1004, "当日验证码发送次数超限");
        }

        SmsCode row = new SmsCode();
        row.setPhone(p);
        row.setCode(IdUtil.smsCode());
        row.setExpireAt(now.plusSeconds(codeTtlSeconds));
        row.setUsed(0);
        row.setSendIp(sendIp);
        row.setCreatedAt(now);
        smsCodeMapper.insert(row);
        log.info("下发验证码 phone={} ttl={}s", TextUtil.maskPhone(p), codeTtlSeconds);

        return new SmsCodeVO(true, resendIntervalSeconds, echoCode ? row.getCode() : null);
    }

    @Transactional
    public LoginVO smsLogin(String phone, String code) {
        String p = requirePhone(phone);
        verifyCode(p, code);

        User user = selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, p));
        boolean firstLogin = false;
        if (user == null) {
            // 首登即注册；该手机号已有 open_id 的行会在上面被查到，天然复用同一行
            user = register(p, null, null, TextUtil.maskPhone(p), null, VIA_PHONE);
            firstLogin = true;
        }
        return afterLogin(user, firstLogin);
    }

    @Transactional
    public LoginVO wechatLogin(String code, String phoneCode) {
        WechatIdentity identity = wechatCodeExchanger.exchange(code, phoneCode);
        if (identity == null || TextUtil.isBlank(identity.openId())) {
            throw BizException.of(1005, "微信授权失败");
        }
        String openId = identity.openId();
        String phone = identity.phone() != null && identity.phone().matches(PHONE_PATTERN)
                ? identity.phone() : null;

        User user = selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenId, openId));
        boolean firstLogin = false;
        if (user == null && phone != null) {
            // 归并：拿到手机号且该号已注册过，认领这一行并回填 open_id，而不是再建一个用户
            User byPhone = selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
            if (byPhone != null) {
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .set(User::getOpenId, openId)
                        .set(identity.unionId() != null, User::getUnionId, identity.unionId())
                        .eq(User::getId, byPhone.getId())
                        .isNull(User::getOpenId));
                user = userMapper.selectById(byPhone.getId());
            }
        }
        if (user == null) {
            String nickname = TextUtil.isBlank(identity.nickname()) ? "微信用户" : identity.nickname();
            user = register(phone, openId, identity.unionId(), nickname, identity.avatar(), VIA_WECHAT);
            firstLogin = true;
        } else if (phone != null && TextUtil.isBlank(user.getPhone())) {
            // 反向归并：先用微信注册、之后才授权手机号
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getPhone, phone)
                    .eq(User::getId, user.getId())
                    .isNull(User::getPhone));
            user = userMapper.selectById(user.getId());
        }
        return afterLogin(user, firstLogin);
    }

    public MeVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(401, "未登录或令牌过期");
        }
        StatsVO stats = new StatsVO(userStatsMapper.countScripts(userId), userStatsMapper.countGenerated(userId));
        return new MeVO(toVO(user), creditService.overview(userId).balance(), stats);
    }

    /** JWT 无状态，服务端没有会话可清；这里只留一条审计日志 */
    public void logout(Long userId) {
        log.info("用户登出 userId={}", userId);
    }

    private void verifyCode(String phone, String code) {
        if (TextUtil.isBlank(code)) {
            throw BizException.of(1002, "验证码错误或已过期");
        }
        SmsCode row = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getUsed, 0)
                .gt(SmsCode::getExpireAt, LocalDateTime.now())
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));
        if (row == null || !row.getCode().equals(code.trim())) {
            throw BizException.of(1002, "验证码错误或已过期");
        }
        // 一次性：先置 used 再往下走，避免同一条码被并发复用
        row.setUsed(1);
        smsCodeMapper.updateById(row);
    }

    private User register(String phone, String openId, String unionId, String nickname, String avatar, String via) {
        User u = new User();
        u.setPhone(phone);
        u.setOpenId(openId);
        u.setUnionId(unionId);
        u.setNickname(nickname);
        u.setAvatar(avatar);
        u.setVia(via);
        u.setStatus(1);
        u.setFreeGranted(0);
        userMapper.insert(u);

        personaService.initDefault(u.getId());
        creditService.grantFreeIfAbsent(u.getId());
        // free_granted 只是给运营看的标记，真正的幂等闸门在 sv_credit_account.total_granted
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getFreeGranted, 1)
                .eq(User::getId, u.getId())
                .eq(User::getFreeGranted, 0));
        u.setFreeGranted(1);
        log.info("首登即注册 userId={} via={}", u.getId(), via);
        return u;
    }

    private LoginVO afterLogin(User user, boolean firstLogin) {
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.of(1006, "账号已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getLastLoginAt, now)
                .eq(User::getId, user.getId()));
        user.setLastLoginAt(now);
        int credits = creditService.overview(user.getId()).balance();
        return new LoginVO(jwtUtil.issue(user.getId()), jwtUtil.getExpireSeconds(), firstLogin, toVO(user), credits);
    }

    private UserVO toVO(User user) {
        String account = VIA_WECHAT.equals(user.getVia())
                ? (user.getOpenId() != null ? user.getOpenId() : user.getPhone())
                : (user.getPhone() != null ? user.getPhone() : user.getOpenId());
        return new UserVO(user.getId(), user.getNickname(), account, user.getVia(), user.getAvatar());
    }

    private User selectOne(LambdaQueryWrapper<User> wrapper) {
        return userMapper.selectOne(wrapper.orderByAsc(User::getId).last("LIMIT 1"));
    }

    private String requirePhone(String phone) {
        String p = phone == null ? null : phone.trim();
        if (p == null || !p.matches(PHONE_PATTERN)) {
            throw BizException.of(1001, "手机号格式不正确");
        }
        return p;
    }
}
