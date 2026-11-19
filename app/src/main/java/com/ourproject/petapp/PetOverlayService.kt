package com.ourproject.petapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ourproject.petapp.core.FgServiceType
import com.ourproject.petapp.core.ForegroundServicePolicy
import com.ourproject.petapp.core.GestureType
import com.ourproject.petapp.core.OverlayGesture
import com.ourproject.petapp.core.snapToEdge

class PetOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var ballView: View
    private lateinit var stateText: TextView
    private val gesture = OverlayGesture()
    private val fgPolicy = ForegroundServicePolicy()

    private val overlayParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        addOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    /** Android 14 动态降级：按权限选择前台服务类型。 */
    private fun startForegroundCompat() {
        val hasRecord = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        // MediaProjection 权限需用户在系统弹窗中授予，此处按未授予处理
        val decision = fgPolicy.decide(hasRecord, hasMediaProjection = false)
        createNotificationChannel()
        val notification = buildNotification(decision.type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, decision.type.toServiceInfoType())
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun FgServiceType.toServiceInfoType(): Int = when (this) {
        FgServiceType.FULL -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        FgServiceType.MICROPHONE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        FgServiceType.SPECIAL_USE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌宠运行中", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(type: FgServiceType): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("桌宠运行中")
            .setContentText("前台服务类型：${type.name}")
            .setSmallIcon(R.drawable.ic_pet_notify)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun addOverlayView() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_pet, null)
        ballView = overlayView.findViewById(R.id.pet_ball)
        stateText = overlayView.findViewById(R.id.pet_state)

        ballView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        stateText.text = "待机"

        windowManager.addView(overlayView, overlayParams)
    }

    /** 点击悬浮球呼出会话页。 */
    private fun openChatActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("PetOverlay", "openChatActivity failed: ${e.message}")
        }
    }

    private fun removeOverlayView() {
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            // view 可能已移除
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val screenWidth = metrics.widthPixels.toFloat()
        val ballSize = ballView.width.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.onDown(event.rawX, event.rawY)
                Log.d("PetOverlay", "DOWN raw=(${event.rawX},${event.rawY})")
            }
            MotionEvent.ACTION_MOVE -> {
                val result = gesture.onMove(event.rawX, event.rawY)
                Log.d("PetOverlay", "MOVE raw=(${event.rawX},${event.rawY}) type=${result.type} px=${overlayParams.x},${overlayParams.y}")
                if (result.type == GestureType.DRAG) {
                    overlayParams.x = (overlayParams.x + result.dx).toInt()
                    overlayParams.y = (overlayParams.y + result.dy).toInt()
                    try {
                        windowManager.updateViewLayout(overlayView, overlayParams)
                    } catch (e: Exception) {
                        Log.e("PetOverlay", "updateViewLayout failed: ${e.message}")
                    }
                }
                gesture.checkLongPress().let { lp ->
                    if (lp.type == GestureType.LONG_PRESS) {
                        stateText.text = "已长按"
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val result = gesture.onUp(event.rawX, event.rawY)
                Log.d("PetOverlay", "UP raw=(${event.rawX},${event.rawY}) type=${result.type} px=${overlayParams.x},${overlayParams.y}")
                when (result.type) {
                    GestureType.CLICK -> {
                        stateText.text = "呼出会话"
                        openChatActivity()
                    }
                    GestureType.DRAG -> {
                        val snappedX = snapToEdge(overlayParams.x.toFloat(), screenWidth, ballSize)
                        overlayParams.x = snappedX.toInt()
                        windowManager.updateViewLayout(overlayView, overlayParams)
                        Log.d("PetOverlay", "SNAP x=${overlayParams.x} snappedX=$snappedX")
                        stateText.text = "已贴边"
                    }
                    GestureType.LONG_PRESS -> {
                        stateText.text = "长按停止"
                        stopSelf()
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_CANCEL -> gesture.reset()
        }
        return true
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1001
    }
}
