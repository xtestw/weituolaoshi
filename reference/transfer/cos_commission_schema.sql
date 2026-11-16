-- COS委托平台：角色、小时报价、媒体链接整合版 / 2026-11-14
-- MySQL 8.0.16+（建议8.4 LTS），11张表，空库新建脚本，不是已有库升级迁移；未执行真实建表。
-- D1A先付款后coser确认；D2A一次全款；D3A同coser排他接单；D5A一期关闭改期。
-- D4B仅适用于单主取消已确认且未开始的预约：距服务开始>=48小时退100%；>=24且<48小时退50%；<24小时退0。
-- 未确认前单主取消、coser拒单、确认超时、coser开始服务前取消、取消后迟到支付：全额退款。
-- 已开始服务、爽约、不可抗力及其他争议：转人工，不能直接套用自动取消规则。
-- D6A单主确认完成，未确认转人工，不按时间自动完成/自动结算；人工处理时限尚未确定。
-- D7付款15分钟，付款后coser确认2小时，至少提前24小时预约。
-- 退款按币种最小单位四舍五入（正数HALF_UP），核算基数为实际已付金额，不是帖子原价。
-- 取消费用=实付-核定应退，不等于平台收入或coser已结算收入，费用归属与结算仍需单独确定。
-- 本模型含业务退款与操作日志；渠道支付尝试、账务分录、结算及争议工单由独立系统承担。
-- 所有连接均需设置UTC；金额用Decimal；匿名、地点、图片需应用访问控制。

SET time_zone = '+00:00';

