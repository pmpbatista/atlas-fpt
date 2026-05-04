# Delete Transaction — Design Spec

## Overview

Add a delete action to the edit transaction screen. Only visible when editing an existing transaction. Requires confirmation before deleting.

## Affected Files

- `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt`
- `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt`

No new files required. `DeleteTransactionUseCase` already exists and handles full cleanup (including recurring rule orphan removal).

## State Changes

Add two fields to `AddTransactionUiState`:

```kotlin
val showDeleteConfirmation: Boolean = false
val isDeleted: Boolean = false
```

## ViewModel Changes

Inject `DeleteTransactionUseCase` into `AddTransactionViewModel`.

Add three functions:

- `onDeleteRequested()` — sets `showDeleteConfirmation = true`
- `onDeleteDismissed()` — sets `showDeleteConfirmation = false`
- `delete()` — calls `DeleteTransactionUseCase(transaction)` using the loaded transaction data, then sets `isDeleted = true`

`delete()` must load the full `Transaction` from the repository by `editingTransactionId` before calling the use case (since `DeleteTransactionUseCase` takes a `Transaction`, not an id).

## Screen Changes

### TopAppBar — delete icon
Add a `DeleteOutline` icon button to `TopAppBar` actions. Only rendered when `transactionId != null && transactionId != 0L` (edit mode). Calls `viewModel.onDeleteRequested()` on click.

### Navigation — pop on delete
Add a `LaunchedEffect(uiState.isDeleted)` that calls `navController.popBackStack()` when `isDeleted` is true. Mirrors the existing `isSaved` pattern.

### Confirmation dialog
Render an `AlertDialog` when `uiState.showDeleteConfirmation` is true:

- **Title:** "Delete transaction?"
- **Body:** "This cannot be undone."
- **Dismiss button:** "Cancel" — calls `viewModel.onDeleteDismissed()`
- **Confirm button:** "Delete" — text in `MaterialTheme.colorScheme.error`, calls `viewModel.delete()`

## Error Handling

No error state needed — delete is a local Room operation that does not fail under normal conditions.

## Testing

Manual: open an existing transaction in edit mode, tap the trash icon, confirm the dialog, verify the transaction is removed from the timeline.
