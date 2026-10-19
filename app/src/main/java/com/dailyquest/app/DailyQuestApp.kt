package com.dailyquest.app

import android.app.Application
import com.dailyquest.app.util.CrashLogger

class DailyQuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
    }
}
