package com.shanchuang.modules.auth.dto;

/** total 是文案总数，generated 是本机生成（非内置样稿）的条数 */
public record StatsVO(long total, long generated) {
}
