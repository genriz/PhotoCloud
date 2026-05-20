package com.app.photocloud.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.app.photocloud.data.model.ItemPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Insert
    suspend fun insertPhoto(photo: ItemPhoto)

    @Query("DELETE FROM inspection_photos WHERE filePath = :filePath")
    suspend fun deletePhotoByPath(filePath: String)

    @Query("UPDATE inspection_photos SET uploadStatus = :status WHERE filePath = :filePath")
    suspend fun updatePhotoStatus(filePath: String, status: String)

    @Query("SELECT * FROM inspection_photos WHERE filePath = :filePath LIMIT 1")
    fun getPhotoByPath(filePath: String): Flow<ItemPhoto?>

    @Query("SELECT * FROM inspection_photos WHERE filePath = :filePath LIMIT 1")
    suspend fun getPhotoByPathSync(filePath: String): ItemPhoto?

    @Query("SELECT * FROM inspection_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<ItemPhoto>>
}
