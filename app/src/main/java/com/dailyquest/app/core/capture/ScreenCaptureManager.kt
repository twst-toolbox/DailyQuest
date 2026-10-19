package com.dailyquest.app.core.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.dailyquest.app.util.CrashLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * MediaProjection 截屏管理器
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        var lastError: String? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private val screenWidth: Int
    private val screenHeight: Int
    private val screenDensity: Int

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    fun start(resultCode: Int, data: Intent) {
        lastError = null
        try {
            Log.i(TAG, "start: 获取 MediaProjection...")
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                lastError = "getMediaProjection 返回 null"
                Log.e(TAG, lastError!!)
                return
            }
            mediaProjection = projection
            Log.i(TAG, "start: MediaProjection 获取成功")

            // Android 14 必须在 createVirtualDisplay 之前注册回调
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection onStop")
                    release()
                }
            }, handler)

            Log.i(TAG, "start: 创建 ImageReader ${screenWidth}x${screenHeight}")
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

            Log.i(TAG, "start: 创建 VirtualDisplay...")
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )

            if (virtualDisplay == null) {
                lastError = "createVirtualDisplay 返回 null"
                Log.e(TAG, lastError!!)
                release()
            } else {
                Log.i(TAG, "start: 全部成功，VirtualDisplay=${virtualDisplay?.display?.displayId}")
            }
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "start 失败", e)
            CrashLogger.write(context, "ScreenCaptureManager.start", e)
            release()
        }
    }

    suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()
            cont.resume(cropped)
        } catch (e: Throwable) {
            CrashLogger.write(context, "captureScreen", e)
            cont.resume(null)
        } finally {
            image.close()
        }
    }

    fun stop() = release()

    private fun release() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    fun isRunning(): Boolean = mediaProjection != null && virtualDisplay != null && imageReader != null
}
