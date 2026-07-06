package com.embylite.data.api

import com.embylite.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器：
 * - 统一注入 X-Emby-Authorization（Emby 服务器对所有请求都要求此 header）
 * - 若已登录则注入 X-Emby-Token
 * - 只用同步缓存，禁止 runBlocking（避免 OkHttp 拦截器死锁）
 */
class AuthInterceptor : Interceptor {

    companion object {
        // Emby 要求的客户端标识
        const val EMBY_AUTH =
            "MediaBrowser Client=\"EmbyLite\", Device=\"Android\", DeviceId=\"EmbyLite-Android-001\", Version=\"0.04\""
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-Emby-Authorization", EMBY_AUTH)
            .header("Accept", "application/json")

        // 注入 token（同步读取缓存）
        val token = TokenManager.getCachedToken()
        if (!token.isNullOrEmpty()) {
            builder.header("X-Emby-Token", token)
        }

        if (original.method == "POST" || original.method == "PUT") {
            builder.header("Content-Type", "application/json")
        }

        return chain.proceed(builder.build())
    }
}
