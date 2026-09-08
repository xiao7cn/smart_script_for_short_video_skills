package com.shanchuang.modules.credit.service;

import com.shanchuang.common.result.PageData;
import com.shanchuang.modules.credit.dto.CreditAccountVO;
import com.shanchuang.modules.credit.dto.CreditPackVO;
import com.shanchuang.modules.credit.dto.CreditTxnVO;
import com.shanchuang.modules.credit.dto.RechargeVO;

import java.util.List;

public interface CreditService {

    /** 账户概览，没有账户时自动建 */
    CreditAccountVO overview(Long userId);

    /** 新用户赠额，只赠一次（幂等） */
    void grantFreeIfAbsent(Long userId);

    /**
     * 预扣。requested 超过余额时按余额受理，返回实际受理条数（可能为 0）。
     * 必须用条件更新保证不超扣：
     *   UPDATE sv_credit_account SET balance=balance-n, hold=hold+n, version=version+1
     *   WHERE user_id=? AND balance>=n
     * 受影响行数为 0 时重试一次，仍失败返回 0。受理成功要写一条 CONSUME 流水（amount 为负）。
     */
    int hold(Long userId, int requested, String refId);

    /** 某条成功：把 1 条从 hold 里核销（balance 不动，预扣时已扣） */
    void consumeOne(Long userId, String refId);

    /** 某条失败：退 1 条回 balance 并减 hold，写 REFUND 流水（amount 为正） */
    void refundOne(Long userId, String refId);

    /** 任务终态兜底：把该任务残留的 hold 全额退回并写 REFUND 流水 */
    void settle(Long userId, String refId, int remainingHold);

    /* ---- 以下供 /api/credits 系列接口使用 ---- */

    List<CreditPackVO> packs();

    RechargeVO recharge(Long userId, String packId);

    PageData<CreditTxnVO> transactions(Long userId, int page, int size);
}
