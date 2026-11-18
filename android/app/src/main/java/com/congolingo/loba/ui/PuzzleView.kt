package com.congolingo.loba.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.LessonUnit
import com.congolingo.loba.core.Puzzle
import com.congolingo.loba.core.PuzzleScores
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.UiLang
import com.congolingo.loba.core.Voice
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val wordColors: List<Color> = listOf(Sky, Leaf, Coral, Plum, SunShade, hexColor("ff8a3d"))

/** SwiftUI's `.spring(duration:bounce:)`. */
private fun swiftSpring(duration: Double, bounce: Double): SpringSpec<Float> {
    val w = 2 * PI / duration
    return spring(dampingRatio = (1 - bounce).toFloat(), stiffness = (w * w).toFloat())
}

/** Overshoots a little before settling, like a bouncy spring, but can be delayed per letter. */
private val bouncy = Easing { t -> val u = t - 1f; u * u * (2.70158f * u + 1.70158f) + 1f }

/** A star, grey and faint when it is not earned (`.saturation(0).opacity(…)` on iOS). */
@Composable
private fun StarArt(size: Dp, on: Boolean, faint: Float, modifier: Modifier = Modifier) {
    val id = drawableId("art-star")
    if (id == 0) { Spacer(modifier.size(size)); return }
    Image(
        painterResource(id), null, modifier.size(size), contentScale = ContentScale.Fit, alpha = if (on) 1f else faint,
        colorFilter = if (on) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Surface).tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Close, L("Fermer", "Close"), Modifier.size(22.dp), tint = Ink) }
}

// MARK: - Choosing a puzzle

