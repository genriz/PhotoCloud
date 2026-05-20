package com.app.photocloud.ui.fragments

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.app.photocloud.R
import com.app.photocloud.databinding.FragmentCameraBinding
import com.app.photocloud.ui.viewmodels.MainViewModel
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var currentResolution = RESOLUTION_1080P
    
    private val viewModel: MainViewModel by activityViewModels()

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastStableLocation: Location? = null
    private val locationHistory = mutableListOf<Location>()
    private val MAX_LOCATION_HISTORY = 5

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                updateStableLocation(location)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        startCamera()
        startLocationUpdates()

        binding.btnBack.setOnClickListener {
            binding.btnBack.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(100)
                .withEndAction {
                    binding.btnBack.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            if (isAdded) findNavController().popBackStack()
                        }
                }
        }
        binding.btnCapture.setOnClickListener { takePhoto() }
        
        binding.toggleResolution.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentResolution = if (checkedId == R.id.btn_720p) {
                    RESOLUTION_720P
                } else {
                    RESOLUTION_1080P
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    startCamera()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun updateStableLocation(newLocation: Location) {
        locationHistory.add(newLocation)
        if (locationHistory.size > MAX_LOCATION_HISTORY) {
            locationHistory.removeAt(0)
        }

        var bestLocation = locationHistory[0]
        for (loc in locationHistory) {
            if (loc.accuracy < bestLocation.accuracy) {
                bestLocation = loc
            }
        }
        lastStableLocation = bestLocation
    }

    private fun startCamera() {
        if (_binding == null) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
                return@addListener
            }

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        currentResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())
        
        val photoFile = File(
            requireContext().getExternalFilesDir(null),
            "${name}_raw.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isAdded) {
                        lifecycleScope.launch {
                            processAndSaveImage(photoFile, name)
                        }
                    }
                }
            }
        )
    }

    private suspend fun processAndSaveImage(rawFile: File, baseName: String) {
        withContext(Dispatchers.IO) {
            val exifRaw = try { ExifInterface(rawFile.absolutePath) } catch (e: Exception) {
                Log.v("DASD", e.localizedMessage?:"exception null")
                null
            }
            val orientation = exifRaw?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val originalBitmap = BitmapFactory.decodeFile(rawFile.absolutePath) ?: return@withContext
            
            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
                else -> originalBitmap
            }

            var quality = 100
            var compressedData: ByteArray
            
            val targetMaxSize = 700 * 1024
            
            var stream = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            compressedData = stream.toByteArray()

            if (compressedData.size > targetMaxSize) {
                while (compressedData.size > targetMaxSize && quality > 10) {
                    quality -= 5
                    stream = ByteArrayOutputStream()
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    compressedData = stream.toByteArray()
                }
            }

            val processedFile = File(
                rawFile.parent,
                "$baseName.jpg"
            )
            
            FileOutputStream(processedFile).use { it.write(compressedData) }
            rawFile.delete()

            if (rotatedBitmap != originalBitmap) {
                rotatedBitmap.recycle()
            }
            originalBitmap.recycle()

            injectExif(processedFile)
            
            saveToGallery(processedFile, baseName)
            
            val displayDate = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())
            viewModel.savePhoto(processedFile.absolutePath, displayDate)

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    val bundle = Bundle().apply {
                        putString("photoPath", processedFile.absolutePath)
                    }
                    findNavController().navigate(R.id.action_cameraFragment_to_photoDetailsFragment, bundle)
                }
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun saveToGallery(file: File, name: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoCloud")
            }
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    private fun injectExif(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            
            val prefs = requireContext().getSharedPreferences("coords_prefs", Context.MODE_PRIVATE)
            val isManualEnabled = prefs.getBoolean("is_manual_enabled", false)
            val manualCoords = prefs.getString("manual_coords", null)

            if (isManualEnabled && manualCoords != null) {
                val parts = manualCoords.split(",")
                if (parts.size == 2) {
                    val lat = parts[0].trim().toDoubleOrNull()
                    val lon = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lon != null) {
                        exif.setLatLong(lat, lon)
                        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "MANUAL")
                    }
                }
            } else {
                lastStableLocation?.let { location ->
                    exif.setLatLong(location.latitude, location.longitude)
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, location.altitude.toString())
                    exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, location.provider)
                }
            }
            
            val timeStamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
            exif.setAttribute(ExifInterface.TAG_DATETIME, timeStamp)
            
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject EXIF", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
        private val RESOLUTION_720P = Size(1280, 720)
        private val RESOLUTION_1080P = Size(1920, 1080)
    }
}
