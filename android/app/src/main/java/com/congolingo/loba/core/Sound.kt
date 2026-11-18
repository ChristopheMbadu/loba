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
    /** How many game screens are showing, and whether the app is in front. */
    private var games = 0
    private var front = true

    private fun load() {
        if (loaded) return
        loaded = true
        on = Prefs.store.getBoolean(key, true)
    }

    private fun sync() {
        load()
        val wanted = on && games > 0 && front
        try {
            if (wanted) {
                val p = player ?: MediaPlayer.create(appContext, R.raw.game_loop)?.apply {
                    isLooping = true
                    // Behind the game's own sounds, not over them.
                    setVolume(0.32f, 0.32f)
                }.also { player = it }
                if (p != null && !p.isPlaying) p.start()
            } else if (games == 0) {
                player?.release()
                player = null
            } else {
                player?.takeIf { it.isPlaying }?.pause()
            }
        } catch (_: Exception) {
            player = null
        }
    }

    /** Whether it is on, read before any game opens (for the settings switch). */
    val enabled: Boolean get() { load(); return on }

    fun toggle() {
        load()
        on = !on
        Prefs.store.edit().putBoolean(key, on).apply()
        sync()
    }

    fun enter() { games += 1; sync() }
    fun leave() { games = maxOf(0, games - 1); sync() }
    fun foreground(now: Boolean) { front = now; sync() }
}

/** A teacher's recording of a word. */
object Voice {
    private var player: MediaPlayer? = null

    fun say(word: Word) {
        val id = word.audio
        if (!AppStore.state.voice || id == null) return
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(Api.audioURL(id))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.release(); if (player === it) player = null }
                setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; true }
                prepareAsync()
            }
        } catch (_: Exception) {
            player = null
        }
    }

    fun stop() {
        try { player?.release() } catch (_: Exception) {}
        player = null
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
