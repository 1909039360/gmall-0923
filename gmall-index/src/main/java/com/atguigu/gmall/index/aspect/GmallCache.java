package com.atguigu.gmall.index.aspect;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {
   //缓存key的前缀
    String prefix() default "";
    //缓存的过期时间
    int timeout() default 1440;
    //防止缓存雪崩，给缓存时间添加随机值范围，默认五天
    int random() default 50;
    //为了防止缓存击穿，添加分布式锁前缀
    String lock() default "lock:";
}
