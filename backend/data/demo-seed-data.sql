-- ============================================================================
-- 演示数据规范化 ②：业务数据归属 / 租户校正 / 状态补齐 / 克隆扩量
--
-- 前置：demo-seed-accounts.sql 已执行（账号与角色已规范）
-- 结果：
--   * 管理员名下不再有商品/订单/动态/互助/交换等业务数据
--   * 所有业务表 tenant_id 与其 owner 账号的租户一致
--   * 商品覆盖 onsale/auditing/sold/offshelf/rejected/draft
--   * 订单覆盖 unpaid/unshipped/shipped/done/closed
--   * 交换广场：物品 onswap/auditing/offshelf + 意向 pending/rejected
--   * 每个演示账号都有可点开的数据
-- 幂等性：以 UPDATE / DELETE 为主；克隆部分使用固定单号（NO-DEMO-* / RUN-DEMO-*
--        / ESC-DEMO-*），重复执行前请先回滚备份
-- ============================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================================
-- 1. 归属迁移：管理员名下的业务数据交给对应演示账号
-- ============================================================================
UPDATE product              SET seller_id   = 102 WHERE seller_id   = 1;
UPDATE trade_order          SET buyer_id    = 101 WHERE buyer_id IN (1, 125);
UPDATE feed                 SET user_id     = 101 WHERE user_id     = 1;
UPDATE feed_like            SET user_id     = 101 WHERE user_id     = 1;
UPDATE feed_comment         SET user_id     = 101 WHERE user_id     = 1;
UPDATE twelve_runner_task   SET publisher_id= 101 WHERE publisher_id= 1;
UPDATE twelve_runner_task   SET assignee_id = 102 WHERE assignee_id = 1;
UPDATE twelve_exchange_item SET owner_id    = 101 WHERE owner_id    = 1;
UPDATE twelve_exchange_offer SET owner_id   = 101 WHERE owner_id    = 1;
UPDATE user_address         SET user_id     = 101 WHERE user_id     = 1;
UPDATE product_favorite     SET user_id     = 101 WHERE user_id     = 1;
UPDATE user_interest_stat   SET user_id     = 101 WHERE user_id     = 1;
UPDATE recommend_impression_log SET user_id = 101 WHERE user_id     = 1;

-- 收藏去重（同一用户同一商品只保留最早一条）
DELETE f1 FROM product_favorite f1
JOIN product_favorite f2
  ON f1.user_id = f2.user_id AND f1.product_id = f2.product_id AND f1.fav_id > f2.fav_id;

-- ============================================================================
-- 2. 脏数据清理
-- ============================================================================
-- 2.1 自买自卖订单（buyer = seller = 101）
DELETE FROM trade_pay   WHERE order_id IN (11, 12);
DELETE FROM trade_order WHERE order_id IN (11, 12);

-- 2.2 测试动态（"tenant isolation test feed" / "rollback mode feed"）与无文字无图动态
CREATE TEMPORARY TABLE tmp_bad_feed AS
SELECT feed_id FROM feed
WHERE feed_id IN (24, 25)
   OR (CHAR_LENGTH(IFNULL(content, '')) = 0 AND IFNULL(images, '[]') IN ('', '[]'));
DELETE FROM feed_product_ref WHERE feed_id IN (SELECT feed_id FROM tmp_bad_feed);
DELETE FROM feed_comment     WHERE feed_id IN (SELECT feed_id FROM tmp_bad_feed);
DELETE FROM feed_like        WHERE feed_id IN (SELECT feed_id FROM tmp_bad_feed);
DELETE FROM feed             WHERE feed_id IN (SELECT feed_id FROM tmp_bad_feed);

-- 2.3 动态-商品关联去重
DELETE r1 FROM feed_product_ref r1
JOIN feed_product_ref r2
  ON r1.feed_id = r2.feed_id AND r1.product_id = r2.product_id AND r1.id > r2.id;

-- 2.4 会话清理：管理员参与的、自己跟自己的、跨租户的
CREATE TEMPORARY TABLE tmp_bad_conv AS
SELECT conv_id FROM im_conversation
WHERE user_a_id = 1 OR user_b_id = 1
   OR user_a_id = user_b_id
   OR (user_a_id = 102 AND user_b_id = 125)
   OR (user_a_id = 125 AND user_b_id = 102);
DELETE FROM im_unread       WHERE conv_id IN (SELECT conv_id FROM tmp_bad_conv);
DELETE FROM im_message      WHERE conv_id IN (SELECT conv_id FROM tmp_bad_conv);
DELETE FROM im_conversation WHERE conv_id IN (SELECT conv_id FROM tmp_bad_conv);

