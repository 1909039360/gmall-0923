package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class SkuAttrValueServiceImplTest {
@Autowired
private SkuAttrValueService attrValueService;
    @Test
    void querySaleAttrsMappingSkuIdBySpuId() {
        this.attrValueService.querySaleAttrsMappingSkuIdBySpuId(0l);
    }
}