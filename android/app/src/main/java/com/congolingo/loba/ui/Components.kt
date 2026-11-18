package com.congolingo.loba.ui

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.UiLang
import com.congolingo.loba.core.appContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// MARK: - Motion

object Motion {
    /** True when the learner asked Android to remove animations. Decorative motion then stands still. */
    val still: Boolean by lazy {
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f || DebugFlags.still
    }
}

/**
 * The clock for decorative motion (Loba breathing, clouds, the river): seconds since it started, about 30 times
 * a second. Read `.value` inside a Canvas draw block so only the drawing is redone, not the whole screen.
 * This is `TimelineView(ambient())` on iOS.
 */
@Composable
fun rememberAmbient(paused: Boolean = false): State<Double> {
    val seconds = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(paused) {
        if (paused || Motion.still) return@LaunchedEffect
        val offset = seconds.doubleValue
        var first = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (first < 0) first = now
                if (now - last >= 30_000_000L) {
                    last = now
                    seconds.doubleValue = offset + (now - first) / 1e9
                }
            }
        }
    }
    return seconds
}

// MARK: - Taps

/** A tap with no ripple, for custom-drawn controls. */
fun Modifier.tappable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
}

// MARK: - Buttons with a pressable bottom edge

enum class Chunky { GREEN, SKY, SUN, OUTLINE, DANGER }

/** `Button(...).buttonStyle(.chunky(.green))` on iOS. The label is shown in capitals. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: Chunky = Chunky.GREEN,
    small: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (face, edge, ink) = if (!enabled) Triple(Line, LineShade, Muted) else when (kind) {
        Chunky.GREEN -> Triple(Leaf, LeafShade, Color.White)
        Chunky.SKY -> Triple(Sky, SkyShade, Color.White)
        Chunky.SUN -> Triple(Sun, SunShade, Ink)
        Chunky.OUTLINE -> Triple(Color.White, Line, Sky)
        Chunky.DANGER -> Triple(Coral, CoralShade, Color.White)
    }
    val depth by animateDpAsState(if (pressed) 0.dp else 5.dp, spring(stiffness = 1500f), label = "depth")
    LaunchedEffect(pressed) { if (pressed) { Sfx.play(Sfx.Cue.TAP); Haptic.tap() } }
    val shape = RoundedCornerShape(18.dp)
    Box(
        (if (small) modifier else modifier.fillMaxWidth())
            .padding(bottom = 5.dp)
            .drawBehind { drawRoundRect(edge, Offset(0f, 5.dp.toPx()), size, CornerRadius(18.dp.toPx())) }
            .offset(y = 5.dp - depth)
            .clip(shape)
            .background(face)
            .then(if (kind == Chunky.OUTLINE && enabled) Modifier.border(2.dp, Line, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = if (small) 10.dp else 16.dp, horizontal = if (small) 18.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(text.uppercase(), style = textFont(if (small) 15 else 17, W.HEAVY), color = ink, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A white card with the soft bottom edge used all over Loba: `.card(selected:tint:)` on iOS. Put padding after it. */
fun Modifier.card(selected: Boolean = false, tint: Color = Sky): Modifier = this
    .padding(bottom = 4.dp)
    .drawBehind {
        val r = CornerRadius(20.dp.toPx())
        val border = if (selected) tint else Line
        drawRoundRect(border, Offset(0f, 4.dp.toPx()), size, r)
        drawRoundRect(Color.White, Offset.Zero, size, r)
        if (selected) drawRoundRect(tint.copy(alpha = 0.12f), Offset.Zero, size, r)
        val w = 2.dp.toPx()
        drawRoundRect(border, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(20.dp.toPx() - w / 2), style = Stroke(w))
    }

// MARK: - Artwork

private val artIds = HashMap<String, Int>()

/** The drawable for a picture named as on iOS: "art-chestLocked", "story-back-3", "RaffiaSplash". 0 when there is none. */
@SuppressLint("DiscouragedApi")
fun drawableId(iosName: String): Int = artIds.getOrPut(iosName) {
    val name = iosName.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace('-', '_').lowercase()
    appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
}

/** One of Loba's illustrated icons (flame, gem, heart, chest…): `Art(name: "gem", size: 26)` on iOS. */
@Composable
fun Art(name: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val id = drawableId("art-$name")
    if (id != 0) Image(painterResource(id), null, modifier.size(size), contentScale = ContentScale.Fit) else Spacer(modifier.size(size))
}

