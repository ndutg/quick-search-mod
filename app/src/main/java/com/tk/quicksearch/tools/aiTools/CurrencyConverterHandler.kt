package com.tk.quicksearch.tools.aiTools

import android.content.Context
import android.util.Xml
import com.tk.quicksearch.R
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency
import java.util.Locale
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

private const val ECB_RATES_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
private const val RATES_PREFERENCES = "currency_exchange_rates"
private const val RATES_JSON_KEY = "rates_json"
private const val RATES_FETCHED_AT_KEY = "rates_fetched_at"
private const val CACHE_DURATION_MS = 4 * 60 * 60 * 1000L
private val CALCULATION_CONTEXT = MathContext(16, RoundingMode.HALF_UP)

class CurrencyConverterHandler(
        private val context: Context,
) {
    private val preferences = context.getSharedPreferences(RATES_PREFERENCES, Context.MODE_PRIVATE)

    /** Fetches one complete ECB rate snapshot on demand, then reuses it for four hours. */
    suspend fun convert(confirmed: ConfirmedCurrencyQuery): Result<CurrencyConversionModelResult> =
            runCatching {
                val rates = loadRates()
                val sourceRate = rates[confirmed.fromCurrency]
                        ?: error("Unsupported currency: ${confirmed.fromCurrency}")
                val targetRate = rates[confirmed.toCurrency]
                        ?: error("Unsupported currency: ${confirmed.toCurrency}")
                val converted = confirmed.amount.toBigDecimal()
                        .multiply(targetRate, CALCULATION_CONTEXT)
                        .divide(sourceRate, CALCULATION_CONTEXT)
                CurrencyConversionModelResult(
                        convertedAmount = converted.stripTrailingZeros().toPlainString(),
                        targetCurrencyCode = confirmed.toCurrency,
                        targetCurrencyName = currencyDisplayName(confirmed.toCurrency),
                        sourceAmount = confirmed.amount,
                        sourceCurrencyCode = confirmed.fromCurrency,
                )
            }

    private fun loadRates(): Map<String, BigDecimal> {
        readCachedRates()?.takeIf { it.fetchedAtMillis + CACHE_DURATION_MS > System.currentTimeMillis() }
                ?.let { return it.rates }
        return fetchRates().also(::saveRates)
    }

    private fun fetchRates(): Map<String, BigDecimal> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ECB_RATES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/xml")
            }
            check(connection.responseCode in 200..299) {
                context.getString(R.string.direct_search_error_generic)
            }
            val rates = mutableMapOf("EUR" to BigDecimal.ONE)
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, null) }
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG || parser.name != "Cube") continue
                    val code = parser.getAttributeValue(null, "currency")?.uppercase(Locale.ROOT)
                    val rate = parser.getAttributeValue(null, "rate")?.toBigDecimalOrNull()
                    if (code != null && code.length == 3 && rate != null && rate > BigDecimal.ZERO) {
                        rates[code] = rate
                    }
                }
            }
            check(rates.size > 1) { context.getString(R.string.direct_search_error_generic) }
            return rates
        } finally {
            connection?.disconnect()
        }
    }

    private fun readCachedRates(): CachedRates? = runCatching {
        val fetchedAtMillis = preferences.getLong(RATES_FETCHED_AT_KEY, 0L)
        val rawRates = preferences.getString(RATES_JSON_KEY, null) ?: return null
        val json = JSONObject(rawRates)
        val rates = buildMap {
            json.keys().forEach { code ->
                json.optString(code).toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let {
                    put(code, it)
                }
            }
        }
        if (fetchedAtMillis <= 0L || rates["EUR"] != BigDecimal.ONE || rates.size <= 1) null
        else CachedRates(fetchedAtMillis, rates)
    }.getOrNull()

    private fun saveRates(rates: Map<String, BigDecimal>) {
        val json = JSONObject().apply {
            rates.forEach { (code, rate) -> put(code, rate.toPlainString()) }
        }
        preferences.edit().putString(RATES_JSON_KEY, json.toString())
                .putLong(RATES_FETCHED_AT_KEY, System.currentTimeMillis()).apply()
    }

    private fun currencyDisplayName(code: String): String =
            runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)

    private data class CachedRates(
            val fetchedAtMillis: Long,
            val rates: Map<String, BigDecimal>,
    )
}

data class CurrencyConversionModelResult(
        val convertedAmount: String,
        val targetCurrencyCode: String,
        val targetCurrencyName: String,
        val sourceAmount: String,
        val sourceCurrencyCode: String,
)
