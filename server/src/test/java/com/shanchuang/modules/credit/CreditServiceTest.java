package com.shanchuang.modules.credit;

import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.modules.credit.dto.CreditAccountVO;
import com.shanchuang.modules.credit.dto.CreditPackVO;
import com.shanchuang.modules.credit.dto.CreditTxnVO;
import com.shanchuang.modules.credit.dto.RechargeVO;
import com.shanchuang.modules.credit.service.CreditService;
import com.shanchuang.modules.testsupport.ModuleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditServiceTest extends ModuleTestBase {

    private static final long UID = 9001L;
    private static final String REF = "task-abc";

    @Autowired
    private CreditService creditService;

    @Test
    void 概览没有账户时自动建() {
        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(0, vo.balance());
        assertEquals(0, vo.hold());
        assertEquals(1, count("sv_credit_account WHERE user_id = ?", UID));
    }

    @Test
    void 新人赠额只赠一次() {
        creditService.grantFreeIfAbsent(UID);
        creditService.grantFreeIfAbsent(UID);
        creditService.grantFreeIfAbsent(UID);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(20, vo.balance());
        assertEquals(20, vo.totalGranted());
        assertEquals(1, count("sv_credit_txn WHERE user_id = ? AND type = 'GRANT'", UID));
    }

    @Test
    void 预扣超过余额时按余额受理() {
        giveBalance(3);

        assertEquals(3, creditService.hold(UID, 5, REF));

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(0, vo.balance());
        assertEquals(3, vo.hold());
        assertEquals(-3, intOf("SELECT amount FROM sv_credit_txn WHERE user_id = ? AND type = 'CONSUME'", UID));
        assertEquals(0, intOf("SELECT balance_after FROM sv_credit_txn WHERE user_id = ? AND type = 'CONSUME'", UID));
    }

    @Test
    void 余额为零时预扣返回零且不写流水() {
        creditService.overview(UID);

        assertEquals(0, creditService.hold(UID, 5, REF));
        assertEquals(0, count("sv_credit_txn WHERE user_id = ?", UID));
        assertEquals(0, creditService.overview(UID).hold());
    }

    @Test
    void 预扣条数不合法时返回零() {
        giveBalance(10);
        assertEquals(0, creditService.hold(UID, 0, REF));
        assertEquals(0, creditService.hold(UID, -3, REF));
        assertEquals(10, creditService.overview(UID).balance());
    }

    @Test
    void 并发预扣不超扣() throws Exception {
        int initial = 10;
        int threads = 8;
        giveBalance(initial);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    accepted.addAndGet(creditService.hold(UID, 3, REF));
                } catch (Exception ignored) {
                    // 抢不到额度或被数据库锁拒绝都算受理 0 条，账目仍必须平
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        CreditAccountVO vo = creditService.overview(UID);
        assertTrue(accepted.get() > 0, "至少要有线程拿到额度");
        assertTrue(accepted.get() <= initial, "受理总数不能超过初始余额：" + accepted.get());
        assertEquals(initial - accepted.get(), vo.balance());
        assertEquals(accepted.get(), vo.hold());
        assertTrue(vo.balance() >= 0);
    }

    @Test
    void 核销只动hold不动余额() {
        giveBalance(5);
        creditService.hold(UID, 3, REF);

        creditService.consumeOne(UID, REF);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(2, vo.balance());
        assertEquals(2, vo.hold());
        assertEquals(1, vo.totalConsumed());
        // 预扣时已写过 CONSUME，核销不再重复记账
        assertEquals(1, count("sv_credit_txn WHERE user_id = ?", UID));
    }

    @Test
    void 单条失败退回一条并写正数流水() {
        giveBalance(5);
        creditService.hold(UID, 3, REF);

        creditService.refundOne(UID, REF);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(3, vo.balance());
        assertEquals(2, vo.hold());
        assertEquals(1, intOf("SELECT amount FROM sv_credit_txn WHERE user_id = ? AND type = 'REFUND'", UID));
        assertEquals(3, intOf("SELECT balance_after FROM sv_credit_txn WHERE user_id = ? AND type = 'REFUND'", UID));
    }

    @Test
    void hold不足时退额不会退成负数() {
        creditService.overview(UID);

        creditService.refundOne(UID, REF);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(0, vo.balance());
        assertEquals(0, vo.hold());
        assertEquals(0, count("sv_credit_txn WHERE user_id = ?", UID));
    }

    @Test
    void 结算把残留预扣全额退回() {
        giveBalance(5);
        creditService.hold(UID, 5, REF);
        creditService.consumeOne(UID, REF);
        creditService.consumeOne(UID, REF);

        creditService.settle(UID, REF, 3);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(3, vo.balance());
        assertEquals(0, vo.hold());
        assertEquals(2, vo.totalConsumed());
        assertEquals(3, intOf("SELECT amount FROM sv_credit_txn WHERE user_id = ? AND type = 'REFUND'", UID));
    }

    @Test
    void 结算按账户实际hold收口且可重复调用() {
        giveBalance(5);
        creditService.hold(UID, 2, REF);

        // 上层重复兜底、或传了偏大的残留数，都不能把别人的预扣退掉
        creditService.settle(UID, REF, 99);
        creditService.settle(UID, REF, 99);

        CreditAccountVO vo = creditService.overview(UID);
        assertEquals(5, vo.balance());
        assertEquals(0, vo.hold());
        assertEquals(1, count("sv_credit_txn WHERE user_id = ? AND type = 'REFUND'", UID));
    }

    @Test
    void 套餐来自配置() {
        List<CreditPackVO> packs = creditService.packs();
        assertEquals(4, packs.size());
        CreditPackVO p59 = packs.stream().filter(p -> "p59".equals(p.packId())).findFirst().orElseThrow();
        assertEquals(5900, p59.priceFen());
        assertEquals(100, p59.base());
        assertEquals(15, p59.bonus());
    }

    @Test
    void 充值下单即到账() {
        RechargeVO vo = creditService.recharge(UID, "p59");

        assertNotNull(vo.orderNo());
        assertEquals("PAID", vo.status());
        assertEquals(115, vo.credited());
        assertEquals(115, vo.balance());
        assertEquals(1, count("sv_recharge_order WHERE user_id = ? AND status = 'PAID' AND pay_channel = 'mock'", UID));
        assertEquals(115, intOf("SELECT amount FROM sv_credit_txn WHERE user_id = ? AND type = 'RECHARGE'", UID));
        assertEquals(115, creditService.overview(UID).totalRecharged());
    }

    @Test
    void 充值套餐不存在返回6001() {
        BizException e = assertThrows(BizException.class, () -> creditService.recharge(UID, "p999"));
        assertEquals(6001, e.getCode());
        assertEquals(0, count("sv_recharge_order WHERE user_id = ?", UID));
    }

    @Test
    void 流水分页倒序() {
        creditService.grantFreeIfAbsent(UID);
        creditService.recharge(UID, "p19");
        creditService.hold(UID, 1, REF);

        PageData<CreditTxnVO> first = creditService.transactions(UID, 1, 2);
        assertEquals(3, first.total());
        assertEquals(2, first.records().size());
        assertEquals("CONSUME", first.records().get(0).type());
        assertEquals("RECHARGE", first.records().get(1).type());

        PageData<CreditTxnVO> second = creditService.transactions(UID, 2, 2);
        assertEquals(1, second.records().size());
        assertEquals("GRANT", second.records().get(0).type());

        PageData<CreditTxnVO> overflow = creditService.transactions(UID, Integer.MAX_VALUE, 100);
        assertEquals(3, overflow.total());
        assertTrue(overflow.records().isEmpty());
    }

    /** 直接把余额写进库，绕开赠额与充值，测例才好摆出想要的初始状态 */
    private void giveBalance(int balance) {
        creditService.overview(UID);
        jdbc.update("UPDATE sv_credit_account SET balance = ? WHERE user_id = ?", balance, UID);
    }
}
