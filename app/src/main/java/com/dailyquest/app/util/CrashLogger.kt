package com.dailyquest.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志记录器：把未捕获异常写入文件，下次启动时可查看
 */
object CrashLogger : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        write(appContext ?: return, "UncaughtException[${thread.name}]", throwable)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    fun write(context: Context, tag: String, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val text = "===== $time [$tag] =====\n$sw\n\n"
            File(context.filesDir, "crash_log.txt").appendText(text)
        } catch (_: Throwable) {}
    }

    fun read(context: Context): String {
        return try {
            val f = File(context.filesDir, "crash_log.txt")
            if (f.exists()) f.readText() else ""
        } catch (_: Throwable) { "" }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, "crash_log.txt").delete()
        } catch (_: Throwable) {}
    }
}
