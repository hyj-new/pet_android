package com.ourproject.petapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TtsQueueTest {
    @Test
    fun enqueue_assigns_incrementing_ids() {
        val q = OrderedTtsQueue {}
        val a = q.enqueue("a", "uri://a")
        val b = q.enqueue("b", "uri://b")
        assertEquals(0L, a.id)
        assertEquals(1L, b.id)
        assertEquals(2, q.size)
        q.close()
    }

    @Test
    fun plays_in_order() {
        val played = mutableListOf<String>()
        val q = OrderedTtsQueue { audio -> played.add(audio.text) }
        q.run()
        q.enqueue("one", "u1")
        q.enqueue("two", "u2")
        q.enqueue("three", "u3")
        Thread.sleep(400)
        q.close()
        Thread.sleep(200)
        assertEquals(listOf("one", "two", "three"), played)
    }

    @Test
    fun failed_audio_does_not_block_queue() {
        val played = mutableListOf<String>()
        val q = OrderedTtsQueue { audio ->
            if (audio.text == "bad") throw RuntimeException("fail")
            played.add(audio.text)
        }
        q.run()
        q.enqueue("bad", "u")
        q.enqueue("good", "u")
        Thread.sleep(500)
        q.close()
        Thread.sleep(200)
        assertEquals(listOf("good"), played)
    }
}

class ConversationLoopTest {
    @Test
    fun state_machine_flows_to_speaking() {
        val states = mutableListOf<LoopState>()
        val callbacks = object : PetConversationLoop.ConversationCallbacks {
            override fun onUserSpeech(text: String): String = "你好呀"
            override fun onStateChange(state: LoopState) { states.add(state) }
        }
        val loop = PetConversationLoop(callbacks)
        loop.start()
        assertEquals(LoopState.LISTENING, loop.state)
        loop.onUserSpeech("老板好")
        assertEquals(LoopState.SPEAKING, loop.state)
        assertTrue(states.contains(LoopState.THINKING))
        loop.onTtsFinished()
        assertEquals(LoopState.LISTENING, loop.state)
        loop.stop()
    }

    @Test
    fun lifecycle_invalidation_drops_reply() {
        var replied = false
        lateinit var loop: PetConversationLoop
        val callbacks = object : PetConversationLoop.ConversationCallbacks {
            override fun onUserSpeech(text: String): String {
                loop.invalidateLifecycle()
                return "不应出现"
            }
            override fun finishReply(fullReply: String) { replied = true }
        }
        loop = PetConversationLoop(callbacks)
        loop.start()
        loop.onUserSpeech("hi")
        assertEquals(false, replied)
        loop.stop()
    }

    @Test
    fun blank_reply_returns_to_listening() {
        val states = mutableListOf<LoopState>()
        val callbacks = object : PetConversationLoop.ConversationCallbacks {
            override fun onUserSpeech(text: String): String = ""
            override fun onStateChange(state: LoopState) { states.add(state) }
        }
        val loop = PetConversationLoop(callbacks)
        loop.start()
        loop.onUserSpeech("hi")
        assertEquals(LoopState.LISTENING, loop.state)
        loop.stop()
    }

    @Test
    fun consume_delta_and_finish_builds_reply() {
        val replyRef = AtomicReference("")
        val callbacks = object : PetConversationLoop.ConversationCallbacks {
            override fun onUserSpeech(text: String): String = ""
            override fun finishReply(fullReply: String) { replyRef.set(fullReply) }
        }
        val loop = PetConversationLoop(callbacks)
        loop.start()
        loop.onUserSpeech("hi")  // 空回复回到 LISTENING
        loop.consumeReplyDelta("你")
        loop.consumeReplyDelta("好")
        loop.finishReply()
        loop.stop()
        // onUserSpeech 返回空串不会进入 THINKING，因此 delta 不生效，回复为空
        assertEquals("", replyRef.get())
    }
}

class OverlayGestureTest {
    @Test
    fun tap_click_detected() {
        val g = OverlayGesture(longPressMs = 600, touchSlopPx = 10f)
        g.onDown(100f, 100f)
        Thread.sleep(50)
        val r = g.onUp(101f, 101f)
        assertEquals(GestureType.CLICK, r.type)
    }

    @Test
    fun drag_detected() {
        val g = OverlayGesture(longPressMs = 600, touchSlopPx = 10f)
        g.onDown(100f, 100f)
        val r = g.onMove(130f, 100f)
        assertEquals(GestureType.DRAG, r.type)
        assertEquals(30f, r.dx, 0.001f)
    }

    @Test
    fun long_press_detected() {
        val g = OverlayGesture(longPressMs = 50, touchSlopPx = 10f)
        g.onDown(100f, 100f)
        Thread.sleep(100)
        val r = g.checkLongPress()
        assertEquals(GestureType.LONG_PRESS, r.type)
    }

    @Test
    fun snap_to_left_edge() {
        val x = snapToEdge(20f, screenWidth = 1080f, ballSize = 100f, mode = "both")
        assertEquals(50f, x, 0.001f)
    }

    @Test
    fun snap_to_right_edge() {
        val x = snapToEdge(1060f, screenWidth = 1080f, ballSize = 100f, mode = "both")
        assertEquals(1030f, x, 0.001f)
    }

    @Test
    fun snap_off_keeps_x() {
        assertEquals(20f, snapToEdge(20f, 1080f, 100f, mode = "off"), 0.001f)
    }
}

class FpsPolicyTest {
    @Test
    fun default_fps_full() {
        assertEquals(60f, FpsPolicy().defaultFps, 0.001f)
    }

    @Test
    fun paused_10fps() {
        assertEquals(10f, FpsPolicy(paused = true).defaultFps, 0.001f)
    }

    @Test
    fun low_end_30fps() {
        assertEquals(30f, FpsPolicy(lowEndGpu = true).defaultFps, 0.001f)
    }

    @Test
    fun shouldRenderTick_30fps_pattern() {
        val p = FpsPolicy(maxFps = 60f, lowEndGpu = true)
        assertTrue(p.shouldRenderTick(0))
        assertEquals(false, p.shouldRenderTick(1))
        assertTrue(p.shouldRenderTick(2))
    }
}

class ForegroundServicePolicyTest {
    @Test
    fun full_when_both_permissions() {
        val d = ForegroundServicePolicy().decide(true, true)
        assertEquals(FgServiceType.FULL, d.type)
        assertTrue(d.reasons.isNotEmpty())
        assertTrue(ForegroundServicePolicy().requiredManifestDeclarations(d).contains("FOREGROUND_SERVICE_MEDIA_PROJECTION"))
    }

    @Test
    fun microphone_when_only_audio() {
        val d = ForegroundServicePolicy().decide(true, false)
        assertEquals(FgServiceType.MICROPHONE, d.type)
        assertTrue(ForegroundServicePolicy().requiredManifestDeclarations(d).contains("FOREGROUND_SERVICE_MICROPHONE"))
    }

    @Test
    fun special_use_fallback() {
        val d = ForegroundServicePolicy().decide(false, false)
        assertEquals(FgServiceType.SPECIAL_USE, d.type)
        assertTrue(ForegroundServicePolicy().requiredManifestDeclarations(d).contains("FOREGROUND_SERVICE_SPECIAL_USE"))
    }
}
