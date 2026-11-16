package com.twelvet.gen.server.service.impl;

import com.twelvet.gen.api.domain.GenGroup;
import com.twelvet.gen.api.domain.GenTableColumn;
import com.twelvet.framework.utils.Convert;
import com.twelvet.gen.server.mapper.GenGroupMapper;
import com.twelvet.gen.server.mapper.GenTableColumnMapper;
import com.twelvet.gen.server.service.IGenTableColumnService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author twelvet
 * @Description: 业务字段 服务层实现
 */
@Service
public class GenTableColumnServiceImpl implements IGenTableColumnService {

	private final GenTableColumnMapper genTableColumnMapper;

	private final GenGroupMapper genGroupMapper;

	public GenTableColumnServiceImpl(GenTableColumnMapper genTableColumnMapper, GenGroupMapper genGroupMapper) {
		this.genTableColumnMapper = genTableColumnMapper;
		this.genGroupMapper = genGroupMapper;
	}

	/**
	 * 查询业务字段列表
	 * @param tableId 业务字段编号
	 * @return 业务字段集合
	 */
	@Override
	public List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId) {
		return genTableColumnMapper.selectGenTableColumnListByTableId(tableId);
	}

	/**
	 * 新增业务字段
	 * @param genTableColumn 业务字段信息
	 * @return 结果
	 */
	@Override
	public int insertGenTableColumn(GenTableColumn genTableColumn) {
		return genTableColumnMapper.insertGenTableColumn(genTableColumn);
	}

	/**
	 * 修改业务字段
	 * @param genTableColumn 业务字段信息
	 * @return 结果
	 */
	@Override
	public int updateGenTableColumn(GenTableColumn genTableColumn) {
		return genTableColumnMapper.updateGenTableColumn(genTableColumn);
	}

	/**
	 * 删除业务字段对象
	 * @param ids 需要删除的数据ID
	 * @return 结果
	 */
	@Override
	public int deleteGenTableColumnByIds(String ids) {
		return genTableColumnMapper.deleteGenTableColumnByIds(Convert.toLongArray(ids));
	}

	/**
	 * 查询代码生成业务所有模板分组列表
	 * @return List GenTemplate
	 */
	@Override
	public List<GenGroup> selectGenGroupAll() {
		return genGroupMapper.selectGenGroupAll();
	}

}
