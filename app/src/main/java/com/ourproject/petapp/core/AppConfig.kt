package com.ourproject.petapp.core

import android.content.Context
import org.json.JSONObject

/**
 * 运行配置（服务商配置由用户自改，Agent 不代填凭据）。
 * 存储于 SharedPreferences，UI 编辑后持久化。
 * LLM 默认指向本地 Ollama（模拟器内用 10.0.2.2 访问宿主机）。
 */
data class AppConfig(
    var llmBaseUrl: String = "http://10.0.2.2:11434/v1",
    var llmApiKey: String = "",
    var llmModel: String = "qwen2.5:3b",
    var llmSystemPrompt: String = "你是一个桌面宠物小助手，请用简体中文简短回答，保持活泼可爱的语气。",
    var ttsMode: String = "local", // local | cloud
    var ttsBaseUrl: String = "",
    var ttsApiKey: String = "",
    var ttsModel: String = "tts-1",
    var ttsVoice: String = "alloy",
    var memoryEnabled: Boolean = true,
    var charName: String = "小马",
    var playerTerm: String = "老板",
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("llm_base_url", llmBaseUrl)
        o.put("llm_api_key", llmApiKey)
        o.put("llm_model", llmModel)
        o.put("llm_system_prompt", llmSystemPrompt)
        o.put("tts_mode", ttsMode)
        o.put("tts_base_url", ttsBaseUrl)
        o.put("tts_api_key", ttsApiKey)
        o.put("tts_model", ttsModel)
        o.put("tts_voice", ttsVoice)
        o.put("memory_enabled", memoryEnabled)
        o.put("char_name", charName)
        o.put("player_term", playerTerm)
        return o.toString()
    }

    companion object {
        private const val PREFS = "pet_app_config"
        private const val KEY = "config_json"

        fun fromJson(raw: String): AppConfig {
            val o = try {
                JSONObject(raw)
            } catch (e: Exception) {
                JSONObject()
            }
            return AppConfig(
                llmBaseUrl = o.optString("llm_base_url", "http://10.0.2.2:11434/v1"),
                llmApiKey = o.optString("llm_api_key", ""),
                llmModel = o.optString("llm_model", "qwen2.5:3b"),
                llmSystemPrompt = o.optString("llm_system_prompt", "你是一个桌面宠物小助手，请用简体中文简短回答，保持活泼可爱的语气。"),
                ttsMode = o.optString("tts_mode", "local"),
                ttsBaseUrl = o.optString("tts_base_url", ""),
                ttsApiKey = o.optString("tts_api_key", ""),
                ttsModel = o.optString("tts_model", "tts-1"),
                ttsVoice = o.optString("tts_voice", "alloy"),
                memoryEnabled = o.optBoolean("memory_enabled", true),
                charName = o.optString("char_name", "小马"),
                playerTerm = o.optString("player_term", "老板"),
            )
        }

        fun load(ctx: Context): AppConfig {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = sp.getString(KEY, "") ?: ""
            return if (raw.isBlank()) AppConfig() else fromJson(raw)
        }

        fun save(ctx: Context, cfg: AppConfig) {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            sp.edit().putString(KEY, cfg.toJson()).apply()
        }
    }
}
