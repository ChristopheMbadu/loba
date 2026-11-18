package com.congolingo.loba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.DRC
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.MapProgress
import com.congolingo.loba.core.MapRegion
import com.congolingo.loba.core.Province
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.Satellite
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.UiLang
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private sealed class MapScreen {
    data object Hub : MapScreen()
    data class Puzzle(val level: String) : MapScreen()
    data class Quiz(val ids: List<String>) : MapScreen()
    data object Atlas : MapScreen()
    data object Book : MapScreen()
}

/** Mboka na biso ("our country"): the map of the DRC as a puzzle, a quiz, an atlas and a little book. */
@Composable
fun MapGameView(onClose: () -> Unit) {
    var screen by remember {
        // Debug builds only: `--es maplevel atlas|book|quiz|<level>` opens that screen directly.
        val start = when (val level = DebugFlags.maplevel) {
            null -> MapScreen.Hub
            "atlas" -> MapScreen.Atlas
            "book" -> MapScreen.Book
            "quiz" -> MapScreen.Quiz(DRC.shared.ids.shuffled().take(8))
            else -> MapScreen.Puzzle(level)
        }
        mutableStateOf<MapScreen>(start)
    }
    val go: (MapScreen) -> Unit = { next ->
        Sfx.play(Sfx.Cue.WHOOSH)
        screen = next
    }
    LaunchedEffect(Unit) { Satellite.load() }
    BackHandler(enabled = screen != MapScreen.Hub) { go(MapScreen.Hub) }

    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val hub = screen == MapScreen.Hub
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Surface)
                    .semantics { role = Role.Button }
                    .tappable { if (hub) onClose() else go(MapScreen.Hub) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (hub) Icons.Rounded.Close else Icons.Rounded.ChevronLeft,
                    contentDescription = if (hub) L("Fermer", "Close") else L("Retour", "Back"),
                    tint = Ink, modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Mboka na biso", style = displayFont(20), color = Ink)
                Text(L("La carte du Congo", "The map of Congo"), style = textFont(13, W.BOLD), color = Muted)
            }
            UiSwitch()
        }

        AnimatedContent(
            screen, Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "mapscreen",
        ) { shown ->
            when (shown) {
                MapScreen.Hub -> MapHub(go)
                is MapScreen.Puzzle -> MapPuzzle(shown.level, go)
                is MapScreen.Quiz -> MapQuiz(shown.ids, go)
                MapScreen.Atlas -> MapAtlas()
                MapScreen.Book -> MapBook()
            }
        }
    }
}

// MARK: - The map

enum class MapTone { PLACED, SLOT, QUIET, HINT, RIGHT, WRONG, OPEN }

/** The whole country as one path, and everything around it (for the veil over the neighbours). */
private class Country(drc: DRC) {
    val land = Path().apply { for (id in drc.ids) addPath(drc.paths.getValue(id)) }
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(drc.whole)
        for (id in drc.ids) addPath(drc.paths.getValue(id))
    }
}

private val labelHalo = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -0.7f to -0.7f, 0.7f to 0.7f, -0.7f to 0.7f, 0.7f to -0.7f)

