package com.ourproject.petapp.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class AudioState { PENDING, PLAYING, DONE, FAILED }

data class TtsAudio(
    val id: Long,
    val text: String,
    val audioUri: String,
    val state: AudioState = AudioState.PENDING,
)

typealias TtsPlayHandler = (TtsAudio) -> Unit

/**
 * 有序 TTS 队列（对应妹居 TtsQueue 乱序补偿设计）。
 * - 按自增 id 严格顺序播放：非队首则等待
 * - 队首播放完成后推进
 * - 单播放线程消费；close 后清空并停止
 * - mark_failed 同样推进队列，避免卡死
 */
class OrderedTtsQueue(
    private val playHandler: TtsPlayHandler,
) {
    private val queue = LinkedBlockingQueue<TtsAudio>()
    private val closed = AtomicBoolean(false)
    private val seq = AtomicLong(0)
    private val playingId = AtomicReference<Long?>(null)
    @Volatile private var worker: Thread? = null

    fun enqueue(text: String, audioUri: String): TtsAudio {
        val audio = TtsAudio(id = seq.getAndIncrement(), text = text, audioUri = audioUri)
        queue.offer(audio)
        return audio
    }

    /** 播放线程中调用：标记当前播放完成并推进。 */
    fun markDone(id: Long) {
        if (playingId.get() == id) playingId.set(null)
    }

    /** 播放失败同样推进队列，避免永久卡死。 */
    fun markFailed(id: Long) {
        if (playingId.get() == id) playingId.set(null)
    }

    /** 启动消费线程。 */
    fun run() {
        if (worker != null) return
        val t = Thread(::consumeLoop, "pet-tts-queue")
        t.isDaemon = true
        worker = t
        t.start()
    }

    private fun consumeLoop() {
        while (!closed.get()) {
            val audio = try {
                queue.poll(300, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                break
            } ?: continue

            while (!closed.get()) {
                val cur = playingId.get()
                if (cur == null || cur == audio.id) break
                Thread.sleep(30)
            }
            if (closed.get()) break
            playingId.set(audio.id)
            try {
                playHandler(audio)
                // 同步播放完成自动推进；异步场景由外部调用 markDone
                if (playingId.get() == audio.id) markDone(audio.id)
            } catch (e: Exception) {
                markFailed(audio.id)
            }
        }
    }

    fun close() {
        closed.set(true)
        queue.clear()
        playingId.set(null)
        worker?.interrupt()
        worker = null
    }

    val size: Int get() = queue.size
    val isClosed: Boolean get() = closed.get()
}
