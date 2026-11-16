package com.twelvet.product.server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 举报闭环所需的 Mapper 方法契约。
 * <p>
 * MyBatis 接口无实现类，SQL 本身的正确性靠手工验收； 本测试只钉死「方法名 + 参数顺序 + 返回类型」——这些是跨任务引用的公共契约， 改名或改顺序会让
 * ReportServiceImpl 静默编译失败或运行期绑定错 SQL。
 * </p>
 *
 * @author twelvet
 */
class ReportMapperContractTest {

	@Test
	@DisplayName("ProductMapper.selectByIds 批量取商品摘要")
	void productSelectByIds() throws NoSuchMethodException {
		Method m = ProductMapper.class.getMethod("selectByIds", List.class, Long.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("FeedMapper.selectByIds 批量取动态摘要")
	void feedSelectByIds() throws NoSuchMethodException {
		Method m = FeedMapper.class.getMethod("selectByIds", List.class, Long.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("UserMapper.selectByIds 批量取用户摘要（含 status/del_flag）")
	void userSelectByIds() throws NoSuchMethodException {
		Method m = UserMapper.class.getMethod("selectByIds", List.class);
		assertEquals(List.class, m.getReturnType());
	}

	@Test
	@DisplayName("UserMapper.updateStatus 封号写入")
	void userUpdateStatus() throws NoSuchMethodException {
		Method m = UserMapper.class.getMethod("updateStatus", Long.class, Integer.class, Long.class);
		assertEquals(int.class, m.getReturnType());
	}

}
