package com.atguigu.gmall.item.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
@Bean
    public ThreadPoolExecutor threadPoolExecutor(
            @Value("${threadPool.coreSize}")Integer coreSize,
            @Value("${threadPool.maxSize}")Integer maxSize,
            @Value("${threadPool.keepAliveTime}")Integer keepAliveTime,
            @Value("${threadPool.blockQueueSize}")Integer blockQueueSize
){
    return new ThreadPoolExecutor(coreSize,maxSize,keepAliveTime, TimeUnit.SECONDS,new ArrayBlockingQueue<>(blockQueueSize));
}
}
