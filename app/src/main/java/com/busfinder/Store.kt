package com.busfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Favourite stops and recent destinations, kept on the phone. */
object Store {
    private fun p(ctx: Context) = ctx.getSharedPreferences("busfinder", Context.MODE_PRIVATE)

    private fun toJson(l: List<Place>) = JSONArray().apply {
        l.forEach { put(JSONObject().put("name", it.name).put("stopId", it.stopId).put("lat", it.lat).put("lon", it.lon)) }
    }.toString()

    private fun fromJson(s: String?): List<Place> = try {
        JSONArray(s ?: "[]").objects().map { Place(it.str("name"), it.str("stopId"), it.dbl("lat"), it.dbl("lon")) }
    } catch (e: Exception) { emptyList() }

    fun favourites(ctx: Context): List<Place> = fromJson(p(ctx).getString("fav", null))
    fun isFavourite(ctx: Context, stopId: String) = favourites(ctx).any { it.stopId == stopId }
    fun toggleFavourite(ctx: Context, place: Place): Boolean {
        val l = favourites(ctx)
        val now = l.none { it.stopId == place.stopId }
        val next = if (now) l + place.copy(departure = 0, arrival = 0) else l.filter { it.stopId != place.stopId }
        p(ctx).edit().putString("fav", toJson(next)).apply()
        return now
    }

    fun recents(ctx: Context): List<Place> = fromJson(p(ctx).getString("recent", null))
    fun addRecent(ctx: Context, place: Place) {
        val next = (listOf(place) + recents(ctx).filter { it.name != place.name }).take(8)
        p(ctx).edit().putString("recent", toJson(next)).apply()
    }
    fun clearRecents(ctx: Context) = p(ctx).edit().remove("recent").apply()
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { toast("Couldn't open link") }
}

/** Walking directions to a stop in Google Maps (or any maps app). */
fun Context.walkTo(p: Place) =
    openLink("https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lon}&travelmode=walking")

fun friendlyError(e: Throwable): String = when {
    e is HttpError && e.code == 429 -> "The timetable service is busy. Try again in a minute."
    e is HttpError && e.code >= 500 -> "The timetable service is having problems right now. Try again shortly."
    e is HttpError -> "Couldn't get times for this (error ${e.code})."
    e is java.net.UnknownHostException || e is java.net.ConnectException -> "No internet connection."
    e is java.net.SocketTimeoutException -> "The timetable service took too long to answer. Try again."
    else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
}
