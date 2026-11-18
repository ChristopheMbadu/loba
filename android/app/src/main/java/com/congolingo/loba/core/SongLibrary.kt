package com.congolingo.loba.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONObject

/** Shares the Studio catalog, but explicit listening is separate from ambient music. */
data class LibrarySong(val id: String, val title: String) {
    val artwork: Int get() = id.toByteArray(Charsets.UTF_8).fold(0) { sum, c -> (sum + c.toInt()) % 6 }
}
class SongLibrary {
    var songs by mutableStateOf<List<LibrarySong>>(emptyList()); private set
    var selected by mutableStateOf<LibrarySong?>(null); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf(false); private set
    var elapsed by mutableStateOf(0f); private set
    var duration by mutableStateOf(0f); private set
    var repeatSong by mutableStateOf(false)
    var favorites by mutableStateOf(Prefs.store.getStringSet("loba-favorite-songs", emptySet())?.toSet() ?: emptySet()); private set
    private var player: MediaPlayer? = null
    private var ready = false
    private var opened = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change -> if (change != AudioManager.AUDIOFOCUS_GAIN) pause() }.build()
    private val noisy = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { pause() } }

    suspend fun open() {
        if (opened) return
        opened = true
        Voice.stop(); Music.library(true)
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") appContext.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        scope.launch {
            while (isActive) {
                val p = player
                if (ready && p != null) try { elapsed = p.currentPosition / 1000f; duration = p.duration.coerceAtLeast(0) / 1000f } catch (_: Exception) {}
                delay(250)
            }
        }
        load()
    }
    suspend fun load() {
        loading = true; error = false
        try {
            val data = Api.fetch(Api.url("/api/v1/music")) ?: throw IllegalStateException()
            val rows = JSONObject(String(data, Charsets.UTF_8)).getJSONArray("tracks")
            val result = (0 until rows.length()).mapNotNull { i ->
                val row = rows.getJSONObject(i); val id = row.getString("id")
                if (id.matches(Regex("[0-9a-f-]{36}"))) LibrarySong(id,row.getString("title")) else null
            }
            if (opened) {
                songs = result
                if (selected != null && songs.none { it.id == selected?.id }) { pause(); release(); selected = null }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (opened) error = true }
        finally { loading = false }
    }
    fun favorite(song: LibrarySong) {
        favorites = if (song.id in favorites) favorites - song.id else favorites + song.id
        Prefs.store.edit().putStringSet("loba-favorite-songs",favorites).apply()
    }
    private fun release() { try { player?.release() } catch (_: Exception) {}; player = null; ready = false }
    fun play(song: LibrarySong) {
        if (!opened) return
        release(); selected = song; error = false; elapsed = 0f; duration = 0f; buffering = true; playing = true
        try {
            val p = MediaPlayer(); player = p
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setVolume(0.7f,0.7f)
            p.setDataSource(Api.url("/api/v1/music/${song.id}"))
            p.setOnPreparedListener {
                if (opened && player === it) {
                    ready = true; buffering = false; duration = it.duration.coerceAtLeast(0) / 1000f
                    if (playing) start()
                }
            }
            p.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) buffering = playing
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) buffering = false
                false
            }
            p.setOnCompletionListener { if (opened && player === it) { if (repeatSong) { it.seekTo(0); start() } else next() } }
            p.setOnErrorListener { mp, _, _ -> if (player === mp) { pause(); release(); error = true }; true }
            p.prepareAsync()
        } catch (_: Exception) { pause(); release(); error = true }
    }
    private fun start() {
        try {
            if (manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { player?.start(); playing = true }
            else { playing = false; buffering = false }
        } catch (_: Exception) { playing = false; buffering = false; error = true }
    }
    fun pause() { playing = false; buffering = false; try { if (ready) player?.pause() } catch (_: Exception) {} }
    fun toggle() {
        if (playing) pause()
        else if (selected != null) { if (error || player == null) play(selected!!) else if (ready) start() else { playing = true; buffering = true } }
        else songs.firstOrNull()?.let(::play)
    }
    fun seek(seconds: Float) { if (ready) { elapsed = seconds.coerceIn(0f,duration); try { player?.seekTo((elapsed*1000).toInt()) } catch (_: Exception) {} } }
    fun next(direction: Int = 1) {
        if (songs.isEmpty()) return
        val current = songs.indexOfFirst { it.id == selected?.id }.let { if (it < 0 && direction < 0) 0 else it }
        play(songs[(current + direction + songs.size) % songs.size])
    }
    fun surprise() { (if (songs.size > 1) songs.filter { it.id != selected?.id } else songs).randomOrNull()?.let(::play) }
    fun close() {
        if (!opened) return
        opened = false; pause(); release(); scope.cancel(); manager.abandonAudioFocusRequest(focus)
        try { appContext.unregisterReceiver(noisy) } catch (_: Exception) {}
        Music.library(false)
    }
    companion object { fun clock(seconds: Float): String { val s=if(seconds.isFinite())seconds.toInt().coerceAtLeast(0) else 0;return "%d:%02d".format(s/60,s%60) } }
}
