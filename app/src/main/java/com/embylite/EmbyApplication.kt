package com.embylite

import android.app.Application
import android.util.Log
import com.embylite.data.local.TokenManager
import com.embylite.utils.AppLogger

/**
 * Application 入口：
 * - 初始化 TokenManager（预热缓存，避免拦截器死锁）
 * - 初始化系统级日志
 * - 设置全局未捕获异常处理器（记录日志但不崩溃）
 */
class EmbyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 所有初始化 try-catch 包裹，禁止崩溃
        try {
            AppLogger.init(this)
            AppLogger.i("EmbyApplication onCreate")
        } catch (e: Exception) {
            Log.e("EmbyLite", "AppLogger init error", e)
        }
        try {
            TokenManager.init(this)
            AppLogger.i("TokenManager init ok, isLoggedIn=${TokenManager.isLoggedIn()}")
        } catch (e: Exception) {
            AppLogger.e("TokenManager init error", e)
        }

        // 全局异常捕获：写日志但不主动杀进程
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