-- ============================================================================
-- 3. 商品规范化（原先的测试脏数据：quota 回归、计费 P0、只有一个"手机"二字）
-- ============================================================================
UPDATE product
SET title          = 'iPad Air 4 64G WiFi 版 95新',
    cat_l1         = 'digital',
    cat_l2         = 'digital-tablet',
    price          = 1899.00,
    original_price = 4399.00,
    description    = '自用一年，屏幕无划痕，附原装充电器与保护壳。',
    cover          = 'https://picsum.photos/seed/p21/600/600',
    images         = '["https://picsum.photos/seed/p21/600/600"]'
WHERE product_id = 21;

UPDATE product
SET title          = '小米13 8+256G 远山蓝',
    cat_l1         = 'digital',
    cat_l2         = 'digital-phone',
    price          = 1299.00,
    original_price = 3999.00,
    description    = '自用一年，无磕碰，功能正常，附原装充电器。',
    cover          = 'https://picsum.photos/seed/p22/600/600',
    images         = '["https://picsum.photos/seed/p22/600/600"]',
    status         = 'auditing'
WHERE product_id = 22;

UPDATE product
SET title          = '考研英语真题解析（2024版）',
    cat_l1         = 'books',
    cat_l2         = 'books-education',
    price          = 39.00,
    original_price = 68.00,
    description    = '做过一半，笔记已擦除，适合二刷。'
WHERE product_id = 23;

UPDATE product
SET title          = 'iPhone 12 128G 白色 9成新',
    price          = 1599.00,
    original_price = 4599.00
WHERE product_id = 26;

-- ============================================================================
-- 4. 订单时间线补齐（done 却没有 shipped_at/done_at 的记录）
-- ============================================================================
UPDATE trade_order
SET shipped_at = DATE_ADD(IFNULL(paid_at, create_time), INTERVAL 1 DAY)
WHERE status IN ('shipped', 'done') AND shipped_at IS NULL;

UPDATE trade_order
SET done_at = DATE_ADD(IFNULL(shipped_at, create_time), INTERVAL 2 DAY)
WHERE status = 'done' AND done_at IS NULL;

-- ============================================================================
-- 5. 租户归属校正：所有业务行的 tenant_id 与其 owner 账号的租户一致
-- ============================================================================
UPDATE product              p JOIN sys_user u ON u.user_id = p.seller_id     SET p.tenant_id = u.tenant_id;
UPDATE trade_order          o JOIN sys_user b ON b.user_id = o.buyer_id      SET o.tenant_id = b.tenant_id;
UPDATE trade_pay            p JOIN trade_order o ON o.order_id = p.order_id  SET p.tenant_id = o.tenant_id;
UPDATE trade_review         r JOIN trade_order o ON o.order_id = r.order_id  SET r.tenant_id = o.tenant_id;
UPDATE feed                 f JOIN sys_user u ON u.user_id = f.user_id       SET f.tenant_id = u.tenant_id;
UPDATE feed_like            l JOIN sys_user u ON u.user_id = l.user_id       SET l.tenant_id = u.tenant_id;
UPDATE twelve_runner_task   t JOIN sys_user u ON u.user_id = t.publisher_id  SET t.tenant_id = u.tenant_id;
UPDATE twelve_exchange_item i JOIN sys_user u ON u.user_id = i.owner_id      SET i.tenant_id = u.tenant_id;
UPDATE twelve_exchange_offer o JOIN sys_user u ON u.user_id = o.owner_id     SET o.tenant_id = u.tenant_id;
UPDATE im_conversation      c JOIN sys_user u ON u.user_id = c.user_a_id     SET c.tenant_id = u.tenant_id;
UPDATE im_message           m JOIN im_conversation c ON c.conv_id = m.conv_id SET m.tenant_id = c.tenant_id;
UPDATE product_favorite     f JOIN sys_user u ON u.user_id = f.user_id       SET f.tenant_id = u.tenant_id;
UPDATE user_interest_stat   s JOIN sys_user u ON u.user_id = s.user_id       SET s.tenant_id = u.tenant_id;
UPDATE recommend_impression_log l JOIN sys_user u ON u.user_id = l.user_id   SET l.tenant_id = u.tenant_id;
UPDATE ai_user_memory       m JOIN sys_user u ON u.user_id = m.user_id       SET m.tenant_id = u.tenant_id;

-- ============================================================================
-- 6. 商品状态补齐（克隆现有商品，覆盖 sold / offshelf / rejected / draft）
--    实现说明：用动态 SQL 取「除主键外的全部列」做 INSERT ... SELECT 克隆。
--    不用 CREATE TABLE AS SELECT + ALTER：MySQL 的 ALTER 会隐式提交当前事务，
--    脚本就不再是原子的（首次执行踩过这个坑，导致部分数据落库）。
-- ============================================================================
SET @pcols = (SELECT GROUP_CONCAT(CONCAT('`', column_name, '`') ORDER BY ordinal_position)
              FROM information_schema.columns
              WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name <> 'product_id');

