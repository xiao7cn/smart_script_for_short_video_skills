package com.shanchuang.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_sms_code")
public class SmsCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String code;
    private LocalDateTime expireAt;
    private Integer used;
    private String sendIp;
    private LocalDateTime createdAt;
}
