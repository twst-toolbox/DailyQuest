package com.dailyquest.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.TemplateStore

/**
 * 模板录制界面
 *
 * 流程：
 * 1. 显示当前屏幕截图
 * 2. 用户拖动框选区域
 * 3. 输入模板名称
 * 4. 保存到 games/{gameId}/templates/
 */
@Composable
fun TemplateRecorderScreen(
    screenshot: Bitmap,
    gameId: String,
    templateStore: TemplateStore,
    onSaveComplete: (String) -> Unit,
    onCancel: () -> Unit
) {
    var templateName by remember { mutableStateOf("") }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "录制模板",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "在截图上拖动框选按钮区域（只框核心，避开背景）",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 截图 + 框选层
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Image(
                bitmap = screenshot.asImageBitmap(),
                contentDescription = "屏幕截图",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { imageSize = it },
                contentScale = ContentScale.Fit
            )

            // 框选层
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                selectionRect = Rect(offset, Size.Zero)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragStart?.let { start ->
                                    val current = change.position
                                    selectionRect = Rect(
                                        left = minOf(start.x, current.x),
                                        top = minOf(start.y, current.y),
                                        right = maxOf(start.x, current.x),
                                        bottom = maxOf(start.y, current.y)
                                    )
                                }
                            },
                            onDragEnd = {
                                dragStart = null
                            }
                        )
                    }
            ) {
                selectionRect?.let { rect ->
                    // 半透明遮罩（未选中区域）
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset.Zero,
                        size = size
                    )
                    // 清除选中区域的遮罩
                    drawRect(
                        color = Color.Transparent,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                    // 选中框边框
                    drawRect(
                        color = Color.Green,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 3f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }

            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.weight(1f),
                enabled = selectionRect != null && selectionRect!!.width > 20 && selectionRect!!.height > 20
            ) {
                Text("保存模板")
            }
        }
    }

    // 保存对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存模板") },
            text = {
                Column {
                    Text("模板名称（如：lesson_button）")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "提示：名称用英文小写+下划线，不含空格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (templateName.isNotBlank() && selectionRect != null) {
                            // 裁剪并保存
                            val cropped = cropBitmap(screenshot, selectionRect!!, imageSize)
                            val fileName = "${templateName.trim()}.png"
                            templateStore.saveTemplate(gameId, fileName, cropped)
                            // 保存元数据（屏幕分辨率）
                            templateStore.saveMeta(gameId, TemplateStore.TemplateMeta(
                                screenshot.width, screenshot.height
                            ))
                            onSaveComplete(fileName)
                        }
                    },
                    enabled = templateName.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 根据框选区域裁剪位图
 */
private fun cropBitmap(source: Bitmap, rect: Rect, viewSize: IntSize): Bitmap {
    // 将视图坐标转换为位图坐标
    val scaleX = source.width.toFloat() / viewSize.width
    val scaleY = source.height.toFloat() / viewSize.height

    val x = (rect.left * scaleX).toInt().coerceIn(0, source.width - 1)
    val y = (rect.top * scaleY).toInt().coerceIn(0, source.height - 1)
    val width = (rect.width * scaleX).toInt().coerceIn(1, source.width - x)
    val height = (rect.height * scaleY).toInt().coerceIn(1, source.height - y)

    return Bitmap.createBitmap(source, x, y, width, height)
}