-- 6.1 已售出：iPhone
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = 'iPhone 13 128G 蓝色 95新（已售）', status = 'sold', stock = 0,
    cover = 'https://picsum.photos/seed/p101/600/600',
    images = '["https://picsum.photos/seed/p101/600/600"]',
    view_count = 156, want_count = 12, chat_count = 7, hot_score = 18.6,
    publish_time = DATE_SUB(NOW(), INTERVAL 40 DAY),
    offshelf_time = DATE_SUB(NOW(), INTERVAL 5 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 40 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.2 已售出：Switch
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 4');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = '任天堂Switch OLED 白色（已售）', status = 'sold', stock = 0,
    cover = 'https://picsum.photos/seed/p102/600/600',
    images = '["https://picsum.photos/seed/p102/600/600"]',
    view_count = 98, want_count = 9, chat_count = 4, hot_score = 12.3,
    publish_time = DATE_SUB(NOW(), INTERVAL 30 DAY),
    offshelf_time = DATE_SUB(NOW(), INTERVAL 2 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 30 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.3 已下架
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 10');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = 'Lemaire 牛角包 焦糖色（已下架）', status = 'offshelf', stock = 0,
    cover = 'https://picsum.photos/seed/p103/600/600',
    images = '["https://picsum.photos/seed/p103/600/600"]',
    view_count = 64, want_count = 5, chat_count = 2, hot_score = 6.8,
    publish_time = DATE_SUB(NOW(), INTERVAL 25 DAY),
    offshelf_time = DATE_SUB(NOW(), INTERVAL 1 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 25 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.4 审核驳回（给运营的「审核」页留一条）
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 14');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = '加拿大鹅羽绒服 M码（待整改）', status = 'rejected', stock = 1,
    audit_reject_reason = '图片与描述不符，请补充实拍图后再提交',
    cover = 'https://picsum.photos/seed/p104/600/600',
    images = '["https://picsum.photos/seed/p104/600/600"]',
    publish_time = DATE_SUB(NOW(), INTERVAL 2 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 2 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.5 草稿（用户尚未提交）
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 11');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = 'Kindle Paperwhite 5 8G（草稿）', status = 'draft', stock = 1,
    cover = 'https://picsum.photos/seed/p105/600/600',
    images = '["https://picsum.photos/seed/p105/600/600"]',
    publish_time = NULL, view_count = 0, want_count = 0, chat_count = 0, hot_score = 0,
    create_time = DATE_SUB(NOW(), INTERVAL 1 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.6 在售补充：相机
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 17');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = '索尼 A7M4 单机身 快门8000', status = 'onsale', stock = 1,
    cover = 'https://picsum.photos/seed/p106/600/600',
    images = '["https://picsum.photos/seed/p106/600/600"]',
    publish_time = DATE_SUB(NOW(), INTERVAL 6 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 6 DAY), update_time = NOW()
WHERE product_id = @pid;

-- 6.7 在售补充：公路车
SET @sql = CONCAT('INSERT INTO product (', @pcols, ') SELECT ', @pcols, ' FROM product WHERE product_id = 9');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @pid = LAST_INSERT_ID();
UPDATE product
SET title = '捷安特 TCR 公路车 S码', status = 'onsale', stock = 1,
    cover = 'https://picsum.photos/seed/p107/600/600',
    images = '["https://picsum.photos/seed/p107/600/600"]',
    publish_time = DATE_SUB(NOW(), INTERVAL 4 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 4 DAY), update_time = NOW()
WHERE product_id = @pid;

-- ============================================================================
-- 7. 订单补齐：closed / unshipped / done 各一单（固定单号，便于识别）
-- ============================================================================
SET @ocols = (SELECT GROUP_CONCAT(CONCAT('`', column_name, '`') ORDER BY ordinal_position)
              FROM information_schema.columns
              WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name <> 'order_id');

-- 7.1 已关闭（超时未支付）
-- 说明：order_no 有唯一索引，克隆时必须在 INSERT 阶段就换成新单号（复制完再改会撞键）。
-- 因此动态拼一个 SELECT 列表，把 order_no 换成字面量，其余列原样复制。
SET @newno = 'NO-DEMO-CLOSED-01';
SET @oselect = (SELECT GROUP_CONCAT(
        CASE WHEN column_name = 'order_no' THEN CONCAT('''', @newno, '''')
             ELSE CONCAT('`', column_name, '`') END
        ORDER BY ordinal_position SEPARATOR ', ')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name <> 'order_id');
SET @sql = CONCAT('INSERT INTO trade_order (', @ocols, ') SELECT ', @oselect, ' FROM trade_order WHERE order_id = 6');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @oid = LAST_INSERT_ID();
UPDATE trade_order
SET status = 'closed', paid_at = NULL,
    expire_at = DATE_SUB(NOW(), INTERVAL 1 DAY),
    create_time = DATE_SUB(NOW(), INTERVAL 3 DAY), update_time = NOW()
WHERE order_id = @oid;

-- 7.2 待发货（买家已付）
SET @newno = 'NO-DEMO-UNSHIP-01';
SET @oselect = (SELECT GROUP_CONCAT(
        CASE WHEN column_name = 'order_no' THEN CONCAT('''', @newno, '''')
             ELSE CONCAT('`', column_name, '`') END
        ORDER BY ordinal_position SEPARATOR ', ')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name <> 'order_id');
SET @sql = CONCAT('INSERT INTO trade_order (', @ocols, ') SELECT ', @oselect, ' FROM trade_order WHERE order_id = 1');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @oid = LAST_INSERT_ID();
UPDATE trade_order
SET product_id = 7, status = 'unshipped',
    paid_at = DATE_SUB(NOW(), INTERVAL 2 DAY), shipped_at = NULL, done_at = NULL,
    create_time = DATE_SUB(NOW(), INTERVAL 2 DAY), update_time = NOW()
WHERE order_id = @oid;

-- 7.3 已完成（附双向评价）
SET @newno = 'NO-DEMO-DONE-01';
SET @oselect = (SELECT GROUP_CONCAT(
        CASE WHEN column_name = 'order_no' THEN CONCAT('''', @newno, '''')
             ELSE CONCAT('`', column_name, '`') END
        ORDER BY ordinal_position SEPARATOR ', ')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name <> 'order_id');
SET @sql = CONCAT('INSERT INTO trade_order (', @ocols, ') SELECT ', @oselect, ' FROM trade_order WHERE order_id = 3');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @oid = LAST_INSERT_ID();
UPDATE trade_order
SET product_id = 13, status = 'done',
    paid_at = DATE_SUB(NOW(), INTERVAL 10 DAY),
    shipped_at = DATE_SUB(NOW(), INTERVAL 9 DAY),
    done_at = DATE_SUB(NOW(), INTERVAL 6 DAY),
    buyer_review_id = NULL, seller_review_id = NULL,
    create_time = DATE_SUB(NOW(), INTERVAL 10 DAY), update_time = NOW()
WHERE order_id = @oid;

-- 7.4 让新订单的金额与快照匹配对应商品
UPDATE trade_order o JOIN product p ON p.product_id = o.product_id
SET o.sku_price = p.price,
    o.total_amount = p.price + IFNULL(o.freight, 0),
    o.product_snapshot = CONCAT('{"title":"', p.title, '","price":', p.price,
                                ',"cover":"', IFNULL(p.cover, ''), '"}')
WHERE o.order_no LIKE 'NO-DEMO-%';

-- 7.5 支付流水
INSERT INTO trade_pay
    (order_id, pay_user_id, out_trade_no, pay_channel, pay_amount, status, paid_at, create_time, update_time, tenant_id)
SELECT o.order_id, o.buyer_id, CONCAT('PAY-DEMO-', o.order_id), 'alipay',
       o.total_amount, 'paid', o.paid_at, o.create_time, NOW(), o.tenant_id
FROM trade_order o
WHERE o.order_no IN ('NO-DEMO-UNSHIP-01', 'NO-DEMO-DONE-01')
  AND NOT EXISTS (SELECT 1 FROM trade_pay p WHERE p.order_id = o.order_id);

-- ============================================================================
-- 8. 评价：给新的已完成订单补双向评价
-- ============================================================================
INSERT INTO trade_review
    (order_id, role, reviewer_id, reviewee_id, overall_score, content, tags, anonymous, create_time, tenant_id, product_id, status)
SELECT o.order_id, 'buyer', o.buyer_id, o.seller_id, 5,
       '东西和描述一致，卖家发货很快，还送了小礼物，好评！',
       '["描述相符","发货快","态度好"]', 0, o.done_at, o.tenant_id, o.product_id, 'normal'
FROM trade_order o
WHERE o.order_no = 'NO-DEMO-DONE-01'
  AND NOT EXISTS (SELECT 1 FROM trade_review r WHERE r.order_id = o.order_id AND r.role = 'buyer');
SET @r_buyer = LAST_INSERT_ID();

INSERT INTO trade_review
    (order_id, role, reviewer_id, reviewee_id, overall_score, content, tags, anonymous, create_time, tenant_id, product_id, status)
SELECT o.order_id, 'seller', o.seller_id, o.buyer_id, 5,
       '爽快买家，付款及时，沟通顺畅，欢迎再来。',
       '["付款快","好沟通"]', 0, o.done_at, o.tenant_id, o.product_id, 'normal'
FROM trade_order o
WHERE o.order_no = 'NO-DEMO-DONE-01'
  AND NOT EXISTS (SELECT 1 FROM trade_review r WHERE r.order_id = o.order_id AND r.role = 'seller');
SET @r_seller = LAST_INSERT_ID();

UPDATE trade_order
SET buyer_review_id = @r_buyer, seller_review_id = @r_seller
WHERE order_no = 'NO-DEMO-DONE-01';

-- ============================================================================
-- 9. 社区动态：买家 5 条（3 通过 / 1 待审 / 1 驳回）+ 卖家 1 条
-- ============================================================================
INSERT INTO feed
    (user_id, content, images, related_product_id, visibility, like_count, comment_count, forward_count,
     hot_score, status, audit_status, audit_reason, create_time, update_time, publish_time, tenant_id)
VALUES
    (101, '搬家清仓～这辆公路车骑了半年，车况很好，有喜欢骑行的邻居吗？',
     '["https://picsum.photos/seed/f101/800/600"]', (SELECT product_id FROM product WHERE title LIKE '捷安特 TCR%' LIMIT 1),
     'public', 12, 3, 1, 8.5, 'normal', 'approved', NULL,
     DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 5 DAY), 1),

    (101, '周末在小区门口摆摊出了几件闲置，邻居们都很好说话，二手真香。',
     '["https://picsum.photos/seed/f102/800/600"]', NULL,
     'public', 25, 6, 2, 15.2, 'normal', 'approved', NULL,
     DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 3 DAY), 1),

    (101, '求推荐附近靠谱的搬家公司，下周六要用，谢谢各位邻居！',
     NULL, NULL,
     'public', 5, 8, 0, 4.1, 'normal', 'approved', NULL,
     DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY), 1),

    (101, '这条动态还在审核中，用来演示内容审核流程。',
     NULL, NULL,
     'public', 0, 0, 0, 0, 'normal', 'pending', NULL,
     DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), DATE_SUB(NOW(), INTERVAL 2 HOUR), 1),

    (101, '这条动态已被驳回，用来演示审核结果展示。',
     NULL, NULL,
     'public', 0, 0, 0, 0, 'normal', 'rejected', '含外部引流链接，已驳回',
     DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW(), DATE_SUB(NOW(), INTERVAL 6 HOUR), 1),

    (102, '新到一批数码好物，都是自用闲置，支持同城面交验货。',
     '["https://picsum.photos/seed/f106/800/600"]', NULL,
     'public', 31, 9, 3, 21.7, 'normal', 'approved', NULL,
     DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 4 DAY), 1);

-- ============================================================================
-- 10. 社区互助：待接单 / 已接单 各一条（发布人=买家，接单人=卖家）
-- ============================================================================
INSERT INTO twelve_runner_task
    (tenant_id, task_no, publisher_id, assignee_id, task_type, title, description,
     pickup_addr, deliver_addr, is_paid, price, status, deadline, pay_status, create_time)
VALUES
    (1, 'RUN-DEMO-0001', 101, NULL, 1, '帮取快递（菜鸟驿站）',
     '两个小件，晚上 7 点后驿站关门，麻烦今天取一下放门口。',
     '科技园南区菜鸟驿站', '科技园南区A栋12楼', 1, 8.00, 'pending',
     DATE_ADD(NOW(), INTERVAL 1 DAY), 'unpaid', DATE_SUB(NOW(), INTERVAL 3 HOUR)),

    (1, 'RUN-DEMO-0002', 101, 102, 2, '代买星巴克咖啡两杯',
     '美式 + 拿铁，去冰，送到 12 楼前台即可。',
     '科技园星巴克', '科技园南区A栋12楼', 1, 12.00, 'accepted',
     DATE_ADD(NOW(), INTERVAL 1 DAY), 'paid', DATE_SUB(NOW(), INTERVAL 1 DAY));

UPDATE twelve_runner_task SET assign_time = DATE_SUB(NOW(), INTERVAL 20 HOUR)
WHERE task_no = 'RUN-DEMO-0002';

-- 10.1 让任务大厅有内容可接：
--   selectHall 的条件是 status='pending' AND deadline > NOW() AND (is_paid=0 OR pay_status='paid')，
--   存量任务既过期又没标支付，所以大厅一直是空的。这里给买家的待接单任务续期并标记「赏金已托管」。
UPDATE twelve_runner_task
SET deadline    = DATE_ADD(NOW(), INTERVAL 3 DAY),
    pay_status  = 'paid',
    is_paid     = 1,
    price       = CASE (id % 5)
                      WHEN 0 THEN 6.00
                      WHEN 1 THEN 8.00
                      WHEN 2 THEN 10.00
                      WHEN 3 THEN 12.00
                      ELSE 15.00
                  END,
    expect_time = DATE_ADD(NOW(), INTERVAL 4 HOUR)
WHERE publisher_id = 101 AND status = 'pending';

-- 10.2 大厅任务的文案规范化（存量里混着 "Review E2E task"、"KWPAGE7X 分页验证"、"12121" 这类测试残留）
UPDATE twelve_runner_task SET title='帮取快递（顺丰驿站）', description='一个小件，麻烦今天取一下送到公司前台。',
       pickup_addr='科技园南区顺丰驿站', deliver_addr='科技园南区A栋12楼', price=8.00 WHERE id = 9;
UPDATE twelve_runner_task SET title='帮带一份猪脚饭', description='食堂二楼，微辣，不要香菜。',
       pickup_addr='科技园食堂二楼', deliver_addr='科技园北区B栋8楼', price=12.00 WHERE id = 13;
UPDATE twelve_runner_task SET title='帮去药店取药', description='已线上下单，报手机号后四位 1002 取药。',
       pickup_addr='科技园大药房', deliver_addr='科技园北区C栋502', price=10.00 WHERE id = 17;
UPDATE twelve_runner_task SET title='傍晚帮遛狗 30 分钟', description='金毛，性格温顺，牵引绳在我家门口。',
       pickup_addr='小区3栋1单元', deliver_addr='小区中央花园', price=12.00 WHERE id = 18;
UPDATE twelve_runner_task SET title='代买日用品（洗发水）', description='海飞丝去屑 750ml，小票留好我报销。',
       pickup_addr='沃尔玛（科技园店）', deliver_addr='小区5栋901', price=6.00 WHERE id = 20;
UPDATE twelve_runner_task SET title='帮搬两个纸箱上楼', description='不重，就是占手，电梯直达。',
       pickup_addr='小区南门快递点', deliver_addr='小区7栋1201', price=8.00 WHERE id = 21;
UPDATE twelve_runner_task SET title='代买奶茶两杯', description='一杯美式不加糖、一杯拿铁去冰。',
       pickup_addr='科技园南区茶百道', deliver_addr='科技园南区A栋12楼', price=15.00 WHERE id = 4;

-- ============================================================================
-- 11. 交换广场：物品 + 意向
-- ============================================================================
-- 11.1 规范化原有物品（管理员测试数据）
UPDATE twelve_exchange_item
SET title = '联想小新 Pro 16 笔记本 R7 32G',
    cat_l1 = 'digital', cat_l2 = 'digital-laptop',
    condition_level = '90', est_value = 3200.00,
    want_cats = '["digital","home"]', want_desc = '想换一台平板或家用投影',
    city = '深圳', location = '南山区科技园',
    description = '自用两年，无磕碰，电池健康 92%，附原装电源。',
    status = 'onswap', publish_time = DATE_SUB(NOW(), INTERVAL 5 DAY)
WHERE id = 1;

-- 11.2 新增物品
INSERT INTO twelve_exchange_item
    (tenant_id, item_no, owner_id, title, cover, images, cat_l1, cat_l2, condition_level, est_value,
     want_cats, want_desc, accept_any, delivery_mode, city, location, description, status, publish_time, hot_score)
VALUES
    (1, 'ESC-DEMO-0001', 101, 'Cherry MX 机械键盘 87 键',
     'https://picsum.photos/seed/e101/600/600', '["https://picsum.photos/seed/e101/600/600"]',
     'digital', 'digital-accessory', '95', 380.00,
     '["digital"]', '想换无线鼠标或耳机', 1, 'face', '深圳', '南山区科技园',
     '红轴，手感很好，带原装键帽。', 'onswap', DATE_SUB(NOW(), INTERVAL 4 DAY), 6.2),

    (1, 'ESC-DEMO-0002', 102, '捷安特 ATX 山地车 27.5 寸',
     'https://picsum.photos/seed/e102/600/600', '["https://picsum.photos/seed/e102/600/600"]',
     'sport', 'sport-bike', '85', 900.00,
     '["sport","digital"]', '想换运动手表', 0, 'face', '深圳', '南山区科技园',
     '骑了两年，刚做过保养，链条新换。', 'onswap', DATE_SUB(NOW(), INTERVAL 3 DAY), 9.4),

    (1, 'ESC-DEMO-0003', 102, '极米 Z6X 投影仪',
     'https://picsum.photos/seed/e103/600/600', '["https://picsum.photos/seed/e103/600/600"]',
     'home', 'home-appliance', '90', 1200.00,
     '["home","digital"]', '想换空气炸锅 + 差价', 1, 'both', '深圳', '南山区科技园',
     '亮度够用，附幕布，适合卧室。', 'auditing', NULL, 0),

    (1, 'ESC-DEMO-0004', 101, '摩飞空气炸锅 4L',
     'https://picsum.photos/seed/e104/600/600', '["https://picsum.photos/seed/e104/600/600"]',
     'home', 'home-kitchen', '95', 260.00,
     '["home"]', '想换书或小家电', 1, 'face', '深圳', '南山区科技园',
     '用了不到 10 次，配件齐全。', 'offshelf', DATE_SUB(NOW(), INTERVAL 20 DAY), 2.1);

-- 11.3 交换意向：卖家 → 买家键盘（待处理）、买家 → 卖家山地车（已拒绝）
INSERT INTO twelve_exchange_offer
    (tenant_id, item_id, owner_id, offeror_id, offer_item_id, offer_snapshot, message, diff_amount, status, expire_time, create_time)
VALUES
    (1, (SELECT id FROM twelve_exchange_item WHERE item_no = 'ESC-DEMO-0001'), 101, 102,
     (SELECT id FROM twelve_exchange_item WHERE item_no = 'ESC-DEMO-0002'),
     '{"title":"捷安特 ATX 山地车 27.5 寸","estValue":900}',
     '我这辆车市价 900，可以补你 300 差价换键盘，考虑下？', 300.00, 'pending',
     DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 HOUR)),

    (1, (SELECT id FROM twelve_exchange_item WHERE item_no = 'ESC-DEMO-0002'), 102, 101,
     (SELECT id FROM twelve_exchange_item WHERE item_no = 'ESC-DEMO-0001'),
     '{"title":"Cherry MX 机械键盘 87 键","estValue":380}',
     '用键盘换你的山地车可以吗？可以补差价。', -500.00, 'rejected',
     DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

UPDATE twelve_exchange_offer SET handle_time = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE status = 'rejected';

UPDATE twelve_exchange_item i
SET i.offer_count = (SELECT COUNT(*) FROM twelve_exchange_offer o WHERE o.item_id = i.id)
WHERE i.item_no IN ('ESC-DEMO-0001', 'ESC-DEMO-0002');

-- ============================================================================
-- 12. IM：买家↔客服、卖家↔客服 两段会话 + 消息
-- ============================================================================
-- 12.1 会话（已存在的跳过）
INSERT INTO im_conversation (user_a_id, user_b_id, create_time, update_time, tenant_id)
SELECT 101, 104, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM im_conversation WHERE user_a_id = 101 AND user_b_id = 104);

INSERT INTO im_conversation (user_a_id, user_b_id, create_time, update_time, tenant_id)
SELECT 102, 104, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM im_conversation WHERE user_a_id = 102 AND user_b_id = 104);

-- 12.2 消息（只在会话消息为空时插入，避免重复）
SET @conv_buyer = (SELECT conv_id FROM im_conversation WHERE user_a_id = 101 AND user_b_id = 104 LIMIT 1);
SET @conv_seller = (SELECT conv_id FROM im_conversation WHERE user_a_id = 102 AND user_b_id = 104 LIMIT 1);

INSERT INTO im_message (conv_id, sender_id, receiver_id, type, content, status, create_time, tenant_id)
SELECT * FROM (
    SELECT @conv_buyer AS conv_id, 101 AS sender_id, 104 AS receiver_id, 'text' AS type,
           '你好，我买的订单一直显示待发货，能帮忙看下吗？' AS content, 'sent' AS status,
           DATE_SUB(NOW(), INTERVAL 2 DAY) AS create_time, 1 AS tenant_id
) t WHERE NOT EXISTS (SELECT 1 FROM im_message m WHERE m.conv_id = @conv_buyer);

INSERT INTO im_message (conv_id, sender_id, receiver_id, type, content, status, create_time, tenant_id)
SELECT * FROM (
    SELECT @conv_buyer AS conv_id, 104 AS sender_id, 101 AS receiver_id, 'text' AS type,
           '您好，刚帮您联系卖家了，今天下午会发出，请留意物流。' AS content, 'sent' AS status,
           DATE_SUB(NOW(), INTERVAL 46 HOUR) AS create_time, 1 AS tenant_id
) t WHERE (SELECT COUNT(*) FROM im_message m WHERE m.conv_id = @conv_buyer) = 1;

INSERT INTO im_message (conv_id, sender_id, receiver_id, type, content, status, create_time, tenant_id)
SELECT * FROM (
    SELECT @conv_buyer AS conv_id, 101 AS sender_id, 104 AS receiver_id, 'text' AS type,
           '好的，谢谢！' AS content, 'sent' AS status,
           DATE_SUB(NOW(), INTERVAL 45 HOUR) AS create_time, 1 AS tenant_id
) t WHERE (SELECT COUNT(*) FROM im_message m WHERE m.conv_id = @conv_buyer) = 2;

INSERT INTO im_message (conv_id, sender_id, receiver_id, type, content, status, create_time, tenant_id)
SELECT * FROM (
    SELECT @conv_seller AS conv_id, 102 AS sender_id, 104 AS receiver_id, 'text' AS type,
           '我这边有两笔订单买家一直没付款，会自动关闭吗？' AS content, 'sent' AS status,
           DATE_SUB(NOW(), INTERVAL 1 DAY) AS create_time, 1 AS tenant_id
) t WHERE NOT EXISTS (SELECT 1 FROM im_message m WHERE m.conv_id = @conv_seller);

INSERT INTO im_message (conv_id, sender_id, receiver_id, type, content, status, create_time, tenant_id)
SELECT * FROM (
    SELECT @conv_seller AS conv_id, 104 AS sender_id, 102 AS receiver_id, 'text' AS type,
           '会的，30 分钟未支付系统会自动关闭订单，不影响您的店铺。' AS content, 'sent' AS status,
           DATE_SUB(NOW(), INTERVAL 23 HOUR) AS create_time, 1 AS tenant_id
) t WHERE (SELECT COUNT(*) FROM im_message m WHERE m.conv_id = @conv_seller) = 1;

-- 12.3 会话最后消息 + 未读
UPDATE im_conversation c
SET c.last_msg_id = (SELECT MAX(m.msg_id) FROM im_message m WHERE m.conv_id = c.conv_id),
    c.last_msg_time = (SELECT MAX(m.create_time) FROM im_message m WHERE m.conv_id = c.conv_id)
WHERE c.conv_id IN (@conv_buyer, @conv_seller);

INSERT INTO im_unread (user_id, conv_id, unread_count, notice_unread, last_read_msg_id)
SELECT 101, @conv_buyer, 1, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM im_unread WHERE user_id = 101 AND conv_id = @conv_buyer);
INSERT INTO im_unread (user_id, conv_id, unread_count, notice_unread, last_read_msg_id)
SELECT 104, @conv_buyer, 0, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM im_unread WHERE user_id = 104 AND conv_id = @conv_buyer);
INSERT INTO im_unread (user_id, conv_id, unread_count, notice_unread, last_read_msg_id)
SELECT 102, @conv_seller, 0, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM im_unread WHERE user_id = 102 AND conv_id = @conv_seller);
INSERT INTO im_unread (user_id, conv_id, unread_count, notice_unread, last_read_msg_id)
SELECT 104, @conv_seller, 1, 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM im_unread WHERE user_id = 104 AND conv_id = @conv_seller);