/** The country drawn in one canvas: satellite image, a veil over the neighbours, then each province in its tone. */
@Composable
fun MapCanvas(
    modifier: Modifier = Modifier,
    window: Rect = DRC.shared.whole,
    outlines: Boolean = true,
    labels: Set<String> = emptySet(),
    onTap: ((String?) -> Unit)? = null,
    tone: (String) -> MapTone,
) {
    val drc = DRC.shared
    val country = remember(drc) { Country(drc) }
    val pulsing = drc.ids.any { tone(it) == MapTone.HINT }
    val clock = rememberAmbient(paused = !pulsing)
    val measurer = rememberTextMeasurer()
    val tapped = rememberUpdatedState(onTap)
    val night = hexColor("0d2a45")
    val sea = hexColor("a9c9e2")
    val forest = hexColor("3f7d4e")
    val halo = hexColor("14203a").copy(alpha = 0.8f)

    Canvas(
        modifier
            .aspectRatio(window.width / window.height)
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (onTap == null) Modifier else Modifier.pointerInput(window) {
                    detectTapGestures { at ->
                        val scale = size.width / window.width
                        tapped.value?.invoke(drc.province(at = Offset(window.left + at.x / scale, window.top + at.y / scale)))
                    }
                },
            ),
    ) {
        val s = size.width / window.width
        val beat = if (pulsing) ((sin(clock.value * 6) + 1) / 2).toFloat() else 0f
        drawRect(night)
        // Map point p lands on (p − window.topLeft) × scale; widths inside are in map units, as on iOS.
        withTransform({ scale(s, s, pivot = Offset.Zero); translate(-window.left, -window.top) }) {
            val whole = drc.whole
            val picture = Satellite.image
            if (picture != null) {
                drawImage(picture, dstOffset = IntOffset.Zero, dstSize = IntSize(whole.width.roundToInt(), whole.height.roundToInt()))
            } else {
                drawRect(sea, whole.topLeft, whole.size)
                drawPath(country.land, forest)
            }
            // Everything outside the country fades, and the border is drawn only on its outer side.
            clipPath(country.outside) {
                drawRect(Color.White.copy(alpha = 0.62f), whole.topLeft, whole.size)
                drawPath(country.land, Color.White, style = Stroke(7f * window.width / 640f, join = StrokeJoin.Round))
            }

            val line = 1.3f * window.width / 640f
            for (id in drc.ids) {
                val path = drc.paths.getValue(id)
                when (tone(id)) {
                    MapTone.PLACED -> { drawPath(path, drc.color(id).copy(alpha = 0.9f)); drawPath(path, Color.White, style = Stroke(line)) }
                    MapTone.RIGHT -> { drawPath(path, Leaf); drawPath(path, Color.White, style = Stroke(line)) }
                    MapTone.WRONG -> { drawPath(path, Coral); drawPath(path, Color.White, style = Stroke(line)) }
                    MapTone.HINT -> { drawPath(path, Sun.copy(alpha = 0.5f + beat * 0.4f)); drawPath(path, Sun, style = Stroke(line * 2.4f)) }
                    MapTone.QUIET -> { drawPath(path, Color.White.copy(alpha = 0.55f)); drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(line)) }
                    MapTone.OPEN -> { drawPath(path, Color.White.copy(alpha = 0.2f)); drawPath(path, Color.White, style = Stroke(line)) }
                    MapTone.SLOT -> if (outlines) { drawPath(path, Color.White.copy(alpha = 0.14f)); drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(line)) }
                }
            }
        }
        if (labels.isNotEmpty()) {
            val style = displayFont(15).copy(fontSize = max(11f, 15f * size.width.toDp().value / 640f).sp)
            val spread = 1.4.dp.toPx()
            for (id in labels) {
                val p = drc.province(id)
                val text = measurer.measure(p.name, style, softWrap = false, maxLines = 1)
                val at = Offset(
                    (p.centre.x - window.left) * s - text.size.width / 2f,
                    (p.centre.y - window.top) * s - text.size.height / 2f,
                )
                for ((dx, dy) in labelHalo) drawText(text, color = halo, topLeft = Offset(at.x + dx * spread, at.y + dy * spread))
                drawText(text, color = Color.White, topLeft = at)
            }
        }
    }
}

/** One province as a puzzle piece. */
@Composable
fun PieceShape(id: String, modifier: Modifier = Modifier, shadow: Boolean = false) {
    val drc = DRC.shared
    val box = drc.province(id).bounds
    val path = drc.paths.getValue(id)
    val color = drc.color(id)
    Canvas(modifier) {
        val s = min(size.width / box.width, size.height / box.height)
        val dx = (size.width - box.width * s) / 2
        val dy = (size.height - box.height * s) / 2
        // The piece in the hand casts a shadow (a flat one: Compose has no blurred shadow that follows a path).
        if (shadow) {
            val drop = 8.dp.toPx()
            withTransform({ translate(dx, dy + drop); scale(s, s, pivot = Offset.Zero); translate(-box.left, -box.top) }) {
                drawPath(path, Color.Black.copy(alpha = 0.22f))
            }
        }
        withTransform({ translate(dx, dy); scale(s, s, pivot = Offset.Zero); translate(-box.left, -box.top) }) {
            drawPath(path, color)
            drawPath(path, Color.White, style = Stroke(2.5.dp.toPx() / s, join = StrokeJoin.Round))
        }
    }
}

private val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
private fun MapStars(n: Int, size: Dp = 20.dp) {
    val star = drawableId("art-star")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 3) {
            if (star == 0) Spacer(Modifier.size(size))
            else Image(
                painterResource(star), null, Modifier.size(size), contentScale = ContentScale.Fit,
                alpha = if (i < n) 1f else 0.28f, colorFilter = if (i < n) null else grey,
            )
        }
    }
}

/** Swift's `.capitalized`: every word starts with a capital, the rest is lower case. */
private fun capitalized(s: String): String =
    s.split(" ").joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.titlecase() } }

