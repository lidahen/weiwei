package com.twelvet.product.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户地址Mapper
 *
 * @author twelvet
 */
@Mapper
public interface UserAddressMapper {

	/**
	 * 查询用户地址列表
	 * @param userId 用户ID
	 * @return 地址列表
	 */
	List<Map<String, Object>> selectAddressList(@Param("userId") Long userId);

	/**
	 * 新增地址
	 * @param userId 用户ID
	 * @param receiver 收件人
	 * @param mobile 手机号
	 * @param province 省
	 * @param city 市
	 * @param district 区
	 * @param detail 详细地址
	 * @param isDefault 是否默认
	 * @return 结果
	 */
	int insertAddress(@Param("userId") Long userId, @Param("receiver") String receiver, @Param("mobile") String mobile,
			@Param("province") String province, @Param("city") String city, @Param("district") String district,
			@Param("detail") String detail, @Param("isDefault") Object isDefault);

	/**
	 * 取消用户所有默认地址
	 * @param userId 用户ID
	 * @return 结果
	 */
	int clearDefaultAddress(@Param("userId") Long userId);

	/**
	 * 删除地址
	 * @param addressId 地址ID
	 * @param userId 用户ID
	 * @return 结果
	 */
	int deleteAddress(@Param("addressId") Long addressId, @Param("userId") Long userId);

	/**
	 * 查询单条地址（下单时校验归属并取快照）
	 * <p>
	 * 带 {@code user_id} 条件，地址不存在或不属于该用户时返回 null—— 归属校验与读取一步完成，防止用他人地址下单。
	 * </p>
	 * @param addressId 地址ID
	 * @param userId 用户ID（归属校验）
	 * @return 地址信息，无权或不存在时为 null
	 */
	Map<String, Object> selectAddressById(@Param("addressId") Long addressId, @Param("userId") Long userId);

}
