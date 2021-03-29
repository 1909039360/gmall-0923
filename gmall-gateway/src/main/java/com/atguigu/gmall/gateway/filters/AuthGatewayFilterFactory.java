package com.atguigu.gmall.gateway.filters;

import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties properties;

    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        return (ServerWebExchange exchange, GatewayFilterChain chain) -> {
            System.out.println("我是局部过滤器，我只拦截配置了该过滤器的服务。 pathes = " + config.pathes);

            // 获取请求及响应对象 ServerHttpRequest --> HttpServletRequest
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            // 1.获取当前请求的路径，判断是否在拦截名单中，不在直接放行
            String curPath = request.getURI().getPath();
            List<String> pathes = config.pathes;
            if (pathes.stream().allMatch(path -> StringUtils.indexOf(curPath, path) < 0)) {
                return chain.filter(exchange);
            }

            // 2.获取请求中的jwt：cookie（同步） 头信息（异步）
            String token = request.getHeaders().getFirst("token");
            if (StringUtils.isBlank(token)){
                MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(this.properties.getCookieName())){
                    token = cookies.getFirst(this.properties.getCookieName()).getValue();
                }
            }

            // 3.判断jwt类型的token是否为空，重定向到登录页面。
            if (StringUtils.isBlank(token)){
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                return response.setComplete();
            }

            try {
                // 4.尝试解析jwt，如果出现异常则重定向到登录页面
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());

                // 5.获取载荷中的ip地址 和 当前请求的ip地址 比较，不同则重定向到登录
                String ip = map.get("ip").toString();
                String curIp = IpUtils.getIpAddressAtGateway(request);
                if (!StringUtils.equals(ip, curIp)){
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete();
                }

                // 6.把用户信息传递给后续服务（请求头信息）
                request.mutate().header("userId", map.get("id").toString()).build();
                exchange.mutate().request(request).build();

                // 7.放行
                return chain.filter(exchange);
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                return response.setComplete();
            }
        };
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("pathes");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Data
    public static class PathConfig {
        private List<String> pathes;
    }
}