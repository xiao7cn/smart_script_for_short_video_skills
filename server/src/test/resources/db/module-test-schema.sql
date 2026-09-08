-- 四个业务模块单测用的 H2 建表脚本（MODE=MySQL）
-- 字段与 deploy/sql/01-schema.sql 对齐；只去掉 ON UPDATE CURRENT_TIMESTAMP 与 MySQL 专有类型

DROP TABLE IF EXISTS sv_user;
CREATE TABLE sv_user
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone         VARCHAR(20)   DEFAULT NULL,
    open_id       VARCHAR(64)   DEFAULT NULL,
    union_id      VARCHAR(64)   DEFAULT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    avatar        VARCHAR(255)  DEFAULT NULL,
    via           VARCHAR(16)  NOT NULL,
    status        TINYINT      NOT NULL DEFAULT 1,
    free_granted  TINYINT      NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP     DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_phone UNIQUE (phone),
    CONSTRAINT uk_user_open_id UNIQUE (open_id)
);

DROP TABLE IF EXISTS sv_sms_code;
CREATE TABLE sv_sms_code
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(20) NOT NULL,
    code       VARCHAR(8)  NOT NULL,
    expire_at  TIMESTAMP   NOT NULL,
    used       TINYINT     NOT NULL DEFAULT 0,
    send_ip    VARCHAR(64) DEFAULT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS sv_persona;
CREATE TABLE sv_persona
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    model       VARCHAR(64)   NOT NULL,
    model_desc  VARCHAR(500)  DEFAULT NULL,
    identity    VARCHAR(500)  DEFAULT NULL,
    value_prop  VARCHAR(500)  DEFAULT NULL,
    tone        VARCHAR(255)  DEFAULT NULL,
    audience    VARCHAR(500)  DEFAULT NULL,
    needs_json  VARCHAR(4000) DEFAULT NULL,
    banned_json VARCHAR(4000) DEFAULT NULL,
    min_words   INT           NOT NULL DEFAULT 500,
    cta_style   VARCHAR(255)  DEFAULT NULL,
    cta_asset   VARCHAR(255)  DEFAULT NULL,
    platform    VARCHAR(32)   NOT NULL DEFAULT '抖音',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_persona_user UNIQUE (user_id)
);

DROP TABLE IF EXISTS sv_option_item;
CREATE TABLE sv_option_item
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category    VARCHAR(32)   NOT NULL,
    item_key    VARCHAR(64)   NOT NULL,
    item_name   VARCHAR(64)   DEFAULT NULL,
    tag         VARCHAR(32)   DEFAULT NULL,
    hint        VARCHAR(500)  DEFAULT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    formula     VARCHAR(255)  DEFAULT NULL,
    goal        VARCHAR(32)   DEFAULT NULL,
    ratio       INT           DEFAULT NULL,
    ready       TINYINT       NOT NULL DEFAULT 1,
    note        VARCHAR(500)  DEFAULT NULL,
    sort_no     INT           NOT NULL DEFAULT 0,
    status      TINYINT       NOT NULL DEFAULT 1,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_option_cat_key UNIQUE (category, item_key)
);

DROP TABLE IF EXISTS sv_credit_account;
CREATE TABLE sv_credit_account
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT    NOT NULL,
    balance         INT       NOT NULL DEFAULT 0,
    hold            INT       NOT NULL DEFAULT 0,
    total_granted   INT       NOT NULL DEFAULT 0,
    total_recharged INT       NOT NULL DEFAULT 0,
    total_consumed  INT       NOT NULL DEFAULT 0,
    version         INT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_user UNIQUE (user_id)
);

DROP TABLE IF EXISTS sv_credit_txn;
CREATE TABLE sv_credit_txn
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    amount        INT          NOT NULL,
    balance_after INT          NOT NULL,
    ref_type      VARCHAR(16)  DEFAULT NULL,
    ref_id        VARCHAR(64)  DEFAULT NULL,
    remark        VARCHAR(255) DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS sv_recharge_order;
CREATE TABLE sv_recharge_order
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(32) NOT NULL,
    user_id       BIGINT      NOT NULL,
    pack_id       VARCHAR(32) NOT NULL,
    price_fen     INT         NOT NULL,
    base_credits  INT         NOT NULL,
    bonus_credits INT         NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    pay_channel   VARCHAR(16) NOT NULL DEFAULT 'mock',
    paid_at       TIMESTAMP   DEFAULT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_no UNIQUE (order_no)
);

-- /api/auth/me 的 stats 要数这张表；只建计数用得到的列
-- generated 用反引号建列：它是 MySQL 保留字，查询侧也必须反引号，两边写法保持一致
DROP TABLE IF EXISTS sv_script;
CREATE TABLE sv_script
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT    NOT NULL,
    seq_no       INT       NOT NULL DEFAULT 1,
    title        VARCHAR(255) DEFAULT NULL,
    `generated`  TINYINT   NOT NULL DEFAULT 1,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT   NOT NULL DEFAULT 0
);
