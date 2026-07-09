package com.anchat.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 白名单拦截器注册配置
 * 拦截所有请求，仅排除静态资源和错误页
 * 各项目无需额外配置，引入 BaseUtils 即自动生效
 */
@Configuration
public class AuthInterceptorConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",            // 登录页跳转
                        "/login.html",       // 登录页静态资源
                        "/api/login",        // 登录接口
                        "/api/logout",       // 登出接口
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/images/**",
                        "/static/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error"
                );
    }
}
