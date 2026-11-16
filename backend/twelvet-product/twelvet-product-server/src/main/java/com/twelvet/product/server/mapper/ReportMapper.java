package com.twelvet.product.server.mapper;

import com.twelvet.product.server.domain.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 举报表 Mapper —— report 表的唯一读写点
 * <p>
 * 运营端列表与处置也走本 Mapper（trade 的 AdminMarketplaceMapper 已删除同名 SQL），避免两处 SQL 漂移。
 * </p>
 *
 * @author twelvet
 */
@Mapper
public interface ReportMapper {

	/**
	 * 新增举报
	 * @param report 举报信息
	 * @return 影响行数
	 */
	int insert(Report report);

	/**
	 * 按举报人 + 对象查已有举报（去重与复活用）
	 * @param reporterId 举报人ID
	 * @param targetType 对象类型
	 * @param targetId 对象ID
	 * @return 已有举报；无则 null
	 */
	Report selectByReporterAndTarget(@Param("reporterId") Long reporterId, @Param("targetType") String targetType,
			@Param("targetId") Long targetId);

	/**
	 * 复活已处理举报：重置为 pending、覆盖原因与证据、清空处理字段
	 * @param reportId 举报ID
	 * @param reason 举报原因
	 * @param evidenceImages 证据图 JSON
	 * @return 影响行数
	 */
	int revive(@Param("reportId") Long reportId, @Param("reason") String reason,
			@Param("evidenceImages") String evidenceImages);

	/**
	 * 举报列表（运营端）
	 * @param status pending/done；null 表示全部
	 * @param tenantId 租户ID(为 null 不过滤)，经 reporter 的 sys_user 取租户
	 * @return 举报列表
	 */
	List<Report> selectList(@Param("status") String status, @Param("tenantId") Long tenantId);

	/**
	 * 按 ID 查举报（含租户过滤）
	 * @param reportId 举报ID
	 * @param tenantId 租户ID(为 null 不过滤)
	 * @return 举报；无则 null
	 */
	Report selectById(@Param("reportId") Long reportId, @Param("tenantId") Long tenantId);

	/**
	 * 置为已处理 —— 唯一的状态迁移入口
	 * <p>
	 * WHERE 带 status='pending'：并发双击时只有先到者 rows=1，后到者 rows=0 由 Service 抛异常。
	 * </p>
	 * @param reportId 举报ID
	 * @param action 处理动作
	 * @param note 处理备注
	 * @param handlerId 处理人ID
	 * @return 影响行数（0 表示该举报已被处理）
	 */
	int markHandled(@Param("reportId") Long reportId, @Param("action") String action, @Param("note") String note,
			@Param("handlerId") Long handlerId);

	/**
	 * 仅更新处理备注（处置未生效时追加「对象已不存在」说明）
	 * @param reportId 举报ID
	 * @param note 备注
	 * @return 影响行数
	 */
	int updateHandleNote(@Param("reportId") Long reportId, @Param("note") String note);

}
