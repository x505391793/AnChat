package com.anchat.service;

/**
 * 配置项统一读取服务
 * 所有模块获取配置值必须通过此接口，禁止直接注入 ConfigOptionDao
 */
public interface ConfigOptionService {

    /**
     * 获取字符串配置值
     *
     * @param key          配置项 key
     * @param defaultValue 不存在或为 null 时的默认值
     * @return 配置值，或 defaultValue
     */
    String getValue(String key, String defaultValue);

    /**
     * 获取 int 配置值
     *
     * @param key          配置项 key
     * @param defaultValue 不存在、为 null 或格式错误时的默认值
     * @return 配置值，或 defaultValue
     */
    int getIntValue(String key, int defaultValue);

    /**
     * 保存/更新配置项（upsert：key 存在则更新，不存在则新增）
     *
     * @param key   配置项 key
     * @param value 配置项 value
     */
    void saveValue(String key, String value);

    /**
     * 从数据库重新加载所有配置到内存缓存
     * 调用场景：外部修改配置后需立即生效时调用
     */
    void reload();
}
