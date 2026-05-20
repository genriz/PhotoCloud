package com.app.photocloud.ui.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.app.photocloud.data.local.AppDatabase
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.data.sync.GoogleDriveService
import com.app.photocloud.data.sync.YandexDiskService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).inspectionDao()

    val allPhotos: LiveData<List<ItemPhoto>> = dao.getAllPhotos().asLiveData()

    private val _uploadResult = MutableSharedFlow<String>()

    fun uploadPhoto(file: File, accountEmail: String) {
        viewModelScope.launch {
            updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOADING)
            
            val driveService = GoogleDriveService(getApplication(), accountEmail)
            val fileId = driveService.uploadPhoto(file)

            if (fileId != null) {
                updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOADED)
                _uploadResult.emit(getApplication<Application>().getString(com.app.photocloud.R.string.msg_upload_success))
            } else {
                updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOAD_ERROR)
                _uploadResult.emit(getApplication<Application>().getString(com.app.photocloud.R.string.msg_upload_failed))
            }
        }
    }

    fun uploadPhotoToYandex(file: File, oauthToken: String) {
        viewModelScope.launch {
            updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOADING)

            val photo = dao.getPhotoByPathSync(file.absolutePath)
            val folderName = if (photo != null) {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(photo.timestamp))
            } else {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            }
            
            val yandexService = YandexDiskService(oauthToken)
            val success = yandexService.uploadPhoto(file, folderName)

            if (success) {
                updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOADED)
                _uploadResult.emit(getApplication<Application>().getString(com.app.photocloud.R.string.msg_upload_success))
            } else {
                updatePhotoStatus(file.absolutePath, ItemPhoto.STATUS_UPLOAD_ERROR)
                _uploadResult.emit(getApplication<Application>().getString(com.app.photocloud.R.string.msg_upload_failed))
            }
        }
    }

    fun savePhoto(filePath: String, captureDate: String) {
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "Saving photo to DB: $filePath")
            dao.insertPhoto(ItemPhoto(
                filePath = filePath,
                captureDate = captureDate,
                uploadStatus = ItemPhoto.STATUS_SAVED_LOCALLY
            ))
        }
    }

    fun updatePhotoStatus(filePath: String, status: String) {
        viewModelScope.launch {
            dao.updatePhotoStatus(filePath, status)
        }
    }

    fun getPhotoByPath(filePath: String): LiveData<ItemPhoto?> {
        return dao.getPhotoByPath(filePath).asLiveData()
    }

    fun deletePhoto(filePath: String) {
        viewModelScope.launch {
            dao.deletePhotoByPath(filePath)
        }
    }
}
