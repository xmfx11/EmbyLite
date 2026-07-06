package com.embylite.presentation.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embylite.data.local.TokenManager
import com.embylite.data.repository.EmbyRepository
import com.embylite.utils.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 登录状态
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // 登录
    fun login(server: String, username: String, password: String) {
        if (server.isBlank() || username.isBlank()) {
            _loginState.value = LoginState.Error("请填写服务器地址和用户名")
            return
        }
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val apiService = NetworkModule.createApiService(server)
                val repository = EmbyRepository(apiService)
                val result = withContext(Dispatchers.IO) {
                    repository.login(username, password)
                }
                result.onSuccess { response ->
                    val token = response.AccessToken
                    val user = response.User
                    val userId = user?.Id
                    if (!token.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                        TokenManager.saveCredentials(server, token, userId)
                        _loginState.value = LoginState.Success
                    } else {
                        _loginState.value = LoginState.Error("服务器返回无效的响应")
                    }
                }.onFailure { e ->
                    _loginState.value = LoginState.Error(mapError(e))
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(mapError(e))
            }
        }
    }

    // 自动登录：检查已保存 token 是否有效
    fun checkAutoLogin() {
        viewModelScope.launch {
            val server = TokenManager.getServer()
            val token = TokenManager.getToken()
            val userId = TokenManager.getUserId()
            if (server.isNullOrEmpty() || token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _loginState.value = LoginState.Idle
                return@launch
            }
            _loginState.value = LoginState.Loading
            try {
                val apiService = NetworkModule.createApiService(server)
                val repository = EmbyRepository(apiService)
                val result = withContext(Dispatchers.IO) { repository.getUser(userId) }
                if (result.isSuccess) {
                    _loginState.value = LoginState.Success
                } else {
                    // token 失效，清空
                    TokenManager.clear()
                    _loginState.value = LoginState.Idle
                }
            } catch (e: Exception) {
                TokenManager.clear()
                _loginState.value = LoginState.Idle
            }
        }
    }

    // 错误信息映射（友好提示）
    private fun mapError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Unable to resolve host") -> "无法连接到服务器，请检查地址是否正确"
            msg.contains("connect timed out") || msg.contains("timeout") -> "连接超时，请检查服务器是否运行"
            msg.contains("401") -> "用户名或密码错误"
            msg.contains("404") -> "服务器地址错误或不是 Emby 服务器"
            msg.contains("Failed to connect") -> "无法连接服务器，请检查地址和网络"
            else -> "登录失败: $msg"
        }
    }

    fun resetState() { _loginState.value = LoginState.Idle }
}
