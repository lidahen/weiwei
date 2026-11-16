# 批次 1：正确性与可靠性（作品集完善）— 设计文档

> 日期：2026-11-03
> 来源：会话「作品集完善路线图」批次 1；前序：biz_type 收口（2026-11-03）最终审查遗留 + CLAUDE.md 已知问题
> 定位：**作品集项目**——不留功能短板，不需要真实支付

## 1. 目标 / 非目标

**目标（4 项独立修复）：**
1. runner 石英超时取消自动退款（资损风险收口）
2. i18n Redis 层修复（Jackson 序列化 LocalDateTime 失败，静默丢缓存）
3. `@TwSynchronized` 分布式锁 AOP 实现（注解已有、无实现）
4. ai-service 关键配置启动 fail-fast（关键密钥缺失时启动即报错）

**非目标：**
- 真实支付渠道接入；mock 支付保持现状。
- 前端改造（批次 3）；SQL 序列整理（批次 2）。

**反目标：**
- 不得破坏用户取消退款链路（RunnerTaskController:224 既有路径）。
- i18n 修复不得改变缓存语义（键、值、TTL 均不变）。

## 2. 现状核实（2026-11-03 实地）

| 项 | 现状 | 文件 |
|---|---|---|
| runner 超时扫描 | `scanTimeoutPending` 取消后仅记日志（注释自认"当前简化"）；用户取消路径已退款（Controller:224） | `RunnerTaskServiceImpl.java:198-216`、`RunnerQuartzTask.java:34` |
| runner 退款字段 | task 有 `isPaid`/`price`/`payOutTradeNo`；用户取消时 Controller 调 `refund(outTradeNo, price, reason)` 后 `markRefunded(id)` | `RunnerTaskServiceImpl.java:104-118` |
| i18n 缓存 | `RedisUtils`（静态取 `redisTemplate`，`GenericJackson2JsonRedisSerializer` 无 jsr310）；`I18nServiceImpl.initI18n` 在线程池里写 `List<SysDictData>`（含 LocalDateTime）→ `InvalidDefinitionException`，无告警 | `RedisTemplateConfiguration.java:27`、`I18nServiceImpl.java:57-77` |
| @TwSynchronized | 注解已定义（value=锁名，不支持重入），无切面实现，无使用方 | `TwSynchronized.java` |
| ai-service 配置 | 82 项全默认值，密钥空串静默降级；Java 侧 `internal.token` 已按"失败关闭"设计（Python 报错不回退直写） | `ai-service/app/config.py` |

## 3. 设计

### 3.1 runner 超时自动退款

`RunnerTaskServiceImpl.scanTimeoutPending` 取消成功后，对付费任务（`isPaid=1 && payOutTradeNo != null`）：

1. 注入 `IPayOrderService`（`twelvet-payment-api` 依赖 runner-server 已有——Controller 已注入）。
2. 调 `refund(payOutTradeNo, price, "任务超时未接单，自动退款")`。
3. 成功 → `markPayRefundedByTaskId(taskId)`（Task biz_type 收口新增的独立语句，仅 paid→refunded）；失败 → 仅 WARN（不阻断批次），任务保持 paid 状态待人工/重试。
4. 取消失败或已取消的旧任务不退款（幂等：trade_pay refund 入口非 paid 拒绝）。

**改动面**：`RunnerTaskServiceImpl` 注入 + 循环内约 12 行。`selectExpiredPending` 需确认返回字段含 payOutTradeNo/price（实施时核实，不含则改 SQL）。

### 3.2 i18n Redis 层修复

**根因**：`GenericJackson2JsonRedisSerializer` 默认 ObjectMapper 不注册 `jackson-datatype-jsr310`。**三层核实结论（builder check）**：
1. **RedisTemplateConfiguration**（修）：构建 `GenericJackson2JsonRedisSerializer` 时传入注册了 `JavaTimeModule` 的 ObjectMapper（`activateDefaultTyping` 保留——@class 元数据靠它）。
2. **Spring Cache CacheManager（RedisConfig.java:53-59）同修（builder check 发现的平行实例）**：`Jackson2JsonRedisSerializer` + 裸 ObjectMapper 同样无 jsr310；`SysDictTypeServiceImpl.java:60` 的 `@Cacheable(SYS_DICT_KEY)` 缓存的正是 `List<SysDictData>`（LocalDateTime 在 `twelvet-framework-core` 的 `BaseEntity.java:45`）。同样只换 serializer 构造。
3. **Redisson 不动**：默认 `JsonJacksonCodec`，主要写 sa-token 会话（LoginUser），核实后风险面小，动全局 codec 影响面大——保持默认。

**行为不变量**：键格式、值格式（含 @class 类型元数据）、TTL 全部不变——旧缓存可直接读，新写入兼容。

**验证**：单元测试用注册 jsr310 的序列化器对 `SysDictData` 序列化/反序列化往返；启动后 `initI18n` 日志确认无异常、Redis 有键。

### 3.3 @TwSynchronized AOP 实现

