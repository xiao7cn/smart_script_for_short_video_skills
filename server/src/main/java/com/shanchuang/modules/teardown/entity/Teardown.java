package com.shanchuang.modules.teardown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_teardown")
public class Teardown {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long taskId;
    private String platform;
    private String sourceUrl;
    private String sourceType;
    private String videoTitle;
    private Integer durationSec;
    private Integer words;
    private Integer speechRate;
    /** 口播稿，一句一行，不含时间戳 */
    private String transcript;
    private String rhythmText;
    private String segmentsJson;
    private String asrBackend;
    private String teardownJson;
    private String frameworkJson;
    private String status;
    /** 取件失败时给用户的录屏指引 */
    private String fetchGuide;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
