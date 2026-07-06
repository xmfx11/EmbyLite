package com.embylite.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.embylite.BuildConfig
import com.embylite.data.api.GithubService
import com.embylite.data.model.GithubRelease
import com.embylite.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OTA 更新管理器：
 * - 检查 GitHub Releases 最新版本
 * - 下载 APK 到缓存目录
 * - 调用系统安装器安装
 *
 * 配置：仓库 owner=xmfx11 repo=EmbyLite
 */
object UpdateManager {

    private const val OWNER = "xmfx11"
    private const val REPO = "EmbyLite"
    private const val BASE_URL = "https://api.github.com/"

    // 检查更新：返回 (有更新?, 最新版本号, 下载链接, 更新说明)
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(GithubService::class.java)

            AppLogger.i("OTA: checking $OWNER/$REPO")
            val response = service.getLatestRelease(OWNER, REPO)
            if (!response.isSuccessful || response.body() == null) {
                AppLogger.w("OTA: check failed ${response.code()}")
                return@withContext null
            }
            val release = response.body()!!
            val tagName = release.tag_name ?: return@withContext null
            val remoteVersion = tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME.trim()

            AppLogger.i("OTA: remote=$remoteVersion current=$currentVersion")
            val hasUpdate = isNewer(remoteVersion, currentVersion)

            // 找 apk 资源
            val apkAsset = release.assets?.firstOrNull {
                it.name?.endsWith(".apk") == true
            }
            val url = apkAsset?.browser_download_url
            UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = remoteVersion,
                currentVersion = currentVersion,
                downloadUrl = url ?: "",
                releaseNotes = release.body ?: "",
                releaseName = release.name ?: tagName,
                publishedAt = release.published_at ?: ""
            )
        } catch (e: Exception) {
            AppLogger.e("OTA checkUpdate exception", e)
            null
        }
    }

    // 下载 APK，progress 回调 0-100
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.w("OTA download failed ${response.code}")
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            val total = body.contentLength()
            val target = File(context.cacheDir, "embylite_update.apk")
            if (target.exists()) target.delete()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    var lastReport = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastReport) {
                                lastReport = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            AppLogger.i("OTA download ok, size=${target.length()}")
            target
        } catch (e: Exception) {
            AppLogger.e("OTA download exception", e)
            null
        }
    }

    // 触发系统安装器
    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLogger.i("OTA install triggered")
        } catch (e: Exception) {
            AppLogger.e("OTA install exception", e)
        }
    }

    // 简单版本比较：支持 0.1 / 0.02 / 1.2.3 等
    // 用"逐段数字比较 + 段数补零"逻辑
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(r.size, c.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseName: String,
    val publishedAt: String
)
