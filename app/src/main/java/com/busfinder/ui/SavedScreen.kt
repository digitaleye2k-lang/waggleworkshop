package com.busfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.busfinder.Place
import com.busfinder.Store
import com.busfinder.Transitous
import com.busfinder.openLink

@Composable
fun SavedScreen(onStop: (Place) -> Unit, onPlanTo: (Place) -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val favs = remember(version) { Store.favourites(ctx) }
    val recents = remember(version) { Store.recents(ctx) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Title("Saved", "Your stops and recent destinations") }
        item { Text("⭐ Saved stops", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall) }
        if (favs.isEmpty()) item {
            Text("Open any stop and tap the star to keep it here for quick departures.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        items(favs, key = { "f" + it.stopId }) { p ->
            Row(Modifier.fillMaxWidth().clickable { onStop(p) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🚏  ${p.name}", Modifier.weight(1f))
                TextButton({ onPlanTo(p) }) { Text("Go here") }
            }
            HorizontalDivider()
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕘 Recent destinations", Modifier.weight(1f).padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall)
                if (recents.isNotEmpty()) TextButton({ Store.clearRecents(ctx); version++ }) { Text("Clear") }
            }
        }
        if (recents.isEmpty()) item {
            Text("Places you plan trips to show up here.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        items(recents, key = { "r" + it.name + it.lat }) { p ->
            Text("📍  ${p.name}", Modifier.fillMaxWidth().clickable { onPlanTo(p) }.padding(16.dp, 12.dp))
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("About BusFinder") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "BusFinder finds stops near you with your phone's GPS, shows the next buses and trains, and compares " +
                    "every way of getting somewhere – quickest first – with a recommended option.\n\n" +
                    "Times marked Live come from operators' real-time feeds. Others are from the timetable. " +
                    "In London, live TfL arrivals are shown at the top of each stop.\n\n" +
                    "BusFinder is free and open source, and doesn't collect any data. Your location is only sent to the " +
                    "timetable services to look up stops and routes.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Data", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            TextButton({ ctx.openLink(Transitous.SITE_URL) }) { Text("Journey planning by Transitous (community-run)") }
            TextButton({ ctx.openLink(Transitous.SOURCES_URL) }) { Text("Timetable data sources and licences") }
            TextButton({ ctx.openLink("https://www.openstreetmap.org/copyright") }) { Text("Map data © OpenStreetMap contributors") }
            TextButton({ ctx.openLink("https://tfl.gov.uk/info-for/open-data-users/") }) { Text("Powered by TfL Open Data (London live times)") }
            TextButton({ ctx.openLink("https://transitous.org/donate/") }) { Text("Support Transitous with a donation") }
            Text("Source code", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            TextButton({ ctx.openLink("https://github.com/digitaleye2k-lang/waggleworkshop/tree/busfinder") }) {
                Text("github.com/digitaleye2k-lang/waggleworkshop (busfinder branch) – MIT licence")
            }
        }
    }
}
