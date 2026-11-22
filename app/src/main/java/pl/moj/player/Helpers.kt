package pl.moj.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import java.io.File

fun buildItem(ctx: Context, uri: String): MediaItem {
    val m = Store.metaFor(ctx, uri)
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(m.title)
                .setArtist(m.artist)
                .setArtworkUri(m.art?.let { Uri.fromFile(File(it)) })
                .build()
        ).build()
}

/** Zapamiętuje uprawnienie do pliku wybranego przez systemowy wybieracz plików. */
fun persistRead(ctx: Context, uri: Uri) {
    try {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) { }
}

/** Ustawia kolejkę i zaczyna grać od wskazanego indeksu. */
fun MediaController.playQueue(ctx: Context, uris: List<String>, start: Int = 0) {
    setMediaItems(uris.map { buildItem(ctx, it) }, start, 0L)
    prepare()
    play()
}

fun MediaController.togglePlay() {
    if (isPlaying) {
        pause()
    } else {
        // Po dojściu do końca utworu "play" odtwarza go od początku (nie przeskakuje do następnego).
        if (playbackState == Player.STATE_ENDED || (duration > 0 && currentPosition >= duration - 300)) seekTo(0)
        play()
    }
}

/** Odświeża notyfikację/sesję po edycji metadanych utworu, nie gubiąc pozycji. */
fun MediaController.refreshItem(ctx: Context, uri: String) {
    for (i in 0 until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == uri) {
            val wasCurrent = i == currentMediaItemIndex
            val pos = currentPosition
            replaceMediaItem(i, buildItem(ctx, uri))
            if (wasCurrent) seekTo(i, pos)
        }
    }
}

/** Stan odtwarzacza obserwowalny przez Compose. */
class PlayerUi(val c: MediaController) {
    var isPlaying by mutableStateOf(c.isPlaying)
    var currentId by mutableStateOf<String?>(c.currentMediaItem?.mediaId)
    var queue by mutableStateOf(readQueue())
    var index by mutableIntStateOf(c.currentMediaItemIndex)
    var canNext by mutableStateOf(c.hasNextMediaItem())
    var canPrev by mutableStateOf(c.hasPreviousMediaItem())

    private fun readQueue() = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }

    fun sync() {
        isPlaying = c.isPlaying
        currentId = c.currentMediaItem?.mediaId
        queue = readQueue()
        index = c.currentMediaItemIndex
        canNext = c.hasNextMediaItem()
        canPrev = c.hasPreviousMediaItem()
    }
}

@Composable
fun rememberPlayerUi(c: MediaController?): PlayerUi? {
    if (c == null) return null
    val ui = remember(c) { PlayerUi(c) }
    DisposableEffect(c) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = ui.sync()
        }
        c.addListener(l)
        ui.sync()
        onDispose { c.removeListener(l) }
    }
    return ui
}

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
