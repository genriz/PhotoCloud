package com.app.photocloud.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.lifecycle.*
import com.app.photocloud.data.local.AppDatabase
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.data.sync.GoogleDriveService
import com.app.photocloud.data.sync.YandexDiskService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).inspectionDao()

    val allPhotos: LiveData<List<ItemPhoto>> = dao.getAllPhotos().asLiveData()

    private val _uploadResult = MutableSharedFlow<String>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    var lastStableLocation: Location? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                if (lastStableLocation==null){
                    lastStableLocation = location
                } else {
                    if (location.accuracy < lastStableLocation!!.accuracy){
                        lastStableLocation = location
                    }
                }
            }
        }
    }

    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        fusedLocationClient.lastLocation.addOnCompleteListener { task ->  lastStableLocation = task.result }
    }

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

//    fun getPhotoByPath(filePath: String): LiveData<ItemPhoto?> {
//        return dao.getPhotoByPath(filePath).asLiveData()
//    }

    fun deletePhoto(filePath: String) {
        viewModelScope.launch {
            dao.deletePhotoByPath(filePath)
        }
    }
}
