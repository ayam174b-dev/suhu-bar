package com.tempmonitor.app.ui.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tempmonitor.app.data.TemperatureReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class InfoViewModel @Inject constructor(
    private val reader: TemperatureReader
) : ViewModel() {

    private val _diagnostics = MutableStateFlow<TemperatureReader.Diagnostics?>(null)
    val diagnostics: StateFlow<TemperatureReader.Diagnostics?> = _diagnostics.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { reader.getDiagnostics() }
            _diagnostics.value = snapshot
        }
    }
}
