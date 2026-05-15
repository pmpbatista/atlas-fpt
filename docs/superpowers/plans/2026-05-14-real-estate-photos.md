# Real-Estate Photos (#32) Implementation Notes

**Goal:** Single cover photo per real-estate property. Photo Picker → app-private storage → Room column + migration v5→v6 → thumbnail in list / large image in detail / picker in edit.

## What landed

### `feat(#32): schema v6 with photoUri on real_estate_details`
- `real_estate_details` gains `photoUri TEXT` via `MIGRATION_5_6` (purely additive, no backfill).
- `RealEstateDetailsEntity`, `RealEstateAsset`, `AssetListItem` all gain `photoUri: String? = null` (defaulted last param keeps positional constructors safe).
- `AssetDao.observeAssetList()` LEFT JOIN pulls `r.photoUri AS photoUri` — financial assets always get null.
- Schema `6.json` exported.

### `feat(#32): photo picker + storage on real-estate edit`
- `PhotoStorage` (`@Singleton`) copies a `content://` URI from the Photo Picker into `filesDir/real_estate_photos/{uuid}.jpg` and returns the absolute path. Also `delete(path)`.
- `AddEditRealEstateViewModel` injects `PhotoStorage`; tracks `loadedPhotoUri` (the path persisted in the DB when the screen opened) separately from `_form.value.photoUri` (the path the user has chosen this session). On pick/replace/remove, in-session-only files are deleted immediately. The loaded file is preserved until save succeeds.
- `AddEditRealEstateScreen.PhotoSection` is the first form item: 180dp rounded photo + remove ✕ when set, "Add photo" outlined button when empty. Uses `PickVisualMedia.ImageOnly` via `rememberLauncherForActivityResult` — no runtime permissions.

### `feat(#32): render property photo on detail screen + list thumbnail`
- `RealEstateDetailScreen`: 200dp rounded `AsyncImage` at the top of the `LazyColumn`, only rendered when `asset.photoUri != null`.
- `AssetListRow`: 48dp rounded thumbnail when `item.photoUri != null`; falls back to the existing 24dp icon otherwise.

### `fix(#32): clean up in-session photo pick on property delete`
Per review: `confirmDelete()` was only deleting `loadedPhotoUri`. If the user picked a new photo and then deleted the property, the freshly-copied file was orphaned in `filesDir/`. The fix deletes both the loaded path and any in-session replacement.

## Lifecycle invariants

- `loadedPhotoUri` = path persisted in the DB when the screen opened (or null for new properties).
- `_form.value.photoUri` = path the user has chosen this session (or null if they removed it).
- **Pick a new photo** → copy → delete previous in-session path (if any). Don't touch `loadedPhotoUri`.
- **Remove photo** → delete in-session path (if any). Don't touch `loadedPhotoUri`.
- **Save** → on success, delete `loadedPhotoUri` if it was replaced or removed.
- **Delete property** → delete both `loadedPhotoUri` (if any) and the in-session pick (if it differs from `loadedPhotoUri`).
- **Cancel** → no save = no DB change. The in-session pick is orphaned for the session but a future open of the same property won't read it (the DB has the old `loadedPhotoUri`). This is acceptable v1; a more careful design would queue these for cleanup. Tracked as a follow-up.

## Known minor trade-offs (flagged by review, not blocking)

- **Layout shift on AssetListRow**: rows with photos are 48dp wide on the leading side; rows without are 24dp. Wrap the icon in a `Box(Modifier.size(48.dp))` if uniform spacing is desired.
- **`PhotoStorage.dir` is a `get()` property**: calls `mkdirs()` on every access. Since the class is `@Singleton`, moving it to a one-time `init` block would save the gratuitous I/O.
- **No tests for the pick/remove/save lifecycle**: the orphan bug above would have been caught by a unit test. The current `AddEditRealEstateViewModelTest` only mocks `PhotoStorage` as relaxed without asserting `delete()` is called.

## Out of scope

- Multiple photos per property (v1 = single cover photo).
- Image compression / max-dimension scaling.
- Photo on financial assets.
- Cleanup of orphaned photos from cancelled in-session picks.
- Persistence across app uninstall (per the issue, related to the local-backup track).
