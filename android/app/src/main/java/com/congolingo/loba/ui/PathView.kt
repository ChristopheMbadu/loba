package com.congolingo.loba.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Game
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.LessonUnit
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.Voice
import com.congolingo.loba.core.lessonsPerUnit
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TabsView() {
    var tab by rememberSaveable { mutableStateOf(mapOf("practice" to 1, "quests" to 2, "shop" to 3, "profile" to 4)[DebugFlags.screen ?: ""] ?: 0) }
    val pages = rememberSaveableStateHolder()
    val s = AppStore.state
    val claimable = Rules.dailyQuests(s, AppStore.today).count { it.done && it.id.raw !in s.daily.claimed }
    val pick = { next: Int -> if (next != tab) { tab = next; Sfx.play(Sfx.Cue.TAP); Haptic.tap() } }

    val page: @Composable () -> Unit = {
        // Each tab keeps its scroll position while another one is shown.
        pages.SaveableStateProvider(tab) {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                StatsBar()
                when (tab) {
                    0 -> PathView()
                    1 -> PracticeView()
                    2 -> QuestsView()
                    3 -> ShopView()
                    else -> ProfileView()
                }
            }
        }
    }
    // Built by hand, not a material NavigationBar: the artwork keeps its own colours.
    val items: @Composable (Modifier) -> Unit = { each ->
        TabItem("navLearn", L("Apprendre", "Learn"), tab == 0, each) { pick(0) }
        TabItem("dumbbell", L("Pratique", "Practice"), tab == 1, each) { pick(1) }
        TabItem("target", L("Quêtes", "Quests"), tab == 2, each, claimable) { pick(2) }
        TabItem("navShop", L("Boutique", "Shop"), tab == 3, each) { pick(3) }
        TabItem("navProfile", L("Profil", "Profile"), tab == 4, each) { pick(4) }
    }

    if (LocalConfiguration.current.screenWidthDp >= 600) {
        // Tablets: the tabs run down the left side, where a thumb holds the tablet.
        Row(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Column(
                Modifier.width(96.dp).fillMaxHeight()
                    .drawBehind { drawRect(Line, Offset(size.width - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height)) }
                    .navigationBarsPadding().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { items(Modifier.fillMaxWidth().heightIn(min = 64.dp)) }
            Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { page() }
            Row(
                Modifier.fillMaxWidth().background(Color.White)
                    .drawBehind { drawRect(Line, Offset.Zero, Size(size.width, 2.dp.toPx())) }
                    .navigationBarsPadding().padding(top = 4.dp, bottom = 2.dp),
            ) { items(Modifier.weight(1f)) }
        }
    }
}

