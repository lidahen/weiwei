package com.twelvet.product.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 举报 Service
 *
 * @author twelvet
 */
public interface IReportService {

	/**
	 * 提交举报
	 * @param targetType product/feed/user
	 * @param targetId 被举报对象ID
	 * @param reporterId 举报人ID
	 * @param reasonType 原因编码（spam/fraud/fake/prohibited/abuse/other）
	 * @param reasonDetail 补充说明（可空，≤200 字）
	 * @param evidenceImages 证据图 OSS 相对路径（可空，≤3 张）
	 */
	void submit(String targetType, Long targetId, Long reporterId, String reasonType, String reasonDetail,
			List<String> evidenceImages);

	/**
	 * 运营举报列表（含被举报对象摘要）
	 * @param status pending/done/all
	 * @return 分页结果；每行含 reportId/targetType/targetId/targetTitle/targetCover/targetExtra/
	 * targetExists/ownerId/ownerNickName/reporterNickName/handlerNickName/
	 * reasonType/reasonDetail/evidenceImages/status/action/handleNote/handleTime
	 */
	TableDataInfo<Map<String, Object>> adminList(String status);

	/**
	 * 处理举报（处置真实生效 + 站内通知）
	 * @param reportId 举报ID
	 * @param action dismiss/warn/remove_product/remove_feed/ban_user
	 * @param note 处理备注（dismiss 时必填）
	 * @param handlerId 处理人ID
	 */
	void adminHandle(Long reportId, String action, String note, Long handlerId);

}
