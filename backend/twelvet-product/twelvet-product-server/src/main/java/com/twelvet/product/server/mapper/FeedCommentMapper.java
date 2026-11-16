package com.twelvet.product.server.mapper;

import com.twelvet.product.api.domain.FeedComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("productFeedCommentMapper")
@Mapper
public interface FeedCommentMapper {

	List<FeedComment> selectByFeedId(@Param("feedId") Long feedId, @Param("tenantId") Long tenantId);

	FeedComment selectById(@Param("commentId") Long commentId, @Param("tenantId") Long tenantId);

	int insert(FeedComment comment);

	int updateStatus(@Param("commentId") Long commentId, @Param("status") String status,
			@Param("tenantId") Long tenantId);

}
