-- 闪创工厂 · 初始数据
-- 对应文档：docs/数据库设计.md 第 6 章

USE `shanchuang`;
SET NAMES utf8mb4;


-- ============================================================
-- 1. 向导选项
-- ============================================================

DELETE FROM `sv_option_item`;

-- 1.1 选题类型
INSERT INTO `sv_option_item` (`category`, `item_key`, `item_name`, `tag`, `description`, `sort_no`)
VALUES ('TOPIC_TYPE', '转化类', '转化类选题', '变现核心',
        '聚焦 AI 转行前景、岗位解析、培训避坑等刚需话题，直击用户学习意愿，快速筛选高意向潜在学员。', 1),
       ('TOPIC_TYPE', '破圈类', '破圈类选题', '拉曝光',
        '围绕职场内卷、赛道抉择、成长感悟等普适话题，依托情绪共鸣打破圈层壁垒，持续注入新流量。', 2),
       ('TOPIC_TYPE', '家长类', '家长类选题', '撬报名',
        '从子女职业规划、核心技能价值、就业稳定性切入，立足家庭视角建立信任、消解防御心理。', 3);

-- 1.2 选题来源（ready=0 表示需补素材）
INSERT INTO `sv_option_item` (`category`, `item_key`, `description`, `ready`, `note`, `sort_no`)
VALUES ('TOPIC_SOURCE', '客户咨询高频提问',
        '客户高频提问就是最好的选题方向，直面真实诉求，产出既有共鸣又利于转化的内容。', 1, NULL, 1),
       ('TOPIC_SOURCE', '对标爆款拆解',
        '拆解同赛道高赞内容的底层逻辑，换视角、换话术、换案例做差异化二次创作。', 0,
        '需额外提供对标视频链接或文案原文，交由视频文案抽取能力处理。', 2),
       ('TOPIC_SOURCE', '评论私信需求挖掘',
        '深挖评论区、私信中的疑问、吐槽与建议，精准匹配潜在受众的好奇点、焦虑点。', 0,
        '需真实留言原文，不可编造。', 3),
       ('TOPIC_SOURCE', '行业热点借势融合',
        '紧跟行业新闻、职场趋势与社会热点，结合自身领域输出专业观点，借势流量风口。', 1, NULL, 4);

-- 1.3 25 宫格 · 内圈（领域）
INSERT INTO `sv_option_item` (`category`, `item_key`, `sort_no`)
VALUES ('GRID_INNER', 'AI就业', 1);

-- 1.4 25 宫格 · 中圈（话题）
INSERT INTO `sv_option_item` (`category`, `item_key`, `sort_no`)
VALUES ('GRID_MIDDLE', '求职', 1),
       ('GRID_MIDDLE', '面试', 2),
       ('GRID_MIDDLE', '专业', 3),
       ('GRID_MIDDLE', '薪资', 4),
       ('GRID_MIDDLE', '晋升', 5),
       ('GRID_MIDDLE', '岗位/职业', 6),
       ('GRID_MIDDLE', '培训', 7),
       ('GRID_MIDDLE', 'AIGC', 8);

-- 1.5 25 宫格 · 外圈（人群 / 维度）
INSERT INTO `sv_option_item` (`category`, `item_key`, `sort_no`)
VALUES ('GRID_OUTER', '毕业生', 1),
       ('GRID_OUTER', '求职者', 2),
       ('GRID_OUTER', '待转行', 3),
       ('GRID_OUTER', '专/本科生', 4),
       ('GRID_OUTER', '机构', 5),
       ('GRID_OUTER', '考公/编', 6),
       ('GRID_OUTER', '失业/被裁', 7),
       ('GRID_OUTER', '文/理科生', 8),
       ('GRID_OUTER', '不同专业', 9),
       ('GRID_OUTER', '普通人', 10),
       ('GRID_OUTER', '找对象', 11),
       ('GRID_OUTER', '大厂', 12),
       ('GRID_OUTER', '投资成本', 13),
       ('GRID_OUTER', '发展前景', 14),
       ('GRID_OUTER', '职场', 15),
       ('GRID_OUTER', '保障', 16);

-- 1.6 爆款元素句式
INSERT INTO `sv_option_item` (`category`, `item_key`, `hint`, `sort_no`)
VALUES ('VIRAL_ELEMENT', '成本', '便宜又有面子的 / 十分之一的时间金钱 / 花大钱干的', 1),
       ('VIRAL_ELEMENT', '人群', '想要（）但不具备条件的 / 因为（）现在可愁了', 2),
       ('VIRAL_ELEMENT', '奇葩', '外行人不知道的 / 脑回路有病的 / 黑心内幕操作', 3),
       ('VIRAL_ELEMENT', '头牌', '生意最好的 / 最贵的 / 明星名校名企', 4),
       ('VIRAL_ELEMENT', '怀旧', '20 年前的 / 如果能重来一次 / 历史风潮盘点', 5),
       ('VIRAL_ELEMENT', '反差', '反向操作 / 身份反差 / 古今穷富对照', 6),
       ('VIRAL_ELEMENT', '最差', '最没面子的 / 差评最多的 / 贬值最多的', 7),
       ('VIRAL_ELEMENT', '荷尔蒙', '好找对象的 / 魅力变强的', 8);

