package com.shanchuang.modules.aiconfig.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 按场景的模型配置。api_key_env 只存环境变量名，密钥本身不落库。
 *
 * 可选字段一律标 updateStrategy = ALWAYS：管理端的 PUT 是全量覆盖语义，
 * 而 MyBatis-Plus 的 updateById 默认跳过 null，导致「把 baseUrl 清空、
 * 从中转站换回官方端点」这个操作根本做不到——配置显示 deepseek，
 * 实际仍打到旧的中转站地址，属于会静默走错端点的那类故障。
 */
@Data
@TableName("sv_ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String provider;
    private String modelId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal temperature;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer maxTokens;

    private String thinking;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String baseUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String apiKeyEnv;
    private Integer maxSteps;
    private Integer timeoutMs;
    private Integer enabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
