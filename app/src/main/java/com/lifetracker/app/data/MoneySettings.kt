package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Money settings kept on the phone. Only the currency symbol for now. */
class MoneySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("money", Context.MODE_PRIVATE)

    var currency: String
        get() = prefs.getString("currency", DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        set(value) = prefs.edit { putString("currency", value.trim().take(4).ifEmpty { DEFAULT_CURRENCY }) }

    companion object {
        const val DEFAULT_CURRENCY = "₹"
    }
}
