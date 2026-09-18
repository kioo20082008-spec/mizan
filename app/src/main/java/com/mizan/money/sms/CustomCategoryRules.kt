package com.mizan.money.sms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// User-defined keyword -> category rules, persisted as a JSON array string in
// the same "mizan_prefs" file the rest of the app uses. Loaded at app start
// (MoneyApp) and live in MainViewModel, so CategoryClassifier can honor them
// both at parse time and when recategorizing existing transactions.
object CustomCategoryRules {
    private const val PREFS = "mizan_prefs"
    private const val KEY = "custom_category_rules"

    fun load(ctx: Context): List<Pair<String, String>> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Pair<String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val keyword = obj.optString("keyword").trim()
                val category = obj.optString("category").trim()
                if (keyword.isNotEmpty() && category.isNotEmpty()) out.add(keyword to category)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, rules: List<Pair<String, String>>) {
        val arr = JSONArray()
        for ((keyword, category) in rules) {
            arr.put(JSONObject().put("keyword", keyword).put("category", category))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
