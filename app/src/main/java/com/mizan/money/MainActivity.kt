package com.mizan.money

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mizan.money.ui.AppRoot
import com.mizan.money.ui.theme.LanguageMode
import com.mizan.money.ui.theme.LanguagePreference
import java.util.Locale

class MainActivity : ComponentActivity() {

    // Applying the chosen language at the Activity level (via attachBaseContext)
    // is what lets BOTH normal composables AND AlertDialogs pick up the right
    // locale — a localized context provided higher up the Compose tree does not
    // reach Dialog windows, and breaks the dialog's own theme lookup.
    override fun attachBaseContext(newBase: Context) {
        val mode = LanguagePreference.load(newBase)
        val locale = when (mode) {
            LanguageMode.ARABIC -> Locale.forLanguageTag("ar")
            LanguageMode.ENGLISH -> Locale.forLanguageTag("en")
            LanguageMode.SYSTEM -> null
        }
        if (locale == null) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
