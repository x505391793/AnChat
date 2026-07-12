package com.anchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.engine.RawReplyTotals
import com.anchat.engine.core.contract.RawReply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日志页 VM：分页加载原始数据表（raw_replies，API 返回），最新在前。
 * 默认首屏 20 条，列表滚到底部时续拉 20 条，直到不足一页为止。
 */
class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AnChatApplication).localRepository
    private val pageSize = 20
    private var offset = 0

    private val _logs = MutableStateFlow<List<RawReply>>(emptyList())
    val logs: StateFlow<List<RawReply>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _totals = MutableStateFlow<RawReplyTotals?>(null)
    val totals: StateFlow<RawReplyTotals?> = _totals.asStateFlow()

    init {
        loadTotals()
        loadMore()
    }

    private fun loadTotals() {
        viewModelScope.launch { _totals.value = repo.getRawReplyTotals() }
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val page = repo.getRawRepliesPaged(pageSize, offset)
                _logs.value = _logs.value + page
                offset += pageSize
                if (page.size < pageSize) _hasMore.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
}
