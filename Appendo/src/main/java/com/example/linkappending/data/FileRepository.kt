package com.example.linkappending.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile

class FileRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("link_appending", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FILE_URI = "file_uri"
        private const val KEY_FILE_LAST_MODIFIED = "file_last_modified"
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
        val uriString = prefs.getString(KEY_FILE_URI, null) ?: return null
        return try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save the file URI and take persistable permission
     */
    fun saveFileUri(uri: Uri) {
        prefs.edit().putString(KEY_FILE_URI, uri.toString()).apply()

        // Take persistable URI permission
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    /**
     * Clear the saved file URI and release permission
     */
    fun clearFileUri() {
        val uri = getFileUri()
        if (uri != null) {
            try {
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (e: Exception) {
                // Permission may not be held, ignore
            }
        }
        prefs.edit().remove(KEY_FILE_URI).apply()
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

    fun getFileLastModified(): Long = prefs.getLong(KEY_FILE_LAST_MODIFIED, 0L)

    fun setFileLastModified(timestamp: Long) {
        prefs.edit().putLong(KEY_FILE_LAST_MODIFIED, timestamp).apply()
    }

    fun clearFileLastModified() {
        prefs.edit().remove(KEY_FILE_LAST_MODIFIED).apply()
    }
}
