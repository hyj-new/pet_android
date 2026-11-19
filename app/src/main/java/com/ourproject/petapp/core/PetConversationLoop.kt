package com.ourproject.petapp.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class LoopState { IDLE, LISTENING, THINKING, SPEAKING }

data class LoopOptions(
    var maxContextTurns: Int = 8,
    var ttsEnabled: Boolean = true,
    var memoryEnabled: Boolean = true,
    var thinkingTimeoutMs: Long = 15_000,
    var speakingTimeoutMs: Long = 30_000,
)

/**
 * 会话回路状态机：LISTENING → THINKING → SPEAKING → LISTENING。
 * 核心回调（对应 README 接入表）：
 * - onUserSpeech: ASR 结果进入
 * - consumeReplyDelta / finishReply: LLM 增量输出
 * - enqueueTtsAudio: 将回复文本排队 TTS
 * - lifecycleVersion 失效校验：版本变化即中断当前回合
 */
class PetConversationLoop(
    private val callbacks: ConversationCallbacks,
    private val options: LoopOptions = LoopOptions(),
) {
    interface ConversationCallbacks {
        /** 收到用户语音/文本输入，返回回复文本（同步或异步完成由实现决定）。 */
        fun onUserSpeech(text: String): String
        /** LLM 增量回调，用于流式 UI。 */
        fun consumeReplyDelta(delta: String) {}
        /** 整句回复完成。 */
        fun finishReply(fullReply: String) {}
        /** 将回复入队 TTS，返回可识别 id（可返回 -1 表示不播）。 */
        fun enqueueTtsAudio(text: String): Long = -1L
        /** 进入某状态时回调。 */
        fun onStateChange(state: LoopState) {}
    }

    private val stateRef = AtomicReference<LoopState>(LoopState.IDLE)
    private val lifecycleRef = AtomicLong(0)
    private val running = AtomicBoolean(false)
    private val contextTurns = AtomicInteger(0)
    @Volatile private var replyBuffer = StringBuilder()

    val state: LoopState get() = stateRef.get()
    val lifecycleVersion: Long get() = lifecycleRef.get()

    fun start() {
        running.set(true)
        contextTurns.set(0)
        setState(LoopState.LISTENING)
    }

    fun stop() {
        running.set(false)
        invalidateLifecycle()
        setState(LoopState.IDLE)
    }

    /** 使当前回合失效（如角色切换、停止）。 */
    fun invalidateLifecycle() {
        lifecycleRef.incrementAndGet()
    }

    private fun setState(s: LoopState) {
        stateRef.set(s)
        callbacks.onStateChange(s)
    }

    /** ASR 入口。 */
    fun onUserSpeech(text: String) {
        if (!running.get() || text.isBlank()) return
        val version = lifecycleRef.get()
        setState(LoopState.THINKING)
        replyBuffer = StringBuilder()
        contextTurns.incrementAndGet()
        val reply = callbacks.onUserSpeech(text)
        // 回调期间 lifecycle 失效则丢弃
        if (lifecycleRef.get() != version) return
        if (reply.isBlank()) {
            setState(LoopState.LISTENING)
            return
        }
        appendReply(reply)
        finishReplyInternal(version)
    }

    /** 追加回复文本到缓冲并转发增量回调。 */
    private fun appendReply(text: String) {
        replyBuffer.append(text)
        callbacks.consumeReplyDelta(text)
    }

    fun consumeReplyDelta(delta: String) {
        if (!running.get() || state != LoopState.THINKING) return
        replyBuffer.append(delta)
        callbacks.consumeReplyDelta(delta)
    }

    fun finishReply() {
        if (!running.get() || state != LoopState.THINKING) return
        finishReplyInternal(lifecycleRef.get())
    }

    private fun finishReplyInternal(version: Long) {
        if (lifecycleRef.get() != version) return
        val full = replyBuffer.toString().trim()
        callbacks.finishReply(full)
        if (options.ttsEnabled && full.isNotEmpty()) {
            setState(LoopState.SPEAKING)
            callbacks.enqueueTtsAudio(full)
        }
        // TTS 完成后由外部调用 onTtsFinished() 回到 LISTENING
    }

    /** 由 TTS 播放结束回调调用，回到听候状态。 */
    fun onTtsFinished() {
        if (state == LoopState.SPEAKING) setState(LoopState.LISTENING)
    }

    val contextTurnCount: Int get() = contextTurns.get()
}
