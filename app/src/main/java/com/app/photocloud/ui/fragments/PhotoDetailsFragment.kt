package com.app.photocloud.ui.fragments

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.SharedElementCallback
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionInflater
import androidx.viewpager2.widget.ViewPager2
import com.app.photocloud.R
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.databinding.FragmentPhotoDetailsBinding
import com.app.photocloud.ui.adapters.PhotoPagerAdapter
import com.app.photocloud.ui.viewmodels.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoDetailsFragment : Fragment() {

    private var _binding: FragmentPhotoDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var pagerAdapter: PhotoPagerAdapter
    private var currentPhoto: ItemPhoto? = null
    private var initialPhotoPath: String? = null
    private var isInitialPositionSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val moveTransition = TransitionInflater.from(requireContext())
            .inflateTransition(android.R.transition.move)
            ?.setDuration(220)

        sharedElementEnterTransition = moveTransition
        sharedElementReturnTransition = moveTransition

        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val recyclerView = binding.vpPhotoPager.getChildAt(0) as? RecyclerView
                val holder = recyclerView?.findViewHolderForAdapterPosition(binding.vpPhotoPager.currentItem)
                holder?.itemView?.findViewById<View>(R.id.iv_pager_photo)?.let { view ->
                    val name = names.getOrNull(0)
                    if (name != null) {
                        sharedElements.clear()
                        sharedElements[name] = view
                    }
                }
            }
        })
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhoto?.let { finalizeDeletion(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })

        initialPhotoPath = arguments?.getString("photoPath")
        val path = initialPhotoPath ?: return
        
        setupViewPager(path)
        setupButtons()
    }

    private fun setupViewPager(initialPath: String) {
        pagerAdapter = PhotoPagerAdapter { loadedPath ->
            if (loadedPath == (currentPhoto?.filePath ?: initialPhotoPath)) {
                startPostponedEnterTransition()
            }
        }
        binding.vpPhotoPager.adapter = pagerAdapter
        binding.vpPhotoPager.offscreenPageLimit = 1

        binding.vpPhotoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (pagerAdapter.currentList.isNotEmpty()) {
                    val photo = pagerAdapter.currentList[position]
                    currentPhoto = photo
                    updateUI(photo)
                }
            }
        })

        viewModel.allPhotos.observe(viewLifecycleOwner) { photos ->
            if (photos.isNullOrEmpty()) {
                if (isInitialPositionSet && isAdded) {
                    findNavController().popBackStack()
                }
                return@observe
            }
            
            pagerAdapter.submitList(photos) {
                if (_binding == null) return@submitList

                if (!isInitialPositionSet) {
                    val targetIndex = photos.indexOfFirst { it.filePath == initialPath }
                    if (targetIndex != -1) {
                        binding.vpPhotoPager.post {
                            if (_binding != null) {
                                binding.vpPhotoPager.setCurrentItem(targetIndex, false)
                                currentPhoto = photos[targetIndex]
                                updateUI(photos[targetIndex])
                                isInitialPositionSet = true
                            }
                        }
                    }
                } else {
                    val currentPos = binding.vpPhotoPager.currentItem
                    val photoAtPos = if (currentPos in photos.indices) photos[currentPos] else null
                    if (photoAtPos != null) {
                        currentPhoto = photoAtPos
                        updateUI(photoAtPos)
                    }
                }
            }
        }
    }

    private fun updateUI(photo: ItemPhoto) {
        displayMetadata(photo)
        updateStatusUI(photo)
    }

    private fun updateStatusUI(photo: ItemPhoto) {
        val (statusRes, colorRes) = when (photo.uploadStatus) {
            ItemPhoto.STATUS_SAVED_LOCALLY -> R.string.status_saved_locally to R.color.primary
            ItemPhoto.STATUS_UPLOADING -> R.string.status_uploading to R.color.secondary
            ItemPhoto.STATUS_UPLOADED -> R.string.status_uploaded to android.R.color.holo_green_dark
            ItemPhoto.STATUS_UPLOAD_ERROR -> R.string.status_upload_error to R.color.error
            else -> R.string.status_unknown to R.color.outline
        }
        binding.tvDetailStatus.text = getString(R.string.format_status, getString(statusRes))
        binding.tvDetailStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        val isUploading = photo.uploadStatus == ItemPhoto.STATUS_UPLOADING
        binding.btnUploadDrive.isEnabled = !isUploading
        binding.btnUploadYandex.isEnabled = !isUploading
        binding.progressUpload.visibility = if (isUploading) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {
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
                            navigateBack()
                        }
                }
        }

        binding.btnUploadDrive.setOnClickListener {
            val photo = currentPhoto ?: return@setOnClickListener
            val prefs = requireContext().getSharedPreferences("google_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("google_email", null)
            if (email != null) {
                uploadToDrive(File(photo.filePath), email)
            } else {
                Toast.makeText(requireContext(), R.string.msg_select_account, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUploadYandex.setOnClickListener {
            val photo = currentPhoto ?: return@setOnClickListener
            val prefs = requireContext().getSharedPreferences("yandex_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("yandex_token", null)
            if (token != null) {
                uploadToYandexDisk(File(photo.filePath), token)
            } else {
                Toast.makeText(requireContext(),
                    getString(R.string.please_sign_in_to_yandex_on_dashboard), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeletePhoto.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.tvDetailCoordinatesUpdate.setOnClickListener {
            updateLocation()
        }
    }

    private fun updateLocation() {
        viewModel.lastStableLocation?.let{ location ->
            val exif = ExifInterface(currentPhoto!!.filePath)
            exif.setLatLong(location.latitude, location.longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, location.altitude.toString())
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, location.provider)
            exif.saveAttributes()
            val latLong = exif.latLong
            if (latLong != null) {
                val formattedCoordinates = String.format(Locale.US, "%.4f, %.4f", latLong[0], latLong[1])
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, formattedCoordinates)
            } else {
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, "Not available")
            }
            //viewModel.savePhoto(currentPhoto!!.filePath, currentPhoto!!.captureDate)
        }
    }

    private fun navigateBack() {
        currentPhoto?.let { photo ->
            val result = Bundle().apply {
                putString("returnedPath", photo.filePath)
            }
            parentFragmentManager.setFragmentResult("photo_details_result", result)
        }
        if (isAdded) findNavController().popBackStack()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.btn_delete_photo) { _, _ ->
                performDeletion()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun performDeletion() {
        val photo = currentPhoto ?: return
        val file = File(photo.filePath)
        lifecycleScope.launch {
            val deletedFromMediaStore = withContext(Dispatchers.IO) {
                deleteFromMediaStore(file.name)
            }

            if (deletedFromMediaStore) {
                finalizeDeletion(photo)
            }
        }
    }

    private fun deleteFromMediaStore(fileName: String): Boolean {
        val resolver = requireContext().contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        val namesToTry = listOf(fileName, fileName.replace(":", "_"))
        
        var id: Long? = null
        for (name in namesToTry) {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(name)
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                }
            }
            if (id != null) break
        }

        if (id != null) {
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            return try {
                resolver.delete(contentUri, null, null) > 0
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                    val intentSender = securityException.userAction.actionIntent.intentSender
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    false
                } else {
                    throw securityException
                }
            }
        }
        return true
    }

    private fun finalizeDeletion(photo: ItemPhoto) {
        val file = File(photo.filePath)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (file.exists()) {
                    file.delete()
                }
            }
            viewModel.deletePhoto(photo.filePath)
            Toast.makeText(requireContext(), R.string.delete_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadToDrive(file: File, accountEmail: String) {
        viewModel.uploadPhoto(file, accountEmail)
        Toast.makeText(requireContext(), R.string.msg_upload_started, Toast.LENGTH_SHORT).show()
    }

    private fun uploadToYandexDisk(file: File, token: String) {
        viewModel.uploadPhotoToYandex(file, token)
        Toast.makeText(requireContext(), R.string.msg_upload_started, Toast.LENGTH_SHORT).show()
    }

    private fun displayMetadata(photo: ItemPhoto) {
        val file = File(photo.filePath)
        if (!file.exists()) {
            binding.tvDetailSize.text = getString(R.string.file_not_found)
            return
        }
        val sizeInKb = file.length() / 1024.0
        val df = DecimalFormat("#.##")
        binding.tvDetailSize.text = getString(R.string.format_size, df.format(sizeInKb))

        try {
            val exif = ExifInterface(file.absolutePath)
            val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            binding.tvDetailResolution.text = getString(R.string.format_resolution, "${width}x${height}")

            val exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val displayDate = if (exifDate != null) {
                try {
                    val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    val formatter = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
                    parser.parse(exifDate)?.let { formatter.format(it) } ?: exifDate
                } catch (e: Exception) {
                    Log.v("DASD", e.localizedMessage?:"exception null")
                    exifDate
                }
            } else {
                photo.captureDate
            }
            binding.tvDetailDatetime.text = getString(R.string.format_datetime, displayDate)

            val latLong = exif.latLong
            if (latLong != null) {
                val formattedCoordinates = String.format(Locale.US, "%.4f, %.4f", latLong[0], latLong[1])
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, formattedCoordinates)
            } else {
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, "Not available")
            }
        } catch (e: Exception) {
            Log.v("DASD", e.localizedMessage?:"exception null")
            binding.tvDetailResolution.text = getString(R.string.metadata_error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
