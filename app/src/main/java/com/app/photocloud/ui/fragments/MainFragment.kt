package com.app.photocloud.ui.fragments

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.app.photocloud.R
import com.app.photocloud.databinding.DialogManualCoordinatesBinding
import com.app.photocloud.databinding.FragmentMainBinding
import com.app.photocloud.data.local.SubscriptionManager
import com.app.photocloud.data.sync.RobokassaService
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.internal.strategy.LoginType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.content.edit

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var yandexAuthSdk: YandexAuthSdk

    private val yandexLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val yandexResult = yandexAuthSdk.contract.parseResult(result.resultCode, result.data)
            if (yandexResult is YandexAuthResult.Success) {
                val yandexToken = yandexResult.token
                saveYandexToken(yandexToken.value)
                fetchYandexEmail(yandexToken.value)
            } else {
                Toast.makeText(requireContext(), "Yandex sign-in failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchYandexEmail(token: String) {
        lifecycleScope.launch {
            try {
                val email = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://login.yandex.ru/info?format=json")
                        .addHeader("Authorization", "OAuth $token")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        val body = response.body?.string() ?: return@withContext null
                        val json = JSONObject(body)
                        if (json.has("default_email")) json.getString("default_email") else null
                    }
                }

                if (email != null) {
                    saveYandexEmail(email)
                    binding.tvYandexAccount.text = email
                } else {
                    binding.tvYandexAccount.text = getString(R.string.status_synced)
                }
            } catch (e: Exception) {
                Log.v("DASD", e.localizedMessage?:"exception null")
                binding.tvYandexAccount.text = getString(R.string.status_synced)
            }
        }
    }

    private fun saveYandexEmail(email: String) {
        val prefs = requireContext().getSharedPreferences("yandex_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("yandex_email", email) }
    }

    private fun saveYandexToken(token: String) {
        val prefs = requireContext().getSharedPreferences("yandex_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("yandex_token", token) }
    }

    private val googleAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authResult = Identity.getAuthorizationClient(requireActivity())
                    .getAuthorizationResultFromIntent(result.data)
                authResult.accessToken?.let { fetchGoogleEmail(it) }
            } catch (e: Exception) {
                Log.v("DASD", e.localizedMessage?:"exception null")
                Toast.makeText(requireContext(), R.string.msg_sign_in_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchGoogleEmail(accessToken: String) {
        lifecycleScope.launch {
            try {
                val email = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://www.googleapis.com/oauth2/v3/userinfo")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        val body = response.body?.string() ?: return@withContext null
                        val json = JSONObject(body)
                        if (json.has("email")) json.getString("email") else null
                    }
                }

                if (email != null) {
                    saveGoogleEmail(email)
                    binding.tvGoogleAccount.text = email
                }
            } catch (e: Exception) {
                Log.v("DASD", e.localizedMessage?:"exception null")
            }
        }
    }

    private fun saveGoogleEmail(email: String) {
        val prefs = requireContext().getSharedPreferences("google_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("google_email", email) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            findNavController().navigate(R.id.action_dashboardFragment_to_cameraFragment)
        } else {
            Toast.makeText(requireContext(), R.string.msg_permissions_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yandexAuthSdk = YandexAuthSdk.create(YandexAuthOptions(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleDrive()
        setupYandexDisk()
        setupCoordinates()
        setupGallery()
        setupSubscription()
        setupTakePhoto()
    }

    private fun setupSubscription() {
        val subscriptionManager = SubscriptionManager(requireContext())

        binding.cardSubscription.setOnClickListener {
            binding.cardSubscription.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.cardSubscription.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            if (subscriptionManager.isSubscriptionActive()) {
                                showSubscriptionDialog(subscriptionManager)
                            } else {
                                startPaymentProcess()
                            }
                        }
                }
        }

        if (subscriptionManager.isSubscriptionActive()) {
            val date = subscriptionManager.getFormattedExpiryDate()
            binding.tvSubscriptionStatus.text = getString(R.string.format_subscription_valid_until, date)
            binding.tvSubscriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        } else {
            binding.tvSubscriptionStatus.text = getString(R.string.label_no_subscription)
            binding.tvSubscriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
        }
    }

    private fun showSubscriptionDialog(subscriptionManager: SubscriptionManager) {
        val date = subscriptionManager.getFormattedExpiryDate()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.label_subscription)
            .setMessage(getString(R.string.msg_subscription_active, date))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startPaymentProcess() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.label_subscription)
            .setMessage(R.string.label_no_subscription)
            .setPositiveButton(R.string.btn_pay) { _, _ ->
                val invId = (System.currentTimeMillis() % 1000000).toInt()
                val paymentUrl = RobokassaService.generatePaymentUrl(
                    invId = invId,
                    outSum = "300.00",
                    description = "Subscription for 1 month"
                )
                val bundle = Bundle().apply {
                    putString("paymentUrl", paymentUrl)
                }
                findNavController().navigate(R.id.action_dashboardFragment_to_paymentFragment, bundle)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun setupGoogleDrive() {
        val prefs = requireContext().getSharedPreferences("google_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("google_email", null)
        if (savedEmail != null) {
            binding.tvGoogleAccount.text = savedEmail
        }

        val credentialManager = CredentialManager.create(requireContext())

        binding.cardGoogleDrive.setOnClickListener {
            binding.cardGoogleDrive.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.cardGoogleDrive.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            lifecycleScope.launch {
                                try {
                                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                } catch (e: Exception) {
                                    Log.e("MainFragment", "Failed to clear credential state", e)
                                }

                                val requestedScopes = listOf(
                                    Scope("https://www.googleapis.com/auth/drive.file"),
                                    Scope("https://www.googleapis.com/auth/userinfo.email")
                                )
                                val request = AuthorizationRequest.builder()
                                    .setRequestedScopes(requestedScopes)
                                    .build()

                                Identity.getAuthorizationClient(requireActivity())
                                    .authorize(request)
                                    .addOnSuccessListener { result ->
                                        if (result.hasResolution()) {
                                            val intentSender = result.pendingIntent?.intentSender
                                            googleAuthLauncher.launch(IntentSenderRequest.Builder(intentSender!!).build())
                                        } else {
                                            result.accessToken?.let { fetchGoogleEmail(it) }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(requireContext(), "Auth error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                }
        }
    }

    private fun setupYandexDisk() {
        val prefs = requireContext().getSharedPreferences("yandex_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("yandex_token", null)
        val email = prefs.getString("yandex_email", null)
        if (token != null) {
            binding.tvYandexAccount.text = email ?: getString(R.string.status_synced)
        }

        binding.cardYandexDisk.setOnClickListener {
            binding.cardYandexDisk.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.cardYandexDisk.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            val loginOptions = YandexAuthLoginOptions(LoginType.NATIVE)
                            val intent = yandexAuthSdk.contract.createIntent(requireContext(), loginOptions)
                            yandexLoginLauncher.launch(intent)
                        }
                }
        }
    }

    private fun setupCoordinates() {
        val prefs = requireContext().getSharedPreferences("coords_prefs", Context.MODE_PRIVATE)
        val savedCoords = prefs.getString("manual_coords", null)
        val isManualEnabled = prefs.getBoolean("is_manual_enabled", false)

        binding.switchManualCoords.isChecked = isManualEnabled
        if (savedCoords != null) {
            binding.tvCurrentCoords.text = getString(R.string.format_coordinates, savedCoords)
            binding.tvCurrentCoords.visibility = if (isManualEnabled) View.VISIBLE else View.GONE
        }

        binding.switchManualCoords.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("is_manual_enabled", isChecked) }
            if (isChecked && prefs.getString("manual_coords", null) == null) {
                showCoordinateDialog()
            } else {
                binding.tvCurrentCoords.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCoordinateDialog() {
        val dialogBinding = DialogManualCoordinatesBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.label_manual_coords)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val coords = dialogBinding.etCoordinates.text.toString()
                if (validateCoordinates(coords)) {
                    val prefs = requireContext().getSharedPreferences("coords_prefs", Context.MODE_PRIVATE)
                    prefs.edit { putString("manual_coords", coords) }
                    binding.tvCurrentCoords.text = getString(R.string.format_coordinates, coords)
                    binding.tvCurrentCoords.visibility = View.VISIBLE
                } else {
                    Toast.makeText(requireContext(), R.string.msg_invalid_format, Toast.LENGTH_SHORT).show()
                    binding.switchManualCoords.isChecked = false
                }
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                binding.switchManualCoords.isChecked = false
            }
            .show()
    }

    private fun validateCoordinates(input: String): Boolean {
        val regex = Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$")
        return regex.matches(input)
    }

    private fun setupGallery() {
        binding.cardGallery.setOnClickListener {
            binding.cardGallery.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.cardGallery.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            findNavController().navigate(R.id.action_dashboardFragment_to_galleryFragment)
                        }
                }
        }
    }

    private fun setupTakePhoto() {
        binding.fabTakePhoto.setOnClickListener {
            binding.fabTakePhoto.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.fabTakePhoto.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            val permissions = arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
                                findNavController().navigate(R.id.action_dashboardFragment_to_cameraFragment)
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        }
                }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
