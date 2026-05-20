package com.app.photocloud.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.app.photocloud.data.local.SubscriptionManager
import com.app.photocloud.data.sync.RobokassaService
import com.app.photocloud.databinding.FragmentPaymentBinding
import java.util.Calendar

class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val paymentUrl = arguments?.getString("paymentUrl") ?: return

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith(RobokassaService.SUCCESS_URL)) {
                    handlePaymentSuccess()
                    return true
                } else if (url.startsWith(RobokassaService.FAIL_URL)) {
                    handlePaymentFail()
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
            }
        }

        binding.webView.loadUrl(paymentUrl)
    }

    private fun handlePaymentSuccess() {
        val subscriptionManager = SubscriptionManager(requireContext())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, 1)
        subscriptionManager.saveSubscription(calendar.timeInMillis)
        findNavController().popBackStack()
    }

    private fun handlePaymentFail() {
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