/** Mots mêlés: one puzzle per unit, opened one after the other. */
@Composable
fun PuzzleHub(autostart: Boolean = false, onClose: () -> Unit) {
    var playing by remember { mutableStateOf<LessonUnit?>(null) }
    var scores by remember { mutableStateOf(PuzzleScores.all) }
    val scope = rememberCoroutineScope()
    val units = AppStore.units
    val ui = AppStore.state.ui
    val open = units.indexOfFirst { scores[it.id] == null }.let { if (it < 0) units.size else it }

    GameMusic()
    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().readable(720.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClose)
            Spacer(Modifier.weight(1f))
            MusicToggle(Modifier.padding(end = 10.dp))
            Row(
                Modifier.clip(CircleShape).background(SunTint).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Art("star", 22.dp)
                Text("${scores.values.sum()} / ${units.size * 3}", style = textFont(16, W.HEAVY), color = SunShade)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LobaView(mood = Mood.WAVE, size = 104.dp)
                Bubble(Modifier.weight(1f, fill = false)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(L("Mots mêlés", "Word hunt"), style = displayFont(26), color = Ink)
                        Text(
                            L("Trouve les mots cachés. Chacun te révèle son sens et une phrase.", "Find the hidden words. Each one reveals its meaning and a sentence."),
                            style = textFont(15), color = Ink2,
                        )
                    }
                }
            }
            KubaBand(Modifier.clip(CircleShape), height = 10.dp)
            units.forEachIndexed { i, unit ->
                key(unit.id) {
                    val locked = i > open
                    val stars = scores[unit.id] ?: 0
                    Row(
                        Modifier.fillMaxWidth().card(selected = i == open, tint = Sun).tappable(enabled = !locked) { playing = unit }.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (locked) Surface else wordColors[i % wordColors.size].copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (locked) "🔒" else unit.icon, fontSize = 30.sp, color = Ink) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(L("Grille ${i + 1}", "Puzzle ${i + 1}").uppercase(), style = textFont(12, W.HEAVY), color = Muted, letterSpacing = 1.2.sp)
                            Text(unit.name(ui), style = textFont(18, W.HEAVY), color = if (locked) Muted else Ink, textAlign = TextAlign.Start)
                        }
                        if (!locked) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (n in 0 until 3) StarArt(22.dp, on = n < stars, faint = 0.28f)
                            }
                        }
                    }
                }
            }
        }
    }

    playing?.let { unit ->
        key(unit.id) {
            // Back does what the game's close button does: leave without a next puzzle.
            FullScreenCover(onDismiss = { scores = PuzzleScores.all; playing = null }) {
                PuzzleGame(unit) { next ->
                    scores = PuzzleScores.all
                    playing = null
                    if (next != null) scope.launch { delay(450); playing = next }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (autostart && playing == null) playing = units.firstOrNull() }
}

// MARK: - Playing

/** What one game holds while it is played. */
private class PuzzlePlay(val unit: LessonUnit, val puzzle: Puzzle?, private val scope: CoroutineScope) {
    var found by mutableStateOf(listOf<String>())
    var hinted by mutableStateOf(setOf<String>())
    var start by mutableStateOf<Puzzle.Cell?>(null)
    var selection by mutableStateOf(listOf<Puzzle.Cell>())
    var hint by mutableStateOf<Puzzle.Cell?>(null)
    var last by mutableStateOf<Puzzle.Entry?>(null)
    var burst by mutableIntStateOf(0)
    var burstAt by mutableStateOf(Offset.Zero)
    var shake by mutableIntStateOf(0)
    var dealt by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var reward by mutableStateOf<Rules.Reward?>(null)
    /** Width of one cell, in pixels. */
    var side = 0f

    fun color(entry: Puzzle.Entry): Color = wordColors[(puzzle?.entries?.indexOf(entry) ?: 0).coerceAtLeast(0) % wordColors.size]

    /** The finger is at this point of the grid. */
    fun touch(at: Offset, side: Float) {
        if (side <= 0f) return
        val cell = Puzzle.Cell(
            row = (at.y / side).toInt().coerceIn(0, Puzzle.size - 1),
            col = (at.x / side).toInt().coerceIn(0, Puzzle.size - 1),
        )
        if (start == null) { start = cell; hint = null }
        val line = Puzzle.line(start!!, cell)
        if (line != selection) {
            selection = line
            Sfx.play(Sfx.Cue.TILE, line.size - 1); Haptic.tap()
        }
    }

    fun release() {
        val puzzle = puzzle ?: return
        val entry = puzzle.match(selection, found.toSet())
        start = null
        if (entry == null) {
            if (selection.size > 1) { Sfx.play(Sfx.Cue.TICK); shake += 1 }
            selection = emptyList()
            return
        }
        val middle = entry.cells[entry.cells.size / 2]
        burstAt = Offset((middle.col + 0.5f) * side, (middle.row + 0.5f) * side)
        burst += 1
        hint = null
        found = found + entry.id
        selection = emptyList()
        last = entry
        Sfx.play(Sfx.Cue.CORRECT, found.size); Haptic.success()
        Voice.say(entry.word)
        // A word found without help counts as known; one found with a hint is still new.
        if (entry.id !in hinted) AppStore.recordWord(entry.word.term, correct = true)
        if (found.size == puzzle.entries.size) finish(puzzle)
    }

    /** Tapping a clue shows where its word begins. It costs a star, not hearts. */
    fun help(entry: Puzzle.Entry) {
        hinted = hinted + entry.id
        Sfx.play(Sfx.Cue.POP); Haptic.thud()
        hint = entry.cells.firstOrNull()
    }

    private fun finish(puzzle: Puzzle) {
        val stars = Puzzle.stars(hinted.size)
        PuzzleScores.record(unit.id, stars)
        reward = AppStore.complete(
            Rules.SessionResult(kind = Rules.SessionKind.PRACTICE, unitId = null, lessonIndex = null, mistakes = hinted.size, bestCombo = puzzle.entries.size - hinted.size),
        )
        scope.launch {
            delay(1600)
            Sfx.play(Sfx.Cue.COMPLETE)
            finished = true
        }
    }
}

/** `leave` is called when the learner leaves: with the next unit when they asked for the next puzzle. */
@Composable
fun PuzzleGame(unit: LessonUnit, leave: (LessonUnit?) -> Unit) {
    val scope = rememberCoroutineScope()
    val play = remember(unit.id) { PuzzlePlay(unit, Puzzle.make(unit, AppStore.units), scope) }
    val puzzle = play.puzzle
    val next = AppStore.units.let { units ->
        val i = units.indexOfFirst { it.id == unit.id }
        if (i >= 0 && i + 1 < units.size) units[i + 1] else null
    }

    Box(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().readable(680.dp), contentAlignment = Alignment.Center) {
        if (puzzle != null) {
            Crossfade(play.finished && play.reward != null, Modifier.fillMaxSize(), animationSpec = tween(350), label = "done") { done ->
                val reward = play.reward
                if (done && reward != null) PuzzleDone(puzzle, Puzzle.stars(play.hinted.size), reward, next, leave)
                else Board(play, puzzle, leave)
            }
        } else {
            Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LobaView(mood = Mood.THINK, size = 140.dp)
                Text(
                    L("Pas encore assez de mots courts dans cette unité.", "Not enough short words in this unit yet."),
                    style = textFont(17, W.BOLD), color = Ink2, textAlign = TextAlign.Center,
                )
                ChunkyButton(L("Retour", "Back"), kind = Chunky.OUTLINE) { leave(null) }
            }
        }
    }

    LaunchedEffect(play) {
        Sfx.play(Sfx.Cue.WHOOSH)
        play.dealt = true
        // `autoplay yes` finds every word, `autoplay hold` stops halfway: for screenshots.
        val style = DebugFlags.autoplay ?: return@LaunchedEffect
        if (puzzle == null) return@LaunchedEffect
        delay(1200)
        for ((i, entry) in puzzle.entries.withIndex()) {
            if (style == "hold" && i == 3) { play.selection = entry.cells.take(3); return@LaunchedEffect }
            if (style == "wrong" && i == 1) { play.help(entry); delay(500) }
            play.selection = entry.cells
            delay(350)
            play.release()
            delay(700)
        }
    }
}

// MARK: layout

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Board(play: PuzzlePlay, puzzle: Puzzle, leave: (LessonUnit?) -> Unit) {
    val ui = AppStore.state.ui
    val unit = play.unit
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton { leave(null) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${unit.icon} ${unit.name(ui)}", style = textFont(15, W.HEAVY), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Meter(play.found.size.toDouble() / puzzle.entries.size.toDouble(), color = Leaf, height = 12.dp)
            }
            Text("${play.found.size}/${puzzle.entries.size}", style = displayFont(20), color = LeafShade)
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (entry in puzzle.entries) key(entry.id) { Clue(play, entry, ui) }
        }

        // The grid gives way first on a short screen (`.aspectRatio(1, contentMode: .fit)` on iOS).
        Grid(play, puzzle, Modifier.weight(1f, fill = false).padding(horizontal = 14.dp).aspectRatio(1f))

        AnimatedContent(
            play.last, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith fadeOut() },
            contentKey = { it?.id }, label = "discovery",
        ) { last ->
            if (last != null) Discovery(last, play.color(last), ui)
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                LobaView(mood = Mood.THINK, size = 84.dp)
                Bubble(Modifier.weight(1f, fill = false)) {
                    Text(
                        L("Glisse ton doigt sur les lettres. Besoin d’aide ? Touche un indice.", "Slide your finger across the letters. Stuck? Tap a clue."),
                        style = textFont(15, W.BOLD), color = Ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Clue(play: PuzzlePlay, entry: Puzzle.Entry, ui: UiLang) {
    val done = entry.id in play.found
    val tint = play.color(entry)
    val bounce = spring<Color>(dampingRatio = 0.5f, stiffness = 246f)
    val face by animateColorAsState(if (done) tint else Color.White, bounce, label = "face")
    val edge by animateColorAsState(if (done) tint.copy(alpha = 0.5f) else Line, bounce, label = "edge")
    val border = if (done) tint else if (entry.id in play.hinted) Sun else Line
    val ink = if (done) Color.White else Ink
    Row(
        Modifier
            .padding(bottom = 3.dp)
            .drawBehind { drawRoundRect(edge, Offset(0f, 3.dp.toPx()), size, CornerRadius(size.height / 2)) }
            .clip(CircleShape).background(face).border(2.dp, border, CircleShape)
            .tappable(enabled = !done) { play.help(entry) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = ink)
        Text(
            if (done) entry.word.term else entry.word.meaning(ui), Modifier.weight(1f, fill = false),
            style = textFont(15, W.HEAVY), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!done) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text("${entry.letters.size}", style = textFont(12, W.HEAVY), color = tint)
            }
        }
    }
}

@Composable
private fun Grid(play: PuzzlePlay, puzzle: Puzzle, modifier: Modifier) {
    val selection = play.selection
    val hint = play.hint
    val found = play.found
    val lit = selection.toSet() + puzzle.entries.filter { it.id in found }.flatMap { it.cells }
    val clock = rememberAmbient(paused = hint == null)
    // Ribbons over the words already found fade and grow in.
    val ribbons = puzzle.entries.map { entry ->
        key(entry.id) { animateFloatAsState(if (entry.id in found) 1f else 0f, swiftSpring(0.4, 0.4), label = "ribbon") }
    }
    // `Shake` on iOS: the grid swings twice from side to side after a wrong line.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(play.shake) { shake.animateTo(play.shake.toFloat(), tween(300, easing = LinearEasing)) }

    BoxWithConstraints(
        modifier
            .graphicsLayer { translationX = sin(shake.value * PI.toFloat() * 4) * 7.dp.toPx() }
            .onSizeChanged { play.side = it.width / Puzzle.size.toFloat() }
            .pointerInput(play) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    play.touch(down.position, size.width / Puzzle.size.toFloat())
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            change.consume()
                            play.touch(change.position, size.width / Puzzle.size.toFloat())
                        }
                    }
                    play.release()
                }
            },
    ) {
        val side = maxWidth / Puzzle.size
        val letter = with(LocalDensity.current) { (side * 0.52f).toSp() }

        Canvas(Modifier.matchParentSize()) {
            val s = size.width / Puzzle.size
            fun center(c: Puzzle.Cell) = Offset((c.col + 0.5f) * s, (c.row + 0.5f) * s)
            fun ribbon(cells: List<Puzzle.Cell>, color: Color, width: Float) {
                val first = cells.firstOrNull() ?: return
                val end = cells.last()
                // A single cell is a dot.
                if (first == end) drawCircle(color, width / 2, center(first))
                else drawLine(color, center(first), center(end), strokeWidth = width, cap = StrokeCap.Round)
            }
            val r = CornerRadius(22.dp.toPx())
            val w = 2.dp.toPx()
            drawRoundRect(Surface, cornerRadius = r)
            drawRoundRect(Line, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(22.dp.toPx() - w / 2), style = Stroke(w))
            puzzle.entries.forEachIndexed { i, entry ->
                val p = ribbons[i].value
                if (p > 0.01f) ribbon(entry.cells, wordColors[i % wordColors.size].copy(alpha = p.coerceIn(0f, 1f)), s * 0.78f * (0.9f + 0.1f * p))
            }
            // Then the one being drawn.
            if (selection.isNotEmpty()) ribbon(selection, Sun, s * 0.78f)
            if (hint != null) {
                val beat = ((sin(clock.value * 7) + 1) / 2).toFloat()
                val d = s * (0.8f + beat * 0.14f)
                val line = 4.dp.toPx()
                drawCircle(SunTint, d / 2, center(hint))
                drawCircle(Sun, d / 2 - line / 2, center(hint), style = Stroke(line))
            }
        }

        for (r in 0 until Puzzle.size) {
            for (c in 0 until Puzzle.size) {
                key(r, c) {
                    val cell = Puzzle.Cell(r, c)
                    val selected = cell in selection
                    val on = (cell in lit && hint != cell) || selected
                    val deal by animateFloatAsState(
                        if (play.dealt) 1f else 0f,
                        if (Motion.still) tween(0) else tween(350, delayMillis = 100 + (r + c) * 25, easing = bouncy), label = "deal",
                    )
                    val grow by animateFloatAsState(if (selected) 1.22f else 1f, swiftSpring(0.18, 0.5), label = "grow")
                    Box(
                        Modifier.offset(x = side * c, y = side * r).size(side).graphicsLayer {
                            val scale = (0.2f + 0.8f * deal) * grow
                            scaleX = scale; scaleY = scale
                            alpha = deal.coerceIn(0f, 1f)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            puzzle.grid[r][c], style = displayFont(20).copy(fontSize = letter),
                            color = if (selected) Ink else if (on) Color.White else Ink, maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }

        val half = with(LocalDensity.current) { 150.dp.toPx() }
        Burst(
            trigger = play.burst,
            modifier = Modifier
                .offset { IntOffset((play.burstAt.x - half).roundToInt(), (play.burstAt.y - half).roundToInt()) }
                .requiredSize(300.dp),
        )
    }
}

@Composable
private fun Discovery(entry: Puzzle.Entry, color: Color, ui: UiLang) {
    Column(Modifier.fillMaxWidth().card(selected = true, tint = color).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.word.audio != null) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(color).tappable { Voice.say(entry.word) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, L("Écouter", "Listen"), Modifier.size(20.dp), tint = Color.White)
                }
            }
            Text(entry.word.term, style = displayFont(26), color = color)
            Text("=", style = textFont(18, W.HEAVY), color = Muted)
            Text(entry.word.meaning(ui), Modifier.weight(1f), style = textFont(18, W.HEAVY), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        entry.example?.let { example ->
            Column(
                Modifier
                    .drawBehind { drawRoundRect(color.copy(alpha = 0.5f), size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx())) }
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(highlighted(example.text, entry.word.term, color), style = textFont(17, W.BOLD), color = Ink)
                entry.word.exampleAudio?.let { audio ->
                    Text(L("Écouter la phrase", "Listen to the sentence"), modifier = Modifier.tappable { Voice.say(entry.word.copy(audio = audio)) }.padding(12.dp), style = textFont(14, W.BOLD), color = color)
                }
                Text(example.translation(ui), style = textFont(15), color = Muted)
            }
        }
    }
}

