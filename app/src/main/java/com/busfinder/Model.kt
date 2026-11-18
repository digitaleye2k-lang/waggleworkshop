package com.busfinder

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- JSON helpers (tolerant of missing / null fields) ----------

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.dbl(k: String): Double = if (isNull(k)) 0.0 else optDouble(k, 0.0)
fun JSONObject.lng(k: String): Long = if (isNull(k)) 0L else optLong(k, 0L)
fun JSONObject.time(k: String): Long = parseTime(str(k))
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun parseTime(s: String): Long = if (s.isEmpty()) 0L else try { Instant.parse(s).toEpochMilli() } catch (e: Exception) { 0L }

private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
fun clock(ms: Long): String = if (ms <= 0) "--:--" else hhmm.format(Instant.ofEpochMilli(ms))

fun minutesFromNow(ms: Long): Long = Math.floorDiv(ms - System.currentTimeMillis(), 60_000L)

fun dueText(ms: Long): String {
    val m = minutesFromNow(ms)
    return when {
        m <= 0 -> "Due"
        m < 60 -> "$m min"
        else -> "${m / 60}h ${m % 60}m"
    }
}

fun durText(sec: Long): String {
    val m = (sec + 59) / 60
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun distText(m: Double): String = if (m < 1000) "${(m / 10).toInt() * 10} m" else String.format("%.1f km", m / 1000)

// ---------- Transport modes ----------

enum class Kind(val emoji: String, val label: String) {
    BUS("🚌", "Bus"), TRAIN("🚆", "Train"), METRO("🚇", "Underground / metro"), TRAM("🚊", "Tram"),
    FERRY("⛴️", "Ferry"), PLANE("✈️", "Flight"), WALK("🚶", "Walk"), BIKE("🚲", "Cycle"), CAR("🚗", "Drive / taxi"), OTHER("🚐", "Other")
}

fun kindOf(mode: String): Kind = when (mode.uppercase()) {
    "BUS", "COACH" -> Kind.BUS
    "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_FAST_RAIL", "REGIONAL_RAIL", "SUBURBAN", "METRO" -> Kind.TRAIN
    "SUBWAY" -> Kind.METRO
    "TRAM", "CABLE_CAR", "FUNICULAR" -> Kind.TRAM
    "FERRY" -> Kind.FERRY
    "AIRPLANE" -> Kind.PLANE
    "WALK", "FOOT" -> Kind.WALK
    "BIKE", "BIKE_SHARING", "BIKE_TO_PARK", "RENTAL" -> Kind.BIKE
    "CAR", "CAR_PARKING", "CAR_DROPOFF", "CAR_SHARING", "ODM", "FLEX" -> Kind.CAR
    else -> Kind.OTHER
}

// ---------- Data ----------

data class Place(
    val name: String,
    val stopId: String,
    val lat: Double,
    val lon: Double,
    val departure: Long = 0,
    val scheduledDeparture: Long = 0,
    val arrival: Long = 0,
    val scheduledArrival: Long = 0,
    val track: String = "",
) {
    /** Text used as fromPlace/toPlace in a plan request. */
    val query: String get() = if (stopId.isNotEmpty()) stopId else "$lat,$lon"

    companion object {
        fun from(o: JSONObject) = Place(
            name = o.str("name"), stopId = o.str("stopId"), lat = o.dbl("lat"), lon = o.dbl("lon"),
            departure = o.time("departure"), scheduledDeparture = o.time("scheduledDeparture"),
            arrival = o.time("arrival"), scheduledArrival = o.time("scheduledArrival"),
            track = o.str("track").ifEmpty { o.str("scheduledTrack") },
        )
    }
}

data class StopTime(
    val place: Place,
    val mode: String,
    val line: String,
    val headsign: String,
    val agency: String,
    val realTime: Boolean,
    val cancelled: Boolean,
) {
    val kind: Kind get() = kindOf(mode)
    val time: Long get() = place.departure.takeIf { it > 0 } ?: place.arrival
    val scheduled: Long get() = place.scheduledDeparture.takeIf { it > 0 } ?: place.scheduledArrival
    val delayMin: Long get() = if (realTime && scheduled > 0 && time > 0) (time - scheduled) / 60_000 else 0

    companion object {
        fun from(o: JSONObject) = StopTime(
            place = Place.from(o.optJSONObject("place") ?: JSONObject()),
            mode = o.str("mode"),
            line = lineName(o),
            headsign = o.str("headsign"),
            agency = o.str("agencyName"),
            realTime = o.optBoolean("realTime", false),
            cancelled = o.optBoolean("cancelled", false) || o.optBoolean("tripCancelled", false),
        )
    }
}

fun lineName(o: JSONObject): String =
    o.str("displayName").ifEmpty { o.str("routeShortName") }.ifEmpty { o.str("routeLongName") }

data class Leg(
    val mode: String,
    val from: Place,
    val to: Place,
    val start: Long,
    val end: Long,
    val scheduledStart: Long,
    val durationSec: Long,
    val distanceM: Double,
    val line: String,
    val headsign: String,
    val agency: String,
    val realTime: Boolean,
    val stops: Int,
) {
    val kind: Kind get() = kindOf(mode)
    val isTransit: Boolean get() = kind !in setOf(Kind.WALK, Kind.BIKE, Kind.CAR)

    companion object {
        fun from(o: JSONObject): Leg {
            val start = o.time("startTime")
            val end = o.time("endTime")
            return Leg(
                mode = o.str("mode"),
                from = Place.from(o.optJSONObject("from") ?: JSONObject()),
                to = Place.from(o.optJSONObject("to") ?: JSONObject()),
                start = start, end = end,
                scheduledStart = o.time("scheduledStartTime"),
                durationSec = o.lng("duration").takeIf { it > 0 } ?: ((end - start) / 1000),
                distanceM = o.dbl("distance"),
                line = lineName(o),
                headsign = o.str("headsign"),
                agency = o.str("agencyName"),
                realTime = o.optBoolean("realTime", false),
                stops = (o.optJSONArray("intermediateStops")?.length() ?: 0) + 1,
            )
        }
    }
}

data class Journey(
    val durationSec: Long,
    val start: Long,
    val end: Long,
    val transfers: Int,
    val legs: List<Leg>,
    /** Direct = no public transport (walk / cycle / drive the whole way). */
    val direct: Boolean,
) {
    val walkSec: Long get() = legs.filter { it.kind == Kind.WALK }.sumOf { it.durationSec }
    val transitLegs: List<Leg> get() = legs.filter { it.isTransit }
    val mainKind: Kind get() = transitLegs.maxByOrNull { it.durationSec }?.kind ?: legs.firstOrNull()?.kind ?: Kind.WALK
    val live: Boolean get() = transitLegs.any { it.realTime }
    /** Lower is better: time, plus 5 min per change and a penalty for long walks. */
    val score: Double get() = durationSec + transfers * 300.0 + (walkSec - 600).coerceAtLeast(0L) * 0.5 +
        (if (direct && mainKind == Kind.CAR) 600.0 else 0.0)

    companion object {
        fun from(o: JSONObject, direct: Boolean): Journey {
            val legs = (o.optJSONArray("legs") ?: JSONArray()).objects().map { Leg.from(it) }
            val start = o.time("startTime").takeIf { it > 0 } ?: legs.firstOrNull()?.start ?: 0
            val end = o.time("endTime").takeIf { it > 0 } ?: legs.lastOrNull()?.end ?: 0
            return Journey(
                durationSec = o.lng("duration").takeIf { it > 0 } ?: ((end - start) / 1000),
                start = start, end = end,
                transfers = if (o.has("transfers")) o.optInt("transfers") else (legs.count { it.isTransit } - 1).coerceAtLeast(0),
                legs = legs, direct = direct,
            )
        }
    }
}

data class GeoMatch(val name: String, val area: String, val lat: Double, val lon: Double, val type: String, val id: String) {
    val isStop: Boolean get() = type.equals("STOP", true)
    fun toPlace() = Place(name, if (isStop) id else "", lat, lon)

    companion object {
        fun from(o: JSONObject): GeoMatch {
            val areas = (o.optJSONArray("areas") ?: JSONArray()).objects()
            val area = (areas.firstOrNull { it.optBoolean("default") } ?: areas.firstOrNull())?.str("name") ?: ""
            return GeoMatch(o.str("name"), area, o.dbl("lat"), o.dbl("lon"), o.str("type"), o.str("id"))
        }
    }
}

/** A live London arrival from TfL. */
data class LiveArrival(val line: String, val destination: String, val seconds: Long, val platform: String, val mode: String)
