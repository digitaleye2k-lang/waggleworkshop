package com.busfinder.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.busfinder.Gps
import com.busfinder.Kind
import com.busfinder.LiveArrival
import com.busfinder.Place
import com.busfinder.StopTime
import com.busfinder.Store
import com.busfinder.Tfl
import com.busfinder.Transitous
import com.busfinder.clock
import com.busfinder.distText
import com.busfinder.dueText
import com.busfinder.friendlyError
import com.busfinder.toast
import com.busfinder.walkTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ----------------------------------------------------------------------------------------------
// Nearby stops
// ----------------------------------------------------------------------------------------------

@Composable
fun NearbyScreen(onStop: (Place) -> Unit, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    var permission by remember { mutableStateOf(Gps.hasPermission(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var stops by remember { mutableStateOf<List<Pair<Place, Double>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = Gps.hasPermission(ctx)
        if (permission) refresh++
    }

    LaunchedEffect(permission, refresh) {
        if (!permission) return@LaunchedEffect
        error = null
        if (!Gps.enabled(ctx)) { error = "Location is turned off. Turn it on in quick settings, then tap refresh."; return@LaunchedEffect }
        status = "Finding your location…"
        val loc = Gps.current(ctx)
        if (loc == null) { status = null; error = "Couldn't get your location. Try again outside or near a window."; return@LaunchedEffect }
        status = "Looking for stops near you…"
        try {
            var r = Transitous.stopsNear(loc.latitude, loc.longitude, 600.0)
            if (r.size < 3) r = Transitous.stopsNear(loc.latitude, loc.longitude, 1500.0)
            stops = r.take(30)
        } catch (e: Exception) { error = friendlyError(e) }
        status = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Title("Nearby", "Bus stops and stations around you") }
            IconButton({ refresh++ }) { Icon(Icons.Default.Refresh, "Refresh") }
            IconButton(onAbout) { Icon(Icons.Default.Info, "About") }
        }
        PlaceSearch(
            "Search a stop or station", search, { search = it },
            onPick = { m -> onStop(m.toPlace()); search = "" }, stopsOnly = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            if (!permission) item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Allow location", style = MaterialTheme.typography.titleMedium)
                        Text("BusFinder uses your phone's GPS to find the stops closest to you. Your location is only sent to the timetable service to look up stops and routes.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                        Button({ launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                            Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Allow")
                        }
                    }
                }
            }
            status?.let { item { Loading(it) } }
            error?.let { item { ErrorText(it) } }
            val l = stops
            if (l != null && l.isEmpty() && status == null) item { Text("No stops found within 1.5 km.", Modifier.padding(16.dp)) }
            items(l ?: emptyList(), key = { it.first.stopId }) { (p, d) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onStop(p) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚏", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${distText(d)} · ${(d / 80).toInt().coerceAtLeast(1)} min walk",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (Store.isFavourite(ctx, p.stopId)) Icon(Icons.Default.Star, "Saved", tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            item { DataCredit() }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Departures from one stop
// ----------------------------------------------------------------------------------------------

private enum class DepFilter(val title: String, val kinds: Set<Kind>?) {
    ALL("All", null), BUS("🚌 Bus", setOf(Kind.BUS)), TRAIN("🚆 Train", setOf(Kind.TRAIN)),
    METRO("🚇 Metro / tram", setOf(Kind.METRO, Kind.TRAM)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(stop: Place, onBack: () -> Unit, onPlanTo: (Place) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<StopTime>?>(null) }
    var live by remember { mutableStateOf<List<LiveArrival>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DepFilter.ALL) }
    var fav by remember { mutableStateOf(Store.isFavourite(ctx, stop.stopId)) }
    var tick by remember { mutableIntStateOf(0) }
    val london = remember(stop) { Tfl.inLondon(stop.lat, stop.lon) }

    suspend fun load() {
        loading = true
        try {
            list = Transitous.departures(stop.stopId)
            error = null
        } catch (e: Exception) { error = friendlyError(e) }
        if (london) live = try { Tfl.liveArrivals(stop.lat, stop.lon, stop.name).take(12) } catch (e: Exception) { emptyList() }
        loading = false
    }
    // Refresh every minute while the screen is open; "in X min" counts down in between.
    LaunchedEffect(stop.stopId) {
        while (true) {
            load()
            repeat(4) { delay(15_000); tick++ }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton({ fav = Store.toggleFavourite(ctx, stop); ctx.toast(if (fav) "Saved" else "Removed from saved") }) {
                    Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Save stop")
                }
                IconButton({ scope.launch { load() } }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        )
    }) { pad ->
        val all = list
        val kindsHere = remember(all) { all?.map { it.kind }?.toSet() ?: emptySet() }
        val shown = remember(all, filter, tick) {
            val ks = filter.kinds
            all?.filter { (ks == null || it.kind in ks) && it.time > System.currentTimeMillis() - 60_000 }
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ ctx.walkTo(stop) }) { Text("🚶 Walk here") }
                    OutlinedButton({ onPlanTo(stop) }) { Text("🧭 Plan a trip here") }
                }
                if (kindsHere.size > 1) Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DepFilter.entries.filter { f -> f.kinds == null || f.kinds.any { it in kindsHere } }.forEach { f ->
                        FilterChip(filter == f, { filter = f }, label = { Text(f.title) })
                    }
                }
            }
            if (live.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("🟢 Live from TfL", fontWeight = FontWeight.SemiBold)
                            live.forEach { a ->
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val k = if (a.mode == "tube") Kind.METRO else if (a.mode.contains("rail") || a.mode == "overground" || a.mode == "elizabeth-line") Kind.TRAIN else Kind.BUS
                                    LineBadge(k, a.line)
                                    Text(a.destination, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (a.seconds < 60) "Due" else "${a.seconds / 60} min", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            if (loading && all == null) item { Loading("Getting departures…") }
            error?.let { item { ErrorText(it) } }
            if (shown != null && shown.isEmpty() && !loading) item {
                Text("No departures found in the next few hours.", Modifier.padding(16.dp))
            }
            items(shown ?: emptyList(), key = { "${it.line}_${it.time}_${it.headsign}_${it.place.stopId}" }) { d -> DepartureRow(d) }
            item { DataCredit(tfl = live.isNotEmpty()) }
        }
    }
}

@Composable
private fun DepartureRow(d: StopTime) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(d.kind, d.line)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                d.headsign.ifEmpty { d.agency }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null
            )
            val bits = buildList {
                if (d.place.track.isNotEmpty()) add("Platform ${d.place.track}")
                if (d.cancelled) add("Cancelled")
                else if (d.realTime) add(if (d.delayMin > 0) "Live · ${d.delayMin} min late" else "Live · on time")
                else add("Timetable")
            }
            Text(
                bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = when {
                    d.cancelled || d.delayMin > 0 -> LateRed
                    d.realTime -> LiveGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dueText(d.time), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(clock(d.time), style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}