-- 1.7 脚本类型（ratio 即 4:1:3:2 的权重）
INSERT INTO `sv_option_item` (`category`, `item_key`, `ratio`, `goal`, `formula`, `description`, `sort_no`)
VALUES ('SCRIPT_TYPE', '痛点科普', 4, '白嫖你', '开头抛痛点 + 中间讲干货 + 结尾软引导',
        '用硬核干货建立信任壁垒，最后以解决方案自然承接，是高效变现的核心框架。', 1),
       ('SCRIPT_TYPE', 'Vlog 叙事', 1, '了解你', '开场引入 + 片段拼接 + 结尾感悟',
        '以沉浸式镜头语言串联碎片化场景，弱化刻意感，通过结尾情绪升华提升温度。', 2),
       ('SCRIPT_TYPE', '聊天纪实', 3, '信任你', '场景引入 + 对话片段 + 观点总结',
        '还原真实沟通场景，以实景对话增强可信度，兼顾故事质感与专业说服力。', 3),
       ('SCRIPT_TYPE', '话题共鸣', 2, '喜欢你', '抛出话题 + 表达观点 + 引导评论',
        '用争议或共情话题做钩子，结尾开放提问带动互动，拉高平台推荐权重。', 4);


-- ============================================================
-- 2. AI 模型配置（按场景独立）
--
--    分工按各家长处来：写长文用 DeepSeek（快且稳，实测 500 字约 10 秒），
--    生成、去 AI 味、重写这三个出长文的场景都归它；
--    选题发散与爆款拆解归 Kimi。
--
--    Kimi 这两行有三个反直觉的地方，改之前先读，否则会得到「空文案」而不是报错：
--    1. provider 必须是 pi-ai 内置的 moonshotai-cn，base_url 必须留空。
--       填了 base_url 就会走通用 OpenAI 兼容分支，那条路不会发 Kimi 私有的
--       thinking 参数，k2.6 便按默认开启思考，把 max_tokens 全用在 reasoning_content 上，
--       content 返回空串。内置 provider 会自动带 thinking:{"type":"disabled"}。
--    2. temperature 只能是 0.60。Kimi 的合法值随思考模式变：思考开启时只收 1，
--       关闭时只收 0.6，其余一律 invalid temperature。这里既然关了思考，就只能 0.6。
--       别担心 0.6 对拆解不够稳：拿一条 6656 字的真实稿子各跑两遍比过，
--       Kimi 0.6 两次的骨架段数与框架步数完全一致，DeepSeek 0.3 反而漂了（7→8、7→10）。
--       低温度不等于结论稳，模型本身的输出稳定性才是主因。代价是慢约 4 倍
--       （111s vs 29s），拆解是异步任务，能接受。
--    3. 用 kimi-k2.6 而不是更新的 kimi-k3：k3 始终推理且关不掉，与上面两条冲突。
--    任何一行都能单独换厂商换模型，改完下一次调用生效，不重启也不发版。
--
--    Kimi 用 kimi-k2.6 而不是更新的 kimi-k3：k3 始终推理且关不掉，
--    与本表 thinking='off' 的约定冲突，k2.6 才支持思考开关。
--
--    base_url 非空即视为自建/中转端点，Agent 会为它动态注册一个
--    OpenAI 兼容 provider（走 /v1/chat/completions，兼容面最广）。
--    api_key_env 只存环境变量名，密钥本身只在 agent/.env，不落库。
-- ============================================================

DELETE FROM `sv_ai_model_config`;

INSERT INTO `sv_ai_model_config`
(`scene`, `provider`, `model_id`, `temperature`, `max_tokens`, `thinking`,
 `base_url`, `api_key_env`, `max_steps`, `timeout_ms`, `enabled`, `remark`)
VALUES ('TOPIC_TITLE', 'moonshotai-cn', 'kimi-k2.6', 0.60, 2048, 'off',
        NULL, 'MOONSHOT_API_KEY', 1, 120000, 1,
        '选题与标题：0.6 是 Kimi 关思考时的唯一合法值，不是调出来的'),
       ('SCRIPT_GENERATE', 'deepseek', 'deepseek-chat', 0.80, 4096, 'off',
        NULL, 'DEEPSEEK_API_KEY', 1, 180000, 1,
        '脚本生成：DeepSeek，长文快且稳'),
       ('SCRIPT_DEAI', 'deepseek', 'deepseek-chat', 0.70, 4096, 'off',
        NULL, 'DEEPSEEK_API_KEY', 1, 180000, 1,
        '去 AI 味：DeepSeek。与写作分成两次独立调用，同一次里既写又改会留下模型自己的表达习惯'),
       ('SCRIPT_REWRITE', 'deepseek', 'deepseek-chat', 0.60, 8192, 'off',
        NULL, 'DEEPSEEK_API_KEY', 6, 600000, 1,
        '文案重写：十一段合同，长上下文 + 强结构遵循，同属长文场景'),
       ('VIDEO_EXTRACT', 'moonshotai-cn', 'kimi-k2.6', 0.60, 8192, 'off',
        NULL, 'MOONSHOT_API_KEY', 8, 600000, 1,
        '爆款拆解：需要工具调用。0.6 实测比 DeepSeek 0.3 的结论更稳、拆得更深');


-- ============================================================
-- 3. Harness 配置
--    is_default=1 的记录为当前生效项，改这一行即可切换 harness
-- ============================================================

DELETE FROM `sv_harness_config`;

INSERT INTO `sv_harness_config` (`name`, `endpoint`, `timeout_ms`, `enabled`, `is_default`, `remark`)
VALUES ('pi', 'http://127.0.0.1:8790', 300000, 1, 1,
        'pi agent harness（@earendil-works/pi-ai + pi-agent-core），Node sidecar'),
       ('mock', NULL, 5000, 1, 0,
        '离线开发与单元测试用，按场景返回结构合法的假数据'),
       ('deepseek', NULL, 300000, 0, 0,
        '预留：后续切换 deepseek harness 时填 endpoint 并置 is_default=1');
