package com.shanchuang.workflow;

import com.shanchuang.workflow.model.OptionCatalog;
import com.shanchuang.workflow.model.PersonaSnapshot;

import java.util.List;

/** 测试用的选项目录与人设，值照 deploy/sql/02-data.sql 的初始数据 */
final class TestFixtures {

    private TestFixtures() {
    }

    static OptionCatalog catalog() {
        return new OptionCatalog(
                List.of(
                        new OptionCatalog.TopicType("转化类", "转化类选题", "变现核心", "聚焦刚需话题"),
                        new OptionCatalog.TopicType("破圈类", "破圈类选题", "拉曝光", "情绪共鸣破圈"),
                        new OptionCatalog.TopicType("家长类", "家长类选题", "撬报名", "家庭视角建立信任")
                ),
                List.of(
                        new OptionCatalog.TopicSource("客户咨询高频提问", "直面真实诉求", true, null),
                        new OptionCatalog.TopicSource("对标爆款拆解", "拆解高赞逻辑", false, "需提供链接"),
                        new OptionCatalog.TopicSource("评论私信需求挖掘", "深挖留言", false, "需真实留言"),
                        new OptionCatalog.TopicSource("行业热点借势融合", "借势流量风口", true, null)
                ),
                List.of("AI就业"),
                List.of("求职", "面试", "专业", "薪资", "晋升", "岗位/职业", "培训", "AIGC"),
                List.of("毕业生", "求职者", "待转行", "专/本科生", "机构", "考公/编",
                        "失业/被裁", "文/理科生", "不同专业", "普通人", "找对象", "大厂",
                        "投资成本", "发展前景", "职场", "保障"),
                List.of(
                        new OptionCatalog.ViralElement("成本", "便宜又有面子的"),
                        new OptionCatalog.ViralElement("人群", "想要但不具备条件的"),
                        new OptionCatalog.ViralElement("奇葩", "外行人不知道的"),
                        new OptionCatalog.ViralElement("头牌", "生意最好的"),
                        new OptionCatalog.ViralElement("怀旧", "20 年前的"),
                        new OptionCatalog.ViralElement("反差", "反向操作"),
                        new OptionCatalog.ViralElement("最差", "最没面子的"),
                        new OptionCatalog.ViralElement("荷尔蒙", "好找对象的")
                ),
                List.of(
                        new OptionCatalog.ScriptType("痛点科普", 4, "白嫖你",
                                "开头抛痛点 + 中间讲干货 + 结尾软引导", "干货建立信任"),
                        new OptionCatalog.ScriptType("Vlog 叙事", 1, "了解你",
                                "开场引入 + 片段拼接 + 结尾感悟", "沉浸式镜头语言"),
                        new OptionCatalog.ScriptType("聊天纪实", 3, "信任你",
                                "场景引入 + 对话片段 + 观点总结", "还原真实沟通"),
                        new OptionCatalog.ScriptType("话题共鸣", 2, "喜欢你",
                                "抛出话题 + 表达观点 + 引导评论", "争议话题做钩子")
                ),
                List.of("对标爆款拆解", "评论私信需求挖掘"),
                "去 AI 味提示词"
        );
    }

    static PersonaSnapshot persona() {
        return PersonaSnapshot.systemDefault();
    }

    /** 一段能过质量门的正文：够字数、有我/你、有语气词、无禁用词与书面腔 */
    static String passingBody() {
        String para = "你知道吗？我见过太多人卡在同一个地方。不是不够努力，是方向没找对。"
                + "我先说个可能不太中听的判断：这块的真正门槛，跟大家想的不一样。"
                + "第一件事，别再盲目堆时间。我见过有人学了半年，一到实战全崩。"
                + "第二件事，找一个你能落地的小切口，把一件重复又烦人的事做出结果来，对吧？"
                + "它比十张证书都有用，因为别人能顺着它往下问。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(para).append("\n\n");
        }
        sb.append("其实啊，你现在最想解决的是哪一个？评论区说说你的情况，我帮你看看。");
        return sb.toString();
    }
}
