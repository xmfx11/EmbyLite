package com.embylite.data.repository

import com.embylite.data.api.EmbyService
import com.embylite.data.model.EmbyItem
import com.embylite.data.model.EmbyUser
import com.embylite.data.model.LoginRequest
import com.embylite.data.model.LoginResponse

/**
 * 单一数据源，所有请求走这里
 * 所有方法返回 Result，禁止抛异常给 UI
 */
class EmbyRepository(private val apiService: EmbyService) {

    // 登录
    suspend fun login(username: String, password: String): Result<LoginResponse> = try {
        val response = apiService.login(LoginRequest(Username = username, Pw = password))
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            val err = response.errorBody()?.string() ?: ""
            Result.failure(Exception("登录失败 ${response.code()}: $err"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 验证 token（自动登录用）
    suspend fun getUser(userId: String): Result<EmbyUser> = try {
        val response = apiService.getUser(userId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("用户验证失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 媒体库列表（首页入口）
    suspend fun getViews(userId: String): Result<List<EmbyItem>> = try {
        val response = apiService.getViews(userId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("获取媒体库失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 媒体库内容
    suspend fun getItems(userId: String, parentId: String? = null): Result<List<EmbyItem>> = try {
        val response = apiService.getItems(userId, parentId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("获取内容失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 按标签筛选
    suspend fun getItemsByTag(userId: String, tag: String): Result<List<EmbyItem>> = try {
        val response = apiService.getItemsByTag(userId, tag)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("标签筛选失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 按演员筛选
    suspend fun getItemsByPerson(userId: String, personId: String): Result<List<EmbyItem>> = try {
        val response = apiService.getItemsByPerson(userId, personId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("演员筛选失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 搜索
    suspend fun search(userId: String, term: String): Result<List<EmbyItem>> = try {
        val response = apiService.search(userId, term)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.SearchHints ?: emptyList())
        } else {
            Result.failure(Exception("搜索失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 播放信息（提取直链）
    suspend fun getPlaybackInfo(itemId: String, userId: String): Result<List<com.embylite.data.model.MediaSource>> = try {
        val response = apiService.getPlaybackInfo(itemId, userId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.MediaSources ?: emptyList())
        } else {
            Result.failure(Exception("获取播放信息失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
