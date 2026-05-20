package com.app.photocloud.data.local

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

class SubscriptionManager(context: Context) {
    private val prefs = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)

    fun saveSubscription(expiryDateMillis: Long) {
        prefs.edit { putLong("expiry_date", expiryDateMillis) }
    }

    fun getExpiryDateMillis(): Long {
        return prefs.getLong("expiry_date", 0)
    }

    fun isSubscriptionActive(): Boolean {
        return getExpiryDateMillis() > System.currentTimeMillis()
    }

    fun getFormattedExpiryDate(): String {
        val expiry = getExpiryDateMillis()
        if (expiry == 0L) return ""
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(expiry))
    }
}
