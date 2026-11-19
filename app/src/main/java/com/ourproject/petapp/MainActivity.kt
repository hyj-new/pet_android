package com.ourproject.petapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ourproject.petapp.core.AppConfig
import com.ourproject.petapp.core.LlmProvider
import com.ourproject.petapp.core.TtsProvider

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var config: AppConfig
    private lateinit var tts: TtsProvider

    private lateinit var etLlmUrl: EditText
    private lateinit var etLlmModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etTtsMode: EditText
    private lateinit var etInput: EditText
    private lateinit var tvReply: TextView

    private val history = mutableListOf<LlmProvider.Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = AppConfig.load(this)
        tts = TtsProvider(this)

        statusText = findViewById(R.id.status_text)
        etLlmUrl = findViewById(R.id.et_llm_url)
        etLlmModel = findViewById(R.id.et_llm_model)
        etApiKey = findViewById(R.id.et_api_key)
        etTtsMode = findViewById(R.id.et_tts_mode)
        etInput = findViewById(R.id.et_input)
        tvReply = findViewById(R.id.tv_reply)

        etLlmUrl.setText(config.llmBaseUrl)
        etLlmModel.setText(config.llmModel)
        etApiKey.setText(config.llmApiKey)
        etTtsMode.setText(config.ttsMode)

        findViewById<Button>(R.id.btn_grant).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_start).setOnClickListener { startPetService() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, PetOverlayService::class.java))
            statusText.text = "桌宠已停止"
        }
        findViewById<Button>(R.id.btn_save_config).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.btn_send).setOnClickListener { sendChat() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        statusText.text = if (overlay) "悬浮窗权限：已授予 | LLM: ${config.llmModel}" else "悬浮窗权限：未授予"
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            refreshStatus()
        }
    }

    private fun startPetService() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "请先授予悬浮窗权限"
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, PetOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "桌宠服务已启动"
    }

    private fun saveConfig() {
        config.llmBaseUrl = etLlmUrl.text.toString().trim()
        config.llmModel = etLlmModel.text.toString().trim()
        config.llmApiKey = etApiKey.text.toString().trim()
        config.ttsMode = etTtsMode.text.toString().trim().ifEmpty { "local" }
        AppConfig.save(this, config)
        statusText.text = "配置已保存（本机）"
    }

    private fun sendChat() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) {
            statusText.text = "输入为空"
            return
        }
        saveConfig()
        history.add(LlmProvider.Message("user", text))
        tvReply.append("你：$text\n")
        etInput.setText("")
        statusText.text = "思考中..."
        val cfg = config.copy()
        LlmProvider.chatAsync(
            baseUrl = cfg.llmBaseUrl,
            apiKey = cfg.llmApiKey,
            model = cfg.llmModel,
            systemPrompt = cfg.llmSystemPrompt,
            history = history,
            callback = object : LlmProvider.LlmCallback {
                override fun onReply(reply: String) {
                    runOnUiThread {
                        history.add(LlmProvider.Message("assistant", reply))
                        tvReply.append("${cfg.charName}：$reply\n")
                        statusText.text = "回复完成，播放语音"
                        tts.speak(
                            text = reply,
                            mode = cfg.ttsMode,
                            cloudBaseUrl = cfg.ttsBaseUrl,
                            cloudApiKey = cfg.ttsApiKey,
                            cloudModel = cfg.ttsModel,
                            cloudVoice = cfg.ttsVoice,
                            onDone = {
                                runOnUiThread { statusText.text = "语音播放完成" }
                            }
                        )
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        tvReply.append("（错误：$message）\n")
                        statusText.text = "请求失败"
                    }
                }
            }
        )
    }
}
