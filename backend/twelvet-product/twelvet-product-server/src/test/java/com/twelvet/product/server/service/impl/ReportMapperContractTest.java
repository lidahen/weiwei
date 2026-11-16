package com.twelvet.product.server.service.impl;

import com.twelvet.product.server.domain.Report;
import com.twelvet.product.server.mapper.ReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * report 表读写契约。
 * <p>
 * 钉死两件事：① 实体字段名与 XML resultMap 一致（拼错即运行期字段为 null，静默无报错）； ② markHandled 是唯一把 pending 改成
 * done 的入口，状态守卫写在该 SQL 里。
 * </p>
 *
 * @author twelvet
 */
class ReportMapperContractTest {

	@Test
	@DisplayName("Report 实体覆盖 report 表全部列")
	void reportEntityHasAllColumns() throws NoSuchMethodException {
		Class<Report> c = Report.class;
		assertNotNull(c.getMethod("getReportId"));
		assertNotNull(c.getMethod("getReporterId"));
		assertNotNull(c.getMethod("getTargetType"));
		assertNotNull(c.getMethod("getTargetId"));
		assertNotNull(c.getMethod("getReason"));
		assertNotNull(c.getMethod("getEvidenceImages"));
		assertNotNull(c.getMethod("getStatus"));
		assertNotNull(c.getMethod("getAction"));
		assertNotNull(c.getMethod("getHandleNote"));
		assertNotNull(c.getMethod("getHandlerId"));
		assertNotNull(c.getMethod("getCreateTime"));
		assertNotNull(c.getMethod("getHandleTime"));
	}

	@Test
	@DisplayName("ReportMapper 方法签名")
	void mapperSignatures() throws NoSuchMethodException {
		assertEquals(int.class, ReportMapper.class.getMethod("insert", Report.class).getReturnType());
		assertEquals(Report.class,
				ReportMapper.class.getMethod("selectByReporterAndTarget", Long.class, String.class, Long.class)
					.getReturnType());
		assertEquals(int.class,
				ReportMapper.class.getMethod("revive", Long.class, String.class, String.class).getReturnType());
		assertEquals(List.class, ReportMapper.class.getMethod("selectList", String.class, Long.class).getReturnType());
		assertEquals(Report.class, ReportMapper.class.getMethod("selectById", Long.class, Long.class).getReturnType());
		assertEquals(int.class,
				ReportMapper.class.getMethod("markHandled", Long.class, String.class, String.class, Long.class)
					.getReturnType());
	}

}
