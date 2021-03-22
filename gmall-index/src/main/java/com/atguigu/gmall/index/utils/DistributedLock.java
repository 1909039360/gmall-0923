package com.atguigu.gmall.index.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Component
public class DistributedLock {
    @Autowired
    private StringRedisTemplate redisTemplate;
    private Timer timer;

    public Boolean tryLock(String lockName, String uuid, Integer expire) {
        String script = "if(redis.call('exists',KEYS[1]) == 0 or redis.call('hexists',KEYS[1],KEYS[2])==1) then redis.call('hincrby',KEYS[1],KEYS[2],1)redis.call('expire',KEYS[1],ARGV[1])return 1 else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName, uuid), expire.toString());
        if (!flag) {
            try {
                Thread.sleep(100);
                tryLock(lockName, uuid, expire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.renewExpire(lockName, uuid, expire);
        return true;
    }

    public void unlock(String lockName, String uuid) {
        String script = "if(redis.call('hexists', KEYS[1], KEYS[2]) == 0) then return nil elseif (redis.call('hincrby', KEYS[1], KEYS[2], -1)== 0)then return redis.call('del', KEYS[1])else return 0 end";
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(lockName, uuid));
        if (flag == null) {
            throw new RuntimeException("要解的锁不存在,或者不属于你");
        } else if (flag == 1) {
            timer.cancel();
        }
    }

    private void renewExpire(String lockName, String uuid, Integer expire) {
        String script = "if(redis.call('hexists',KEYS[1],KEYS[2]) == 1 ) then return redis.call('expire','lock',ARGV[1]) else return 0 end";
        timer = new Timer();
        timer.schedule((new TimerTask() {
            @Override
            public void run() {
                redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName, uuid), expire.toString());
            }
        }), expire * 1000 / 3, expire * 1000 / 3);
    }
}

