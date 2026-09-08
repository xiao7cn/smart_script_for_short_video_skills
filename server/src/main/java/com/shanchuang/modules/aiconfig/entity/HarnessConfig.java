package com.shanchuang.modules.aiconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** harness 端点配置。is_default=1 的那一行决定当前用哪个 harness */
@Data
@TableName("sv_harness_config")
public class HarnessConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String endpoint;
    private Integer timeoutMs;
    private Integer enabled;
    private Integer isDefault;
    private String version;
    private LocalDateTime lastHealthAt;
    private Integer lastHealthOk;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