@Composable
private fun FactCard(p: Province, full: Boolean = false) {
    val ui = AppStore.state.ui
    val tint = DRC.shared.color(p.id)
    Column(Modifier.fillMaxWidth().card(selected = true, tint = tint).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color.White, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(p.emoji, fontSize = 32.sp, color = Ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(p.name, style = displayFont(24), color = tint)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Chef-lieu", "Capital").uppercase(), style = textFont(12, W.HEAVY), color = Muted)
                    Text(p.capital, style = textFont(16, W.HEAVY), color = Ink)
                }
            }
        }
        Text(p.fact(ui), style = textFont(16, W.BOLD), color = Ink)
        FactRow(L("On y parle", "People speak"), capitalized(p.language))
        if (full) {
            FactRow(L("Peuples", "Peoples"), p.peoples.joinToString(", "))
            FactRow(L("Superficie", "Area"), "${NumberFormat.getIntegerInstance(Locale.forLanguageTag(ui.raw)).format(p.area)} km²")
            FactRow(L("Ancienne province", "Former province"), p.former)
            FactRow(L("Voisins", "Neighbours"), if (p.borders.isEmpty()) L("aucun", "none") else p.borders.joinToString(", "))
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = textFont(13, W.HEAVY), color = Muted, modifier = Modifier.width(124.dp))
        Text(value, style = textFont(14, W.BOLD), color = Ink, modifier = Modifier.weight(1f))
    }
}

// MARK: - Hub

private val pieceOffsets = listOf((-12).dp to (-12).dp, 13.dp to (-2).dp, (-2).dp to 15.dp)

@Composable
private fun MapHub(go: (MapScreen) -> Unit) {
    val drc = DRC.shared
    val ui = AppStore.state.ui
    val stars = remember { MapProgress.stars }
    val stamps = remember { MapProgress.stamps }
    val next = drc.regions.firstOrNull { stars[it.id] == null }
    val expertOpen = stars["hard"] != null || drc.regions.all { stars[it.id] != null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MapCanvas(tone = { if (it in stamps) MapTone.PLACED else MapTone.SLOT })
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            LobaView(mood = Mood.WAVE, size = 92.dp)
            Bubble(Modifier.weight(1f, fill = false)) {
                Text(L("Remets chaque province à sa place et découvre notre pays.", "Put every province back in its place and discover our country."), style = textFont(15, W.BOLD), color = Ink2)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(SunTint, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stamps.size}", style = displayFont(34), color = SunShade, modifier = Modifier.alignByBaseline())
                Text("/ ${drc.ids.size}", style = displayFont(18), color = SunShade, modifier = Modifier.alignByBaseline())
                Text(L("provinces découvertes", "provinces discovered"), style = textFont(15, W.HEAVY), color = hexColor("6b4d00"), modifier = Modifier.alignByBaseline())
            }
            Meter(stamps.size.toDouble() / drc.ids.size.toDouble(), color = Sun, height = 12.dp)
        }
        if (next != null) {
            ChunkyButton("${L("Jouer", "Play")} · ${next.name(ui)}", kind = Chunky.GREEN) { go(MapScreen.Puzzle(next.id)) }
        }

        Text(L("Les régions", "The regions"), style = displayFont(22), color = Ink, modifier = Modifier.padding(top = 8.dp))
        drc.regions.forEachIndexed { i, region ->
            HubCard(
                tint = region.tint, selected = region.id == next?.id, eyebrow = "${i + 1} · ${region.provinces.size} provinces",
                title = region.name(ui), stars = stars[region.id] ?: 0, action = { go(MapScreen.Puzzle(region.id)) },
            ) {
                region.provinces.take(3).forEachIndexed { n, id ->
                    PieceShape(id, Modifier.offset(pieceOffsets[n].first, pieceOffsets[n].second).size(30.dp))
                }
            }
        }

        Text(L("Pour les experts", "For experts"), style = displayFont(22), color = Ink, modifier = Modifier.padding(top = 8.dp))
        HubCard(
            tint = Coral, eyebrow = "26 provinces", title = L("Mode difficile", "Hard mode"),
            text = if (stars["hard"] == null) L("Tout le pays d’un coup : pas de régions, pas d’indices.", "The whole country at once: no regions, no hints.") else null,
            stars = stars["hard"], action = { go(MapScreen.Puzzle("hard")) },
        ) { Text("🔥", fontSize = 32.sp, color = Ink) }
        HubCard(
            tint = Sun, eyebrow = "26 provinces", title = L("Le grand défi", "The big challenge"),
            text = if (expertOpen) (if (stars["all"] == null) L("Les 26 provinces, sans les contours.", "All 26 provinces, without the outlines.") else null)
            else L("Termine le mode difficile ou les 6 régions pour l’ouvrir.", "Finish hard mode or the 6 regions to open it."),
            stars = if (expertOpen) stars["all"] else null, enabled = expertOpen, action = { go(MapScreen.Puzzle("all")) },
        ) { Text(if (expertOpen) "🏆" else "🔒", fontSize = 32.sp, color = Ink) }

        Text(L("Encore plus", "Even more"), style = displayFont(22), color = Ink, modifier = Modifier.padding(top = 8.dp))
        HubCard(
            tint = Plum, title = L("Quiz éclair", "Quick quiz"), text = L("Où est-ce ? Quel est le chef-lieu ?", "Where is it? What is its capital?"),
            action = { go(MapScreen.Quiz((if (stamps.size >= 4) stamps else drc.ids).shuffled().take(8))) },
        ) { Text("⚡", fontSize = 32.sp, color = Ink) }
        HubCard(tint = Leaf, title = "Atlas", text = L("Touche une province pour tout savoir.", "Tap a province to learn all about it."), action = { go(MapScreen.Atlas) }) {
            Text("🧭", fontSize = 32.sp, color = Ink)
        }
        HubCard(tint = SunShade, title = L("Histoire et cultures", "History and cultures"), text = L("Des royaumes à la rumba.", "From kingdoms to rumba."), action = { go(MapScreen.Book) }) {
            Text("📜", fontSize = 32.sp, color = Ink)
        }
        MapCredits()
    }
}

