package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import com.atguigu.gmall.sms.mapper.SkuFullReductionMapper;
import com.atguigu.gmall.sms.mapper.SkuLadderMapper;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import org.bouncycastle.jcajce.provider.symmetric.AES;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.sms.mapper.SkuBoundsMapper;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsMapper, SkuBoundsEntity> implements SkuBoundsService {

    @Autowired
    private SkuLadderMapper ladderMapper;
    @Autowired
    private SkuFullReductionMapper reductionMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuBoundsEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public void saveSales(SkuSaleVo saleVo) {
        //  3.1. 保存sms_sku_bounds
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        BeanUtils.copyProperties(saleVo, skuBoundsEntity);
        List<Integer> work = saleVo.getWork();
        if (!CollectionUtils.isEmpty(work)){
            skuBoundsEntity.setWork(work.get(3) * 8 + work.get(2) * 4 + work.get(1) * 2 + work.get(0));
        }
        this.save(skuBoundsEntity);

        //  3.2. 保存sms_sku_full_reduction
        SkuFullReductionEntity reductionEntity = new SkuFullReductionEntity();
        BeanUtils.copyProperties(saleVo, reductionEntity);
        reductionEntity.setAddOther(saleVo.getFullAddOther());
        this.reductionMapper.insert(reductionEntity);

        //  3.3. 保存sms_sku_ladder
        SkuLadderEntity ladderEntity = new SkuLadderEntity();
        BeanUtils.copyProperties(saleVo, ladderEntity);
        ladderEntity.setAddOther(saleVo.getLadderAddOther());
        this.ladderMapper.insert(ladderEntity);

    }

    @Override
    public List<ItemSaleVo> querySalesBySkuId(Long skuId) {
        ArrayList<ItemSaleVo> itemSaleVos = new ArrayList<>();
        // 1积分查询
        SkuBoundsEntity skuBoundsEntity = this.getOne(new QueryWrapper<SkuBoundsEntity>().eq("sku_id", skuId));
        if(skuBoundsEntity != null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setType("积分");
            itemSaleVo.setDesc("送"+skuBoundsEntity.getGrowBounds()+"成长积分,送"+skuBoundsEntity.getBuyBounds()+"购物积分");
            itemSaleVos.add(itemSaleVo);
        }
        // 2查询满减
       SkuFullReductionEntity reductionEntity = this.reductionMapper.selectOne(new QueryWrapper<SkuFullReductionEntity>().eq("sku_id",skuId));
        if(reductionEntity != null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setType("满减");
            itemSaleVo.setDesc("满" +reductionEntity.getFullPrice()+"减"+reductionEntity.getReducePrice());
            itemSaleVos.add(itemSaleVo);
        }
        // 3查询打折
        SkuLadderEntity ladderEntity = this.ladderMapper.selectOne(new QueryWrapper<SkuLadderEntity>().eq("sku_id",skuId));
        if(ladderEntity != null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setType("打折");
            itemSaleVo.setDesc("满"+ladderEntity.getFullCount()+"折打"+ladderEntity.getDiscount().divide(new BigDecimal(10))+"折");
            itemSaleVos.add(itemSaleVo);
        }
        return itemSaleVos;
    }

}
