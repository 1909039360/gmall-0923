package com.atguigu.gmall.wms.vo;

import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.Data;

@Data
public class SkuLockVo {
    private Long skuId;
    private Integer count;

    private Boolean lock; //锁库存的状态
    private Long wareSkuId;//在锁定成功的情况下，记录锁定成功的仓库id，以方便来解锁
}
