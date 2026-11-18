package com.busfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.busfinder.ui.AboutScreen
import com.busfinder.ui.AppTheme
import com.busfinder.ui.DeparturesScreen
import com.busfinder.ui.JourneyScreen
import com.busfinder.ui.NearbyScreen
import com.busfinder.ui.SavedScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { Root() } }
    }
}

@Composable
fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stop by remember { mutableStateOf<Place?>(null) }
    var about by remember { mutableStateOf(false) }
    // Destination handed from Saved/Nearby to the Journey tab.
    var journeyTo by remember { mutableStateOf<Place?>(null) }

    BackHandler(enabled = stop != null || about) { if (about) about = false else stop = null }

    val s = stop
    when {
        about -> AboutScreen { about = false }
        s != null -> DeparturesScreen(s, onBack = { stop = null }, onPlanTo = { journeyTo = it; stop = null; tab = 1 })
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("📍" to "Nearby", "🧭" to "Journey", "⭐" to "Saved").forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(tab == i, { tab = i }, icon = { Text(icon) }, label = { Text(label) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> NearbyScreen(onStop = { stop = it }, onAbout = { about = true })
                    1 -> JourneyScreen(initialTo = journeyTo, onConsumed = { journeyTo = null }, onAbout = { about = true })
                    else -> SavedScreen(onStop = { stop = it }, onPlanTo = { journeyTo = it; tab = 1 })
                }
            }
        }
    }
}
