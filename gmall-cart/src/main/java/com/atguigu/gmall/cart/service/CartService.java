package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptors.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService asyncService;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    public void saveCart(Cart cart) {
        // 1.获取登录状态：未登录-userKey 登录-userId
        String userId = getUserId();
        String key = KEY_PREFIX + userId;

        // 2.判断当前用户的购物车中是否包含该记录:redisTemplate.opsForHash()
        // 内层的map<skuId, cart>
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);

        String skuIdString = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        if (hashOps.hasKey(skuIdString)) {
            // 包含，更新数量
            String json = hashOps.get(skuIdString).toString();
            cart = JSON.parseObject(json, Cart.class);
            // 数量累加
            cart.setCount(cart.getCount().add(count));
            // 把更新数量之后的cart写入redis 和 mysql
            this.asyncService.updateCart(userId, cart.getSkuId(), cart);
        } else {
            // 不包含，新增一条记录
            cart.setCheck(true);
            cart.setUserId(userId);
            // 查询sku的信息
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new CartException("您加入购物车的商品不存在！！");
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 库存
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 营销信息d
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            cart.setSales(JSON.toJSONString(itemSaleVos));

            // 销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValueBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrsResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 新增到redis 和 mysql
            this.asyncService.insertCart(userId,cart);
            // 加入购物车时，添加实时价格缓存
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuIdString, skuEntity.getPrice().toString());
        }
        hashOps.put(skuIdString, JSON.toJSONString(cart));
    }


    /**
     * 封装统一获取用户信息
     * 登录返回userId
     * 未登录返回的userKey
     *
     * @return
     */
    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userId = "";
        // 外层的key
        if (userInfo.getUserId() != null) {
            userId = userInfo.getUserId().toString();
        } else {
            userId = userInfo.getUserKey();
        }
        return userId;
    }

    public Cart queryCartBySkuId(Long skuId, Integer count) {
        String userId = this.getUserId();

        // 获取内层的map操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(skuId.toString())) {
            String json = hashOps.get(skuId.toString()).toString();
            Cart cart = JSON.parseObject(json, Cart.class);
            cart.setCount(new BigDecimal(count));
            return cart;
        } else {
            throw new CartException("当前用户不包含该购物车记录");
        }
    }

    public List<Cart> queryCarts() {
        // 1.获取userkey 查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userKey);
        List<Object> unloginCartJsons = unLoginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)) {
            unloginCarts = unloginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        // 2.获取userId，判断userId是否为空，为空说明未登录，直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unloginCarts;
        }

        // 3.userId不为空，合并未登录的购物车到登录状态的购物车
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!CollectionUtils.isEmpty(unloginCarts)) {
            // 遍历未登录的购物车，合并到登录状态的购物车中
            unloginCarts.forEach(cart -> {
                String skuIdString = cart.getSkuId().toString();
                BigDecimal count = cart.getCount();
                if (loginHashOps.hasKey(skuIdString)) {
                    // 如果登录状态的购物车已经包含了该记录，则累加数量
                    String cartJson = loginHashOps.get(skuIdString).toString();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    // 保存到redis，并异步保存到mysql
                    this.asyncService.updateCart(userId.toString(), cart.getSkuId(), cart);
                } else {
                    // 不包含，则新增一条记录
                    cart.setUserId(userId.toString());
                    this.asyncService.insertCart(userId.toString(),cart);
                }
                loginHashOps.put(skuIdString, JSON.toJSONString(cart));
            });

            // 4.删除未登录的购物
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.asyncService.deleteCartByUserId(userKey);
        }

        // 5.查询登录状态的购物车并返回
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)) {
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        return null;
    }

    @Async
    public String executor1() {
        try {
            System.out.println("executor1开始执行-----------");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("executor1结束执行===================");
        } catch (InterruptedException e) {
            //return AsyncResult.forExecutionException(e);
        }
        return "executor1";
    }

    @Async
    public String executor2() {
        try {
            System.out.println("executor2开始执行-----------");
            TimeUnit.SECONDS.sleep(5);
            int i = 1 / 0;
            System.out.println("executor2结束执行===================");
        } catch (InterruptedException e) { //return AsyncResult.forExecutionException(e);
        }
        return "executor2";
    }


    public void updateNum(Cart cart) {
        String userId = this.getUserId();
        //获取内层的map操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(cart.getSkuId().toString())) {
            BigDecimal count = cart.getCount();
            String json = hashOps.get(cart.getSkuId().toString()).toString();
            cart = JSON.parseObject(json, Cart.class);
            cart.setCount(count);
            // 更新到数据库，redis  mysql
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            this.asyncService.updateCart(userId, cart.getSkuId(), cart);
            return;
        }
        throw new CartException("该用户的用户车不包含该记录！");
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        hashOps.delete(skuId.toString());
        this.asyncService.deleteCartByUserIdAndSkuId(userId, skuId);
    }

    public List<Cart> queryCheckedCarts(Long userId) {
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        List<Object> cartJsons = hashOps.values();
        if(!CollectionUtils.isEmpty(cartJsons)){
            return  cartJsons.stream().map(cartJson->JSON.parseObject(cartJson.toString(),Cart.class)).filter(Cart::getCheck).collect(Collectors.toList());
        }
        return null;
    }
}