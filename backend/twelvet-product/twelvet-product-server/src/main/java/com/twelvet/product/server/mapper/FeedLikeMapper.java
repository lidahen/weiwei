package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Component("productFeedLikeMapper")
@Mapper
public interface FeedLikeMapper {

	int insertLike(@Param("feedId") Long feedId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

	int deleteLike(@Param("feedId") Long feedId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

	Long countLike(@Param("feedId") Long feedId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
