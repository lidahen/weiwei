package com.twelvet.exchange.server.service;

import com.twelvet.exchange.api.domain.ExchangeOrder;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 交换订单服务（双边履约：交付/确认/取消/纠纷/超时/裁决）
 *
 * @author twelvet
 */
public interface IExchangeOrderService {

	/**
	 * 我的交换订单（A/B 双视角，真分页；statusList 为空 = 全部）
	 */
	TableDataInfo myOrdersPage(Long userId, List<String> statusList, Long pageNum, Long pageSize);

	/**
	 * 订单详情（双边进度时间线 + 面交码 + 对方信息 + 流转日志；仅参与方可看）
	 */
	Map<String, Object> detail(Long orderId, Long userId);

	/**
	 * 标记我方已交付（face 点按钮 / express 传运单号，express 时 trackNo 必填）
	 */
	void deliver(Long orderId, Long userId, String trackNo);

	/**
	 * 确认已收到对方物品（双确认 → completed，双方物品 swapped）
	 */
	void confirm(Long orderId, Long userId);

	/**
	 * 取消（仅双方均未交付；事务内两物品解锁回 onswap）
	 */
	void cancel(Long orderId, Long userId, String reason);

	/**
	 * 发起纠纷（delivering/pending_confirm，物品保持 locked）
	 */
	void dispute(Long orderId, Long userId, String reason);

	/**
	 * 订单超时扫描（建单 7 天未双交付 → cancelled+解锁+通知；pending_confirm 超 3 天未双确认 → 自动 completed），用于
	 * Quartz 定时任务
	 * @return 受影响订单数
	 */
	int scanTimeoutOrder();

	/**
	 * 平台管理：订单分页（disputed 置顶）
	 */
	TableDataInfo adminPage(Long pageNum, Long pageSize, String status, String keyword);

	/**
	 * 后台裁决：按现状完成（disputed → completed + 双方物品 swapped）
	 */
	void adminForceComplete(Long orderId, Long operatorId, String remark);

	/**
	 * 后台裁决：取消并解锁物品（disputed → cancelled + 物品回 onswap）
	 */
	void adminForceCancel(Long orderId, Long operatorId, String remark);

}
