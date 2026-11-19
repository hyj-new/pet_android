package com.ourproject.petapp.core

enum class FgServiceType(val value: Int) {
    FULL(0x100),        // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 0x100
    MICROPHONE(0x80),   // FOREGROUND_SERVICE_TYPE_MICROPHONE 0x80
    SPECIAL_USE(0x20);  // FOREGROUND_SERVICE_TYPE_SPECIAL_USE 0x20

    companion object {
        fun fromValue(v: Int): FgServiceType? = entries.firstOrNull { it.value == v }
    }
}

data class ServiceTypeDecision(
    val type: FgServiceType,
    val reasons: List<String>,
)

/**
 * 前台服务类型决策（对应妹居 Android14 动态降级语义）：
 * - 同时持有录音+投屏权限 → FULL(0x100)
 * - 仅录音 → MICROPHONE(0x80)
 * - 兜底 → SPECIAL_USE(0x20)
 * 以及对应 Manifest 声明清单。
 */
class ForegroundServicePolicy {

    fun decide(
        hasRecordAudio: Boolean,
        hasMediaProjection: Boolean,
    ): ServiceTypeDecision {
        return when {
            hasRecordAudio && hasMediaProjection ->
                ServiceTypeDecision(FgServiceType.FULL, listOf("录音+投屏权限齐备"))
            hasRecordAudio ->
                ServiceTypeDecision(FgServiceType.MICROPHONE, listOf("仅持有录音权限"))
            else ->
                ServiceTypeDecision(FgServiceType.SPECIAL_USE, listOf("无敏感权限，使用特殊用途兜底"))
        }
    }

    /** Manifest 必须声明的服务类型属性（按决策结果补齐）。 */
    fun requiredManifestDeclarations(decision: ServiceTypeDecision): List<String> {
        val out = mutableListOf<String>()
        if (decision.type == FgServiceType.FULL) {
            out.add("FOREGROUND_SERVICE_MEDIA_PROJECTION")
        }
        if (decision.type == FgServiceType.MICROPHONE || decision.type == FgServiceType.FULL) {
            out.add("FOREGROUND_SERVICE_MICROPHONE")
        }
        if (decision.type == FgServiceType.SPECIAL_USE) {
            out.add("FOREGROUND_SERVICE_SPECIAL_USE")
        }
        return out
    }
}