- 新增 `TwSynchronizedAspect`（framework-redis 模块，Redisson `RLock`）：
  - 锁名 = `value`（支持 SpEL 占位符？**不做 SpEL，YAGNI**——当前无使用方，加参数拼锁名即可：`value + "://" + 参数 toString`？**也不做**，value 即锁名，文档注明「同类同方法共用锁名时互斥」）。
  - `tryLock(10, 30, SECONDS)`：等锁 10s、持锁 30s 自动释放（防死锁）；获取失败抛 TWTException("操作繁忙，请稍后重试")。
  - 类内自调用不生效（AOP 限制），注解文档注明。
- **不引入使用方**（当前无场景，作品集展示价值即实现本身 + 单测）。
- 测试：单测 mock RLock 或用真实 Redisson（本地 Redis 可用，集成测试直接起 Redisson client）。

### 3.4 ai-service 启动 fail-fast

`config.py` 末尾加 `validate_required()`（或 main.py 启动时校验），只校验**真正影响核心功能**的项：
- `ai_api_key` 为空 → 仅 WARN（LLM 挂了但服务能起，Agent 对话报错信息明确即可）。
- `internal_api_token` 为空 → **WARN 加严**：Java 侧失败关闭，Agent 下单必失败——打 WARN 提示配置不完整，不阻止启动（dev 可不配）。
- 本地开发（`app_port`/localhost database）不做强制。

**从简裁定**：不做分级 required/optional 机制，只加一条启动日志汇总「以下关键配置为空：ai_api_key / internal_api_token / embedding_api_key …」，各功能降级行为不变。**W1 裁定（见 §5）**。

## 4. 测试策略

1. runner：单测覆盖「超时取消→refund 调用→markRefunded」、「refund 失败→不抛、任务保持 paid」、「非付费任务不调 refund」。
2. i18n：`SysDictData` 序列化往返测试（红→绿：修复前序列化抛 InvalidDefinitionException）。
3. @TwSynchronized：集成测试（本地 Redis）——并发 2 线程同注解方法互斥、锁释放、获取失败抛 TWTException。
4. ai-service：validate 函数单测（pytest）：空 key 列表输出正确。

## 5. 设计期 builder check 已裁决项

- **W1（ai-service fail-fast 强度）**：设计期裁定「汇总启动日志」从简方案——不做启动阻断、不做分级机制。理由：本地开发为主（memory：仅在本地开发），阻断启动反而伤开发体验；降级行为已有明确日志。
- **W2（@TwSynchronized 锁名拼接）**：不做 SpEL、不做参数拼接——value 即锁名。当前零使用方，复杂度留给真实场景。
- **W3（i18n 行为不变量）**：键/值/TTL 不变是硬约束，只换序列化器构造方式（对 CacheManager 路径同样适用）。
- **W4（builder check 裁决）**：`selectExpiredPending` 已含 `is_paid/price/pay_out_trade_no`（taskCols），无需改 SQL。
- **W5（builder check 裁决）**：用户取消后退款失败的既有缺口（任务已 cancelled、无重试）**不在本批处理**——本批是「对齐」不是「收口」，记录即可。
- **W6（builder check 裁决）**：切面放 `com.twelvet.framework` 下即可被 `FastApplication` 的 `@ComponentScan` 扫到，无需 AutoConfiguration.imports。

## 7. 实施后补充（2026-11-03 实施完成记录）

**提交**：`90c82d1`（runner 退款）、`47a841d`（Redis jsr310）、`7dc082b`（锁切面）、`8ee165b`（ai-service 告警）。

**实施偏差**：
- **切面异常（Task 3）**：framework-core 已反向依赖 framework-redis，切面抛 TWTException 需 redis→core 依赖，Maven reactor 拒绝循环——改为 redis 模块内定义 `LockAcquireException`，由全局兜底 handler 捕获（HTTP 500 而非 400，语义差异记录在异常 Javadoc）。
- **jsr310 测试方式（Task 2）**：测试调用生产静态工厂（`RedisTemplateConfiguration.jacksonRedisObjectMapper()` / `RedisConfig.cacheObjectMapper()`）断言序列化往返，锁定生产构造；同构 POJO 替代 SysDictData（redis 模块不依赖 core）。
- **ai-service 测试环境（Task 4）**：本机无现成 venv，用系统 Python 3.14（`py -3.14`）跑 pytest（用户确认本机环境）；全量 645 passed。

**验证**：后端 `mvn test` 762 用例 0 失败；ai-service `pytest tests/` 645 passed / 1 skipped。

## 8. 最终审查裁决（2026-11-03）

- **Important（裁决延后，批次 2 候选）**：退款失败无自动重试——scanTimeoutPending 只扫 pending，cancelled 后 refund 失败的任务永久滞留 paid。已在代码注释明确「无自动重试，靠日志人工」；批次 2 可补 `status='cancelled' AND pay_status='paid'` 对账 job。
- **Minor（已修）**：① Javadoc TWTException→LockAcquireException 更正；② NullValueSerializer 边界以 Javadoc 注明（包私有无法直接注册，当前无缓存 null 的调用方）。
- 双退防护、序列化旧缓存兼容、锁泄漏防护、中断恢复——审查均核实正确。
