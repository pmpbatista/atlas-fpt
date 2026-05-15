package com.atlasfpt.ui.feature.assets.realestate.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.data.storage.PhotoStorage
import com.atlasfpt.domain.model.EnergyRating
import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.domain.usecase.DeleteAssetUseCase
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import com.atlasfpt.domain.usecase.SaveRealEstateUseCase
import com.atlasfpt.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class FormErrors(
    val name: String? = null,
    val purchaseDate: String? = null,
    val currentValue: String? = null,
    val cost: String? = null,
    val investedCapital: String? = null,
    val debtAmount: String? = null,
    val outstandingDebt: String? = null,
    val interestType: String? = null,
    val fixedRate: String? = null,
    val referenceRate: String? = null,
    val spread: String? = null,
    val creditEndDate: String? = null,
    val district: String? = null,
    val council: String? = null,
    val parish: String? = null,
    val sizeM2: String? = null,
) {
    val hasAny: Boolean
        get() = listOf(
            name, purchaseDate, currentValue, cost, investedCapital,
            debtAmount, outstandingDebt, interestType, fixedRate,
            referenceRate, spread, creditEndDate, district, council, parish, sizeM2
        ).any { it != null }
}

data class AddEditRealEstateUiState(
    val isEditMode: Boolean = false,
    val name: String = "",
    val currencyCode: String = "EUR",
    val currentValue: String = "",
    val purchaseDate: LocalDate? = null,
    val notes: String = "",
    val cost: String = "",
    val investedCapital: String = "",
    val debtAmount: String = "",
    val outstandingDebt: String = "",
    val interestType: InterestType? = null,
    val fixedRate: String = "",
    val referenceRate: ReferenceRate? = null,
    val spread: String = "",
    val creditEndDate: LocalDate? = null,
    val district: String = "",
    val council: String = "",
    val parish: String = "",
    val sizeM2: String = "",
    val energyRating: EnergyRating = EnergyRating.B,
    val photoUri: String? = null,
    val formErrors: FormErrors = FormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddEditRealEstateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveUseCase: SaveRealEstateUseCase,
    private val deleteUseCase: DeleteAssetUseCase,
    private val getUseCase: GetRealEstateUseCase,
    private val photoStorage: PhotoStorage,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val editingId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L
    private val isEditMode = editingId != 0L
    private var loadedCurrencyCode: String? = null
    private var loadedPhotoUri: String? = null

    private val initialState = AddEditRealEstateUiState(
        isEditMode = isEditMode,
        currencyCode = settingsRepository.settings.value.currencyCode
    )
    private val _form = MutableStateFlow(initialState)

    val uiState: StateFlow<AddEditRealEstateUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialState.copy(formErrors = computeErrors(initialState))
        )

    init {
        if (isEditMode) loadAsset(editingId)
    }

    private fun loadAsset(id: Long) {
        viewModelScope.launch {
            val asset = runCatching { getUseCase(id) }.getOrNull() ?: return@launch
            loadedCurrencyCode = asset.currencyCode
            loadedPhotoUri = asset.photoUri
            _form.update {
                it.copy(
                    name = asset.name,
                    currencyCode = asset.currencyCode,
                    currentValue = asset.currentValue.toCleanString(),
                    purchaseDate = asset.purchaseDate,
                    notes = asset.notes ?: "",
                    cost = asset.cost.toCleanString(),
                    investedCapital = asset.investedCapital.toCleanString(),
                    debtAmount = asset.debtAmount?.toCleanString() ?: "",
                    outstandingDebt = asset.outstandingDebt?.toCleanString() ?: "",
                    interestType = asset.interestType,
                    fixedRate = asset.fixedRate?.toCleanString() ?: "",
                    referenceRate = asset.referenceRate,
                    spread = asset.spread?.toCleanString() ?: "",
                    creditEndDate = asset.creditEndDate,
                    district = asset.district,
                    council = asset.council,
                    parish = asset.parish,
                    sizeM2 = asset.sizeM2.toCleanString(),
                    energyRating = asset.energyRating,
                    photoUri = asset.photoUri
                )
            }
        }
    }

    fun onName(v: String) = _form.update { it.copy(name = v) }
    fun onCurrencyCode(v: String) {
        if (isEditMode) return // immutable after creation
        _form.update { it.copy(currencyCode = v) }
    }
    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onCurrentValue(v: String) = _form.update { it.copy(currentValue = v) }
    fun onNotes(v: String) = _form.update { it.copy(notes = v) }
    fun onCost(v: String) = _form.update { it.copy(cost = v) }
    fun onInvestedCapital(v: String) = _form.update { it.copy(investedCapital = v) }
    fun onDebtAmount(v: String) = _form.update {
        val cleared = v.isBlank()
        it.copy(
            debtAmount = v,
            outstandingDebt = if (cleared) "" else it.outstandingDebt.ifBlank { v },
            interestType = if (cleared) null else it.interestType,
            fixedRate = if (cleared) "" else it.fixedRate,
            referenceRate = if (cleared) null else it.referenceRate,
            spread = if (cleared) "" else it.spread,
            creditEndDate = if (cleared) null else it.creditEndDate
        )
    }
    fun onOutstandingDebt(v: String) = _form.update { it.copy(outstandingDebt = v) }
    fun onInterestType(t: InterestType?) = _form.update { it.copy(interestType = t) }
    fun onFixedRate(v: String) = _form.update { it.copy(fixedRate = v) }
    fun onReferenceRate(r: ReferenceRate?) = _form.update { it.copy(referenceRate = r) }
    fun onSpread(v: String) = _form.update { it.copy(spread = v) }
    fun onCreditEndDate(v: LocalDate?) = _form.update { it.copy(creditEndDate = v) }
    fun onDistrict(v: String) = _form.update { it.copy(district = v) }
    fun onCouncil(v: String) = _form.update { it.copy(council = v) }
    fun onParish(v: String) = _form.update { it.copy(parish = v) }
    fun onSizeM2(v: String) = _form.update { it.copy(sizeM2 = v) }
    fun onEnergyRating(r: EnergyRating) = _form.update { it.copy(energyRating = r) }

    fun onPickPhoto(contentUri: Uri) {
        viewModelScope.launch {
            val newPath = photoStorage.copyInto(contentUri) ?: return@launch
            // Delete the previously-shown photo if it's an in-session pick (not the loaded one)
            val current = _form.value.photoUri
            if (current != null && current != loadedPhotoUri) {
                photoStorage.delete(current)
            }
            _form.update { it.copy(photoUri = newPath) }
        }
    }

    fun onRemovePhoto() {
        val current = _form.value.photoUri ?: return
        // Only delete if it's an in-session pick — the loaded one stays on disk until save commits
        if (current != loadedPhotoUri) {
            photoStorage.delete(current)
        }
        _form.update { it.copy(photoUri = null) }
    }

    fun showDeleteConfirmation() = _form.update { it.copy(showDeleteConfirmation = true) }
    fun hideDeleteConfirmation() = _form.update { it.copy(showDeleteConfirmation = false) }

    fun save() {
        val state = _form.value
        if (computeErrors(state).hasAny) return
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val asset = state.toDomain(id = editingId)
                saveUseCase(asset)
                // If we replaced or removed the loaded photo, delete the prior file now that the
                // new state has been persisted.
                val previous = loadedPhotoUri
                val nowSaved = state.photoUri
                if (previous != null && previous != nowSaved) {
                    photoStorage.delete(previous)
                }
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                // RealEstateRepository.save throws this when the parent row was deleted
                // out from under us. Surface a specific message; user taps back manually.
                _form.update {
                    it.copy(isLoading = false, errorMessage = "This property no longer exists")
                }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't save property. Try again.")
                }
            }
        }
    }

    fun clearErrorMessage() {
        _form.update { it.copy(errorMessage = null) }
    }

    fun confirmDelete() {
        if (!isEditMode) return
        _form.update { it.copy(showDeleteConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            try {
                deleteUseCase(editingId)
                // Clean up both the loaded photo (if any) and any in-session pick that
                // replaced it. If they're the same path, deleting twice is a no-op.
                val current = _form.value.photoUri
                loadedPhotoUri?.let { photoStorage.delete(it) }
                if (current != null && current != loadedPhotoUri) {
                    photoStorage.delete(current)
                }
                _form.update { it.copy(isLoading = false, isDeleted = true) }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Couldn't delete property. Try again."
                    )
                }
            }
        }
    }

    private fun computeErrors(s: AddEditRealEstateUiState): FormErrors {
        val today = LocalDate.now()
        val nameErr = if (s.name.isBlank()) "Name is required" else null
        val purchaseErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        fun amtErr(field: String, raw: String, requireAtLeastZero: Boolean = true): String? {
            if (raw.isBlank()) return "$field is required"
            val v = parseDecimal(raw) ?: return "$field is required"
            return if (requireAtLeastZero && v < 0) "$field must be >= 0" else null
        }
        val currentValueErr = amtErr("Current value", s.currentValue)
        val costErr = amtErr("Cost", s.cost)
        val capitalErr = amtErr("Invested capital", s.investedCapital)

        val hasDebt = s.debtAmount.isNotBlank()
        val debtAmountErr = if (hasDebt) {
            val v = parseDecimal(s.debtAmount)
            if (v == null || v <= 0) "Must be greater than 0" else null
        } else null

        val outstandingErr = if (hasDebt) amtErr("Outstanding debt", s.outstandingDebt) else null
        val interestTypeErr = if (hasDebt && s.interestType == null) "Pick fixed or variable" else null
        val fixedErr = if (hasDebt && s.interestType == InterestType.FIXED) {
            val v = parseDecimal(s.fixedRate)
            if (s.fixedRate.isBlank() || v == null) "Rate is required" else null
        } else null
        val referenceErr =
            if (hasDebt && s.interestType == InterestType.VARIABLE && s.referenceRate == null)
                "Pick a reference rate"
            else null
        val spreadErr = if (hasDebt && s.interestType == InterestType.VARIABLE) {
            val v = parseDecimal(s.spread)
            if (s.spread.isBlank() || v == null) "Spread is required" else null
        } else null
        val endDateErr = if (hasDebt) {
            when {
                s.creditEndDate == null -> "Credit end date is required"
                s.purchaseDate != null && !s.creditEndDate.isAfter(s.purchaseDate) ->
                    "Must be after purchase date"
                else -> null
            }
        } else null

        val districtErr = if (s.district.isBlank()) "Required" else null
        val councilErr = if (s.council.isBlank()) "Required" else null
        val parishErr = if (s.parish.isBlank()) "Required" else null
        val sizeErr = run {
            val v = parseDecimal(s.sizeM2)
            if (s.sizeM2.isBlank() || v == null || v <= 0)
                "Size must be greater than 0"
            else null
        }

        return FormErrors(
            name = nameErr,
            purchaseDate = purchaseErr,
            currentValue = currentValueErr,
            cost = costErr,
            investedCapital = capitalErr,
            debtAmount = debtAmountErr,
            outstandingDebt = outstandingErr,
            interestType = interestTypeErr,
            fixedRate = fixedErr,
            referenceRate = referenceErr,
            spread = spreadErr,
            creditEndDate = endDateErr,
            district = districtErr,
            council = councilErr,
            parish = parishErr,
            sizeM2 = sizeErr
        )
    }

    private fun AddEditRealEstateUiState.toDomain(id: Long): RealEstateAsset {
        val hasDebt = debtAmount.isNotBlank()
        return RealEstateAsset(
            id = id,
            name = name.trim(),
            currencyCode = currencyCode,
            currentValue = parseDecimal(currentValue)!!,
            currentValueUpdatedAt = Instant.now(),
            purchaseDate = purchaseDate!!,
            notes = notes.takeIf { it.isNotBlank() },
            cost = parseDecimal(cost)!!,
            investedCapital = parseDecimal(investedCapital)!!,
            debtAmount = if (hasDebt) parseDecimal(debtAmount) else null,
            outstandingDebt = if (hasDebt) parseDecimal(outstandingDebt) else null,
            interestType = if (hasDebt) interestType else null,
            fixedRate = if (hasDebt && interestType == InterestType.FIXED) parseDecimal(fixedRate) else null,
            referenceRate = if (hasDebt && interestType == InterestType.VARIABLE) referenceRate else null,
            spread = if (hasDebt && interestType == InterestType.VARIABLE) parseDecimal(spread) else null,
            creditEndDate = if (hasDebt) creditEndDate else null,
            district = district.trim(),
            council = council.trim(),
            parish = parish.trim(),
            sizeM2 = parseDecimal(sizeM2)!!,
            energyRating = energyRating,
            photoUri = this.photoUri
        )
    }
}

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else toString()
