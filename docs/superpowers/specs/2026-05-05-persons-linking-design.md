# Persons Linking — Design Spec

**Date:** 2026-05-05  
**Feature:** Link expenses and income entries to one or more persons

---

## Overview

Users can associate any transaction (expense or income) with one or more named persons. Persons are a distinct concept from labels — they represent real people involved in a transaction (e.g., who shared a bill, who paid, who was reimbursed). Person management (create, delete) is done in a dedicated Settings sub-screen. Linking happens in the Add/Edit Transaction screen.

---

## Data Model

### New domain model

```kotlin
// domain/model/Person.kt
data class Person(val id: Long, val name: String)
```

### Updated `Transaction`

Add `val persons: List<Person>` alongside the existing `labels` field.

### New DB tables (DB version 1 → 2)

**`persons`**
| column | type |
|---|---|
| `id` | INTEGER PK AUTOINCREMENT |
| `name` | TEXT NOT NULL |

**`transaction_person_cross_ref`**
| column | type |
|---|---|
| `transactionId` | INTEGER FK → `transactions(id)` ON DELETE CASCADE |
| `personId` | INTEGER FK → `persons(id)` ON DELETE CASCADE |

Primary key is `(transactionId, personId)`. Both foreign keys use `ON DELETE CASCADE` so Room automatically removes cross-refs when either side is deleted. The delete-warning dialog in the UI fires *before* Room executes the cascade.

### Migration

A `Migration(1, 2)` object runs the two `CREATE TABLE` statements and creates an index on `transaction_person_cross_ref.personId`.

---

## Data Layer

### New files

| File | Purpose |
|---|---|
| `data/db/entity/PersonEntity.kt` | Room entity for `persons` table |
| `data/db/entity/TransactionPersonCrossRef.kt` | Cross-ref entity mirroring `TransactionLabelCrossRef` |
| `data/db/dao/PersonDao.kt` | `observeAll()`, `insert()`, `delete()`, `countTransactions(personId)` |
| `data/repository/PersonRepository.kt` | Wraps PersonDao; same shape as `LabelRepository` plus `countTransactions(personId): Int` |

### Modified files

- **`TransactionWithDetails.kt`** — add a `persons: List<PersonEntity>` `@Relation` via `Junction(TransactionPersonCrossRef::class, parentColumn = "transactionId", entityColumn = "personId")`
- **`Mappers.kt`** — add `PersonEntity.toDomain()` and `Person.toEntity()`; update `TransactionWithDetails.toDomain()` to map `persons`; update `Transaction.toEntity()` (no change needed, persons are in cross-ref only)
- **`TransactionDao.kt`** — add `insertPersonCrossRef(TransactionPersonCrossRef)` and `deleteAllPersonsForTransaction(transactionId: Long)`
- **`TransactionRepository.kt`** — in `save()`, after the existing label block, delete then re-insert person cross-refs
- **`AppDatabase.kt`** — version 2; add `PersonEntity` and `TransactionPersonCrossRef` to `entities`; add `abstract fun personDao(): PersonDao`; add `Migration(1, 2)` to the builder
- **`DatabaseModule.kt`** — add `providePersonDao(db: AppDatabase): PersonDao`

`PersonRepository` uses `@Singleton` + `@Inject constructor(private val dao: PersonDao)` — no explicit Hilt module entry needed.

---

## UI — Add/Edit Transaction screen

### Form layout change

Below the existing labels chips row, add:

1. A `FlowRow` of person chips (shown only when `persons` is non-empty). Each chip is a `PersonChip` — a thin wrapper around `LabelChip` that passes `MaterialTheme.colorScheme.secondaryContainer` / `onSecondaryContainer` as background and content colors. `LabelChip` gains two optional color parameters (`containerColor`, `contentColor`) defaulting to the current `surfaceVariant` / `onSurfaceVariant` values so existing call-sites are unaffected.
2. A persistent "Link persons…" row styled identically to the category and date rows, with a person icon. Tapping it shows `PersonPickerBottomSheet`.

### `AddTransactionUiState` additions

```kotlin
val persons: List<Person> = emptyList()
val availablePersons: List<Person> = emptyList()
val showPersonPicker: Boolean = false
```

### `AddTransactionViewModel` changes

- Inject `PersonRepository`
- Extend the `combine()` to include `personRepository.observeAll()` as a 4th stream, merging into `availablePersons`
- `loadTransaction()` copies `tx.persons` into form state
- `save()` passes `persons = state.persons` in the `Transaction` constructor
- New handlers: `onPersonAdded(Person)`, `onPersonRemoved(Person)`, `onShowPersonPicker()`, `onDismissPersonPicker()`

