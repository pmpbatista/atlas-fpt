package com.atlasfpt.ui.feature.assets.financial.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.TickerQuote
import com.atlasfpt.domain.usecase.SaveFinancialAssetUseCase
import com.atlasfpt.domain.usecase.ValidateTickerUseCase
import com.atlasfpt.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface TickerState {
    data object Idle : TickerState
    data object Validating : TickerState
    data class Valid(val quote: TickerQuote) : TickerState
    data class Invalid(val reason: String) : TickerState
    data class Error(val reason: String) : TickerState
}

data class AddFinancialFormErrors(
    val ticker: String? = null,
    val name: String? = null,
    val purchaseDate: String? = null,
    val quantity: String? = null,
    val pricePerUnit: String? = null,
) {
    val hasAny: Boolean get() = listOf(ticker, name, purchaseDate, quantity, pricePerUnit).any { it != null }
}

data class AddFinancialAssetUiState(
    val ticker: String = "",
    val tickerState: TickerState = TickerState.Idle,
    val name: String = "",
    val purchaseDate: LocalDate? = null,
    val quantity: String = "",
    val pricePerUnit: String = "",
    val notes: String = "",
    val currencyCode: String = "",
    val formErrors: AddFinancialFormErrors = AddFinancialFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = !isLoading && !formErrors.hasAny && tickerState is TickerState.Valid
}

@HiltViewModel
class AddFinancialAssetViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
    private val validateTicker: ValidateTickerUseCase,
    private val saveAsset: SaveFinancialAssetUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(AddFinancialAssetUiState())
    private var nameUserEdited = false
    private var validationJob: Job? = null

    val uiState: StateFlow<AddFinancialAssetUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AddFinancialAssetUiState().let {
                it.copy(formErrors = computeErrors(it))
            },
        )

    fun onTicker(raw: String) {
        val normalized = raw.trim().uppercase()
        _form.update {
            it.copy(
                ticker = normalized,
                tickerState = if (normalized.isBlank()) TickerState.Idle else TickerState.Validating,
            )
        }
        validationJob?.cancel()
        if (normalized.isBlank()) return
        validationJob = viewModelScope.launch {
            delay(500) // debounce
            when (val result = validateTicker(normalized)) {
                is QuoteResult.Success -> {
                    val quote = result.quote
                    _form.update { current ->
                        current.copy(
                            tickerState = TickerState.Valid(quote),
                            currencyCode = quote.currencyCode,
                            // Prefill name only if not user-edited
                            name = if (!nameUserEdited) quote.displayName else current.name,
                            pricePerUnit = if (current.pricePerUnit.isBlank())
                                String.format(java.util.Locale.US, "%.2f", quote.price)
                            else current.pricePerUnit,
                        )
                    }
                }
                is QuoteResult.NotFound ->
                    _form.update {
                        it.copy(
                            tickerState = TickerState.Invalid(
                                "Ticker not found. Try the symbol exactly as on Yahoo Finance (e.g. AAPL, VWCE.DE, BTC-USD).",
                            ),
                        )
                    }
                is QuoteResult.Error ->
                    _form.update {
                        it.copy(
                            tickerState = TickerState.Error("Couldn't reach price source. Check your connection."),
                        )
                    }
            }
        }
    }

    fun onName(v: String) {
        nameUserEdited = true
        _form.update { it.copy(name = v) }
    }

    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onQuantity(v: String) = _form.update { it.copy(quantity = v) }
    fun onPricePerUnit(v: String) = _form.update { it.copy(pricePerUnit = v) }
    fun onNotes(v: String) = _form.update { it.copy(notes = v) }

    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny || s.tickerState !is TickerState.Valid) return
        val quote = s.tickerState.quote
        val lot = FinancialLot(
            id = 0L,
            purchaseDate = s.purchaseDate!!,
            quantity = parseDecimal(s.quantity)!!,
            pricePerUnit = parseDecimal(s.pricePerUnit)!!,
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                saveAsset(s.name.trim(), s.notes.trim().takeIf { it.isNotBlank() }, quote, lot)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "An asset for this ticker already exists.")
                }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't save asset. Try again.")
                }
            }
        }
    }

    private fun computeErrors(s: AddFinancialAssetUiState): AddFinancialFormErrors {
        val today = LocalDate.now()
        val tickerErr = when (s.tickerState) {
            is TickerState.Valid -> null
            TickerState.Idle -> "Ticker is required"
            TickerState.Validating -> "Validating…" // not user-shown; gates Save
            is TickerState.Invalid -> s.tickerState.reason
            is TickerState.Error -> s.tickerState.reason
        }
        val nameErr = if (s.name.isBlank()) "Name is required" else null
        val dateErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        val qtyErr = run {
            val v = parseDecimal(s.quantity)
            if (s.quantity.isBlank() || v == null || v <= 0.0) "Quantity must be greater than 0" else null
        }
        val priceErr = run {
            val v = parseDecimal(s.pricePerUnit)
            if (s.pricePerUnit.isBlank() || v == null || v <= 0.0) "Price must be greater than 0" else null
        }
        return AddFinancialFormErrors(tickerErr, nameErr, dateErr, qtyErr, priceErr)
    }
}
