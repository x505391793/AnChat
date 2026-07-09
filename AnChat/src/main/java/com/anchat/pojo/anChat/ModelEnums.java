package com.anchat.pojo.anChat;

public enum ModelEnums {

    DEEPSEEK_V4_PRO("deepseek-v4-pro", false),

    // 开启 Thinking
    DEEPSEEK_V4_FLASH("deepseek-v4-flash", true),

    // 不开启 Thinking，但 model 字段仍然是 deepseek-v4-flash
    DEEPSEEK_V4_FLASH_CHAT("deepseek-v4-flash-chat", false);

    private final String value;
    private final boolean thinkingEnabled;

    ModelEnums(String value, boolean thinkingEnabled) {
        this.value = value;
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getValue() {
        return value;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public static boolean needThinking(String modelName) {
        for (ModelEnums model : values()) {
            if (model.name().equals(modelName)) {
                return model.thinkingEnabled;
            }
        }
        return false;
    }

    public static String getModelValue(String modelName) {
        for (ModelEnums model : values()) {
            if (model.name().equals(modelName)) {
                return model.value;
            }
        }
        return modelName;
    }
}