/** A band of Kuba cloth: the diamond pattern woven in the Kasaï. */
@Composable
fun KubaBand(modifier: Modifier = Modifier, color: Color = Cocoa, background: Color = Sand, height: Dp = 16.dp) {
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        drawRect(background)
        val h = size.height
        val w = h * 2
        var x = -w / 2
        while (x < size.width + w) {
            val diamond = Path().apply {
                moveTo(x, h / 2); lineTo(x + w / 2, 1f); lineTo(x + w, h / 2); lineTo(x + w / 2, h - 1); close()
            }
            drawPath(diamond, color.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
            drawCircle(color, h * 0.14f, Offset(x + w / 2, h / 2))
            x += w
        }
        drawRect(color, Offset.Zero, Size(size.width, 2.dp.toPx()))
        drawRect(color, Offset(0f, h - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
    }
}

/** The diamond weave drawn faintly over coloured banners. Give it `Modifier.matchParentSize()` inside a Box. */
@Composable
fun KubaWeave(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(0.dp))) {
        val step = 44.dp.toPx()
        var y = -step / 2
        var row = 0
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width + step) {
                for (scale in floatArrayOf(1f, 0.6f, 0.25f)) {
                    val r = step / 2 * scale
                    val d = Path().apply { moveTo(x - r, y); lineTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); close() }
                    if (scale == 0.25f) drawPath(d, Color.White.copy(alpha = 0.16f)) else drawPath(d, Color.White.copy(alpha = 0.13f), style = Stroke(2.dp.toPx()))
                }
                x += step
            }
            y += step / 2
            row += 1
        }
    }
}

@Composable
fun Meter(value: Double, modifier: Modifier = Modifier, color: Color = Leaf, height: Dp = 16.dp) {
    val shown by animateFloatAsState(value.toFloat().coerceIn(0f, 1f), spring(dampingRatio = 0.6f, stiffness = 200f), label = "meter")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        drawRoundRect(Line, cornerRadius = CornerRadius(h / 2))
        if (value > 0) {
            val w = max(h, size.width * shown.coerceIn(0f, 1f))
            drawRoundRect(color, size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            val inset = h * 0.4f
            if (w > inset * 2) drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(inset, h * 0.2f), Size(w - inset * 2, h * 0.28f), CornerRadius(h * 0.14f))
        }
    }
}

