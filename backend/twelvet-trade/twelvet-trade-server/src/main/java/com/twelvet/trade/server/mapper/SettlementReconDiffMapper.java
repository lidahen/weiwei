package com.twelvet.trade.server.mapper;

import com.twelvet.trade.server.domain.SettlementReconDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 结算对账差异 Mapper
 *
 * @author twelvet
 */
@Mapper
public interface SettlementReconDiffMapper {

	/**
	 * 入队（uk_diff 命中时返回 0）
	 * @param diff 差异
	 * @return 影响行数
	 */
	int insertIgnore(SettlementReconDiff diff);

	/**
	 * 运营分页（PageHelper 在 service 层启动）
	 * @param bizDate 业务日 yyyy-MM-dd，null 不过滤
	 * @param status 状态，null 不过滤
	 * @param tenantId 租户ID，null 不过滤
	 * @return 差异列表
	 */
	List<SettlementReconDiff> selectPage(@Param("bizDate") String bizDate, @Param("status") String status,
			@Param("tenantId") Long tenantId);

	/**
	 * 标记已处理（仅 open 可处理）
	 * @param diffId 差异ID
	 * @param resolvedBy 处理人
	 * @param resolvedNote 处理说明
	 * @param tenantId 租户ID
	 * @return 影响行数
	 */
	int resolve(@Param("diffId") Long diffId, @Param("resolvedBy") Long resolvedBy,
			@Param("resolvedNote") String resolvedNote, @Param("tenantId") Long tenantId);

	/**
	 * 卖家未闭环差异数
	 * @param sellerId 卖家用户ID
	 * @param tenantId 租户ID
	 * @return 数量
	 */
	int countOpenBySeller(@Param("sellerId") Long sellerId, @Param("tenantId") Long tenantId);

	/**
	 * 冻结该业务日存在未闭环差异的卖家
	 * @param bizDate 业务日 yyyy-MM-dd
	 * @return 影响行数
	 */
	int freezeSellersWithOpenDiffs(@Param("bizDate") String bizDate);

}