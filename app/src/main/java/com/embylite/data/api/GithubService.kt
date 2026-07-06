package com.embylite.data.api

import com.embylite.data.model.GithubRelease
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubService {
    // 获取仓库最新 release（OTA 检查更新）
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GithubRelease>
}
