package com.shanchuang.modules.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 额度流水。amount 正数入账、负数出账，balance_after 用于对账 */
@Data
@TableName("sv_credit_txn")
public class CreditTxn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;
    private Integer amount;
    private Integer balanceAfter;
    private String refType;
    private String refId;
    private String remark;
    private LocalDateTime createdAt;
}
