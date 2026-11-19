# pet_android

Android 桌面宠物助手：**悬浮球桌宠 + OpenAI 兼容大模型对话 + 双路 TTS 语音播报 + 前台服务常驻**。

纯 Kotlin 实现，对话与语音均走 OpenAI 兼容协议，可接本地 Ollama / LM Studio，也可接任意兼容云端网关，不依赖第三方网络 SDK。

## 功能

- **悬浮球桌宠**：通过「显示在其它应用上层」权限在任意界面显示宠物球，支持拖动、贴边吸附、点击呼出会话、长按关闭
- **大模型对话**：调用 OpenAI 兼容 `/chat/completions`，非流式整句返回；默认指向本地 Ollama（`http://10.0.2.2:11434/v1`，模型 `qwen2.5:3b`），真机可改为局域网地址
- **双路 TTS**：优先云端 `/v1/audio/speech`（返回 mp3 写缓存后用 `MediaPlayer` 播放），失败自动回退系统 `TextToSpeech`（中文）
- **前台服务常驻**：`PetOverlayService` 以 `foregroundServiceType="specialUse|microphone"` 运行，并按实际运行时授权动态降级前台服务类型
- **配置持久化**：LLM 地址 / 模型 / API Key、TTS 模式、角色名、称呼等存入 `SharedPreferences`
- **单元测试**：JVM 测试覆盖配置模型、记忆抽取、TTS 队列、会话状态机、帧率策略

## 页面与组件

| 类型 | 名称 | 说明 |
|---|---|---|
| Activity | `MainActivity` | 配置与会话页（`singleTop`、LAUNCHER 入口）：保存配置、授权、启停服务、发送消息 |
| Service | `PetOverlayService` | 悬浮窗前台服务：通知渠道、悬浮球视图、手势、贴边吸附 |
| Layout | `overlay_pet` | 悬浮球视图（`pet_ball`、`pet_state`） |

`MainActivity` 主要控件：`status_text`、`btn_grant`、`btn_start`、`btn_stop`、`et_llm_url`、`et_llm_model`、`et_api_key`、`et_tts_mode`、`btn_save_config`、`et_input`、`btn_send`、`tv_reply`。

## 核心类

| 类 | 职责 |
|---|---|
| `MainActivity` | 申请悬浮窗权限、启停服务、保存配置、发消息并驱动 TTS 播报 |
| `PetOverlayService` | 前台服务与悬浮窗管理：`startForegroundCompat`、通知渠道、`addOverlayView`、触摸处理、`snapToEdge`、`openChatActivity` |
| `LlmProvider` | OpenAI 兼容对话客户端，零第三方依赖（`HttpURLConnection`），Bearer 鉴权，解析 `choices[0].message.content` |
| `TtsProvider` | 双路 TTS：本地 `TextToSpeech`（`QUEUE_ADD` + `UtteranceProgressListener`）与云端 `/v1/audio/speech`，云端失败回退本地 |
| `PetConversationLoop` | 会话状态机 `IDLE / LISTENING / THINKING / SPEAKING`，上下文 8 轮上限、思考 15s 超时、播报 30s 超时 |
| `CharacterConfig` | 15 项角色参数（服务地址、鉴权、角色名与身份、称呼、头像、云端音色、人设提示词、语言、代词、日记等），含 `validate()` 与 JSON 序列化 |
| `OverlayGesture` | 手势判定 `DRAG / LONG_PRESS / CLICK`（移动阈值 10dp、长按 600ms）与边缘吸附 |
| `MemoryExtractor` | 从日记原文正则抽取最近 3 条记忆，清洗时间戳与语气标签 |
| `OrderedTtsQueue` | 严格顺序音频队列（`PENDING / PLAYING / DONE / FAILED`） |
| `FpsPolicy` | 帧率策略：暂停 10fps、低端 GPU 30fps、其余取最大 |
| `ForegroundServicePolicy` | 前台服务类型位与所需 Manifest 声明判定 |

测试：`CoreLogicTest.kt`、`MoreCoreLogicTest.kt`。

## 权限

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 悬浮球显示在其它应用上层 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 桌面宠物常驻（`PROPERTY_SPECIAL_USE_FGS_SUBTYPE="pet_overlay"`） |
| `FOREGROUND_SERVICE_MICROPHONE` | 前台服务类型声明 |
| `RECORD_AUDIO` | 麦克风权限声明 |
| `POST_NOTIFICATIONS` | Android 13+ 常驻通知 |
| `INTERNET` | 访问 LLM / TTS 服务 |

其他：`allowBackup=false`，`usesCleartextTraffic=true`（便于局域网明文调用本地 Ollama）。

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin 2.2.10（`jvmTarget = JVM_17`） |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |
| applicationId | `com.ourproject.petapp` |
| Android Gradle Plugin | 8.5.2 |
| 依赖 | core-ktx 1.13.1、appcompat 1.7.0、junit 4.13.2、org.json 20240303 |

## 运行

1. Android Studio 打开工程并同步（需本机 SDK 34）
2. 安装到 Android 8.0（API 26）及以上设备
3. 首次使用点击「授权」按钮，手动授予「显示在其它应用上层」；Android 13+ 还需通知权限
4. 填入可用的 OpenAI 兼容地址与模型（真机请把 `10.0.2.2` 换成电脑的局域网 IP），保存后点击「启动」

## 已知限制

- **工程缺少 Gradle Wrapper**（无 `gradle/wrapper`、无 `gradlew`），需依赖本机 Gradle 或 Android Studio 提供的版本，构建可行性尚未验证
- 对话为非流式整句返回，暂无逐字输出
- 麦克风与录音能力目前仅体现为前台服务类型与权限声明，源码中未发现录音实现
- 根目录保留 `build_apk.log`、`build_test.log` 历史构建日志

## License

[MIT](LICENSE)
