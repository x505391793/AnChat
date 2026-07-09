package com.anchat.common;

import lombok.extern.slf4j.Slf4j;
import com.anchat.pojo.anChat.ModelEnums;
import com.anchat.service.ConfigOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AnChat 模块配置常量
 * 项目启动时自动从 config_option 表加载配置到静态字段
 * 业务层统一从此类取配置，禁止直接调用 ConfigOptionDao
 */
@Configuration
@Slf4j
public class AnChatConstants {

    // ==================== 数据库配置项 key ====================

    public static final String KEY_MAX_SLICE_CHARS = "MAX_SLICE_CHARS";
    public static final String KEY_MIN_CONTEXT_TURNS = "MIN_CONTEXT_TURNS";
    public static final String KEY_ANCHAT_MODEL = "ANCHAT_MODEL";

    // ==================== 默认值（数据库无记录时使用） ====================

    public static final int DEFAULT_MAX_SLICE_CHARS = 20000;
    public static final int DEFAULT_MIN_CONTEXT_TURNS = 10;
    public static final String DEFAULT_ANCHAT_MODEL = ModelEnums.DEEPSEEK_V4_PRO.getValue();

    // ==================== 运行时配置值（启动后赋值） ====================

    public static int maxSliceChars = DEFAULT_MAX_SLICE_CHARS;
    public static int minContextTurns = DEFAULT_MIN_CONTEXT_TURNS;
    public static String anChatModel = DEFAULT_ANCHAT_MODEL;

    // ==================== 版本时间（项目启动时间） ====================

    public static String startupTime = "";
    private static final DateTimeFormatter STARTUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String HIDDEN_PROMPT = "";

    @Autowired
    private ConfigOptionService configOptionService;

    @PostConstruct
    public void init() {
        if (startupTime.isEmpty()) {
            startupTime = LocalDateTime.now().format(STARTUP_TIME_FORMATTER);
        }
        maxSliceChars = configOptionService.getIntValue(KEY_MAX_SLICE_CHARS, DEFAULT_MAX_SLICE_CHARS);
        minContextTurns = configOptionService.getIntValue(KEY_MIN_CONTEXT_TURNS, DEFAULT_MIN_CONTEXT_TURNS);
        HIDDEN_PROMPT = configOptionService.getValue("HIDDEN_PROMPT", "");
        anChatModel = configOptionService.getValue(KEY_ANCHAT_MODEL, DEFAULT_ANCHAT_MODEL);
        log.info("AnChatConstants 预加载完成");
    }

    /**
     * 提供外部刷新入口（可选）
     */
    public void reload() {
        init();
    }
}
