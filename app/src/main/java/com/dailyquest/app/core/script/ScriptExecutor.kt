package com.dailyquest.app.core.script

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dailyquest.app.core.input.GestureController
import com.dailyquest.app.core.vision.TemplateMatcher
import com.dailyquest.app.data.TemplateStore
import kotlinx.coroutines.delay

/**
 * 脚本执行器：把 JSON 脚本解析成动作序列执行
 *
 * 状态机特性：
 * - 每步可暂停/停止
 * - 失败自动截图存档
 * - 实时日志回调
 */
class ScriptExecutor(
    private val context: Context,
    private val gestureController: GestureController,
    private val templateStore: TemplateStore,
    private val templateMatcher: TemplateMatcher
) {

    companion object {
        private const val TAG = "ScriptExecutor"
    }

    /** 执行状态 */
    enum class State {
        IDLE, RUNNING, PAUSED, STOPPING
    }

    var state = State.IDLE
        private set

    /** 日志回调（UI 层订阅显示） */
    var onLog: ((String) -> Unit)? = null

    /** 当前执行进度回调 */
    var onProgress: ((taskName: String, stepIndex: Int, totalSteps: Int) -> Unit)? = null

    private var shouldStop = false

    fun stop() {
        shouldStop = true
        state = State.STOPPING
        log("收到停止指令")
    }

    /**
     * 执行整个游戏的所有启用任务
     */
    suspend fun executeGame(gameId: String): Boolean {
        val script = templateStore.loadScript(gameId) ?: run {
            log("错误：找不到脚本 $gameId")
            return false
        }

        state = State.RUNNING
        shouldStop = false
        log("开始执行游戏：${script.gameName}")

        for (task in script.tasks.filter { it.enabled }) {
            if (shouldStop) break
            val success = executeTask(gameId, task)
            if (!success) {
                log("任务失败：${task.taskName}")
                state = State.IDLE
                return false
            }
        }

        log("游戏执行完成：${script.gameName}")
        state = State.IDLE
        return true
    }

    /**
     * 执行单个任务
     */
    suspend fun executeTask(gameId: String, task: TaskScript): Boolean {
        log("开始任务：${task.taskName}")
        val meta = templateStore.loadMeta(gameId) ?: TemplateStore.TemplateMeta(1920, 1080)

        for ((index, step) in task.steps.withIndex()) {
            if (shouldStop) {
                log("任务被停止：${task.taskName}")
                return false
            }
            onProgress?.invoke(task.taskName, index + 1, task.steps.size)
            val success = executeStep(gameId, step, meta)
            if (!success) {
                log("步骤失败 [${index + 1}/${task.steps.size}]: ${stepDescription(step)}")
                return false
            }
        }
        log("任务完成：${task.taskName}")
        return true
    }

    private suspend fun executeStep(
        gameId: String,
        step: Step,
        meta: TemplateStore.TemplateMeta
    ): Boolean {
        return when (step) {
            is Step.WaitImage -> {
                log("等待图片：${step.template}（超时 ${step.timeoutSec}s）")
                val result = waitForImage(gameId, step.template, step.timeoutSec, step.region, meta)
                result != null
            }

            is Step.TapImage -> {
                val result = waitForImage(gameId, step.template, step.timeoutSec, step.region, meta)
                if (result == null) {
                    if (step.optional) {
                        log("可选图片未找到，跳过：${step.template}")
                        true
                    } else {
                        log("找不到图片：${step.template}")
                        false
                    }
                } else {
                    log("点击图片：${step.template} @ (${result.centerX.toInt()}, ${result.centerY.toInt()}) 置信度 ${"%.2f".format(result.confidence)}")
                    gestureController.tap(result.centerX, result.centerY)
                    delay(300)
                    true
                }
            }

            is Step.TapAt -> {
                // TODO: 需要当前屏幕尺寸，从截屏获取
                log("点击坐标：(${"%.2f".format(step.xRatio)}, ${"%.2f".format(step.yRatio)})")
                // 简化：先截屏拿尺寸
                val screen = gestureController.captureScreen() ?: return false
                gestureController.tap(screen.width * step.xRatio, screen.height * step.yRatio)
                screen.recycle()
                delay(300)
                true
            }

            is Step.Swipe -> {
                val screen = gestureController.captureScreen() ?: return false
                gestureController.swipe(
                    screen.width * step.x1Ratio, screen.height * step.y1Ratio,
                    screen.width * step.x2Ratio, screen.height * step.y2Ratio,
                    step.durationMs
                )
                screen.recycle()
                delay(300)
                true
            }

            is Step.Back -> {
                repeat(step.times) {
                    gestureController.back()
                    delay(500)
                }
                true
            }

            is Step.Wait -> {
                delay(step.millis)
                true
            }

            is Step.CloseUntilImage -> {
                log("循环关闭弹窗，直到出现：${step.anchorTemplate}")
                var attempts = 0
                while (attempts < step.maxAttempts) {
                    if (shouldStop) return false
                    // 先检查锚点是否已出现
                    val anchor = findImage(gameId, step.anchorTemplate, step.anchorRegion, meta)
                    if (anchor != null) {
                        log("锚点出现，关闭循环结束")
                        return true
                    }
                    // 找关闭按钮并点击
                    val closeBtn = findImage(gameId, step.closeTemplate, null, meta)
                    if (closeBtn != null) {
                        gestureController.tap(closeBtn.centerX, closeBtn.centerY)
                        delay(800)
                    } else {
                        // 没有关闭按钮也没有锚点，等待一下再重试
                        delay(1000)
                    }
                    attempts++
                }
                log("关闭循环超过最大次数 ${step.maxAttempts}，视为失败")
                false
            }

            is Step.RepeatUntilImage -> {
                log("开始循环（直到出现 ${step.conditionTemplate}）")
                var loops = 0
                while (loops < step.maxLoops) {
                    if (shouldStop) return false
                    // 检查停止条件
                    val condition = findImage(gameId, step.conditionTemplate, null, meta)
                    if (condition != null) {
                        log("条件图片出现，循环结束（共 $loops 轮）")
                        return true
                    }
                    // 执行子步骤
                    for (subStep in step.steps) {
                        if (!executeStep(gameId, subStep, meta)) return false
                    }
                    loops++
                    log("循环第 $loops 轮完成")
                }
                log("循环超过最大次数 ${step.maxLoops}")
                false
            }

            is Step.LaunchGame -> {
                log("启动游戏：${step.packageName}")
                val intent = context.packageManager.getLaunchIntentForPackage(step.packageName)
                if (intent == null) {
                    log("错误：找不到应用 ${step.packageName}")
                    false
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    delay(5000)  // 等待游戏启动
                    true
                }
            }

            is Step.ExitGame -> {
                log("退出游戏")
                gestureController.back()
                delay(1000)
                if (step.confirmTemplate != null) {
                    val confirm = waitForImage(gameId, step.confirmTemplate, 5, null, meta)
                    if (confirm != null) {
                        gestureController.tap(confirm.centerX, confirm.centerY)
                        delay(1000)
                    }
                }
                true
            }
        }
    }

    /** 等待图片出现（轮询） */
    private suspend fun waitForImage(
        gameId: String,
        templateName: String,
        timeoutSec: Int,
        region: List<Float>?,
        meta: TemplateStore.TemplateMeta
    ): TemplateMatcher.MatchResult? {
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (shouldStop) return null
            val result = findImage(gameId, templateName, region, meta)
            if (result != null) return result
            delay(500)
        }
        return null
    }

    /** 单次查找图片 */
    private suspend fun findImage(
        gameId: String,
        templateName: String,
        region: List<Float>?,
        meta: TemplateStore.TemplateMeta
    ): TemplateMatcher.MatchResult? {
        val screen = gestureController.captureScreen() ?: return null
        val template = templateStore.loadTemplate(gameId, templateName)
        if (template == null) {
            log("模板不存在：$templateName")
            screen.recycle()
            return null
        }

        val searchRegion = region?.let {
            TemplateMatcher.SearchRegion(it[0], it[1], it[2], it[3])
        }

        val result = templateMatcher.findTemplate(
            screen, template,
            meta.screenWidth, meta.screenHeight,
            searchRegion = searchRegion
        )
        screen.recycle()
        template.recycle()
        return result
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        onLog?.invoke(message)
    }

    private fun stepDescription(step: Step): String = when (step) {
        is Step.WaitImage -> "WaitImage(${step.template})"
        is Step.TapImage -> "TapImage(${step.template})"
        is Step.TapAt -> "TapAt(${step.xRatio}, ${step.yRatio})"
        is Step.Swipe -> "Swipe"
        is Step.Back -> "Back(${step.times})"
        is Step.Wait -> "Wait(${step.millis}ms)"
        is Step.CloseUntilImage -> "CloseUntil(${step.anchorTemplate})"
        is Step.RepeatUntilImage -> "RepeatUntil(${step.conditionTemplate})"
        is Step.LaunchGame -> "Launch(${step.packageName})"
        is Step.ExitGame -> "ExitGame"
    }
}
