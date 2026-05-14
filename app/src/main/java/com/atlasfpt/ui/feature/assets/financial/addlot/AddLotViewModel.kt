package com.atlasfpt.ui.feature.assets.financial.addlot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.usecase.AddLotUseCase
import com.atlasfpt.domain.usecase.GetFinancialAssetUseCase
import com.atlasfpt.domain.usecase.UpdateLotUseCase
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

data class AddLotFormErrors(
    val purchaseDate: String? = null,
    val quantity: String? = null,
    val pricePerUnit: String? = null,
) {
    val hasAny: Boolean get() = listOf(purchaseDate, quantity, pricePerUnit).any { it != null }
}

data class AddLotUiState(
    val isEditMode: Boolean = false,
    val ticker: String = "",
    val currencyCode: String = "",
    val purchaseDate: LocalDate? = null,
    val quantity: String = "",
    val pricePerUnit: String = "",
    val formErrors: AddLotFormErrors = AddLotFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddLotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetFinancialAssetUseCase,
    private val addLotUseCase: AddLotUseCase,
    private val updateLotUseCase: UpdateLotUseCase,
) : ViewModel() {

    private val assetId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L
    private val lotId: Long = savedStateHandle.get<String>("lotId")?.toLongOrNull() ?: 0L
    private val isEditMode = lotId != 0L

    private val initial = AddLotUiState(isEditMode = isEditMode)
    private val _form = MutableStateFlow(initial)

    val uiState: StateFlow<AddLotUiState> = _form
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
                    val lot = asset.lots.firstOrNull { it.id == lotId } ?: return@update current
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        purchaseDate = lot.purchaseDate,
                        quantity = trimZeros(lot.quantity),
                        pricePerUnit = trimZeros(lot.pricePerUnit),
                    )
                } else {
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        purchaseDate = current.purchaseDate ?: LocalDate.now(),
                        pricePerUnit = if (current.pricePerUnit.isBlank() && asset.latestPrice != null)
                            "%.2f".format(java.util.Locale.US, asset.latestPrice)
                        else current.pricePerUnit,
                    )
                }
            }
        }
    }

    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onQuantity(v: String) = _form.update { it.copy(quantity = v) }
    fun onPricePerUnit(v: String) = _form.update { it.copy(pricePerUnit = v) }
    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny) return
        val lot = FinancialLot(
            id = if (isEditMode) lotId else 0L,
            purchaseDate = s.purchaseDate!!,
            quantity = parseDecimal(s.quantity)!!,
            pricePerUnit = parseDecimal(s.pricePerUnit)!!,
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (isEditMode) updateLotUseCase(assetId, lot) else addLotUseCase(assetId, lot)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                _form.update { it.copy(isLoading = false, errorMessage = "This asset no longer exists.") }
            } catch (t: Throwable) {
                _form.update { it.copy(isLoading = false, errorMessage = "Couldn't save lot. Try again.") }
            }
        }
    }

    private fun computeErrors(s: AddLotUiState): AddLotFormErrors {
        val today = LocalDate.now()
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
        return AddLotFormErrors(dateErr, qtyErr, priceErr)
    }

    private fun trimZeros(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
