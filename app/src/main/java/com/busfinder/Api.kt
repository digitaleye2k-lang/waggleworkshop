package com.busfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class HttpError(val code: Int, body: String) : Exception("HTTP $code ${body.take(200)}")

private suspend fun httpGet(url: String, userAgent: String): String = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 15_000
        c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        if (code !in 200..299) throw HttpError(code, c.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
        c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun query(params: Map<String, String>) = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

/**
 * Transitous (community-run MOTIS 2 API, open data from bus & rail operators).
 * Usage policy: open-source, non-commercial, light use, User-Agent with contact, visible attribution.
 * MOTIS bumps endpoint versions now and then, so each call tries the newest version first.
 */
object Transitous {
    private const val BASE = "https://api.transitous.org"
    const val SOURCES_URL = "https://transitous.org/sources/"
    const val SITE_URL = "https://transitous.org/"
    private const val UA = "BusFinder/1.0 (open source; +https://github.com/digitaleye2k-lang/waggleworkshop/tree/busfinder)"

    private suspend fun get(paths: List<String>, params: Map<String, String>): String {
        var last: Exception? = null
        for (p in paths) {
            try {
                return httpGet("$BASE$p?${query(params)}", UA)
            } catch (e: HttpError) {
                last = e
                if (e.code != 404) throw e
            }
        }
        throw last ?: IllegalStateException("No endpoint")
    }

    suspend fun geocode(text: String): List<GeoMatch> {
        val body = get(listOf("/api/v1/geocode"), mapOf("text" to text, "language" to "en"))
        return JSONArray(body).objects().map { GeoMatch.from(it) }.filter { it.name.isNotEmpty() }
    }

    /** Stops inside a box around a point. */
    suspend fun stopsNear(lat: Double, lon: Double, radiusM: Double): List<Pair<Place, Double>> {
        val dLat = radiusM / 111_000.0
        val dLon = radiusM / (111_000.0 * Math.cos(Math.toRadians(lat)))
        val body = get(
            listOf("/api/v6/map/stops", "/api/v5/map/stops", "/api/v1/map/stops"),
            mapOf("min" to "${lat - dLat},${lon - dLon}", "max" to "${lat + dLat},${lon + dLon}")
        )
        val arr = body.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it).optJSONArray("stops") ?: JSONArray() }
        return arr.objects().map { Place.from(it) }
            .filter { it.stopId.isNotEmpty() && it.name.isNotEmpty() }
            // Platforms of one station often come back separately – keep the closest of each name.
            .map { it to distanceM(lat, lon, it.lat, it.lon) }
            .groupBy { it.first.name.lowercase() }.map { (_, l) -> l.minBy { it.second } }
            .filter { it.second <= radiusM * 1.2 }
            .sortedBy { it.second }
    }

    suspend fun departures(stopId: String, n: Int = 40): List<StopTime> {
        val body = get(
            listOf("/api/v6/stoptimes", "/api/v5/stoptimes", "/api/v4/stoptimes", "/api/v1/stoptimes"),
            mapOf("stopId" to stopId, "n" to n.toString())
        )
        val o = JSONObject(body)
        return (o.optJSONArray("stopTimes") ?: JSONArray()).objects().map { StopTime.from(it) }
            .sortedBy { it.time }
    }

    /**
     * Journey planning. Returns public-transport options plus direct walk / cycle / drive times.
     * This is the "expensive" endpoint, so the app only calls it when you press Search.
     */
    suspend fun plan(from: Place, to: Place, timeMs: Long?, arriveBy: Boolean): List<Journey> {
        val base = buildMap {
            put("fromPlace", from.query)
            put("toPlace", to.query)
            put("numItineraries", "6")
            put("arriveBy", arriveBy.toString())
            if (timeMs != null) put("time", Instant.ofEpochMilli(timeMs).toString())
        }
        val paths = listOf("/api/v6/plan", "/api/v5/plan", "/api/v4/plan", "/api/v3/plan", "/api/v1/plan")
        val body = try {
            get(paths, base + ("directModes" to "WALK,BIKE,CAR"))
        } catch (e: HttpError) {
            if (e.code == 400 || e.code == 422) get(paths, base + ("directModes" to "WALK")) else throw e
        }
        val o = JSONObject(body)
        val transit = (o.optJSONArray("itineraries") ?: JSONArray()).objects().map { Journey.from(it, false) }
        val direct = (o.optJSONArray("direct") ?: JSONArray()).objects().map { Journey.from(it, true) }
            .groupBy { it.mainKind }.map { (_, l) -> l.minBy { it.durationSec } }
        return (transit + direct).filter { it.legs.isNotEmpty() }.distinctBy { j ->
            j.legs.joinToString("|") { "${it.mode}${it.line}${it.start}" }
        }
    }
}

/** Live London arrivals from TfL's open data (no key needed for light use). */
object Tfl {
    private const val BASE = "https://api.tfl.gov.uk"
    private const val UA = "BusFinder/1.0 (+https://github.com/digitaleye2k-lang/waggleworkshop/tree/busfinder)"

    fun inLondon(lat: Double, lon: Double) = lat in 51.28..51.70 && lon in -0.52..0.34

    suspend fun liveArrivals(lat: Double, lon: Double, name: String): List<LiveArrival> {
        val body = httpGet(
            "$BASE/StopPoint?" + query(
                mapOf(
                    "lat" to lat.toString(), "lon" to lon.toString(), "radius" to "80",
                    "stopTypes" to "NaptanPublicBusCoachTram,NaptanMetroStation,NaptanRailStation"
                )
            ), UA
        )
        val points = (JSONObject(body).optJSONArray("stopPoints") ?: JSONArray()).objects()
        if (points.isEmpty()) return emptyList()
        val first = name.lowercase().take(6)
        val best = points.firstOrNull { it.str("commonName").lowercase().startsWith(first) } ?: points.first()
        val id = best.str("naptanId").ifEmpty { best.str("id") }
        if (id.isEmpty()) return emptyList()
        val arr = JSONArray(httpGet("$BASE/StopPoint/${enc(id)}/Arrivals", UA))
        return arr.objects().map {
            LiveArrival(it.str("lineName"), it.str("destinationName"), it.lng("timeToStation"), it.str("platformName"), it.str("modeName"))
        }.sortedBy { it.seconds }
    }
}
