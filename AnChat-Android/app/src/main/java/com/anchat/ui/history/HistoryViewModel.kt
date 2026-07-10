package com.anchat.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.Conversation
import com.anchat.data.repository.LocalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val localRepo: LocalRepository = (app as AnChatApplication).localRepository

    val conversations: Flow<List<Conversation>> = localRepo.observeConversations()

    fun delete(id: Long) {
        viewModelScope.launch { localRepo.deleteConversation(id) }
    }
}
