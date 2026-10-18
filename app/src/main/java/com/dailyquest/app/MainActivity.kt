package com.dailyquest.app

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyquest.app.core.capture.ScreenCaptureManager
import com.dailyquest.app.core.input.GestureController
import com.dailyquest.app.service.DailyQuestAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var gestureController: GestureController

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            captureManager.start(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureManager = ScreenCaptureManager(this)
        gestureController = GestureController(captureManager)

        setContent {
            DailyQuestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        gestureController = gestureController,
                        onRequestAccessibility = { openAccessibilitySettings() },
                        onRequestMediaProjection = { requestMediaProjection() },
                        onRequestOverlay = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ))
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.stop()
    }
}

@Composable
fun DailyQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

@Composable
fun MainScreen(
    gestureController: GestureController,
    onRequestAccessibility: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("等待操作") }
    var isAccessibilityRunning by remember { mutableStateOf(false) }
    var isCaptureRunning by remember { mutableStateOf(false) }
    var canDrawOverlay by remember { mutableStateOf(false) }

    // 定时刷新状态
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityRunning = gestureController.isAccessibilityReady()
            isCaptureRunning = gestureController.isCaptureReady()
            canDrawOverlay = Settings.canDrawOverlays(context)
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DailyQuest",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "M1 测试版",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 状态卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务状态", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow("无障碍服务", isAccessibilityRunning)
                StatusRow("屏幕录制", isCaptureRunning)
                StatusRow("悬浮窗", canDrawOverlay)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 权限按钮
        Button(
            onClick = onRequestAccessibility,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. 开启无障碍服务")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRequestOverlay,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. 开启悬浮窗权限")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRequestMediaProjection,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. 授权屏幕录制")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 测试按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("功能测试", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            statusText = "截屏中..."
                            val bitmap = gestureController.captureScreen()
                            statusText = if (bitmap != null) {
                                "截屏成功！尺寸: ${bitmap.width}x${bitmap.height}"
                            } else {
                                "截屏失败"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isCaptureRunning
                ) {
                    Text("测试截屏")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            statusText = "点击屏幕中央..."
                            val success = gestureController.tap(540f, 960f)
                            statusText = if (success) "点击成功" else "点击失败"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAccessibilityRunning
                ) {
                    Text("测试点击(540, 960)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        gestureController.back()
                        statusText = "已按返回键"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAccessibilityRunning
                ) {
                    Text("测试返回键")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 状态显示
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusRow(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(
            text = if (isActive) "✓ 已开启" else "✗ 未开启",
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
