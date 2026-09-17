package com.mizan.money.data

import android.content.SharedPreferences

// Foreign-currency transactions used to be dropped from every total (summarize
// filtered `currency == "SAR"`), so a USD/EUR/AED purchase showed up in the
// list while the budget never moved. Instead, all totals now convert to SAR
// using fixed rates the user can edit in Settings. The defaults are rough
// on-purpose (no network calls — the app is fully local), so they're only an
// estimate until the user adjusts them.
object ExchangeRates {
    const val BASE = "SAR"
    val supported = listOf("USD", "EUR", "AED")
    val defaults = mapOf("USD" to 3.75, "EUR" to 4.05, "AED" to 1.02)
    val DEFAULT: Map<String, Double> = mapOf(BASE to 1.0) + defaults

    fun load(prefs: SharedPreferences): Map<String, Double> {
        val rates = HashMap<String, Double>()
        rates[BASE] = 1.0
        for (code in supported) {
            rates[code] = prefs.getFloat("rate_$code", defaults.getValue(code).toFloat()).toDouble()
        }
        return rates
    }

    fun save(prefs: SharedPreferences, code: String, rate: Double) {
        if (code == BASE || code !in supported) return
        if (rate <= 0.0) prefs.edit().remove("rate_$code").apply()
        else prefs.edit().putFloat("rate_$code", rate.toFloat()).apply()
    }

    // Null means "no usable rate for this currency", so callers can exclude the
    // transaction rather than silently counting the raw number as if it were SAR.
    fun toSar(amount: Double, currency: String, rates: Map<String, Double>): Double? {
        if (currency == BASE) return amount
        val rate = rates[currency] ?: return null
        return if (rate > 0.0) amount * rate else null
    }
}
