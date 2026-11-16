package com.twelvet.ai.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.ai.api.domain.AiMusicWork;
import com.twelvet.ai.server.client.AiServiceClient;
import com.twelvet.ai.server.service.IAiMusicWorkService;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.log.annotation.Log;
import com.twelvet.framework.log.enums.BusinessType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 音乐作品 Controller
 *
 * <p>
 * 用户提交任务 → Python ai-service 调用商汤 → 返回 vendor task_id → 落库 ai_music_work; 前端轮询调用 /status
 * 推进状态机。
 * </p>
 */
@Tag(description = "AI 音乐作品", name = "AI 音乐作品")
@RestController
@RequestMapping("/ai/music/songs")
public class AiMusicController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(AiMusicController.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final AiServiceClient aiServiceClient;

	private final IAiMusicWorkService aiMusicWorkService;

	public AiMusicController(AiServiceClient aiServiceClient, IAiMusicWorkService aiMusicWorkService) {
		this.aiServiceClient = aiServiceClient;
		this.aiMusicWorkService = aiMusicWorkService;
	}

	/**
	 * 提交歌曲生成任务
	 * @param req {type, prompt, style, lyrics, voiceId, voiceProfileId, gender, bpm,
	 * expectDuration, language, title, styleTags}
	 */
	@Operation(summary = "提交歌曲生成任务")
	@Log(service = "AI 音乐", businessType = BusinessType.INSERT)
	@PostMapping("/submit")
	@SaCheckPermission("ai:music:song:submit")
	public JsonResult<Map<String, Object>> submit(@RequestBody Map<String, Object> req) {
		long userId = StpUtil.getLoginIdAsLong();
		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(userId));

		@SuppressWarnings("unchecked")
		Map<String, Object> pyResp = aiServiceClient.post("/ai/music/songs", req, Map.class, headers);

		String vendorTaskId = (String) pyResp.get("vendor_task_id");
		String vendor = (String) pyResp.getOrDefault("vendor", "senseaudio");

		// 落库
		AiMusicWork work = new AiMusicWork();
		work.setUserId(userId);
		Object t = req.get("type");
		work.setType(t == null ? 1 : Integer.parseInt(t.toString().equals("text_to_song") ? "1"
				: t.toString().equals("platform_cover") ? "2" : t.toString().equals("clone_tts") ? "3" : "4"));
		work.setTitle((String) req.get("title"));
		work.setStyleTags((String) req.get("styleTags"));
		Object bpm = req.get("bpm");
		if (bpm != null)
			work.setBpm(Integer.parseInt(bpm.toString()));
		work.setKeySignature((String) req.get("keyScale"));
		work.setLyrics((String) req.get("lyrics"));
		work.setPrompt((String) req.get("prompt"));
		Object vpid = req.get("voiceProfileId");
		if (vpid != null)
			work.setVoiceProfileId(Long.parseLong(vpid.toString()));
		work.setVendor(vendor);
		work.setVendorTaskId(vendorTaskId);
		work.setVendorVoiceId((String) req.get("voiceId"));
		work.setStatus(1); // 生成中
		work.setQuotaCost(5);
		aiMusicWorkService.insertAiMusicWork(work);

		return JsonResult.success(Map.of("id", work.getId(), "vendorTaskId", vendorTaskId, "status", work.getStatus()));
	}

	/**
	 * 轮询歌曲任务状态, 推进本地数据库
	 */
	@Operation(summary = "查询歌曲状态")
	@GetMapping("/status/{id}")
	@SaCheckPermission("ai:music:song:query")
	public JsonResult<Map<String, Object>> status(@PathVariable Long id) {
		AiMusicWork work = aiMusicWorkService.selectAiMusicWorkById(id);
		if (work == null)
			return JsonResult.error("作品不存在");
		if (!work.getUserId().equals(StpUtil.getLoginIdAsLong()))
			return JsonResult.error("无权访问");

		Map<String, String> headers = Map.of("X-User-Id", String.valueOf(work.getUserId()));
		@SuppressWarnings("unchecked")
		Map<String, Object> pyResp = aiServiceClient
			.get("/ai/music/songs/status?vendor_task_id=" + work.getVendorTaskId(), Map.class, headers);
		// unwrapBody 已自动解包内层 data
		String status = (String) pyResp.get("status");
		Integer newStatus = null;
		if ("PENDING".equals(status))
			newStatus = 1;
		else if ("SUCCESS".equals(status))
			newStatus = 2;
		else if ("FAILED".equals(status))
			newStatus = 3;

		if (newStatus != null && !newStatus.equals(work.getStatus())) {
			work.setStatus(newStatus);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> songs = (List<Map<String, Object>>) pyResp.get("songs");
			if (newStatus == 2 && songs != null && !songs.isEmpty()) {
				Map<String, Object> song = songs.get(0);
				work.setAudioUrl((String) song.get("audio_url"));
				work.setCoverUrl((String) song.get("cover_url"));
				Object lyrics = song.get("lyrics");
				if (lyrics != null)
					work.setLyrics(lyrics.toString());
				Object dur = song.get("duration_ms");
				if (dur != null)
					work.setAudioDurationMs(Integer.parseInt(dur.toString()));
				@SuppressWarnings("unchecked")
				Map<String, Object> derived = (Map<String, Object>) song.get("derived_audio");
				// derived_audio 是 MySQL JSON 列：必须写合法 JSON，
				// Map.toString() 得到的是 {mp3=url} 这种非 JSON 格式会触发 Data truncation
				if (derived != null && !derived.isEmpty()) {
					try {
						work.setDerivedAudio(OBJECT_MAPPER.writeValueAsString(derived));
					}
					catch (JsonProcessingException e) {
						log.warn("derived_audio 序列化失败，跳过该字段: {}", e.getMessage());
					}
				}
				work.setTitle(work.getTitle() != null ? work.getTitle() : (String) song.get("title"));
			}
			else if (newStatus == 3) {
				work.setErrorMsg((String) pyResp.get("fail_reason"));
			}
			aiMusicWorkService.updateAiMusicWorkStatus(work);
		}

		return JsonResult.success(Map.of("id", work.getId(), "status", work.getStatus(), "audioUrl",
				work.getAudioUrl() == null ? "" : work.getAudioUrl(), "coverUrl",
				work.getCoverUrl() == null ? "" : work.getCoverUrl(), "durationMs",
				work.getAudioDurationMs() == null ? 0 : work.getAudioDurationMs(), "errorMsg",
				work.getErrorMsg() == null ? "" : work.getErrorMsg()));
	}

	@Operation(summary = "我的作品列表")
	@GetMapping("/pageQuery")
	@SaCheckPermission("ai:music:song:list")
	public JsonResult<TableDataInfo<AiMusicWork>> pageQuery(AiMusicWork work) {
		work.setUserId(StpUtil.getLoginIdAsLong());
		PageUtils.startPage();
		List<AiMusicWork> list = aiMusicWorkService.selectAiMusicWorkList(work);
		return JsonResult.success(PageUtils.getDataTable(list));
	}

	@Operation(summary = "作品详情")
	@GetMapping("/{id}")
	@SaCheckPermission("ai:music:song:query")
	public JsonResult<AiMusicWork> getById(@PathVariable Long id) {
		AiMusicWork work = aiMusicWorkService.selectAiMusicWorkById(id);
		if (work != null && !work.getUserId().equals(StpUtil.getLoginIdAsLong())) {
			return JsonResult.error("无权访问");
		}
		return JsonResult.success(work);
	}

	@Operation(summary = "删除作品(软删除)")
	@Log(service = "AI 音乐", businessType = BusinessType.DELETE)
	@DeleteMapping("/{id}")
	@SaCheckPermission("ai:music:song:remove")
	public JsonResult<String> remove(@PathVariable Long id) {
		AiMusicWork work = aiMusicWorkService.selectAiMusicWorkById(id);
		if (work == null || !work.getUserId().equals(StpUtil.getLoginIdAsLong())) {
			return JsonResult.error("作品不存在或无权访问");
		}
		aiMusicWorkService.deleteAiMusicWorkById(id);
		return JsonResult.success("删除成功");
	}

}
