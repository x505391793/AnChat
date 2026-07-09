package com.anchat.auth;

import lombok.extern.slf4j.Slf4j;
import com.anchat.service.ConfigOptionService;
import com.anchat.utils.IpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录控制器
 * 提供登录页面跳转和登录验证接口
 * 登录成功后下发 auth_token Cookie，有效期 1 天（与 Redis TTL 同步）
 */
@Controller
@Slf4j
public class LoginController {

    /** 登录密码在 config_option 表中的 key */
    public static final String LOGIN_PASSWORD_KEY = "LOGIN_PASSWORD";

    /** 默认登录密码（首次使用需在 config_option 表中修改） */
    private static final String DEFAULT_PASSWORD = "kldwn1231";

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ConfigOptionService configOptionService;

    /**
     * GET /login → 重定向到静态登录页 login.html
     * 携带 redirect 参数，登录成功后跳回原页面
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String redirect) {
        String target = (redirect != null && !redirect.isEmpty()) ? redirect : "/";
        return "redirect:/login.html?redirect=" + target;
    }

    /**
     * POST /api/login
     * 验证密码，成功则生成 token 写入 Cookie + Redis
     *
     * @param body     请求体，包含 password 字段
     * @param request  用于获取客户端 IP
     * @param response 用于写入 Cookie
     * @return JSON：{success, message, redirect}
     */
    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> doLogin(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String password = body.getOrDefault("password", "");
        String expectedPassword = configOptionService.getValue(LOGIN_PASSWORD_KEY, DEFAULT_PASSWORD);
        String clientIp = IpUtils.getClientIp(request);
        String redirect = body.getOrDefault("redirect", "/");

        Map<String, Object> result = new HashMap<>();

        if (password.equals(expectedPassword) && !password.isEmpty()) {
            // 密码正确：生成 token，写入 Cookie
            String token = tokenService.createToken(clientIp);
            Cookie cookie = new Cookie(TokenService.TOKEN_COOKIE_NAME, token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(24 * 60 * 60); // 1 天，与 Redis TTL 保持一致
            response.addCookie(cookie);

            log.info("登录成功: ip={}", clientIp);
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("redirect", redirect);
            return ResponseEntity.ok(result);
        }

        log.warn("登录失败，密码错误: ip={}", clientIp);
        result.put("success", false);
        result.put("message", "密码错误");
        return ResponseEntity.status(401).body(result);
    }

    /**
     * POST /api/logout
     * 清除 token Cookie 并从 Redis 中删除
     */
    @PostMapping("/api/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> doLogout(
            @CookieValue(value = TokenService.TOKEN_COOKIE_NAME, required = false) String token,
            HttpServletResponse response) {

        // 从 Redis 删除 token
        tokenService.removeToken(token);

        // 清除 Cookie
        Cookie cookie = new Cookie(TokenService.TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 立即过期，触发浏览器删除
        response.addCookie(cookie);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已退出登录");
        return ResponseEntity.ok(result);
    }
}
