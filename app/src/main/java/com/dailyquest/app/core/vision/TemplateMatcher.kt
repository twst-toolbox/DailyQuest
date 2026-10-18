package com.dailyquest.app.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * 模板匹配器
 *
 * 设计要点（对应 docs/GAMES/twisted-wonderland-jp.md 的适配策略）：
 * 1. 多尺度匹配：模板在 0.7x~1.5x 范围内缩放搜索，适配不同分辨率
 * 2. 区域限定：可在屏幕局部区域搜索，提速 + 防误识别
 * 3. 记录分辨率：录入模板时记录设备分辨率，运行时按比例计算初始缩放
 */
class TemplateMatcher {

    companion object {
        private const val TAG = "TemplateMatcher"

        /** 默认匹配阈值（0~1，越高越严格） */
        const val DEFAULT_THRESHOLD = 0.80

        /** 多尺度搜索范围与步长 */
        private val SCALE_MIN = 0.7
        private val SCALE_MAX = 1.5
        private val SCALE_STEP = 0.05

        init {
            // OpenCV 4.9.0 Maven 版自动加载 native 库，无需 OpenCV Manager
            try {
                System.loadLibrary("opencv_java4")
                Log.i(TAG, "OpenCV native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load OpenCV native library", e)
            }
        }
    }

    /**
     * 匹配结果
     */
    data class MatchResult(
        /** 匹配到的中心点（屏幕绝对坐标） */
        val centerX: Float,
        val centerY: Float,
        /** 匹配置信度 0~1 */
        val confidence: Double,
        /** 匹配到的矩形区域（屏幕绝对坐标） */
        val rect: Rect,
        /** 实际使用的缩放比例 */
        val scale: Double
    )

    /**
     * 在屏幕截图中查找模板
     *
     * @param screen 当前屏幕截图
     * @param template 模板图（录入时裁剪的按钮本体，不含背景）
     * @param templateScreenWidth 录入模板时的设备宽度
     * @param templateScreenHeight 录入模板时的设备高度
     * @param threshold 匹配阈值
     * @param searchRegion 限定搜索区域（屏幕比例坐标 0~1），null 表示全屏
     * @return 最佳匹配结果，未找到返回 null
     */
    fun findTemplate(
        screen: Bitmap,
        template: Bitmap,
        templateScreenWidth: Int,
        templateScreenHeight: Int,
        threshold: Double = DEFAULT_THRESHOLD,
        searchRegion: SearchRegion? = null
    ): MatchResult? {
        // 转灰度 Mat
        val screenMat = bitmapToGrayMat(screen)
        val templateMat = bitmapToGrayMat(template)

        // 裁剪搜索区域
        val (searchMat, offsetX, offsetY) = cropSearchRegion(screenMat, screen.width, screen.height, searchRegion)

        // 计算初始缩放：基于录入分辨率与当前分辨率的比例
        val baseScaleX = screen.width.toDouble() / templateScreenWidth
        val baseScaleY = screen.height.toDouble() / templateScreenHeight
        val baseScale = (baseScaleX + baseScaleY) / 2.0

        // 多尺度匹配
        var bestResult: MatchResult? = null
        var bestConfidence = 0.0

        val scaleFrom = (baseScale * SCALE_MIN)
        val scaleTo = (baseScale * SCALE_MAX)

        var scale = scaleFrom
        while (scale <= scaleTo) {
            val scaledWidth = (templateMat.cols() * scale).roundToInt()
            val scaledHeight = (templateMat.rows() * scale).roundToInt()

            // 缩放后模板比搜索区域还大，跳过
            if (scaledWidth >= searchMat.cols() || scaledHeight >= searchMat.rows() ||
                scaledWidth < 8 || scaledHeight < 8) {
                scale += SCALE_STEP * baseScale
                continue
            }

            val scaledTemplate = Mat()
            Imgproc.resize(templateMat, scaledTemplate, Size(scaledWidth.toDouble(), scaledHeight.toDouble()))

            val resultMat = Mat()
            Imgproc.matchTemplate(searchMat, scaledTemplate, resultMat, Imgproc.TM_CCOEFF_NORMED)

            val mmr = Core.minMaxLoc(resultMat)
            if (mmr.maxVal > bestConfidence) {
                val topLeft = mmr.maxLoc
                bestConfidence = mmr.maxVal
                bestResult = MatchResult(
                    centerX = (topLeft.x + scaledWidth / 2.0 + offsetX).toFloat(),
                    centerY = (topLeft.y + scaledHeight / 2.0 + offsetY).toFloat(),
                    confidence = mmr.maxVal,
                    rect = Rect(
                        (topLeft.x + offsetX).toInt(),
                        (topLeft.y + offsetY).toInt(),
                        (topLeft.x + scaledWidth + offsetX).toInt(),
                        (topLeft.y + scaledHeight + offsetY).toInt()
                    ),
                    scale = scale
                )
            }

            resultMat.release()
            scaledTemplate.release()
            scale += SCALE_STEP * baseScale
        }

        screenMat.release()
        templateMat.release()
        searchMat.release()

        return if (bestConfidence >= threshold) bestResult else null
    }

    /**
     * 限定搜索区域（屏幕比例坐标，0~1）
     * 例如 SearchRegion(0.5, 0.0, 1.0, 1.0) = 只在屏幕右半部分搜索
     */
    data class SearchRegion(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        companion object {
            val FULL = SearchRegion(0f, 0f, 1f, 1f)
            val RIGHT_HALF = SearchRegion(0.5f, 0f, 1f, 1f)
            val LEFT_HALF = SearchRegion(0f, 0f, 0.5f, 1f)
            val TOP_HALF = SearchRegion(0f, 0f, 1f, 0.5f)
            val BOTTOM_HALF = SearchRegion(0f, 0.5f, 1f, 1f)
            val BOTTOM_BAR = SearchRegion(0f, 0.85f, 1f, 1f)
        }
    }

    private fun bitmapToGrayMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        mat.release()
        return gray
    }

    private fun cropSearchRegion(
        screenMat: Mat,
        screenWidth: Int,
        screenHeight: Int,
        region: SearchRegion?
    ): Triple<Mat, Int, Int> {
        if (region == null || region == SearchRegion.FULL) {
            return Triple(screenMat.clone(), 0, 0)
        }
        val x = (region.left * screenWidth).toInt().coerceIn(0, screenWidth - 1)
        val y = (region.top * screenHeight).toInt().coerceIn(0, screenHeight - 1)
        val w = ((region.right - region.left) * screenWidth).toInt().coerceIn(1, screenWidth - x)
        val h = ((region.bottom - region.top) * screenHeight).toInt().coerceIn(1, screenHeight - y)
        val cropped = Mat(screenMat, org.opencv.core.Rect(x, y, w, h)).clone()
        return Triple(cropped, x, y)
    }
}
