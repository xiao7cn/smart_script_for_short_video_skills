-- 闪创工厂 · 建表脚本
-- MySQL 8.0 / utf8mb4
-- 对应文档：docs/数据库设计.md

CREATE DATABASE IF NOT EXISTS `shanchuang`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `shanchuang`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- ============================================================
-- 账号
-- ============================================================

DROP TABLE IF EXISTS `sv_user`;
CREATE TABLE `sv_user`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `phone`         VARCHAR(20)              DEFAULT NULL COMMENT '手机号，跨端唯一身份',
    `open_id`       VARCHAR(64)              DEFAULT NULL COMMENT '微信 openid',
    `union_id`      VARCHAR(64)              DEFAULT NULL COMMENT '微信 unionid',
    `nickname`      VARCHAR(64)     NOT NULL COMMENT '展示名；手机号登录默认脱敏号',
    `avatar`        VARCHAR(255)             DEFAULT NULL COMMENT '头像 URL 或 emoji',
    `via`           VARCHAR(16)     NOT NULL COMMENT '首次注册渠道 wechat / phone',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
    `free_granted`  TINYINT         NOT NULL DEFAULT 0 COMMENT '新人赠额只发一次',
    `last_login_at` DATETIME                 DEFAULT NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_open_id` (`open_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';


