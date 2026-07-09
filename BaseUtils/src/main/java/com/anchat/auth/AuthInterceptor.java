package com.anchat.auth;

import lombok.extern.slf4j.Slf4j;
import com.anchat.service.ConfigOptionService;
import com.anchat.utils.IpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * IP 白名单 + Token 登录拦截器
 * 鉴权优先级：
 *   1. auth_token Cookie（Redis 中有效）→ 直接放行
 *   2. IP 在白名单内 → 直接放行
 *   3. 否则 → 302 跳转到 /login 登录页
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    /** 白名单配置项在 config_option 表中的 key */
    public static final String WHITE_LIST_IP_KEY = "WHITE_LIST_IP";

    @Autowired
    private ConfigOptionService configOptionService;

    @Autowired
    private TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = IpUtils.getClientIp(request);

        // ① 优先检查 Token Cookie：有效则直接放行
        String token = getTokenFromCookie(request);
        if (token != null && tokenService.isValid(token)) {
            return true;
        }

        // ② 检查 IP 白名单
        String whiteListStr = configOptionService.getValue(WHITE_LIST_IP_KEY, "");
        List<String> rules = IpUtils.parseWhiteList(whiteListStr);

        // 白名单为空时拒绝所有请求（保护性兆底，避免配置丢失导致裸奔）
        if (rules.isEmpty()) {
            log.warn("WHITE_LIST_IP 配置为空，拒绝所有请求");
            redirectToLogin(request, response);
            return false;
        }

        if (IpUtils.isIpAllowed(clientIp, rules)) {
            return true;
        }

        // ③ IP 不在白名单，跳转到登录页
        log.warn("IP 不在白名单内，跳转登录页: ip={}", clientIp);
        redirectToLogin(request, response);
        return false;
    }

    /**
     * 从请求 Cookie 中读取 auth_token
     */
    private String getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TokenService.TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value != null && !value.isEmpty()) ? value : null;
            }
        }
        return null;
    }

    /**
     * 302 跳转到登录页，携带当前请求路径作为 redirect 参数
     */
    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = (queryString != null) ? requestUri + "?" + queryString : requestUri;
        String redirect = URLEncoder.encode(fullPath, StandardCharsets.UTF_8.name());
        response.sendRedirect(request.getContextPath() + "/login?redirect=" + redirect);
    }
}
