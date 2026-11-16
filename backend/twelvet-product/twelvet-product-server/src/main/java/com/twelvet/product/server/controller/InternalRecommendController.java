package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.internal.InternalApiToken;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.core.tenant.TenantSupport;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Product;
import com.twelvet.product.server.mapper.UserMapper;
import com.twelvet.product.server.service.IRecommendService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部推荐接口（服务间调用：Python ai-service → Java）
 * <p>
 * 存在的理由：AI 导购 Agent 没有用户登录态，公开端点 {@code GET /product/home/recommend} 的 userId 取自
 * {@code StpUtil.isLogin()}（ProductController），直调只能拿到匿名推荐， 六路召回中的兴趣/协同/同城三路会失效。本端点由服务端从
 * sys_user 解析租户后调用 {@link IRecommendService#homeRecommend}，六路召回/打分/推荐理由全部复用，推荐逻辑零改动。
 * </p>
 * <p>
 * 安全设计（三道，复刻 InternalOrderController）：
 * <ol>
 * <li>接口置于 {@code /internal/**} 前缀下，便于反向代理（Caddy）统一 deny，不暴露到公网</li>
 * <li>必须携带 {@code X-Internal-Token}，常量时间比对；未配置令牌一律拒绝（失败关闭）</li>
 * <li>租户由服务端从 sys_user.tenant_id 解析，绝不取调用方传参</li>
 * </ol>
 * 本接口 {@code @SaIgnore}（绕过 Sa-Token 登录校验），因此第 2 条是它唯一的鉴权手段，不可省略。
 * </p>
 *
 * @author twelvet
 */
@Hidden
@Tag(description = "内部推荐接口(服务间调用)", name = "InternalRecommendController")
@RestController
@RequestMapping("/internal/recommend")
public class InternalRecommendController {

	private static final Logger log = LoggerFactory.getLogger(InternalRecommendController.class);

	private static final int DEFAULT_PAGE_SIZE = 8;

	private static final int MAX_PAGE_SIZE = 20;

	private final IRecommendService recommendService;

	private final InternalApiToken internalApiToken;

	private final TenantSupport tenantSupport;

	private final UserMapper userMapper;

	public InternalRecommendController(IRecommendService recommendService, InternalApiToken internalApiToken,
			TenantSupport tenantSupport, UserMapper userMapper) {
		this.recommendService = recommendService;
		this.internalApiToken = internalApiToken;
		this.tenantSupport = tenantSupport;
		this.userMapper = userMapper;
	}

	/**
	 * 服务端解析租户后调用六路召回推荐（推荐逻辑零改动复用）。
	 * @param token 请求头 {@code X-Internal-Token}
	 * @param userId 被推荐的用户 ID（必填数字）
	 * @param catL1 一级分类（可选）
	 * @param pageSize 条数（默认 8，上限 20）
	 * @return 推荐分页；鉴权失败 403、参数缺失或用户无租户 400
	 */
	@Operation(summary = "内部个性化推荐（服务间调用）")
	@SaIgnore
	@GetMapping("/for-user")
	public ResponseEntity<JsonResult<TableDataInfo<Product>>> forUser(
			@RequestHeader(value = InternalApiToken.HEADER, required = false) String token,
			@RequestParam(required = false) String userId, @RequestParam(required = false) String catL1,
			@RequestParam(required = false, defaultValue = "8") Integer pageSize) {
		if (!internalApiToken.matches(token)) {
			// 失败关闭且不区分"未配置"与"令牌错误"，避免向外泄露内部接口的配置状态
			log.warn("内部推荐接口鉴权失败，已拒绝");
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(JsonResult.<TableDataInfo<Product>>error(HttpStatus.FORBIDDEN.value(), "内部接口鉴权失败"));
		}
		Long uid = toLong(userId);
		if (uid == null) {
			return ResponseEntity.badRequest()
				.body(JsonResult.<TableDataInfo<Product>>error(HttpStatus.BAD_REQUEST.value(), "参数缺失：userId 必填且必须为数字"));
		}
		int size = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
		if (!tenantSupport.enabled()) {
			// 租户开关关闭：与公开路径完全一致（上下文为 null），零侵入回滚能力不被破坏
			return ResponseEntity.ok(JsonResult.success(recommendService.homeRecommend(uid, catL1, 1, size)));
		}
		// 引导查询（上下文尚未建立，查询本身不带租户过滤）；绝不使用调用方传入的租户标识
		Long tid = userMapper.selectUserTenantId(uid);
		if (tid == null) {
			log.warn("内部推荐：用户 {} 不存在或未归属任何租户，失败关闭", uid);
			return ResponseEntity.badRequest()
				.body(JsonResult.<TableDataInfo<Product>>error(HttpStatus.BAD_REQUEST.value(), "用户不存在或未归属任何租户"));
		}
		TenantContext.set(tid);
		try {
			return ResponseEntity.ok(JsonResult.success(recommendService.homeRecommend(uid, catL1, 1, size)));
		}
		finally {
			// 必须清理：否则上下文会随线程复用泄漏到后续无关请求
			TenantContext.clear();
		}
	}

	private Long toLong(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
