package com.busfinder.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.busfinder.GeoMatch
import com.busfinder.Kind
import com.busfinder.Transitous
import com.busfinder.openLink
import kotlinx.coroutines.delay

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF4DB6AC))
        else -> lightColorScheme(primary = Color(0xFF00796B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val LiveGreen = Color(0xFF2E7D32)
val LateRed = Color(0xFFC62828)

fun kindColor(k: Kind): Color = when (k) {
    Kind.BUS -> Color(0xFFD32F2F)
    Kind.TRAIN -> Color(0xFF1565C0)
    Kind.METRO -> Color(0xFF283593)
    Kind.TRAM -> Color(0xFF2E7D32)
    Kind.FERRY -> Color(0xFF00838F)
    Kind.PLANE -> Color(0xFF6A1B9A)
    Kind.WALK -> Color(0xFF616161)
    Kind.BIKE -> Color(0xFF558B2F)
    Kind.CAR -> Color(0xFF455A64)
    Kind.OTHER -> Color(0xFF5D4037)
}

/** Coloured badge like "🚌 42". */
@Composable
fun LineBadge(kind: Kind, line: String) {
    Surface(color = kindColor(kind), shape = RoundedCornerShape(6.dp)) {
        Text(
            "${kind.emoji} ${line.ifEmpty { kind.label }}".trim(),
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Title(title: String, sub: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Loading(text: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Text(text, Modifier.padding(start = 16.dp))
    }
}

@Composable
fun ErrorText(text: String) {
    Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
}

/** Required credit for the open data (Transitous policy, OpenStreetMap, TfL). */
@Composable
fun DataCredit(tfl: Boolean = false) {
    val ctx = LocalContext.current
    Text(
        "Timetables: Transitous & open operator data · © OpenStreetMap contributors" + if (tfl) " · Powered by TfL Open Data" else "",
        Modifier.fillMaxWidth().clickable { ctx.openLink(Transitous.SOURCES_URL) }.padding(12.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Text field with place/stop suggestions as you type. */
@Composable
fun PlaceSearch(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onPick: (GeoMatch) -> Unit,
    stopsOnly: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var results by remember { mutableStateOf<List<GeoMatch>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(value) {
        if (value.length < 3 || value == picked) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        results = try {
            Transitous.geocode(value).let { l -> if (stopsOnly) l.filter { it.isStop } else l }.take(8)
        } catch (e: Exception) { emptyList() }
    }
    Column(modifier) {
        OutlinedTextField(
            value, { onValue(it); focused = true }, Modifier.fillMaxWidth(),
            label = { Text(label) }, singleLine = true, leadingIcon = leading, trailingIcon = trailing
        )
        if (focused && results.isNotEmpty()) Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(8.dp)) {
            Column {
                results.forEach { m ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            picked = m.name; focused = false; results = emptyList(); onPick(m)
                        }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text((if (m.isStop) "🚏 " else "📍 ") + m.name, style = MaterialTheme.typography.bodyMedium)
                        if (m.area.isNotEmpty()) Text(m.area, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