DROP TABLE IF EXISTS `sv_sms_code`;
CREATE TABLE `sv_sms_code`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `phone`      VARCHAR(20)     NOT NULL,
    `code`       VARCHAR(8)      NOT NULL COMMENT '6 位数字',
    `expire_at`  DATETIME        NOT NULL COMMENT '签发 +5 分钟',
    `used`       TINYINT         NOT NULL DEFAULT 0,
    `send_ip`    VARCHAR(64)              DEFAULT NULL COMMENT '风控用',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_phone_created` (`phone`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短信验证码';


-- ============================================================
-- 人设与选项
-- ============================================================

DROP TABLE IF EXISTS `sv_persona`;
CREATE TABLE `sv_persona`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `model`       VARCHAR(64)     NOT NULL COMMENT '人设模型，如 靠谱顾问',
    `model_desc`  VARCHAR(500)             DEFAULT NULL,
    `identity`    VARCHAR(500)             DEFAULT NULL COMMENT '身份定位',
    `value_prop`  VARCHAR(500)             DEFAULT NULL COMMENT '价值定位',
    `tone`        VARCHAR(255)             DEFAULT NULL COMMENT '表达风格',
    `audience`    VARCHAR(500)             DEFAULT NULL COMMENT '目标人群',
    `needs_json`  JSON                     DEFAULT NULL COMMENT '用户需求数组',
    `banned_json` JSON                     DEFAULT NULL COMMENT '禁用词数组',
    `min_words`   INT             NOT NULL DEFAULT 500 COMMENT '正文字数下限',
    `cta_style`   VARCHAR(255)             DEFAULT NULL COMMENT '软引流方式',
    `cta_asset`   VARCHAR(255)             DEFAULT NULL COMMENT '钩子资产',
    `platform`    VARCHAR(32)     NOT NULL DEFAULT '抖音',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='人设档案';


DROP TABLE IF EXISTS `sv_option_item`;
CREATE TABLE `sv_option_item`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `category`    VARCHAR(32)     NOT NULL COMMENT 'TOPIC_TYPE/TOPIC_SOURCE/GRID_INNER/GRID_MIDDLE/GRID_OUTER/VIRAL_ELEMENT/SCRIPT_TYPE',
    `item_key`    VARCHAR(64)     NOT NULL,
    `item_name`   VARCHAR(64)              DEFAULT NULL COMMENT '展示名',
    `tag`         VARCHAR(32)              DEFAULT NULL COMMENT '角标',
    `hint`        VARCHAR(500)             DEFAULT NULL COMMENT '句式提示（爆款元素）',
    `description` VARCHAR(1000)            DEFAULT NULL,
    `formula`     VARCHAR(255)             DEFAULT NULL COMMENT '结构公式（脚本类型）',
    `goal`        VARCHAR(32)              DEFAULT NULL COMMENT '目标，如 白嫖你',
    `ratio`       INT                      DEFAULT NULL COMMENT '配比权重 4/1/3/2',
    `ready`       TINYINT         NOT NULL DEFAULT 1 COMMENT '0 表示需补素材',
    `note`        VARCHAR(500)             DEFAULT NULL,
    `sort_no`     INT             NOT NULL DEFAULT 0,
    `status`      TINYINT         NOT NULL DEFAULT 1,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cat_key` (`category`, `item_key`),
    KEY `idx_cat_sort` (`category`, `status`, `sort_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='向导选项（运营可配）';


-- ============================================================
-- 任务
-- ============================================================

DROP TABLE IF EXISTS `sv_task`;
CREATE TABLE `sv_task`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_no`          CHAR(32)        NOT NULL COMMENT '对外任务号',
    `request_id`       VARCHAR(64)              DEFAULT NULL COMMENT '幂等键',
    `user_id`          BIGINT UNSIGNED NOT NULL,
    `type`             VARCHAR(24)     NOT NULL COMMENT 'SCRIPT_GENERATE/SCRIPT_REWRITE/VIDEO_EXTRACT',
    `status`           VARCHAR(16)     NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/CANCELLED',
    `total`            INT             NOT NULL DEFAULT 0,
    `done_count`       INT             NOT NULL DEFAULT 0,
    `fail_count`       INT             NOT NULL DEFAULT 0,
    `param_json`       JSON                     DEFAULT NULL COMMENT '向导参数快照 + 手改提示词',
    `credits_hold`     INT             NOT NULL DEFAULT 0,
    `credits_settled`  INT             NOT NULL DEFAULT 0,
    `error_code`       VARCHAR(32)              DEFAULT NULL,
    `error_msg`        VARCHAR(500)             DEFAULT NULL,
    `started_at`       DATETIME                 DEFAULT NULL,
    `finished_at`      DATETIME                 DEFAULT NULL,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    UNIQUE KEY `uk_req` (`user_id`, `request_id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_status` (`status`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='异步任务';


DROP TABLE IF EXISTS `sv_task_item`;
CREATE TABLE `sv_task_item`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`     BIGINT UNSIGNED NOT NULL,
    `idx`         INT             NOT NULL COMMENT '批内序号，从 0 起',
    `status`      VARCHAR(16)     NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `param_json`  JSON                     DEFAULT NULL COMMENT '该条实际参数卡',
    `script_id`   BIGINT UNSIGNED          DEFAULT NULL,
    `retry_count` INT             NOT NULL DEFAULT 0,
    `error_code`  VARCHAR(32)              DEFAULT NULL,
    `error_msg`   VARCHAR(500)             DEFAULT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_idx` (`task_id`, `idx`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='任务子项';


-- ============================================================
-- 文案
-- ============================================================

DROP TABLE IF EXISTS `sv_script`;
CREATE TABLE `sv_script`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL,
    `seq_no`         INT             NOT NULL COMMENT '用户维度展示编号',
    `task_id`        BIGINT UNSIGNED          DEFAULT NULL,
    `title`          VARCHAR(255)    NOT NULL COMMENT '吸睛标题',
    `topic`          VARCHAR(500)    NOT NULL COMMENT '选题（内容主线）',
    `script_type`    VARCHAR(24)     NOT NULL COMMENT '痛点科普/Vlog 叙事/聊天纪实/话题共鸣',
    `topic_type`     VARCHAR(24)     NOT NULL COMMENT '转化类/破圈类/家长类',
    `source`         VARCHAR(32)              DEFAULT NULL COMMENT '选题来源',
    `grid`           VARCHAR(128)             DEFAULT NULL COMMENT '25 宫格配对',
    `element`        VARCHAR(24)              DEFAULT NULL COMMENT '爆款元素',
    `structure`      VARCHAR(255)             DEFAULT NULL COMMENT '结构公式',
    `words`          INT             NOT NULL DEFAULT 0,
    `body`           MEDIUMTEXT      NOT NULL COMMENT '纯口播正文',
    `draft_body`     MEDIUMTEXT               DEFAULT NULL COMMENT '去 AI 味之前的初稿',
    `needs_material` VARCHAR(500)             DEFAULT NULL COMMENT '需替换真实素材提示',
    `generated`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 本机生成 0 内置样稿',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_user_filter` (`user_id`, `topic_type`, `script_type`),
    KEY `idx_task` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文案';


DROP TABLE IF EXISTS `sv_script_breakdown`;
CREATE TABLE `sv_script_breakdown`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `script_id`    BIGINT UNSIGNED NOT NULL,
    `teardown_id`  BIGINT UNSIGNED          DEFAULT NULL,
    `refs_json`    JSON                     DEFAULT NULL COMMENT '对标链接数组',
    `auto_search`  TINYINT         NOT NULL DEFAULT 0,
    `original`     MEDIUMTEXT               DEFAULT NULL COMMENT '对标爆款口播原文',
    `points_json`  JSON                     DEFAULT NULL COMMENT '[{label, detail}]',
    `rewrite_note` TEXT                     DEFAULT NULL COMMENT '二次创作差异说明',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_script` (`script_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='对标拆解附属';


-- ============================================================
-- 拆解与洗稿
-- ============================================================

DROP TABLE IF EXISTS `sv_teardown`;
CREATE TABLE `sv_teardown`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL,
    `task_id`        BIGINT UNSIGNED          DEFAULT NULL,
    `platform`       VARCHAR(16)              DEFAULT NULL COMMENT 'douyin/kuaishou/xiaohongshu/wechat_channel/manual',
    `source_url`     VARCHAR(500)             DEFAULT NULL,
    `source_type`    VARCHAR(16)              DEFAULT NULL COMMENT 'url/file/text',
    `video_title`    VARCHAR(500)             DEFAULT NULL COMMENT '平台完整标题，未知则 标题未知',
    `duration_sec`   INT                      DEFAULT NULL,
    `words`          INT                      DEFAULT NULL,
    `speech_rate`    INT                      DEFAULT NULL COMMENT '字/分钟',
    `transcript`     MEDIUMTEXT               DEFAULT NULL COMMENT '口播稿，不含时间戳',
    `rhythm_text`    MEDIUMTEXT               DEFAULT NULL COMMENT '节奏文件',
    `segments_json`  JSON                     DEFAULT NULL COMMENT '句级时间戳',
    `asr_backend`    VARCHAR(32)              DEFAULT NULL,
    `teardown_json`  JSON                     DEFAULT NULL COMMENT '结构拆解表',
    `framework_json` JSON                     DEFAULT NULL COMMENT '逻辑框架 + 角度',
    `status`         VARCHAR(16)     NOT NULL COMMENT 'PENDING/NEED_FILE/SUCCESS/FAILED',
    `fetch_guide`    TEXT                     DEFAULT NULL COMMENT '取件失败时的录屏指引',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_task` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='视频拆解';


DROP TABLE IF EXISTS `sv_rewrite`;
CREATE TABLE `sv_rewrite`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL,
    `task_id`        BIGINT UNSIGNED          DEFAULT NULL,
    `teardown_id`    BIGINT UNSIGNED          DEFAULT NULL,
    `script_id`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '定稿入库后的文案 id',
    `original_title` VARCHAR(500)    NOT NULL COMMENT '原视频标题',
    `original_body`  MEDIUMTEXT      NOT NULL COMMENT '原视频完整口播文案',
    `my_title`       VARCHAR(255)             DEFAULT NULL COMMENT '洗稿后自己的标题',
    `sections_json`  JSON                     DEFAULT NULL COMMENT '十一段，键 s1..s11',
    `final_body`     MEDIUMTEXT               DEFAULT NULL COMMENT '第九段定稿',
    `words`          INT                      DEFAULT NULL,
    `score_json`     JSON                     DEFAULT NULL COMMENT '8 项自检分',
    `readaloud_pass` TINYINT                  DEFAULT NULL,
    `overlap_max`    INT                      DEFAULT NULL COMMENT '与原文最长连续重合字数',
    `check_report`   TEXT                     DEFAULT NULL,
    `status`         VARCHAR(16)     NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_teardown` (`teardown_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='洗稿十一段';


-- ============================================================
-- 计费
-- ============================================================

DROP TABLE IF EXISTS `sv_credit_account`;
CREATE TABLE `sv_credit_account`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `balance`         INT             NOT NULL DEFAULT 0 COMMENT '可用额度',
    `hold`            INT             NOT NULL DEFAULT 0 COMMENT '预扣中',
    `total_granted`   INT             NOT NULL DEFAULT 0,
    `total_recharged` INT             NOT NULL DEFAULT 0,
    `total_consumed`  INT             NOT NULL DEFAULT 0,
    `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='额度账户';


DROP TABLE IF EXISTS `sv_credit_txn`;
CREATE TABLE `sv_credit_txn`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `type`          VARCHAR(16)     NOT NULL COMMENT 'GRANT/RECHARGE/CONSUME/REFUND',
    `amount`        INT             NOT NULL COMMENT '正数入账 负数出账',
    `balance_after` INT             NOT NULL,
    `ref_type`      VARCHAR(16)              DEFAULT NULL COMMENT 'TASK/ORDER/SYSTEM',
    `ref_id`        VARCHAR(64)              DEFAULT NULL,
    `remark`        VARCHAR(255)             DEFAULT NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_ref` (`ref_type`, `ref_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='额度流水';


DROP TABLE IF EXISTS `sv_recharge_order`;
CREATE TABLE `sv_recharge_order`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`      CHAR(32)        NOT NULL,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `pack_id`       VARCHAR(32)     NOT NULL,
    `price_fen`     INT UNSIGNED    NOT NULL COMMENT '支付金额（分）',
    `base_credits`  INT             NOT NULL,
    `bonus_credits` INT             NOT NULL DEFAULT 0,
    `status`        VARCHAR(16)     NOT NULL COMMENT 'PENDING/PAID/CLOSED',
    `pay_channel`   VARCHAR(16)     NOT NULL DEFAULT 'mock',
    `paid_at`       DATETIME                 DEFAULT NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='充值订单';


-- ============================================================
-- AI 配置与留痕
-- ============================================================

DROP TABLE IF EXISTS `sv_ai_model_config`;
CREATE TABLE `sv_ai_model_config`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `scene`       VARCHAR(32)     NOT NULL COMMENT 'TOPIC_TITLE/SCRIPT_GENERATE/SCRIPT_DEAI/SCRIPT_REWRITE/VIDEO_EXTRACT',
    `provider`    VARCHAR(32)     NOT NULL COMMENT 'deepseek/openai/anthropic/google/ollama…',
    `model_id`    VARCHAR(64)     NOT NULL,
    `temperature` DECIMAL(3, 2)            DEFAULT NULL,
    `max_tokens`  INT                      DEFAULT NULL,
    `thinking`    VARCHAR(16)     NOT NULL DEFAULT 'off',
    `base_url`    VARCHAR(255)             DEFAULT NULL COMMENT 'OpenAI 兼容端点',
    `api_key_env` VARCHAR(64)              DEFAULT NULL COMMENT '环境变量名，不存密钥本身',
    `max_steps`   INT             NOT NULL DEFAULT 1 COMMENT 'Agent 循环步数上限',
    `timeout_ms`  INT             NOT NULL DEFAULT 180000,
    `enabled`     TINYINT         NOT NULL DEFAULT 1,
    `remark`      VARCHAR(255)             DEFAULT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene` (`scene`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='按场景的模型配置';


DROP TABLE IF EXISTS `sv_harness_config`;
CREATE TABLE `sv_harness_config`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`            VARCHAR(32)     NOT NULL COMMENT 'pi/deepseek/mock',
    `endpoint`        VARCHAR(255)             DEFAULT NULL,
    `timeout_ms`      INT             NOT NULL DEFAULT 180000,
    `enabled`         TINYINT         NOT NULL DEFAULT 1,
    `is_default`      TINYINT         NOT NULL DEFAULT 0 COMMENT '当前生效的 harness',
    `version`         VARCHAR(32)              DEFAULT NULL,
    `last_health_at`  DATETIME                 DEFAULT NULL,
    `last_health_ok`  TINYINT                  DEFAULT NULL,
    `remark`          VARCHAR(255)             DEFAULT NULL,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Harness 配置';


DROP TABLE IF EXISTS `sv_llm_call_log`;
CREATE TABLE `sv_llm_call_log`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `request_id`      VARCHAR(64)              DEFAULT NULL,
    `user_id`         BIGINT UNSIGNED          DEFAULT NULL,
    `task_id`         BIGINT UNSIGNED          DEFAULT NULL,
    `scene`           VARCHAR(32)     NOT NULL,
    `harness_name`    VARCHAR(32)              DEFAULT NULL,
    `harness_version` VARCHAR(32)              DEFAULT NULL,
    `provider`        VARCHAR(32)              DEFAULT NULL,
    `model_id`        VARCHAR(64)              DEFAULT NULL,
    `input_tokens`    INT                      DEFAULT NULL,
    `output_tokens`   INT                      DEFAULT NULL,
    `cost_usd`        DECIMAL(12, 6)           DEFAULT NULL,
    `steps`           INT                      DEFAULT NULL,
    `latency_ms`      INT                      DEFAULT NULL,
    `ok`              TINYINT         NOT NULL,
    `error_code`      VARCHAR(32)              DEFAULT NULL,
    `error_msg`       VARCHAR(500)             DEFAULT NULL,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_scene_created` (`scene`, `created_at`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='LLM 调用留痕';


DROP TABLE IF EXISTS `sv_file`;
CREATE TABLE `sv_file`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `file_id`    VARCHAR(64)     NOT NULL COMMENT '对外文件 id',
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `file_name`  VARCHAR(255)    NOT NULL,
    `store_path` VARCHAR(500)    NOT NULL COMMENT '私有目录相对路径',
    `size_bytes` BIGINT          NOT NULL,
    `mime`       VARCHAR(64)              DEFAULT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_id` (`file_id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='上传素材';


SET FOREIGN_KEY_CHECKS = 1;
