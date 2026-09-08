package com.shanchuang.modules.options.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 向导选项。原型里硬编码在 data/config.ts 的候选值，产品化后交给运营配 */
@Data
@TableName("sv_option_item")
public class OptionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    private String itemKey;
    private String itemName;
    private String tag;
    private String hint;
    private String description;
    private String formula;
    private String goal;
    private Integer ratio;
    private Integer ready;
    private String note;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
