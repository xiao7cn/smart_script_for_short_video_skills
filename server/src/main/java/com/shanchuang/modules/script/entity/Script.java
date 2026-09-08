package com.shanchuang.modules.script.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_script")
public class Script {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer seqNo;
    private Long taskId;
    private String title;
    private String topic;
    private String scriptType;
    private String topicType;
    private String source;
    private String grid;
    private String element;
    private String structure;
    private Integer words;
    private String body;
    /** 去 AI 味之前的初稿，留着用于比对与排查 */
    private String draftBody;
    private String needsMaterial;
    /** generated 是 MySQL 保留字，MyBatis-Plus 生成的 SQL 必须显式加反引号 */
    @TableField("`generated`")
    private Integer generated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
