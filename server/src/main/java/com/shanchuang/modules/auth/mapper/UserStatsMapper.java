package com.shanchuang.modules.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * /api/auth/me 的 stats 只要两个计数。
 * 这里直接写 count 而不是复用文案模块的 Mapper：账号模块不该依赖生成模块的实体，
 * 逻辑删除条件手写在 SQL 里。
 */
public interface UserStatsMapper {

    @Select("SELECT COUNT(*) FROM sv_script WHERE user_id = #{userId} AND deleted = 0")
    long countScripts(@Param("userId") Long userId);

    /** generated 是 MySQL 保留字，不加反引号会报语法错误 */
    @Select("SELECT COUNT(*) FROM sv_script WHERE user_id = #{userId} AND `generated` = 1 AND deleted = 0")
    long countGenerated(@Param("userId") Long userId);
}
