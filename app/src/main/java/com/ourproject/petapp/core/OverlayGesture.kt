package com.ourproject.petapp.core

enum class GestureType { NONE, DRAG, LONG_PRESS, CLICK }

data class GestureResult(
    val type: GestureType,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
)

/**
 * 悬浮球手势状态机（对应妹居悬浮球交互语义）。
 * - 移动阈值 10dp：超过判定 DRAG
 * - 长按 600ms：判定 LONG_PRESS
 * - 抬起：未拖动且未长按 → CLICK
 */
class OverlayGesture(
    private val longPressMs: Long = 600L,
    private val touchSlopPx: Float = 10f,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var longPressed = false
    private var isDown = false

    fun onDown(x: Float, y: Float) {
        isDown = true
        dragging = false
        longPressed = false
        downX = x
        downY = y
        lastX = x
        lastY = y
        downTimeMs = System.currentTimeMillis()
    }

    fun onMove(x: Float, y: Float): GestureResult {
        if (!isDown) return GestureResult(GestureType.NONE)
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        if (!dragging && !longPressed) {
            val totalDx = x - downX
            val totalDy = y - downY
            if (kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()) > touchSlopPx) {
                dragging = true
            }
        }
        return if (dragging && !longPressed) {
            GestureResult(GestureType.DRAG, dx, dy, x, y)
        } else {
            GestureResult(GestureType.NONE, 0f, 0f, x, y)
        }
    }

    fun checkLongPress(): GestureResult {
        if (!isDown || dragging || longPressed) return GestureResult(GestureType.NONE)
        val elapsed = System.currentTimeMillis() - downTimeMs
        if (elapsed >= longPressMs) {
            longPressed = true
            return GestureResult(GestureType.LONG_PRESS, 0f, 0f, lastX, lastY)
        }
        return GestureResult(GestureType.NONE)
    }

    fun onUp(x: Float, y: Float): GestureResult {
        if (!isDown) return GestureResult(GestureType.NONE)
        val wasDragging = dragging
        val wasLongPressed = longPressed
        val result = when {
            wasLongPressed -> GestureType.LONG_PRESS
            wasDragging -> GestureType.DRAG
            else -> GestureType.CLICK
        }
        reset()
        return GestureResult(result, 0f, 0f, x, y)
    }

    fun reset() {
        isDown = false
        dragging = false
        longPressed = false
    }

    val isDragging: Boolean get() = dragging
    val isLongPressed: Boolean get() = longPressed
}

/**
 * 悬浮球贴边吸附：给定当前坐标与屏幕尺寸，返回吸附后的坐标。
 * 拖动结束时，球心吸附到最近侧屏幕边缘（球体一半在屏幕外）。
 * mode: left/right/both/off
 */
fun snapToEdge(
    x: Float,
    screenWidth: Float,
    ballSize: Float,
    mode: String = "both",
): Float {
    if (mode == "off") return x
    val margin = ballSize / 2f
    val leftSnap = x < screenWidth / 2f
    return when {
        leftSnap -> -margin
        else -> screenWidth - margin
    }
}
