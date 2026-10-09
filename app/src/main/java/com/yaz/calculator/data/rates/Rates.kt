package com.yaz.calculator.data.rates

import android.content.Context
import com.yaz.calculator.core.common.writeTextAtomically
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Exchange rates against the euro, as published on [date] (YYYY-MM-DD). */
@Serializable
data class RateTable(val date: String, val fetched: Long, val rates: Map<String, String>)

enum class RatesState { IDLE, LOADING, FAILED }

/**
 * Exchange rates for every currency in use, from fawazahmed0's exchange-api
 * (public domain, updated daily, github.com/fawazahmed0/exchange-api),
 * fetched only when the currency converter opens and the rates are older
 * than a few hours. Kept on the phone for use offline. Only codes Android
 * knows as currencies are taken, each rate checked to be a positive number.
 */
class Rates(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "rates.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _table = MutableStateFlow<RateTable?>(null)
    val table: StateFlow<RateTable?> = _table.asStateFlow()
    private val _state = MutableStateFlow(RatesState.IDLE)
    val state: StateFlow<RatesState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            if (_table.value == null) _table.value = runCatching { json.decodeFromString<RateTable>(file.readText()) }.getOrNull()
        }
    }

    /** Fetches new rates when the ones kept are missing or older than [FRESH_MS]. */
    fun refreshIfOld() {
        val kept = _table.value
        if (kept != null && System.currentTimeMillis() - kept.fetched < FRESH_MS) return
        refresh()
    }

    fun refresh() {
        if (_state.value == RatesState.LOADING) return
        _state.value = RatesState.LOADING
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                SOURCES.firstNotNullOfOrNull { url -> runCatching { parse(get(url)) }.getOrNull() }
            }
            if (fresh != null) {
                _table.value = fresh
                withContext(Dispatchers.IO) { runCatching { file.writeTextAtomically(json.encodeToString(fresh)) } }
                _state.value = RatesState.IDLE
            } else {
                _state.value = RatesState.FAILED
            }
        }
    }

    private fun parse(body: String): RateTable? {
        val root = json.parseToJsonElement(body).jsonObject
        val date = (root["date"] as? JsonPrimitive)?.content?.takeIf { DATE.matches(it) } ?: return null
        val eur = root["eur"] as? JsonObject ?: return null
        val known = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
        val rates = HashMap<String, String>()
        for ((code, value) in eur) {
            val upper = code.uppercase()
            if (upper !in known) continue
            val text = (value as? JsonPrimitive)?.takeIf { !it.isString }?.content ?: continue
            val number = text.toDoubleOrNull() ?: continue
            if (number.isFinite() && number > 0 && NUMBER.matches(text)) rates[upper] = text
        }
        rates["EUR"] = "1"
        if (rates.size < 30) return null
        return RateTable(date, System.currentTimeMillis(), rates)
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Calculator")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != 200) error("http ${connection.responseCode}")
            connection.inputStream.use { input ->
                val bytes = input.readNBytes(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) error("too large")
                return bytes.decodeToString()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val SOURCES = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.min.json",
            "https://latest.currency-api.pages.dev/v1/currencies/eur.min.json"
        )
        private const val FRESH_MS = 6L * 60 * 60 * 1000
        private const val MAX_BYTES = 256 * 1024
        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val NUMBER = Regex("\\d+(\\.\\d+)?([eE][-+]?\\d+)?")
    }
}
