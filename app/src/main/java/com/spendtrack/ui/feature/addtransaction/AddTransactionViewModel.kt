package com.spendtrack.ui.feature.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.repository.CategoryRepository
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.data.repository.TransactionRepository
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.Label
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import com.spendtrack.domain.usecase.DeleteTransactionUseCase
import com.spendtrack.domain.usecase.SaveTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddTransactionUiState(
    val amountCents: Long = 0L,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val selectedCategory: Category? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val photoUri: String? = null,
    val labels: List<Label> = emptyList(),
    val persons: List<Person> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availablePersons: List<Person> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val showCategoryPicker: Boolean = false,
    val showPersonPicker: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleted: Boolean = false
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _form = MutableStateFlow(AddTransactionUiState())
    private var editingTransactionId: Long = 0L
    private var loadedTransaction: Transaction? = null

    val uiState: StateFlow<AddTransactionUiState> = combine(
        _form,
        categoryRepository.observeAll(),
        settingsRepository.settings,
        personRepository.observeAll()
    ) { form, categories, settings, persons ->
        form.copy(availableCategories = categories, settings = settings, availablePersons = persons)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddTransactionUiState()
    )

    fun loadTransaction(id: Long) {
        editingTransactionId = id
        viewModelScope.launch {
            val tx = transactionRepository.getById(id) ?: return@launch
            loadedTransaction = tx
            _form.update {
                it.copy(
                    amountCents = (tx.amount * 100).toLong(),
                    transactionType = tx.type,
                    selectedCategory = tx.category,
                    date = tx.date,
                    note = tx.note ?: "",
                    photoUri = tx.photoUri,
                    labels = tx.labels,
                    persons = tx.persons
                )
            }
        }
    }

    fun onDigit(digit: Int) {
        _form.update { s ->
            if (s.amountCents >= 9_999_999L) s
            else s.copy(amountCents = s.amountCents * 10 + digit)
        }
    }

    fun onBackspace() {
        _form.update { it.copy(amountCents = it.amountCents / 10) }
    }

    fun onTypeToggle(type: TransactionType) {
        _form.update { it.copy(transactionType = type, selectedCategory = null) }
    }

    fun onCategorySelected(category: Category) {
        _form.update { it.copy(selectedCategory = category, showCategoryPicker = false) }
    }

    fun onShowCategoryPicker() { _form.update { it.copy(showCategoryPicker = true) } }
    fun onDismissCategoryPicker() { _form.update { it.copy(showCategoryPicker = false) } }
    fun onDateChanged(newDate: LocalDate) { _form.update { it.copy(date = newDate) } }
    fun onNoteChanged(text: String) { _form.update { it.copy(note = text) } }
    fun onPhotoUri(uri: String?) { _form.update { it.copy(photoUri = uri) } }

    fun onLabelAdded(label: Label) {
        _form.update { s ->
            if (s.labels.any { it.id == label.id }) s
            else s.copy(labels = s.labels + label)
        }
    }

    fun onLabelRemoved(label: Label) {
        _form.update { it.copy(labels = it.labels.filter { l -> l.id != label.id }) }
    }

    fun onPersonAdded(person: Person) {
        _form.update { s ->
            if (s.persons.any { it.id == person.id }) s
            else s.copy(persons = s.persons + person)
        }
    }

    fun onPersonRemoved(person: Person) {
        _form.update { it.copy(persons = it.persons.filter { p -> p.id != person.id }) }
    }

    fun onShowPersonPicker() { _form.update { it.copy(showPersonPicker = true) } }
    fun onDismissPersonPicker() { _form.update { it.copy(showPersonPicker = false) } }

    fun save() {
        val state = _form.value
        val category = state.selectedCategory ?: return
        if (state.amountCents == 0L) return
        _form.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val tx = Transaction(
                id = editingTransactionId,
                amount = state.amountCents / 100.0,
                type = state.transactionType,
                category = category,
                date = state.date,
                note = state.note.takeIf { it.isNotBlank() },
                photoUri = state.photoUri,
                labels = state.labels,
                persons = state.persons,
                recurringRuleId = null,
                isScheduled = false
            )
            saveTransaction(tx)
            _form.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    fun onDeleteRequested() { _form.update { it.copy(showDeleteConfirmation = true) } }

    fun onDeleteDismissed() { _form.update { it.copy(showDeleteConfirmation = false) } }

    fun delete() {
        val tx = loadedTransaction ?: return
        viewModelScope.launch {
            deleteTransaction(tx)
            _form.update { it.copy(isDeleted = true) }
        }
    }
}
