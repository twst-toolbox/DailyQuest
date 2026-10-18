package com.dailyquest.app.core.script

import kotlinx.serialization.Serializable

/**
 * 任务脚本数据模型
 *
 * 一个游戏 = 一个 GameScript
 * 一个游戏有多个 Task（如：签到、清体力）
 * 一个 Task 由多个 Step 组成
 */
@Serializable
data class GameScript(
    val gameId: String,                 // 如 "twst-jp"
    val gameName: String,               // 如 "扭曲仙境 日服"
    val packageName: String,            // 如 "com.aniplex.twst.jp"
    val tasks: List<TaskScript>
)

@Serializable
data class TaskScript(
    val taskId: String,
    val taskName: String,               // 如 "每日签到"
    val enabled: Boolean = true,
    val steps: List<Step>
)

/**
 * 动作原语。所有游戏的所有任务都由这些原语拼成。
 */
@Serializable
sealed class Step {

    /** 等待某模板出现（超时则失败） */
    @Serializable
    data class WaitImage(
        val template: String,           // 模板文件名
        val timeoutSec: Int = 60,
        val region: List<Float>? = null // [left, top, right, bottom] 比例坐标
    ) : Step()

    /** 找到模板并点击（找不到则失败） */
    @Serializable
    data class TapImage(
        val template: String,
        val timeoutSec: Int = 10,
        val region: List<Float>? = null,
        val optional: Boolean = false   // true=找不到也继续（用于关闭可能不存在的弹窗）
    ) : Step()

    /** 点击屏幕比例坐标 */
    @Serializable
    data class TapAt(
        val xRatio: Float,              // 0~1
        val yRatio: Float
    ) : Step()

    /** 滑动 */
    @Serializable
    data class Swipe(
        val x1Ratio: Float, val y1Ratio: Float,
        val x2Ratio: Float, val y2Ratio: Float,
        val durationMs: Long = 300
    ) : Step()

    /** 系统返回键 */
    @Serializable
    data class Back(val times: Int = 1) : Step()

    /** 等待固定时间 */
    @Serializable
    data class Wait(val millis: Long) : Step()

    /** 循环点击关闭按钮，直到锚点出现（应对公告数量不固定） */
    @Serializable
    data class CloseUntilImage(
        val closeTemplate: String,      // 关闭按钮模板
        val anchorTemplate: String,     // 目标锚点模板
        val maxAttempts: Int = 10,
        val anchorRegion: List<Float>? = null
    ) : Step()

    /** 循环执行子步骤，直到条件模板消失（如：反复上课直到体力不足弹窗出现） */
    @Serializable
    data class RepeatUntilImage(
        val conditionTemplate: String,  // 出现此图则停止循环
        val maxLoops: Int = 50,
        val steps: List<Step>
    ) : Step()

    /** 启动游戏 */
    @Serializable
    data class LaunchGame(val packageName: String) : Step()

    /** 退出游戏：按返回键直到出现退出确认，点确认 */
    @Serializable
    data class ExitGame(
        val confirmTemplate: String? = null  // 退出确认的"是"按钮模板
    ) : Step()
}