@Composable
private fun HubCard(
    tint: Color,
    title: String,
    action: () -> Unit,
    selected: Boolean = false,
    eyebrow: String? = null,
    text: String? = null,
    stars: Int? = null,
    enabled: Boolean = true,
    art: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).card(selected = selected, tint = tint)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tappable(enabled = enabled, onClick = action).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(66.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { art() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = textFont(12, W.HEAVY), color = Muted, letterSpacing = 1.sp)
            Text(title, style = displayFont(19), color = Ink)
            if (text != null) Text(text, style = textFont(14), color = Ink2)
            if (stars != null) MapStars(stars)
        }
    }
}

@Composable
private fun MapCredits() {
    Text(
        (if (Satellite.image == null) "" else "${L("Image satellite", "Satellite image")} © Mapbox © Maxar · ") + "© OpenStreetMap",
        style = textFont(11), color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

// MARK: - Puzzle: drag each province to its place

private class PuzzleGame(val level: String) {
    val drc = DRC.shared
    val region: MapRegion? = drc.regions.firstOrNull { it.id == level }
    val ids: List<String> = region?.provinces ?: drc.ids
    val window: Rect = if (region == null) drc.whole else drc.frame(ids)
    val hard = level == "hard"

    var queue by mutableStateOf(ids.shuffled())
    var placed by mutableStateOf(listOf<String>())
    var misses by mutableStateOf(mapOf<String, Int>())
    var note by mutableStateOf<String?>(null)
    var reveal by mutableStateOf<String?>(null)
    var wrong by mutableStateOf<String?>(null)
    var wrongCount by mutableIntStateOf(0)
    /** Where the finger is while dragging, in the puzzle's own coordinates. */
    var finger by mutableStateOf<Offset?>(null)
    var mapWidth by mutableIntStateOf(0)
    var xp by mutableStateOf<Int?>(null)

    // Where things are on screen: iOS reads the map's frame through a PreferenceKey in a named coordinate space.
    // Positions are worked out between these when needed, so they stay right while the screen slides in.
    var box: LayoutCoordinates? = null
    var map: LayoutCoordinates? = null
    var tray: LayoutCoordinates? = null

    val totalMisses: Int get() = misses.values.sum()
    val stars: Int get() = if (totalMisses <= 1) 3 else if (totalMisses <= ids.size) 2 else 1

    /** A point of the tray piece, in the puzzle's coordinates. */
    fun fromTray(local: Offset): Offset? {
        val box = box?.takeIf { it.isAttached } ?: return null
        val tray = tray?.takeIf { it.isAttached } ?: return null
        return box.localPositionOf(tray, local)
    }

    fun drop(p: Province, point: Offset, lift: Float) {
        finger = null
        val box = box?.takeIf { it.isAttached } ?: return
        val map = map?.takeIf { it.isAttached } ?: return
        val mapFrame = Rect(box.localPositionOf(map, Offset.Zero), map.size.toSize())
        val spot = Offset(point.x, point.y - lift)
        // Letting go outside the map is not a mistake.
        if (!mapFrame.contains(spot) || mapFrame.width <= 0) return
        val scale = mapFrame.width / window.width
        val at = Offset(window.left + (spot.x - mapFrame.left) / scale, window.top + (spot.y - mapFrame.top) / scale)
        // Small provinces get a generous landing zone: the centre only has to come close.
        val near = hypot(at.x - p.centre.x, at.y - p.centre.y) < max(13.0, sqrt(p.shapeArea) * 0.34)
        attempt(p.id, if (near) p.id else drc.province(at = at))
    }

    fun attempt(id: String, target: String?) {
        if (target == id) {
            Sfx.play(Sfx.Cue.CORRECT, placed.size + 1); Haptic.success()
            MapProgress.stamp(id)
            note = null; wrong = null
            placed = placed + id
            queue = queue.filter { it != id }
            reveal = id
            return
        }
        // A miss is a clue, not a failure: name the place they chose.
        Sfx.play(Sfx.Cue.TICK); Haptic.thud()
        misses = misses + (id to (misses[id] ?: 0) + 1)
        val again = L("Essaie encore !", "Try again!")
        note = if (target != null) "${L("Ici, c’est", "That is")} ${drc.province(target).name}. $again" else "${L("Pas tout à fait.", "Not quite.")} $again"
        wrong = target
        wrongCount += 1
    }

    fun finish() {
        if (xp != null) return
        MapProgress.record(level, stars)
        xp = AppStore.complete(Rules.SessionResult(kind = Rules.SessionKind.PRACTICE, unitId = null, lessonIndex = null, mistakes = totalMisses, bestCombo = ids.size)).xp
        Sfx.play(Sfx.Cue.COMPLETE); Haptic.success()
    }
}

@Composable
private fun MapPuzzle(level: String, go: (MapScreen) -> Unit) {
    val game = remember(level) { PuzzleGame(level) }
    val drc = game.drc
    val region = game.region
    val ids = game.ids
    val ui = AppStore.state.ui
    val density = LocalDensity.current
    /** Above the finger, so it doesn't hide the piece. */
    val lift = with(density) { 54.dp.toPx() }
    val current = game.queue.firstOrNull()
    val reveal = game.reveal
    // Hard mode gives no written hint, and the place only glows after three misses.
    val hinted = current != null && (game.misses[current] ?: 0) >= (if (region == null) 3 else 2)
    val finished = game.xp != null && reveal == null

    // The wrong place stays red for a moment.
    LaunchedEffect(game.wrongCount) {
        if (game.wrongCount == 0) return@LaunchedEffect
        delay(900)
        game.wrong = null
    }
    // `--es autoplay yes` places every province, `hold` stops on the first fact card, `wrong` misses once: for screenshots.
    LaunchedEffect(game) {
        val style = DebugFlags.autoplay ?: return@LaunchedEffect
        if (game.placed.isNotEmpty()) return@LaunchedEffect
        delay(1500)
        if (style == "wrong") {
            val first = game.queue.firstOrNull()
            val other = ids.firstOrNull { it != first }
            if (first != null && other != null) game.attempt(first, other)
            return@LaunchedEffect
        }
        while (true) {
            val next = game.queue.firstOrNull() ?: break
            game.attempt(next, next)
            if (style == "hold") return@LaunchedEffect
            delay(250)
            game.reveal = null
        }
        game.finish()
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { game.box = it }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (finished) PuzzleDone(game, ui, go) else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        region?.name?.invoke(ui) ?: if (game.hard) L("Mode difficile", "Hard mode") else L("Le grand défi", "The big challenge"),
                        style = textFont(14, W.HEAVY), color = Color.White,
                        modifier = Modifier.background(region?.tint ?: Coral, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    Meter(game.placed.size.toDouble() / max(1, ids.size).toDouble(), Modifier.weight(1f), color = Leaf, height = 12.dp)
                    Text("${game.placed.size}/${ids.size}", style = displayFont(18), color = LeafShade)
                }
                MapCanvas(
                    modifier = Modifier.onGloballyPositioned { game.map = it; game.mapWidth = it.size.width },
                    window = game.window, outlines = region != null || game.hard,
                    labels = game.placed.filter { region != null || it == reveal }.toSet(),
                    onTap = { id ->
                        // Tapping the place works as well as dragging: easier for small hands.
                        val piece = game.queue.firstOrNull()
                        if (piece != null && game.reveal == null && id != null && id !in game.placed) game.attempt(piece, id)
                    },
                    tone = { id ->
                        if (id in game.placed) MapTone.PLACED else if (id == game.wrong) MapTone.WRONG
                        else if (id == current && hinted) MapTone.HINT else if (id in ids) MapTone.SLOT else MapTone.QUIET
                    },
                )

                AnimatedContent(
                    if (reveal != null) "fact:$reveal" else current?.let { "tray:$it" },
                    Modifier.fillMaxWidth(),
                    transitionSpec = { (slideInVertically(tween(300)) { it } + fadeIn(tween(300))) togetherWith (slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) },
                    label = "tray",
                ) { shown ->
                    if (shown == null) Spacer(Modifier.fillMaxWidth())
                    else if (shown.startsWith("fact:")) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(L("Bien joué !", "Well done!"), style = displayFont(20), color = LeafShade)
                            FactCard(drc.province(shown.removePrefix("fact:")))
                            ChunkyButton(L("Continuer", "Continue"), kind = Chunky.GREEN) {
                                game.reveal = null
                                if (game.placed.size == ids.size) game.finish()
                            }
                        }
                    } else PuzzleTray(game, drc.province(shown.removePrefix("tray:")), ui, lift)
                }
            }
        }

        // The piece in the hand, drawn at the map's own scale.
        val finger = game.finger
        if (finger != null && current != null && game.mapWidth > 0 && !finished) {
            val p = drc.province(current)
            val scale = game.mapWidth / game.window.width
            val w = p.bounds.width * scale
            val h = p.bounds.height * scale
            val x = finger.x - (p.centre.x - p.bounds.center.x) * scale
            val y = finger.y - lift - (p.centre.y - p.bounds.center.y) * scale
            PieceShape(
                current,
                Modifier.offset { IntOffset((x - w / 2).roundToInt(), (y - h / 2).roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() }).alpha(0.94f),
                shadow = true,
            )
        }
    }
}

