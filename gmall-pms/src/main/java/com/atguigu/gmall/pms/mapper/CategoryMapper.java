package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品三级分类
 * 
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2021-03-08 14:58:56
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {


    List<CategoryEntity> queryLvl2CateWithSubsByPid(Long pid);

}
