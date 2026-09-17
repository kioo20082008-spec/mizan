package com.mizan.money

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mizan.money.ui.AppRoot
import com.mizan.money.ui.theme.localizedContext

class MainActivity : ComponentActivity() {

    // Applying the chosen language at the Activity level (via attachBaseContext)
    // is what lets BOTH normal composables AND AlertDialogs pick up the right
    // locale — a localized context provided higher up the Compose tree does not
    // reach Dialog windows, and breaks the dialog's own theme lookup.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