@Composable
fun LangBadge(code: String, color: Color = Sky, modifier: Modifier = Modifier) {
    Text(
        code.uppercase(), style = textFont(13, W.HEAVY), color = Color.White,
        modifier = modifier
            .padding(bottom = 2.5.dp)
            .drawBehind {
                val r = CornerRadius(9.dp.toPx())
                drawRoundRect(color, Offset(0f, 2.5.dp.toPx()), size, r)
                drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(0f, 2.5.dp.toPx()), size, r)
                drawRoundRect(color, Offset.Zero, size, r)
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** Streak, gems and hearts, across the top of every tab. */
@Composable
fun StatsBar(modifier: Modifier = Modifier) {
    val s = AppStore.state
    val streak = Rules.visibleStreak(s, AppStore.today)
    Row(
        modifier.fillMaxWidth().background(Color.White)
            .drawBehind { drawRect(Line, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        LangBadge(s.lang, hexColor(AppStore.language?.color ?: "1c9bef"))
        Stat(if (streak > 0) "flame" else "flameOff", "$streak", if (streak > 0) SunShade else Muted)
        Stat("gem", "${s.gems}", Sky)
        Stat(if (s.hearts > 0) "heart" else "heartOff", "${s.hearts}", Coral)
    }
}

@Composable
private fun Stat(art: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(art, 26.dp)
        Text(value, style = textFont(18, W.HEAVY), color = color)
    }
}

/** Paper streamers in the flag's colours, thrown once. Give it `Modifier.matchParentSize()` or `fillMaxSize()`. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    class Piece(val x: Float, val drift: Float, val speed: Float, val spin: Float, val size: Float, val delay: Float, val color: Color)
    val pieces = remember {
        List(90) {
            Piece(Random.nextFloat(), Random.nextFloat() * 0.5f - 0.25f, 0.55f + Random.nextFloat() * 0.55f, Random.nextFloat() * 12f - 6f,
                7f + Random.nextFloat() * 6f, Random.nextFloat() * 0.5f, listOf(Sky, Sun, Coral, Leaf, Plum).random())
        }
    }
    val clock = rememberAmbient()
    Canvas(modifier) {
        val t = clock.value.toFloat()
        for (p in pieces) {
            val life = t - p.delay
            if (life <= 0 || life >= 3.4f) continue
            val y = -20.dp.toPx() + life * p.speed * size.height * 0.5f + life * life * 60.dp.toPx()
            val x = p.x * size.width + sin(life * 3 + p.x * 10) * 18.dp.toPx() + p.drift * life * size.width * 0.3f
            val s = p.size.dp.toPx()
            translate(x, y) {
                rotateRad(life * p.spin, Offset.Zero) {
                    drawRoundRect(p.color.copy(alpha = min(1f, (3.4f - life) * 2)), Offset(-s / 2, -s / 4), Size(s, s / 2), CornerRadius(2.dp.toPx()))
                }
            }
        }
    }
}

/** FR | EN, wherever the interface language can be changed. */
@Composable
fun UiSwitch(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Row(modifier.clip(shape).background(Surface).border(2.dp, Line, shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (lang in listOf(UiLang.FR, UiLang.EN)) {
            val on = AppStore.state.ui == lang
            Text(
                lang.raw.uppercase(), style = textFont(13, W.HEAVY), color = if (on) Sky else Muted,
                modifier = Modifier
                    .drawBehind {
                        if (on) {
                            val r = CornerRadius(10.dp.toPx())
                            drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(0f, 2.dp.toPx()), size, r)
                            drawRoundRect(Color.White, Offset.Zero, size, r)
                        }
                    }
                    .tappable { Sfx.play(Sfx.Cue.TAP); AppStore.setUi(lang) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
}

/** A speech bubble next to Loba. */
@Composable
fun Bubble(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(Color.White).border(2.dp, Line, shape).padding(horizontal = 18.dp, vertical = 14.dp), content = content)
}

// MARK: - Text fields

/** `.fieldStyle()` on iOS: the sand-coloured box around a text field. */
fun Modifier.fieldStyle(): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return clip(shape).background(Surface).border(2.dp, Line, shape).padding(14.dp)
}

@Composable
fun LobaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    BasicTextField(
        value, onValueChange, modifier.fillMaxWidth().fieldStyle(), enabled = enabled, singleLine = singleLine,
        textStyle = textFont(17).copy(color = Ink), cursorBrush = SolidColor(Sky),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = textFont(17), color = Muted)
                inner()
            }
        },
    )
}

// MARK: - Covers and sheets

class CoverHost {
    class Entry(val id: Int, val content: State<@Composable () -> Unit>, val dismiss: State<() -> Unit>)
    val stack = mutableStateListOf<Entry>()
    var next = 0
}

val LocalCoverHost = compositionLocalOf<CoverHost?> { null }

/**
 * `.fullScreenCover` on iOS: a screen laid over the whole app, from the bottom. Call it while the cover should
 * be shown: `if (story) FullScreenCover(onDismiss = { story = false }) { StoryView(onClose = { story = false }) }`.
 * The system back button calls `onDismiss`. The content is not padded for the system bars: screens do that themselves.
 */
@Composable
fun FullScreenCover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val host = LocalCoverHost.current ?: return
    val current = rememberUpdatedState(content)
    val dismiss = rememberUpdatedState(onDismiss)
    DisposableEffect(host) {
        val entry = CoverHost.Entry(host.next++, current, dismiss)
        host.stack.add(entry)
        onDispose { host.stack.remove(entry) }
    }
}

/** Wraps the whole app; covers are drawn over `content`. */
@Composable
fun CoverStack(content: @Composable () -> Unit) {
    val host = remember { CoverHost() }
    CompositionLocalProvider(LocalCoverHost provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            for (entry in host.stack) {
                key(entry.id) {
                    val slide = remember { Animatable(1f) }
                    LaunchedEffect(Unit) { slide.animateTo(0f, tween(if (Motion.still) 0 else 280)) }
                    BackHandler { entry.dismiss.value() }
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { translationY = slide.value * size.height }
                            .background(Color.White)
                            // Nothing underneath can be touched.
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) { entry.content.value() }
                }
            }
        }
    }
}

/** `.sheet` on iOS: a panel that slides up over the screen and can be dragged away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
        containerColor = Color.White, contentColor = Ink, content = content,
    )
}
