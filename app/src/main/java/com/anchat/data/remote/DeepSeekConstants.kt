package com.anchat.data.remote

/**
 * Constants for talking to DeepSeek. The endpoint and model ids are kept in sync
 * with the original Spring Boot contract (`api.deepseek.com`, ModelEnums).
 */
object DeepSeekConstants {
    const val API_URL = "https://api.deepseek.com/chat/completions"
    const val MODELS_URL = "https://api.deepseek.com/models"

    /** Bundled model list (mirrors the old `ModelEnums`).
     *  Note: deepseek-v4-flash-chat was a UI alias in the Spring Boot version;
     *  it was converted to "deepseek-v4-flash" before the API call.
     *  Here we just use the real API IDs directly. */
    val MODELS: List<ModelInfo> = listOf(
        ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", "最强推理与生成能力"),
        ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", "均衡的速度与质量")
    )
}

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String = ""
)
