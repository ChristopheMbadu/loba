package com.congolingo.loba.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.UiLang
import com.congolingo.loba.core.Word
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private enum class GuessPhase { INTRO, POSITIONING, COUNTDOWN, PLAYING, RECAP }

private tailrec fun Context.guessActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.guessActivity()
    else -> null
}

/** Devine le mot: a timed round with the phone on the forehead. Tilt it down for found, up to pass. */
@Composable
fun GuessWordGame(words: List<Word>, ui: UiLang, notebook: String, onClose: () -> Unit) {
    var source by remember { mutableIntStateOf(0) }
    var editBook by remember { mutableStateOf(false) }
    var personal by remember { mutableStateOf(listOf<Word>()) }
    var deck by remember { mutableStateOf(listOf<Word>()) }
    var index by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf(listOf<Word>()) }
    var phase by remember { mutableStateOf(GuessPhase.INTRO) }
    var seconds by remember { mutableIntStateOf(60) }
    var count by remember { mutableIntStateOf(3) }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    var scored by remember { mutableIntStateOf(0) }
    var useButtons by remember { mutableStateOf(false) }

    val horizontal = phase == GuessPhase.COUNTDOWN || phase == GuessPhase.PLAYING
    val context = LocalContext.current
    val view = LocalView.current
    val active = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    // Sensor axes follow the device's natural orientation: on a device that is naturally landscape, sideways is its y axis.
    val wide = remember {
        val turn = view.display?.rotation
        turn == android.view.Surface.ROTATION_90 || turn == android.view.Surface.ROTATION_270
    }

    fun loadBook() {
        personal = Notebook.read(notebook).filter { !it.sentence }.map { Word(it.term, it.translation, it.translation, null) }
    }

    fun chosen(): List<Word> = when (source) {
        1 -> personal
        2 -> personal.shuffled().take(4) + words.shuffled().take(4)
        else -> words
    }

    fun start(pool: List<Word>) {
        val seen = HashSet<String>()
        val next = pool.shuffled().filter { it.term.isNotEmpty() && seen.add(it.term.lowercase()) }.take(8)
        if (next.isEmpty()) return
        deck = next
        missed = emptyList()
        index = 0
        scored = 0
        count = 3
        seconds = 60
        useButtons = false
        phase = GuessPhase.POSITIONING
    }

    fun answer(correct: Boolean) {
        if (phase != GuessPhase.PLAYING || feedback != null || seconds <= 0) return
        val word = deck.getOrNull(index) ?: return
        feedback = correct
        if (correct) {
            scored += 1
            Sfx.play(Sfx.Cue.CORRECT)
            Haptic.success()
        } else {
            missed = missed + word
            Sfx.play(Sfx.Cue.TICK)
            Haptic.thud()
        }
    }

    LaunchedEffect(notebook) { loadBook() }

    // The round is played sideways, and the screen stays on while the phone is on a forehead.
    DisposableEffect(horizontal) {
        val activity = context.guessActivity()
        if (horizontal) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            view.keepScreenOn = true
        }
        onDispose {
            if (horizontal) {
                activity?.requestedOrientation = Screen.restOrientation
                view.keepScreenOn = false
            }
        }
    }

    // Each result stays in colour for a moment before the next word.
    LaunchedEffect(if (active) feedback else null) {
        if (feedback == null || !active) return@LaunchedEffect
        delay(900)
        feedback = null
        if (seconds == 0 || index + 1 == deck.size) {
            Sfx.play(Sfx.Cue.COMPLETE)
            phase = GuessPhase.RECAP
        } else {
            index += 1
        }
    }

    // Gravity tells how the phone is held. iOS reports it in g, pointing down; Android reports the opposite force in m/s².
    val sensing = active && (phase == GuessPhase.POSITIONING || horizontal)
    DisposableEffect(sensing) {
        if (!sensing) return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravity = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravity ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var armed = false
        val smooth = FloatArray(3)
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                // The plain accelerometer also feels every shake: smooth it into gravity.
                val k = if (gravity != null || !primed) 1f else 0.2f
                for (i in 0..2) smooth[i] += (event.values[i] - smooth[i]) * k
                primed = true
                val norm = sqrt(smooth[0] * smooth[0] + smooth[1] * smooth[1] + smooth[2] * smooth[2])
                if (norm < 1f) return
                val gx = -(if (wide) smooth[1] else smooth[0]) / norm
                val gz = -smooth[2] / norm
                val upright = abs(gx) > 0.82f && abs(gz) < 0.32f
                // iOS also picks which way to turn the picture; here the system turns the screen itself.
                if (phase == GuessPhase.POSITIONING && upright) phase = GuessPhase.COUNTDOWN
                if (phase == GuessPhase.PLAYING && !useButtons) {
                    if (upright) armed = true
                    if (armed && abs(gz) > 0.72f) {
                        armed = false
                        // Screen toward the floor: found. Toward the ceiling: pass.
                        answer(gz > 0)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }

    LaunchedEffect(active && horizontal) {
        if (!active || !horizontal) return@LaunchedEffect
        while (phase == GuessPhase.COUNTDOWN) {
            Sfx.play(Sfx.Cue.TICK)
            delay(1000)
            if (count > 1) count -= 1 else { Sfx.play(Sfx.Cue.POP); phase = GuessPhase.PLAYING }
        }
        while (phase == GuessPhase.PLAYING) {
            delay(1000)
            if (phase != GuessPhase.PLAYING) return@LaunchedEffect
            seconds -= 1
            if (seconds == 0) {
                if (feedback == null) { Sfx.play(Sfx.Cue.COMPLETE); phase = GuessPhase.RECAP }
                return@LaunchedEffect
            } else if (seconds <= 5) Sfx.play(Sfx.Cue.TICK)
        }
    }

    val shown = feedback
    val backdrop = when {
        shown != null -> if (shown) hexColor("087844") else hexColor("BA3044")
        horizontal -> hexColor("143957")
        else -> SkyTint
    }
    val fg = if (horizontal) Color.White else Ink
    val body = textFont(18)
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().background(backdrop).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).tappable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L("Fermer", "Close"), Modifier.size(22.dp), tint = Sky)
            }
            Spacer(Modifier.weight(1f))
            if (phase == GuessPhase.PLAYING) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GuessLabel(Icons.Rounded.Timer, "${seconds}s", if (seconds <= 5) Sun else Color.White)
                    GuessLabel(Icons.Rounded.Star, "$scored", Sun)
                }
            }
        }
        Column(
            // The list of passed words scrolls by itself; everything else scrolls only when the screen is too small.
            Modifier.weight(1f).fillMaxWidth().then(if (phase == GuessPhase.RECAP) Modifier else Modifier.verticalScroll(scroll)),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                GuessPhase.INTRO -> {
                    LobaView(mood = Mood.WAVE, size = 88.dp)
                    KubaBand(Modifier.widthIn(max = 170.dp).clip(CircleShape), height = 8.dp)
                    Text(L("Devine le mot", "Guess the word"), style = displayFont(32), color = fg, textAlign = TextAlign.Center)
                    Text(
                        L("Tiens le téléphone contre ton front, écran vers ton partenaire. Ton partenaire décrit le mot sans le dire. Devine-le dans la langue que tu apprends.", "Hold the phone to your forehead with the screen facing your partner. They describe the word without saying it. Guess it aloud in the language you’re learning."),
                        style = body, color = fg, textAlign = TextAlign.Center,
                    )
                    Text(
                        L("Incline l’écran vers le sol si tu trouves, vers le ciel pour passer. Tu peux aussi toucher les boutons.", "Tilt the screen toward the floor for correct or toward the ceiling to pass. You can also tap the buttons."),
                        style = body, color = fg, textAlign = TextAlign.Center,
                    )
                    GuessDeckPicker(listOf("Studio", L("Mes mots", "My words"), L("Mixte", "Mixed")), source) { source = it }
                    if (source != 0 && personal.isEmpty()) GuessLink(L("Ajouter mes mots", "Add my words")) { editBook = true }
                    if (source != 1 && words.isEmpty()) {
                        Text(L("Aucun mot Studio disponible pour ce jeu.", "No Studio words available for this game."), style = body, color = fg, textAlign = TextAlign.Center)
                    }
                    val empty = when (source) {
                        1 -> personal.isEmpty()
                        2 -> words.isEmpty() || personal.isEmpty()
                        else -> words.isEmpty()
                    }
                    ChunkyButton(L("Jouer", "Play"), kind = Chunky.GREEN, enabled = !empty) { start(chosen()) }
                }
                GuessPhase.POSITIONING -> {
                    Icon(Icons.Rounded.ScreenRotation, null, Modifier.size(40.dp), tint = fg)
                    Text(
                        L("Tourne ton téléphone à l’horizontale, écran vers ton partenaire.", "Turn the phone sideways with the screen facing your partner."),
                        style = body, color = fg, textAlign = TextAlign.Center,
                    )
                    GuessLink(L("Utiliser les boutons", "Use buttons")) {
                        useButtons = true
                        phase = GuessPhase.COUNTDOWN
                    }
                }
                GuessPhase.COUNTDOWN -> {
                    Text(L("Prêt ?", "Ready?"), style = displayFont(24), color = fg)
                    Text("$count", style = displayFont(64).copy(fontFeatureSettings = "tnum"), color = fg)
                }
                GuessPhase.PLAYING -> {
                    val word = deck.getOrNull(index)
                    if (word != null && shown != null) {
                        Icon(if (shown) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, Modifier.size(64.dp), tint = fg)
                        Text(if (shown) L("Trouvé !", "Got it!") else L("Raté !", "Missed!"), style = displayFont(46), color = fg, textAlign = TextAlign.Center)
                        Text(word.term, style = textFont(20, W.HEAVY), color = fg, textAlign = TextAlign.Center)
                    } else if (word != null) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            KubaBand(Modifier.width(84.dp).clip(CircleShape), height = 6.dp)
                            BasicText(
                                text = word.term, style = displayFont(58).copy(color = Ink, textAlign = TextAlign.Center), maxLines = 2,
                                autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 58.sp, stepSize = 2.sp),
                            )
                            Text(word.meaning(ui), style = textFont(20, W.BOLD), color = Ink2, textAlign = TextAlign.Center)
                        }
                        if (useButtons) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChunkyButton(L("Passer", "Pass"), Modifier.weight(1f), kind = Chunky.OUTLINE) { answer(false) }
                                ChunkyButton(L("Trouvé !", "Got it!"), Modifier.weight(1f), kind = Chunky.GREEN) { answer(true) }
                            }
                        }
                    }
                }
                GuessPhase.RECAP -> {
                    Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(40.dp), tint = SunShade)
                    Text(L("Manche terminée", "Round complete"), style = displayFont(30), color = fg, textAlign = TextAlign.Center)
                    Text(L("Mots trouvés", "Words guessed") + " · $scored / ${scored + missed.size}", style = body, color = fg, textAlign = TextAlign.Center)
                    if (missed.isNotEmpty()) {
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (word in missed) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(word.term, style = textFont(18, W.BOLD), color = fg)
                                    Text(word.meaning(ui), style = body, color = fg)
                                    if (word.example.isNotEmpty()) Text(word.example, style = body.copy(fontStyle = FontStyle.Italic), color = fg)
                                }
                            }
                        }
                        ChunkyButton(L("Rejouer les mots passés", "Retry passed words"), kind = Chunky.GREEN) { start(missed) }
                    }
                    ChunkyButton(L("Nouvelle manche", "New round"), kind = Chunky.OUTLINE) { start(chosen()) }
                }
            }
        }
    }

    if (editBook) {
        Sheet(onDismiss = { editBook = false; loadBook() }) {
            PhrasebookView(key = notebook, onClose = { editBook = false; loadBook() })
        }
    }
}

/** The timer and the score, in figures that do not jump about. */
@Composable
private fun GuessLabel(icon: ImageVector, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(text, style = textFont(18).copy(fontFeatureSettings = "tnum"), color = color)
    }
}

/** A plain text button in the accent colour. */
@Composable
private fun GuessLink(text: String, onClick: () -> Unit) {
    Box(Modifier.defaultMinSize(minHeight = 44.dp).tappable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = textFont(18), color = Sky, textAlign = TextAlign.Center)
    }
}

/** The segmented control that picks the deck. */
@Composable
private fun GuessDeckPicker(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Ink.copy(alpha = 0.1f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(9.dp)).background(if (i == selected) Color.White else Color.Transparent)
                    .tappable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = textFont(14, if (i == selected) W.HEAVY else W.SEMIBOLD), color = if (i == selected) Ink else Ink2, textAlign = TextAlign.Center) }
        }
    }
}
