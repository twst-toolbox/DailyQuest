package com.dailyquest.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.dailyquest.app.MainActivity
import com.dailyquest.app.core.capture.ScreenCaptureManager

/**
 * 前台服务：保持 MediaProjection 会话 + 悬浮窗控制
 */
class DailyQuestService : Service() {

    companion object {
        private const val CHANNEL_ID = "DailyQuestService"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.dailyquest.app.action.START"
        const val ACTION_STOP = "com.dailyquest.app.action.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        var instance: DailyQuestService? = null
            private set

        var captureManager: ScreenCaptureManager? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode != -1 && resultData != null) {
                    startMediaProjection(resultCode, resultData)
                }
                startForeground(NOTIFICATION_ID, createNotification("待机中"))
                showOverlay()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        captureManager = ScreenCaptureManager(this)
        captureManager?.start(resultCode, data)
        updateNotification("屏幕录制已启动")
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 100
        }

        overlayView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    OverlayControlPanel(
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DailyQuest 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持脚本引擎运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DailyQuest")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager?.stop()
        captureManager = null
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayControlPanel(onClose: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("DQ", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "收起" else "展开")
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("服务运行中", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onClose) {
                    Text("停止服务", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
