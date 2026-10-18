package com.dailyquest.app.core.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * 模板匹配器
 *
 * 适配策略（对应 docs/GAMES/twisted-wonderland-jp.md）：
 * 1. 多尺度匹配：模板在 0.7x~1.5x 范围内缩放搜索，适配不同分辨率
 * 2. 区域限定：可在屏幕局部区域搜索，提速 + 防误识别
 * 3. 分辨率自适应：录入时记录分辨率，运行时按比例计算初始缩放
 * 4. 遮罩匹配：模板边缘像素可不参与匹配，免疫背景/主题更换
 * 5. 边缘匹配模式：只比轮廓不比颜色，应对颜色变化的按钮
 */
class TemplateMatcher {

    companion object {
        private const val TAG = "TemplateMatcher"

        /** 默认匹配阈值（0~1，越高越严格） */
        const val DEFAULT_THRESHOLD = 0.80

        /** 多尺度搜索范围与步长 */
        private const val SCALE_MIN = 0.7
        private const val SCALE_MAX = 1.5
        private const val SCALE_STEP = 0.05

        /**
         * 自动生成遮罩时的边缘收缩像素数。
         * 模板的四周各缩 N 像素不参与匹配（这些位置容易混入背景色）。
         */
        const val AUTO_MASK_BORDER_PX = 4

        init {
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
     * 限定搜索区域（屏幕比例坐标，0~1）
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

    /**
     * 在屏幕截图中查找模板
     *
     * @param screen 当前屏幕截图
     * @param template 模板图（录入时裁剪的按钮核心区域）
     * @param templateScreenWidth 录入模板时的设备宽度
     * @param templateScreenHeight 录入模板时的设备高度
     * @param threshold 匹配阈值
     * @param searchRegion 限定搜索区域（比例坐标），null 表示全屏
     * @param ignoreBorder 是否自动生成遮罩忽略模板边缘（免疫背景更换），默认开启
     * @param edgeMode 边缘匹配模式（只比轮廓），默认关闭
     * @return 最佳匹配结果，未找到返回 null
     */
    fun findTemplate(
        screen: Bitmap,
        template: Bitmap,
        templateScreenWidth: Int,
        templateScreenHeight: Int,
        threshold: Double = DEFAULT_THRESHOLD,
        searchRegion: SearchRegion? = null,
        ignoreBorder: Boolean = true,
        edgeMode: Boolean = false
    ): MatchResult? {
        // 转灰度 Mat（可选边缘化）
        val screenMat = bitmapToGrayMat(screen, edgeMode)
        val templateMat = bitmapToGrayMat(template, edgeMode)

        // 生成遮罩：收缩模板边缘，免疫背景混入
        val maskMat = if (ignoreBorder) buildBorderMask(templateMat.cols(), templateMat.rows()) else null

        // 裁剪搜索区域
        val (searchMat, offsetX, offsetY) = cropSearchRegion(screenMat, screen.width, screen.height, searchRegion)

        // 计算初始缩放：基于录入分辨率与当前分辨率的比例
        val baseScaleX = screen.width.toDouble() / templateScreenWidth
        val baseScaleY = screen.height.toDouble() / templateScreenHeight
        val baseScale = (baseScaleX + baseScaleY) / 2.0

        var bestResult: MatchResult? = null
        var bestConfidence = 0.0

        val scaleFrom = baseScale * SCALE_MIN
        val scaleTo = baseScale * SCALE_MAX

        var scale = scaleFrom
        while (scale <= scaleTo) {
            val scaledWidth = (templateMat.cols() * scale).roundToInt()
            val scaledHeight = (templateMat.rows() * scale).roundToInt()

            if (scaledWidth >= searchMat.cols() || scaledHeight >= searchMat.rows() ||
                scaledWidth < 8 || scaledHeight < 8
            ) {
                scale += SCALE_STEP * baseScale
                continue
            }

            val scaledTemplate = Mat()
            Imgproc.resize(templateMat, scaledTemplate, Size(scaledWidth.toDouble(), scaledHeight.toDouble()))

            // 遮罩同步缩放
            val scaledMask = maskMat?.let {
                val m = Mat()
                Imgproc.resize(it, m, Size(scaledWidth.toDouble(), scaledHeight.toDouble()))
                m
            }

            val resultMat = Mat()
            // 带遮罩时使用 TM_CCORR_NORMED（mask 支持最稳的方法）
            if (scaledMask != null) {
                Imgproc.matchTemplate(searchMat, scaledTemplate, resultMat, Imgproc.TM_CCORR_NORMED, scaledMask)
            } else {
                Imgproc.matchTemplate(searchMat, scaledTemplate, resultMat, Imgproc.TM_CCOEFF_NORMED)
            }

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
            scaledMask?.release()
            scale += SCALE_STEP * baseScale
        }

        screenMat.release()
        templateMat.release()
        maskMat?.release()
        searchMat.release()

        if (bestResult == null || bestConfidence < threshold) {
            // 低置信度警告：提示用户可能需要重录该模板
            if (bestConfidence > threshold - 0.15) {
                Log.w(TAG, "模板匹配置信度偏低: ${"%.2f".format(bestConfidence)} (阈值 $threshold)")
            }
            return null
        }
        return bestResult
    }

    /**
     * 生成"忽略边缘"遮罩：中心白色（参与匹配），四周黑色（忽略）。
     * 用于免疫模板边缘混入的背景像素（换主题/换背景后边缘会变色）。
     */
    private fun buildBorderMask(width: Int, height: Int, border: Int = AUTO_MASK_BORDER_PX): Mat {
        val mask = Mat.zeros(height, width, CvType.CV_8UC1)
        val x1 = border.coerceAtMost(width / 4)
        val y1 = border.coerceAtMost(height / 4)
        val x2 = width - x1
        val y2 = height - y1
        if (x2 > x1 && y2 > y1) {
            Imgproc.rectangle(
                mask,
                org.opencv.core.Point(x1.toDouble(), y1.toDouble()),
                org.opencv.core.Point(x2.toDouble() - 1, y2.toDouble() - 1),
                org.opencv.core.Scalar(255.0),
                -1  // 填充
            )
        }
        return mask
    }

    private fun bitmapToGrayMat(bitmap: Bitmap, edgeMode: Boolean): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        mat.release()
        if (edgeMode) {
            val edges = Mat()
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            gray.release()
            return edges
        }
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
