package com.shanchuang.modules.aiconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 调用留痕。
 * 刻意不存 prompt 与响应正文：一是隐私，二是体积。
 * 需要排查具体内容时用 requestId 关联 Agent 侧日志。
 */
@Data
@TableName("sv_llm_call_log")
public class LlmCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private Long userId;
    private Long taskId;
    private String scene;
    private String harnessName;
    private String harnessVersion;
    private String provider;
    private String modelId;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal costUsd;
    private Integer steps;
    private Integer latencyMs;
    private Integer ok;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime createdAt;
}
