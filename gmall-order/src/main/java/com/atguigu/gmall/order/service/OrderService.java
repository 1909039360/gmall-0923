package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;

import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptors.LoginInterceptor;
import com.atguigu.gmall.order.pojo.UserInfo;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;

import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String KEY_PREFIX = "order:token:";

    public OrderConfirmVo confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();

        // 从拦截器中获取用户id
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        // 查询送货地址列表
        ResponseVo<List<UserAddressEntity>> addressesResponseVo = this.umsClient.queryAddressesByUserId(userId);
        List<UserAddressEntity> userAddressEntities = addressesResponseVo.getData();
        confirmVo.setAddresses(userAddressEntities);

        // 查询送货清单
        ResponseVo<List<Cart>> cartsResponseVo = this.cartClient.queryCheckedCarts(userId);
        List<Cart> carts = cartsResponseVo.getData();
        if (CollectionUtils.isEmpty(carts)){
            throw new OrderException("您没有选中的购物车记录！");
        }
        List<OrderItemVo> itemVos = carts.stream().map(cart -> { // 只取skuId count
            OrderItemVo orderItemVo = new OrderItemVo();

            orderItemVo.setSkuId(cart.getSkuId());
            orderItemVo.setCount(cart.getCount());

            // 根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                orderItemVo.setDefaultImage(skuEntity.getDefaultImage());
                orderItemVo.setTitle(skuEntity.getTitle());
                orderItemVo.setPrice(skuEntity.getPrice());
                orderItemVo.setWeight(skuEntity.getWeight());
            }

            // 根据skuId查询该商品的销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrValueBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            orderItemVo.setSaleAttrs(skuAttrValueEntities);

            // 根据skuId查询库存
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)){
                orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 根据skuId查询营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            orderItemVo.setSales(itemSaleVos);

            return orderItemVo;
        }).collect(Collectors.toList());
        confirmVo.setOrderItems(itemVos);

        // 根据用户id查询用户信息：bounds
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            confirmVo.setBounds(userEntity.getIntegration());
        }

        // 防重：浏览器一份  redis一份
        String orderToken = IdWorker.getIdStr();
        confirmVo.setOrderToken(orderToken);
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 1, TimeUnit.HOURS);

        return confirmVo;
    }

    public void submit(OrderSubmitVo submitVo) {

        String orderToken = submitVo.getOrderToken();
        if (StringUtils.isBlank(orderToken)){
            throw new OrderException("非法提交！！");
        }
        // 1.防重：页面orderToken  redis，为了保证原子性使用lua脚本
        String script = "if(redis.call('get', KEYS[1]) == ARGV[1]) then return redis.call('del', KEYS[1]) else  return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag){
            throw new OrderException("请不要重复提交！");
        }

        // 2.验总价
        BigDecimal totalPrice = submitVo.getTotalPrice();
        // 获取页面上的送货清单
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)){
            throw new OrderException("您没有要购买的商品");
        }
        // 获取数据库中的实时总价
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                return skuEntity.getPrice().multiply(item.getCount()); // 实时价格小计
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        if (currentTotalPrice.compareTo(totalPrice) != 0){
            throw new OrderException("页面已过期，请刷新后重试");
        }

        // 3.验库存并锁定库存
        List<SkuLockVo> skuLockVos = items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount().intValue());
            return skuLockVo;
        }).collect(Collectors.toList());
        ResponseVo<List<SkuLockVo>> wareResponseVo = this.wmsClient.checkAndLock(skuLockVos, orderToken);
        List<SkuLockVo> skuLockVoList = wareResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVoList)){
            throw new OrderException(JSON.toJSONString(skuLockVoList));
        }

        int i = 1/0;

        // 4.下单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        try {
            this.omsClient.saveOrder(submitVo, userId);
            // 定时关单
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.ttl", orderToken);
        } catch (Exception e) {
            // 调用失败了或者时oms宕机了  响应失败了
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.failure", orderToken);
            throw new OrderException("服务器错误。。。");
        }

        // 5.异步删除购物车中对应的记录
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        message.put("skuIds", JSON.toJSONString(skuIds));
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", message);
    }
}