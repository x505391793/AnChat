package com.anchat.data.config

/**
 * 模型提供方配置：唯一真相源，聊天地址全部内置，不对外暴露、不可编辑。
 * 新增一个提供方只需在 [ALL] 加一行（UI 下拉自动跟着变）。
 */
object ModelProviders {

    const val DEEPSEEK = "deepseek"
    const val AIMAT = "aimat"
    /** 旧数据 / 未知提供方：继续用记录里原有的 apiUrl */
    const val LEGACY = "legacy"

    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    const val AIMAT_BASE_URL = "https://aimat.cn/v1"

    data class Spec(val id: String, val label: String, val baseUrl: String)

    val ALL: List<Spec> = listOf(
        Spec(DEEPSEEK, "DeepSeek", DEEPSEEK_BASE_URL),
        Spec(AIMAT, "Aimat", AIMAT_BASE_URL)
    )

    fun baseUrl(provider: String): String? = ALL.firstOrNull { it.id == provider }?.baseUrl

    fun label(provider: String): String = when (provider) {
        DEEPSEEK -> "DeepSeek"
        AIMAT -> "Aimat"
        else -> "自定义地址"
    }

    /** 模型选择下拉的展示名：「模型名 [提供方]」，提供方由记录自动带出，无需用户填写 */
    fun displayName(m: ModelConfig): String =
        "${m.name.ifBlank { apiModel(m) }} [${label(resolve(m).first)}]"

    /** 真正传给 API 的 model 值。旧记录没有 modelName，回退到 id（旧版 id 就是模型名）。 */
    fun apiModel(m: ModelConfig): String = m.modelName.ifBlank { m.id }

    /** 模型逻辑唯一性 = 服务方 + 模型名；旧记录的 provider 由 resolve() 归一得出。 */
    fun sameModel(m: ModelConfig, provider: String, modelName: String): Boolean =
        apiModel(m) == modelName && resolve(m).first == provider

    /**
     * 生成新记录的引用键：默认沿用模型名本身（与旧格式一致，旧版 App 读备份仍能正常发请求）；
     * 只有该名字已被「另一个服务方」占用时，才加提供方前缀避免主键冲突。
     */
    fun nextModelId(models: List<ModelConfig>, provider: String, modelName: String): String {
        val takenByOther = models.any { it.id == modelName && !sameModel(it, provider, modelName) }
        return if (takenByOther) "$provider|$modelName" else modelName
    }

    /**
     * 归一化一条模型记录 → (provider, 实际请求基地址)。
     * - 显式 provider：一律用内置地址，用户填什么都无效。
     * - 旧数据（provider 为空）：官方地址或空 → DeepSeek；其它自定义地址原样保留，零丢失。
     * - 未知 provider（降级 / 回滚）：走原 apiUrl 兜底。
     */
    fun resolve(m: ModelConfig): Pair<String, String> {
        baseUrl(m.provider)?.let { return m.provider to it }
        val raw = m.apiUrl.trim().removeSuffix("/")
        if (raw.isBlank()) return DEEPSEEK to DEEPSEEK_BASE_URL
        return if (m.provider.isEmpty() && raw == DEEPSEEK_BASE_URL) {
            DEEPSEEK to DEEPSEEK_BASE_URL
        } else {
            LEGACY to raw
        }
    }

    /** 是否声明 response_format=json_object（DeepSeek 官方才需要）。LEGACY 沿用旧的模型名判断。 */
    fun needsJsonObject(provider: String, modelId: String): Boolean = when (provider) {
        DEEPSEEK -> true
        AIMAT -> false
        else -> modelId.contains("deepseek", ignoreCase = true)
    }
}
