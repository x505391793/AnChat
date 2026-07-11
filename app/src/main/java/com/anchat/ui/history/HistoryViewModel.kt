package com.anchat.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.Conversation
import com.anchat.data.repository.LocalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [HistoryScreen]: exposes the list of conversations and supports deletion.
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: LocalRepository = (app as AnChatApplication).localRepository

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** 会话 id → 未读计数（红点） */
    val unread: StateFlow<Map<Long, Int>> =
        repo.observeUnread().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            repo.observeConversations().collect { _conversations.value = it }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        viewModelScope.launch { repo.setPinned(id, pinned) }
    }
}
