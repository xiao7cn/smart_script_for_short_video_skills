package com.shanchuang.workflow.model;

import java.util.List;
import java.util.Optional;

/**
 * 向导选项目录。
 *
 * 原型里这些值硬编码在 data/config.ts，产品化后交给运营在 sv_option_item 里配，
 * 由 /api/options 下发。参数抽取与提示词拼装都以这份目录为候选池。
 */
public record OptionCatalog(
        List<TopicType> topicTypes,
        List<TopicSource> topicSources,
        List<String> gridInner,
        List<String> gridMiddle,
        List<String> gridOuter,
        List<ViralElement> viralElements,
        List<ScriptType> scriptTypes,
        List<String> refSources,
        String deaiPrompt
) {

    public record TopicType(String key, String name, String tag, String desc) {
    }

    public record TopicSource(String key, String desc, boolean ready, String note) {
    }

    public record ViralElement(String key, String hint) {
    }

    public record ScriptType(String key, int ratio, String goal, String formula, String desc) {
    }

    public List<String> topicTypeKeys() {
        return topicTypes.stream().map(TopicType::key).toList();
    }

    public List<String> topicSourceKeys() {
        return topicSources.stream().map(TopicSource::key).toList();
    }

    public List<String> elementKeys() {
        return viralElements.stream().map(ViralElement::key).toList();
    }

    public List<String> scriptTypeKeys() {
        return scriptTypes.stream().map(ScriptType::key).toList();
    }

    public Optional<ScriptType> scriptType(String key) {
        return scriptTypes.stream().filter(s -> s.key().equals(key)).findFirst();
    }

    public String formulaOf(String scriptTypeKey) {
        return scriptType(scriptTypeKey).map(ScriptType::formula).orElse("");
    }

    public Optional<ViralElement> element(String key) {
        return viralElements.stream().filter(e -> e.key().equals(key)).findFirst();
    }
}
