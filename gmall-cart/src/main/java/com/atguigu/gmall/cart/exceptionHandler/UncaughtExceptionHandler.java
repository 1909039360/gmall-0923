package com.atguigu.gmall.cart.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@Slf4j
public class UncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... objects) {
        // 输出错误日志，或者记录到数据库
        log.error("异步执行出错。方法：{}，参数：{}，异常信息：{}", method.getName(), Arrays.asList(objects), throwable.getMessage());
    }
}