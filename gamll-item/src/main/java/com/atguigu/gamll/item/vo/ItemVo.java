package com.atguigu.gamll.item.vo;

import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {
    // 面包屑：一二三级分类 V
    private List<CategoryEntity> categories;
    // 面包屑：品牌信息 V
    private Long brandId;
    private String brandName;
    // 面包屑：spu信息 V
    private Long spuId;
    private String spuName;

    // sku相关信息 V
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private Integer weight;
    private String defaultImage;

    // 商品营销信息 V
    private List<ItemSaleVo> sales;
    // 是否有货 V
    private Boolean store = false;
    // 图片列表 V
    private List<SkuImagesEntity> images;

    // 销售属性列表 V：[{attrId: 3, attrName: 颜色, attrValues: ['黑色', '白色']},
    // {attrId: 4, attrName: 内存, attrValues: ['8G', '12G']},
    // {attrId: 5, attrName: 存储, attrValues: ['128G', '256G', '512G']}]
    private List<SaleAttrValueVo> saleAttrs;

    // 当前商品的销售属性JSON格式 V：{3: '黑色', 4: '8G', 5: '128G'}
    private String saleAttr;

    // 销售属性组合和skuId的映射关系 V：['黑色,8G,128G': 10, '黑色,12G,256G': 11]
    private Map<String, Object> skuJsons;

    // 商品描述 V
    private List<String> spuImages;

    // 规格参数
    private List<ItemGroupVo> groups;
}
