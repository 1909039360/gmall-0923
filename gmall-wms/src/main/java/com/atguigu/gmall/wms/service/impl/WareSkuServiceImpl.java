package com.atguigu.gmall.wms.service.impl;

import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.wms.vo.SKuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {
    @Autowired
    private RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "stock:lock:";
    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SKuLockVo> checkAndLock(List<SKuLockVo> lockVos) {
        if (CollectionUtils.isEmpty(lockVos)) {
            throw new OrderException("您没有选中的商品。。。");
        }
        //遍历所有的商品，验库存并锁库存
        lockVos.forEach(sKuLockVo -> {
            this.checkLock(sKuLockVo);
        });
        // 判断是否存在锁定失败的商品，如果存在，要解锁已经锁定成功的商品的库存
        return null;
    }

    private void checkLock(SKuLockVo lockVo) {
        //添加分布式锁，保证操作的原子性
        RLock lock = this.redissonClient.getFairLock(LOCK_PREFIX + lockVo.getSkuId());
        lock.lock();
        try {
            //验库存：本质就是查询库存
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.check(lockVo.getSkuId(), lockVo.getCount());
            if (CollectionUtils.isEmpty(wareSkuEntities)) {
                lockVo.setLock(false);
                return;
            }
            //锁库存：本质更新锁定库存的数量
            //TODO: 从最近的仓库锁库存，这里我们取第一个仓库
            WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);
            if (this.wareSkuMapper.lock(wareSkuEntity.getId(), lockVo.getCount()) == 1) {
                lockVo.setLock(true);
                lockVo.setWareSkuId(wareSkuEntity.getId());
            }
        } finally {
            lock.unlock();
        }
    }

}