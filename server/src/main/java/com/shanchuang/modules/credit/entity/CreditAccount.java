package com.shanchuang.modules.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 额度账户。可用额度只看 balance，hold 是未终态任务的预扣，只用于展示与对账 */
@Data
@TableName("sv_credit_account")
public class CreditAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer balance;
    private Integer hold;
    private Integer totalGranted;
    private Integer totalRecharged;
    private Integer totalConsumed;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
