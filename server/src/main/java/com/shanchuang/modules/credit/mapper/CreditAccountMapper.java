package com.shanchuang.modules.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanchuang.modules.credit.entity.CreditAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 账户变动一律走带条件的原子更新，不用「先查再写」：
 * 生成链路是多线程并发扣额度的，读到的余额在写回时早就不是最新值了。
 * 条件不满足时受影响行数为 0，由 Service 决定是重试还是拒绝。
 */
public interface CreditAccountMapper extends BaseMapper<CreditAccount> {

    @Update("UPDATE sv_credit_account SET balance = balance - #{n}, hold = hold + #{n}, version = version + 1 "
            + "WHERE user_id = #{userId} AND balance >= #{n}")
    int hold(@Param("userId") Long userId, @Param("n") int n);

    @Update("UPDATE sv_credit_account SET hold = hold - #{n}, total_consumed = total_consumed + #{n}, version = version + 1 "
            + "WHERE user_id = #{userId} AND hold >= #{n}")
    int consumeFromHold(@Param("userId") Long userId, @Param("n") int n);

    @Update("UPDATE sv_credit_account SET balance = balance + #{n}, hold = hold - #{n}, version = version + 1 "
            + "WHERE user_id = #{userId} AND hold >= #{n}")
    int refundFromHold(@Param("userId") Long userId, @Param("n") int n);

    /** total_granted = 0 是赠额的幂等闸门，重复调用只有第一次能命中 */
    @Update("UPDATE sv_credit_account SET balance = balance + #{n}, total_granted = total_granted + #{n}, version = version + 1 "
            + "WHERE user_id = #{userId} AND total_granted = 0")
    int grantOnce(@Param("userId") Long userId, @Param("n") int n);

    @Update("UPDATE sv_credit_account SET balance = balance + #{n}, total_recharged = total_recharged + #{n}, version = version + 1 "
            + "WHERE user_id = #{userId}")
    int recharge(@Param("userId") Long userId, @Param("n") int n);
}
