package com.busfinder.ui

import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.busfinder.Gps
import com.busfinder.Journey
import com.busfinder.Kind
import com.busfinder.Leg
import com.busfinder.Place
import com.busfinder.Store
import com.busfinder.Transitous
import com.busfinder.clock
import com.busfinder.distText
import com.busfinder.durText
import com.busfinder.friendlyError
import com.busfinder.minutesFromNow
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class When(val title: String) { NOW("Leave now"), LEAVE_AT("Leave at…"), ARRIVE_BY("Arrive by…") }

/** Labels shown on the results. */
private data class Ranked(val j: Journey, val tags: List<String>)

private fun rank(list: List<Journey>): Pair<List<Ranked>, Journey?> {
    if (list.isEmpty()) return emptyList<Ranked>() to null
    // Don't recommend a 2-hour walk or a car if public transport is reasonable.
    val usable = list.filter { !(it.direct && it.mainKind == Kind.WALK && it.durationSec > 45 * 60) }
    val best = (usable.ifEmpty { list }).minBy { it.score }
    val fastest = list.minBy { it.durationSec }
    val transit = list.filter { !it.direct }
    val fewest = transit.minByOrNull { it.transfers * 10_000L + it.durationSec }
    val leastWalk = transit.minByOrNull { it.walkSec * 10 + it.durationSec }
    val ranked = list.sortedBy { it.durationSec }.map { j ->
        Ranked(j, buildList {
            if (j === best) add("⭐ Recommended")
            if (j === fastest) add("Fastest")
            if (j === fewest && j.transfers < (transit.maxOfOrNull { it.transfers } ?: 0)) add("Fewest changes")
            if (j === leastWalk && transit.size > 1) add("Least walking")
            if (j.live) add("🟢 Live")
        })
    }
    return ranked to best
}

private fun summary(j: Journey): String {
    if (j.direct) return "${j.mainKind.label} the whole way – ${durText(j.durationSec)}."
    val first = j.transitLegs.firstOrNull() ?: return "${durText(j.durationSec)} total."
    val take = "${first.kind.label.lowercase()} ${first.line}".trim()
    val leave = minutesFromNow(j.start)
    return buildString {
        append("Take the $take from ${first.from.name} at ${clock(first.start)}")
        if (j.transfers > 0) append(", change ${j.transfers}×")
        append(", and arrive at ${clock(j.end)}. ")
        append("${durText(j.durationSec)} door to door, ${durText(j.walkSec)} walking.")
        if (leave in 0L..90L) append(" Leave ${if (leave <= 1) "now" else "in $leave min"}.")
    }
}

private fun why(best: Journey, all: List<Journey>): String {
    val fastest = all.minBy { it.durationSec }
    return when {
        best === fastest -> "It's the quickest way there."
        best.transfers < fastest.transfers ->
            "Only ${(best.durationSec - fastest.durationSec) / 60} min slower than the fastest, with fewer changes."
        best.walkSec < fastest.walkSec ->
            "Only ${(best.durationSec - fastest.durationSec) / 60} min slower than the fastest, with less walking."
        else -> "The best balance of time, changes and walking."
    }
}