### New component: `PersonPickerBottomSheet`

`ModalBottomSheet` containing a `LazyColumn` of all persons. Each row shows a checkbox (checked if the person is in `selectedPersons`) and the person's name. Tapping a row calls `onToggle(Person)`. Footer note: "Persons managed in Settings".

Signature:
```kotlin
@Composable
fun PersonPickerBottomSheet(
    allPersons: List<Person>,
    selectedPersons: List<Person>,
    onToggle: (Person) -> Unit,
    onDismiss: () -> Unit
)
```

---

## UI — Persons sub-screen (Settings)

### New screen

Route: `"persons"` → `Screen.Persons`

**`PersonsScreen`**: `Scaffold` with a `TopAppBar` (back navigation, title "Persons", `+` action icon). Body is a `LazyColumn` with one row per person — name text + delete `IconButton`. Empty state shows "No persons yet. Tap + to add one."

**Add flow**: Tapping `+` shows an `AlertDialog` with an `OutlinedTextField` for the name. Confirm trims whitespace and calls `viewModel.addPerson(name)`. The confirm button is disabled when the trimmed name is blank.

**Delete flow**:
1. Tapping the delete icon calls `viewModel.requestDelete(person)`.
2. The ViewModel fetches `personRepository.countTransactions(person.id)` to determine the count.
3. An `AlertDialog` is shown:
   - If count == 0: "Delete [name]? This cannot be undone."
   - If count > 0: "Delete [name]? This person is linked to N transactions. Deleting will remove the link from all of them."
4. Confirming calls `viewModel.confirmDelete()` → `personRepository.delete(person)` → Room CASCADE removes cross-refs.

**`PersonsViewModel` state**:
```kotlin
data class PersonsUiState(
    val persons: List<Person> = emptyList(),
    val showAddDialog: Boolean = false,
    val deleteTarget: Person? = null,
    val deleteTransactionCount: Int = 0
)
```

### Settings integration

Add a new `SettingsRow` in `SettingsScreen` above the existing Currency row:
- Icon: `Icons.Default.Person`
- Title: `"Persons"`
- Subtitle: `"Manage persons"`
- `onClick`: `navController.navigate(Screen.Persons.route)`

Add `Screen.Persons` to the `Screen` sealed class in `AppNavGraph.kt` and register its `composable`.

---

## What is NOT in scope

- Renaming a person after creation (can be added later)
- Filtering the timeline/overview by person
- Showing persons on `TransactionRow` in the timeline (can be added later)
- Person avatars or any metadata beyond name

---

## File change summary

| File | Change |
|---|---|
| `domain/model/Person.kt` | **new** |
| `domain/model/Transaction.kt` | add `persons` field |
| `data/db/entity/PersonEntity.kt` | **new** |
| `data/db/entity/TransactionPersonCrossRef.kt` | **new** |
| `data/db/entity/TransactionWithDetails.kt` | add persons `@Relation` |
| `data/db/entity/Mappers.kt` | add Person mappers, update `toDomain()` |
| `data/db/dao/PersonDao.kt` | **new** |
| `data/db/dao/TransactionDao.kt` | add person cross-ref methods |
| `data/repository/PersonRepository.kt` | **new** |
| `data/repository/TransactionRepository.kt` | save person cross-refs |
| `data/db/AppDatabase.kt` | version 2, new entities, migration, personDao |
| `di/DatabaseModule.kt` | providePersonDao |
| `ui/component/LabelChip.kt` | add optional `containerColor`/`contentColor` params |
| `ui/component/PersonChip.kt` | **new** — wraps `LabelChip` with secondary container colors |
| `ui/component/PersonPickerBottomSheet.kt` | **new** |
| `ui/feature/addtransaction/AddTransactionUiState` | add persons fields |
| `ui/feature/addtransaction/AddTransactionViewModel.kt` | inject PersonRepository, new handlers |
| `ui/feature/addtransaction/AddTransactionScreen.kt` | person chips + row + bottom sheet |
| `ui/feature/persons/PersonsScreen.kt` | **new** |
| `ui/feature/persons/PersonsViewModel.kt` | **new** |
| `ui/feature/settings/SettingsScreen.kt` | add Persons row |
| `ui/navigation/AppNavGraph.kt` | add Screen.Persons, composable |
