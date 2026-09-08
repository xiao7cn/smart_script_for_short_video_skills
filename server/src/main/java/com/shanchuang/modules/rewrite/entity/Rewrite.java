package com.shanchuang.modules.rewrite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_rewrite")
public class Rewrite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long taskId;
    private Long teardownId;
    /** 定稿入库后的文案 id */
    private Long scriptId;
    private String originalTitle;
    private String originalBody;
    private String myTitle;
    /** 十一段，键 s1..s11。段落是整体交付物，拆列会让合同加一段变成改表 */
    private String sectionsJson;
    private String finalBody;
    private Integer words;
    private String scoreJson;
    private Integer readaloudPass;
    private Integer overlapMax;
    private String checkReport;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