-- ============================================================================
-- 13. 地址 / 收藏 / 隐私
-- ============================================================================
-- 13.1 地址规范化（原管理员地址城市写成了"南京"，省份却是广东省）
UPDATE user_address
SET receiver = '小美', mobile = '13800001001', province = '广东省', city = '深圳市',
    district = '南山区', detail = '科技园南区B栋 8 楼 802'
WHERE user_id = 101 AND receiver = '李老板';

UPDATE user_address
SET receiver = '清华管理员', mobile = '13800002002', province = '北京市', city = '北京市',
    district = '海淀区', detail = '清华大学东门 1 号楼'
WHERE user_id = 125;

-- 13.2 卖家收货地址
INSERT INTO user_address (user_id, receiver, mobile, province, city, district, detail, is_default, create_time)
SELECT 102, '老张', '13800001002', '广东省', '深圳市', '南山区', '科技园北区 3 栋 501', 1, DATE_SUB(NOW(), INTERVAL 10 DAY)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_address WHERE user_id = 102);

-- 13.3 卖家收藏
INSERT INTO product_favorite (user_id, product_id, original_price, fav_time, tenant_id)
SELECT 102, p.product_id, p.price, DATE_SUB(NOW(), INTERVAL 2 DAY), 1
FROM product p
WHERE p.product_id IN (1, 7)
  AND NOT EXISTS (SELECT 1 FROM product_favorite f WHERE f.user_id = 102 AND f.product_id = p.product_id);

