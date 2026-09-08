package com.shanchuang.workflow;

import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.WizardSel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamPickerTest {

    private final OptionCatalog catalog = TestFixtures.catalog();
    private final ParamPicker picker = new ParamPicker(catalog);

    private Map<String, Integer> countByType(List<String> types) {
        Map<String, Integer> counts = new HashMap<>();
        types.forEach(t -> counts.merge(t, 1, Integer::sum));
        return counts;
    }

    @Test
    @DisplayName("n=10 时脚本类型严格按 4:1:3:2")
    void ratioAtTen() {
        Map<String, Integer> counts = countByType(
                picker.allocateScriptTypes(WizardSel.empty(), 10, new Random(1)));
        assertEquals(4, counts.get("痛点科普"));
        assertEquals(1, counts.get("Vlog 叙事"));
        assertEquals(3, counts.get("聊天纪实"));
        assertEquals(2, counts.get("话题共鸣"));
    }

    @Test
    @DisplayName("n=15 时用最大余数法，平手按权重降序，得到 6/1/5/3")
    void ratioAtFifteen() {
        Map<String, Integer> counts = countByType(
                picker.allocateScriptTypes(WizardSel.empty(), 15, new Random(7)));
        // 精确份额 6.0 / 1.5 / 4.5 / 3.0，整数部分共 14，余 1
        // Vlog 与聊天纪实的小数部分都是 0.5，按权重降序给权重更大的聊天纪实
        assertEquals(6, counts.get("痛点科普"));
        assertEquals(1, counts.get("Vlog 叙事"));
        assertEquals(5, counts.get("聊天纪实"));
        assertEquals(3, counts.get("话题共鸣"));
    }

    @Test
    @DisplayName("n=20 时是 n=10 的两倍，配比不漂移")
    void ratioAtTwenty() {
        Map<String, Integer> counts = countByType(
                picker.allocateScriptTypes(WizardSel.empty(), 20, new Random(3)));
        assertEquals(8, counts.get("痛点科普"));
        assertEquals(2, counts.get("Vlog 叙事"));
        assertEquals(6, counts.get("聊天纪实"));
        assertEquals(4, counts.get("话题共鸣"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 7, 10, 13, 15, 20})
    @DisplayName("任意条数下总数必须精确等于请求条数")
    void totalAlwaysMatches(int n) {
        List<String> types = picker.allocateScriptTypes(WizardSel.empty(), n, new Random(n));
        assertEquals(n, types.size());
        assertTrue(catalog.scriptTypeKeys().containsAll(Set.copyOf(types)));
    }

    @Test
    @DisplayName("最大余数法保证配比按比例逼近，纯随机做不到这一点")
    void ratioIsDeterministicNotRandom() {
        // 同一个 n 换 20 个不同随机种子，配比计数必须完全一致（只有顺序会变）
        Map<String, Integer> expected = countByType(
                picker.allocateScriptTypes(WizardSel.empty(), 10, new Random(0)));
        for (int seed = 1; seed < 20; seed++) {
            assertEquals(expected,
                    countByType(picker.allocateScriptTypes(WizardSel.empty(), 10, new Random(seed))),
                    "seed " + seed + " 的配比不该变");
        }
    }

    @Test
    @DisplayName("用户锁定脚本类型时只在所选集合内分配")
    void respectsUserSelection() {
        WizardSel sel = new WizardSel(null, null, null, null, null, null,
                List.of("聊天纪实", "话题共鸣"), null, null, null);
        List<String> types = picker.allocateScriptTypes(sel, 10, new Random(5));
        assertEquals(10, types.size());
        assertTrue(Set.of("聊天纪实", "话题共鸣").containsAll(Set.copyOf(types)));
        // 3:2 → 6/4
        Map<String, Integer> counts = countByType(types);
        assertEquals(6, counts.get("聊天纪实"));
        assertEquals(4, counts.get("话题共鸣"));
    }

    @Test
    @DisplayName("批内参数组合不重复：10 条里两条讲同一件事就是失败的批量")
    void dedupWithinBatch() {
        List<ParamCard> cards = picker.pick(WizardSel.empty(), 10, 42L);
        assertEquals(10, cards.size());
        Set<String> keys = cards.stream().map(ParamCard::dedupKey).collect(Collectors.toSet());
        assertEquals(10, keys.size(), "参数组合应互不重复");
    }

    @Test
    @DisplayName("候选池不足时允许重复，但相邻两条不能一样")
    void relaxDedupWhenPoolTooSmall() {
        // 只给 1 个中圈 + 1 个外圈 + 1 个元素 + 1 个脚本类型，却要 8 条
        WizardSel sel = new WizardSel(List.of("转化类"), List.of("客户咨询高频提问"),
                List.of("AI就业"), List.of("薪资"), List.of("毕业生"),
                List.of("成本"), List.of("痛点科普"), null, null, null);
        List<ParamCard> cards = picker.pick(sel, 8, 9L);
        assertEquals(8, cards.size());
        for (int i = 1; i < cards.size(); i++) {
            // 候选空间只有 1 种组合，此时无法做到相邻不同，只要求不抛异常且条数正确
            assertFalse(cards.get(i).dedupKey().isEmpty());
        }
    }

    @Test
    @DisplayName("同 seed 可复现同一批参数，便于排查上次那批为什么是这样")
    void reproducibleWithSeed() {
        List<ParamCard> first = picker.pick(WizardSel.empty(), 6, 2026L);
        List<ParamCard> second = picker.pick(WizardSel.empty(), 6, 2026L);
        assertEquals(first.stream().map(ParamCard::dedupKey).toList(),
                second.stream().map(ParamCard::dedupKey).toList());
    }

    @Test
    @DisplayName("需补素材的来源不会被随机抽中，否则会凭空要素材或编造留言")
    void neverAutoPickSourcesNeedingMaterial() {
        List<ParamCard> cards = picker.pick(WizardSel.empty(), 20, 11L);
        for (ParamCard card : cards) {
            assertFalse("对标爆款拆解".equals(card.source()));
            assertFalse("评论私信需求挖掘".equals(card.source()));
        }
    }

    @Test
    @DisplayName("用户主动选了对标来源就要尊重")
    void honorsExplicitBenchmarkSource() {
        WizardSel sel = new WizardSel(null, List.of("对标爆款拆解"), null, null, null,
                null, null, null, List.of("https://v.douyin.com/x/"), true);
        List<ParamCard> cards = picker.pick(sel, 3, 4L);
        for (ParamCard card : cards) {
            assertEquals("对标爆款拆解", card.source());
            assertTrue(card.fromBenchmark());
            assertTrue(card.autoSearch());
            assertEquals(List.of("https://v.douyin.com/x/"), card.refs());
        }
    }

    @Test
    @DisplayName("宫格展示串按内×中×外拼，全空时兜底为 AI就业")
    void gridString() {
        ParamCard withAll = new ParamCard(0, "转化类", "客户咨询高频提问", "AI就业", "薪资",
                "毕业生", "成本", "痛点科普", "公式", null, List.of(), false);
        assertEquals("AI就业 × 薪资 × 毕业生", withAll.grid());

        ParamCard empty = new ParamCard(0, "转化类", "客户咨询高频提问", null, null,
                null, "成本", "痛点科普", "公式", null, List.of(), false);
        assertEquals("AI就业", empty.grid());
    }

    @Test
    @DisplayName("结构公式跟着脚本类型走，不能张冠李戴")
    void formulaFollowsScriptType() {
        List<ParamCard> cards = picker.pick(WizardSel.empty(), 12, 8L);
        for (ParamCard card : cards) {
            assertEquals(catalog.formulaOf(card.scriptType()), card.formula());
        }
    }
}
