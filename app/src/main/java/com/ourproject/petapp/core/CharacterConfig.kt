package com.ourproject.petapp.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 角色配置（对应妹居 launchDesktopPet 的 15 参数语义）。
 * 原生层零角色数据存储，一切配置由前端/上层下发。
 */
data class CharacterConfig(
    var serverUrl: String = "",
    var authToken: String = "",
    var gender: String = "brother",
    var diaryJson: String = "",
    var accountId: String = "",
    var analyticsId: String = "",
    var promptKey: String = "sister-high",
    var charName: String = "Yuki",
    var charIdentity: String = "妹妹",
    var playerTerm: String = "哥哥",
    var portraitUrl: String = "",
    var cloudVoiceId: String = "",
    var personaPrompt: String = "",
    var promptLocale: String = "zh-CN",
    var femalePlayerPronouns: Boolean = false,
) {
    /** 返回错误信息列表；空列表 = 合法。 */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (serverUrl.isBlank()) errors.add("server_url 必填，空则不启动会话")
        if (charName.isBlank()) errors.add("char_name 不能为空")
        if (charIdentity.isBlank()) errors.add("char_identity 不能为空")
        return errors
    }

    /** 生效人设：persona_prompt 优先，其次由 prompt_key 决定。 */
    val effectivePrompt: String
        get() = personaPrompt.trim().ifEmpty { promptKey.trim() }

    /** 供日志/审计使用的脱敏快照（隐藏 token）。 */
    fun snapshot(): Map<String, Any?> {
        val d = mutableMapOf<String, Any?>(
            "serverUrl" to serverUrl,
            "authToken" to (if (authToken.isNotBlank()) "***" else ""),
            "gender" to gender,
            "charName" to charName,
            "charIdentity" to charIdentity,
            "playerTerm" to playerTerm,
            "promptKey" to promptKey,
            "promptLocale" to promptLocale,
        )
        return d
    }

    fun toJson(): String {
        val o = JSONObject()
        o.put("server_url", serverUrl)
        o.put("auth_token", authToken)
        o.put("gender", gender)
        o.put("diary_json", diaryJson)
        o.put("account_id", accountId)
        o.put("analytics_id", analyticsId)
        o.put("prompt_key", promptKey)
        o.put("char_name", charName)
        o.put("char_identity", charIdentity)
        o.put("player_term", playerTerm)
        o.put("portrait_url", portraitUrl)
        o.put("cloud_voice_id", cloudVoiceId)
        o.put("persona_prompt", personaPrompt)
        o.put("prompt_locale", promptLocale)
        o.put("female_player_pronouns", femalePlayerPronouns)
        return o.toString()
    }

    companion object {
        fun fromJson(raw: String): CharacterConfig {
            val o = JSONObject(raw)
            return CharacterConfig(
                serverUrl = o.optString("server_url", ""),
                authToken = o.optString("auth_token", ""),
                gender = o.optString("gender", "brother"),
                diaryJson = o.optString("diary_json", ""),
                accountId = o.optString("account_id", ""),
                analyticsId = o.optString("analytics_id", ""),
                promptKey = o.optString("prompt_key", "sister-high"),
                charName = o.optString("char_name", "Yuki"),
                charIdentity = o.optString("char_identity", "妹妹"),
                playerTerm = o.optString("player_term", "哥哥"),
                portraitUrl = o.optString("portrait_url", ""),
                cloudVoiceId = o.optString("cloud_voice_id", ""),
                personaPrompt = o.optString("persona_prompt", ""),
                promptLocale = o.optString("prompt_locale", "zh-CN"),
                femalePlayerPronouns = o.optBoolean("female_player_pronouns", false),
            )
        }

        /** 解析日记 JSON，结构为 [{content: ...}]；失败返回空列表。 */
        fun parseDiaryJson(raw: String): List<Map<String, String>> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    mapOf("content" to (item.optString("content") ?: ""))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
