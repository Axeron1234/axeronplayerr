package pl.moj.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dane utworu zapisane w aplikacji (nadpisują tagi z pliku; sam plik audio nie jest zmieniany). */
data class TrackMeta(
    val uri: String,
    val title: String,
    val artist: String,
    val art: String?,   // ścieżka do pliku obrazka w pamięci aplikacji
    val lrc: String     // tekst z czasami, np. [01:21.56] zdanie
)

class Playlist(val name: String) {
    val uris = mutableStateListOf<String>()
}

data class LyricLine(val timeMs: Long, val text: String)

object Store {
    val metas = mutableStateMapOf<String, TrackMeta>()
    val playlists = mutableStateListOf<Playlist>()

    private fun file(ctx: Context) = File(ctx.filesDir, "store.json")

    fun load(ctx: Context) {
        val f = file(ctx)
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            metas.clear()
            val m = root.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until m.length()) {
                val o = m.getJSONObject(i)
                metas[o.getString("uri")] = TrackMeta(
                    o.getString("uri"), o.getString("title"), o.getString("artist"),
                    if (o.isNull("art")) null else o.getString("art"), o.optString("lrc", "")
                )
            }
            playlists.clear()
            val p = root.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until p.length()) {
                val o = p.getJSONObject(i)
                val pl = Playlist(o.getString("name"))
                val arr = o.getJSONArray("uris")
                for (j in 0 until arr.length()) pl.uris.add(arr.getString(j))
                playlists.add(pl)
            }
        } catch (_: Exception) { /* uszkodzony plik – zaczynamy od zera */ }
    }

    fun save(ctx: Context) {
        val root = JSONObject()
        val m = JSONArray()
        metas.values.forEach {
            m.put(JSONObject().put("uri", it.uri).put("title", it.title).put("artist", it.artist)
                .put("art", it.art ?: JSONObject.NULL).put("lrc", it.lrc))
        }
        val p = JSONArray()
        playlists.forEach { pl ->
            p.put(JSONObject().put("name", pl.name).put("uris", JSONArray(pl.uris.toList())))
        }
        root.put("tracks", m).put("playlists", p)
        file(ctx).writeText(root.toString())
    }

    /** Zwraca zapisane metadane albo tworzy je z tagów pliku (tytuł, wykonawca, okładka). */
    fun metaFor(ctx: Context, uri: String): TrackMeta {
        metas[uri]?.let { return it }
        var title = Uri.parse(uri).lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Utwór"
        var artist = "Nieznany wykonawca"
        var art: String? = null
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(ctx, Uri.parse(uri))
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            r.embeddedPicture?.let { bytes ->
                val dir = File(ctx.filesDir, "art").apply { mkdirs() }
                val out = File(dir, "emb_${uri.hashCode()}.jpg")
                out.writeBytes(bytes)
                art = out.absolutePath
            }
            r.release()
        } catch (_: Exception) { }
        val meta = TrackMeta(uri, title, artist, art, "")
        metas[uri] = meta
        save(ctx)
        return meta
    }

    fun update(ctx: Context, meta: TrackMeta) {
        metas[meta.uri] = meta
        save(ctx)
    }

    /** Kopiuje wybrany obrazek do pamięci aplikacji i zwraca jego ścieżkę. */
    fun importImage(ctx: Context, src: Uri, key: String): String? = try {
        val dir = File(ctx.filesDir, "art").apply { mkdirs() }
        val out = File(dir, "img_${key.hashCode()}_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(src)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        out.absolutePath
    } catch (_: Exception) { null }
}

/**
 * Parser tekstu z czasami. Akceptuje [mm:ss.xx], [mm.ss.xx] i [mm:ss]
 * (np. [01.21.56] = 1 min, 21 s, 560 ms). Linie bez znacznika są pomijane.
 */
private val LRC_REGEX = Regex("""^\s*\[(\d{1,3})[.:](\d{1,2})(?:[.:](\d{1,3}))?]\s*(.*)$""")

fun parseLyrics(raw: String): List<LyricLine> =
    raw.lines().mapNotNull { line ->
        val m = LRC_REGEX.matchEntire(line) ?: return@mapNotNull null
        val (min, sec, frac, text) = m.destructured
        val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.toLong() }
        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, text)
    }.sortedBy { it.timeMs }