@Composable
private fun PuzzleTray(game: PuzzleGame, p: Province, ui: UiLang, lift: Float) {
    val note = game.note
    Row(
        Modifier.fillMaxWidth().card().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(118.dp)
                .onGloballyPositioned { game.tray = it }
                .pointerInput(p.id) {
                    // A drag that starts at once, as `DragGesture(minimumDistance: 0)` does.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = game.fromTray(down.position) ?: return@awaitEachGesture
                        Sfx.play(Sfx.Cue.POP); Haptic.tap(); game.note = null
                        game.finger = last
                        val ended = drag(down.id) { change ->
                            game.fromTray(change.position)?.let { last = it; game.finger = it }
                            change.consume()
                        }
                        if (ended) game.drop(p, last, lift) else game.finger = null
                    }
                }
                .alpha(if (game.finger == null) 1f else 0.3f)
                .background(Surface, RoundedCornerShape(20.dp)),
        ) { PieceShape(p.id, Modifier.fillMaxSize().padding(12.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(L("Glisse", "Drag").uppercase(), style = textFont(12, W.HEAVY), color = Muted, letterSpacing = 1.sp)
            BasicText(
                text = p.name, style = displayFont(25).copy(color = Ink), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 17.5.sp, maxFontSize = 25.sp, stepSize = 0.5.sp),
            )
            Text(L("sur la carte · ou touche sa place", "onto the map · or tap its place").uppercase(), style = textFont(12, W.HEAVY), color = Muted)
            Text(
                note ?: if (game.region != null) p.hint(ui) else L("À toi de trouver sa place.", "Work out where it goes."),
                style = textFont(15, W.BOLD), color = if (note == null) Ink2 else CoralShade, modifier = Modifier.padding(top = 4.dp),
            )
            if (game.queue.size > 1) {
                Text(
                    "${L("Plus tard", "Later")} ›", style = textFont(14, W.HEAVY), color = Sky,
                    modifier = Modifier.padding(top = 4.dp).semantics { role = Role.Button }.tappable {
                        Sfx.play(Sfx.Cue.TAP); game.note = null
                        val q = game.queue
                        if (q.size > 1) game.queue = q.drop(1) + q.first()
                    }.heightIn(min = 30.dp),
                )
            }
        }
    }
}

