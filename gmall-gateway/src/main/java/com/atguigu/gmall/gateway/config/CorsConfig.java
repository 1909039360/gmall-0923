package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){
        // 初始化cors配置类
        CorsConfiguration config = new CorsConfiguration();
        // 允许跨域访问的域名。*：代表所有域名可以跨域访问，但是不能携带cookie。要携带cookie，这里必须配置具体的域名
        config.addAllowedOrigin("http://manager.gmall.com");
        config.addAllowedOrigin("http://gmall.com");
        config.addAllowedOrigin("http://www.gmall.com");
        // 允许所有请求方式跨域访问
        config.addAllowedMethod("*");
        // 允许携带cookie
        config.setAllowCredentials(true);
        // 允许携带所有头信息
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(configurationSource);
    }
}