/** The sentence with the word it illustrates picked out in colour. */
private fun highlighted(sentence: String, term: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(sentence)
    val (hay, at) = folded(sentence)
    val needle = folded(term).first
    val i = if (needle.isEmpty()) -1 else hay.indexOf(needle)
    if (i >= 0) {
        val from = at[i]
        var to = at[i + needle.length - 1] + 1
        // Keep the tone marks that follow the last letter.
        while (to < sentence.length && Character.getType(sentence[to]) == Character.NON_SPACING_MARK.toInt()) to += 1
        // Compose underlines in the text's own colour; iOS draws the line at 40 %.
        addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), from, to)
    }
}

/** Lower case without accents (the search ignores both), and where each of its characters comes from. */
private fun folded(s: String): Pair<String, List<Int>> {
    val out = StringBuilder()
    val at = ArrayList<Int>()
    s.forEachIndexed { i, ch ->
        for (c in Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).lowercase()) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) { out.append(c); at.add(i) }
        }
    }
    return out.toString() to at
}

// MARK: - Finished

@Composable
private fun PuzzleDone(puzzle: Puzzle, stars: Int, reward: Rules.Reward, next: LessonUnit?, leave: (LessonUnit?) -> Unit) {
    val ui = AppStore.state.ui
    var shown by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    SunRays(Modifier.requiredSize(280.dp).alpha(0.5f))
                    LobaView(mood = Mood.CHEER, size = 150.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (n in 0 until 3) {
                        val landed by animateFloatAsState(if (shown > n || n >= stars) 1f else 0f, swiftSpring(0.5, 0.6), label = "star")
                        StarArt(
                            if (n == 1) 64.dp else 50.dp, on = n < stars, faint = 0.25f,
                            modifier = Modifier.offset(y = if (n == 1) (-8).dp else 0.dp).graphicsLayer {
                                val scale = 0.1f + 0.9f * landed
                                scaleX = scale; scaleY = scale
                                rotationZ = -140f * (1f - landed)
                            },
                        )
                    }
                }
                Text(
                    if (stars == 3) L("Sans aucune aide !", "No help needed!") else L("Grille terminée !", "Puzzle solved!"),
                    style = displayFont(30), color = Ink, textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.clip(CircleShape).background(SunTint).padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Art("bolt", 26.dp)
                    Text("+${reward.xp} XP", style = displayFont(22), color = SunShade)
                }
                Text(
                    L("Tes mots", "Your words").uppercase(), Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = textFont(13, W.HEAVY), color = Muted, letterSpacing = 1.5.sp,
                )
                puzzle.entries.forEachIndexed { i, entry ->
                    val tint = wordColors[i % wordColors.size]
                    Column(
                        Modifier.fillMaxWidth().card().tappable { Sfx.play(Sfx.Cue.TILE, i); Voice.say(entry.word) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.word.term, style = displayFont(20), color = tint)
                            Text(entry.word.meaning(ui), Modifier.weight(1f), style = textFont(16, W.BOLD), color = Ink)
                            if (entry.word.audio != null) Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(22.dp), tint = tint)
                        }
                        entry.example?.let { example ->
                            Text(highlighted(example.text, entry.word.term, tint), style = textFont(15, W.BOLD), color = Ink)
                            Text(example.translation(ui), style = textFont(14), color = Muted)
                        }
                    }
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (next != null) ChunkyButton(L("Grille suivante", "Next puzzle"), kind = Chunky.GREEN) { leave(next) }
                ChunkyButton(L("Terminer", "Done"), kind = if (next == null) Chunky.GREEN else Chunky.OUTLINE) { leave(null) }
            }
        }
        Confetti(Modifier.matchParentSize())
    }
    LaunchedEffect(Unit) {
        Haptic.success()
        for (n in 0 until stars) {
            delay(if (n == 0) 350 else 320)
            Sfx.play(Sfx.Cue.CORRECT, n + 2)
            shown = n + 1
        }
    }
}
