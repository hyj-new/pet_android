package com.ourproject.petapp.core

import org.json.JSONArray

data class RecentMemory(val content: String, val index: Int)

/**
 * 日记 → 近期记忆提取与清洗（对应妹居 VoiceChatController 的关键设计）。
 * - 解析 diary JSON（每项含 content 字段）
 * - 正则清洗：时间戳行、【日记】标题行、括号语气标签
 * - 只取最近 N 条非空记忆，格式化为提示片段
 * - 异常时返回空串，不阻塞会话
 */
class MemoryExtractor(
    private val maxItems: Int = 3,
) {
    companion object {
        // 时间戳行，例如：【日记书写时间为2026年10月31日14点】
        private val TIMESTAMP_RE = Regex("""\n*\s*【日记书写时间为\d{4}年\d{1,2}月\d{1,2}日\d{1,2}点】\s*\n*""")
        // 【日记】 标题行
        private val TITLE_RE = Regex("""【日记】\s*\n+""")
        // 语音变化标签：如 (撒娇) (轻声) 等
        private val VOICE_TAG_RE = Regex(
            """[（(]\s*(?:撒娇|轻声|低语|叹气|大笑|小声|高冷|温柔|害羞|生气|哭泣|激动|平淡|俏皮|开心|难过|疑惑|兴奋|沮丧|慵懒|严肃|戏谑)\s*[)）]"""
        )

        fun cleanText(text: String): String {
            var out = text
            out = TIMESTAMP_RE.replace(out, "")
            out = TITLE_RE.replace(out, "")
            out = VOICE_TAG_RE.replace(out, "")
            return out.trim()
        }
    }

    /** 从 diary JSON 提取最近 N 条非空记忆（按原始顺序取尾部）。 */
    fun extract(diaryRaw: String): List<RecentMemory> {
        val data: JSONArray? = try {
            JSONArray(diaryRaw)
        } catch (e: Exception) {
            null
        }
        if (data == null) return emptyList()
        val cleaned = mutableListOf<RecentMemory>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val content = item.optString("content") ?: item.optString("text") ?: ""
            val c = cleanText(content)
            if (c.isNotEmpty()) cleaned.add(RecentMemory(content = c, index = i))
        }
        val from = (cleaned.size - maxItems).coerceAtLeast(0)
        return cleaned.subList(from, cleaned.size)
    }

    /** 格式化为可注入 systemPrompt 的近期记忆片段。 */
    fun formatRecentMemories(diaryRaw: String, locale: String = "zh-CN"): String {
        val memories = extract(diaryRaw)
        if (memories.isEmpty()) return ""
        val lines = mutableListOf<String>()
        lines.add(
            when {
                locale.startsWith("zh") -> "【近期记忆】"
                locale.startsWith("ja") -> "【最近の記憶】"
                locale.startsWith("en") -> "[Recent memories]"
                else -> "[近期记忆]"
            }
        )
        for (m in memories) lines.add("- ${m.content}")
        return lines.joinToString("\n")
    }
}
