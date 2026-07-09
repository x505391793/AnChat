package com.anchat.utils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IP 工具类
 * 1. 从请求中提取客户端真实 IP（兼容反向代理场景）
 * 2. 将逗号分隔的白名单字符串解析为规则列表
 * 3. 支持精确 IP 匹配和 CIDR 段匹配（如 192.168.1.0/24）
 */
public class IpUtils {

    /**
     * 从 HttpServletRequest 中提取客户端真实 IP
     * 优先依次读取：X-Forwarded-For → X-Real-IP → request.getRemoteAddr()
     * X-Forwarded-For 格式为 "client, proxy1, proxy2"，取第一个即真实 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getHeader("X-Real-IP");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        }
        // 将 IPv6 本地回环归一化为 127.0.0.1，避免白名单需额外配置
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    /**
     * 将逗号分隔的白名单字符串解析为规则列表
     * 例如 "127.0.0.1,192.168.1.0/24,10.0.0.5" → ["127.0.0.1", "192.168.1.0/24", "10.0.0.5"]
     *
     * @param whiteListStr 逗号分隔的 IP 规则字符串，null 或空串返回空列表
     * @return 规则列表（已去空格、去空项）
     */
    public static List<String> parseWhiteList(String whiteListStr) {
        if (whiteListStr == null || whiteListStr.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(whiteListStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 判断指定 IP 是否在白名单规则列表中
     * 支持精确匹配（如 127.0.0.1）和 CIDR 段匹配（如 192.168.1.0/24）
     *
     * @param clientIp 客户端 IP
     * @param rules    白名单规则列表
     * @return true 表示在白名单内，允许访问
     */
    public static boolean isIpAllowed(String clientIp, List<String> rules) {
        if (clientIp == null || clientIp.isEmpty() || rules == null) {
            return false;
        }
        for (String rule : rules) {
            if (rule.contains("/")) {
                // CIDR 段匹配
                if (matchCidr(clientIp, rule)) {
                    return true;
                }
            } else {
                // 精确匹配
                if (clientIp.equals(rule)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否匹配 CIDR 规则
     * 例如：192.168.1.55 匹配 192.168.1.0/24 → true
     */
    private static boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            InetAddress cidrAddress = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] cidrBytes = cidrAddress.getAddress();
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();

            // IPv4 4字节，IPv6 16字节，长度不同直接不匹配
            if (cidrBytes.length != ipBytes.length) {
                return false;
            }

            // 按 prefixLength 逐位比较
            int fullBytes = prefixLength / 8;
            int remainBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != ipBytes[i]) {
                    return false;
                }
            }

            if (remainBits > 0 && fullBytes < cidrBytes.length) {
                int mask = (0xFF << (8 - remainBits)) & 0xFF;
                if ((cidrBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}
