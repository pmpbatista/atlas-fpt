package com.atlasfpt.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.filesDir, "real_estate_photos").apply { mkdirs() }

    /**
     * Copy [source] (a content:// URI from the Photo Picker) into app-private storage.
     * Returns the absolute file path on success, or null on failure.
     */
    suspend fun copyInto(source: Uri): String? {
        return try {
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (t: Throwable) {
            runCatching { Log.w("PhotoStorage", "copyInto($source) failed", t) }
            null
        }
    }

    /** Delete the photo at [path], if it exists. Silent on failure. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
