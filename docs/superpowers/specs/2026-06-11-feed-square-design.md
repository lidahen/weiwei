---
name: 动态广场功能设计
description: 二手交易平台动态广场的完整设计文档，涵盖发布、流、互动、管控
metadata:
  type: project
  created: 2026-06-11
---

# 动态广场 — 功能设计文档

## 概述

在现有二手交易平台基础上，新增"动态广场"功能，让用户可以发布图文混编动态，支持点赞、评论、转发、关注流等社交互动。

## 需求总结

### 核心功能

| 模块 | 功能 | 优先级 |
|------|------|--------|
| **发布** | 文字+图片(最多9张)+可选绑定商品+可见范围(public/friends/self) | P0 |
| **广场流** | 双Tab切换："最新"(时间倒序)+"推荐"(热度加权) | P0 |
| **互动** | 点赞/取消、评论+楼中楼回复、转发/分享、关注流 | P0 |
| **管控** | 敏感词过滤+人工审核、举报机制、禁言/封号 | P0 |
| **个人主页** | 瀑布流网格展示用户动态 | P1 |

### 技术选型

| 决策 | 方案 | 理由 |
|------|------|------|
| 数据存储 | MySQL + 4张表 | 复用现有twelvet-product模块 |
| 图片存储 | DFS/OSS | 复用现有文件服务 |
| 审核机制 | 敏感词自动过滤+人工审核队列 | 复用商品审核体系 |
| 推荐排序 | MySQL热度分(点赞×1 + 评论×3)加权 | 避免引入复杂消息队列 |
| 前端组件 | Ant Design List/Card | 复用现有UI模式 |

## 数据模型

### 1. feed（动态主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| feed_id | BIGINT PK | 主键 |
| user_id | BIGINT NOT NULL | 发布者ID |
| content | VARCHAR(2000) | 文字内容 |
| images | JSON | 图片URL数组，最多9张 |
| related_product_id | BIGINT NULL | 关联商品ID(可选) |
| visibility | ENUM('public','friends','self') | 可见范围 |
| like_count | INT DEFAULT 0 | 点赞数(冗余) |
| comment_count | INT DEFAULT 0 | 评论数(冗余) |
| forward_count | INT DEFAULT 0 | 转发数(冗余) |
| hot_score | DECIMAL(10,2) DEFAULT 0 | 热度分(推荐算法用) |
| status | ENUM('normal','deleted') DEFAULT 'normal' | 状态 |
| audit_status | ENUM('pending','approved','rejected') | 审核状态 |
| audit_reason | VARCHAR(500) | 审核拒绝原因 |
| original_feed_id | BIGINT NULL | 来源动态ID(转发时用) |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| publish_time | DATETIME | 发布时间 |

### 2. feed_comment（评论表）

| 字段 | 类型 | 说明 |
|------|------|------|
| comment_id | BIGINT PK | 主键 |
| feed_id | BIGINT NOT NULL | 所属动态 |
| user_id | BIGINT NOT NULL | 评论者 |
| reply_to_id | BIGINT NULL | 回复的目标评论ID(NULL=一级评论) |
| reply_to_user_id | BIGINT NULL | 被回复用户ID |
| content | VARCHAR(500) NOT NULL | 评论内容 |
| status | ENUM('normal','deleted') DEFAULT 'normal' | 状态 |
| create_time | DATETIME | 创建时间 |

### 3. feed_like（点赞表）

| 字段 | 类型 | 说明 |
|------|------|--------|
| id | BIGINT PK | 主键 |
| feed_id | BIGINT NOT NULL | 动态ID |
| user_id | BIGINT NOT NULL | 用户ID |
| create_time | DATETIME | 创建时间 |

唯一约束: (feed_id, user_id)

### 4. feed_report（举报表）

| 字段 | 类型 | 说明 |
|------|------|--------|
| report_id | BIGINT PK | 主键 |
| feed_id | BIGINT NOT NULL | 被举报动态 |
| user_id | BIGINT NOT NULL | 举报者 |
| reason_type | VARCHAR(50) | 举报类型(spam/violence/fraud/other) |
| reason_detail | VARCHAR(500) | 详细描述 |
| status | ENUM('pending','handled','dismissed') DEFAULT 'pending' | 处理状态 |
| handle_result | VARCHAR(500) | 处理结果 |
| create_time | DATETIME | 举报时间 |

## API设计

### 后端 Controller (FeedController)

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | /feed/publish | 登录 | 发布动态 |
| GET | /feed/stream | 登录 | 动态流(最新/推荐) |
| GET | /feed/detail/{id} | 公开 | 动态详情+互动状态 |
| POST | /feed/like | 登录 | 点赞/取消点赞 |
| POST | /feed/comment | 登录 | 发表评论/回复 |
| GET | /feed/comments/{id} | 公开 | 评论列表(楼中楼) |
| GET | /feed/my | 登录 | 我的动态列表 |
| GET | /feed/my/self | 登录 | 仅自己可见 |
| GET | /feed/search | 公开 | 搜索动态 |
| PUT | /feed/{id} | 登录/作者 | 编辑动态 |
| DELETE | /feed/{id} | 登录/作者 | 删除动态 |
| POST | /feed/forward | 登录 | 转发动态 |
| POST | /feed/report | 登录 | 举报动态 |

### 前端 API 函数

已存在于 `src/services/marketplace/api.ts`，需补充：
- `feedPublish`
- `feedMyPage`
- `feedForward`
- `feedReport`
- `feedSearch`
- `feedCommentList`
- `feedEdit`

## 前端页面规划

| 页面 | 路由 | 状态 | 改造内容 |
|------|------|------|---------|
| FeedStream | /feed/stream | 占位 | 双Tab流(最新+推荐) |
| FeedDetail | /feed/detail/:feedId | 占位 | 详情+点赞+评论+回复 |
| FeedPublish | /feed/publish | 占位 | 发布表单(文字+图片+商品+可见范围) |

## 管控机制

1. **敏感词过滤**：发布时检查content+images(可选OCR)，命中关键词自动设audit_status=pending
2. **审核队列**：默认全部进入pending，管理员后台审核
3. **举报处理**：运营后台 /admin/marketplace/report 已有举报处理页面，扩展支持动态举报
4. **禁言封号**：复用用户封禁机制