CREATE TABLE marketplace_user (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，JavaScript接口以字符串传输',
    identity_provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '身份提供方',
    identity_subject VARCHAR(191) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '身份提供方内不可变主体ID',
    nickname VARCHAR(64) NOT NULL COMMENT '展示昵称，不唯一',
    age TINYINT UNSIGNED DEFAULT NULL COMMENT '当前年龄；未知或脱敏为空，由应用维护，不作为实名认证依据',
    gender_code VARCHAR(32) COLLATE utf8mb4_0900_bin DEFAULT NULL COMMENT '性别代码；应用字典定义，不预设仅两种',
    same_gender_commission_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '同性委托开关；0关闭1开启；不代表性取向',
    avatar_key VARCHAR(512) DEFAULT NULL COMMENT '头像持久对象键；非公开访问凭证',
    avatar_url VARCHAR(2048) DEFAULT NULL COMMENT '头像稳定HTTPS访问链接；不存临时签名URL，默认头像可为空',
    bio VARCHAR(500) DEFAULT NULL COMMENT '公开简介',
    country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'ISO国家码',
    account_status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1正常 2冻结 3注销',
    last_login_at DATETIME(3) DEFAULT NULL COMMENT '最近登录时间UTC',
    closed_at DATETIME(3) DEFAULT NULL COMMENT '注销时间；保留历史关联并按隐私策略脱敏',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '应用递增的乐观锁版本号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_identity (identity_provider, identity_subject),
    CONSTRAINT ck_user_status CHECK (account_status IN (1,2,3)),
    CONSTRAINT ck_user_closed CHECK ((account_status = 3 AND closed_at IS NOT NULL) OR (account_status IN (1,2) AND closed_at IS NULL)),
    CONSTRAINT ck_user_age CHECK (age IS NULL OR age <= 120),
    CONSTRAINT ck_user_commission_flag CHECK (same_gender_commission_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单主与coser共用的用户账号';

CREATE TABLE marketplace_author (
    author_id BIGINT UNSIGNED NOT NULL COMMENT 'coser ID，共享用户主键',
    display_name VARCHAR(100) NOT NULL COMMENT 'coser展示名',
    introduction TEXT COMMENT '公开专业介绍',
    service_city_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '主要服务城市编码，非精确地址',
    timezone_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'IANA时区，由应用校验，例如Asia/Shanghai',
    verification_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未认证 1审核中 2通过 3驳回',
    verification_description VARCHAR(255) DEFAULT NULL COMMENT '可公开认证描述，不保存身份证件',
    service_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待开通 1可接单 2暂停 3禁用',
    hourly_price DECIMAL(18,4) DEFAULT NULL COMMENT 'coser基础小时单价；发布时复制到委托帖，改价不自动覆盖已发布帖子',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '基础报价ISO4217币种，由应用校验',
    currency_scale TINYINT UNSIGNED DEFAULT NULL COMMENT '币种金额小数位，由币种配置确定',
    booking_step_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 60 COMMENT '选时及预约时长步长，分钟；锚定窗口起点',
    buffer_before_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '服务前准备或通勤占用分钟数',
    buffer_after_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '服务后缓冲占用分钟数',
    min_lead_minutes INT UNSIGNED NOT NULL DEFAULT 1440 COMMENT '最少提前预约分钟数，默认1440即24小时，不得低于1440',
    activated_at DATETIME(3) DEFAULT NULL COMMENT '作者身份开通时间UTC',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '作者资料与排期策略版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (author_id),
    KEY idx_author_city (service_status, service_city_code, author_id),
    CONSTRAINT fk_author_user FOREIGN KEY (author_id) REFERENCES marketplace_user (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_author_verify CHECK (verification_status IN (0,1,2,3)),
    CONSTRAINT ck_author_service CHECK (service_status IN (0,1,2,3)),
    CONSTRAINT ck_author_lead CHECK (min_lead_minutes >= 1440),
    CONSTRAINT ck_author_step CHECK (booking_step_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_author_price CHECK ((hourly_price IS NULL AND currency IS NULL AND currency_scale IS NULL)
        OR (hourly_price IS NOT NULL AND hourly_price>0 AND currency IS NOT NULL AND CHAR_LENGTH(TRIM(currency))=3
            AND currency_scale IS NOT NULL AND currency_scale BETWEEN 0 AND 4 AND hourly_price=ROUND(hourly_price,currency_scale))),
    CONSTRAINT ck_author_ready CHECK (service_status<>1 OR hourly_price IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='coser身份与全局排期策略；预约事务先锁定此行';

CREATE TABLE marketplace_author_role (
    author_role_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'coser可委托角色记录ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT 'coser ID',
    ip_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '作品或系列IP名称；应用统一去首尾空格及别名',
    character_name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '该IP下可接受委托的角色名称',
    role_status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1可委托 2暂停委托；不物理删除已成交引用',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色资料应用版本号；修改时递增',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (author_role_id),
    UNIQUE KEY uk_author_ip_character (author_id, ip_name, character_name),
    UNIQUE KEY uk_author_role_owner (author_role_id, author_id),
    KEY idx_author_role_list (author_id, role_status),
    CONSTRAINT fk_author_role_author FOREIGN KEY (author_id) REFERENCES marketplace_author (author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_author_role_status CHECK (role_status IN (1,2)),
    CONSTRAINT ck_author_role_name CHECK (CHAR_LENGTH(TRIM(ip_name))>0 AND CHAR_LENGTH(TRIM(character_name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='coser可接受委托的多IP角色清单；非全站IP字典';

CREATE TABLE marketplace_content (
    content_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT '发布者coser ID',
    content_kind TINYINT UNSIGNED NOT NULL COMMENT '1仅展示作品帖 2可预约委托帖；均可配图片或视频',
    author_role_id BIGINT UNSIGNED DEFAULT NULL COMMENT '本帖委托角色；委托帖必填且属于同一coser，展示帖可为空',
    cover_object_key VARCHAR(512) DEFAULT NULL COMMENT '列表封面对象键；来自本帖首个审核通过且就绪媒体',
    cover_url VARCHAR(2048) DEFAULT NULL COMMENT '列表封面稳定HTTPS访问链接；派生缓存，媒体变更时同步',
    title VARCHAR(200) NOT NULL COMMENT '帖子标题',
    description TEXT NOT NULL COMMENT '作品或委托服务说明',
    ip_name VARCHAR(100) DEFAULT NULL COMMENT '帖子IP展示快照；委托帖发布时复制所选角色IP',
    character_name VARCHAR(100) DEFAULT NULL COMMENT '帖子角色展示快照；委托帖发布时复制所选角色，单帖对应单一委托角色',
    service_type_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '委托服务类型编码，字典由业务维护',
    service_mode TINYINT UNSIGNED DEFAULT NULL COMMENT '1线下 2线上；仅委托帖使用',
    service_city_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '服务城市，线下委托必填',
    pricing_type TINYINT UNSIGNED DEFAULT NULL COMMENT '本版委托帖固定1按小时，展示帖为空',
    unit_price DECIMAL(18,4) DEFAULT NULL COMMENT '委托帖对外小时报价；发布时复制作者基础价，后续调价显式操作并递增版本',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'ISO 4217币种',
    currency_scale TINYINT UNSIGNED DEFAULT NULL COMMENT '币种金额小数位0至4，由币种字典决定，不能自由填写',
    min_duration_minutes SMALLINT UNSIGNED DEFAULT NULL COMMENT '最短预约分钟数',
    max_duration_minutes SMALLINT UNSIGNED DEFAULT NULL COMMENT '最长预约分钟数',
    service_terms TEXT COMMENT '服务范围、费用包含项、取消规则等；成交时快照',
    visibility TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1公开 2私密',
    audit_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未提交 1审核中 2通过 3驳回',
    publish_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0草稿 1发布或上架 2下架',
    published_at DATETIME(3) DEFAULT NULL COMMENT '发布时间UTC',
    deleted_at DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间UTC',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '报价、服务及图片变更均递增',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (content_id),
    UNIQUE KEY uk_content_author (content_id, author_id),
    KEY idx_content_author (author_id, deleted_at, content_kind, created_at, content_id),
    KEY idx_content_market (content_kind, publish_status, visibility, audit_status, deleted_at, service_city_code, published_at, content_id),
    CONSTRAINT fk_content_author FOREIGN KEY (author_id) REFERENCES marketplace_author (author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_content_kind CHECK (content_kind IN (1,2)),
    CONSTRAINT ck_content_status CHECK (visibility IN (1,2) AND audit_status IN (0,1,2,3) AND publish_status IN (0,1,2)),
    CONSTRAINT ck_content_commission CHECK (
        (content_kind = 1 AND service_mode IS NULL AND pricing_type IS NULL AND unit_price IS NULL
         AND currency IS NULL AND currency_scale IS NULL AND min_duration_minutes IS NULL AND max_duration_minutes IS NULL)
        OR
        (content_kind = 2 AND service_mode IS NOT NULL AND service_mode IN (1,2)
         AND pricing_type IS NOT NULL AND pricing_type=1 AND unit_price IS NOT NULL AND unit_price > 0
         AND currency IS NOT NULL AND CHAR_LENGTH(TRIM(currency)) = 3
         AND currency_scale IS NOT NULL AND currency_scale BETWEEN 0 AND 4 AND unit_price = ROUND(unit_price,currency_scale)
         AND min_duration_minutes IS NOT NULL AND min_duration_minutes > 0
         AND max_duration_minutes IS NOT NULL AND max_duration_minutes >= min_duration_minutes
         AND service_terms IS NOT NULL AND CHAR_LENGTH(TRIM(service_terms)) > 0
         AND (service_mode = 2 OR (service_city_code IS NOT NULL AND CHAR_LENGTH(TRIM(service_city_code)) > 0)))
    ),
    UNIQUE KEY uk_content_order_role (content_id, author_id, author_role_id),
    KEY idx_content_role (author_role_id, author_id),
    CONSTRAINT fk_content_role FOREIGN KEY (author_role_id, author_id) REFERENCES marketplace_author_role (author_role_id, author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_content_role_required CHECK (content_kind<>2 OR (author_role_id IS NOT NULL
        AND ip_name IS NOT NULL AND CHAR_LENGTH(TRIM(ip_name))>0
        AND character_name IS NOT NULL AND CHAR_LENGTH(TRIM(character_name))>0)),
    CONSTRAINT ck_content_published CHECK (publish_status<>1 OR (audit_status=2 AND published_at IS NOT NULL
        AND cover_object_key IS NOT NULL AND CHAR_LENGTH(TRIM(cover_object_key))>0
        AND cover_url IS NOT NULL AND CHAR_LENGTH(TRIM(cover_url))>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品帖与委托帖；排期归coser而非帖子独占';

CREATE TABLE marketplace_content_media (
    media_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '媒体记录ID',
    content_id BIGINT UNSIGNED NOT NULL COMMENT '所属帖子ID',
    media_type TINYINT UNSIGNED NOT NULL COMMENT '1图片 2视频',
    object_key VARCHAR(512) NOT NULL COMMENT '持久对象键，访问时鉴权生成URL',
    thumbnail_key VARCHAR(512) DEFAULT NULL COMMENT '缩略图或视频封面对象键',
    media_url VARCHAR(2048) DEFAULT NULL COMMENT '图片或视频稳定HTTPS访问入口；不存临时签名URL，受控内容由网关鉴权',
    thumbnail_url VARCHAR(2048) DEFAULT NULL COMMENT '缩略图或视频封面稳定HTTPS访问入口',
    processing_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0处理中 1就绪 2处理失败；只有就绪且审核通过才可展示',
    deleted_at DATETIME(3) DEFAULT NULL COMMENT '媒体逻辑删除时间UTC；订单快照引用按保留策略处理',
    sort_order SMALLINT UNSIGNED NOT NULL COMMENT '展示顺序，最小的审核通过媒体作为封面',
    width_px INT UNSIGNED DEFAULT NULL COMMENT '宽度像素',
    height_px INT UNSIGNED DEFAULT NULL COMMENT '高度像素',
    duration_ms INT UNSIGNED DEFAULT NULL COMMENT '视频时长，图片为NULL',
    audit_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (media_id),
    UNIQUE KEY uk_media_position (content_id, sort_order),
    CONSTRAINT fk_media_content FOREIGN KEY (content_id) REFERENCES marketplace_content (content_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_media_type CHECK (media_type IN (1,2)),
    CONSTRAINT ck_media_audit CHECK (audit_status IN (0,1,2)),
    CONSTRAINT ck_media_processing CHECK (processing_status IN (0,1,2)),
    CONSTRAINT ck_media_ready CHECK (processing_status<>1 OR (media_url IS NOT NULL AND CHAR_LENGTH(TRIM(media_url))>0
        AND (media_type=1 OR (thumbnail_key IS NOT NULL AND thumbnail_url IS NOT NULL AND duration_ms IS NOT NULL AND duration_ms>0)))),
    CONSTRAINT ck_media_dimensions CHECK ((width_px IS NULL OR width_px>0) AND (height_px IS NULL OR height_px>0)),
    CONSTRAINT ck_media_image_duration CHECK (media_type<>1 OR duration_ms IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子多图及视频；调序须事务处理唯一键冲突';

CREATE TABLE marketplace_availability (
    availability_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '可约窗口ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT 'coser ID，全帖子共享此排期',
    window_start_at DATETIME(3) NOT NULL COMMENT '开放窗口开始UTC，含此时刻',
    window_end_at DATETIME(3) NOT NULL COMMENT '开放窗口结束UTC，不含此时刻',
    timezone_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布窗口时的IANA时区快照',
    availability_status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1开放 2关闭；关闭不自动取消已有预约',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '窗口版本，排期修改时递增',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (availability_id),
    UNIQUE KEY uk_availability_author (availability_id, author_id),
    KEY idx_availability_search (author_id, availability_status, window_start_at, window_end_at),
    CONSTRAINT fk_availability_author FOREIGN KEY (author_id) REFERENCES marketplace_author (author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_availability_range CHECK (window_start_at < window_end_at),
    CONSTRAINT ck_availability_minute CHECK (SECOND(window_start_at)=0 AND MICROSECOND(window_start_at)=0 AND SECOND(window_end_at)=0 AND MICROSECOND(window_end_at)=0),
    CONSTRAINT ck_availability_status CHECK (availability_status IN (1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='具体日期的可约窗口，非订单占用，不用一个is_booked字段管理整窗';

CREATE TABLE marketplace_order (
    order_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部订单ID',
    order_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务订单号',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '单主用户ID',
    seller_author_id BIGINT UNSIGNED NOT NULL COMMENT '服务coser ID',
    service_content_id BIGINT UNSIGNED NOT NULL COMMENT '被购买委托帖ID',
    author_role_id BIGINT UNSIGNED NOT NULL COMMENT '本单委托角色ID；与所选帖及coser一致',
    ip_snapshot VARCHAR(100) NOT NULL COMMENT '成交IP名称快照，不随角色清单修改',
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '买家下单幂等键',
    service_version INT UNSIGNED NOT NULL COMMENT '委托帖版本快照',
    service_title_snapshot VARCHAR(200) NOT NULL COMMENT '委托标题快照',
    character_snapshot VARCHAR(100) NOT NULL COMMENT '成交角色名称快照，不随角色清单修改',
    seller_name_snapshot VARCHAR(100) NOT NULL COMMENT 'coser展示名称快照',
    service_terms_snapshot TEXT NOT NULL COMMENT '完整服务内容、费用及取消规则快照，不随帖子变化',
    media_keys_snapshot JSON DEFAULT NULL COMMENT '成交媒体对象键数组；权威定位依据，对象按争议及隐私策略保留',
    media_urls_snapshot JSON DEFAULT NULL COMMENT '成交媒体稳定访问入口数组；与对象键数组同序，不能绕过鉴权',
    cover_url_snapshot VARCHAR(2048) DEFAULT NULL COMMENT '成交封面稳定访问入口快照；域名迁移可由对象键重新解析',
    service_mode_snapshot TINYINT UNSIGNED NOT NULL COMMENT '1线下 2线上',
    service_city_snapshot VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '服务城市快照',
    meeting_location_ciphertext VARBINARY(2048) DEFAULT NULL COMMENT '约定精确地点的加密内容；严格限制读取权限',
    buyer_requirements TEXT NOT NULL COMMENT '本次委托需求',
    pricing_type_snapshot TINYINT UNSIGNED NOT NULL COMMENT '本版固定1按小时；成交定价类型快照',
    unit_price_snapshot DECIMAL(18,4) NOT NULL COMMENT '成交单价快照',
    duration_minutes SMALLINT UNSIGNED NOT NULL COMMENT '本单服务时长分钟；一期不允许改期或改时长',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单币种，金额为主单位',
    currency_scale TINYINT UNSIGNED NOT NULL COMMENT '币种金额小数位快照',
    original_amount DECIMAL(18,4) NOT NULL COMMENT '按小时=单价乘分钟数除60并按币种精度舍入；按次=单价',
    discount_amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    payable_amount DECIMAL(18,4) NOT NULL COMMENT '应付金额=原价减优惠，必须大于0',
    paid_amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '确认实收，不随退款清零',
    refunded_amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已成功退款累计金额，可部分退或全额退',
    order_status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '10待付款 20待coser确认 30待服务 40服务中 50待单主确认完成 60已完成 70已取消 80取消退款已处理完成',
    payment_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未支付成功 1已付未退 2已部分退款 3已全额退款',
    refund_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0无应退 1退款处理中 2应退金额已全部退完 3退款失败待重试',
    payment_provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '支付渠道及必要商户命名空间',
    payment_transaction_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '本单唯一成功支付流水号',
    payment_timeout_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 15 COMMENT '付款期限快照，分钟',
    confirm_timeout_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 120 COMMENT '付款后coser确认期限快照，分钟',
    min_lead_minutes_snapshot INT UNSIGNED NOT NULL DEFAULT 1440 COMMENT '最少提前预约分钟数快照',
    cancel_policy_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'COS_CANCEL_V1' COMMENT '取消政策版本；该版本48/24小时及50%比例不可变',
    free_cancel_before_hours SMALLINT UNSIGNED NOT NULL DEFAULT 48 COMMENT '单主取消已确认预约：距服务开始至少此小时数退100%',
    partial_cancel_before_hours SMALLINT UNSIGNED NOT NULL DEFAULT 24 COMMENT '小于全退阈值且至少此小时数按部分比例退',
    partial_refund_rate_bps SMALLINT UNSIGNED NOT NULL DEFAULT 5000 COMMENT '部分退款比例，基点，5000为50%',
    cancellation_requested_at DATETIME(3) DEFAULT NULL COMMENT '服务端首次成功受理有效取消请求时间UTC，幂等重试不重置',
    cancel_actor_type TINYINT UNSIGNED DEFAULT NULL COMMENT '1单主 2coser 3系统 4平台人工',
    cancelled_from_order_status TINYINT UNSIGNED DEFAULT NULL COMMENT '取消生效前的订单状态快照',
    cancel_reason_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'BUYER_CANCEL/COSER_REJECT/CONFIRM_TIMEOUT/COSER_CANCEL/PAY_TIMEOUT/LATE_PAYMENT等',
    cancel_base_start_at DATETIME(3) DEFAULT NULL COMMENT '退款计算依据的预约服务开始时间快照，不是缓冲开始时间',
    cancel_refund_rate_bps SMALLINT UNSIGNED DEFAULT NULL COMMENT '本次取消核定比例基点；0/5000/10000，人工例外需留痕',
    refund_amount_due DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '核定累计应退款，不等于已经到账',
    cancellation_fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '取消后不予退还金额；不等于已结算给coser',
    completion_submitted_at DATETIME(3) DEFAULT NULL COMMENT 'coser提交服务完成的时间',
    completion_review_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0无人工处理 1待人工处理 2人工处理完成',
    completion_resolution_type TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未完成 1单主确认完成 2平台人工确认完成；无自动完成',
    cancel_reason VARCHAR(500) DEFAULT NULL COMMENT '取消或拒单原因',
    refund_reason VARCHAR(500) DEFAULT NULL COMMENT '退款原因',
    payment_expires_at DATETIME(3) NOT NULL COMMENT '付款截止时间UTC，与预约占档期限同步',
    paid_at DATETIME(3) DEFAULT NULL COMMENT '成功付款时间UTC',
    completed_at DATETIME(3) DEFAULT NULL COMMENT '订单完成时间UTC',
    cancelled_at DATETIME(3) DEFAULT NULL COMMENT '取消时间UTC',
    refunded_at DATETIME(3) DEFAULT NULL COMMENT '最近一次成功退款时间UTC',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态更新乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_request (buyer_id, request_key),
    UNIQUE KEY uk_order_payment (payment_provider, payment_transaction_no),
    UNIQUE KEY uk_order_participants (order_id, buyer_id, seller_author_id),
    UNIQUE KEY uk_order_author (order_id, seller_author_id),
    KEY idx_order_buyer (buyer_id, created_at, order_id),
    KEY idx_order_seller (seller_author_id, created_at, order_id),
    KEY idx_order_content (service_content_id, seller_author_id),
    KEY idx_order_expiry (order_status, payment_expires_at, order_id),
    CONSTRAINT fk_order_buyer FOREIGN KEY (buyer_id) REFERENCES marketplace_user (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_seller FOREIGN KEY (seller_author_id) REFERENCES marketplace_author (author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_content FOREIGN KEY (service_content_id, seller_author_id) REFERENCES marketplace_content (content_id, author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_not_self CHECK (buyer_id <> seller_author_id),
    CONSTRAINT ck_order_status CHECK (order_status IN (10,20,30,40,50,60,70,80) AND payment_status IN (0,1,2,3) AND refund_status IN (0,1,2,3)),
    CONSTRAINT ck_order_mode CHECK (service_mode_snapshot IN (1,2) AND (service_mode_snapshot=2 OR (service_city_snapshot IS NOT NULL AND CHAR_LENGTH(TRIM(service_city_snapshot))>0))),
    CONSTRAINT ck_order_quote CHECK (
        duration_minutes > 0 AND unit_price_snapshot > 0 AND currency_scale BETWEEN 0 AND 4
        AND unit_price_snapshot = ROUND(unit_price_snapshot,currency_scale)
        AND ((pricing_type_snapshot=1 AND original_amount=ROUND(unit_price_snapshot*duration_minutes/60,currency_scale))
             OR (pricing_type_snapshot=2 AND original_amount=unit_price_snapshot))
    ),
    CONSTRAINT ck_order_amount CHECK (original_amount>0 AND discount_amount>=0 AND discount_amount<original_amount
        AND discount_amount=ROUND(discount_amount,currency_scale) AND payable_amount=original_amount-discount_amount
        AND paid_amount>=0 AND refunded_amount>=0 AND refunded_amount<=paid_amount),
    CONSTRAINT ck_order_payment_pair CHECK ((payment_provider IS NULL AND payment_transaction_no IS NULL) OR (payment_provider IS NOT NULL AND payment_transaction_no IS NOT NULL)),
    CONSTRAINT ck_order_payment_money CHECK (
        (payment_status=0 AND paid_amount=0 AND refunded_amount=0 AND paid_at IS NULL)
        OR (payment_status=1 AND paid_amount=payable_amount AND refunded_amount=0 AND paid_at IS NOT NULL AND payment_provider IS NOT NULL AND payment_transaction_no IS NOT NULL)
        OR (payment_status=2 AND paid_amount=payable_amount AND refunded_amount>0 AND refunded_amount<paid_amount AND paid_at IS NOT NULL AND payment_provider IS NOT NULL AND payment_transaction_no IS NOT NULL)
        OR (payment_status=3 AND paid_amount=payable_amount AND refunded_amount=paid_amount AND paid_at IS NOT NULL AND payment_provider IS NOT NULL AND payment_transaction_no IS NOT NULL)
    ),
    CONSTRAINT ck_order_refund_amounts CHECK (refund_amount_due>=0 AND refund_amount_due<=paid_amount AND refunded_amount<=refund_amount_due
        AND refund_amount_due=ROUND(refund_amount_due,currency_scale) AND refunded_amount=ROUND(refunded_amount,currency_scale)
        AND cancellation_fee_amount>=0 AND cancellation_fee_amount<=paid_amount AND cancellation_fee_amount=ROUND(cancellation_fee_amount,currency_scale)),
    CONSTRAINT ck_order_refund_progress CHECK (
        (refund_status=0 AND refund_amount_due=0 AND refunded_amount=0)
        OR (refund_status IN (1,3) AND refund_amount_due>refunded_amount)
        OR (refund_status=2 AND refund_amount_due>0 AND refunded_amount=refund_amount_due)
    ),
    CONSTRAINT ck_order_refunded_time CHECK ((refunded_amount=0 AND refunded_at IS NULL) OR (refunded_amount>0 AND refunded_at IS NOT NULL)),
    CONSTRAINT ck_order_cancel_accounting CHECK (cancelled_at IS NULL OR refund_amount_due+cancellation_fee_amount=paid_amount),
    CONSTRAINT ck_order_cancel_actor CHECK (cancel_actor_type IS NULL OR cancel_actor_type IN (1,2,3,4)),
    CONSTRAINT ck_order_cancel_rate CHECK (cancel_refund_rate_bps IS NULL OR cancel_refund_rate_bps<=10000),
    CONSTRAINT ck_order_policy CHECK (free_cancel_before_hours>partial_cancel_before_hours AND partial_refund_rate_bps<=10000
        AND (cancel_policy_code<>'COS_CANCEL_V1' OR (free_cancel_before_hours=48 AND partial_cancel_before_hours=24 AND partial_refund_rate_bps=5000))),
    CONSTRAINT ck_order_timeouts CHECK (payment_timeout_minutes>0 AND confirm_timeout_minutes>0 AND min_lead_minutes_snapshot>=1440),
    CONSTRAINT ck_order_completion CHECK (completion_review_status IN (0,1,2) AND completion_resolution_type IN (0,1,2)
        AND (order_status<>60 OR (completion_resolution_type IN (1,2) AND completed_at IS NOT NULL))),
    CONSTRAINT ck_order_media_snapshot CHECK (media_keys_snapshot IS NULL OR JSON_TYPE(media_keys_snapshot)='ARRAY'),
    KEY idx_order_role_content (service_content_id, seller_author_id, author_role_id),
    CONSTRAINT fk_order_content_role FOREIGN KEY (service_content_id, seller_author_id, author_role_id) REFERENCES marketplace_content (content_id, author_id, author_role_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_hourly CHECK (pricing_type_snapshot=1),
    CONSTRAINT ck_order_role_names CHECK (CHAR_LENGTH(TRIM(ip_snapshot))>0 AND CHAR_LENGTH(TRIM(character_snapshot))>0),
    CONSTRAINT ck_order_media_urls CHECK (media_urls_snapshot IS NULL OR JSON_TYPE(media_urls_snapshot)='ARRAY'),
    CONSTRAINT ck_order_media_pair CHECK ((media_keys_snapshot IS NULL AND media_urls_snapshot IS NULL)
        OR (media_keys_snapshot IS NOT NULL AND media_urls_snapshot IS NOT NULL AND JSON_LENGTH(media_keys_snapshot)=JSON_LENGTH(media_urls_snapshot)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='COS委托订单、取消政策快照及金额汇总；退款流水与账务另行记录';

CREATE TABLE marketplace_appointment (
    appointment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约记录ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '关联订单ID',
    author_id BIGINT UNSIGNED NOT NULL COMMENT 'coser ID，必须等于订单卖家及窗口所属作者',
    availability_id BIGINT UNSIGNED NOT NULL COMMENT '本次预约所在开放窗口',
    service_start_at DATETIME(3) NOT NULL COMMENT '约定服务开始UTC，含此时刻',
    service_end_at DATETIME(3) NOT NULL COMMENT '约定服务结束UTC，不含此时刻',
    buffer_before_minutes SMALLINT UNSIGNED NOT NULL COMMENT '服务前缓冲快照',
    buffer_after_minutes SMALLINT UNSIGNED NOT NULL COMMENT '服务后缓冲快照',
    occupied_start_at DATETIME(3) NOT NULL COMMENT '服务开始减前缓冲，冲突判断用此字段',
    occupied_end_at DATETIME(3) NOT NULL COMMENT '服务结束加后缓冲，冲突判断用此字段',
    timezone_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '展示时区快照',
    appointment_status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '10待付款占档 20已付款待coser确认 30已确认待服务 40服务中 50已结束 60已取消 70超时失效',
    hold_expires_at DATETIME(3) NOT NULL COMMENT '最初付款占档期限UTC；状态变更后保留原值用于追溯',
    confirm_expires_at DATETIME(3) DEFAULT NULL COMMENT '付款后coser确认截止UTC，必须早于服务开始',
    confirmed_at DATETIME(3) DEFAULT NULL COMMENT 'coser确认时间UTC',
    released_at DATETIME(3) DEFAULT NULL COMMENT '取消或超时释放时间；一期不开改期',
    actual_start_at DATETIME(3) DEFAULT NULL COMMENT '实际开始，仅作履约记录，不自动延长占用',
    actual_end_at DATETIME(3) DEFAULT NULL COMMENT '实际结束，仅作履约记录',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '并发更新版本号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (appointment_id),
    UNIQUE KEY uk_appointment_order (order_id),
    KEY idx_appointment_order_author (order_id, author_id),
    KEY idx_appointment_window_author (availability_id, author_id),
    KEY idx_appointment_conflict (author_id, appointment_status, occupied_start_at, occupied_end_at),
    KEY idx_appointment_hold_expiry (appointment_status, hold_expires_at, author_id),
    KEY idx_appointment_confirm_expiry (appointment_status, confirm_expires_at, author_id),
    CONSTRAINT fk_appointment_order FOREIGN KEY (order_id, author_id) REFERENCES marketplace_order (order_id, seller_author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_appointment_window FOREIGN KEY (availability_id, author_id) REFERENCES marketplace_availability (availability_id, author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_appointment_status CHECK (appointment_status IN (10,20,30,40,50,60,70)),
    CONSTRAINT ck_appointment_range CHECK (occupied_start_at<=service_start_at AND service_start_at<service_end_at AND service_end_at<=occupied_end_at),
    CONSTRAINT ck_appointment_minute CHECK (SECOND(service_start_at)=0 AND MICROSECOND(service_start_at)=0 AND SECOND(service_end_at)=0 AND MICROSECOND(service_end_at)=0 AND SECOND(occupied_start_at)=0 AND MICROSECOND(occupied_start_at)=0 AND SECOND(occupied_end_at)=0 AND MICROSECOND(occupied_end_at)=0),
    CONSTRAINT ck_appointment_buffers CHECK (TIMESTAMPDIFF(MINUTE,occupied_start_at,service_start_at)=buffer_before_minutes AND TIMESTAMPDIFF(MINUTE,service_end_at,occupied_end_at)=buffer_after_minutes),
    CONSTRAINT ck_appointment_deadlines CHECK (hold_expires_at<=occupied_start_at AND hold_expires_at>created_at AND (confirm_expires_at IS NULL OR confirm_expires_at<=occupied_start_at)),
    CONSTRAINT ck_appointment_confirm CHECK ((appointment_status=20 AND confirm_expires_at IS NOT NULL) OR appointment_status<>20),
    CONSTRAINT ck_appointment_confirmed CHECK ((appointment_status IN (30,40,50) AND confirmed_at IS NOT NULL) OR appointment_status NOT IN (30,40,50)),
    CONSTRAINT ck_appointment_release CHECK ((appointment_status IN (60,70) AND released_at IS NOT NULL) OR (appointment_status IN (10,20,30,40,50) AND released_at IS NULL)),
    CONSTRAINT ck_appointment_actual CHECK (actual_end_at IS NULL OR (actual_start_at IS NOT NULL AND actual_end_at>=actual_start_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一单一条预约；一期时间不可修改；coser级事务锁防撞档';

CREATE TABLE marketplace_review (
    review_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '已完成订单，每单一次评价',
    buyer_id BIGINT UNSIGNED NOT NULL COMMENT '评价单主，等于订单买家',
    seller_author_id BIGINT UNSIGNED NOT NULL COMMENT '被评价coser，等于订单卖家',
    service_score TINYINT UNSIGNED NOT NULL COMMENT '服务总评分整数1至5，无默认好评',
    review_text TEXT NOT NULL COMMENT '评价正文',
    image_keys JSON DEFAULT NULL COMMENT '评价图片对象键数组，JSON元素结构由应用校验',
    image_urls JSON DEFAULT NULL COMMENT '评价图片稳定HTTPS访问入口数组；与image_keys同序同长度',
    is_anonymous TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0正常展示 1前台匿名，不暴露单主ID',
    review_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1公开 2隐藏 3逻辑删除',
    audit_reason VARCHAR(500) DEFAULT NULL COMMENT '内部审核原因',
    author_reply TEXT COMMENT 'coser当前回复',
    replied_at DATETIME(3) DEFAULT NULL COMMENT '回复时间UTC',
    reply_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0无回复 1待审核 2公开 3隐藏',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '应用递增版本号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_review_order (order_id),
    KEY idx_review_participants (order_id, buyer_id, seller_author_id),
    KEY idx_review_author (seller_author_id, review_status, created_at, review_id),
    KEY idx_review_buyer (buyer_id, created_at, review_id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id, buyer_id, seller_author_id) REFERENCES marketplace_order (order_id, buyer_id, seller_author_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_review_score CHECK (service_score BETWEEN 1 AND 5),
    CONSTRAINT ck_review_status CHECK (is_anonymous IN (0,1) AND review_status IN (0,1,2,3) AND reply_status IN (0,1,2,3)),
    CONSTRAINT ck_review_reply CHECK ((reply_status=0 AND author_reply IS NULL AND replied_at IS NULL) OR (reply_status IN (1,2,3) AND author_reply IS NOT NULL AND replied_at IS NOT NULL)),
    CONSTRAINT ck_review_images CHECK (image_keys IS NULL OR JSON_TYPE(image_keys)='ARRAY'),
    CONSTRAINT ck_review_image_urls CHECK (image_urls IS NULL OR JSON_TYPE(image_urls)='ARRAY'),
    CONSTRAINT ck_review_image_pair CHECK ((image_keys IS NULL AND image_urls IS NULL)
        OR (image_keys IS NOT NULL AND image_urls IS NOT NULL AND JSON_LENGTH(image_keys)=JSON_LENGTH(image_urls)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='COS委托完成后的单主评价与coser回复';

CREATE TABLE marketplace_refund (
    refund_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '退款记录ID',
    refund_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外退款业务号，也是向支付渠道重试的幂等标识',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '所属订单',
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同订单同一业务退款请求的幂等键',
    reason_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款原因编码',
    trigger_actor_type TINYINT UNSIGNED NOT NULL COMMENT '1单主 2coser 3系统 4平台人工',
    trigger_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '实际操作用户；系统或人工服务账号可为NULL',
    cancellation_requested_at DATETIME(3) DEFAULT NULL COMMENT '本次退款对应的取消受理时间快照',
    service_start_snapshot DATETIME(3) DEFAULT NULL COMMENT '核算依据的服务开始时间UTC',
    cancel_policy_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次核算政策版本快照',
    calculation_base_amount DECIMAL(18,4) NOT NULL COMMENT '核算基数：本次对应的真实已支付金额',
    refund_rate_bps SMALLINT UNSIGNED NOT NULL COMMENT '核定比例，10000为100%，5000为50%，不创建0金额退款单',
    refund_amount DECIMAL(18,4) NOT NULL COMMENT '本退款单金额，币种主单位',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '与订单币种一致',
    currency_scale TINYINT UNSIGNED NOT NULL COMMENT '币种金额小数位快照',
    refund_status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '10待提交 20处理中 30成功 40明确失败可重试 50已关闭',
    payment_provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款渠道命名空间，与原支付一致',
    provider_refund_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '渠道退款流水号，非本地构造',
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建退款请求时间UTC',
    submitted_at DATETIME(3) DEFAULT NULL COMMENT '首次提交渠道时间UTC',
    succeeded_at DATETIME(3) DEFAULT NULL COMMENT '渠道确认退款成功时间UTC',
    failed_at DATETIME(3) DEFAULT NULL COMMENT '最近明确失败时间，超时未知不记失败',
    failure_code VARCHAR(100) DEFAULT NULL COMMENT '渠道明确失败原因码',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数；同一退款不新建第二个请求',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '回调与主动查询并发版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间UTC',
    PRIMARY KEY (refund_id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_request (order_id, request_key),
    UNIQUE KEY uk_refund_provider (payment_provider, provider_refund_no),
    KEY idx_refund_retry (refund_status, updated_at, refund_id),
    KEY idx_refund_actor (trigger_user_id),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES marketplace_order (order_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_refund_actor FOREIGN KEY (trigger_user_id) REFERENCES marketplace_user (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_refund_actor CHECK (trigger_actor_type IN (1,2,3,4)),
    CONSTRAINT ck_refund_state CHECK (refund_status IN (10,20,30,40,50)),
    CONSTRAINT ck_refund_amount CHECK (calculation_base_amount>0 AND refund_rate_bps BETWEEN 1 AND 10000 AND currency_scale BETWEEN 0 AND 4
        AND refund_amount>0 AND refund_amount<=calculation_base_amount
        AND refund_amount=ROUND(calculation_base_amount*refund_rate_bps/10000,currency_scale)),
    CONSTRAINT ck_refund_success CHECK ((refund_status=30 AND succeeded_at IS NOT NULL AND provider_refund_no IS NOT NULL) OR (refund_status<>30 AND succeeded_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支持部分或全额退款的业务流水；与渠道结果对账，不是完整资金账本';

CREATE TABLE marketplace_order_event (
    event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '所属订单',
    event_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单内唯一事件幂等键',
    event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CREATE/PAY_SUCCESS/COSER_ACCEPT/CANCEL/REFUND_SUCCESS/COMPLETE/MANUAL_REVIEW等',
    actor_type TINYINT UNSIGNED NOT NULL COMMENT '1单主 2coser 3系统 4平台人工',
    actor_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人用户ID，系统处理可为NULL',
    from_order_status TINYINT UNSIGNED DEFAULT NULL COMMENT '变更前订单状态',
    to_order_status TINYINT UNSIGNED DEFAULT NULL COMMENT '变更后订单状态',
    reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作原因编码',
    details_json JSON DEFAULT NULL COMMENT '最小必要审计信息：退款核算、前后状态、证据引用，不写明文隐私或支付密钥',
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间UTC',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_event_idempotency (order_id, event_key),
    KEY idx_event_order_time (order_id, occurred_at, event_id),
    KEY idx_event_actor (actor_user_id),
    CONSTRAINT fk_event_order FOREIGN KEY (order_id) REFERENCES marketplace_order (order_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_user_id) REFERENCES marketplace_user (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_event_actor CHECK (actor_type IN (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单追加式操作日志；应用与权限层禁止普通流程更新或删除历史事件';

-- ===================== 应用事务与业务规则（必需，不由DDL自动执行） =====================
-- 1. 统一作者行锁：所有下单、支付、确认、取消、回收、退款汇总、排期编辑先SELECT author FOR UPDATE。
--    后锁相关窗口/帖子、订单、预约及退款单；同类记录按ID升序。禁止相反顺序。
--    不同coser可并发，同一coser涉及档期的写入串行化。冲突查询使用当前读，不读旧快照。
-- 2. 下单只接受公开上架且审核通过的委托帖；排期窗口开放且包含服务时间和前后缓冲。
--    从服务端成功受理下单到service_start_at至少24小时；选时和时长符合步长、最短/最长时长。
--    必须留足15分钟付款+2小时确认，并在occupied_start_at前结束确认，不满足则拒绝下单。
--    payment_expires_at=创建订单时的数据库当前时间+15分钟；appointment.hold_expires_at与其一致。
--    下单读取并固化报价、角色、服务范围、取消政策参数；保存审计事件；两方账号均正常。
--    重试使用(buyer_id,request_key)并比对完整载荷；刷新不延长付款期限；取消后重下是新订单。
-- 3. 有效占用：预约状态30/40/50，或10且hold_expires_at>当前时间，或20且confirm_expires_at>当前时间。
--    同coser跨帖子、跨窗口判断：old.occupied_start_at<new.occupied_end_at AND old.occupied_end_at>new.occupied_start_at。
--    无缓冲首尾相接不冲突；已结束记录仍保留到occupied_end_at的缓冲；过去记录不影响未来区间。
--    后台扫描及新请求在作者锁下回收过期占用，订单同步取消；已付款则生成全额退款请求。
-- 4. 支付成功以渠道验签/主动查询为准，核对金额币种；外部网络调用在锁事务外完成。
--    锁内确认订单/预约仍有效后订单10->20、预约10->20，confirm_expires_at=渠道可核验paid_at+2小时。
--    如果收到时该确认截止已过、占用已释放或订单已取消，不复活原预约，记录真实收款并走全额退款。
--    coser在期限内确认：订单20->30、预约20->30；拒单/超时取消全退，释放档期。渠道延迟不计单主违约。
-- 5. 取消计算：先验证权限、当前状态，记录首次成功受理有效请求的cancellation_requested_at，幂等重试复用。
--    以预约的service_start_at（不是occupied_start_at、不是本地日期）减取消受理时间，比较精确时差，不按整小时截断。
--    未付款取消：应退0，取消费用0，不创建0元退款流水。
--    单主取消order_status=20：100%退；单主取消order_status=30且服务未开始：
--       delta >= 48小时 => rate=10000；24小时 <= delta < 48小时 => rate=5000；0 < delta < 24小时 => rate=0。
--    service_start_at<=受理时间，即使订单状态仍为30，也禁止走自动取消；转人工核实履约。
--    coser拒单、确认超时或服务前coser取消：100%退；服务开始后争议不自动判责。
--    refund_amount_due=ROUND(paid_amount*rate/10000,currency_scale)；cancellation_fee_amount=paid_amount-refund_amount_due。
--    不予退款仍允许服务前单主明确确认取消并释放档期；页面必须先展示退还与不退金额。
--    取消事务：写取消来源/原因/时间/政策/比例/基准服务开始快照，订单置70，预约置60或70，写事件。
--    应退>0时创建幂等退款流水并置refund_status=1；应退=0不发起退款，refund_status=0。
--    取消生效即可释放未来档期，不等退款到账；过去占用与事件记录保留，不物理删除。
-- 6. 部分退款：订单paid_amount不清零；成功退款回调与订单refunded_amount、payment_status、refund_status同事务更新。
--    refunded_amount仅累计渠道确认成功的流水；防止同一回调重复加款；校验币种、原支付渠道、实际金额。
--    创建/重试退款在订单锁下检查全部成功及在途请求，防止累计退款超过实付或政策核定应退。
--    渠道结果不明保持处理中并主动查询，不能新建另一单退款；同一refund_no重试，未知状态不得随意关闭。
--    所有核定应退到账后refund_status=2；0<refunded_amount<paid_amount时payment_status=2，全退为3。
--    已取消订单核定退款全部处理完后可转80；50%退款也可转80，不能把80解释为全额退完。
--    退款失败不恢复订单/预约。渠道明确失败写原因并重试；异常及人工例外均留事件记录。
-- 7. 禁止改期：一单一条预约，时间/时长/角色等成交内容不可直接更改；无改期接口和改期审批流程。
--    如需换时间，取消原单后新下单，原单仍严格执行原取消政策，不豁免取消费用；新时间不保证仍可约。
-- 8. 履约：订单30->40->50；coser提交完成写completion_submitted_at，预约40->50；
--    单主明确确认后订单50->60，completion_resolution_type=1；未确认提醒并转人工处理，不定时自动完成。
--    平台核实后可记resolution_type=2并写操作依据；平台工单处理时限另定。coser不能替单主确认完成。
--    评价仅允许订单60且操作者=buyer_id、无退款处理中；取消订单（即使不退费）不自动获得评价资格。
--    结算需独立核实资金及争议状态；此脚本不执行打款，不决定取消费用在平台和coser之间的分配。
-- 9. 已引用的可约窗口起止不能直接改：关闭旧窗只影响新单，不自动取消旧预约；缓冲策略只影响新预约。
--    时区用IANA转换，UTC存储；夏令时不存在或重复的本地时间需要明确处理，不能静默漂移。
-- 10. 服务条款与自动计算的政策一致，政策版本不可复用改义；新规则仅影响新订单。
--     当前COS_CANCEL_V1参数固定48/24小时与50%；所有订单新建采用15分钟付款/120分钟确认/至少1440分钟提前。
--     普通账号不允许修改订单政策快照、实付、退款汇总、已生效预约时间、历史事件。
-- 11. 线下精确地址和联系方式加密且最小授权；不公开，匿名评价不能返回buyer_id，媒体需要访问鉴权。
--     头像/图片/视频保留对象键并存稳定HTTPS入口，禁止持久化临时签名URL；成交对象按隐私与争议期限保留。

-- ===================== 补充应用协议 =====================
-- 范围：本版11表仅覆盖账号、coser、多IP角色、作品发布、多图/视频、档期、委托交易、评价及退款。
-- 身份与头像：coser头像通过author_id=user_id读取marketplace_user.avatar_url；不复制到作者表。头像key是对象治理依据，URL是稳定入口；默认头像允许为空。
-- 年龄与性别：年龄0至120为建议数据质量范围，不是交易年龄准入政策；年龄由应用维护。性别使用应用字典。同性委托开关仅保存用户选择，不推断性取向，具体准入判断由产品规则确定，不能静默按性别自动拒单。
-- 角色关联：一个coser多个IP角色；一期单一委托帖对应一个角色。发布校验角色属于作者且开启，复制IP和角色名称为帖快照。下单校验角色仍开启，订单引用同帖同作者同角色并复制帖的IP角色快照。已成交帖禁止更换角色ID；改IP角色语义需新建角色/帖子。
-- 价格来源：本版新委托单只按小时。作者基础价发布时复制到帖的unit_price/currency/currency_scale；改作者价不自动修改已发布帖。显式改帖价递增帖version，仅影响新订单。订单保存帖版本、成交单价和币种，Decimal按币种精度四舍五入，不用浮点。
-- 作品发布：先创建草稿、上传对象、生成媒体及缩略图、视频转码；processing_status=1且media.audit_status=1且未逻辑删除才可展示。帖子audit_status=2后才允许publish_status=1；应用事务校验至少一条合格媒体。JSON数组长度CHECK不能校验每个元素，应用校验字符串和顺序。
-- 封面一致性：封面默认取sort_order最小的未删除、就绪、审核通过媒体：图片取thumbnail（没有则取原图），视频取thumbnail；写入帖子cover_object_key和cover_url。媒体增删/调序/审核变化与封面同步；全部媒体不可用则下架并清封面。
-- 媒体安全：稳定URL是可长期定位的HTTPS受控入口，不是永久公开授权。非公开/下架/删除/封禁/订单快照需服务端鉴权。对象键不复用覆盖；URL不写token。审核待通过内容不可通过猜测URL访问。平台上传校验类型、文件大小、恶意内容；代抓外链须防SSRF。
-- 订单快照：下单同时固化IP、角色、标题、条款、单价、币种、对象键和稳定URL。图片内容使用不可变对象版本；URL失效可由key重建，不能把截图链接当永久证据。快照不复制用户头像以减少个人信息沉淀。
-- 日历语义：未开放时间默认不可接单；开放窗包含整个服务及前后缓冲。同coser开放窗不重叠且相邻窗需按产品规则合并；无单时拆分窗口实现局部关闭，已有预约引用的窗口禁止直接改变边界；关闭旧窗后创建替代窗不取消旧单。所有更改在作者锁下进行。
-- 日历展示：日历显示开放、已占用、不可约；已占用由appointment计算，不写整窗is_booked。实际可选还需满足24小时提前、步长、最短最长时长及15分钟付款和2小时确认。先UTC存储，本地IANA时区渲染，夏令时歧义显式提示。
-- 并发锁：所有作者资料/角色开关/帖子报价/媒体可售状态/档期/下单/支付/确认/取消/回收/退款汇总入口统一先锁author行。再按窗口->角色->帖子->订单->预约->退款的顺序锁定，同类按ID升序。读取可售状态和重叠预约用当前读；外部支付网络调用在事务外。
-- 乐观锁：所有version字段由应用UPDATE ... SET version=version+1 WHERE id=? AND version=?维护，检查影响行数。ON UPDATE CURRENT_TIMESTAMP不递增version。订单事件追加式写入，权限禁止普通流程修改或删除。
-- 注销脱敏：保留user_id和必要交易关联，注销不执行物理删除。按策略清理年龄、性别、头像key/url、简介、最近登录、认证映射并关闭委托；identity_provider/subject受非空和唯一约束，注销时用专用提供方与随机不可逆唯一替代值，不能只清空一个统一值。同步撤销会话和对象访问、清CDN缓存。
-- 全链路隐私：作者展示名、介绍、认证描述、作品与评价、精确地点、订单及媒体快照也可能含个人信息，按法定留存/争议需要区分保留与去标识化；备份和对象存储设保留期限。仅设置closed_at不自动脱敏。不存在已确定的统一留存天数。
-- 上线未决：取消费用归属与结算时点、未确认订单的人工处理SLA仍待业务决定；当前不会自动打款或自动确认完成。身份校验、支付流水对账、工单系统、媒体转码审核均为外部服务或应用能力，并非DDL自动实现。
