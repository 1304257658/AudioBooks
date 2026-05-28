package com.example.audiobooks.storage

import android.content.Context
import java.io.File

class LocalProjectPaths(context: Context) {
    private val legacyRoot: File = File(context.filesDir, "audiobooks-data")
    val root: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "audiobooks-data")
    val books: File = File(root, "books")
    val models: File = File(root, "models")
    val downloads: File = File(root, "downloads")
    val libraryIndex: File = File(root, "library.json")
    val settings: File = File(root, "settings.json")

    fun ensure() {
        if (!root.exists() && legacyRoot.exists()) {
            legacyRoot.copyRecursively(root, overwrite = true)
        }
        books.mkdirs()
        models.mkdirs()
        downloads.mkdirs()
    }
}
