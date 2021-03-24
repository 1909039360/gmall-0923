package com.atguigu.gamll.item;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class GamllItemApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamllItemApplication.class, args);
    }

}
