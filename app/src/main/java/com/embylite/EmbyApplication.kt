package com.embylite

import android.app.Application
import android.util.Log
import com.embylite.data.local.TokenManager

/**
 * Application 入口：
 * - 初始化 TokenManager（预热缓存，避免拦截器死锁）
 * - 设置全局未捕获异常处理器（记录日志但不崩溃）
 */
class EmbyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 所有初始化 try-catch 包裹，禁止崩溃
        try {
            TokenManager.init(this)
        } catch (e: Exception) {
            Log.e("EmbyLite", "TokenManager init error", e)
        }

        // 全局异常捕获：记录日志但不崩溃
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("EmbyLite", "Uncaught exception on ${thread.name}", throwable)
            // 不主动杀进程，交给系统默认处理或忽略，避免闪退
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
