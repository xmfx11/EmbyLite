package com.embylite.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// DataStore 扩展（单例）
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "embylite_prefs")

/**
 * Token 管理器：存储 serverAddress / accessToken / userId
 * - 同步缓存避免在 OkHttp 拦截器中 runBlocking 死锁
 * - init 由 Application 调用，预热缓存
 */
object TokenManager {

    private val KEY_SERVER = stringPreferencesKey("server_address")
    private val KEY_TOKEN = stringPreferencesKey("access_token")
    private val KEY_USER_ID = stringPreferencesKey("user_id")

    private lateinit var appContext: Context

    // 内存缓存（避免拦截器中 runBlocking）
    @Volatile private var cachedServer: String? = null
    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedUserId: String? = null
    @Volatile private var initialized: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        // 预热缓存（同步阻塞只在此处，禁止在拦截器里做）
        try {
            val prefs = runBlocking { appContext.dataStore.data.first() }
            cachedServer = prefs[KEY_SERVER]
            cachedToken = prefs[KEY_TOKEN]
            cachedUserId = prefs[KEY_USER_ID]
        } catch (e: Exception) {
            // 初始化失败不崩溃
        }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    // 同步读取（给 OkHttp 拦截器用）
    fun getCachedToken(): String? = cachedToken
    fun getCachedServer(): String? = cachedServer
    fun getCachedUserId(): String? = cachedUserId

    fun isLoggedIn(): Boolean = !cachedToken.isNullOrEmpty() && !cachedUserId.isNullOrEmpty()

    // 异步保存（登录后调用）
    suspend fun saveCredentials(server: String, token: String, userId: String) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_SERVER] = server
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = userId
        }
        cachedServer = server
        cachedToken = token
        cachedUserId = userId
    }

    // 异步读取（ViewModel 用）
    suspend fun getServer(): String? = appContext.dataStore.data.first()[KEY_SERVER].also { cachedServer = it }
    suspend fun getToken(): String? = appContext.dataStore.data.first()[KEY_TOKEN].also { cachedToken = it }
    suspend fun getUserId(): String? = appContext.dataStore.data.first()[KEY_USER_ID].also { cachedUserId = it }

    // 清空（退出登录 / token 失效）
    suspend fun clear() {
        appContext.dataStore.edit { it.clear() }
        cachedServer = null
        cachedToken = null
        cachedUserId = null
    }
}