/** The artwork is drawn at 30 points, as in the iOS tab bar. */
@Composable
private fun TabItem(art: String, label: String, selected: Boolean, modifier: Modifier, badge: Int = 0, onClick: () -> Unit) {
    Column(
        modifier
            .selectable(selected, remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box {
            Art(art, 30.dp)
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-3).dp).defaultMinSize(18.dp, 18.dp)
                        .background(Coral, CircleShape).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$badge", style = textFont(12, W.HEAVY), color = Color.White) }
            }
        }
        Text(label, style = textFont(11, W.BOLD), color = if (selected) Sky else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - The river path

private val unitColors: List<Pair<Color, Color>> =
    listOf(Sky to SkyShade, Leaf to LeafShade, Plum to PlumShade, Coral to CoralShade, hexColor("ff8a1f") to hexColor("d96c05"))

data class LessonTarget(val unit: LessonUnit, val index: Int) {
    val id: String get() = "${unit.id}-$index"
}

@Composable
fun PathView() {
    var picked by remember { mutableStateOf<LessonTarget?>(null) }
    var playing by remember { mutableStateOf<LessonTarget?>(null) }
    var guide by remember { mutableStateOf<LessonUnit?>(null) }
    var noHearts by remember { mutableStateOf(false) }

    val units = AppStore.units
    val s = AppStore.state
    val current = Rules.currentUnitIndex(s, units)
    val list = rememberLazyListState()
    // The today card is the first row, so unit i is row i + 1.
    LaunchedEffect(Unit) { if (current > 0 && current < units.size) list.scrollToItem(current + 1) }

    LazyColumn(Modifier.fillMaxSize().readable(), state = list) {
        item(key = "loba-today") { TodayCard() }
        itemsIndexed(units, key = { _, unit -> unit.id }) { i, unit ->
            UnitSection(
                unit = unit, number = i + 1, colors = unitColors[i % unitColors.size], unlocked = Rules.unitUnlocked(s, units, i),
                isCurrent = i == current, flip = i % 2 != 0, guide = { guide = unit },
            ) { index -> picked = LessonTarget(unit, index) }
        }
        item(key = "loba-end") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                KubaBand(Modifier.padding(top = 8.dp))
                Text(
                    L("D’autres unités arrivent bientôt.", "More units are on their way."), style = textFont(15), color = Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    picked?.let { target ->
        Sheet(onDismiss = { picked = null }) {
            StartSheet(target) {
                picked = null
                if (AppStore.state.hearts > 0) playing = target else noHearts = true
            }
        }
    }
    guide?.let { unit -> Sheet(onDismiss = { guide = null }, expanded = false) { GuideSheet(unit) } }
    playing?.let { target ->
        FullScreenCover(onDismiss = { playing = null }) { LessonView(LessonMode.Lesson(target.unit, target.index), onClose = { playing = null }) }
    }
    if (noHearts) {
        val refill = Rules.canBuy(AppStore.state, Rules.ShopItem.REFILL, Game.now())
        AlertDialog(
            onDismissRequest = { noHearts = false },
            containerColor = Color.White,
            title = { Text(L("Plus de cœurs", "Out of hearts"), style = displayFont(22), color = Ink) },
            text = {
                Text(
                    L("Un cœur revient toutes les 30 minutes. La pratique, elle, n’en coûte jamais.", "A heart comes back every 30 minutes. Practice never costs any."),
                    style = textFont(16), color = Ink2,
                )
            },
            confirmButton = { TextButton(onClick = { noHearts = false }) { Text("OK", style = textFont(16, W.HEAVY), color = Sky) } },
            dismissButton = if (refill) {
                {
                    TextButton(onClick = { AppStore.buy(Rules.ShopItem.REFILL); Sfx.play(Sfx.Cue.HEART); noHearts = false }) {
                        Text(L("Recharger (50 gemmes)", "Refill (50 gems)"), style = textFont(16, W.BOLD), color = Sky)
                    }
                }
            } else null,
        )
    }
}

private val pathSpacing = 104.dp
private val pathTop = 104.dp

/** `.position(x:y:)` on iOS: the content's centre is put at this point of the river area. */
@Composable
private fun At(x: Dp, y: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            for (p in placeables) p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2)
        }
    }
}

