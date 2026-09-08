package com.shanchuang.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sv_file")
public class StoredFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileId;
    private Long userId;
    private String fileName;
    private String storePath;
    private Long sizeBytes;
    private String mime;
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
