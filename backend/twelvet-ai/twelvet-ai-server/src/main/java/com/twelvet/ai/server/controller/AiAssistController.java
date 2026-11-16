package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.twelvet.framework.utils.http.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 商品发布辅助控制器（文案 / 配图）
 * <p>
 * 供社区商品发布页使用，仅要求登录（@SaCheckLogin），业务逻辑代理到 Python FastAPI 服务。 请求体以 Map 原样透传（前端驼峰
 * JSON，Python 侧 schema 使用驼峰别名可直接解析）。
 */
@Tag(description = "AI商品发布辅助接口", name = "AI商品发布辅助")
@RestController
@RequestMapping("/ai/assist")
public class AiAssistController extends TWTController {

	private final AiServiceClient aiServiceClient;

	public AiAssistController(AiServiceClient aiServiceClient) {
		this.aiServiceClient = aiServiceClient;
	}

	/**
	 * 用量计费身份头：透传 X-Tenant-Id / X-User-Id 给 Python， 供 ai-service
	 * 侧事前配额检查（quota_ai_call_day）与调用明细落账（ai_usage_log）。 与 AiChatController.addTenantHeader
	 * 的惯例一致（TenantContext 取租户，StpUtil 取当前用户）。
	 * @return 请求头集合
	 */
	private Map<String, String> billingHeaders() {
		Map<String, String> headers = new HashMap<>();
		Long tenantId = TenantContext.get();
		if (tenantId != null) {
			headers.put("X-Tenant-Id", String.valueOf(tenantId));
		}
		try {
			headers.put("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		}
		catch (Exception e) {
			// 登录态理论上由 @SaCheckLogin 保证；异常时忽略用户头，不阻断代理
		}
		return headers;
	}

	/**
	 * 首页小组件：天气 + 日期 + AI 激励语池
	 * <p>
	 * 游客可见（@SaIgnore，同 C 端 FeedController 惯例）：未登录时 billingHeaders 仅透传租户头， Python
	 * 侧对无租户上下文的请求返回内置模板池，不调 LLM（首页小组件永不 429/报错）。
	 * @param city 城市名（显式指定时优先），空则按客户端 IP 定位
	 * @param httpRequest 当前请求（取 X-Forwarded-For / X-Real-IP / remoteAddr 作为客户端 IP）
	 * @return JsonResult 天气 + 日期 + 激励语池（5 条，前端「换一句」本地轮换）
	 */
	@Operation(summary = "首页小组件（天气/日期/AI激励语）")
	@GetMapping("/home-widget")
	@SaIgnore
	public JsonResult<Object> homeWidget(@RequestParam(required = false) String city, HttpServletRequest httpRequest) {
		// 城市名原样交给 RestTemplate 的 URI 模板编码：RestTemplate 默认 EncodingMode.URI_COMPONENT
		// 会把中文编码为 %XX；若在此处提前 URLEncoder.encode，% 会被二次编码成 %25XX，
		// Python 侧将收到 "%E6%B7%B1%E5%9C%B3" 这样的乱码城市名（天气块显示异常）。
		StringBuilder query = new StringBuilder();
		if (city != null && !city.isBlank()) {
			query.append("?city=").append(city);
		}
		// 客户端 IP：生产环境 Caddy 会写入 X-Forwarded-For（取第一个=原始客户端）；
		// 本地开发经 dev proxy 后 remoteAddr 恒为 127.0.0.1，Python 侧会自动改用出口 IP 兜底
		String ip = resolveClientIp(httpRequest);
		if (ip != null && !ip.isBlank()) {
			query.append(query.length() == 0 ? "?" : "&").append("ip=").append(ip);
		}
		return JsonResult
			.success(aiServiceClient.get("/ai/assist/home-widget" + query, Object.class, billingHeaders()));
	}

	/**
	 * IP 归属地城市识别（首页定位默认值）
	 * <p>
	 * 游客可见（@SaIgnore，同 homeWidget 惯例）。Caddy→Java 代理链的真实客户端 IP 由 IpUtils.getIpAddr
	 * 解析（X-Forwarded-For / X-Real-IP），以 X-Client-Ip 显式透传给 Python； Python 侧私网/查询失败返回
	 * city=null，前端按「手动选择 > IP 定位 > 默认城市」回落。 定位失败不影响首页可用性。
	 * @return JsonResult ip + city（均可能为 null）
	 */
	@Operation(summary = "IP归属地城市识别")
	@GetMapping("/ip-city")
	@SaIgnore
	public JsonResult<Object> ipCity() {
		Map<String, String> headers = billingHeaders();
		String ip = IpUtils.getIpAddr();
		if (ip != null && !ip.isBlank()) {
			headers.put("X-Client-Ip", ip);
		}
		return JsonResult.success(aiServiceClient.get("/ai/assist/ip-city", Object.class, headers));
	}

	/**
	 * 解析客户端真实 IP：X-Forwarded-For 第一段（原始客户端）→ X-Real-IP → remoteAddr。
	 * @param request 当前请求
	 * @return 客户端 IP（可能是 127.0.0.1 等内网地址，由 Python 侧判断是否可用）
	 */
	private String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim();
		}
		return request.getRemoteAddr();
	}

