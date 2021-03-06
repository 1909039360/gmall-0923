package com.atguigu.gmall.pms.service.impl;

import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.CategoryMapper;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.service.CategoryService;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<CategoryEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<CategoryEntity> queryCategoriesByPid(Long pid) {

        QueryWrapper<CategoryEntity> wrapper = new QueryWrapper<>();
        if (pid != -1) {
            wrapper.eq("parent_id", pid);
        }
        return this.list(wrapper);
    }

    @Override
    public List<CategoryEntity> queryLvl2CateWithSubsByPid(Long pid) {

        return this.categoryMapper.queryLvl2CateWithSubsByPid(pid);
    }

    @Override
    public List<CategoryEntity> queryLvlAllCategoriesByCid3(Long cid) {
        CategoryEntity categoryEntity3 = this.getById(cid);
        if (categoryEntity3 == null) {
            return null;
        }
        CategoryEntity categoryEntity2 = this.getById(categoryEntity3.getParentId());
        CategoryEntity categoryEntity1 = this.getById(categoryEntity2.getParentId());
        return Arrays.asList(categoryEntity1,categoryEntity2,categoryEntity3);
    }

}
