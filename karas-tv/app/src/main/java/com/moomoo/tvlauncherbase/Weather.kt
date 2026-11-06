package com.moomoo.tvlauncherbase

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Weather from Open-Meteo (free, no account, no API key).
 * Location: a city / ZIP you pick (Open-Meteo geocoding) or detected once
 * from your internet connection. Refreshed every 30 minutes and cached, so
 * the header shows the last reading instantly.
 */
object Weather {

    /** What the header shows. [code] is the WMO weather code. */
    class Now(
        val temp: Double, val feels: Double, val code: Int, val isDay: Boolean, val wind: Double,
        val humidity: Int, val high: Double, val low: Double, val rainChance: Int,
        val days: List<Day>, val at: Long
    )
    class Day(val date: String, val code: Int, val high: Double, val low: Double, val rainChance: Int)
    class Place(val name: String, val lat: Double, val lon: Double)

    /** The animated icon to draw. */
    enum class Kind { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUDS, FOG, DRIZZLE, RAIN, SNOW, STORM }

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var current: Now? = null
        private set
    @Volatile private var fetching = false

    fun kind(code: Int, day: Boolean): Kind = when (code) {
        0 -> if (day) Kind.SUN else Kind.MOON
        1, 2 -> if (day) Kind.PARTLY_DAY else Kind.PARTLY_NIGHT
        3 -> Kind.CLOUDS
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57 -> Kind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.CLOUDS
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80 -> "Showers"; 81 -> "Rain showers"; 82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"; 96, 99 -> "Storm with hail"
        else -> "—"
    }

    fun defaultFahrenheit(): Boolean = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    fun deg(c: Context, v: Double) = "${Math.round(v)}°"
    fun unitLetter(c: Context) = if (BasePrefs.weatherFahrenheit(c)) "F" else "C"
    fun windText(c: Context, v: Double) = "${Math.round(v)} " + if (BasePrefs.weatherFahrenheit(c)) "mph" else "km/h"

    // ------------------------------------------------------------ HTTP
    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "TVLauncher/2.7 (Android TV)")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ------------------------------------------------------------ location
    /** City / ZIP search (Open-Meteo geocoding). */
    fun searchPlaces(q: String, done: (List<Place>, String?) -> Unit) {
        pool.execute {
            val out = try {
                val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=8&language=en&format=json"))
                val a = o.optJSONArray("results")
                (0 until (a?.length() ?: 0)).map { i ->
                    val r = a!!.getJSONObject(i)
                    val label = listOfNotNull(r.optString("name"), r.optString("admin1").takeIf { it.isNotEmpty() && it != "null" },
                        r.optString("country_code").takeIf { it.isNotEmpty() && it != "null" }).joinToString(", ")
                    Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
                } to null
            } catch (e: Exception) { emptyList<Place>() to "Couldn't reach the weather service" }
            main.post { done(out.first, out.second) }
        }
    }

    /** Rough location from the internet connection (city level), used once if you allow it. */
    fun detectPlace(done: (Place?) -> Unit) {
        pool.execute {
            val p = try {
                val o = JSONObject(get("http://ip-api.com/json/?fields=status,city,regionName,countryCode,lat,lon"))
                if (o.optString("status") == "success")
                    Place(listOf(o.optString("city"), o.optString("regionName")).filter { it.isNotEmpty() }.joinToString(", "),
                        o.getDouble("lat"), o.getDouble("lon")) else null
            } catch (e: Exception) { null }
            main.post { done(p) }
        }
    }

    // ------------------------------------------------------------ forecast
    fun parse(json: String): Now? = try {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.optJSONObject("daily")
        val days = if (d == null) emptyList() else {
            val t = d.getJSONArray("time"); val wc = d.getJSONArray("weather_code")
            val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
            val pr = d.optJSONArray("precipitation_probability_max")
            (0 until t.length()).map { i ->
                Day(t.getString(i), wc.optInt(i), hi.optDouble(i), lo.optDouble(i), pr?.optInt(i, 0) ?: 0)
            }
        }
        Now(
            cur.optDouble("temperature_2m"), cur.optDouble("apparent_temperature", cur.optDouble("temperature_2m")),
            cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1, cur.optDouble("wind_speed_10m"),
            cur.optInt("relative_humidity_2m"),
            days.firstOrNull()?.high ?: Double.NaN, days.firstOrNull()?.low ?: Double.NaN,
            days.firstOrNull()?.rainChance ?: 0, days, o.optLong("__fetched", System.currentTimeMillis())
        )
    } catch (e: Exception) { null }

    /** Cached reading (instant), then refresh if older than 30 min. [done] on main thread when fresh data lands. */
    fun refresh(c: Context, force: Boolean = false, done: (() -> Unit)? = null) {
        val app = c.applicationContext
        if (!BasePrefs.weatherEnabled(app)) return
        if (current == null) BasePrefs.weatherCache(app)?.let { current = parse(it) }
        val place = BasePrefs.weatherPlace(app) ?: return
        val age = System.currentTimeMillis() - (current?.at ?: 0L)
        if (!force && age < 30 * 60_000L) return
        if (fetching) return
        fetching = true
        pool.execute {
            try {
                val f = BasePrefs.weatherFahrenheit(app)
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&forecast_days=4&timezone=auto" +
                    (if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")
                val json = JSONObject(get(url)).put("__fetched", System.currentTimeMillis()).toString()
                parse(json)?.let { now ->
                    current = now
                    BasePrefs.setWeatherCache(app, json)
                    main.post { done?.invoke() }
                }
            } catch (e: Exception) { /* keep the cached reading */ }
            finally { fetching = false }
        }
    }

    /** Forget the cached reading (after a location / unit change). */
    fun reset(c: Context) { current = null; BasePrefs.setWeatherCache(c, null) }
}