@Composable
private fun PuzzleDone(game: PuzzleGame, ui: UiLang, go: (MapScreen) -> Unit) {
    val drc = game.drc
    val ids = game.ids
    val region = game.region
    val nextRegion = region?.let { r -> drc.regions.indexOfFirst { it.id == r.id }.let { if (it >= 0 && it + 1 < drc.regions.size) drc.regions[it + 1] else null } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapCanvas(
                modifier = Modifier.heightIn(max = 300.dp), window = game.window, labels = if (ids.size <= 6) ids.toSet() else emptySet(),
                tone = { if (it in ids) MapTone.PLACED else MapTone.QUIET },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LobaView(mood = Mood.CHEER, size = 96.dp)
                MapStars(game.stars, 44.dp)
            }
            Text(
                if (region != null) L("Région terminée !", "Region complete!") else L("Tu connais tout le Congo !", "You know all of Congo!"),
                style = displayFont(28), color = Ink, textAlign = TextAlign.Center,
            )
            Text(
                "+${game.xp ?: 0} XP", style = displayFont(20), color = SunShade,
                modifier = Modifier.background(SunTint, CircleShape).padding(horizontal = 18.dp, vertical = 6.dp),
            )
            ChunkyButton(L("Petit quiz sur ces provinces", "Quick quiz on these provinces"), kind = Chunky.SUN) { go(MapScreen.Quiz(ids.shuffled().take(6))) }
            if (nextRegion != null) {
                ChunkyButton("${L("Région suivante", "Next region")} · ${nextRegion.name(ui)}", kind = Chunky.GREEN) { go(MapScreen.Puzzle(nextRegion.id)) }
            }
            ChunkyButton(L("Retour à la carte", "Back to the map"), kind = Chunky.OUTLINE) { go(MapScreen.Hub) }
        }
        Confetti(Modifier.matchParentSize())
    }
}

