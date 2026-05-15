package com.atlasfpt.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.atlasfpt.data.db.AppDatabase
import com.atlasfpt.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val filename: String, val at: Long) : BackupResult
    data class Error(val message: String) : BackupResult
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun runBackup(): BackupResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.value
        val treeUriString = settings.backupFolderUri
            ?: return@withContext BackupResult.Error("Pick a folder in Settings → Backup first")
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull()
            ?: return@withContext BackupResult.Error("Saved folder URI is invalid")
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext BackupResult.Error("Couldn't open the backup folder")
        if (!rootDoc.canWrite()) {
            return@withContext BackupResult.Error("Folder permission revoked — re-pick the folder")
        }

        runCatching { checkpointWal() }.onFailure {
            Log.w("BackupRepository", "WAL checkpoint failed (continuing)", it)
        }

        val createdAt = System.currentTimeMillis()
        val timestamp = TIMESTAMP_FORMAT.format(Date(createdAt))
        val finalName = "atlas-backup-$timestamp.zip"
        val tmpName = "$finalName.tmp"

        val tmpDoc = rootDoc.createFile(MIME_ZIP, tmpName)
            ?: return@withContext BackupResult.Error("Couldn't create the backup file")

        runCatching {
            context.contentResolver.openOutputStream(tmpDoc.uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    val dbFile = context.getDatabasePath(DB_FILE_NAME)
                    if (!dbFile.exists()) {
                        throw IllegalStateException("Database file not found")
                    }
                    zip.copyFile(dbFile, DB_FILE_NAME)
                    File(dbFile.parentFile, "$DB_FILE_NAME-wal").takeIf { it.exists() }?.let {
                        zip.copyFile(it, "$DB_FILE_NAME-wal")
                    }
                    File(dbFile.parentFile, "$DB_FILE_NAME-shm").takeIf { it.exists() }?.let {
                        zip.copyFile(it, "$DB_FILE_NAME-shm")
                    }
                    val manifest = buildManifest(createdAt)
                    zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                    zip.write(manifest.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: throw IllegalStateException("Couldn't open output stream")
        }.onFailure { t ->
            runCatching { tmpDoc.delete() }
            return@withContext BackupResult.Error(t.message ?: t.javaClass.simpleName)
        }

        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, tmpDoc.uri, finalName)
        }.getOrNull()
        if (renamed == null) {
            runCatching { tmpDoc.delete() }
            return@withContext BackupResult.Error("Couldn't finalise the backup filename")
        }

        settingsRepository.setLastBackup(createdAt, finalName)
        runCatching { applyRetention(rootDoc, finalName, settings.backupRetentionCount) }
            .onFailure { Log.w("BackupRepository", "Retention pruning failed", it) }
        BackupResult.Success(finalName, createdAt)
    }

    private fun checkpointWal() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).use {
            it.moveToFirst()
        }
    }

    private fun buildManifest(createdAt: Long): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val dbVersion = db.openHelper.readableDatabase.version
        return """
            {
              "appVersion": "$versionName",
              "dbVersion": $dbVersion,
              "createdAt": $createdAt,
              "schemaHash": null
            }
        """.trimIndent()
    }

    private fun applyRetention(root: DocumentFile, justKept: String, retention: Int) {
        if (retention <= 0) return
        val candidates = root.listFiles()
            .filter { it.name?.matches(BACKUP_FILENAME_REGEX) == true && it.name != justKept }
            .sortedByDescending { it.name }
        val toDelete = candidates.drop(retention - 1)
        toDelete.forEach { runCatching { it.delete() } }
    }

    private fun ZipOutputStream.copyFile(file: File, entryName: String) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private companion object {
        const val DB_FILE_NAME = "spendtrack.db" // historical name, kept since rename in #14 would require migration
        const val MANIFEST_NAME = "manifest.json"
        const val MIME_ZIP = "application/zip"
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        val BACKUP_FILENAME_REGEX = Regex("""atlas-backup-\d{8}-\d{6}\.zip""")
    }
}
