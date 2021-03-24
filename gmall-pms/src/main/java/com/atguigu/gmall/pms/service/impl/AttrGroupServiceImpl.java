package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrMapper attrMapper;
    @Autowired
    private SpuAttrValueMapper spuAttrValueMapper;
    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<AttrGroupEntity> queryGroupsWithAttrsByCid(Long cid) {

        // 1.根据分类id查询分组
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));

        if (CollectionUtils.isEmpty(groupEntities)) {
            return null;
        }

        // 2.遍历所有的分组，根据分组的id查询分组下的规格参数
        groupEntities.forEach(group -> {
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", group.getId()).eq("type", 1));
            group.setAttrEntities(attrEntities);
        });

        return groupEntities;
    }

    @Override
    public List<ItemGroupVo> queryGroupWithAttrValueByCidAndSpuIdAndSkuId(Long cid, Long spuId, Long skuId) {
        //根据分类id查询分组
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)) {
            return null;
        }
        //遍历分组，查询每个分组下的规格参数
        return groupEntities.stream().map(group -> {
            ItemGroupVo groupVo = new ItemGroupVo();
            groupVo.setId(group.getId());
            groupVo.setName(group.getName());
            //遍历分组,查询每个分组下的规格参数
            List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", group.getId()));
            if (CollectionUtils.isEmpty(attrEntities)) {
                return null;
            }
            List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());
            List<AttrValueVo> attrValueVos = new ArrayList<>();
            //查询基本属性及值
            List<SpuAttrValueEntity> baseAttrValues = this.spuAttrValueMapper.selectList(new QueryWrapper<SpuAttrValueEntity>().in("attr_id", attrIds).eq("spu_id", spuId));
            if (!CollectionUtils.isEmpty(baseAttrValues)) {
                attrValueVos.addAll(baseAttrValues.stream().map(spuAttrValueEntity -> {
                    AttrValueVo attrValueVo = new AttrValueVo();
                    BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);
                    return attrValueVo;
                }).collect(Collectors.toList()));
            }
            //查询销售属性及值
            List<SkuAttrValueEntity> saleAttrValues = this.skuAttrValueMapper.selectList(new QueryWrapper<SkuAttrValueEntity>().in("attr_id", attrIds).eq("sku_Id", skuId));
            if (!CollectionUtils.isEmpty(saleAttrValues)) {
                attrValueVos.addAll(baseAttrValues.stream().map(skuAttrValueEntity -> {
                    AttrValueVo attrValueVo = new AttrValueVo();
                    BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);
                    return attrValueVo;
                }).collect(Collectors.toList()));
            }
            return groupVo;
        }).collect(Collectors.toList());

    }

}
