# 批次 1：正确性与可靠性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 四项独立修复：runner 超时自动退款、Redis 序列化 jsr310 修复（RedisTemplate + CacheManager 双层）、@TwSynchronized AOP 实现、ai-service 启动配置汇总日志。

**Architecture:** 全部为既有模块内的小改动：runner-server 注入 IPayOrderService 补退款；framework-redis 换两个 serializer 的 ObjectMapper 构造并新增锁切面；ai-service config 加纯函数校验器。

**Tech Stack:** Java 17 + Spring Boot 3.5.4 + Redisson 3.30.0；Python 3.11 + pydantic-settings + pytest。

**Spec:** `docs/superpowers/specs/2026-11-03-portfolio-batch1-design.md`

## Global Constraints

- 行为不变量（W3）：i18n/字典缓存的键格式、值格式（含 @class 元数据）、TTL 不变。
- Redisson codec 不动（W 裁决）。
- @TwSynchronized：value 即锁名，不做 SpEL/参数拼接（W2）；tryLock(10, 30, SECONDS)；获取失败抛 TWTException；类内自调用不生效须在注解文档注明。
- ai-service：校验函数接收 `Settings` 参数（非读全局），只输出汇总启动日志，不阻断启动（W1）。
- 代码格式 Spring Java Format；提交信息 conventional commits；涉及 JSR-310 测试断言用 `LocalDateTime`。

## Review Focus

- 用户取消退款与石英退款并发：双方都经 `stateMachine.doCancel` CAS，只有一方能取消成功——但退款调用在取消成功之后，须确认石英路径 refund 失败不抛出（批量循环内 catch）。
- 旧缓存兼容：修复后启动读旧格式缓存（无 jsr310 写入的）不得崩——serializer 换法必须保持 @class 元数据结构。
- @TwSynchronized 锁的 leaseTime 30s：被注解方法执行超 30s 后锁自动释放，并发第二线程可进入——这是设计选择（防死锁），测试须记录该语义而非当 bug 修。
- ai-service 校验函数不得 import Settings 全局实例（便于 pytest）。

---

### Task 1: runner 超时自动退款

**Files:**
- Modify: `backend/twelvet-runner/twelvet-runner-server/src/main/java/com/twelvet/runner/server/service/impl/RunnerTaskServiceImpl.java:198-216`（scanTimeoutPending）+ 构造器注入
- Test: `backend/twelvet-runner/twelvet-runner-server/src/test/java/com/twelvet/runner/server/service/impl/RunnerTaskScanTimeoutRefundTest.java`（新建，含测试目录）

**Interfaces:**
- Consumes: `IPayOrderService.refund(String outTradeNo, BigDecimal amount, String reason)`（payment-api）；`taskMapper.markPayRefundedByTaskId(Long)`（已有）；`taskMapper.selectExpiredPending(LocalDateTime, int)`（taskCols 已含 is_paid/price/pay_out_trade_no，W4）。
- Produces: `scanTimeoutPending()` 返回取消数不变；新增副作用：付费任务取消成功后调 refund+markRefunded。

- [ ] **Step 1: 写失败测试**（mock taskMapper/stateMachine/IPayOrderService）

三个用例：
1. `paidTaskCancelled_refundAndMark`：task isPaid=1、price=5.00、payOutTradeNo="PAY1"、doCancel 返回 true → verify refund("PAY1", 5.00, 含"超时") 且 markPayRefundedByTaskId(id)。
2. `refundFails_taskStaysPaid_noThrow`：refund 返回 false → 不抛异常、markPayRefundedByTaskId 零调用、count 仍 +1。
3. `unpaidTask_noRefund`：isPaid=0 → refund 零调用。

- [ ] **Step 2: 运行确认 RED** — `mvn -pl twelvet-runner/twelvet-runner-server -am test -Dtest=RunnerTaskScanTimeoutRefundTest -Dsurefire.failIfNoSpecifiedTests=false`，Expected: FAIL（构造器缺 IPayOrderService）。

- [ ] **Step 3: 实现** — `scanTimeoutPending` 循环内 doCancel 成功后：`if (isPaid==1 && payOutTradeNo != null)` → try{refund; 成功则 markPayRefundedByTaskId} catch/WARN（不阻断批次）；构造器加 `IPayOrderService payOrderService`（追加在最后）。

- [ ] **Step 4: 运行确认 GREEN** — 同 Step 2（去掉 -Dtest），runner 全部 PASS。

- [ ] **Step 5: Commit** — `fix(runner): 超时未接单任务自动原路退款`

---

### Task 2: Redis 序列化 jsr310 修复（RedisTemplate + CacheManager）

**Files:**
- Modify: `backend/twelvet-framework/twelvet-framework-redis/src/main/java/com/twelvet/framework/configure/RedisTemplateConfiguration.java:27-28`
- Modify: `backend/twelvet-framework/twelvet-framework-redis/src/main/java/com/twelvet/framework/configure/RedisConfig.java:53-59`
- Test: `backend/twelvet-framework/twelvet-framework-redis/src/test/java/com/twelvet/framework/redis/SerializerJsr310Test.java`（新建）

**Interfaces:**
- Produces: 两个 serializer 均可序列化含 LocalDateTime 的对象；值格式保持 @class 元数据。

