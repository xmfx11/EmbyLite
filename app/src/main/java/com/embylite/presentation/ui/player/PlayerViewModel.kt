package com.embylite.presentation.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embylite.data.local.TokenManager
import com.embylite.data.model.MediaSource
import com.embylite.data.repository.EmbyRepository
import com.embylite.utils.AppLogger
import com.embylite.utils.NetworkModule
import com.embylite.utils.PlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PlayerState {
    object Loading : PlayerState()
    data class Ready(
        val url: String,
        val mediaSource: MediaSource?,
        val title: String
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

class PlayerViewModel : ViewModel() {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var repository: EmbyRepository? = null

    fun loadAndPrepare(itemId: String) {
        viewModelScope.launch {
            val server = TokenManager.getCachedServer()
            val userId = TokenManager.getCachedUserId()
            val token = TokenManager.getCachedToken()
            AppLogger.i("Player loadAndPrepare itemId=$itemId server=${server ?: "null"} userId=${userId ?: "null"} tokenLen=${token?.length ?: 0}")
            if (server.isNullOrEmpty() || userId.isNullOrEmpty() || itemId.isEmpty()) {
                _state.value = PlayerState.Error("参数无效（server/userId/itemId 缺失）")
                return@launch
            }
            _state.value = PlayerState.Loading
            try {
                val repo = repository ?: EmbyRepository(NetworkModule.createApiService(server))
                    .also { repository = it }
                val result = withContext(Dispatchers.IO) { repo.getPlaybackInfo(itemId, userId) }
                result.onSuccess { sources ->
                    AppLogger.i("Player got ${sources.size} media sources")
                    // 优先选 SupportsDirectStream=true 的源；否则取第一个；都没有则 null 走兜底
                    val source = sources.firstOrNull { it.SupportsDirectStream == true }
                        ?: sources.firstOrNull { it.SupportsDirectPlay == true }
                        ?: sources.firstOrNull()
                    val url = PlayerUtils.buildPlayUrl(server, itemId, source, token)
                    AppLogger.i("Player built url=${url?.take(120)}...")
                    if (!url.isNullOrEmpty()) {
                        val title = source?.Name ?: "EmbyLite 播放器"
                        _state.value = PlayerState.Ready(url, source, title)
                    } else {
                        _state.value = PlayerState.Error("无法获取播放地址")
                    }
                }.onFailure {
                    AppLogger.e("Player getPlaybackInfo failed", it)
                    _state.value = PlayerState.Error(it.message ?: "获取播放信息失败")
                }
            } catch (e: Exception) {
                AppLogger.e("Player loadAndPrepare exception", e)
                _state.value = PlayerState.Error(e.message ?: "播放失败")
            }
        }
    }
}
