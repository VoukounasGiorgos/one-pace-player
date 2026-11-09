package gr.george.onepace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.george.onepace.data.Arc
import gr.george.onepace.data.Episode
import gr.george.onepace.data.Library
import gr.george.onepace.data.Progress
import gr.george.onepace.data.Thumbnails
import gr.george.onepace.data.continueTarget

class HomeActions(
    val onPlay: (Episode, Boolean) -> Unit,
    val onToggleWatched: (Episode) -> Unit,
    val onMarkUntil: (Episode) -> Unit,
    val onRescan: () -> Unit,
    val onChangeFolder: () -> Unit,
)

@Composable
fun HomeScreen(
    library: Library,
    progress: Map<String, Progress>,
    scanning: Boolean,
    actions: HomeActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item(key = "top") { TopBar(scanning, actions) }

        if (library.allEpisodes.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Δεν βρέθηκαν βίντεο σε αυτόν τον φάκελο.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = actions.onChangeFolder) { Text("Επιλογή άλλου φακέλου") }
                }
            }
        } else {
            val target = continueTarget(library, progress)
            if (target != null) {
                item(key = "hero") {
                    Hero(target.episode, target.label, progress[target.episode.id], actions)
                }
            }
            items(library.arcs, key = { "arc:" + it.name }) { arc ->
                ArcRow(arc, progress, actions)
            }
        }
    }
}

@Composable
private fun TopBar(scanning: Boolean, actions: HomeActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ONE PACE",
            color = Accent,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        if (scanning) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Ρυθμίσεις")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ανανέωση βιβλιοθήκης") },
                    onClick = { menu = false; actions.onRescan() },
                )
                DropdownMenuItem(
                    text = { Text("Αλλαγή φακέλου") },
                    onClick = { menu = false; actions.onChangeFolder() },
                )
            }
        }
    }
}

@Composable
private fun Hero(ep: Episode, label: String, p: Progress?, actions: HomeActions) {
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { actions.onPlay(ep, false) },
    ) {
        EpisodeThumb(ep, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.3f to Color.Transparent,
                        1f to Color(0xF0000000),
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                label.uppercase(),
                color = Accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ep.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(ep.arc, color = Color.White.copy(alpha = 0.7f))
            if (p != null && p.inProgress) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { p.fraction },
                    modifier = Modifier
                        .width(260.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                val minutesLeft = ((p.durationMs - p.positionMs) / 60_000).coerceAtLeast(1)
                Text(
                    "Απομένουν $minutesLeft λεπτά",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { actions.onPlay(ep, false) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Παίξε")
            }
        }
    }
}

@Composable
private fun ArcRow(arc: Arc, progress: Map<String, Progress>, actions: HomeActions) {
    val watched = arc.episodes.count { progress[it.id]?.watched == true }
    Column(Modifier.padding(top = 26.dp)) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                arc.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text("$watched/${arc.episodes.size}", color = Muted)
            if (watched == arc.episodes.size) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Ολοκληρώθηκε",
                    tint = Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(arc.episodes, key = { it.id }) { ep ->
                EpisodeCard(ep, progress[ep.id], actions)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(ep: Episode, p: Progress?, actions: HomeActions) {
    var menu by remember { mutableStateOf(false) }
    val watched = p?.watched == true
    Box {
        Column(
            Modifier
                .width(230.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = { actions.onPlay(ep, false) },
                    onLongClick = { menu = true },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                EpisodeThumb(ep, Modifier.fillMaxSize())
                if (watched) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000))
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Ειδωμένο",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (p != null && p.inProgress) {
                    LinearProgressIndicator(
                        progress = { p.fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Accent,
                        trackColor = Color(0x55FFFFFF),
                    )
                }
                if (ep.subtitles.isNotEmpty()) {
                    Text(
                        "CC",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0x99000000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                ep.title,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (watched) Muted else Color.White,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (watched) "Σήμανση ως μη ειδωμένο" else "Σήμανση ως ειδωμένο") },
                onClick = { menu = false; actions.onToggleWatched(ep) },
            )
            DropdownMenuItem(
                text = { Text("Ειδωμένα όλα μέχρι εδώ") },
                onClick = { menu = false; actions.onMarkUntil(ep) },
            )
            DropdownMenuItem(
                text = { Text("Παίξε από την αρχή") },
                onClick = { menu = false; actions.onPlay(ep, true) },
            )
        }
    }
}

@Composable
fun EpisodeThumb(ep: Episode, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val image: ImageBitmap? by produceState(initialValue = Thumbnails.cached(ep.id), ep.id) {
        if (value == null) value = Thumbnails.load(context, ep)
    }
    Box(modifier.background(CardBg), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color(0x55FFFFFF),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
fun SetupScreen(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ONE PACE",
            color = Accent,
            fontWeight = FontWeight.Black,
            fontSize = 40.sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Διάλεξε τον φάκελο όπου έχεις τα επεισόδια.\n" +
                "Κάθε υποφάκελος γίνεται ένα arc. Οι υπότιτλοι (.srt/.ass/.vtt) " +
                "φορτώνονται αυτόματα όταν έχουν το ίδιο όνομα με το βίντεο.",
            textAlign = TextAlign.Center,
            color = Muted,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPick) { Text("Επιλογή φακέλου") }
    }
}

@Composable
fun StatusScreen(scanning: Boolean, error: String?, onRetry: () -> Unit, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error != null && !scanning) {
            Text("Δεν μπόρεσα να διαβάσω τον φάκελο.", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(error, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Δοκίμασε ξανά") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPick) { Text("Επιλογή φακέλου") }
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Σάρωση επεισοδίων…", color = Muted)
        }
    }
}
