package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping
    public String saveCart(Cart cart){
        this.cartService.saveCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId() + "&count=" + cart.getCount();
    }

    @GetMapping("addCart.html")
    public String queryCartBySkuId(@RequestParam("skuId")Long skuId, @RequestParam("count")Integer count, Model model){
        Cart cart = this.cartService.queryCartBySkuId(skuId, count);
        model.addAttribute("cart", cart);
        return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts", carts);
        return "cart";
    }
    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }
    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }
    @GetMapping("user/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCarts(@PathVariable("userId")Long userId){
        List<Cart> carts =  this.cartService.queryCheckedCarts(userId);
        return  ResponseVo.ok(carts);
    }
    @GetMapping("test")
    @ResponseBody
    private String test(HttpServletRequest request){
        //System.out.println("这是controller的测试方法" + LoginInterceptor.userInfo);
        //System.out.println(request.getAttribute("userId") + " ======= " + request.getAttribute("userKey"));
        //System.out.println(LoginInterceptor.getUserInfo());
        long now = System.currentTimeMillis();
        System.out.println("controller中的异步开始。。。。。。");
        this.cartService.executor1();
        this.cartService.executor2();
//        future1.addCallback(result -> System.out.println("调用成功future1: " + result), ex -> System.out.println("调用失败future1：" + ex.getMessage()));
//        future2.addCallback(result -> System.out.println("调用成功future2: " + result), ex -> System.out.println("调用失败future2：" + ex.getMessage()));
//        try {
//            System.out.println("获取到子任务的返回结果集future1：" + future1.get());
//            System.out.println("获取到子任务的返回结果集future2：" + future2.get());
//        } catch (Exception e) {
//            System.out.println("捕获到子任务的一场信息：" + e.getMessage());
//        }
        System.out.println("controller中的异步执行结束。。。。。。。。。。。。。。。" + (System.currentTimeMillis() - now));
        return "hello test";
    }


}