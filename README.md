# Atlas — Financial Planner & Tracker

Android app for managing your full financial life: day-to-day expenses, investments, real estate, and long-term goal planning — in one place.

## Scope

Atlas covers three layers of personal finance:

1. **Tracking** — record where money goes (expenses, income, recurring bills) and where it sits (cash, financial assets, real estate).
2. **Planning** — set savings, investment, and life goals; project progress against them.
3. **Reviewing** — monthly overviews, asset performance, net-wealth evolution.

## Features

### Transactions
- Add expenses and income with amount, category, date, optional note, labels, and linked persons
- Timeline view grouped chronologically
- Monthly overview with category breakdown charts
- Pre-seeded Portuguese expense and income categories, colour-coded
- Recurring rules (daily / weekly / monthly / yearly) that materialise transactions automatically
- Labels for cross-category filtering
- Persons — split shared expenses; manage the persons list from Settings
- CSV import (Spendee-compatible format)

### Assets
- Real-estate properties with valuation and mortgage details
- Financial assets with ticker validation, lots (acquisitions), and yield/return stats
- Assets header with net-wealth total
- Per-asset detail screens

### Settings
- Configurable currency symbol and code
- Persons management

### Planned (see [issues](https://github.com/pmpbatista/atlas-fpt/issues))
- Long-term goal planning and progress tracking
- Sales / FIFO accounting, realized P&L, XIRR
- Dividends and total return
- FX conversion for mixed-currency wealth
- Background price refresh
- Live Euribor for variable-rate mortgages
- Photos for real-estate
- Persons filtering on timeline/overview
- App icon, instrumented tests

## Requirements

- Android 8.0 (API 26) or higher

## Building

```bash
git clone https://github.com/pmpbatista/atlas-fpt.git
cd atlas-fpt
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## CSV Import Format

```
date,amount,type,category,note
2026-01-15,48.14,expense,Contas,Internet bill
2026-01-14,1500.00,income,Salário,January salary
```

- **date:** `yyyy-MM-dd` or `dd/MM/yyyy`
- **type:** `expense` or `income` (case-insensitive; Portuguese variants accepted)
- **category:** matched against existing categories (case-insensitive); falls back to the first category of the matching type if not found

## Tech Stack

Kotlin · Jetpack Compose + Material 3 · Room · Hilt · Navigation Compose · WorkManager · Vico · Coil
