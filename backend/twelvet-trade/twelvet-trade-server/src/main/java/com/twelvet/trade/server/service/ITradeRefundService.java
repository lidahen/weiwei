package com.twelvet.trade.server.service;

import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.trade.server.domain.TradeRefund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 退款售后 Service
 *
 * @author twelvet
 */
public interface ITradeRefundService {

	/**
	 * 申请退款：闸门校验 → 冻结订单为 refunding → 落退款单（pending_seller）→ 写申请日志
	 * @param orderId 订单ID
	 * @param type 退款类型（refund_only / return_refund）
	 * @param reasonCode 退款原因编码
	 * @param amount 申请退款金额
	 * @param description 买家说明
	 * @param evidenceImages 证据图 URL 列表（最多 3 张）
	 * @param buyerId 买家ID（取自登录态）
	 * @return 新建退款单ID
	 */
	Long apply(Long orderId, String type, String reasonCode, BigDecimal amount, String description,
			List<String> evidenceImages, Long buyerId);

	/**
	 * 查询某订单是否可申请退款（前端订单详情用，不抛异常）
	 * @param orderId 订单ID
	 * @param buyerId 买家ID
	 * @return {canApply: Boolean, denyReason: String, activeRefundId: Long}
	 */
	Map<String, Object> checkApply(Long orderId, Long buyerId);

	/**
	 * 退款详情 + 时间线（仅买卖双方可见）
	 * @param refundId 退款单ID
	 * @param viewerId 查看人ID
	 * @return {refund, logs}；非买卖双方返回 null
	 */
	Map<String, Object> detail(Long refundId, Long viewerId);

	/**
	 * 我的退款分页列表（role=buyer/seller）
	 * @param userId 用户ID
	 * @param role buyer / seller
	 * @param status 状态筛选（可空）
	 * @return 分页结果
	 */
	TableDataInfo<TradeRefund> page(Long userId, String role, String status);

	/**
	 * 待我处理（卖家视角）的退款分页列表
	 * @param sellerId 卖家ID
	 * @param status 状态筛选（可空）
	 * @return 分页结果
	 */
	TableDataInfo<TradeRefund> sellerPage(Long sellerId, String status);

	/**
	 * 卖家同意退款（退货退款时快照退货地址）
	 * @param refundId 退款单ID
	 * @param returnAddress 退货地址（仅退货退款需要）
	 * @param sellerId 卖家ID
	 */
	void sellerAgree(Long refundId, String returnAddress, Long sellerId);

	/**
	 * 卖家拒绝退款（退款单关闭，订单恢复原状态）
	 * @param refundId 退款单ID
	 * @param reason 拒绝理由
	 * @param sellerId 卖家ID
	 */
	void sellerReject(Long refundId, String reason, Long sellerId);

	/**
	 * 买家填写退货物流
	 * @param refundId 退款单ID
	 * @param company 物流公司
	 * @param returnNo 物流单号
	 * @param buyerId 买家ID
	 */
	void buyerReturn(Long refundId, String company, String returnNo, Long buyerId);

	/**
	 * 买家撤销退款申请
	 * @param refundId 退款单ID
	 * @param buyerId 买家ID
	 */
	void buyerCancel(Long refundId, Long buyerId);

	/**
	 * 买家申请平台介入（仅 seller_rejected 可发起，订单重新冻结为 refunding）
	 * @param refundId 退款单ID
	 * @param buyerId 买家ID
	 */
	void buyerMediate(Long refundId, Long buyerId);

	/**
	 * 卖家确认收到退货（buyer_returned → refunding 并立即执行退款）
	 * @param refundId 退款单ID
	 * @param sellerId 卖家ID
	 */
	void sellerConfirmReceipt(Long refundId, Long sellerId);

	/**
	 * 运营在退款管理页裁决 mediation 状态的退款单
	 * @param refundId 退款单ID
	 * @param decision 裁决结论（buyer 全额退 / seller 驳回 / partial 部分退）
	 * @param amount 部分退款金额（仅 partial 需要）
	 * @param reason 裁决理由
	 * @param operatorId 操作人ID
	 */
	void judge(Long refundId, String decision, BigDecimal amount, String reason, Long operatorId);

	/**
	 * 客服在纠纷页裁决时联动退款：按 orderId 反查 mediation 退款单
	 * @param orderId 订单ID
	 * @param decision 裁决结论（buyer / seller / partial）
	 * @param amount 部分退款金额（仅 partial 需要）
	 * @param reason 裁决理由
	 * @param operatorId 操作人ID
	 * @return 是否处理了退款单（找不到 mediation 退款单返回 false，不抛异常）
	 */
	boolean judgeByOrder(Long orderId, String decision, BigDecimal amount, String reason, Long operatorId);

	/**
	 * 运营手工收口 refunding 状态的退款单（线下退款已完成/放弃）
	 * @param refundId 退款单ID
	 * @param result 收口结果（success / fail）
	 * @param note 人工处理说明
	 * @param operatorId 操作人ID
	 */
	void resolve(Long refundId, String result, String note, Long operatorId);

	/**
	 * 运营端退款详情（不校验买卖家归属，但仍带租户过滤）
	 * @param refundId 退款单ID
	 * @return {refund, logs, order}；退款单不存在返回 null
	 */
	Map<String, Object> adminDetail(Long refundId);

	/**
	 * 运营端退款分页列表
	 * @param status 状态筛选（可空）
	 * @param overdue 仅看超时单（可空；各状态按停留时长判定，见 Mapper SQL）
	 * @return 分页结果
	 */
	TableDataInfo<TradeRefund> adminPage(String status, Boolean overdue);

	/**
	 * 扫描三类超时（Quartz 任务调用；无租户上下文，全租户扫描）
	 * @return 本次处理的退款单数
	 */
	int scanTimeouts();

}