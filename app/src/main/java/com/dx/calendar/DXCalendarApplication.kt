package com.dx.calendar

import android.app.Application
import com.dx.calendar.util.CrashLogManager

class DXCalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogManager.install(this)
    }
}
