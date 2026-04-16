package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Factory for creating appropriate MarkdownFileOperations implementation
 * based on current storage mode (SAF vs default).
 */
object MarkdownFileFactory {

    /**
     * Create the appropriate MarkdownFileOperations implementation.
     *
     * @param context Application context
     * @param useSAF Whether to use SAF (Storage Access Framework) mode
     * @param fileUri SAF URI (required if useSAF is true)
     * @param defaultFile Default file (used if useSAF is false)
     * @return Appropriate MarkdownFileOperations implementation
     */
    fun create(
        context: Context,
        useSAF: Boolean,
        fileUri: Uri?,
        defaultFile: File
    ): MarkdownFileOperations {
        return if (useSAF && fileUri != null) {
            SafMarkdownFile(context, fileUri)
        } else {
            FileBasedMarkdownFile(context, defaultFile)
        }
    }
}
