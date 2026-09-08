package com.shanchuang.modules.script.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 来源为「对标爆款拆解」的文案才有：原文 + 拆解要点 + 二创差异说明 */
@Data
@TableName("sv_script_breakdown")
public class ScriptBreakdown {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;
    private Long teardownId;
    private String refsJson;
    private Integer autoSearch;
    private String original;
    private String pointsJson;
    private String rewriteNote;
    private LocalDateTime createdAt;
}
