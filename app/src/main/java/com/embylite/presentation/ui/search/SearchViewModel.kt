package com.embylite.presentation.ui.search

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

class SearchViewModel : ViewModel() {

    private val _results = MutableStateFlow<List<EmbyItem>>(emptyList())
    val results: StateFlow<List<EmbyItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var repository: EmbyRepository? = null

    fun search(term: String) {
        if (term.isBlank()) {
            _results.value = emptyList()
            _hasSearched.value = false
            return
        }
        viewModelScope.launch {
            val server = TokenManager.getCachedServer()
            val userId = TokenManager.getCachedUserId()
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) return@launch

            _isSearching.value = true
            _hasSearched.value = true
            try {
                val repo = repository ?: NetworkModule.createApiService(server).let { EmbyRepository(it) }
                    .also { repository = it }
                val result = withContext(Dispatchers.IO) { repo.search(userId, term) }
                result.onSuccess { _results.value = it }
                    .onFailure { _results.value = emptyList() }
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
}
