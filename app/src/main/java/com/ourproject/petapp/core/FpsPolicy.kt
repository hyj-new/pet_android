package com.ourproject.petapp.core

/**
 * FPS 策略（对应妹居渲染节流语义）。
 * - resolveDefaultFps: 暂停→10fps、低端 GPU→30fps、其余→60fps
 * - frameIntervalFrames: 每 N 帧渲染一次（1 = 全帧渲染）
 * - shouldRenderTick: 纯函数判定当前帧是否渲染
 */
class FpsPolicy(
    val maxFps: Float = 60f,
    val paused: Boolean = false,
    val lowEndGpu: Boolean = false,
) {
    val defaultFps: Float = resolveDefaultFps()

    fun resolveDefaultFps(): Float {
        if (paused) return 10f
        if (lowEndGpu) return 30f
        return maxFps
    }

    /** 每 N 帧渲染一次；fps>=max 时返回 1。 */
    fun frameIntervalFrames(): Int {
        if (defaultFps <= 0f) return 1
        val ratio = maxFps / defaultFps
        return if (ratio <= 1f) 1 else kotlin.math.round(ratio).toInt().coerceAtLeast(1)
    }

    /** 当前帧（从 0 起）是否应该渲染。 */
    fun shouldRenderTick(frameIndex: Long): Boolean {
        if (paused) return frameIndex % 3L == 0L // 10fps：每 3 帧一次（60fps 基准）
        val interval = frameIntervalFrames()
        return frameIndex % interval == 0L
    }
}
