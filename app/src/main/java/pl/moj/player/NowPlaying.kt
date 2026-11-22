package pl.moj.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ui.c
    val meta = ui.currentId?.let { Store.metas[it] }

    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(c) {
        while (true) {
            pos = c.currentPosition
            dur = if (c.duration == C.TIME_UNSET) 0L else c.duration
            delay(80) // wystarczająco gęsto dla tekstu z setnymi sekundy
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Wróć") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showEdit = true }, enabled = meta != null) {
                Icon(Icons.Default.Edit, "Edytuj")
            }
        }

        // Obrazek utworu
        Box(
            Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (meta?.art != null) {
                AsyncImage(File(meta.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.Default.MusicNote, null, Modifier.padding(60.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(meta?.title ?: "—", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta?.artist ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)

        // Tekst na żywo: pod obrazkiem, nad przyciskami
        LyricsView(meta?.lrc ?: "", pos, Modifier.fillMaxWidth().height(110.dp))

        Slider(
            value = if (dragging) dragValue else pos.toFloat(),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { c.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..(dur.coerceAtLeast(1L)).toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(if (dragging) dragValue.toLong() else pos), fontSize = 12.sp)
            Text(fmtTime(dur), fontSize = 12.sp)
        }

        // Sterowanie
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { c.seekToPreviousMediaItem() }, enabled = ui.canPrev) {
                Icon(Icons.Default.SkipPrevious, "Poprzedni", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = { c.togglePlay() }, Modifier.size(68.dp)) {
                // Trójkąt = start, dwie pionowe kreski = stop
                Icon(
                    if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (ui.isPlaying) "Stop" else "Start", Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { c.seekToNextMediaItem() }, enabled = ui.canNext) {
                Icon(Icons.Default.SkipNext, "Następny", Modifier.size(36.dp))
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Default.QueueMusic, "Kolejka", Modifier.size(30.dp))
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) { QueueSheet(ui) }
    }
    if (showEdit && meta != null) {
        EditDialog(meta, onDismiss = { showEdit = false }) { updated ->
            Store.update(ctx, updated)
            c.refreshItem(ctx, updated.uri)
            showEdit = false
        }
    }
}

@Composable
fun LyricsView(lrc: String, posMs: Long, modifier: Modifier = Modifier) {
    val lines = remember(lrc) { parseLyrics(lrc) }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (lines.isEmpty()) {
            Text("Brak tekstu — dodaj go przyciskiem edycji ✎", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            return@Column
        }
        val cur = lines.indexOfLast { it.timeMs <= posMs }
        fun line(i: Int) = lines.getOrNull(i)?.text.orEmpty()
        Text(if (cur >= 1) line(cur - 1) else "", fontSize = 13.sp, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        Text(if (cur >= 0) line(cur) else "", fontSize = 19.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
        Text(line(cur + 1), fontSize = 13.sp, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
    }
}

@Composable
fun QueueSheet(ui: PlayerUi) {
    val ctx = LocalContext.current
    val c = ui.c
    var showLib by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            persistRead(ctx, u)
            c.addMediaItem(buildItem(ctx, u.toString()))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Kolejka (${ui.queue.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Dodaj z urządzenia") }
            OutlinedButton(onClick = { showLib = true }) { Text("Dodaj z biblioteki") }
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(ui.queue) { i, id ->
                val m = Store.metas[id]
                Row(
                    Modifier.fillMaxWidth().clickable { c.seekToDefaultPosition(i); c.play() }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m?.title ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (i == ui.index) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == ui.index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                        Text(m?.artist ?: "", fontSize = 12.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { c.removeMediaItem(i) }) { Icon(Icons.Default.Close, "Usuń z kolejki") }
                }
            }
        }
    }

    if (showLib) {
        AlertDialog(
            onDismissRequest = { showLib = false },
            confirmButton = { TextButton(onClick = { showLib = false }) { Text("Zamknij") } },
            title = { Text("Biblioteka") },
            text = {
                LazyColumn {
                    itemsIndexed(Store.metas.values.toList()) { _, m ->
                        Text("${m.title} — ${m.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { c.addMediaItem(buildItem(ctx, m.uri)) }
                                .padding(vertical = 10.dp))
                    }
                }
            }
        )
    }
}

@Composable
fun EditDialog(meta: TrackMeta, onDismiss: () -> Unit, onSave: (TrackMeta) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(meta.title) }
    var artist by remember { mutableStateOf(meta.artist) }
    var art by remember { mutableStateOf(meta.art) }
    var lrc by remember { mutableStateOf(meta.lrc) }

    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) Store.importImage(ctx, u, meta.uri)?.let { art = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj utwór") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nazwa") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Wykonawca") }, singleLine = true)
                OutlinedButton(onClick = { imgPicker.launch(arrayOf("image/*")) }) {
                    Text(if (art != null) "Zmień obrazek (wybrano)" else "Wybierz obrazek")
                }
                OutlinedTextField(
                    lrc, { lrc = it }, label = { Text("Tekst z czasami") },
                    placeholder = { Text("[00:12.50] Pierwsza linijka\n[00:17.00] Druga linijka") },
                    minLines = 5, maxLines = 8
                )
                Text("Format: [minuty:sekundy.setne] tekst — też [01.21.56]", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(meta.copy(title = title.ifBlank { meta.title }, artist = artist, art = art, lrc = lrc))
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
