package com.shanchuang.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanchuang.modules.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /** 进度用原子自增，避免并发的条目互相覆盖计数 */
    @Update("UPDATE sv_task SET done_count = done_count + 1 WHERE id = #{taskId}")
    int incrementDone(Long taskId);

    @Update("UPDATE sv_task SET fail_count = fail_count + 1 WHERE id = #{taskId}")
    int incrementFail(Long taskId);
}
