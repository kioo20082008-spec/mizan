package com.mizan.money.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fire-and-forget helper the rest of the app calls after any change that
// could affect the widget's numbers (new SMS, manual transaction, budget
// edit, etc). Runs on IO, swallows failures so a broken widget never
// crashes the app.
object WidgetUpdater {
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MizanWidget().updateAll(appContext)
            } catch (e: Exception) {
                Log.e("Mizan", "widget update failed", e)
            }
        }
    }
}
