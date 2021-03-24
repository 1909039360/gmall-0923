package com.atguigu.gamll.item.service;

import com.atguigu.gamll.item.feign.GmallPmsClient;
import com.atguigu.gamll.item.feign.GmallSmsClient;
import com.atguigu.gamll.item.feign.GmallWmsClient;
import com.atguigu.gamll.item.vo.ItemVo;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ItemService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallSmsClient smsClient;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();
//        1.根据skuId查询sku
//        2.根据三级分类的id查询一二三级分类
//        3.根据品牌id查询品牌
//        4.根据spuId查询spu
//        5.根据skuId查询营销信息（sms）
//        6.根据skuId查询商品库存信息
//        7.根据skuId查询sku的图片列表
//        8.根据spuId查询所有Sku的销售属性
//        9.根据skuId查询当前sku的销售属性
//        10.根据spuId查询spu下所有销售属性组合和skuId的映射关系
//        11.根据spuId查询描述信息
//        12.根据分类Id、spuId、skuId查询分组及组下规格参数和值
        return null;
    }
}