- [ ] **Step 1: 写失败测试**（纯序列化测试，无 Redis 连接）— 构造与生产同法的 serializer，对 `SysDictData`（set dictCode/dictLabel/createTime=LocalDateTime.now()）序列化→反序列化，断言字段相等。RedisTemplate 层用 `GenericJackson2JsonRedisSerializer`，CacheManager 层用 `Jackson2JsonRedisSerializer`+ObjectMapper（按各自生产构造方式建辅助方法或直接内联构造）。断言序列化产物含 "java.time.LocalDateTime" 字样（jsr310 生效的证据）。

- [ ] **Step 2: RED** — `mvn -pl twelvet-framework/twelvet-framework-redis -am test`，Expected: 序列化抛 InvalidDefinitionException（Java 8 date/time not supported）。

- [ ] **Step 3: 实现** — 两个类各自：`ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());`（RedisTemplate 层：GenericJackson2JsonRedisSerializer 带 @class 元数据的写法是 `om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)`，保留现有语义）；CacheManager 层在现有 om 上加 `registerModule(new JavaTimeModule())`（enableDefaultTyping 保留）。注意：`jackson-datatype-jsr310` 依赖由 spring-boot-starter-web/json 传递提供，若 framework-redis 无则 pom 加（不需要版本号，spring-boot-dependencies 管理）。

- [ ] **Step 4: GREEN** — 同 Step 2，PASS。

- [ ] **Step 5: Commit** — `fix(framework): Redis 序列化注册 JavaTimeModule，修复含 LocalDateTime 对象静默失败`

---

### Task 3: @TwSynchronized AOP 实现

**Files:**
- Create: `backend/twelvet-framework/twelvet-framework-redis/src/main/java/com/twelvet/framework/aspect/TwSynchronizedAspect.java`
- Modify: `backend/twelvet-framework/twelvet-framework-redis/src/main/java/com/twelvet/framework/annotation/TwSynchronized.java`（文档注释补充：value 即锁名/不支持重入/类内自调用不生效/持锁 30s 自动释放）
- Test: `backend/twelvet-framework/twelvet-framework-redis/src/test/java/com/twelvet/framework/aspect/TwSynchronizedAspectTest.java`（新建）

**Interfaces:**
- Produces: `@TwSynchronized("lockName")` 可用于任意 Spring bean 方法；锁 key = value 原样（建议业务方自带前缀防跨业务撞名）。

- [ ] **Step 1: 写失败测试** — 单测用 Mockito mock `RedissonClient`+`RLock`：tryLock 返回 true → 方法执行、finally unlock；tryLock 返回 false → TWTException("操作繁忙，请稍后重试") 且方法未执行、无 unlock；tryLock 抛 InterruptedException → 恢复中断标志并抛 TWTException。

- [ ] **Step 2: RED** — `mvn -pl twelvet-framework/twelvet-framework-redis -am test`，Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现** — `@Aspect @Component`，`@Around("@annotation(twSynchronized)")`：`RLock lock = redissonClient.getLock(twSynchronized.value()); boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);`，finally 中 `if (acquired && lock.isHeldByCurrentThread()) lock.unlock();`。

- [ ] **Step 4: GREEN** — 同 Step 2，PASS。

- [ ] **Step 5: Commit** — `feat(framework): @TwSynchronized 分布式锁 AOP 实现（Redisson）`

---

### Task 4: ai-service 启动配置汇总日志

**Files:**
- Modify: `ai-service/app/config.py`（加模块级函数）
- Modify: `ai-service/app/main.py`（启动时调用，log 一条汇总）
- Test: `ai-service/tests/test_config_validation.py`（新建）

**Interfaces:**
- Produces: `missing_critical_settings(s: Settings) -> list[str]`（纯函数）；main 启动时若有缺失打一条 WARNING 汇总。

- [ ] **Step 1: 写失败测试**（pytest）— `missing_critical_settings(Settings(ai_api_key="", internal_api_token="", embedding_api_key=""))` == ["ai_api_key", "internal_api_token", "embedding_api_key"]；`Settings(ai_api_key="x")` 时 ai_api_key 不在列表。

- [ ] **Step 2: RED** — `cd ai-service && python -m pytest tests/test_config_validation.py -v`，Expected: ImportError（函数不存在）。

- [ ] **Step 3: 实现** — config.py 末尾：`def missing_critical_settings(s: Settings) -> list[str]: return [name for name in ("ai_api_key", "internal_api_token", "embedding_api_key") if not getattr(s, name)]`；main.py 启动处调用，缺失时 `logger.warning("以下关键配置为空（对应功能将降级/不可用）: %s", ", ".join(missing))`。

- [ ] **Step 4: GREEN** — pytest PASS。

- [ ] **Step 5: Commit** — `feat(ai): 启动时汇总缺失的关键配置项并告警`

---

### Task 5: 全量回归 + 设计文档留痕

- [ ] **Step 1:** `mvn test`（全后端）+ `cd ai-service && python -m pytest tests/ -q`，Expected: 双侧零失败。
- [ ] **Step 2:** 设计文档 §7 补实施偏差记录；Commit `docs: 批次1实施记录`。
