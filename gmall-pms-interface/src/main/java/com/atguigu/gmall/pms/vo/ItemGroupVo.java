package com.atguigu.gmall.pms.vo;

import lombok.Data;

import java.util.List;

@Data
public class ItemGroupVo {
    private String name;
    private Long id;
    private List<AttrValueVo> attrValues;
}
