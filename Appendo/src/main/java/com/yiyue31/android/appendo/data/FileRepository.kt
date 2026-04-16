package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import java.io.File

class FileRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("link_appending", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USE_SAF = "use_saf"
        private const val KEY_FILE_URI = "file_uri"
        private const val KEY_FILE_LAST_MODIFIED = "file_last_modified"
        private const val DEFAULT_FILENAME = "Appendo.md"
    }

    /**
     * Get the default file in app's private external storage directory
     */
    fun getDefaultFile(): File {
        val appDir = context.getExternalFilesDir(null)
        if (appDir == null) {
            // Fallback to internal storage if external is not available
            val internalDir = context.filesDir
            return File(internalDir, DEFAULT_FILENAME)
        }
        return File(appDir, DEFAULT_FILENAME)
    }

    /**
     * Check if currently using SAF mode
     */
    fun isUsingSAF(): Boolean {
        return prefs.getBoolean(KEY_USE_SAF, false)
    }

    /**
     * Check if a file URI is already selected
     */
    fun hasFileUri(): Boolean {
        return prefs.contains(KEY_FILE_URI)
    }

    /**
     * Get the saved file URI
     */
    fun getFileUri(): Uri? {
        if (!isUsingSAF()) return null
        val uriString = prefs.getString(KEY_FILE_URI, null) ?: return null
        return try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save the file URI and switch to SAF mode
     */
    fun saveFileUri(uri: Uri) {
        prefs.edit()
            .putBoolean(KEY_USE_SAF, true)
            .putString(KEY_FILE_URI, uri.toString())
            .apply()

        // Take persistable URI permission
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    /**
     * Clear the saved file URI, permissions, and switch back to default mode.
     *
     * IMPORTANT: Clears preferences BEFORE releasing permissions to prevent
     * race condition where app crash between operations causes permanent URI loss.
     */
    fun clearFileUri() {
        val uri = if (isUsingSAF()) getFileUri() else null

        // First clear preferences to switch mode - this prevents race condition
        prefs.edit()
            .remove(KEY_USE_SAF)
            .remove(KEY_FILE_URI)
            .apply()

        // Then release permission (failure is acceptable, prefs already cleared)
        if (uri != null) {
            try {
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (e: Exception) {
                // Permission may not be held, ignore - prefs already cleared
            }
        }
    }

    /**
     * Check if the saved file URI is still valid
     */
    fun isFileUriValid(): Boolean {
        val uri = getFileUri() ?: return false
        return try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.exists() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate archive filename with timestamp
     */
    fun generateArchiveFilename(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "Appendo_$timestamp.md"
    }

    /**
     * Get archive file in default directory
     */
    fun getArchiveFile(): File {
        val appDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(appDir, generateArchiveFilename())
    }

    fun getFileLastModified(): Long = prefs.getLong(KEY_FILE_LAST_MODIFIED, 0L)

    fun setFileLastModified(timestamp: Long) {
        prefs.edit().putLong(KEY_FILE_LAST_MODIFIED, timestamp).apply()
    }

    fun clearFileLastModified() {
        prefs.edit().remove(KEY_FILE_LAST_MODIFIED).apply()
    }
}
