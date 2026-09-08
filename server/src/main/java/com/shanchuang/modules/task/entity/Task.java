package com.shanchuang.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外任务号，不暴露自增 id */
    private String taskNo;
    /** 幂等键，同 user + requestId 只建一次 */
    private String requestId;
    private Long userId;
    private String type;
    private String status;
    private Integer total;
    private Integer doneCount;
    private Integer failCount;
    /**
     * 向导参数 + 人设 + 手改提示词的快照。
     * 选项与人设随时会改，任务必须记下当时用的参数，否则事后无法复现同一批结果。
     */
    private String paramJson;
    private Integer creditsHold;
    private Integer creditsSettled;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
