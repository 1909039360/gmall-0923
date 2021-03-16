package com.atguigu.gmall.pms.api;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GmallPmsApi {

    @PostMapping("pms/spu/json")
    public ResponseVo<List<SpuEntity>> querySpuPage(@RequestBody PageParamVo paramVo);

    @GetMapping("pms/sku/spu/{spuId}")
    public ResponseVo<List<SkuEntity>> querySkusBySpuId(@PathVariable("spuId")Long spuId);

    @GetMapping("pms/brand/{id}")
    public ResponseVo<BrandEntity> queryBrandById(@PathVariable("id") Long id);

    @GetMapping("pms/category/{id}")
    public ResponseVo<CategoryEntity> queryCategoryById(@PathVariable("id") Long id);

    @GetMapping("pms/skuattrvalue/category/{cid}")
    public ResponseVo<List<SkuAttrValueEntity>> querySearchAttrValuesBySkuId(
            @PathVariable("cid")Long cid,
            @RequestParam("skuId")Long skuId
    );

    @GetMapping("pms/spuattrvalue/category/{cid}")
    public ResponseVo<List<SpuAttrValueEntity>> querySearchAttrValuesBySpuId(
            @PathVariable("cid")Long cid,
            @RequestParam("spuId")Long spuId
    );
}