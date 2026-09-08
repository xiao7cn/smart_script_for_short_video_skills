package com.shanchuang.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanchuang.modules.task.entity.TaskItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskItemMapper extends BaseMapper<TaskItem> {
}
