package com.dailyquest.app.core.input

import android.graphics.Bitmap
import com.dailyquest.app.core.capture.ScreenCaptureManager
import com.dailyquest.app.service.DailyQuestAccessibilityService
import kotlinx.coroutines.delay

/**
 * 手势控制器：组合截屏和无障碍点击
 */
class GestureController(
    private val captureManager: ScreenCaptureManager
) {

    private val accessibilityService: DailyQuestAccessibilityService?
        get() = DailyQuestAccessibilityService.instance

    /**
     * 点击屏幕坐标
     */
    suspend fun tap(x: Float, y: Float): Boolean {
        val service = accessibilityService ?: return false
        return service.tap(x, y)
    }

    /**
     * 滑动
     */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300): Boolean {
        val service = accessibilityService ?: return false
        return service.swipe(x1, y1, x2, y2, duration)
    }

    /**
     * 按返回键
     */
    fun back(): Boolean {
        val service = accessibilityService ?: return false
        return service.performBack()
    }

    /**
     * 等待指定毫秒
     */
    suspend fun wait(millis: Long) {
        delay(millis)
    }

    /**
     * 截取当前屏幕
     */
    suspend fun captureScreen(): Bitmap? {
        return captureManager.captureScreen()
    }

    /**
     * 检查无障碍服务是否运行
     */
    fun isAccessibilityReady(): Boolean {
        return DailyQuestAccessibilityService.isRunning()
    }

    /**
     * 检查截屏是否就绪
     */
    fun isCaptureReady(): Boolean {
        return captureManager.isRunning()
    }
}
