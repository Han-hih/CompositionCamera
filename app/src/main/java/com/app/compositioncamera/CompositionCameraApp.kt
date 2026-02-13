package com.app.compositioncamera

import android.app.Application
import android.content.pm.ApplicationInfo
import com.app.compositioncamera.util.Logx

class CompositionCameraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Logx.init(isDebug = isDebug)
    }
}
