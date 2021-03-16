package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {
    //品牌过滤条件
    private List<BrandEntity> brands;
    //分类的过滤条件
    private List<CategoryEntity> categories;
    //规格参数的过滤条件
    private List<SearchResponseAttrVo> filters;
    //分页参数
    private Integer pageNum;
    private Integer pageSize;
    //总记录数
    private Long total;
    //商品列表
    private List<Goods> goodsList;
}
