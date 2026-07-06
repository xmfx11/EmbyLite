package com.embylite.presentation.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embylite.data.local.TokenManager
import com.embylite.data.repository.EmbyRepository
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
    data class Ready(val url: String) : PlayerState()
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
            if (server.isNullOrEmpty() || userId.isNullOrEmpty() || itemId.isEmpty()) {
                _state.value = PlayerState.Error("参数无效")
                return@launch
            }
            _state.value = PlayerState.Loading
            try {
                val repo = repository ?: NetworkModule.createApiService(server).let { EmbyRepository(it) }
                    .also { repository = it }
                val result = withContext(Dispatchers.IO) { repo.getPlaybackInfo(itemId, userId) }
                result.onSuccess { mediaSources ->
                    val source = mediaSources.firstOrNull()
                    val url = PlayerUtils.buildPlayUrl(server, itemId, source, token)
                    if (!url.isNullOrEmpty()) {
                        _state.value = PlayerState.Ready(url)
                    } else {
                        _state.value = PlayerState.Error("无法获取播放地址")
                    }
                }.onFailure { _state.value = PlayerState.Error(it.message ?: "获取播放信息失败") }
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "播放失败")
            }
        }
    }
}