// MARK: - Quiz: recall without the piece in hand

private data class Question(val find: Boolean, val id: String, val choices: List<String>)

@Composable
private fun MapQuiz(ids: List<String>, go: (MapScreen) -> Unit) {
    val drc = DRC.shared
    val ui = AppStore.state.ui
    var queue by remember {
        mutableStateOf(ids.mapIndexed { i, id ->
            val others = drc.ids.filter { it != id && drc.province(it).capital != drc.province(id).capital }.shuffled().take(3)
            Question(find = i % 2 == 0, id = id, choices = (listOf(id) + others).shuffled())
        })
    }
    val total = remember { queue.size }
    var answer by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    var retried by remember { mutableStateOf(setOf<String>()) }

    fun pick(id: String, q: Question) {
        if (answer != null) return
        val right = id == q.id
        if (right) { Sfx.play(Sfx.Cue.CORRECT, score + 1); Haptic.success() } else { Sfx.play(Sfx.Cue.TICK); Haptic.thud() }
        answer = id
        if (right && q.id !in retried) score += 1
    }

    fun next(q: Question) {
        val missed = answer != q.id
        if (q.id !in retried) asked += 1
        // A missed question comes back once at the end: a second try is where it sticks.
        var rest = queue.drop(1)
        if (missed && q.id !in retried) rest = rest + q
        queue = rest
        answer = null
        if (missed) retried = retried + q.id
        if (rest.isEmpty()) Sfx.play(Sfx.Cue.COMPLETE)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val q = queue.firstOrNull()
        val picked = answer
        if (q != null) {
            val p = drc.province(q.id)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L("Quiz éclair", "Quick quiz"), style = textFont(14, W.HEAVY), color = Color.White,
                    modifier = Modifier.background(Plum, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Meter(asked.toDouble() / max(1, total).toDouble(), Modifier.weight(1f), color = Leaf, height = 12.dp)
                Text("${min(asked + 1, total)}/$total", style = displayFont(18), color = LeafShade)
            }
            MapCanvas(
                labels = if (picked == null) emptySet() else setOf(q.id),
                onTap = if (q.find && picked == null) { id -> if (id != null) pick(id, q) } else null,
                tone = { id ->
                    if (picked != null) (if (id == q.id) MapTone.RIGHT else if (id == picked) MapTone.WRONG else MapTone.OPEN)
                    else if (!q.find && id == q.id) MapTone.HINT else MapTone.OPEN
                },
            )
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    buildAnnotatedString {
                        if (q.find) {
                            append("${L("Touche", "Tap")} ")
                            withStyle(SpanStyle(color = SkyShade)) { append(p.name) }
                        } else {
                            withStyle(SpanStyle(color = SkyShade)) { append(p.name) }
                            append(L(" : quel est son chef-lieu ?", ": what is its capital?"))
                        }
                    },
                    style = displayFont(23), color = Ink,
                )
                if (!q.find) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (pair in q.choices.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (id in pair) {
                                    val state: Color? = if (picked == null) null else if (id == q.id) Leaf else if (id == picked) Coral else null
                                    Box(
                                        Modifier.weight(1f).card(selected = state != null, tint = state ?: Sky)
                                            .semantics { role = Role.Button }
                                            .tappable(enabled = picked == null) { pick(id, q) }
                                            .padding(10.dp).heightIn(min = 34.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(drc.province(id).capital, style = textFont(17, W.HEAVY), color = Ink, textAlign = TextAlign.Center)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (picked != null) {
                    val right = picked == q.id
                    Column(
                        Modifier.fillMaxWidth().background(if (right) LeafTint else CoralTint, RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val verdict = if (right) L("Exact !", "Correct!") else if (q.find) L("C’était ici.", "It was here.") else "${L("C’était", "It was")} ${p.capital}."
                        Text("$verdict ${p.emoji} ${p.fact(ui)}", style = textFont(15, W.BOLD), color = Ink)
                        ChunkyButton(L("Continuer", "Continue"), kind = Chunky.GREEN) { next(q) }
                    }
                }
            }
        } else if (total > 0) {
            Spacer(Modifier.weight(1f))
            LobaView(mood = if (score * 2 >= total) Mood.CHEER else Mood.HAPPY, size = 150.dp)
            Text(L("Ton score", "Your score"), style = displayFont(28), color = Ink)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = LeafShade, fontSize = 64.sp)) { append("$score") }
                    withStyle(SpanStyle(color = Muted, fontSize = 28.sp)) { append(" / $total") }
                },
                style = displayFont(64), color = Muted,
            )
            Spacer(Modifier.weight(1f))
            ChunkyButton(L("Rejouer", "Play again"), kind = Chunky.GREEN) { go(MapScreen.Quiz(drc.ids.shuffled().take(8))) }
            ChunkyButton(L("Retour à la carte", "Back to the map"), Modifier.padding(bottom = 12.dp), kind = Chunky.OUTLINE) { go(MapScreen.Hub) }
        }
    }
}

