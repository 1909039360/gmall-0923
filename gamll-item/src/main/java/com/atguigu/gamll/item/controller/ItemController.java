package com.atguigu.gamll.item.controller;

import com.atguigu.gamll.item.service.ItemService;
import com.atguigu.gamll.item.vo.ItemVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ItemController {
    @Autowired
    private ItemService itemService;

    @GetMapping("{skuId}.html")
    @ResponseBody
    public ResponseVo<ItemVo> loadData(@PathVariable("skuId") Long skuId) {
        ItemVo itemVo = this.itemService.loadData(skuId);
        return ResponseVo.ok(itemVo);
    }
}
