package com.anchat.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 管理服务
 * 使用 Redis 存储登录 token，有效期 1 天
 * Redis key 格式：AUTH_TOKEN:{token}  value：客户端 IP（用于审计）
 */
@Service
@Slf4j
public class TokenService {

    /** Redis key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "AUTH_TOKEN:";

    /** Token 有效期：1 天 */
    private static final long TOKEN_TTL_HOURS = 24;

    /** Token Cookie 名称 */
    public static final String TOKEN_COOKIE_NAME = "auth_token";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成并存储 token
     *
     * @param clientIp 客户端 IP，存入 Redis 用于审计
     * @return 新生成的 token 字符串
     */
    public String createToken(String clientIp) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = TOKEN_KEY_PREFIX + token;
        stringRedisTemplate.opsForValue().set(key, clientIp, TOKEN_TTL_HOURS, TimeUnit.HOURS);
        log.info("生成登录 token: ip={}, token={}..., 有效期={}h", clientIp, token.substring(0, 8), TOKEN_TTL_HOURS);
        return token;
    }

    /**
     * 验证 token 是否有效（存在于 Redis 中且未过期）
     *
     * @param token token 字符串
     * @return true 表示有效
     */
    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("Redis 访问异常，token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除 token（退出登录时使用）
     *
     * @param token token 字符串
     */
    public void removeToken(String token) {
        if (token != null && !token.isEmpty()) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + token);
            log.info("已删除 token: {}...", token.substring(0, Math.min(8, token.length())));
        }
    }
}
