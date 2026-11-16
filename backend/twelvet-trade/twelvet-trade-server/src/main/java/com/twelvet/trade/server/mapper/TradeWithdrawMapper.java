package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.TradeWithdraw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提现单 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface TradeWithdrawMapper {

	/**
	 * 新建提现单（useGeneratedKeys 回填 withdrawId）
	 * @param withdraw 提现单
	 * @return 影响行数
	 */
	int insert(TradeWithdraw withdraw);

	/**
	 * 卖家提现记录（PageHelper 在 service 层启动）
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 提现单列表
	 */
	List<TradeWithdraw> selectBySeller(@Param("sellerId") Long sellerId, @Param("tenantId") Long tenantId);

	/**
	 * 按ID查（带租户过滤）
	 * @param withdrawId 提现单ID
	 * @param tenantId 租户ID
	 * @return 提现单
	 */
	TradeWithdraw selectById(@Param("withdrawId") Long withdrawId, @Param("tenantId") Long tenantId);

	/**
	 * 状态推进（带前置状态守卫）
	 * @param withdrawId 提现单ID
	 * @param fromStatus 前置状态
	 * @param toStatus 目标状态
	 * @param operatorId 操作人
	 * @param failReason 驳回原因
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int review(@Param("withdrawId") Long withdrawId, @Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus, @Param("operatorId") Long operatorId,
			@Param("failReason") String failReason, @Param("tenantId") Long tenantId);

	/**
	 * 回填凭证并置 paid（仅 approved 可打款）
	 * @param withdrawId 提现单ID
	 * @param voucherUrl 凭证地址
	 * @param operatorId 打款人
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int markPaid(@Param("withdrawId") Long withdrawId, @Param("voucherUrl") String voucherUrl,
			@Param("operatorId") Long operatorId, @Param("tenantId") Long tenantId);

	/**
	 * 运营分页（PageHelper 在 service 层启动）
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 提现单列表
	 */
	List<TradeWithdraw> selectAdminPage(@Param("status") String status, @Param("tenantId") Long tenantId);

}