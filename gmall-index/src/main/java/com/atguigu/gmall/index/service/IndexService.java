package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.aspect.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private DistributedLock distributedLock;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:cates:";

    private static final String LOCK_PREFIX = "index:cates:lock";

    public List<CategoryEntity> queryLvl1Categories() {

        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l);
        return listResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX, timeout = 259200, random = 7000, lock = LOCK_PREFIX)
    public List<CategoryEntity> queryLvl2WithSubsPid(Long pid) {
        //1.查询缓存，缓存命中直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + pid);
        fairLock.lock();
        String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        try {
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2CatesWithSubsByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            if (CollectionUtils.isEmpty(categoryEntities)) {
                this.redisTemplate.opsForValue().set(pid.toString(), JSON.toJSONString(categoryEntities), 5, TimeUnit.MINUTES);
            } else {
                this.redisTemplate.opsForValue().set(pid.toString(), JSON.toJSONString(categoryEntities), 180l + new Random().nextInt(30), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            fairLock.unlock();
        }
    }


}
