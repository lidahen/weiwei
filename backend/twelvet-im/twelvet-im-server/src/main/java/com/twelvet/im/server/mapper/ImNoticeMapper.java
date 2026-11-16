package com.twelvet.im.server.mapper;

import com.twelvet.im.api.domain.ImNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知Mapper
 *
 * @author twelvet
 */
@Mapper
public interface ImNoticeMapper {

	/**
	 * 查询用户通知列表
	 * @param userId 用户ID
	 * @return 通知列表
	 */
	List<ImNotice> selectNoticeListByUserId(@Param("userId") Long userId);

	/**
	 * 新增通知
	 * @param notice 通知信息
	 * @return 结果
	 */
	int insertNotice(ImNotice notice);

}
