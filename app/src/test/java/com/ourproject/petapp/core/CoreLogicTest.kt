package com.ourproject.petapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterConfigTest {
    @Test
    fun fromJson_roundtrip() {
        val raw = """
            {"server_url":"https://example.com","auth_token":"tok123","gender":"brother",
             "diary_json":"[{\"content\":\"a\"}]","account_id":"acc","analytics_id":"an",
             "prompt_key":"sister-high","char_name":"Yuki","char_identity":"妹妹",
             "player_term":"哥哥","portrait_url":"u","cloud_voice_id":"v",
             "persona_prompt":"","prompt_locale":"zh-CN","female_player_pronouns":false}
        """.trimIndent()
        val cfg = CharacterConfig.fromJson(raw)
        assertEquals("https://example.com", cfg.serverUrl)
        assertEquals("tok123", cfg.authToken)
        assertEquals("Yuki", cfg.charName)
        assertEquals("妹妹", cfg.charIdentity)
        assertEquals("sister-high", cfg.effectivePrompt)
        val back = CharacterConfig.fromJson(cfg.toJson())
        assertEquals(cfg.charName, back.charName)
        assertEquals(cfg.serverUrl, back.serverUrl)
    }

    @Test
    fun validate_detects_blank_required() {
        val cfg = CharacterConfig()
        val errors = cfg.validate()
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun parseDiaryJson_handles_bad_input() {
        assertTrue(CharacterConfig.parseDiaryJson("not json").isEmpty())
        assertTrue(CharacterConfig.parseDiaryJson("").isEmpty())
        val parsed = CharacterConfig.parseDiaryJson("""[{"content":"hello"}]""")
        assertEquals(1, parsed.size)
        assertEquals("hello", parsed[0]["content"])
    }
}

class MemoryExtractorTest {
    @Test
    fun cleanText_removes_timestamp_title_and_tags() {
        val input = "\n【日记书写时间为2026年10月31日14点】\n【日记】\n今天心情很好(撒娇)，买了奶茶。\n"
        val out = MemoryExtractor.cleanText(input)
        assertEquals("今天心情很好，买了奶茶。", out)
    }

    @Test
    fun extract_takes_recent_three() {
        val raw = """
            [{"content":"第一天的日记(开心)"},{"content":"第二天的日记"},{"content":"第三天"},
             {"content":"第四天"},{"content":"第五天"}]
        """.trimIndent()
        val mems = MemoryExtractor().extract(raw)
        assertEquals(3, mems.size)
        assertEquals("第三天", mems[0].content)
        assertEquals("第五天", mems[2].content)
    }

    @Test
    fun formatRecentMemories_locale_zh() {
        val raw = """[{"content":"记忆A"}]"""
        val out = MemoryExtractor().formatRecentMemories(raw)
        assertTrue(out.contains("【近期记忆】"))
        assertTrue(out.contains("记忆A"))
    }

    @Test
    fun formatRecentMemories_empty_diary() {
        assertEquals("", MemoryExtractor().formatRecentMemories(""))
    }
}
