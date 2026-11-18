package com.congolingo.loba.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.congolingo.loba.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/** Loba's sounds: the same likembe, ngoma and shaker cues as the website, recorded from its synthesizer. */
object Sfx {
    enum class Cue {
        TAP, POP, WHOOSH, WRONG, HEART, COIN, CHEST, TICK, COMPLETE, STREAK, SPLASH, CALL, DRUM, SHAKER,
        /** These change with `n`: the note in a tune, the length of a combo, the page of a story. */
        TILE, CORRECT, PAGE,
    }

    // Loading a sound takes a while: far too long for the main thread, where it would freeze the screen on a tap.
    private val queue = Executors.newSingleThreadExecutor()
    private val ids = ConcurrentHashMap<String, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val waiting = ConcurrentHashMap.newKeySet<Int>()

    private val pool: SoundPool by lazy {
        // Game sounds follow the media volume and mix with the learner's music.
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attributes).build().apply {
            setOnLoadCompleteListener { p, id, status ->
                if (status != 0) return@setOnLoadCompleteListener
                ready += id
                if (waiting.remove(id)) p.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    private fun sound(name: String): Int? = ids[name] ?: run {
        val res = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        if (res == 0) null else pool.load(appContext, res, 1).also { ids[name] = it }
    }

    /** Loads the sounds heard on almost every screen, so the first tap is not late. */
    fun warmUp() {
        queue.execute {
            for (name in listOf("tap", "pop", "tile0", "tile1", "tile2", "tile3", "tile4", "tile5", "correct1", "whoosh", "tick")) sound(name)
        }
    }

    fun play(cue: Cue, n: Int = 1) {
        if (!AppStore.state.sound) return
        val name = when (cue) {
            Cue.TILE -> "tile${((n % 6) + 6) % 6}"
            Cue.CORRECT -> "correct${n.coerceIn(1, 5)}"
            Cue.PAGE -> "page${((n % 8) + 8) % 8}"
            else -> cue.name.lowercase()
        }
        queue.execute {
            val id = sound(name) ?: return@execute
            if (id in ready) pool.play(id, 1f, 1f, 1, 0, 1f) else waiting += id
        }
    }
}

/**
 * The tune that plays behind the games (the map, the word hunt). It loops while a game is on screen,
 * stops when the app leaves the foreground, and the learner can turn it off: the choice stays on this phone.
 */
object Music {
    private const val key = "loba-music"
    var on by mutableStateOf(true)
        private set
    private var loaded = false
    private var player: MediaPlayer? = null
    private var ready = false
    private var games = 0
    private var front = true
    private var speaking = false
    private var interrupted = false
    private var tracks = listOf<String>()
    private val remaining = mutableListOf<String>()
    private var current = ""
    private var fetched = 0L
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val audioManager by lazy { appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    private val focus by lazy {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                interrupted = change != android.media.AudioManager.AUDIOFOCUS_GAIN
                if (interrupted) player?.takeIf { ready && it.isPlaying }?.pause() else sync()
            }.build()
    }
    private fun load() {
        if (loaded) return
        loaded = true
        on = Prefs.store.getBoolean(key, true)
    }
    private fun wanted() = on && games > 0 && front && !speaking && !interrupted
    private fun refresh() {
        if (System.currentTimeMillis() - fetched < 60_000) return
        fetched = System.currentTimeMillis()
        scope.launch {
            val data = Api.fetch(Api.url("/api/v1/music")) ?: return@launch
            try {
                val rows = org.json.JSONObject(String(data, Charsets.UTF_8)).getJSONArray("tracks")
                tracks = (0 until rows.length()).mapNotNull { index ->
                    rows.getJSONObject(index).optString("id").takeIf { it.matches(Regex("[0-9a-f-]{36}")) }
                }
                remaining.clear()
                if (current !in tracks) release()
                sync()
            } catch (_: Exception) { /* Learning still works without music. */ }
        }
    }
    private fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null; ready = false
    }
    private fun next() {
        if (!wanted() || tracks.isEmpty()) return
        if (remaining.isEmpty()) {
            remaining.addAll(tracks.shuffled())
            if (remaining.size > 1 && remaining.first() == current) remaining.add(remaining.removeAt(0))
        }
        release()
        current = remaining.removeAt(0)
        try {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setVolume(0.16f, 0.16f)
            p.setDataSource(Api.url("/api/v1/music/$current"))
            p.setOnPreparedListener { if (player === it) { ready = true; sync() } }
            p.setOnCompletionListener { if (player === it) next() }
            p.setOnErrorListener { mp, _, _ -> if (player === mp) release(); true }
            p.prepareAsync()
        } catch (_: Exception) { release() }
    }
    private fun sync() {
        load()
        try {
            if (wanted()) {
                if (player == null) next()
                else if (ready && player?.isPlaying == false && audioManager.requestAudioFocus(focus) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player?.start()
            } else {
                if (ready) player?.takeIf { it.isPlaying }?.pause()
                audioManager.abandonAudioFocusRequest(focus)
            }
        } catch (_: Exception) { release() }
    }
    val enabled: Boolean get() { load(); return on }
    fun toggle() { load(); on = !on; interrupted = false; Prefs.store.edit().putBoolean(key,on).apply(); if(on)refresh(); sync() }
    fun enter() { games += 1; refresh(); sync() }
    fun leave() { games = maxOf(0,games-1); sync() }
    fun foreground(now: Boolean) { front = now; if(now)refresh(); sync() }
    fun voice(now: Boolean) { speaking = now; sync() }
}

/** A teacher's recording of a word. */
object Voice {
    private var player: MediaPlayer? = null

    fun say(word: Word) {
        val id = word.audio
        if (!AppStore.state.voice || id == null) return
        stop()
        Music.voice(true)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(Api.audioURL(id))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.release(); if (player === it) { player = null; Music.voice(false) } }
                setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) { player = null; Music.voice(false) }; true }
                prepareAsync()
            }
        } catch (_: Exception) {
            player = null
            Music.voice(false)
        }
    }

    fun stop() {
        try { player?.release() } catch (_: Exception) {}
        player = null
        Music.voice(false)
    }
}

object Haptic {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    private fun buzz(effect: Int, fallbackMs: Long, pattern: LongArray? = null) {
        if (!AppStore.state.sound) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29 && pattern == null) v.vibrate(VibrationEffect.createPredefined(effect))
            else if (pattern != null) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    fun tap() = buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_TICK else 0, 10)
    fun thud() = buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_CLICK else 0, 20)
    fun success() = buzz(0, 0, longArrayOf(0, 25, 70, 35))
    fun error() = buzz(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.EFFECT_DOUBLE_CLICK else 0, 40)
}
