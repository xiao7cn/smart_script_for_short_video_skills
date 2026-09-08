package com.shanchuang.common.result;

import java.util.List;

/** 分页载荷，字段名与前端约定一致 */
public record PageData<T>(List<T> records, long total, long page, long size) {

    public static <T> PageData<T> of(List<T> records, long total, long page, long size) {
        return new PageData<>(records, total, page, size);
    }

    public static <T> PageData<T> empty(long page, long size) {
        return new PageData<>(List.of(), 0, page, size);
    }
}
