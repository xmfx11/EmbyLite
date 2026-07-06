package com.embylite.presentation.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embylite.data.model.EmbyItem
import com.embylite.data.repository.EmbyRepository
import com.embylite.data.local.TokenManager
import com.embylite.utils.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val item: EmbyItem) : DetailState()
    data class Error(val message: String) : DetailState()
}

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private var repository: EmbyRepository? = null

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            val server = TokenManager.getCachedServer()
            val userId = TokenManager.getCachedUserId()
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _state.value = DetailState.Error("未登录")
                return@launch
            }
            _state.value = DetailState.Loading
            try {
                val repo = repository ?: NetworkModule.createApiService(server).let { EmbyRepository(it) }
                    .also { repository = it }
                // 用 getItems(parentId=itemId) 取单项，过滤出目标
                val result = withContext(Dispatchers.IO) { repo.getItems(userId, itemId) }
                result.onSuccess { items ->
                    val target = items.firstOrNull { it.Id == itemId } ?: items.firstOrNull()
                    if (target != null) {
                        _state.value = DetailState.Success(target)
                    } else {
                        _state.value = DetailState.Error("未找到该项目")
                    }
                }.onFailure { _state.value = DetailState.Error(it.message ?: "加载失败") }
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.message ?: "加载失败")
            }
        }
    }
}
