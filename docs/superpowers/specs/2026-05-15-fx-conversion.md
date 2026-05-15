# Spec: FX conversion for mixed-currency totals (#28)

## Problem

Grand-total wealth across assets in different currencies is currently summed without conversion, so a portfolio with €50k + $50k displays as two separate totals. The user wants a single converted grand total alongside the per-currency breakdown.

## User-visible behaviour

- **Settings → Display currency.** New row showing the current display currency (default = the existing `currencyCode` setting). Tapping opens a picker of supported currency codes; choosing one persists the selection.
- **Assets screen header.** When the user holds assets in more than one currency, the header now shows:
  - the **converted grand total** in the display currency, as the primary headline
  - the per-currency breakdown below it (existing behaviour, smaller)
  - a "FX as of <date>" footnote when rates are available, or "FX rates unavailable" when not
- **Asset rows.** Each row keeps showing the asset's amount in its own currency (already the case via `formatAbsoluteForCurrency` — no change).
- **Refresh.** FX rates piggy-back on the existing background refresh (`RefreshPricesWorker`); a manual "Refresh prices" action will also refresh FX. No separate FX-only toggle.

## Data model

- New Room table `fx_rates`:
  - `currencyCode TEXT PRIMARY KEY` (e.g. `"USD"`)
  - `unitsPerEur REAL NOT NULL` (units of `currencyCode` per 1 EUR — matches ECB convention)
  - `fetchedAt INTEGER NOT NULL` (millis)
- Implicit base is EUR. EUR is not stored as a row — `convert(amount, "EUR", "EUR")` is identity; `convert(amount, "EUR", "USD")` is `amount * unitsPerEur(USD)`; `convert(amount, "USD", "EUR")` is `amount / unitsPerEur(USD)`; `convert(amount, "USD", "GBP")` is `amount / unitsPerEur(USD) * unitsPerEur(GBP)`.
- Migration **6 → 7** adds the table only. No backfill — first refresh populates it.

## Source

ECB euro reference rates feed: `https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml`. ~33 currencies, refreshed daily ~16:00 CET, no auth, no API key. Same `OkHttp` + `Retrofit` stack already used for the SDMX Euribor feed, but a different host — so it gets its own `Retrofit` instance, configured in `NetworkModule`.

XML shape (relevant subset):
```xml
<Cube>
  <Cube time='2026-05-14'>
    <Cube currency='USD' rate='1.0780'/>
    ...
  </Cube>
</Cube>
```

Parsing uses Android's `XmlPullParser` (no extra dep). The parser extracts `time` and the inner `currency`/`rate` attributes.

## Conversion semantics

- `ConvertCurrencyUseCase(amount, from, to)`:
  - if `from == to`: returns `amount`
  - if rates for both legs (or one leg + EUR) are present: returns the converted amount
  - if any leg is missing: returns `null` (callers decide what to show — header will fall back to "FX unavailable")
- Same-currency totals are never coerced through EUR (avoids floating-point noise).

## UI surface

- `TotalWealth` gains:
  - `totalInDisplayCurrency: Double?` — `null` if any currency couldn't be converted
  - `displayCurrencyCode: String`
  - `fxFetchedAt: Long?` — earliest `fetchedAt` of the rates actually used
- `GetTotalWealthUseCase` combines `AssetRepository.observeAssetList()`, `FxRatesRepository.observeRates()`, and `SettingsRepository.settings` to produce the enriched `TotalWealth`.
- `TotalWealthHeader` checks `totalInDisplayCurrency` and `isMixedCurrency` to decide layout.

## Out of scope (intentional)

- **Manual override per pair.** Issue mentions "manual seed" as an option. Deferred — first ship makes ECB the only source. The manifest already differentiates source in the manifest field so a follow-up can layer it in.
- **Per-asset currency override.** An asset's currency is its stored currency; we don't introduce dual currencies on a single asset.
- **Historical FX.** Conversions use the latest cached rate. Time-series FX is not stored.
- **Non-EUR base.** EUR-anchored only. If the user picks USD as display currency, the conversion still goes through cached EUR rates internally. Acceptable because every pair is achievable via EUR.

## Acceptance

- Picking a display currency in Settings persists and re-renders the Assets header.
- With ECB rates cached, the Assets header shows a single converted total + the existing per-currency rows + "FX as of <date>".
- Force-refresh on Assets refreshes prices **and** FX rates (single user action).
- When rates are missing for a currency in the portfolio, the converted total is hidden gracefully and the header shows "FX rates unavailable for X" (just the per-currency totals remain).
- Unit tests cover the ECB XML parser, the conversion math (same currency, EUR↔X, X↔Y), missing-rate handling, and the new `TotalWealth` shape.
- App passes existing instrumented tests; an additional cascade-test isn't required because `fx_rates` has no FKs.

## Open questions, resolved

- **Should the user choose a display currency separate from the existing currency setting?** Yes — they're conceptually distinct (one is the input-entry currency, the other is the totals-display currency), even if defaults match. Settled.
- **WorkManager schedule?** Reuse the existing `RefreshPricesWorker`'s 12h cadence — FX rates change daily but the cost is trivial. No new worker.
- **Where to store the chosen display currency?** `AppSettings.displayCurrencyCode: String` in SharedPreferences. Defaults to `currencyCode` on first read.
