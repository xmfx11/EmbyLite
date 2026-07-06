package com.embylite.data.api

import com.embylite.data.model.EmbyUser
import com.embylite.data.model.ItemsResponse
import com.embylite.data.model.LoginRequest
import com.embylite.data.model.LoginResponse
import com.embylite.data.model.PlaybackInfoResponse
import com.embylite.data.model.SearchResponse
import com.embylite.data.model.ViewsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EmbyService {
    // 登录：POST /Users/AuthenticateByName，body 含 Username 和 Pw
    @POST("Users/AuthenticateByName")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // 验证 token 有效性：GET /Users/{UserId}
    @GET("Users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<EmbyUser>

    // 媒体库列表（首页入口）
    @GET("Users/{userId}/Views")
    suspend fun getViews(@Path("userId") userId: String): Response<ViewsResponse>

    // 媒体库内容 / 按 ParentId 获取子项
    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeTypes: String? = "Movie,Series",
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,Overview,Taglines,People,MediaSources,ImageTags",
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending"
    ): Response<ItemsResponse>

    // 按标签筛选
    @GET("Users/{userId}/Items")
    suspend fun getItemsByTag(
        @Path("userId") userId: String,
        @Query("Tags") tag: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,Overview,Taglines,People,MediaSources,ImageTags"
    ): Response<ItemsResponse>

    // 按演员筛选
    @GET("Users/{userId}/Items")
    suspend fun getItemsByPerson(
        @Path("userId") userId: String,
        @Query("Person") personId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,Overview,Taglines,People,MediaSources,ImageTags"
    ): Response<ItemsResponse>

    // 搜索
    @GET("Search/Hints")
    suspend fun search(
        @Query("UserId") userId: String,
        @Query("SearchTerm") searchTerm: String,
        @Query("IncludeItemTypes") includeTypes: String = "Movie,Series,Episode",
        @Query("Limit") limit: Int = 50,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,Overview"
    ): Response<SearchResponse>

    // 播放信息（提取直链）
    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String
    ): Response<PlaybackInfoResponse>
}
