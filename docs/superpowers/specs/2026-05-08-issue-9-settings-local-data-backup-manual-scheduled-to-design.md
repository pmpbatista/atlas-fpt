# Settings: local data backup (manual + scheduled) to user-chosen folder — Design Spec

**Date:** 2026-05-08
**Issue:** #9
**Source:** Settings: local data backup (manual + scheduled) to user-chosen folder

## Goal

Add a Backup section to Settings that lets the user pick a folder via the Storage Access Framework (SAF), run an ad-hoc backup, and optionally schedule a recurring backup via WorkManager. Each backup writes a single timestamped zip into the chosen folder containing the Room DB (consistently snapshotted) plus a small manifest and the user's settings. Optional retention prunes old files. Local-only — no cloud, no encryption (both called out explicitly as follow-ups).

## Non-goals

- **Restore.** Backup only. A future spec turns the manifest's `dbVersion` into a restore flow with Room migration.
- **Cloud sync** (Drive / Dropbox / etc.). The chosen folder is wherever SAF lets the user write — if they pick a Drive-synced folder, that's their decision; we don't integrate with any cloud SDK.
- **Encryption / passphrase protection.** Open question in the issue; deferred. Plain zip for v1, called out in the Settings copy so the user knows.
- **Per-table selective backup.** All-or-nothing.
- **Cross-app share** of the backup file (no `FileProvider`, no `ACTION_SEND`).
- **CSV-format backup.** The CSV import path is unchanged; backup uses the binary DB.
- **Foreground notification.** Backups are small (KBs to a few MB) and finish in seconds; a `CoroutineWorker` without foreground promotion is enough.
- **Migrating `AppSettings` from SharedPreferences to Room.** Settings stay in SharedPreferences; the backup serializes a JSON snapshot.

## Decisions

### 1. Folder selection via SAF

Use `ActivityResultContracts.OpenDocumentTree`. The returned `Uri` is persisted via `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)` so it survives process death and reboots.

The persisted tree URI string is stored in `BackupSettings.treeUri`.

A subsequent re-pick releases the previous persisted permission via `releasePersistableUriPermission` to keep the OS's persistable list tidy (Android caps it at ~128 URIs per app).

### 2. Backup file shape

A standard zip (`java.util.zip`) with three entries:

```
spendtrack-backup-20260508-143012.zip
├── spendtrack.db        (binary, the SQLite database file)
├── settings.json        ({ "currencyCode": "EUR", "currencySymbol": "€" })
└── manifest.json        ({ "appVersion": "1.0", "dbVersion": 4,
                            "createdAt": "2026-05-08T14:30:12Z",
                            "schemaHash": null })
```

- The DB file is the only thing copied — `-wal` and `-shm` sidecars are *not* included. We force them into the main file before copying (see §3).
- `settings.json` mirrors the current `AppSettings` shape. Restore (out of scope) would deserialize and call `SettingsRepository.updateCurrency`.
- `manifest.json` captures everything a future restore needs to refuse old backups gracefully. `schemaHash` is reserved as `null` for now — a future spec can populate it via Room's identity hash if we want strict cross-version verification.
- `appVersion` is read from `BuildConfig.VERSION_NAME`.

### 3. Atomicity — WAL checkpoint

Room runs in WAL mode by default. Just copying `spendtrack.db` would miss the journal. Pre-snapshot:

```kotlin
db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
```

This forces all pending WAL data into the main DB file and truncates the journal. After this point the on-disk `spendtrack.db` is a complete consistent snapshot. We do *not* need to close + reopen the database, which would be invasive (every active query would fail).

The `TRUNCATE` mode is the strongest available — it succeeds only when no concurrent writers hold the WAL. Since backup runs on a `Dispatchers.IO` coroutine and the rest of the app sees the same singleton DB, briefly serializing on the writable database is fine.

### 4. Atomicity — `.tmp` then rename

