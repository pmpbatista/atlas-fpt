package com.spendtrack.ui.feature.import

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.importer.CsvImporter
import com.spendtrack.data.importer.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val isLoading: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val csvImporter: CsvImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState

    fun import(uri: Uri) {
        _uiState.value = ImportUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val result = csvImporter.import(uri)
                _uiState.value = ImportUiState(result = result)
            } catch (e: Exception) {
                _uiState.value = ImportUiState(error = e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _uiState.value = ImportUiState()
    }
}
