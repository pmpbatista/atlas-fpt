# SpendTrack

A personal finance tracker for Android. Log expenses and income, organise them by category, and get a clear picture of where your money goes each month.

## Features

- **Add transactions** — log expenses and income with amount, category, date, and an optional note
- **Timeline** — chronological view of all transactions, grouped by day
- **Monthly overview** — charts showing spending by category and month-over-month summaries
- **Categories** — pre-seeded with common expense and income categories in Portuguese; colour-coded
- **Recurring transactions** — define rules (daily / weekly / monthly / yearly) that generate scheduled entries automatically
- **Labels** — tag transactions for cross-category filtering
- **Persons** — link transactions to one or more named people; manage the persons list from Settings
- **CSV import** — import transaction history from a CSV file (format: `date, amount, type, category, note`)
- **Currency setting** — configurable currency symbol and code
- **Delete transactions** — remove any transaction from the edit screen with a confirmation step
- **Material 3 dark theme**

## Screenshots

_Coming soon_

## Requirements

- Android 8.0 (API 26) or higher

## Building

```bash
git clone https://github.com/pmpbatista/spendtrack.git
cd spendtrack
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## CSV Import Format

```
date,amount,type,category,note
2026-01-15,48.14,expense,Contas,Internet bill
2026-01-14,1500.00,income,Salário,January salary
```

- **date:** `yyyy-MM-dd` or `dd/MM/yyyy`
- **type:** `expense` or `income` (case-insensitive)
- **category:** matched against existing categories (case-insensitive); falls back to the first category of the matching type if not found

## Tech Stack

Kotlin · Jetpack Compose · Room · Hilt · Navigation Compose · WorkManager · Vico
