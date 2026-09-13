package com.mizan.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mizan.money.ui.AppRoot
import com.mizan.money.ui.PreviewTestScreen

// ⚠️ غيّر إلى false بعد الاختبار للعودة للتطبيق الطبيعي
private const val SHOW_PREVIEW_TEST = true

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (SHOW_PREVIEW_TEST) PreviewTestScreen() else AppRoot()
        }
    }
}
