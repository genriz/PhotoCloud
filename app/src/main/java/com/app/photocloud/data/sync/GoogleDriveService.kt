package com.app.photocloud.data.sync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import java.text.SimpleDateFormat
import java.util.*

class GoogleDriveService(context: Context, accountName: String) {

    private val driveService: Drive

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccountName = accountName

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("PhotoCloud").build()
    }

    suspend fun uploadPhoto(javaFile: JavaFile): String? = withContext(Dispatchers.IO) {
        try {
            val folderName = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())
            val folderId = getOrCreateFolder(folderName)

            val existingFileId = findFileInFolder(javaFile.name, folderId)
            val mediaContent = FileContent("image/jpeg", javaFile)

            if (existingFileId != null) {
                val driveFile = driveService.files().update(existingFileId, null, mediaContent)
                    .setFields("id")
                    .execute()
                driveFile.id
            } else {
                val fileMetadata = File().apply {
                    name = javaFile.name
                    parents = listOf(folderId)
                }
                val driveFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                driveFile.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findFileInFolder(fileName: String, folderId: String): String? {
        val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        return result.files.firstOrNull()?.id
    }

    private fun getOrCreateFolder(folderName: String): String {
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val folder = result.files.firstOrNull()
        if (folder != null) {
            return folder.id
        }

        val folderMetadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }

        val newFolder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        return newFolder.id
    }
}
