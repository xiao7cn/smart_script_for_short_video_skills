package com.shanchuang.modules.options.service;

import com.shanchuang.workflow.model.OptionCatalog;

import java.util.List;

/**
 * 内置候选池，值与 deploy/sql/02-data.sql 一致。
 * 库里没播种数据时用它兜底：候选池空了参数抽取就无从下手，整条生成链路会直接哑掉。
 */
final class OptionDefaults {

    private OptionDefaults() {
    }

    static final List<OptionCatalog.TopicType> TOPIC_TYPES = List.of(
            new OptionCatalog.TopicType("转化类", "转化类选题", "变现核心",
                    "聚焦 AI 转行前景、岗位解析、培训避坑等刚需话题，直击用户学习意愿，快速筛选高意向潜在学员。"),
            new OptionCatalog.TopicType("破圈类", "破圈类选题", "拉曝光",
                    "围绕职场内卷、赛道抉择、成长感悟等普适话题，依托情绪共鸣打破圈层壁垒，持续注入新流量。"),
            new OptionCatalog.TopicType("家长类", "家长类选题", "撬报名",
                    "从子女职业规划、核心技能价值、就业稳定性切入，立足家庭视角建立信任、消解防御心理。")
    );

    static final List<OptionCatalog.TopicSource> TOPIC_SOURCES = List.of(
            new OptionCatalog.TopicSource("客户咨询高频提问",
                    "客户高频提问就是最好的选题方向，直面真实诉求，产出既有共鸣又利于转化的内容。", true, null),
            new OptionCatalog.TopicSource("对标爆款拆解",
                    "拆解同赛道高赞内容的底层逻辑，换视角、换话术、换案例做差异化二次创作。", false,
                    "需额外提供对标视频链接或文案原文，交由视频文案抽取能力处理。"),
            new OptionCatalog.TopicSource("评论私信需求挖掘",
                    "深挖评论区、私信中的疑问、吐槽与建议，精准匹配潜在受众的好奇点、焦虑点。", false,
                    "需真实留言原文，不可编造。"),
            new OptionCatalog.TopicSource("行业热点借势融合",
                    "紧跟行业新闻、职场趋势与社会热点，结合自身领域输出专业观点，借势流量风口。", true, null)
    );

    static final List<String> GRID_INNER = List.of("AI就业");

    static final List<String> GRID_MIDDLE = List.of(
            "求职", "面试", "专业", "薪资", "晋升", "岗位/职业", "培训", "AIGC");

    static final List<String> GRID_OUTER = List.of(
            "毕业生", "求职者", "待转行", "专/本科生", "机构", "考公/编", "失业/被裁", "文/理科生",
            "不同专业", "普通人", "找对象", "大厂", "投资成本", "发展前景", "职场", "保障");

    static final List<OptionCatalog.ViralElement> VIRAL_ELEMENTS = List.of(
            new OptionCatalog.ViralElement("成本", "便宜又有面子的 / 十分之一的时间金钱 / 花大钱干的"),
            new OptionCatalog.ViralElement("人群", "想要（）但不具备条件的 / 因为（）现在可愁了"),
            new OptionCatalog.ViralElement("奇葩", "外行人不知道的 / 脑回路有病的 / 黑心内幕操作"),
            new OptionCatalog.ViralElement("头牌", "生意最好的 / 最贵的 / 明星名校名企"),
            new OptionCatalog.ViralElement("怀旧", "20 年前的 / 如果能重来一次 / 历史风潮盘点"),
            new OptionCatalog.ViralElement("反差", "反向操作 / 身份反差 / 古今穷富对照"),
            new OptionCatalog.ViralElement("最差", "最没面子的 / 差评最多的 / 贬值最多的"),
            new OptionCatalog.ViralElement("荷尔蒙", "好找对象的 / 魅力变强的")
    );

    static final List<OptionCatalog.ScriptType> SCRIPT_TYPES = List.of(
            new OptionCatalog.ScriptType("痛点科普", 4, "白嫖你", "开头抛痛点 + 中间讲干货 + 结尾软引导",
                    "用硬核干货建立信任壁垒，最后以解决方案自然承接，是高效变现的核心框架。"),
            new OptionCatalog.ScriptType("Vlog 叙事", 1, "了解你", "开场引入 + 片段拼接 + 结尾感悟",
                    "以沉浸式镜头语言串联碎片化场景，弱化刻意感，通过结尾情绪升华提升温度。"),
            new OptionCatalog.ScriptType("聊天纪实", 3, "信任你", "场景引入 + 对话片段 + 观点总结",
                    "还原真实沟通场景，以实景对话增强可信度，兼顾故事质感与专业说服力。"),
            new OptionCatalog.ScriptType("话题共鸣", 2, "喜欢你", "抛出话题 + 表达观点 + 引导评论",
                    "用争议或共情话题做钩子，结尾开放提问带动互动，拉高平台推荐权重。")
    );
}
