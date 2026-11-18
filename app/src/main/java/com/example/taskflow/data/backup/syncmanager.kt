package com.example.taskflow.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Sincronizacion manual: sube o baja una copia completa de la base de datos a una carpeta elegida por el usuario.
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) {
    companion object {
        const val FILE_NAME = "taskflow_sync.db"
    }

    private fun folderDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun findFile(tree: Uri): Uri? {
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }

    suspend fun upload(folder: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tree = Uri.parse(folder)
            val resolver = context.contentResolver
            val existing = findFile(tree)
            if (existing != null) DocumentsContract.deleteDocument(resolver, existing)
            val created = DocumentsContract.createDocument(
                resolver, folderDocumentUri(tree), "application/octet-stream", FILE_NAME
            ) ?: return@withContext false
            backupManager.exportDatabase(created)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun download(folder: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = findFile(Uri.parse(folder)) ?: return@withContext false
            backupManager.importDatabase(file)
        } catch (e: Exception) {
            false
        }
    }
}
