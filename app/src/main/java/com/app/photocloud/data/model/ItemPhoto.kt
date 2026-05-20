package com.app.photocloud.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_photos",
    indices = [Index(value = ["id"])]
)
data class ItemPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val uploadStatus: String = "SAVED_LOCALLY",
    val captureDate: String = ""
) {
    companion object {
        const val STATUS_SAVED_LOCALLY = "SAVED_LOCALLY"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_UPLOAD_ERROR = "UPLOAD_ERROR"
    }
}
