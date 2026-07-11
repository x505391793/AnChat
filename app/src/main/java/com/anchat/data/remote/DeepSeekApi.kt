package com.anchat.data.remote

import android.util.Log
import com.anchat.data.remote.model.ChatCompletionResponse
import com.anchat.data.remote.model.ChatRequest
import com.anchat.data.remote.ModelInfo
import com.anchat.data.remote.model.ModelListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for the DeepSeek chat completions API.
 * Currently uses non-streaming mode (stream=false) to get the full response at once.
 */
class DeepSeekApi {

    private companion object {
        const val TAG = "DeepSeekApi"
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // V4 Pro 思考可能要很久
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Send a chat completion request (non-streaming). Returns the full response
     * including content, reasoning_content, and token usage.
     */
    suspend fun sendChat(apiKey: String, apiUrl: String, request: ChatRequest): ChatCompletionResponse {
        Log.d(TAG, "sendChat: model=${request.model}, apiUrl=$apiUrl, messages=${request.messages.size}, stream=${request.stream}")
        val payload = json.encodeToString(ChatRequest.serializer(), request)
        Log.d(TAG, "请求体: $payload")
        val body = payload.toRequestBody("application/json".toMediaType())
        val fullUrl = "${apiUrl.trim().removeSuffix("/")}/chat/completions"
        val httpRequest = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    Log.d(TAG, "响应码: ${response.code}, 响应体长度: ${responseBody?.length}")
                    if (!response.isSuccessful) {
                        Log.e(TAG, "API 错误: code=${response.code}, body=$responseBody")
                        throw Exception("API 错误 (${response.code}): ${responseBody?.take(200)}")
                    }
                    if (responseBody == null) {
                        throw Exception("API 返回空响应")
                    }
                    Log.d(TAG, "响应体: ${responseBody.take(500)}")
                    json.decodeFromString<ChatCompletionResponse>(responseBody)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendChat 异常: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Fetch the available models from the DeepSeek `/models` endpoint.
     * Returns an empty list on any failure (callers fall back to the bundled list).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun getModels(apiKey: String, baseUrl: String): Flow<List<ModelInfo>> = flow {
        val fullUrl = "${baseUrl.trim().removeSuffix("/")}/models"
        val httpRequest = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val result: List<ModelInfo> = withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        emptyList<ModelInfo>()
                    } else {
                        val parsed = json.decodeFromStream<ModelListResponse>(response.body!!.byteStream())
                        parsed.data.map { ModelInfo(id = it.id, name = it.id) }
                    }
                }
            } catch (e: Exception) {
                emptyList<ModelInfo>()
            }
        }
        emit(result)
    }
}
