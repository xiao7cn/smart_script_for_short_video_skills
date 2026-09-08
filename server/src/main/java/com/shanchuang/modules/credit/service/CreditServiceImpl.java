package com.shanchuang.modules.credit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.modules.credit.dto.CreditAccountVO;
import com.shanchuang.modules.credit.dto.CreditPackVO;
import com.shanchuang.modules.credit.dto.CreditTxnVO;
import com.shanchuang.modules.credit.dto.RechargeVO;
import com.shanchuang.modules.credit.entity.CreditAccount;
import com.shanchuang.modules.credit.entity.CreditTxn;
import com.shanchuang.modules.credit.entity.RechargeOrder;
import com.shanchuang.modules.credit.mapper.CreditAccountMapper;
import com.shanchuang.modules.credit.mapper.CreditTxnMapper;
import com.shanchuang.modules.credit.mapper.RechargeOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CreditServiceImpl implements CreditService {

    private static final Logger log = LoggerFactory.getLogger(CreditServiceImpl.class);

    private static final String TYPE_GRANT = "GRANT";
    private static final String TYPE_RECHARGE = "RECHARGE";
    private static final String TYPE_CONSUME = "CONSUME";
    private static final String TYPE_REFUND = "REFUND";
    private static final String REF_TASK = "TASK";
    private static final String REF_ORDER = "ORDER";
    private static final String REF_SYSTEM = "SYSTEM";

    private final CreditAccountMapper accountMapper;
    private final CreditTxnMapper txnMapper;
    private final RechargeOrderMapper orderMapper;
    private final CreditProperties props;

    public CreditServiceImpl(CreditAccountMapper accountMapper, CreditTxnMapper txnMapper,
                             RechargeOrderMapper orderMapper, CreditProperties props) {
        this.accountMapper = accountMapper;
        this.txnMapper = txnMapper;
        this.orderMapper = orderMapper;
        this.props = props;
    }

    @Override
    public CreditAccountVO overview(Long userId) {
        CreditAccount a = ensureAccount(userId);
        return new CreditAccountVO(a.getBalance(), a.getHold(),
                a.getTotalGranted(), a.getTotalRecharged(), a.getTotalConsumed());
    }

    @Override
    @Transactional
    public void grantFreeIfAbsent(Long userId) {
        ensureAccount(userId);
        int n = props.getFreeGrant();
        if (n <= 0) {
            return;
        }
        // 幂等交给 total_granted=0 这个条件，不靠先查再写
        if (accountMapper.grantOnce(userId, n) == 0) {
            return;
        }
        writeTxn(userId, TYPE_GRANT, n, REF_SYSTEM, "FREE_GRANT", "新人赠送 " + n + " 条");
    }

    @Override
    @Transactional
    public int hold(Long userId, int requested, String refId) {
        if (requested <= 0) {
            return 0;
        }
        ensureAccount(userId);
        // 余额不够就按余额受理；条件更新落空说明有并发抢走了额度，重读余额再试一次
        for (int attempt = 0; attempt < 2; attempt++) {
            int balance = currentBalance(userId);
            int accepted = Math.min(requested, balance);
            if (accepted <= 0) {
                return 0;
            }
            if (accountMapper.hold(userId, accepted) > 0) {
                writeTxn(userId, TYPE_CONSUME, -accepted, REF_TASK, refId, "生成预扣 " + accepted + " 条");
                return accepted;
            }
        }
        log.warn("hold 失败，额度被并发抢占 userId={} requested={} refId={}", userId, requested, refId);
        return 0;
    }

    @Override
    @Transactional
    public void consumeOne(Long userId, String refId) {
        // 预扣时 balance 已经扣过，这里只把 hold 转成已消耗，不再写流水
        if (accountMapper.consumeFromHold(userId, 1) == 0) {
            log.warn("核销失败，hold 已不足 userId={} refId={}", userId, refId);
        }
    }

    @Override
    @Transactional
    public void refundOne(Long userId, String refId) {
        if (accountMapper.refundFromHold(userId, 1) == 0) {
            log.warn("退额失败，hold 已不足 userId={} refId={}", userId, refId);
            return;
        }
        writeTxn(userId, TYPE_REFUND, 1, REF_TASK, refId, "生成失败退回 1 条");
    }

    @Override
    @Transactional
    public void settle(Long userId, String refId, int remainingHold) {
        if (remainingHold <= 0) {
            return;
        }
        // 兜底路径可能被重复调用，按账户实际 hold 收口，避免把别的任务的预扣退掉
        CreditAccount acc = ensureAccount(userId);
        int n = Math.min(remainingHold, acc.getHold() == null ? 0 : acc.getHold());
        if (n <= 0) {
            return;
        }
        if (accountMapper.refundFromHold(userId, n) == 0) {
            return;
        }
        writeTxn(userId, TYPE_REFUND, n, REF_TASK, refId, "任务结算退回 " + n + " 条");
    }

    @Override
    public List<CreditPackVO> packs() {
        return props.getPacks().stream()
                .map(p -> new CreditPackVO(p.getPackId(), p.getPriceFen(), p.getBase(), p.getBonus()))
                .toList();
    }

    @Override
    @Transactional
    public RechargeVO recharge(Long userId, String packId) {
        CreditProperties.Pack pack = props.findPack(packId)
                .orElseThrow(() -> BizException.of(6001, "充值套餐不存在"));
        ensureAccount(userId);

        int credited = pack.total();
        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(IdUtil.orderNo());
        order.setUserId(userId);
        order.setPackId(pack.getPackId());
        order.setPriceFen(pack.getPriceFen());
        order.setBaseCredits(pack.getBase());
        order.setBonusCredits(pack.getBonus());
        // 首版 pay_channel=mock，下单即到账；接微信支付后改成 PENDING + 回调驱动
        order.setStatus("PAID");
        order.setPayChannel("mock");
        order.setPaidAt(LocalDateTime.now());
        orderMapper.insert(order);

        accountMapper.recharge(userId, credited);
        int balance = currentBalance(userId);
        writeTxn(userId, TYPE_RECHARGE, credited, REF_ORDER, order.getOrderNo(),
                "充值 " + pack.getPackId() + " 到账 " + credited + " 条");
        return new RechargeVO(order.getOrderNo(), order.getStatus(), credited, balance);
    }

    @Override
    public PageData<CreditTxnVO> transactions(Long userId, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        // 与其余列表接口统一走 selectPage（MybatisPlusConfig 里注册了分页拦截器）。
        // 一个仓库里并存两套分页写法，早晚有人照抄手写 LIMIT 却忘了配 count 查询。
        IPage<CreditTxn> result = txnMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<CreditTxn>()
                        .eq(CreditTxn::getUserId, userId)
                        .orderByDesc(CreditTxn::getId));
        List<CreditTxnVO> records = result.getRecords().stream()
                .map(t -> new CreditTxnVO(t.getId(), t.getType(), t.getAmount(), t.getBalanceAfter(),
                        t.getRefType(), t.getRefId(), t.getRemark(), t.getCreatedAt()))
                .toList();
        return PageData.of(records, result.getTotal(), p, s);
    }

    private CreditAccount ensureAccount(Long userId) {
        if (userId == null) {
            throw BizException.of(401, "未登录或令牌过期");
        }
        CreditAccount acc = selectAccount(userId);
        if (acc != null) {
            return acc;
        }
        acc = new CreditAccount();
        acc.setUserId(userId);
        acc.setBalance(0);
        acc.setHold(0);
        acc.setTotalGranted(0);
        acc.setTotalRecharged(0);
        acc.setTotalConsumed(0);
        acc.setVersion(0);
        try {
            accountMapper.insert(acc);
            return acc;
        } catch (DuplicateKeyException e) {
            // uk_user 撞车说明并发建过了，直接用那一行
            return selectAccount(userId);
        }
    }

    private CreditAccount selectAccount(Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<CreditAccount>()
                .eq(CreditAccount::getUserId, userId));
    }

    private int currentBalance(Long userId) {
        CreditAccount acc = selectAccount(userId);
        return acc == null || acc.getBalance() == null ? 0 : acc.getBalance();
    }

    private void writeTxn(Long userId, String type, int amount, String refType, String refId, String remark) {
        CreditTxn txn = new CreditTxn();
        txn.setUserId(userId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setBalanceAfter(currentBalance(userId));
        txn.setRefType(refType);
        txn.setRefId(refId);
        txn.setRemark(remark);
        txn.setCreatedAt(LocalDateTime.now());
        txnMapper.insert(txn);
    }
}