@Composable
fun JourneyScreen(initialTo: Place?, onConsumed: () -> Unit, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fromText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<Place?>(null) } // null = my location
    var toText by remember { mutableStateOf("") }
    var to by remember { mutableStateOf<Place?>(null) }
    var whenMode by remember { mutableStateOf(When.NOW) }
    var timeMs by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Journey>?>(null) }

    LaunchedEffect(initialTo) {
        if (initialTo != null) { to = initialTo; toText = initialTo.name; onConsumed() }
    }

    fun pickTime(mode: When) {
        val c = Calendar.getInstance()
        TimePickerDialog(ctx, { _, h, m ->
            val t = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
                if (timeInMillis < System.currentTimeMillis() - 5 * 60_000) add(Calendar.DAY_OF_MONTH, 1)
            }
            timeMs = t.timeInMillis; whenMode = mode
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }

    fun search() {
        val dest = to ?: return
        scope.launch {
            error = null; results = null
            val origin = from ?: run {
                busy = "Finding your location…"
                if (!Gps.hasPermission(ctx)) { busy = null; error = "Allow location on the Nearby tab, or type a starting point."; return@launch }
                val l = Gps.current(ctx)
                if (l == null) { busy = null; error = "Couldn't get your location. Type a starting point instead."; return@launch }
                Place("My location", "", l.latitude, l.longitude)
            }
            busy = "Finding the best ways there…"
            try {
                results = Transitous.plan(origin, dest, if (whenMode == When.NOW) null else timeMs, whenMode == When.ARRIVE_BY)
                Store.addRecent(ctx, dest)
            } catch (e: Exception) { error = friendlyError(e) }
            busy = null
        }
    }

    val ranked = remember(results) { results?.let { rank(it) } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Title("Journey", "Compare bus, train, walking and driving") }
                IconButton(onAbout) { Icon(Icons.Default.Info, "About") }
            }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaceSearch(
                    "From", if (from == null && fromText.isEmpty()) "" else fromText,
                    { fromText = it; if (it.isEmpty()) from = null },
                    onPick = { from = it.toPlace(); fromText = it.name },
                    leading = { Icon(Icons.Default.MyLocation, null) },
                )
                if (from == null) Text("Starting from your current location", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                PlaceSearch(
                    "To – address, place or stop", toText, { toText = it; to = null },
                    onPick = { to = it.toPlace(); toText = it.name },
                    trailing = {
                        IconButton({
                            val f = from; val t = to
                            if (t != null) {
                                from = t; fromText = t.name
                                to = f; toText = f?.name ?: ""
                            }
                        }) { Icon(Icons.Default.SwapVert, "Swap") }
                    }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(whenMode == When.NOW, { whenMode = When.NOW; timeMs = null }, label = { Text(When.NOW.title) })
                    FilterChip(whenMode == When.LEAVE_AT, { pickTime(When.LEAVE_AT) },
                        label = { Text(if (whenMode == When.LEAVE_AT) "Leave ${clock(timeMs ?: 0)}" else When.LEAVE_AT.title) })
                    FilterChip(whenMode == When.ARRIVE_BY, { pickTime(When.ARRIVE_BY) },
                        label = { Text(if (whenMode == When.ARRIVE_BY) "Arrive by ${clock(timeMs ?: 0)}" else When.ARRIVE_BY.title) })
                }
                Button({ search() }, Modifier.fillMaxWidth(), enabled = to != null && busy == null) { Text("Find routes") }
                if (to == null && toText.length >= 3) Text("Pick a destination from the suggestions.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        busy?.let { item { Loading(it) } }
        error?.let { item { ErrorText(it) } }
        val r = ranked
        if (r != null) {
            val (list, best) = r
            if (list.isEmpty()) item { Text("No routes found. Try a different time or a nearby stop.", Modifier.padding(16.dp)) }
            if (best != null) item {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⭐ Best way to go", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(summary(best), Modifier.padding(top = 4.dp))
                        Text(why(best, results ?: emptyList()), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (list.isNotEmpty()) item {
                Text("All options, quickest first", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall)
            }
            itemsIndexed(list, key = { i, _ -> i }) { _, item -> JourneyCard(item) }
            item { DataCredit() }
        } else if (busy == null && error == null) {
            item {
                val recent = remember { Store.recents(ctx) }
                if (recent.isNotEmpty()) Column(Modifier.padding(top = 12.dp)) {
                    Text("Recent destinations", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                    recent.forEach { p ->
                        Text("📍 ${p.name}", Modifier.fillMaxWidth().clickable { to = p; toText = p.name }.padding(16.dp, 10.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyCard(r: Ranked) {
    val j = r.j
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).animateContentSize().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(durText(j.durationSec), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("  ${clock(j.start)} → ${clock(j.end)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            if (r.tags.isNotEmpty()) Text(r.tags.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                j.legs.forEachIndexed { i, l ->
                    if (i > 0) Text("›", Modifier.align(Alignment.CenterVertically))
                    if (l.isTransit) LineBadge(l.kind, l.line)
                    else Text("${l.kind.emoji} ${(l.durationSec + 59) / 60}", Modifier.align(Alignment.CenterVertically))
                }
            }
            Text(
                if (j.direct) "${j.mainKind.label} · ${distText(j.legs.sumOf { it.distanceM })}"
                else "${if (j.transfers == 0) "No changes" else "${j.transfers} change${if (j.transfers > 1) "s" else ""}"} · ${durText(j.walkSec)} walking" +
                    (minutesFromNow(j.start).takeIf { it in 0L..120L }?.let { " · leave ${if (it <= 1) "now" else "in $it min"}" } ?: ""),
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                j.legs.forEach { LegRow(it) }
            } else Text("Tap for step-by-step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegRow(l: Leg) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(clock(l.start), Modifier.padding(end = 10.dp), fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            if (l.isTransit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(l.kind, l.line)
                    if (l.headsign.isNotEmpty()) Text(" towards ${l.headsign}", style = MaterialTheme.typography.bodySmall)
                }
                Text("From ${l.from.name}" + (if (l.from.track.isNotEmpty()) " (platform ${l.from.track})" else ""),
                    style = MaterialTheme.typography.bodyMedium)
                Text("Ride ${l.stops} stop${if (l.stops > 1) "s" else ""} · ${durText(l.durationSec)} · get off at ${l.to.name} (${clock(l.end)})",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (l.realTime) {
                    val late = if (l.scheduledStart > 0) (l.start - l.scheduledStart) / 60_000 else 0
                    Text(if (late > 0) "Live: $late min late" else "Live: on time", style = MaterialTheme.typography.bodySmall,
                        color = if (late > 0) LateRed else LiveGreen)
                }
            } else {
                val dest = l.to.name.ifEmpty { "your destination" }
                Text("${l.kind.emoji} ${l.kind.label} ${durText(l.durationSec)}" +
                    (if (l.distanceM > 0) " (${distText(l.distanceM)})" else "") + " to $dest",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
