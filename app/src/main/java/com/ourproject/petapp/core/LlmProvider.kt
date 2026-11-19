package com.ourproject.petapp.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * OpenAI 兼容 LLM 客户端（零第三方依赖，HttpURLConnection）。
 * 支持本地 Ollama / LM Studio 及任意 OpenAI 兼容网关。
 * 非流式调用，返回整句后回调。
 */
object LlmProvider {

    interface LlmCallback {
        fun onReply(reply: String)
        fun onError(message: String)
    }

    data class Message(val role: String, val content: String)

    private val pool = Executors.newFixedThreadPool(2)

    fun chatAsync(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Message>,
        callback: LlmCallback,
    ) {
        pool.execute {
            try {
                val reply = chat(baseUrl, apiKey, model, systemPrompt, history)
                callback.onReply(reply)
            } catch (e: Exception) {
                callback.onError(e.message ?: "llm_error")
            }
        }
    }

    /** 同步调用，失败抛异常。 */
    fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<Message>,
    ): String {
        if (baseUrl.isBlank()) throw IllegalArgumentException("LLM 地址未配置")
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject()
        payload.put("model", model)
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        for (m in history) {
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        payload.put("messages", messages)
        payload.put("temperature", 0.7)
        payload.put("stream", false)

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val body = readBody(conn, code)
            if (code !in 200..299) {
                throw RuntimeException("LLM HTTP $code: ${body.take(300)}")
            }
            val json = JSONObject(body)
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.trim() ?: ""
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        stream ?: return ""
        val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
        return reader.use { it.readText() }
    }
}
