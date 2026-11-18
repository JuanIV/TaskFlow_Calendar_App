package com.example.taskflow.data.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.taskflow.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    suspend fun exportDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Vuelca el contenido pendiente del WAL al archivo principal
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
            val dbPath = context.getDatabasePath("taskflow_db")
            val output = context.contentResolver.openOutputStream(uri) ?: return@withContext false
            output.use { out ->
                FileInputStream(dbPath).use { input -> input.copyTo(out) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore_tmp.db")
        try {
            val currentVersion = database.openHelper.readableDatabase.version
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { source -> FileOutputStream(temp).use { out -> source.copyTo(out) } }

            if (temp.length() < 100) {
                temp.delete()
                return@withContext false
            }
            val header = ByteArray(100)
            FileInputStream(temp).use { it.read(header) }
            val magic = String(header, 0, 15, Charsets.US_ASCII)
            val backupVersion = ByteBuffer.wrap(header, 60, 4).int
            if (magic != "SQLite format 3" || backupVersion > currentVersion) {
                temp.delete()
                return@withContext false
            }

            database.close()
            val dbPath = context.getDatabasePath("taskflow_db")
            temp.copyTo(dbPath, overwrite = true)
            File(dbPath.path + "-wal").delete()
            File(dbPath.path + "-shm").delete()
            temp.delete()
            true
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }
}
