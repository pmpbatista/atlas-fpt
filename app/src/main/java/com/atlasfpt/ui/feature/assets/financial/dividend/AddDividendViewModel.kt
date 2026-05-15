package com.atlasfpt.ui.feature.assets.financial.dividend

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.DividendRepository
import com.atlasfpt.domain.model.Dividend
import com.atlasfpt.domain.usecase.AddDividendUseCase
import com.atlasfpt.domain.usecase.GetFinancialAssetUseCase
import com.atlasfpt.domain.usecase.UpdateDividendUseCase
import com.atlasfpt.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddDividendFormErrors(
    val payDate: String? = null,
    val grossAmount: String? = null,
) {
    val hasAny: Boolean get() = payDate != null || grossAmount != null
}

data class AddDividendUiState(
    val isEditMode: Boolean = false,
    val ticker: String = "",
    val currencyCode: String = "",
    val payDate: LocalDate? = null,
    val grossAmount: String = "",
    val note: String = "",
    val formErrors: AddDividendFormErrors = AddDividendFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddDividendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetFinancialAssetUseCase,
    private val dividendRepository: DividendRepository,
    private val addDividend: AddDividendUseCase,
    private val updateDividend: UpdateDividendUseCase,
) : ViewModel() {

    private val assetId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L
    private val dividendId: Long = savedStateHandle.get<String>("dividendId")?.toLongOrNull() ?: 0L
    private val isEditMode = dividendId != 0L

    private val initial = AddDividendUiState(isEditMode = isEditMode)
    private val _form = MutableStateFlow(initial)

    val uiState: StateFlow<AddDividendUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initial.copy(formErrors = computeErrors(initial)),
        )

    init { if (assetId != 0L) loadInitial() }

    private fun loadInitial() {
        viewModelScope.launch {
            val asset = runCatching { getAsset(assetId) }.getOrNull() ?: return@launch
            _form.update { current ->
                if (isEditMode) {
                    val existing = dividendRepository.getById(dividendId) ?: return@update current
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        payDate = existing.payDate,
                        grossAmount = trimZeros(existing.grossAmount),
                        note = existing.note.orEmpty(),
                    )
                } else {
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        payDate = current.payDate ?: LocalDate.now(),
                    )
                }
            }
        }
    }

    fun onPayDate(v: LocalDate?) = _form.update { it.copy(payDate = v) }
    fun onGrossAmount(v: String) = _form.update { it.copy(grossAmount = v) }
    fun onNote(v: String) = _form.update { it.copy(note = v) }
    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny) return
        val dividend = Dividend(
            id = if (isEditMode) dividendId else 0L,
            payDate = s.payDate!!,
            grossAmount = parseDecimal(s.grossAmount)!!,
            note = s.note.ifBlank { null },
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (isEditMode) updateDividend(assetId, dividend) else addDividend(assetId, dividend)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: Throwable) {
                _form.update { it.copy(isLoading = false, errorMessage = "Couldn't save dividend. Try again.") }
            }
        }
    }

    private fun computeErrors(s: AddDividendUiState): AddDividendFormErrors {
        val today = LocalDate.now()
        val dateErr = when {
            s.payDate == null -> "Pay date is required"
            s.payDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        val amountErr = run {
            val v = parseDecimal(s.grossAmount)
            if (s.grossAmount.isBlank() || v == null || v <= 0.0) "Amount must be greater than 0" else null
        }
        return AddDividendFormErrors(dateErr, amountErr)
    }

    private fun trimZeros(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
