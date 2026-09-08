package com.shanchuang.modules.script.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanchuang.modules.script.entity.Script;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScriptMapper extends BaseMapper<Script> {

    /**
     * 取该用户当前最大展示编号。
     * 软删除的也要算进来，否则删一条再生成会出现重复的 NO.xx。
     */
    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM sv_script WHERE user_id = #{userId}")
    int maxSeqNo(Long userId);

    /** generated 是 MySQL 保留字，手写 SQL 里必须加反引号 */
    @Select("SELECT COUNT(*) FROM sv_script WHERE user_id = #{userId} AND `generated` = 1 AND deleted = 0")
    long countGenerated(Long userId);

    @Select("SELECT COUNT(*) FROM sv_script WHERE user_id = #{userId} AND deleted = 0")
    long countAll(Long userId);
}