	/**
	 * AI 生成商品文案（候选标题 + 描述）
	 * @param params 商品信息（productName 必填，category/condition/price/keywords 选填）
	 * @return JsonResult 候选标题列表与描述文案
	 */
	@Operation(summary = "AI生成商品文案")
	@Log(service = "AI商品辅助", businessType = BusinessType.OTHER)
	@PostMapping("/product-copy")
	@SaCheckLogin
	public JsonResult<Object> productCopy(@RequestBody Map<String, Object> params) {
		return JsonResult
			.success(aiServiceClient.post("/ai/assist/product-copy", params, Object.class, billingHeaders()));
	}

	/**
	 * AI 生成商品配图
	 * @param params 商品信息（productName 必填，size 选填）
	 * @return JsonResult 生成的图片 URL
	 */
	@Operation(summary = "AI生成商品配图")
	@Log(service = "AI商品辅助", businessType = BusinessType.OTHER)
	@PostMapping("/product-image")
	@SaCheckLogin
	public JsonResult<Object> productImage(@RequestBody Map<String, Object> params) {
		return JsonResult
			.success(aiServiceClient.post("/ai/assist/product-image", params, Object.class, billingHeaders()));
	}

	/**
	 * AI 识图填单：从商品实拍图识别类目/标题/描述/成色，发布页一键回填
	 * @param params images（1-5 张，OSS 相对路径或绝对 URL）、hint 选填
	 * @return JsonResult catL1Code/catL2Code/title/description/condition
	 */
	@Operation(summary = "AI识图填单")
	@Log(service = "AI商品辅助", businessType = BusinessType.OTHER)
	@PostMapping("/product-recognition")
	@SaCheckLogin
	public JsonResult<Object> productRecognition(@RequestBody Map<String, Object> params) {
		return JsonResult
			.success(aiServiceClient.post("/ai/assist/product-recognition", params, Object.class, billingHeaders()));
	}

	/**
	 * AI 估价：真实成交分位数 × 成色系数，价格数字全部来自业务库统计
	 * @param params title 必填，catL1/catL2/condition/description 选填
	 * @return JsonResult priceLow/priceHigh/median/p25/p75/suggestion/explanation
	 */
	@Operation(summary = "AI估价")
	@Log(service = "AI商品辅助", businessType = BusinessType.OTHER)
	@PostMapping("/product-valuation")
	@SaCheckLogin
	public JsonResult<Object> productValuation(@RequestBody Map<String, Object> params) {
		return JsonResult
			.success(aiServiceClient.post("/ai/assist/product-valuation", params, Object.class, billingHeaders()));
	}

}
