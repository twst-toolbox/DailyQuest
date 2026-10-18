package com.dailyquest.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import com.dailyquest.app.core.script.ScriptExecutor
import com.dailyquest.app.core.vision.TemplateMatcher
import com.dailyquest.app.data.TemplateStore
import com.dailyquest.app.service.DailyQuestAccessibilityService
import com.dailyquest.app.service.DailyQuestService
import com.dailyquest.app.ui.TemplateRecorderScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var gestureController: GestureController
    private lateinit var templateStore: TemplateStore
    private lateinit var templateMatcher: TemplateMatcher
    private lateinit var scriptExecutor: ScriptExecutor

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 启动前台服务并传递 MediaProjection
            val serviceIntent = Intent(this, DailyQuestService::class.java).apply {
                action = DailyQuestService.ACTION_START
                putExtra(DailyQuestService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(DailyQuestService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureManager = ScreenCaptureManager(this)
        gestureController = GestureController(captureManager)
        templateStore = TemplateStore(this)
        templateMatcher = TemplateMatcher()
        scriptExecutor = ScriptExecutor(this, gestureController, templateStore, templateMatcher)

        setContent {
            DailyQuestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigator(
                        gestureController = gestureController,
                        scriptExecutor = scriptExecutor,
                        templateStore = templateStore,
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
        if (!DailyQuestService.isRunning()) {
            captureManager.stop()
        }
    }
}

@Composable
fun DailyQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

enum class Screen {
    MAIN, RECORDER
}

@Composable
fun AppNavigator(
    gestureController: GestureController,
    scriptExecutor: ScriptExecutor,
    templateStore: TemplateStore,
    onRequestAccessibility: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var currentScreenshot by remember { mutableStateOf<Bitmap?>(null) }
    var currentGameId by remember { mutableStateOf("twst-jp") }
    val scope = rememberCoroutineScope()

    when (currentScreen) {
        Screen.MAIN -> MainScreen(
            gestureController = gestureController,
            scriptExecutor = scriptExecutor,
            templateStore = templateStore,
            onRequestAccessibility = onRequestAccessibility,
            onRequestMediaProjection = onRequestMediaProjection,
            onRequestOverlay = onRequestOverlay,
            onStartRecording = { gameId ->
                scope.launch {
                    val bitmap = gestureController.captureScreen()
                    if (bitmap != null) {
                        currentScreenshot = bitmap
                        currentGameId = gameId
                        currentScreen = Screen.RECORDER
                    }
                }
            }
        )
        Screen.RECORDER -> {
            currentScreenshot?.let { screenshot ->
                TemplateRecorderScreen(
                    screenshot = screenshot,
                    gameId = currentGameId,
                    templateStore = templateStore,
                    onSaveComplete = { fileName ->
                        currentScreen = Screen.MAIN
                    },
                    onCancel = {
                        currentScreen = Screen.MAIN
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    gestureController: GestureController,
    scriptExecutor: ScriptExecutor,
    templateStore: TemplateStore,
    onRequestAccessibility: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onRequestOverlay: () -> Unit,
    onStartRecording: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("等待操作") }
    var logText by remember { mutableStateOf("") }
    var isAccessibilityRunning by remember { mutableStateOf(false) }
    var isCaptureRunning by remember { mutableStateOf(false) }
    var canDrawOverlay by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var availableGames by remember { mutableStateOf(listOf<String>()) }
    var showRecorderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scriptExecutor.onLog = { msg ->
            logText = (logText + msg + "\n").takeLast(2000)
        }
        while (true) {
            isAccessibilityRunning = gestureController.isAccessibilityReady()
            isCaptureRunning = DailyQuestService.captureManager?.isRunning() ?: false
            canDrawOverlay = Settings.canDrawOverlays(context)
            isRunning = scriptExecutor.state == ScriptExecutor.State.RUNNING
            availableGames = templateStore.listGames()
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
            text = "M3 录制版",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 状态卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务状态", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow("无障碍服务", isAccessibilityRunning)
                StatusRow("屏幕录制", isCaptureRunning)
                StatusRow("悬浮窗", canDrawOverlay)
                StatusRow("脚本引擎", isRunning, activeText = "运行中", inactiveText = "待机")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 权限按钮
        if (!isAccessibilityRunning || !canDrawOverlay || !isCaptureRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("权限设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isAccessibilityRunning) {
                        Button(onClick = onRequestAccessibility, modifier = Modifier.fillMaxWidth()) {
                            Text("1. 开启无障碍服务")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!canDrawOverlay) {
                        Button(onClick = onRequestOverlay, modifier = Modifier.fillMaxWidth()) {
                            Text("2. 开启悬浮窗权限")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!isCaptureRunning) {
                        Button(onClick = onRequestMediaProjection, modifier = Modifier.fillMaxWidth()) {
                            Text("3. 启动屏幕录制服务")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 模板录制
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模板录制", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "先切换到游戏画面，再点下方按钮截屏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showRecorderDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isCaptureRunning
                ) {
                    Text("录制新模板")
                }

                if (availableGames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "已有模板：${availableGames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 游戏任务执行
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("游戏任务", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (availableGames.isEmpty()) {
                    Text(
                        "暂无游戏脚本\n（请先录制模板）",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    availableGames.forEach { gameId ->
                        val templates = templateStore.listTemplates(gameId)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(gameId)
                                Text(
                                    "${templates.size} 个模板",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        statusText = "执行中：$gameId"
                                        scriptExecutor.executeGame(gameId)
                                        statusText = "执行完成"
                                    }
                                },
                                enabled = !isRunning && isAccessibilityRunning && isCaptureRunning
                            ) { Text("执行") }
                        }
                        if (gameId != availableGames.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { scriptExecutor.stop() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("停止执行") }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 日志显示
        if (logText.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("执行日志", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 状态显示
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 录制对话框
    if (showRecorderDialog) {
        AlertDialog(
            onDismissRequest = { showRecorderDialog = false },
            title = { Text("选择游戏") },
            text = {
                Column {
                    Text("为哪个游戏录制模板？")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = "twst-jp",
                        onValueChange = {},
                        label = { Text("游戏 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "提示：\n• 扭曲仙境日服：twst-jp\n• 扭曲仙境美服：twst-en\n• FGO 日服：fgo-jp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRecorderDialog = false
                    onStartRecording("twst-jp")
                }) {
                    Text("开始录制")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecorderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun StatusRow(
    label: String,
    isActive: Boolean,
    activeText: String = "✓ 已开启",
    inactiveText: String = "✗ 未开启"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(
            text = if (isActive) activeText else inactiveText,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
