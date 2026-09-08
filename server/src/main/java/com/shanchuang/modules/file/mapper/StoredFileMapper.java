package com.shanchuang.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanchuang.modules.file.entity.StoredFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFile> {
}
