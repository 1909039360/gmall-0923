package com.atguigu.gmall.cart.interceptors;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;
    //public static UserInfo userInfo;
    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserInfo userInfo = new UserInfo();

        // 获取cookie中token 和 userKey
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());
        if (StringUtils.isBlank(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.jwtProperties.getUserKey(), userKey, this.jwtProperties.getExpire());
        }
        userInfo.setUserKey(userKey);

        try {
            // 解析token获取userId
            if (StringUtils.isNoneBlank(token)){
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
                userInfo.setUserId(Long.valueOf(map.get("id").toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            THREAD_LOCAL.set(userInfo);
        }
        return true;
    }

    /**
     * 可以从本地线程的局部变量中获取载荷
     * @return
     */
    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 由于这里使用的是tomcat线程池（线程不会结束），所以必须显式的调用该方法，否则会导致内存泄漏
        THREAD_LOCAL.remove();
    }
}