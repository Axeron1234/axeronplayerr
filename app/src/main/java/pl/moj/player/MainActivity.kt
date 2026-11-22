package pl.moj.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import java.io.File

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(controller) }
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(controller: MediaController?) {
    val ctx = LocalContext.current
    val ui = rememberPlayerUi(controller)
    var tab by remember { mutableIntStateOf(0) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    // Wybór plików audio z urządzenia: dodaje do biblioteki i od razu odtwarza pierwszy.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty() || controller == null) return@rememberLauncherForActivityResult
        val list = uris.map { u -> persistRead(ctx, u); u.toString() }
        list.forEach { Store.metaFor(ctx, it) }
        controller.playQueue(ctx, list, 0)
        showPlayer = true
    }

    // Po włączeniu aplikacji od razu otwórz wybieracz pliku (gdy nic jeszcze nie ma w bibliotece).
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller) {
        if (controller != null && !asked && Store.metas.isEmpty()) {
            asked = true
            pick.launch(arrayOf("audio/*"))
        }
    }

    BackHandler(enabled = showPlayer || openPlaylist != null) {
        if (showPlayer) showPlayer = false else openPlaylist = null
    }

    if (showPlayer && ui != null) {
        NowPlayingScreen(ui) { showPlayer = false }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(openPlaylist?.name ?: "Mój Odtwarzacz") }, navigationIcon = {
                if (openPlaylist != null) IconButton(onClick = { openPlaylist = null }) {
                    Icon(Icons.Default.ArrowBack, "Wróć")
                }
            })
        },
        bottomBar = {
            Column {
                if (ui != null && ui.currentId != null) MiniBar(ui) { showPlayer = true }
                if (openPlaylist == null) NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Biblioteka") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.QueueMusic, null) }, label = { Text("Playlisty") })
                }
            }
        },
        floatingActionButton = {
            if (openPlaylist == null && tab == 0) ExtendedFloatingActionButton(
                onClick = { pick.launch(arrayOf("audio/*")) },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Wybierz plik audio") }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            val pl = openPlaylist
            when {
                pl != null -> PlaylistDetail(pl, controller) { showPlayer = true }
                tab == 0 -> LibraryScreen(controller) { showPlayer = true }
                else -> PlaylistsScreen { openPlaylist = it }
            }
        }
    }
}

@Composable
fun TrackRow(m: TrackMeta, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
            if (m.art != null) AsyncImage(File(m.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Default.MusicNote, null, Modifier.padding(12.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun MiniBar(ui: PlayerUi, onOpen: () -> Unit) {
    val m = ui.currentId?.let { Store.metas[it] } ?: return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artist, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { ui.c.togglePlay() }) {
                Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@Composable
fun LibraryScreen(controller: MediaController?, openPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val tracks = Store.metas.values.sortedBy { it.title.lowercase() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var addTo by remember { mutableStateOf<TrackMeta?>(null) }

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Wybierz plik audio, żeby zacząć", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tracks, key = { it.uri }) { m ->
            TrackRow(m, onClick = {
                controller?.playQueue(ctx, listOf(m.uri)); openPlayer()
            }) {
                Box {
                    IconButton(onClick = { menuFor = m.uri }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(menuFor == m.uri, { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Dodaj do kolejki") }, onClick = {
                            menuFor = null
                            controller?.let { c ->
                                if (c.mediaItemCount == 0) c.playQueue(ctx, listOf(m.uri))
                                else c.addMediaItem(buildItem(ctx, m.uri))
                            }
                        })
                        DropdownMenuItem(text = { Text("Dodaj do playlisty") }, onClick = {
                            menuFor = null; addTo = m
                        })
                    }
                }
            }
        }
    }

    addTo?.let { m ->
        AlertDialog(
            onDismissRequest = { addTo = null },
            confirmButton = { TextButton(onClick = { addTo = null }) { Text("Zamknij") } },
            title = { Text("Dodaj do playlisty") },
            text = {
                if (Store.playlists.isEmpty()) Text("Najpierw utwórz playlistę w zakładce Playlisty.")
                else LazyColumn {
                    items(Store.playlists.toList()) { pl ->
                        Text(pl.name, Modifier.fillMaxWidth().clickable {
                            if (m.uri !in pl.uris) pl.uris.add(m.uri)
                            Store.save(ctx); addTo = null
                        }.padding(vertical = 12.dp))
                    }
                }
            }
        )
    }
}

@Composable
fun PlaylistsScreen(onOpen: (Playlist) -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = { name = ""; creating = true }, Modifier.padding(16.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nowa playlista")
        }
        LazyColumn {
            items(Store.playlists.toList()) { pl ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(pl) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.name)
                        Text("${pl.uris.size} utw.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Store.playlists.remove(pl); Store.save(ctx) }) {
                        Icon(Icons.Default.Delete, "Usuń playlistę")
                    }
                }
            }
        }
    }
    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        title = { Text("Nowa playlista") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) { Store.playlists.add(Playlist(name.trim())); Store.save(ctx) }
                creating = false
            }) { Text("Utwórz") }
        },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Anuluj") } }
    )
}

@Composable
fun PlaylistDetail(pl: Playlist, controller: MediaController?, openPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            persistRead(ctx, u)
            val s = u.toString()
            Store.metaFor(ctx, s)
            if (s !in pl.uris) pl.uris.add(s)
        }
        Store.save(ctx)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = pl.uris.isNotEmpty(), onClick = {
                controller?.playQueue(ctx, pl.uris.toList(), 0); openPlayer()
            }) { Icon(Icons.Default.PlayArrow, null); Text("Odtwarzaj") }
            OutlinedButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Text("Dodaj z urządzenia") }
        }
        LazyColumn {
            items(pl.uris.toList()) { uri ->
                val m = Store.metaFor(ctx, uri)
                TrackRow(m, onClick = {
                    controller?.playQueue(ctx, pl.uris.toList(), pl.uris.indexOf(uri)); openPlayer()
                }) {
                    IconButton(onClick = { pl.uris.remove(uri); Store.save(ctx) }) {
                        Icon(Icons.Default.Close, "Usuń z playlisty")
                    }
                }
            }
        }
    }
}
