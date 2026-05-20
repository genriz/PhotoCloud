package com.app.photocloud.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class YandexDiskService(private val oauthToken: String) {

    private val client = OkHttpClient()

    suspend fun uploadPhoto(file: File, folderName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = if (folderName != null) {
                ensureFolderExists(folderName)
                "app:/$folderName/${file.name}"
            } else {
                "app:/${file.name}"
            }

            val uploadUrlRequest = Request.Builder()
                .url("https://cloud-api.yandex.net/v1/disk/resources/upload?path=$path&overwrite=true")
                .addHeader("Authorization", "OAuth $oauthToken")
                .build()

            val uploadUrl = client.newCall(uploadUrlRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: return@withContext false
                JSONObject(body).getString("href")
            }

            val mediaType = "image/jpeg".toMediaType()
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .put(file.asRequestBody(mediaType))
                .build()

            client.newCall(uploadRequest).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun ensureFolderExists(folderName: String) = withContext(Dispatchers.IO) {
        val createFolderRequest = Request.Builder()
            .url("https://cloud-api.yandex.net/v1/disk/resources?path=app:/$folderName")
            .put("".toRequestBody(null))
            .addHeader("Authorization", "OAuth $oauthToken")
            .build()

        client.newCall(createFolderRequest).execute().use { response ->
            response.isSuccessful || response.code == 409
        }
    }
}
