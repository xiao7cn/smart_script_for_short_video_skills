package com.shanchuang.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户。phone 与 open_id 都是可空唯一键，跨端登录靠它们归并到同一行 */
@Data
@TableName("sv_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String openId;
    private String unionId;
    private String nickname;
    private String avatar;
    private String via;
    private Integer status;
    private Integer freeGranted;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
