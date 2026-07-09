package com.anchat.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.anchat.dao.ConfigOptionDao;
import com.anchat.pojo.ConfigOption;
import com.anchat.service.ConfigOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigOptionService 实现
 * 启动时全量预加载至内存缓存，读取优先走缓存，缓存未命中则查库回填
 * saveValue 同时写库并刷新缓存，保证读写一致
 */
@Service
@Slf4j
public class ConfigOptionServiceImpl implements ConfigOptionService {

    @Autowired
    private ConfigOptionDao configOptionDao;

    /** 内存缓存，key → value，线程安全 */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String value = cache.get(key);
        if (value != null) {
            return value;
        }
        // 缓存未命中，查库回填
        ConfigOption option = configOptionDao.findByKey(key);
        if (option != null && option.getValue() != null) {
            cache.put(key, option.getValue());
            return option.getValue();
        }
        return defaultValue;
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        String value = getValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 值格式错误: {}, 使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public void saveValue(String key, String value) {
        ConfigOption option = configOptionDao.findByKey(key);
        if (option == null) {
            option = new ConfigOption();
            option.setKey(key);
        }
        option.setValue(value);
        configOptionDao.save(option);
        cache.put(key, value);
    }

    @Override
    public void reload() {
        List<ConfigOption> all = configOptionDao.findAll();
        cache.clear();
        all.forEach(opt -> {
            if (opt.getKey() != null && opt.getValue() != null) {
                cache.put(opt.getKey(), opt.getValue());
            }
        });
        log.info("ConfigOption 预加载完成，共 {} 条配置", cache.size());
    }
}
