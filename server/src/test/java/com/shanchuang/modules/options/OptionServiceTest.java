package com.shanchuang.modules.options;

import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.options.service.OptionService;
import com.shanchuang.modules.testsupport.ModuleTestBase;
import com.shanchuang.workflow.model.OptionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionServiceTest extends ModuleTestBase {

    @Autowired
    private OptionService optionService;

    @BeforeEach
    void clearCache() {
        optionService.evict();
    }

    @Test
    void 库空时回落内置默认值() {
        OptionCatalog c = optionService.catalog();

        assertEquals(List.of("转化类", "破圈类", "家长类"), c.topicTypeKeys());
        assertEquals(4, c.topicSources().size());
        assertEquals(List.of("AI就业"), c.gridInner());
        assertEquals(8, c.gridMiddle().size());
        assertEquals(16, c.gridOuter().size());
        assertEquals(8, c.viralElements().size());
        assertEquals(4, c.scriptTypes().size());
        assertEquals("开头抛痛点 + 中间讲干货 + 结尾软引导", c.formulaOf("痛点科普"));
        assertEquals(4, c.scriptType("痛点科普").orElseThrow().ratio());
    }

    @Test
    void 需补素材的来源进入refSources() {
        OptionCatalog c = optionService.catalog();

        assertEquals(List.of("对标爆款拆解", "评论私信需求挖掘"), c.refSources());
        assertTrue(c.topicSources().stream().filter(s -> "客户咨询高频提问".equals(s.key())).allMatch(
                OptionCatalog.TopicSource::ready));
    }

    @Test
    void 去味提示词来自classpath() {
        String prompt = optionService.catalog().deaiPrompt();

        assertFalse(prompt.isBlank());
        assertTrue(prompt.startsWith("请全面化身为顶级语言风格编辑"));
    }

    @Test
    void 下发字段与接口设计一致() {
        Map<String, Object> json = JsonUtil.toMap(JsonUtil.toJson(optionService.catalog()));

        assertEquals(Set.of("topicTypes", "topicSources", "gridInner", "gridMiddle", "gridOuter",
                "viralElements", "scriptTypes", "refSources", "deaiPrompt"), json.keySet());
        assertEquals(Set.of("key", "name", "tag", "desc"), firstKeys(json, "topicTypes"));
        assertEquals(Set.of("key", "desc", "ready", "note"), firstKeys(json, "topicSources"));
        assertEquals(Set.of("key", "hint"), firstKeys(json, "viralElements"));
        assertEquals(Set.of("key", "ratio", "goal", "formula", "desc"), firstKeys(json, "scriptTypes"));
    }

    @Test
    void 有数据时按sortNo升序且过滤停用项() {
        insertOption("TOPIC_TYPE", "破圈类", 2, 1);
        insertOption("TOPIC_TYPE", "转化类", 1, 1);
        insertOption("TOPIC_TYPE", "已下线", 3, 0);

        OptionCatalog c = optionService.catalog();

        assertEquals(List.of("转化类", "破圈类"), c.topicTypeKeys());
    }

    @Test
    void 单类缺数据时只回落这一类() {
        insertOption("TOPIC_TYPE", "只剩一个", 1, 1);

        OptionCatalog c = optionService.catalog();

        assertEquals(List.of("只剩一个"), c.topicTypeKeys());
        assertEquals(16, c.gridOuter().size(), "宫格外圈没配，要用内置默认");
    }

    @Test
    void 缓存生效且可手动清() {
        insertOption("TOPIC_TYPE", "第一版", 1, 1);
        assertEquals(List.of("第一版"), optionService.catalog().topicTypeKeys());

        jdbc.update("UPDATE sv_option_item SET item_key = '第二版' WHERE item_key = '第一版'");
        assertEquals(List.of("第一版"), optionService.catalog().topicTypeKeys(), "5 分钟内应命中缓存");

        optionService.evict();
        assertEquals(List.of("第二版"), optionService.catalog().topicTypeKeys());
    }

    @SuppressWarnings("unchecked")
    private Set<String> firstKeys(Map<String, Object> json, String field) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) json.get(field);
        return list.get(0).keySet();
    }

    private void insertOption(String category, String key, int sortNo, int status) {
        jdbc.update("INSERT INTO sv_option_item (category, item_key, item_name, sort_no, status) VALUES (?, ?, ?, ?, ?)",
                category, key, key, sortNo, status);
    }
}
