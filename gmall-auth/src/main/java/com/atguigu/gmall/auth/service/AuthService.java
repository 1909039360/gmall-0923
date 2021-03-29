package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.UserException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Service
public class AuthService {
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private JwtProperties properties;

    public void login(String loginName, String password, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1.调用远程接口查询登陆名和密码是否正确
            ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUser(loginName, password);
            UserEntity userEntity = userEntityResponseVo.getData();
            // 2.判断用户信息是否为空
            if (userEntity == null) {
                throw new UserException("用户名或者密码错误！");
            }
            // 3.组装载荷信息： 用户id、用户名、ip(防盗用)
            Map<String, Object> map = new HashMap<>();
            map.put("id", userEntity.getId());
            map.put("username", userEntity.getUsername());
            map.put("ip", IpUtils.getIpAddressAtService(request));
            // 4.生成jwt
            String token = JwtUtils.generateToken(map, this.properties.getPrivateKey(), this.properties.getExpire());
            // 5.把jwt类型的token放入cookie中
            CookieUtils.setCookie(request, response, this.properties.getCookieName(), token, this.properties.getExpire() * 60);
            // 6.把用户的昵称放入cookie中
            CookieUtils.setCookie(request, response, this.properties.getUnick(), userEntity.getNickname(), this.properties.getExpire() * 60);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
