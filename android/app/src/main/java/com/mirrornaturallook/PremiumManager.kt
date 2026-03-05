package com.mirrornaturallook

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PremiumManager(context: Context) {

    companion object {
        const val PREFS_FILE = "mirror_premium_prefs"
        const val KEY_INSTALL_DATE = "install_date_ms"
        const val KEY_IS_PREMIUM = "is_premium"
        const val KEY_PURCHASE_TOKEN = "purchase_token"
        const val TRIAL_DAYS = 30
        const val PRODUCT_ID_LIFETIME = "mirror_lifetime_premium"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, PREFS_FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    init {
        if (!prefs.contains(KEY_INSTALL_DATE))
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
    }

    val isPremium: Boolean get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
    val installDateMs: Long get() = prefs.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())
    val daysSinceInstall: Int get() = ((System.currentTimeMillis() - installDateMs) / 86_400_000).toInt()
    val trialDaysLeft: Int get() = maxOf(0, TRIAL_DAYS - daysSinceInstall)
    val isTrialExpired: Boolean get() = daysSinceInstall >= TRIAL_DAYS
    val needsPaywall: Boolean get() = !isPremium && isTrialExpired
    val showAds: Boolean get() = !isPremium

    fun activatePremium(purchaseToken: String) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, true).putString(KEY_PURCHASE_TOKEN, purchaseToken).apply()
    }
}
