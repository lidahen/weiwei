package com.twelvet.ai.server.service;

import com.twelvet.ai.api.domain.DailyReport;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 社区日报 Service 接口
 * <p>
 * 设计：docs/AI社区日报设计.md
 */
public interface IReportService {

	/**
	 * 为所有启用租户生成日报（Quartz 每天 07:00 调用；单租户失败不影响其他租户）
	 * @return 实际生成的日报份数（零活动跳过的不计）
	 */
	int generateAll();

	/**
	 * 生成/覆盖指定租户指定日期的日报（数据日期默认昨天），并 IM 推送给本租户在职用户
	 * @param tenantId 租户ID
	 * @param manual true=管理员手动触发（不做零活动跳过）；false=定时任务
	 * @return 日报（含统计口径），生成被跳过时返回 null
	 */
	DailyReport generate(Long tenantId, boolean manual);

	/**
	 * 生成/覆盖指定租户指定数据日期的日报（generate 的完整形态，便于测试与后续补生成历史）
	 */
	DailyReport generate(Long tenantId, LocalDate reportDate, boolean manual);

	/**
	 * 当前租户日报分页（report_date 倒序，配合 PageHelper）
	 * @param tenantId 租户ID
	 * @return 日报列表
	 */
	List<DailyReport> selectPage(Long tenantId);

	/**
	 * 日报详情（租户校验防越权）
	 * @param id 日报ID
	 * @param tenantId 租户ID
	 * @return 日报，不存在或非本租户返回 null
	 */
	DailyReport selectByIdAndTenant(Long id, Long tenantId);

	/**
	 * 日报语音播报（晨间广播）：按需调 Python TTS 合成正文并本地缓存，返回 mp3 字节。 缓存键含 update_time，日报被覆盖生成后缓存自动失效。
	 * @param id 日报ID
	 * @param tenantId 租户ID（越权校验）
	 * @return mp3 音频字节
	 */
	byte[] loadReportAudio(Long id, Long tenantId);

}