@Composable
private fun UnitSection(
    unit: LessonUnit, number: Int, colors: Pair<Color, Color>, unlocked: Boolean, isCurrent: Boolean, flip: Boolean,
    guide: () -> Unit, pick: (Int) -> Unit,
) {
    val done = Rules.unitProgress(AppStore.state, unit)
    val ui = AppStore.state.ui
    // Sideways drift of stone i from the middle, in dp.
    fun x(i: Int): Float = sin(i * 1.05 + 0.4).toFloat() * 62f * (if (flip) -1 else 1)
    fun y(i: Int): Dp = pathTop + pathSpacing * i

    Column(Modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(22.dp)
        val face = if (unlocked) colors.first else hexColor("b9b3c6")
        val edge = if (unlocked) colors.second else hexColor("9b95ab")
        Box(
            Modifier.zIndex(1f).padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth()
                .drawBehind { drawRoundRect(edge, Offset(0f, 5.dp.toPx()), size, CornerRadius(22.dp.toPx())) }
                .clip(shape).background(face),
        ) {
            KubaWeave(Modifier.matchParentSize())
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${L("Unité", "Unit")} $number · ${unit.words.size} ${L("mots", "words")}".uppercase(),
                        style = textFont(13, W.HEAVY), letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(unit.name(ui), style = displayFont(22), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (unit.tip.isNotEmpty()) {
                    Box(
                        Modifier.defaultMinSize(minHeight = 44.dp).tappable { Sfx.play(Sfx.Cue.TAP); guide() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp), tint = Color.White)
                            Text(L("Guide", "Guide"), style = textFont(14, W.HEAVY), color = Color.White)
                        }
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(pathTop + pathSpacing * lessonsPerUnit + 70.dp)) {
            val width = maxWidth
            Riverbed(unlocked, flip)
            for (i in 0 until lessonsPerUnit) {
                val state = if (!unlocked) NodeState.LOCKED else if (i < done) NodeState.DONE else if (i == done) NodeState.CURRENT else NodeState.LOCKED
                At(width / 2 + x(i).dp, y(i)) { NodeView(state, i, colors) { pick(i) } }
            }
            At(width / 2 + x(lessonsPerUnit).dp, y(lessonsPerUnit)) { ChestView(unit) }
            if (isCurrent) {
                val at = min(done, lessonsPerUnit - 1)
                At(if (x(at) > 0) 70.dp else width - 70.dp, y(at) + 8.dp) { LobaView(mood = if (done == 0) Mood.WAVE else Mood.HAPPY, size = 104.dp) }
            } else if (unlocked) {
                At(if (x(1) > 0) 64.dp else width - 64.dp, y(1) + 20.dp) { Text(unit.icon, fontSize = 54.sp, color = Ink, modifier = Modifier.alpha(0.9f)) }
            }
        }
    }
}

/** The Congo river, running under the lesson stones. */
@Composable
private fun Riverbed(unlocked: Boolean, flip: Boolean) {
    val density = LocalDensity.current.density
    val clock = rememberAmbient(paused = !unlocked)
    // Built once per width, shared by the two canvases.
    val built = remember(flip, density) { arrayOfNulls<Pair<Float, Path>>(1) }
    fun path(width: Float): Path {
        built[0]?.let { if (it.first == width) return it.second }
        fun x(i: Int): Float = sin(i * 1.05 + 0.4).toFloat() * 62f * (if (flip) -1 else 1) * density
        fun point(i: Int) = Offset(width / 2 + x(i), (pathTop.value + i * pathSpacing.value) * density)
        val pts = listOf(Offset(width / 2 + x(0) * 0.4f, -30 * density)) + (0..lessonsPerUnit).map { point(it) } +
            Offset(width / 2, (pathTop.value + lessonsPerUnit * pathSpacing.value + 110) * density)
        val p = Path()
        p.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val midY = (a.y + b.y) / 2
            p.cubicTo(a.x, midY, b.x, midY, b.x, b.y)
        }
        built[0] = width to p
        return p
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = path(size.width)
        drawPath(p, Sand, style = Stroke(84.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
        drawPath(p, if (unlocked) hexColor("d5ebfb") else hexColor("ecebf0"), style = Stroke(68.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
    }
    // The current: dashes that drift downstream, and a second, fainter set that moves slower.
    // Its own canvas, so only the dashes are redrawn on each tick.
    Canvas(Modifier.fillMaxSize()) {
        val p = path(size.width)
        val t = clock.value
        fun dashes(on: Float, off: Float, speed: Double): PathEffect {
            val period = (on + off).toDouble()
            val phase = ((-t * speed) % period + period) % period
            return PathEffect.dashPathEffect(floatArrayOf(on.dp.toPx(), off.dp.toPx()), phase.toFloat().dp.toPx())
        }
        drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes(12f, 22f, 26.0)))
        translate(left = 15.dp.toPx()) {
            drawPath(p, Color.White.copy(alpha = 0.45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes(5f, 44f, 15.0)))
        }
        translate(left = -17.dp.toPx()) {
            drawPath(p, Color.White.copy(alpha = 0.45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes(7f, 58f, 19.0)))
        }
    }
}

private enum class NodeState { DONE, CURRENT, LOCKED }

@Composable
private fun NodeView(state: NodeState, index: Int, colors: Pair<Color, Color>, action: () -> Unit) {
    val face = when (state) { NodeState.LOCKED -> Line; NodeState.DONE -> Sun; NodeState.CURRENT -> colors.first }
    val edge = when (state) { NodeState.LOCKED -> LineShade; NodeState.DONE -> SunShade; NodeState.CURRENT -> colors.second }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed) 5.dp else 0.dp, spring(stiffness = 1500f), label = "press")

    // Ripples (0 → 1, the second half a period later) and the bobbing "Start" label.
    val still = remember { mutableFloatStateOf(0f) }
    val rest = remember { mutableFloatStateOf(-38f) }
    var ripples: List<State<Float>> = listOf(still, still)
    var bob: State<Float> = rest
    if (state == NodeState.CURRENT && !Motion.still) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        ripples = listOf(
            pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = EaseOut), RepeatMode.Restart), label = "ripple0"),
            pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = EaseOut), RepeatMode.Restart, StartOffset(900)), label = "ripple1"),
        )
        bob = pulse.animateFloat(-38f, -44f, infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse), label = "bob")
    }

    val icon = when (state) {
        NodeState.LOCKED -> Icons.Rounded.Lock
        NodeState.DONE -> Icons.Rounded.Check
        NodeState.CURRENT -> listOf(Icons.Rounded.Star, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Rounded.VolumeUp, Icons.Rounded.FitnessCenter)[index % 4]
    }
    Box(Modifier.size(78.dp, 70.dp)) {
        Box(
            Modifier.matchParentSize().offset { IntOffset(0, drop.roundToPx()) }
                .drawBehind {
                    if (state == NodeState.CURRENT) {
                        // Ripples spreading from the stone the learner is on.
                        for (r in ripples) {
                            val t = r.value
                            val scale = 1f + 0.75f * t
                            val oval = Size(size.width * scale, size.height * scale)
                            drawOval(
                                colors.first.copy(alpha = 0.55f * (1f - t)), Offset((size.width - oval.width) / 2, (size.height - oval.height) / 2 + 4.dp.toPx()),
                                oval, style = Stroke(4.dp.toPx() * scale),
                            )
                        }
                    }
                    drawOval(edge, Offset(0f, 8.dp.toPx()), size)
                    drawOval(face, Offset.Zero, size)
                    val shine = Size(30.dp.toPx(), 16.dp.toPx())
                    drawOval(Color.White.copy(alpha = 0.28f), Offset(center.x - 12.dp.toPx() - shine.width / 2, center.y - 18.dp.toPx() - shine.height / 2), shine)
                }
                .clickable(interaction, indication = null, enabled = state != NodeState.LOCKED, role = Role.Button) {
                    Sfx.play(Sfx.Cue.TAP); Haptic.tap(); action()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, "${L("Leçon", "Lesson")} ${index + 1}", Modifier.size(30.dp), tint = if (state == NodeState.LOCKED) Muted else Color.White)
        }
        if (state == NodeState.CURRENT) {
            val shape = RoundedCornerShape(11.dp)
            Text(
                L("Commencer", "Start").uppercase(), style = textFont(13, W.HEAVY), color = colors.first, maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.TopCenter).wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .offset { IntOffset(0, (bob.value * density).roundToInt()) }
                    .drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(0f, 3.dp.toPx()), size, CornerRadius(11.dp.toPx())) }
                    .background(Color.White, shape).border(2.dp, Line, shape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ChestView(unit: LessonUnit) {
    val ready = Rules.unitDone(AppStore.state, unit)
    val opened = unit.id in AppStore.state.chests
    var burst by remember { mutableStateOf(false) }
    LaunchedEffect(burst) { if (burst) { delay(2400); burst = false } }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed) 5.dp else 0.dp, spring(stiffness = 1500f), label = "press")
    // A chest waiting to be opened rocks gently.
    val waiting = ready && !opened
    val still = remember { mutableFloatStateOf(4f) }
    var tilt: State<Float> = still
    if (waiting && !Motion.still) {
        tilt = rememberInfiniteTransition(label = "chest").animateFloat(0f, 4f, infiniteRepeatable(tween(500, easing = EaseInOut), RepeatMode.Reverse), label = "tilt")
    }
    val label = L("Coffre", "Chest")
    Box(Modifier.size(78.dp)) {
        Art(
            if (opened) "chestOpen" else if (ready) "chest" else "chestLocked", 78.dp,
            Modifier.offset { IntOffset(0, drop.roundToPx()) }
                .graphicsLayer { rotationZ = if (waiting) tilt.value else 0f }
                .semantics { contentDescription = label }
                .clickable(interaction, indication = null, enabled = waiting, role = Role.Button) {
                    AppStore.openChest(unit)
                    Sfx.play(Sfx.Cue.CHEST); Haptic.success()
                    burst = true
                },
        )
        AnimatedVisibility(
            burst, Modifier.align(Alignment.TopCenter).wrapContentSize(Alignment.TopCenter, unbounded = true).offset(y = (-44).dp),
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 300f)) + slideInVertically { it } + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Row(
                Modifier.padding(6.dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Diamond, null, Modifier.size(18.dp), tint = Sky)
                Text("+${Game.chestReward}", style = textFont(18, W.HEAVY), color = Sky, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StartSheet(target: LessonTarget, start: () -> Unit) {
    val replay = target.index < Rules.unitProgress(AppStore.state, target.unit)
    val steps = listOf(
        L("Découvre les premiers mots", "Meet the first words"), L("Découvre la suite", "Meet the rest"),
        L("Mélange et premières phrases", "Mix and first sentences"), L("Écris et construis", "Type and build"),
    )
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(target.unit.name(AppStore.state.ui), style = displayFont(24), color = Ink)
        Text(
            "${L("Leçon", "Lesson")} ${target.index + 1} ${L("sur", "of")} $lessonsPerUnit · " + steps[target.index % 4],
            style = textFont(16), color = Ink2,
        )
        // The iOS sheet is 250 points tall: this keeps the button clear of the text.
        Spacer(Modifier.height(24.dp))
        ChunkyButton(if (replay) L("Refaire · +10 XP", "Replay · +10 XP") else L("Commencer · +10 XP", "Start · +10 XP"), kind = Chunky.GREEN, onClick = start)
    }
}

@Composable
private fun GuideSheet(unit: LessonUnit) {
    val ui = AppStore.state.ui
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            LobaView(mood = Mood.THINK, size = 84.dp)
            Bubble(Modifier.weight(1f, fill = false).padding(bottom = 16.dp)) { Text(unit.tip, style = textFont(16), color = Ink) }
        }
        Text(L("Les mots de l’unité", "Words in this unit").uppercase(), style = textFont(14, W.HEAVY), letterSpacing = 1.sp, color = Muted)
        unit.words.forEachIndexed { i, w ->
            Row(
                Modifier.fillMaxWidth().card().tappable { Sfx.play(Sfx.Cue.TILE, i); Voice.say(w) }.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(w.term, style = textFont(17, W.HEAVY), color = SkyShade)
                Text(w.meaning(ui), style = textFont(16), color = Ink2, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                if (w.audio != null) Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(22.dp), tint = Sky)
            }
        }
    }
}

/** Today at a glance: the daily goal filling up, and the week's streak. */
@Composable
private fun TodayCard() {
    val s = AppStore.state
    val xp = if (s.daily.day == AppStore.today) s.daily.xp else 0
    val done = xp >= s.goal
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).fillMaxWidth()
            .drawBehind { drawRoundRect(hexColor("0f5f99"), Offset(0f, 5.dp.toPx()), size, CornerRadius(24.dp.toPx())) }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(hexColor("2aa9f5"), SkyShade))),
    ) {
        KubaWeave(Modifier.matchParentSize())
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GoalRing(xp.toDouble() / max(1, s.goal).toDouble())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (done) L("Objectif atteint !", "Goal reached!") else L("Objectif du jour", "Today’s goal"), style = displayFont(20), color = Color.White)
                    Text("${min(xp, s.goal)} / ${s.goal} XP", style = textFont(15, W.BOLD), color = Color.White.copy(alpha = 0.85f))
                }
                LobaView(mood = if (done) Mood.CHEER else Mood.HAPPY, size = 62.dp)
            }
            Box(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(vertical = 10.dp, horizontal = 6.dp)) {
                WeekStrip(Modifier.fillMaxWidth())
            }
        }
    }
}
