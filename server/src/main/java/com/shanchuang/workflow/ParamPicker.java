package com.shanchuang.workflow;

import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.ParamCard;
import com.shanchuang.workflow.model.WizardSel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 选题参数抽取。
 *
 * 两件事交给代码而不是模型：
 * 1. 脚本类型必须严格按 痛点科普:Vlog叙事:聊天纪实:话题共鸣 = 4:1:3:2，
 *    模型自行随机会明显偏离配比；
 * 2. 批内去重——10 条里有 2 条讲同一件事就是失败的批量。
 */
public class ParamPicker {

    /** 找不重复组合的尝试上限，按条数放大 */
    private static final int DEDUP_ATTEMPT_FACTOR = 8;

    private final OptionCatalog catalog;

    public ParamPicker(OptionCatalog catalog) {
        this.catalog = catalog;
    }

    public List<ParamCard> pick(WizardSel sel, int count) {
        return pick(sel, count, new Random());
    }

    /** 传入固定 seed 可复现同一批参数，便于排查「上次那批为什么是这样」 */
    public List<ParamCard> pick(WizardSel sel, int count, long seed) {
        return pick(sel, count, new Random(seed));
    }

    public List<ParamCard> pick(WizardSel sel, int count, Random random) {
        List<String> scriptTypes = allocateScriptTypes(sel, count, random);

        List<ParamCard> cards = new ArrayList<>(count);
        Set<String> usedKeys = new HashSet<>();
        int attemptLimit = count * DEDUP_ATTEMPT_FACTOR;

        for (int i = 0; i < count; i++) {
            ParamCard card = null;
            for (int attempt = 0; attempt < attemptLimit; attempt++) {
                ParamCard candidate = buildCard(i, sel, scriptTypes.get(i), random);
                if (usedKeys.add(candidate.dedupKey())) {
                    card = candidate;
                    break;
                }
            }
            if (card == null) {
                // 候选空间不足（比如只选了 1 个中圈却要 20 条）。允许重复，
                // 但保证不与上一条相同，读起来不至于连着两条一模一样。
                card = buildDistinctFromPrevious(i, sel, scriptTypes.get(i), random, cards);
            }
            cards.add(card);
        }
        return cards;
    }

    /**
     * 脚本类型分配：最大余数法。
     *
     * 纯随机在 n=10 时方差很大，达不到 Skill 要求的严格配比。
     * 平手时按权重降序打破——早期按遍历顺序补位，导致两批 15 条永远得到
     * 12/2/10/6，永远凑不出 12/3/9/6。
     */
    List<String> allocateScriptTypes(WizardSel sel, int count, Random random) {
        List<OptionCatalog.ScriptType> pool = candidateScriptTypes(sel);
        if (pool.isEmpty()) {
            return Collections.nCopies(count, "痛点科普");
        }

        int totalWeight = pool.stream().mapToInt(OptionCatalog.ScriptType::ratio).sum();
        if (totalWeight <= 0) {
            // 权重没配就退化成均分
            List<String> even = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                even.add(pool.get(i % pool.size()).key());
            }
            Collections.shuffle(even, random);
            return even;
        }

        int[] quota = new int[pool.size()];
        double[] remainder = new double[pool.size()];
        int assigned = 0;
        for (int i = 0; i < pool.size(); i++) {
            double exact = (double) count * pool.get(i).ratio() / totalWeight;
            quota[i] = (int) Math.floor(exact);
            remainder[i] = exact - quota[i];
            assigned += quota[i];
        }

        int left = count - assigned;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            int byRemainder = Double.compare(remainder[b], remainder[a]);
            if (byRemainder != 0) {
                return byRemainder;
            }
            return Integer.compare(pool.get(b).ratio(), pool.get(a).ratio());
        });
        for (int i = 0; i < left; i++) {
            quota[order.get(i % order.size())]++;
        }

        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < pool.size(); i++) {
            for (int j = 0; j < quota[i]; j++) {
                result.add(pool.get(i).key());
            }
        }
        Collections.shuffle(result, random);
        return result;
    }

    /** 用户选了脚本类型就只在所选集合内按同样方法分配 */
    private List<OptionCatalog.ScriptType> candidateScriptTypes(WizardSel sel) {
        if (sel != null && sel.scriptType() != null && !sel.scriptType().isEmpty()) {
            List<OptionCatalog.ScriptType> chosen = catalog.scriptTypes().stream()
                    .filter(s -> sel.scriptType().contains(s.key()))
                    .toList();
            if (!chosen.isEmpty()) {
                return chosen;
            }
        }
        return catalog.scriptTypes();
    }

    private ParamCard buildCard(int idx, WizardSel sel, String scriptType, Random random) {
        String topicType = pickFrom(sel == null ? null : sel.topicType(), catalog.topicTypeKeys(), random);
        String source = pickFrom(sel == null ? null : sel.source(), readySourceKeys(), random);
        // 内圈可以为空：原型里内圈只有「AI就业」一个值，用户常常不选
        String inner = pickOrNull(sel == null ? null : sel.inner(), random);
        String middle = pickFrom(sel == null ? null : sel.middle(), catalog.gridMiddle(), random);
        String outer = pickFrom(sel == null ? null : sel.outer(), catalog.gridOuter(), random);
        String element = pickFrom(sel == null ? null : sel.element(), catalog.elementKeys(), random);

        return new ParamCard(
                idx, topicType, source, inner, middle, outer, element, scriptType,
                catalog.formulaOf(scriptType),
                sel == null ? null : sel.topicDraft(),
                sel == null ? List.of() : sel.refsOrEmpty(),
                sel != null && sel.autoSearchOn()
        );
    }

    private ParamCard buildDistinctFromPrevious(int idx, WizardSel sel, String scriptType,
                                                Random random, List<ParamCard> existing) {
        String previousKey = existing.isEmpty() ? null : existing.get(existing.size() - 1).dedupKey();
        ParamCard candidate = buildCard(idx, sel, scriptType, random);
        for (int i = 0; i < 16 && candidate.dedupKey().equals(previousKey); i++) {
            candidate = buildCard(idx, sel, scriptType, random);
        }
        return candidate;
    }

    /**
     * 「对标爆款拆解」「评论私信需求挖掘」需要用户额外提供素材，
     * 用户没主动选时不该被随机抽中——否则会凭空要素材或编造留言。
     */
    private List<String> readySourceKeys() {
        List<String> ready = catalog.topicSources().stream()
                .filter(OptionCatalog.TopicSource::ready)
                .map(OptionCatalog.TopicSource::key)
                .toList();
        return ready.isEmpty() ? catalog.topicSourceKeys() : ready;
    }

    private String pickFrom(List<String> selected, List<String> pool, Random random) {
        if (selected != null && !selected.isEmpty()) {
            return selected.get(random.nextInt(selected.size()));
        }
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private String pickOrNull(List<String> selected, Random random) {
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        return selected.get(random.nextInt(selected.size()));
    }

    /** 实际配比，用于批量总表末尾核对 4:1:3:2 */
    public static String ratioSummary(List<ParamCard> cards) {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (ParamCard card : cards) {
            counts.merge(card.scriptType(), 1L, Long::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(k).append(" ×").append(v).append("　"));
        return sb.toString().trim();
    }
}
