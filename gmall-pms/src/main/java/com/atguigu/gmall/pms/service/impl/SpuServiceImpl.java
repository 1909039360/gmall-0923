package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescService descService;
    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService imagesService;

    @Autowired
    private SkuAttrValueService saleAttrService;

    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidOrKeyPage(PageParamVo paramVo, Long cid) {

        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        // 判断cid是否为0，如果不为0 根据分类查询
        if (cid != 0) {
            wrapper.eq("category_id", cid);
        }

        // 获取查询关键字
        String key = paramVo.getKey();
        // 如果关键字不为空，拼接查询条件
        if (StringUtils.isNotBlank(key)) {
            // and后需要小括号，所以此处使用了and（Consumer）
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @Override
    @GlobalTransactional
    public void bigSave(SpuVo spu) throws FileNotFoundException {
        // 1. 保存spu的相关信息
        //  1.1. 保存pms_spu
        Long spuId = saveSpuInfo(spu);

        //  1.2. 保存pms_spu_desc
        //this.saveSpuDesc(spu, spuId);
        this.descService.saveSpuDesc(spu, spuId);

        //int i = 1/0;
//        new FileInputStream("xxxx");
//        try {
//            TimeUnit.SECONDS.sleep(4);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        //  1.3. 保存pms_spu_attr_value
        saveBaseAttr(spu, spuId);

        // 2. 保存sku的相关信息
        saveSkuInfo(spu, spuId);

        //int i = 1/0;
        this.rabbitTemplate.convertAndSend("PMS_ITEM_)EXCHANGE","item.insert",spuId);
    }

    private void saveSkuInfo(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }
        skus.forEach(skuVo -> {
            //  2.1. 保存pms_sku
            skuVo.setSpuId(spuId); // 设置sku所属spu的id
            // 设置sku中分类的id和品牌的id
            skuVo.setCategoryId(spu.getCategoryId());
            skuVo.setBrandId(spu.getBrandId());
            // 设置sku的默认图片：取第一张图片
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)) {
                skuVo.setDefaultImage(StringUtils.isNotBlank(skuVo.getDefaultImage()) ? skuVo.getDefaultImage() : images.get(0));
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            //  2.2. 保存pms_sku_images
            if (!CollectionUtils.isEmpty(images)) {
                this.imagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity imagesEntity = new SkuImagesEntity();
                    imagesEntity.setSkuId(skuId);
                    imagesEntity.setUrl(image);
                    imagesEntity.setDefaultStatus(StringUtils.equals(skuVo.getDefaultImage(), image) ? 1 : 0);
                    return imagesEntity;
                }).collect(Collectors.toList()));
            }

            //  2.3. 保存pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            saleAttrs.forEach(saleAttr -> saleAttr.setSkuId(skuId));
            this.saleAttrService.saveBatch(saleAttrs);

            // 3. 保存营销相关信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.smsClient.saveSales(skuSaleVo);
        });
    }

    private void saveBaseAttr(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            this.spuAttrValueService.saveBatch(baseAttrs.stream().map(spuAttrValueVo -> {
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);
                spuAttrValueEntity.setSpuId(spuId);
                return spuAttrValueEntity;
            }).collect(Collectors.toList()));
        }
    }


    private Long saveSpuInfo(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

//    public static void main(String[] args) {
//        List<User> users = Arrays.asList(
//                new User(1l, "柳岩", 20),
//                new User(2l, "马蓉", 21),
//                new User(3l, "小鹿", 22),
//                new User(4l, "芳芳", 23),
//                new User(5l, "一搏", 24),
//                new User(6l, "老王", 25),
//                new User(7l, "小贾", 26)
//        );
//        // 过滤filter 转化map 总和reduce
//        System.out.println(users.stream().filter(user -> user.getAge() > 22).collect(Collectors.toList()));
//        System.out.println(users.stream().map(User::getName).collect(Collectors.toList()));
//        System.out.println(users.stream().map(user -> {
//            Person person = new Person();
//            person.setUserName(user.getName());
//            person.setAge(user.getAge());
//            return person;
//        }).collect(Collectors.toList()));
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//    }

}

//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User{
//    Long id;
//    String name;
//    Integer age;
//}
//
//@Data
//class Person {
//    String userName;
//    Integer age;
//}
