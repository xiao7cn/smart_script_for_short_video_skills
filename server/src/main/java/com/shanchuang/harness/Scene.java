package com.shanchuang.harness;

import java.util.List;

/**
 * 模型使用场景。
 *
 * 场景是「模型可配置」的粒度：每个场景在 sv_ai_model_config 里有独立一行，
 * 所以脚本生成与文案重写可以指向完全不同的 provider 与模型，互不影响。
 * 工具白名单与步数上限也按场景定义，对应 docs/接口设计.md 12.3。
 */
public enum Scene {

    /** 选题与标题：要发散，温度高、便宜快 */
    TOPIC_TITLE("选题与标题", List.of(), 1),

    /** 写口播正文 */
    SCRIPT_GENERATE("脚本生成", List.of(), 1),

    /** 去 AI 味。独立成一个场景是刻意的：写和改交给不同模型，去味效果更好 */
    SCRIPT_DEAI("去 AI 味", List.of(), 1),

    /** 洗稿十一段合同，允许模型自己调 check_script 复核 */
    SCRIPT_REWRITE("文案重写", List.of("check_script"), 6),

    /** 爆款拆解，需要取件与转写工具 */
    VIDEO_EXTRACT("爆款拆解", List.of("fetch_video", "transcribe"), 8);

    private final String label;
    private final List<String> defaultTools;
    private final int defaultMaxSteps;

    Scene(String label, List<String> defaultTools, int defaultMaxSteps) {
        this.label = label;
        this.defaultTools = defaultTools;
        this.defaultMaxSteps = defaultMaxSteps;
    }

    public String label() {
        return label;
    }

    public List<String> defaultTools() {
        return defaultTools;
    }

    public int defaultMaxSteps() {
        return defaultMaxSteps;
    }

    public static Scene of(String name) {
        for (Scene s : values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
