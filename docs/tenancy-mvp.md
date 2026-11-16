# 租户最小闭环 MVP（仅商品）

> **2026-10-27 注**：本文引用的 2026-10-21 之前 SQL 初始化脚本已于 2026-10-27 清理；
> 相关表结构已并入 [docs/db 全量基线](db/twelvet-fast_2026-10-21.sql)，新环境按 README 基线导入。

> 范围：一个最小可验证的"租户隔离"实现，仅覆盖 `product` 表。订单 / Feed / IM / 收藏 等表后置。

## 启用步骤

1. **执行 SQL 增量脚本**（在已有 `twelvet-fast` 数据库上）：
   ```bash
   mysql -uroot -p twelvet-fast < backend/data/neighborswap.sql
   # 脚本末尾已包含 sys_tenant 建表 + product 表加 tenant_id
   ```
   增量部分（也可手动执行）：
   ```sql
   CREATE TABLE sys_tenant (
       id BIGINT PRIMARY KEY AUTO_INCREMENT,
       tenant_code VARCHAR(64) NOT NULL UNIQUE,
       tenant_name VARCHAR(128) NOT NULL,
       status CHAR(1) NOT NULL DEFAULT '0',
       create_time DATETIME DEFAULT CURRENT_TIMESTAMP
   );
   INSERT INTO sys_tenant(id, tenant_code, tenant_name) VALUES
       (1,'default','默认社区'),(2,'thu','清华二手'),(3,'pku','北大二手');
   ALTER TABLE product ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
   UPDATE product SET tenant_id = 1;
   CREATE INDEX idx_product_tenant ON product(tenant_id, publish_time);
   ```

2. **开启开关**（`backend/twelvet-admin/src/main/resources/application.yml`）：
   ```yaml
   tenant:
     enabled: true   # 默认 false
   ```

3. **重启服务**，所有 C 端商品接口必须带 Header：
   ```
   X-Tenant-Code: thu
   ```
   - 识别后写入 `TenantContext`（ThreadLocal）
   - 未识别 → 404，不返回 403
   - 请求结束自动清理

## 验证脚本（7 步）

```bash
# 0. 登录（拿 token，登录接口不变）
TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"xxx"}' | jq -r .data.token)

# 1. A 社区发布商品（自动写入 tenant_id=2）
curl -X POST localhost:8080/product/publish \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Code: thu" \
  -d '{"title":"二手教材","price":50,"catL1":"books"}'
# → 返回 productId

# 2. A 社区能列出（看到刚发布的）
curl localhost:8080/product/home/recommend -H "X-Tenant-Code: thu"

# 3. B 社区列出（应该是空，或不含 A 的）
curl localhost:8080/product/home/recommend -H "X-Tenant-Code: pku"

# 4. B 社区直接访问 A 的商品 ID（应 404）
curl localhost:8080/product/detail/<A 的 ID> -H "X-Tenant-Code: pku"

# 5. 跨租户编辑（应失败）
curl -X POST localhost:8080/product/offshelf?productId=<A 的 ID> \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Code: pku"

# 6. 不带 Header（兼容旧调用，归 default 租户）
curl localhost:8080/product/home/recommend
# → 看到 tenant_id=1 的数据

# 7. 带非法 code（应 404）
curl localhost:8080/product/home/recommend -H "X-Tenant-Code: invalid"
```

## 实现要点

### 数据流
```
HTTP 请求
  → TenantFilter（读 X-Tenant-Code → 查 sys_tenant → 放 TenantContext）
  → SaInterceptor（鉴权）
  → Controller
  → Service（读 TenantContext.get()，作为 tenantId 参数传 mapper）
  → Mapper XML（<if test="tenantId != null">AND tenant_id=#{tenantId}</if>）
  → TenantFieldInterceptor（INSERT 时自动从 TenantContext 写入 entity.tenantId）
```

### 关键设计
| 决策 | 选择 | 理由 |
|---|---|---|
| 识别方式 | HTTP Header `X-Tenant-Code` | 简单，未来换子域名只改 Filter |
| 关闭开关 | `tenant.enabled=false` | 零侵入回滚，老代码完全不受影响 |
| 上下文 | ThreadLocal | 同线程；@Async / 定时任务 需显式 set/clear |
| Mapper 过滤 | XML 内 `<if test="tenantId != null">` | MyBatis 非 Plus，无法用拦截器改 SQL |
| 写入隔离 | `TenantFieldInterceptor`（仅 INSERT） | 反射给 BaseEntity 注入 tenantId |
| 跨租户访问 | 返回 404（不是 403） | 不泄露 ID 存在性 |
| 非法 code | 404 | 同上 |

### 改动的文件清单（9 个）
| 类型 | 路径 |
|---|---|
| 新增 | `twelvet-framework-core/.../tenant/TenantContext.java` |
| 新增 | `twelvet-system-api/.../domain/SysTenant.java` |
| 新增 | `twelvet-system-server/.../mapper/SysTenantMapper.java` + `resources/mapper/system/SysTenantMapper.xml` |
| 新增 | `twelvet-system-server/.../config/tenant/TenantFilter.java` |
| 新增 | `twelvet-system-server/.../config/tenant/TenantFieldInterceptor.java` |
| 修改 | `twelvet-framework-core/.../application/domain/BaseEntity.java`（加 `tenantId` 字段） |
| 修改 | `twelvet-product/.../mapper/ProductMapper.java`（方法签名加 tenantId） |
| 修改 | `twelvet-product/.../resources/mapper/product/ProductMapper.xml`（加 tenant_id 列 + WHERE 过滤） |
| 修改 | `twelvet-product/.../service/impl/ProductServiceImpl.java`（传 TenantContext.get()） |
| 修改 | `twelvet-product/.../service/impl/UserProfileServiceImpl.java`（同上传 tenantId） |
| 修改 | `twelvet-trade/.../service/impl/TradeOrderServiceImpl.java`（同上传 tenantId） |
| 修改 | `twelvet-admin/.../resources/application.yml`（加 `tenant.enabled`） |
| 修改 | `data/neighborswap.sql`（加 sys_tenant 建表 + product 加列） |

## 已知的边界（不掩盖）

| 不在范围 | 影响 | 何时补 |
|---|---|---|
| 订单 / Feed / IM / 收藏 不带 tenant_id | 这些表 B 社区能看 A 社区的 | 第二轮（按需扩，每轮只加 1 个核心实体） |
| `@Async` / `@Scheduled` / WebSocket 任务 | 上下文丢失，所有租户写入都落 default | 第二轮，封装 `TenantRunnable` 工具 |
| 用户表不带 tenant_id | 同一手机号跨租户是不同 sys_user | 第三轮，配合 global_user_id |
| 平台超管 UI | 当前只能 SQL 维护 sys_tenant | 第四轮 |
| 套餐 / 计费 / 审计 | — | 商业化启动时 |
| 独立子域名 | — | 规模化阶段 |
| 单元测试 | 当前零测试，仅 mvn compile 验证 | Phase 1 后期补 |

## 一键回滚

```yaml
# application.yml
tenant:
  enabled: false   # 改回 false
```
重启后所有租户代码路径放行，行为完全等同改造前。
