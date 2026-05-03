package com.tempmonitor.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tempmonitor.app.data.db.GameSession
import com.tempmonitor.app.data.preferences.MonitorPreferences
import com.tempmonitor.app.data.repository.GameSessionRepository
import com.tempmonitor.app.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: GameSessionRepository,
    val preferences: MonitorPreferences
) : ViewModel() {

    val active: StateFlow<GameSession?> = sessionManager.active

    val recent: StateFlow<List<GameSession>> = flowOf(Unit)
        .flatMapLatest { repository.observeRecent(50) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
