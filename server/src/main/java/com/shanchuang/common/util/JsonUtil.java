package com.shanchuang.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanchuang.common.exception.BizException;

import java.util.List;
import java.util.Map;

/** JSON 字段（param_json / needs_json 等）与对象之间的转换 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw BizException.of(500, "序列化失败: " + e.getMessage());
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (TextUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw BizException.of(500, "反序列化失败: " + e.getMessage());
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (TextUtil.isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw BizException.of(500, "反序列化失败: " + e.getMessage());
        }
    }

    /** 字符串数组字段（needs_json / banned_json / refs_json）读取，空值返回空列表 */
    public static List<String> toStringList(String json) {
        if (TextUtil.isBlank(json)) {
            return List.of();
        }
        List<String> list = fromJson(json, new TypeReference<>() {
        });
        return list == null ? List.of() : list;
    }

    public static Map<String, Object> toMap(String json) {
        if (TextUtil.isBlank(json)) {
            return Map.of();
        }
        Map<String, Object> map = fromJson(json, new TypeReference<>() {
        });
        return map == null ? Map.of() : map;
    }
}
