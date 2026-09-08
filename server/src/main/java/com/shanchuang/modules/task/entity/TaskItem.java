package com.shanchuang.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_task_item")
public class TaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    /** idx 是 MySQL 关键字，反引号包起来 */
    @TableField("`idx`")
    private Integer idx;
    private String status;
    private String paramJson;
    private Long scriptId;
    private Integer retryCount;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
