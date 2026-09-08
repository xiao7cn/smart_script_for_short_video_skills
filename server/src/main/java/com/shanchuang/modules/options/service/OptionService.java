package com.shanchuang.modules.options.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanchuang.modules.options.entity.OptionItem;
import com.shanchuang.modules.options.mapper.OptionItemMapper;
import com.shanchuang.workflow.model.OptionCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OptionService {

    private static final Logger log = LoggerFactory.getLogger(OptionService.class);

    /** 运营改选项后最多 5 分钟生效；选项是每次生成都要读的热数据，不缓存等于每条都打一次库 */
    private static final long TTL_MS = 5 * 60 * 1000L;

    private static final String DEAI_PROMPT_PATH = "prompts/deai-prompt.txt";

    private static final String TOPIC_TYPE = "TOPIC_TYPE";
    private static final String TOPIC_SOURCE = "TOPIC_SOURCE";
    private static final String GRID_INNER = "GRID_INNER";
    private static final String GRID_MIDDLE = "GRID_MIDDLE";
    private static final String GRID_OUTER = "GRID_OUTER";
    private static final String VIRAL_ELEMENT = "VIRAL_ELEMENT";
    private static final String SCRIPT_TYPE = "SCRIPT_TYPE";

    private final OptionItemMapper optionItemMapper;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public OptionService(OptionItemMapper optionItemMapper) {
        this.optionItemMapper = optionItemMapper;
    }

    /** 生成链路的候选池，带 5 分钟本地缓存 */
    public OptionCatalog catalog() {
        Cached hit = cache.get();
        long now = System.currentTimeMillis();
        if (hit != null && hit.expireAt > now) {
            return hit.catalog;
        }
        OptionCatalog fresh = load();
        cache.set(new Cached(fresh, now + TTL_MS));
        return fresh;
    }

    /** 运营改完选项想立刻生效时手动清；单测也用它验证缓存边界 */
    public void evict() {
        cache.set(null);
    }

    private OptionCatalog load() {
        List<OptionItem> rows = optionItemMapper.selectList(new LambdaQueryWrapper<OptionItem>()
                .eq(OptionItem::getStatus, 1)
                .orderByAsc(OptionItem::getSortNo)
                .orderByAsc(OptionItem::getId));

        List<OptionCatalog.TopicType> topicTypes = rows.stream()
                .filter(r -> TOPIC_TYPE.equals(r.getCategory()))
                .map(r -> new OptionCatalog.TopicType(r.getItemKey(), r.getItemName(), r.getTag(), r.getDescription()))
                .toList();
        List<OptionCatalog.TopicSource> topicSources = rows.stream()
                .filter(r -> TOPIC_SOURCE.equals(r.getCategory()))
                .map(r -> new OptionCatalog.TopicSource(r.getItemKey(), r.getDescription(),
                        r.getReady() == null || r.getReady() == 1, r.getNote()))
                .toList();
        List<OptionCatalog.ViralElement> elements = rows.stream()
                .filter(r -> VIRAL_ELEMENT.equals(r.getCategory()))
                .map(r -> new OptionCatalog.ViralElement(r.getItemKey(), r.getHint()))
                .toList();
        List<OptionCatalog.ScriptType> scriptTypes = rows.stream()
                .filter(r -> SCRIPT_TYPE.equals(r.getCategory()))
                .map(r -> new OptionCatalog.ScriptType(r.getItemKey(),
                        r.getRatio() == null ? 1 : r.getRatio(), r.getGoal(), r.getFormula(), r.getDescription()))
                .toList();

        // 逐类兜底：只播了一半数据时也不至于让某一层宫格变成空池
        topicTypes = fallbackIfEmpty(topicTypes, OptionDefaults.TOPIC_TYPES, TOPIC_TYPE);
        topicSources = fallbackIfEmpty(topicSources, OptionDefaults.TOPIC_SOURCES, TOPIC_SOURCE);
        elements = fallbackIfEmpty(elements, OptionDefaults.VIRAL_ELEMENTS, VIRAL_ELEMENT);
        scriptTypes = fallbackIfEmpty(scriptTypes, OptionDefaults.SCRIPT_TYPES, SCRIPT_TYPE);
        List<String> inner = fallbackIfEmpty(keys(rows, GRID_INNER), OptionDefaults.GRID_INNER, GRID_INNER);
        List<String> middle = fallbackIfEmpty(keys(rows, GRID_MIDDLE), OptionDefaults.GRID_MIDDLE, GRID_MIDDLE);
        List<String> outer = fallbackIfEmpty(keys(rows, GRID_OUTER), OptionDefaults.GRID_OUTER, GRID_OUTER);

        // ready=0 的来源需要用户额外补对标素材，前端据此决定是否插入「对标视频」步骤
        List<String> refSources = topicSources.stream()
                .filter(s -> !s.ready())
                .map(OptionCatalog.TopicSource::key)
                .toList();

        return new OptionCatalog(topicTypes, topicSources, inner, middle, outer,
                elements, scriptTypes, refSources, deaiPrompt());
    }

    private List<String> keys(List<OptionItem> rows, String category) {
        List<String> out = new ArrayList<>();
        for (OptionItem r : rows) {
            if (category.equals(r.getCategory())) {
                out.add(r.getItemKey());
            }
        }
        return out;
    }

    private <T> List<T> fallbackIfEmpty(List<T> loaded, List<T> defaults, String category) {
        if (!loaded.isEmpty()) {
            return loaded;
        }
        log.warn("sv_option_item 没有 {} 的可用数据，回落内置默认值", category);
        return defaults;
    }

    private String deaiPrompt() {
        try (InputStream in = new ClassPathResource(DEAI_PROMPT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("读取 {} 失败：{}", DEAI_PROMPT_PATH, e.getMessage());
            return "";
        }
    }

    private record Cached(OptionCatalog catalog, long expireAt) {
    }
}
