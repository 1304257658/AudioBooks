package com.example.audiobooks.storage

import android.content.Context
import java.io.File

class LocalProjectPaths(context: Context) {
    val root: File = File(context.filesDir, "audiobooks-data")
    val books: File = File(root, "books")
    val models: File = File(root, "models")
    val downloads: File = File(root, "downloads")
    val libraryIndex: File = File(root, "library.json")

    fun ensure() {
        books.mkdirs()
        models.mkdirs()
        downloads.mkdirs()
    }
}

