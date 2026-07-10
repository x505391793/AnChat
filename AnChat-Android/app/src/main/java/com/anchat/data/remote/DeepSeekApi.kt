package com.anchat.data.remote

import com.anchat.data.remote.model.ChatRequest
import com.anchat.data.remote.model.ModelInfo
import com.anchat.data.remote.model.ModelListResponse
import com.anchat.data.remote.model.StreamChunk
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Thin client for the DeepSeek chat completions API. Mirrors the request-body
 * construction that used to live in the Spring Boot `AnChatServiceImpl`.
 *
 * This is the ONLY outbound network call in the app.
 */
class DeepSeekApi {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Stream a chat completion. Emits one string per content delta. Completes
     * normally when the API sends `[DONE]`.
     */
    fun streamChat(apiKey: String, request: ChatRequest): Flow<String> = callbackFlow {
        val payload = json.encodeToString(ChatRequest.serializer(), request)
        val body = payload.toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(DeepSeekConstants.API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                runCatching {
                    json.decodeFromString<StreamChunk>(data)
                }.getOrNull()?.choices?.firstOrNull()?.delta?.content?.let { token ->
                    if (token.isNotEmpty()) trySend(token)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                // Surface the error so the collector can show it.
                val message = response?.body?.string()
                    ?: t?.message
                    ?: "network error"
                trySend("<error>$message</error>")
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }

    /**
     * Fetch the available models from the DeepSeek `/models` endpoint.
     * Returns an empty list on any failure (callers fall back to the bundled list).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun getModels(apiKey: String): Flow<List<ModelInfo>> = flow {
        val httpRequest = Request.Builder()
            .url(DeepSeekConstants.MODELS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val result = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching emptyList<ModelInfo>()
                    val parsed = json.decodeFromStream<ModelListResponse>(response.body!!.byteStream())
                    parsed.data.map { ModelInfo(id = it.id, name = it.id) }
                }.getOrDefault(emptyList())
            }
        }
        emit(result)
    }
}
