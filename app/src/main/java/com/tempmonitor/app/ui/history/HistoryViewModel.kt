package com.tempmonitor.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tempmonitor.app.data.db.TemperatureRecord
import com.tempmonitor.app.data.repository.TemperatureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.util.concurrent.TimeUnit

enum class HistoryRange(val label: String, val durationMs: Long) {
    ONE_HOUR("1h", TimeUnit.HOURS.toMillis(1)),
    SIX_HOURS("6h", TimeUnit.HOURS.toMillis(6)),
    TWENTY_FOUR_HOURS("24h", TimeUnit.HOURS.toMillis(24))
}

data class HistoryStats(
    val min: Float?,
    val max: Float?,
    val avg: Float?,
    val count: Int
)

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.ONE_HOUR,
    val records: List<TemperatureRecord> = emptyList(),
    val stats: HistoryStats = HistoryStats(null, null, null, 0)
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @Inject constructor(
    private val repository: TemperatureRepository
) : ViewModel() {

    private val rangeState = MutableStateFlow(HistoryRange.ONE_HOUR)
    val range: StateFlow<HistoryRange> = rangeState.asStateFlow()

    private val recordsFlow = rangeState.flatMapLatest { range ->
        val now = System.currentTimeMillis()
        repository.observeInRange(now - range.durationMs, now)
    }

    val uiState: StateFlow<HistoryUiState> = combine(rangeState, recordsFlow) { range, records ->
        HistoryUiState(
            range = range,
            records = records,
            stats = computeStats(records)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun setRange(range: HistoryRange) {
        rangeState.value = range
    }

    private fun computeStats(records: List<TemperatureRecord>): HistoryStats {
        if (records.isEmpty()) return HistoryStats(null, null, null, 0)
        val temps = records.map { it.batteryTemp }
        return HistoryStats(
            min = temps.min(),
            max = temps.max(),
            avg = temps.average().toFloat(),
            count = records.size
        )
    }
}
