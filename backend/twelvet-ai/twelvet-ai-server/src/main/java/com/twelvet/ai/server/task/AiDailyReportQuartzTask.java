package com.twelvet.ai.server.task;

import com.twelvet.ai.server.service.IReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 社区日报定时任务 Bean（由 twelvet-quartz 通过 bean.method 反射调用，同 runnerTask 惯例）
 * <p>
 * 设计：docs/AI社区日报设计.md §3；sys_job 注册 invoke_target=aiDailyReport.generateAll， cron 0 0 7 *
 * * ?（每天 07:00，统计昨日数据）。
 *
 * @author twelvet
 */
@Component("aiDailyReport")
public class AiDailyReportQuartzTask {

	private static final Logger log = LoggerFactory.getLogger(AiDailyReportQuartzTask.class);

	private final IReportService reportService;

	public AiDailyReportQuartzTask(IReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * 每天 07:00：为每个启用租户生成昨日社区日报并 IM 推送（零活动租户跳过）
	 */
	public void generateAll() {
		int count = reportService.generateAll();
		log.info("[Quartz] AiDailyReportJob 完成：生成 {} 份社区日报", count);
	}

}
