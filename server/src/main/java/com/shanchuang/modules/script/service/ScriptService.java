package com.shanchuang.modules.script.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.result.PageData;
import com.shanchuang.common.util.JsonUtil;
import com.shanchuang.modules.script.dto.ScriptDtos.ScriptDetail;
import com.shanchuang.modules.script.dto.ScriptDtos.ScriptListItem;
import com.shanchuang.modules.script.entity.Script;
import com.shanchuang.modules.script.entity.ScriptBreakdown;
import com.shanchuang.modules.script.mapper.ScriptBreakdownMapper;
import com.shanchuang.modules.script.mapper.ScriptMapper;
import com.shanchuang.workflow.model.ScriptDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 文案库的读写。列表不查大字段，详情才取 body 与拆解 */
@Service
public class ScriptService {

    private static final String ALL = "全部";

    private final ScriptMapper scriptMapper;
    private final ScriptBreakdownMapper breakdownMapper;
    /** 已预留到的最大展示编号，配合 allocateSeqRange 防撞号 */
    private final Map<Long, Integer> reservedSeq = new ConcurrentHashMap<>();

    public ScriptService(ScriptMapper scriptMapper, ScriptBreakdownMapper breakdownMapper) {
        this.scriptMapper = scriptMapper;
        this.breakdownMapper = breakdownMapper;
    }

    public PageData<ScriptListItem> page(Long userId, String topicType, String scriptType,
                                         long page, long size) {
        IPage<Script> result = scriptMapper.selectPage(new Page<>(page, size),
                Wrappers.<Script>lambdaQuery()
                        .select(Script::getId, Script::getSeqNo, Script::getTitle, Script::getTopic,
                                Script::getScriptType, Script::getTopicType, Script::getSource,
                                Script::getGrid, Script::getElement, Script::getWords,
                                Script::getGenerated, Script::getCreatedAt)
                        .eq(Script::getUserId, userId)
                        .eq(hasFilter(topicType), Script::getTopicType, topicType)
                        .eq(hasFilter(scriptType), Script::getScriptType, scriptType)
                        .orderByDesc(Script::getCreatedAt));

        List<Long> ids = result.getRecords().stream().map(Script::getId).toList();
        Set<Long> withBreakdown = breakdownIds(ids);

        List<ScriptListItem> records = result.getRecords().stream()
                .map(s -> ScriptListItem.of(s, withBreakdown.contains(s.getId())))
                .toList();
        return PageData.of(records, result.getTotal(), page, size);
    }

    public ScriptDetail detail(Long userId, Long id) {
        Script script = requireOwned(userId, id);
        ScriptBreakdown breakdown = breakdownMapper.selectOne(
                Wrappers.<ScriptBreakdown>lambdaQuery()
                        .eq(ScriptBreakdown::getScriptId, id)
                        .last("limit 1"));
        return ScriptDetail.of(script, breakdown);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        scriptMapper.deleteById(id);
    }

    /**
     * 预留一段展示编号，返回起始号。
     *
     * 批内条目是并发跑的，各自「读 max 再 +1」会撞号（实测 5 条里出现两个 NO.01）。
     * 这里一次性把整批号段占掉，条目只用 base + idx。
     * 内存里额外记一份已预留值，是为了覆盖同一用户并发提交多个任务的情况。
     * 多节点部署时要换成数据库序列，或给 seq_no 建唯一索引后重试。
     */
    public synchronized int allocateSeqRange(Long userId, int count) {
        int dbMax = scriptMapper.maxSeqNo(userId);
        int reserved = reservedSeq.getOrDefault(userId, 0);
        int base = Math.max(dbMax, reserved) + 1;
        reservedSeq.put(userId, base + count - 1);
        return base;
    }

    /** 生成成功后入库。seqNo 由调用方从预留号段里给，删除过的编号不复用 */
    @Transactional
    public Script save(Long userId, Long taskId, ScriptDraft draft, int seqNo) {
        Script script = new Script();
        script.setUserId(userId);
        script.setSeqNo(seqNo);
        script.setTaskId(taskId);
        script.setTitle(draft.title());
        script.setTopic(draft.topic());
        script.setScriptType(draft.card().scriptType());
        script.setTopicType(draft.card().topicType());
        script.setSource(draft.card().source());
        script.setGrid(draft.card().grid());
        script.setElement(draft.card().element());
        script.setStructure(draft.structure());
        script.setWords(draft.words());
        script.setBody(draft.body());
        script.setDraftBody(draft.draftBody());
        script.setNeedsMaterial(draft.needsMaterial());
        script.setGenerated(1);
        scriptMapper.insert(script);
        return script;
    }

    /** 来源是对标爆款拆解时，把原文与拆解要点挂到文案上 */
    @Transactional
    public void saveBreakdown(Long scriptId, Long teardownId, List<String> refs, boolean autoSearch,
                              String original, Object points, String rewriteNote) {
        ScriptBreakdown breakdown = new ScriptBreakdown();
        breakdown.setScriptId(scriptId);
        breakdown.setTeardownId(teardownId);
        breakdown.setRefsJson(JsonUtil.toJson(refs));
        breakdown.setAutoSearch(autoSearch ? 1 : 0);
        breakdown.setOriginal(original);
        breakdown.setPointsJson(JsonUtil.toJson(points));
        breakdown.setRewriteNote(rewriteNote);
        breakdownMapper.insert(breakdown);
    }

    public Script requireOwned(Long userId, Long id) {
        Script script = scriptMapper.selectById(id);
        if (script == null) {
            throw BizException.notFound("文案不存在");
        }
        if (!script.getUserId().equals(userId)) {
            // 返回 403 而不是 404：404 会让人能靠试探判断 id 是否存在
            throw BizException.forbidden("无权访问该文案");
        }
        return script;
    }

    public long countByUser(Long userId) {
        return scriptMapper.countAll(userId);
    }

    public long countGenerated(Long userId) {
        return scriptMapper.countGenerated(userId);
    }

    private Set<Long> breakdownIds(List<Long> scriptIds) {
        if (scriptIds.isEmpty()) {
            return Set.of();
        }
        List<ScriptBreakdown> rows = breakdownMapper.selectList(
                Wrappers.<ScriptBreakdown>lambdaQuery()
                        .select(ScriptBreakdown::getScriptId)
                        .in(ScriptBreakdown::getScriptId, scriptIds));
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.getScriptId()));
        return ids;
    }

    private static boolean hasFilter(String value) {
        return value != null && !value.isBlank() && !ALL.equals(value);
    }
}