// MARK: - Atlas and book

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapAtlas() {
    val drc = DRC.shared
    val ui = AppStore.state.ui
    var id by remember { mutableStateOf<String?>(null) }
    val chosen = id
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MapCanvas(
            labels = if (chosen == null) emptySet() else setOf(chosen),
            onTap = { tapped ->
                if (tapped != null) {
                    Sfx.play(Sfx.Cue.TILE, max(0, drc.ids.indexOf(tapped))); Haptic.tap()
                    id = tapped
                }
            },
            tone = { if (it == chosen) MapTone.RIGHT else MapTone.PLACED },
        )
        if (chosen != null) FactCard(drc.province(chosen), full = true) else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                LobaView(mood = Mood.THINK, size = 84.dp)
                Bubble(Modifier.weight(1f, fill = false)) { Text(L("Touche une province.", "Tap a province."), style = textFont(15, W.BOLD), color = Ink2) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in drc.regions) {
                Text(
                    r.name(ui), style = textFont(13, W.HEAVY), color = Color.White,
                    modifier = Modifier.background(r.tint, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        MapCredits()
    }
}

@Composable
private fun MapBook() {
    val drc = DRC.shared
    val ui = AppStore.state.ui
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The iOS segmented picker, drawn by hand.
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Color(0x1F767680), RoundedCornerShape(9.dp)).padding(2.dp),
        ) {
            listOf(L("Histoire", "History"), L("Cultures", "Cultures"), L("En chiffres", "In numbers")).forEachIndexed { i, title ->
                val on = tab == i
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp)
                        .then(if (on) Modifier.background(Color.White, RoundedCornerShape(7.dp)) else Modifier)
                        .semantics { role = Role.Tab }
                        .tappable { tab = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, style = textFont(13, if (on) W.HEAVY else W.BOLD), color = Ink, maxLines = 1)
                }
            }
        }
        val cards = when (tab) { 0 -> drc.history; 1 -> drc.culture; else -> drc.figures }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(cards) { card ->
                Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val year = card.year
                    if (year != null) {
                        Text(
                            year, style = displayFont(14), color = hexColor("3b2a00"),
                            modifier = Modifier.background(Sun, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        val emoji = card.emoji
                        if (emoji != null) Text(emoji, fontSize = 30.sp, color = Ink)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.title(ui), style = displayFont(19), color = Ink)
                            Text(card.text(ui), style = if (tab == 2) displayFont(18) else textFont(15), color = if (tab == 2) SkyShade else Ink2)
                        }
                    }
                }
            }
        }
    }
}