Within the SAF tree, write to `spendtrack-backup-…tmp.zip` first, then rename to `spendtrack-backup-….zip` via `DocumentsContract.renameDocument(resolver, docUri, finalName)`. If `renameDocument` returns `null` (some providers don't support rename), fall back to copy-into-final + delete-tmp. If the final write completes but the rename fails on a non-rename-capable provider, the tmp file is left in place — surfaced as an error so the user sees what happened.

Existing files are not overwritten silently — we generate a unique filename (with the timestamp). Filename collision in the same second is vanishingly unlikely; if it ever happens, append a `-1` suffix.

### 5. Retention

Optional. Behind a toggle in the Settings UI; default ON, default keep N = 5.

After a successful backup, list the tree's `DocumentFile` children, filter by the regex `^spendtrack-backup-\d{8}-\d{6}\.zip$`, sort by name descending (timestamps in filenames sort lexicographically), and delete everything past the first N.

If the user disables retention later, existing pruning history is irrelevant — the next run just stops pruning.

### 6. WorkManager scheduling

```kotlin
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runBackup: RunBackupUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val r = runBackup()) {
        is BackupResult.Success -> Result.success()
        is BackupResult.NoFolder -> Result.failure()  // user must re-pick; can't recover automatically
        is BackupResult.IoError -> Result.retry()      // transient
    }
    companion object { const val WORK_NAME = "scheduled_backup" }
}
```

On enable / frequency-change:

```kotlin
val period = when (frequency) {
    DAILY   -> Duration.ofDays(1)
    WEEKLY  -> Duration.ofDays(7)
    MONTHLY -> Duration.ofDays(30)
}
val req = PeriodicWorkRequestBuilder<BackupWorker>(period.toMillis(), TimeUnit.MILLISECONDS)
    .setConstraints(Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build())
    .build()
WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(BackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
```

On disable:

```kotlin
WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.WORK_NAME)
```

`PeriodicWorkRequest`'s minimum interval is 15 minutes, so all three frequencies are fine. "Monthly" via 30-day periodic is approximate but matches user expectations on a phone (no calendar-aware scheduling).

The custom WorkManager initializer pattern is already in place (see `SpendTrackApplication`); `@HiltWorker` integrates the same way `RecurringTransactionWorker` does.

### 7. Settings inclusion

Yes — serialize `AppSettings` as `settings.json` inside the zip. The user's currency is part of "their data" in the same sense as transactions. Restore replays it via `SettingsRepository.updateCurrency`.

The `BackupSettings` itself (folder URI, frequency, retention) is *not* included — including it would cause restore to overwrite the destination user's chosen folder with the source user's, which is wrong.

### 8. Permission-revocation handling

On every backup attempt, before any IO:

```kotlin
val tree = settings.treeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
if (tree == null || !tree.exists() || !tree.canWrite()) {
    return BackupResult.NoFolder
}
```

The Settings UI shows this as `"Backup folder is no longer accessible — pick a folder"` in the row's subtitle and red-tints the chevron. The "Back up now" button stays enabled but always returns `NoFolder` until the user re-picks.

Scheduled `BackupWorker` returns `Result.failure()` on `NoFolder` — failure means the periodic work won't auto-retry, but the next scheduled run still fires per period. The `lastBackupError` field is updated so the user sees the failure in Settings on next open.

### 9. State surfaces

**`BackupSettings`** — new data class persisted to a separate SharedPreferences file (`spendtrack_backup`):

```kotlin
data class BackupSettings(
    val treeUri: String? = null,
    val scheduleEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val retentionEnabled: Boolean = true,
    val retentionKeep: Int = 5,
    val lastBackupAt: Long? = null,        // epoch millis, null = "Never"
    val lastBackupFileName: String? = null,
    val lastBackupError: String? = null,   // null when last run succeeded
)

enum class BackupFrequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"),
}
```

**`BackupSettingsRepository`** — same shape as `SettingsRepository`. `StateFlow<BackupSettings>` exposed; updates write to prefs and emit.

Why a separate prefs file from `spendtrack_settings`? The user-data settings (currency) belong in the backup zip; the backup configuration does not. Separating storage means we never accidentally serialize the wrong bucket.

### 10. Error surfacing

Three buckets:

| Result | UI behavior |
|---|---|
| `BackupResult.Success(fileName)` | Snackbar `"Backup saved: $fileName"`. `lastBackupAt`/`lastBackupFileName` updated. `lastBackupError` cleared. |
| `BackupResult.NoFolder` | Snackbar `"Backup folder is no longer accessible — please pick one"`. `lastBackupError = "folder revoked"`. |
| `BackupResult.IoError(cause)` | Snackbar `"Backup failed: $cause"` (truncated). `lastBackupError = cause`. |

For scheduled runs, no snackbar is possible — `lastBackupError` is the only feedback channel until next time the user opens Settings.

### 11. Filename timestamp convention

`spendtrack-backup-${yyyyMMdd}-${HHmmss}.zip` in the **device's local time zone**, not UTC. The user sees this name in their file manager and expects it to match wall-clock time. The manifest's `createdAt` is ISO-8601 UTC (machine-readable; survives time-zone shifts).

## Affected components

```
app/src/main/java/com/spendtrack/
├── data/
│   ├── backup/                                (NEW package)
│   │   ├── BackupSettings.kt
│   │   ├── BackupSettingsRepository.kt
│   │   ├── BackupResult.kt                    (sealed: Success / NoFolder / IoError)
│   │   ├── BackupFileWriter.kt                (zip + manifest + settings.json into the SAF tree)
│   │   └── BackupRetentionPruner.kt
│   └── worker/
│       └── BackupWorker.kt                    (NEW — mirrors RecurringTransactionWorker)
├── di/
│   └── WorkerModule.kt                        (add BackupWorker provision if needed)
├── domain/usecase/
│   └── RunBackupUseCase.kt                    (NEW — orchestrates checkpoint → write → prune)
├── ui/feature/settings/
│   ├── SettingsScreen.kt                      (NEW Backup section with rows + dialogs)
│   └── SettingsViewModel.kt                   (inject BackupSettingsRepository + RunBackupUseCase + WorkManager scheduler)
└── SpendTrackApplication.kt                   (no change; existing custom WorkManager init covers BackupWorker)
```

No new permissions in the manifest. SAF's tree URIs don't need `READ_EXTERNAL_STORAGE` — the persistable URI grant is enough on all supported API levels (minSdk 26).

No new dependencies — `androidx.documentfile:documentfile` (pulled in transitively by other AndroidX libs) covers `DocumentFile.fromTreeUri`, and `java.util.zip` plus `kotlinx.serialization-json` (already present from the Yahoo network layer) cover the zip + JSON.

## Implementation Notes

### `RunBackupUseCase`

```kotlin
class RunBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val backupSettings: BackupSettingsRepository,
    private val appSettings: SettingsRepository,
    private val writer: BackupFileWriter,
    private val pruner: BackupRetentionPruner,
) {
    suspend operator fun invoke(): BackupResult = withContext(Dispatchers.IO) {
        val cfg = backupSettings.settings.value
        val tree = cfg.treeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
            ?: return@withContext BackupResult.NoFolder
        if (!tree.exists() || !tree.canWrite()) return@withContext BackupResult.NoFolder

        try {
            // 1. checkpoint WAL → main DB
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

            // 2. serialize settings + manifest
            val settingsJson = Json.encodeToString(appSettings.settings.value)
            val manifestJson = Json.encodeToString(BackupManifest(
                appVersion = BuildConfig.VERSION_NAME,
                dbVersion = AppDatabase.VERSION,
                createdAt = Instant.now().toString(),
                schemaHash = null,
            ))

            // 3. write zip
            val now = ZonedDateTime.now()
            val finalName = "spendtrack-backup-${now.format(YYYYMMDD_HHMMSS)}.zip"
            val dbFile = context.getDatabasePath("spendtrack.db")  // path to the actual SQLite file
            val written = writer.write(tree, finalName, dbFile, settingsJson, manifestJson)

            // 4. retention
            if (cfg.retentionEnabled) pruner.prune(tree, cfg.retentionKeep)

            // 5. update last-success
            backupSettings.recordSuccess(written.fileName, written.timestampMillis)
            BackupResult.Success(written.fileName)
        } catch (t: IOException) {
            backupSettings.recordError(t.message ?: t.javaClass.simpleName)
            BackupResult.IoError(t.message ?: "I/O error")
        }
    }
}
```

`AppDatabase.VERSION` is exposed as a `companion object const` — referenced by Room's `@Database(version = 4)` annotation, kept in sync manually (single line, easy to remember).

### `BackupFileWriter`

```kotlin
@Singleton
class BackupFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun write(
        tree: DocumentFile,
        finalName: String,
        dbFile: File,
        settingsJson: String,
        manifestJson: String,
    ): WriteResult {
        val tmpName = finalName.removeSuffix(".zip") + ".tmp.zip"
        val tmpDoc = tree.createFile("application/zip", tmpName)
            ?: throw IOException("Couldn't create $tmpName")
        try {
            context.contentResolver.openOutputStream(tmpDoc.uri).use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zip ->
                    zip.putEntry("spendtrack.db", dbFile.inputStream())
                    zip.putEntry("settings.json", settingsJson.byteInputStream())
                    zip.putEntry("manifest.json", manifestJson.byteInputStream())
                }
            }
            // Rename .tmp.zip → final
            val finalUri = DocumentsContract.renameDocument(
                context.contentResolver, tmpDoc.uri, finalName,
            ) ?: throw IOException("Rename failed (provider doesn't support rename)")
            return WriteResult(finalName, System.currentTimeMillis())
        } catch (t: Throwable) {
            // Best-effort tmp cleanup
            runCatching { tmpDoc.delete() }
            throw t
        }
    }
}
```

`putEntry` is a tiny extension that creates a `ZipEntry`, copies the stream, closes the entry. Standard pattern.

### `BackupRetentionPruner`

```kotlin
@Singleton
class BackupRetentionPruner @Inject constructor() {
    private val pattern = Regex("""^spendtrack-backup-\d{8}-\d{6}\.zip$""")

    fun prune(tree: DocumentFile, keep: Int) {
        val matched = tree.listFiles()
            .filter { it.isFile && pattern.matches(it.name.orEmpty()) }
            .sortedByDescending { it.name }
        matched.drop(keep).forEach { runCatching { it.delete() } }
    }
}
```

Sort by name (lexicographic) is equivalent to sort by timestamp because the timestamp format is fixed-width. `runCatching { it.delete() }` swallows individual delete failures — partial pruning is better than failing the whole pruning step.

### Settings UI — Backup section

Insert above the existing "Import from CSV" row:

```kotlin
HorizontalDivider()
Text("Backup", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))

SettingsRow(
    icon = Icons.Default.FolderOpen,
    title = "Backup folder",
    subtitle = backupState.folderDisplayName ?: "Tap to choose a folder",
    onClick = { folderPickerLauncher.launch(null) },
)
SettingsRow(
    icon = Icons.Default.Backup,
    title = "Back up now",
    subtitle = backupState.lastBackupSummary,    // "Last: 2026-05-08 14:30 · spendtrack-backup-….zip" or "Never"
    onClick = { viewModel.runBackupNow() },
)
ToggleSettingsRow(
    icon = Icons.Default.Schedule,
    title = "Scheduled backup",
    subtitle = if (backupState.scheduleEnabled) backupState.frequency.label else "Off",
    checked = backupState.scheduleEnabled,
    onCheckedChange = { viewModel.setScheduleEnabled(it) },
)
if (backupState.scheduleEnabled) {
    FrequencyRow(selected = backupState.frequency, onSelect = { viewModel.setFrequency(it) })
}
ToggleSettingsRow(
    icon = Icons.Default.AutoDelete,
    title = "Keep last $RETENTION_KEEP backups",
    subtitle = if (backupState.retentionEnabled) "Older backups are pruned" else "Off — backups never auto-prune",
    checked = backupState.retentionEnabled,
    onCheckedChange = { viewModel.setRetentionEnabled(it) },
)
```

`ToggleSettingsRow` is a tiny new variant of `SettingsRow` with a trailing `Switch`. `FrequencyRow` is three radio rows.

`folderPickerLauncher` is a `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`. Its callback calls `viewModel.onFolderPicked(uri)` which takes the persistable permission and updates `BackupSettings.treeUri`.

### `SettingsViewModel` additions

```kotlin
fun onFolderPicked(uri: Uri?) {
    if (uri == null) return
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    context.contentResolver.takePersistableUriPermission(uri, flags)
    backupSettingsRepo.setTreeUri(uri.toString())
    // release any previous persisted URI
    context.contentResolver.persistedUriPermissions
        .filter { it.uri.toString() != uri.toString() }
        .forEach { context.contentResolver.releasePersistableUriPermission(it.uri, flags) }
}

fun runBackupNow() = viewModelScope.launch {
    _backupRunning.value = true
    val result = runBackup()
    _backupRunning.value = false
    _snackbar.emit(when (result) {
        is BackupResult.Success -> "Backup saved: ${result.fileName}"
        BackupResult.NoFolder -> "Backup folder is no longer accessible — please pick one"
        is BackupResult.IoError -> "Backup failed: ${result.cause}"
    })
}

fun setScheduleEnabled(enabled: Boolean) {
    backupSettingsRepo.setScheduleEnabled(enabled)
    if (enabled) scheduler.schedule(backupSettingsRepo.settings.value.frequency)
    else scheduler.cancel()
}

fun setFrequency(freq: BackupFrequency) {
    backupSettingsRepo.setFrequency(freq)
    if (backupSettingsRepo.settings.value.scheduleEnabled) scheduler.schedule(freq)
}
```

`scheduler` (a thin `BackupScheduler` class) wraps `WorkManager.getInstance(context).enqueueUniquePeriodicWork(…)` / `cancelUniqueWork(…)`. Keeps WorkManager calls out of the VM tests.

## Testing

### Unit tests

- `BackupRetentionPrunerTest` — fake `DocumentFile` (or in-memory tree) with mixed-name files; confirm only matching pattern + keep-N are retained, sort order is timestamp-descending, individual delete failures don't kill the prune.
- `BackupFileWriterTest` — a mock `DocumentFile`/`ContentResolver` that records `createFile` / `openOutputStream` / `renameDocument` calls; assert zip entry names, body bytes, and that rename is attempted with the final name.
- `RunBackupUseCaseTest` — fakes for everything; covers:
  - No URI configured → `NoFolder`
  - URI configured but `tree.canWrite() == false` → `NoFolder`
  - Happy path → `Success(fileName)`, last-success recorded, prune called when retention enabled, NOT called when disabled
  - Writer throws `IOException` → `IoError`, error recorded
- `BackupSettingsRepositoryTest` — round-trip every field through SharedPreferences; emit on update.
- `SettingsViewModelTest` — gets new tests for `onFolderPicked` (calls `takePersistableUriPermission`), `runBackupNow` (snackbar emissions per result), `setScheduleEnabled` (calls scheduler), `setFrequency` (re-schedules only when enabled).
- `BackupManifestSerializationTest` — kotlinx.serialization round-trip.

### Manual verification checklist

1. Open Settings → Backup section visible
2. Tap "Backup folder" → SAF picker opens; pick `Documents`; subtitle updates to the folder name
3. Tap "Back up now" → snackbar `"Backup saved: spendtrack-backup-….zip"`. File visible in Documents in a file manager
4. Open the zip in a desktop archiver → contains `spendtrack.db`, `settings.json`, `manifest.json`
5. Add a transaction; tap "Back up now" again; open the new zip's `spendtrack.db` in `sqlite3` → confirm the new row exists (WAL checkpoint worked)
6. Toggle "Scheduled backup" on → frequency selector appears; pick Weekly. `WorkManager.getWorkInfosForUniqueWork(WORK_NAME)` shows ENQUEUED state
7. Toggle off → unique work is cancelled
8. Run "Back up now" 7 times → with retention on (keep 5), only 5 zips remain in the folder
9. In OS app-info, revoke the SAF permission → "Back up now" → snackbar `"Backup folder is no longer accessible…"`
10. Re-pick folder → "Back up now" succeeds again
11. Force-stop the app, reboot, reopen → tree URI still works (persistable permission retained)
12. With > 1 transaction in WAL (rapid writes), trigger backup → verify the zip contains the latest data
13. Fill device storage to ~0 free, trigger backup → snackbar `"Backup failed: …"` with a sensible error

## Out of Scope

- **Restore** — separate spec; manifest's `dbVersion` is the integration point
- **Encryption** — separate follow-up; the Settings copy will mention "backups are unencrypted" so users know
- **Settings UI for "keep N" tuning** — fixed default 5; if a user wants different retention, that's a small follow-up
- **Cross-device share/export** — out
- **CSV-format dual export** — the existing CSV import is read-only; export is a different feature
- **Notification on scheduled-backup failure** — the in-app `lastBackupError` is the only signal; a system notification could be added later behind a separate setting
- **Foreground-service promotion** for very large databases — DB is small enough today; revisit if it ever becomes an issue
