package com.openu60.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClashDirectClient @Inject constructor() {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val DEFAULT_SECRET = "123456"
        private const val DEFAULT_PORT = 7788
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getJSON(agentBaseURL: String, path: String): Map<String, Any?> {
        val data = request(agentBaseURL, "GET", path, null)
        val element = Json.parseToJsonElement(data)
        return element.jsonObject.toMap()
    }

    suspend fun putJSON(agentBaseURL: String, path: String, body: Map<String, Any?>): Map<String, Any?> {
        val data = request(agentBaseURL, "PUT", path, mapToJsonString(body))
        return if (data.isBlank()) emptyMap() else Json.parseToJsonElement(data).jsonObject.toMap()
    }

    suspend fun patchJSON(agentBaseURL: String, path: String, body: Map<String, Any?>): Map<String, Any?> {
        val data = request(agentBaseURL, "PATCH", path, mapToJsonString(body))
        return if (data.isBlank()) emptyMap() else Json.parseToJsonElement(data).jsonObject.toMap()
    }

    private suspend fun request(
        agentBaseURL: String,
        method: String,
        path: String,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val url = controllerBaseURL(agentBaseURL) + path
        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $DEFAULT_SECRET")

        when (method) {
            "GET" -> builder.get()
            "PUT" -> builder.put(requestBody ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(requestBody ?: "".toRequestBody(JSON_MEDIA_TYPE))
            else -> error("Unsupported method $method")
        }

        if (body != null) {
            builder.header("Content-Type", "application/json")
        }

        try {
            val response = httpClient.newCall(builder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            when (response.code) {
                in 200..299 -> responseBody
                401 -> throw IllegalStateException("Clash API 鉴权失败，请检查 secret 是否仍为默认 123456")
                else -> throw IllegalStateException(responseBody.ifBlank { "Clash HTTP ${response.code}" })
            }
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("连接 Clash 控制器超时")
        } catch (e: java.net.ConnectException) {
            throw IllegalStateException("无法连接 Clash 控制器，请确认 7788 端口可访问")
        }
    }

    private fun controllerBaseURL(agentBaseURL: String): String {
        val parsed = agentBaseURL.toHttpUrlOrNull()
        val scheme = parsed?.scheme ?: "http"
        val host = parsed?.host ?: "192.168.0.1"
        return "$scheme://$host:$DEFAULT_PORT"
    }

    private fun mapToJsonString(map: Map<String, Any?>): String {
        return buildJsonObject {
            for ((key, value) in map) {
                putAny(key, value)
            }
        }.toString()
    }
}
