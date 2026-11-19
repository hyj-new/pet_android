package com.ourproject.petapp.core

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * TTS 双路：本地系统引擎（无需凭据）或云端 OpenAI 兼容 /v1/audio/speech。
 * 播放结束回调 onDone，供会话状态机回到 LISTENING。
 */
class TtsProvider(context: Context) {

    private val ctx = context.applicationContext
    private val pool = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var sysTts: TextToSpeech? = null
    @Volatile private var sysReady = false
    @Volatile private var playing = false

    init {
        sysTts = TextToSpeech(ctx) { status ->
            sysReady = (status == TextToSpeech.SUCCESS)
            sysTts?.setLanguage(Locale.CHINESE)
        }
    }

    val isSystemTtsReady: Boolean get() = sysReady

    /** 打断当前播放。 */
    fun stop() {
        playing = false
        sysTts?.stop()
    }

    /**
     * 播报一段文本。
     * @param mode local=系统引擎；cloud=云端接口
     */
    fun speak(
        text: String,
        mode: String,
        cloudBaseUrl: String,
        cloudApiKey: String,
        cloudModel: String,
        cloudVoice: String,
        onDone: () -> Unit,
    ) {
        if (text.isBlank()) {
            onDone()
            return
        }
        if (mode.equals("cloud", ignoreCase = true) && cloudBaseUrl.isNotBlank()) {
            pool.execute {
                try {
                    val audio = synthCloud(text, cloudBaseUrl, cloudApiKey, cloudModel, cloudVoice)
                    playAudio(audio, onDone)
                } catch (e: Exception) {
                    mainHandler.post { speak(text, "local", "", "", "", "", onDone) }
                }
            }
        } else {
            speakLocal(text, onDone)
        }
    }

    private fun speakLocal(text: String, onDone: () -> Unit) {
        val tts = sysTts
        if (tts == null || !sysReady) {
            // 引擎未就绪则静默完成
            mainHandler.post(onDone)
            return
        }
        playing = true
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                playing = false
                mainHandler.post(onDone)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                playing = false
                mainHandler.post(onDone)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                playing = false
                mainHandler.post(onDone)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun synthCloud(
        text: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        voice: String,
    ): ByteArray {
        val url = baseUrl.trimEnd('/') + "/audio/speech"
        val payload = "{\"model\":\"$model\",\"voice\":\"$voice\",\"input\":${jsonEscape(text)}}"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000
            conn.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw RuntimeException("TTS HTTP $code: ${err.take(200)}")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun playAudio(audio: ByteArray, onDone: () -> Unit) {
        try {
            val file = File(ctx.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(file).use { it.write(audio) }
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { p ->
                playing = false
                mainHandler.post(onDone)
                p.release()
            }
            player.setOnErrorListener { p, _, _ ->
                playing = false
                mainHandler.post(onDone)
                p.release()
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            playing = false
            mainHandler.post(onDone)
        }
    }

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') sb.append("\\u%04x".format(c.code))
                    else sb.append(c)
                }
            }
        }
        return "\"$sb\""
    }
}
