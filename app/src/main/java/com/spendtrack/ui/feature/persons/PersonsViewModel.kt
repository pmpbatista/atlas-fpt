package com.spendtrack.ui.feature.persons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonsUiState(
    val persons: List<Person> = emptyList(),
    val showAddDialog: Boolean = false,
    val deleteTarget: Person? = null,
    val deleteTransactionCount: Int = 0
)

@HiltViewModel
class PersonsViewModel @Inject constructor(
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _form = MutableStateFlow(PersonsUiState())

    val uiState: StateFlow<PersonsUiState> = combine(
        _form,
        personRepository.observeAll()
    ) { form, persons ->
        form.copy(persons = persons)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonsUiState()
    )

    fun onShowAddDialog() { _form.update { it.copy(showAddDialog = true) } }
    fun onDismissAddDialog() { _form.update { it.copy(showAddDialog = false) } }

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _form.update { it.copy(showAddDialog = false) }
        viewModelScope.launch {
            personRepository.save(Person(id = 0L, name = trimmed))
        }
    }

    fun onRequestDelete(person: Person) {
        viewModelScope.launch {
            val count = personRepository.countTransactions(person.id)
            _form.update { it.copy(deleteTarget = person, deleteTransactionCount = count) }
        }
    }

    fun onDismissDelete() {
        _form.update { it.copy(deleteTarget = null, deleteTransactionCount = 0) }
    }

    fun onConfirmDelete() {
        val target = _form.value.deleteTarget ?: return
        _form.update { it.copy(deleteTarget = null, deleteTransactionCount = 0) }
        viewModelScope.launch {
            personRepository.delete(target)
        }
    }
}
