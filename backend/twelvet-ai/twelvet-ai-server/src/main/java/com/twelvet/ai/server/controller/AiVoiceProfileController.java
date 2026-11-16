package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.api.domain.AiVoiceProfile;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.service.IAiVoiceProfileService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 音色库 Controller
 *
 * <p>
 * 用户上传样本 → Python ai-service 调用硅基流动训练 → 落库。 鉴权在 SaToken 层完成, 配额扣减在 Python ai-service 的
 * quota_service 单一账本。
 * </p>
 */
@Tag(description = "AI 音色库", name = "AI 音色库")
@RestController
@RequestMapping("/ai/music/voices")
public class AiVoiceProfileController extends TWTController {

	private final AiServiceClient aiServiceClient;

	private final IAiVoiceProfileService aiVoiceProfileService;

	private final RestTemplate restTemplate;

	@Value("${ai.service.url}")
	private String aiServiceUrl;

	public AiVoiceProfileController(AiServiceClient aiServiceClient, IAiVoiceProfileService aiVoiceProfileService,
			org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder) {
		this.aiServiceClient = aiServiceClient;
		this.aiVoiceProfileService = aiVoiceProfileService;
		this.restTemplate = restTemplateBuilder.build();
	}

	/**
	 * 上传样本训练克隆音色(代理到 Python)
	 */
	@Operation(summary = "上传样本训练克隆音色")
	@Log(service = "AI 音色库", businessType = BusinessType.INSERT)
	@PostMapping(value = "/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SaCheckPermission("ai:music:voice:train")
	public JsonResult<Map<String, Object>> train(@RequestParam("name") String name, @RequestParam("text") String text,
			@RequestParam(value = "durationMs", required = false) Integer durationMs,
			@RequestParam(value = "consentSigned", required = false, defaultValue = "false") Boolean consentSigned,
			@RequestParam(value = "consentIp", required = false) String consentIp,
			@RequestParam("file") MultipartFile file) throws IOException {

		if (!Boolean.TRUE.equals(consentSigned)) {
			return JsonResult.error("请先签署声音克隆授权协议");
		}
		long userId = StpUtil.getLoginIdAsLong();

		// 每日训练次数限制(MUSIC_QUOTA_VOICE_TRAIN_DAILY_LIMIT 默认 1)
		int todayCount = aiVoiceProfileService.countTodayTrainings(userId);
		if (todayCount >= 1) {
			return JsonResult.error("今日训练次数已达上限, 请明日再来");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.add("X-User-Id", String.valueOf(userId));

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("name", name);
		body.add("text", text);
		body.add("consent_signed", String.valueOf(consentSigned));
		if (durationMs != null)
			body.add("duration_ms", durationMs.toString());
		if (consentIp != null)
			body.add("consent_ip", consentIp);
		body.add("file", file.getResource());

		HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> resp = restTemplate.exchange(aiServiceUrl + "/ai/music/voices", HttpMethod.POST, entity,
				Map.class);

		Map<String, Object> pyData = resp.getBody();
		if (pyData == null) {
			return JsonResult.error("AI 服务无响应");
		}

		// 落库
		AiVoiceProfile profile = new AiVoiceProfile();
		profile.setUserId(userId);
		profile.setName(name);
		profile.setVendor((String) pyData.get("vendor"));
		profile.setVendorVoiceId((String) pyData.get("vendor_voice_id"));
		profile.setVendorModel((String) pyData.get("vendor_model"));
		profile.setDurationMs(durationMs);
		profile.setStatus((Integer) pyData.getOrDefault("status", 2));
		profile.setConsentSigned(1);
		profile.setConsentIp(consentIp);
		profile.setConsentAt(new java.util.Date());
		profile.setSampleText(text);
		aiVoiceProfileService.insertAiVoiceProfile(profile);

		Map<String, Object> out = new HashMap<>();
		out.put("id", profile.getId());
		out.put("vendor", profile.getVendor());
		out.put("vendorVoiceId", profile.getVendorVoiceId());
		out.put("status", profile.getStatus());
		return JsonResult.success(out);
	}

	/**
	 * 平台预置音色列表(代理到 Python, 不落库)
	 * <p>
	 * 必须定义在 /{id} 之前之外: 否则 "platform" 会被当成 Long 主键解析而 500。
	 * </p>
	 */
	@Operation(summary = "平台音色列表")
	@GetMapping("/platform")
	@SaCheckPermission("ai:music:voice:list")
	public JsonResult<Object> platform(@RequestParam(value = "voice_type", required = false) String voiceType) {
		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(StpUtil.getLoginIdAsLong()));
		String path = "/ai/music/voices/platform";
		if (voiceType != null && !voiceType.isBlank()) {
			path += "?voice_type=" + voiceType.trim();
		}
		return JsonResult.success(aiServiceClient.get(path, Object.class, headers));
	}

	@Operation(summary = "我的音色列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:music:voice:list")
	public JsonResult<TableDataInfo<AiVoiceProfile>> pageQuery(AiVoiceProfile profile) {
		profile.setUserId(StpUtil.getLoginIdAsLong());
		PageUtils.startPage();
		List<AiVoiceProfile> list = aiVoiceProfileService.selectAiVoiceProfileList(profile);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	@Operation(summary = "音色详情")
	@GetMapping("/{id}")
	@SaCheckPermission("ai:music:voice:query")
	public JsonResult<AiVoiceProfile> getById(@PathVariable Long id) {
		AiVoiceProfile profile = aiVoiceProfileService.selectAiVoiceProfileById(id);
		if (profile != null && !profile.getUserId().equals(StpUtil.getLoginIdAsLong())) {
			return JsonResult.error("无权访问");
		}
		return JsonResult.success(profile);
	}

	@Operation(summary = "删除音色")
	@Log(service = "AI 音色库", businessType = BusinessType.DELETE)
	@DeleteMapping("/{id}")
	@SaCheckPermission("ai:music:voice:remove")
	public JsonResult<String> remove(@PathVariable Long id) {
		AiVoiceProfile profile = aiVoiceProfileService.selectAiVoiceProfileById(id);
		if (profile == null || !profile.getUserId().equals(StpUtil.getLoginIdAsLong())) {
			return JsonResult.error("音色不存在或无权访问");
		}
		aiVoiceProfileService.deleteAiVoiceProfileById(id);
		// 注: 上游 vendor voice 不主动删除(避免误删影响其他在用任务)
		return JsonResult.success("删除成功");
	}

}