-- 13.4 隐私设置（缺则补，全部默认开启）
INSERT INTO user_privacy (user_id, allow_mobile_search, allow_feed_by_stranger, enable_nearby_recommend)
SELECT 108, 1, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_privacy WHERE user_id = 108);

-- ============================================================================
-- 14. 兴趣画像（推荐系统冷启动数据）
-- ============================================================================
DELETE FROM user_interest_stat WHERE user_id IN (101, 102);
INSERT INTO user_interest_stat (tenant_id, user_id, cat_l1, cat_l2, weight, source, update_time) VALUES
    (1, 101, 'digital', '', 6.0000, 'init', NOW()),
    (1, 101, 'home',    '', 4.0000, 'init', NOW()),
    (1, 101, 'books',   '', 2.0000, 'init', NOW()),
    (1, 102, 'digital', '', 5.0000, 'init', NOW()),
    (1, 102, 'sport',   '', 3.0000, 'init', NOW());

-- ============================================================================
-- 15. 清理空壳租户 pku（无账号、无业务数据；业务行 tenant_id 已在第 5 步归位）
-- ============================================================================
SET @pku_id = (SELECT id FROM sys_tenant WHERE tenant_code = 'pku' LIMIT 1);
DELETE FROM sys_tenant
WHERE id = @pku_id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.tenant_id = @pku_id);

COMMIT;
