package com.atguigu.gmall.wms.mapper;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * εεεΊε­
 * 
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2021-03-10 15:51:17
 */
@Mapper
public interface WareSkuMapper extends BaseMapper<WareSkuEntity> {
	public List<WareSkuEntity>check(@Param("skuId")Long skuId,@Param("count")Integer count);
    public Integer lock(@Param("id")Long id,@Param("count")Integer count);
    public Integer unlock(@Param("id")Long id,@Param("count")Integer count);
}
