package com.shanchuang.modules.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人设档案，一个用户一份。
 * value_prop 对外叫 value，needs_json / banned_json 是 JSON 列，实体里按原始串存，转换放 Service。
 */
@Data
@TableName("sv_persona")
public class Persona {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String model;
    private String modelDesc;
    private String identity;
    private String valueProp;
    private String tone;
    private String audience;
    private String needsJson;
    private String bannedJson;
    private Integer minWords;
    private String ctaStyle;
    private String ctaAsset;
    private String platform;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
