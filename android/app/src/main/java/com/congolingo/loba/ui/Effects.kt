package com.congolingo.loba.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Game
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private fun between(from: Double, to: Double): Double = from + Random.nextDouble() * (to - from)

/** The ambient clock started from the time of day, so a still frame is not always the same one (iOS draws from the date). */
@Composable
private fun rememberSkyClock(): () -> Double {
    val base = remember { (System.currentTimeMillis() % 3_600_000L) / 1000.0 }
    val clock = rememberAmbient()
    return { base + clock.value }
}

/** A puff of coloured sparks, fired each time `trigger` changes. Used when an answer is right. */
@Composable
fun Burst(trigger: Int, modifier: Modifier = Modifier, colors: List<Color> = listOf(Sun, Leaf, Sky, Coral)) {
    class Spark(val angle: Double, val speed: Double, val size: Double, val spin: Double)
    val sparks = remember { List(26) { Spark(between(0.0, 2 * PI), between(90.0, 240.0), between(5.0, 11.0), between(-8.0, 8.0)) } }
    val seen = remember { intArrayOf(trigger) }
    // Seconds since it was fired; negative when there is nothing to draw.
    val elapsed = remember { mutableDoubleStateOf(-1.0) }
    LaunchedEffect(trigger) {
        if (seen[0] == trigger) return@LaunchedEffect
        seen[0] = trigger
        try {
            val start = withFrameNanos { it }
            elapsed.doubleValue = 0.0
            while (elapsed.doubleValue < 0.9) elapsed.doubleValue = withFrameNanos { (it - start) / 1e9 }
        } finally {
            elapsed.doubleValue = -1.0
        }
    }
    Canvas(modifier) {
        val t = elapsed.doubleValue
        if (t < 0 || t >= 0.9 || colors.isEmpty()) return@Canvas
        val d = density
        val ease = 1 - (1 - min(1.0, t / 0.9)).pow(3)
        sparks.forEachIndexed { i, s ->
            val x = size.width / 2 + (cos(s.angle) * s.speed * ease).toFloat() * d
            val y = size.height / 2 + (sin(s.angle) * s.speed * ease + t * t * 220).toFloat() * d
            val w = s.size.toFloat() * d
            val color = colors[i % colors.size]
            val opacity = max(0.0, 1 - t / 0.9).toFloat()
            withTransform({ translate(x, y); rotate(Math.toDegrees(t * s.spin).toFloat(), Offset.Zero) }) {
                if (i % 3 == 0) drawOval(color, Offset(-w / 2, -w / 2), Size(w, w), alpha = opacity)
                else drawRoundRect(color, Offset(-w / 2, -w / 4), Size(w, w / 2), CornerRadius(2 * d), alpha = opacity)
            }
        }
    }
}

/** Stars that twinkle and fireflies that wander: the night pages of the story book. */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    class Star(val x: Double, val y: Double, val r: Double, val p: Double)
    class Fly(val x: Double, val y: Double, val p: Double, val s: Double)
    val stars = remember { List(46) { Star(between(0.0, 1.0), between(0.0, 0.42), between(0.8, 2.2), between(0.0, 6.0)) } }
    val flies = remember { List(14) { Fly(between(0.05, 0.95), between(0.35, 0.92), between(0.0, 6.0), between(0.4, 1.0)) } }
    val glowColor = remember { hexColor("fff27a").copy(alpha = 0.7f) }
    val core = remember { hexColor("fffbc4") }
    val clock = rememberSkyClock()
    Canvas(modifier) {
        val t = clock()
        val d = density
        for (s in stars) {
            val r = s.r.toFloat() * d
            drawOval(
                Color.White, Offset((s.x * size.width).toFloat(), (s.y * size.height).toFloat()), Size(r * 2, r * 2),
                alpha = (0.35 + 0.65 * abs(sin(t * 1.4 + s.p))).toFloat(),
            )
        }
        for (f in flies) {
            val x = ((f.x + sin(t * 0.5 * f.s + f.p) * 0.05) * size.width).toFloat()
            val y = ((f.y + cos(t * 0.7 * f.s + f.p * 2) * 0.04) * size.height).toFloat()
            val glow = (0.25 + 0.75 * abs(sin(t * 1.8 * f.s + f.p)).pow(2)).toFloat()
            drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), Offset(x, y), 7 * d), 7 * d, Offset(x, y), alpha = glow)
            drawCircle(core, 1.8f * d, Offset(x, y), alpha = glow)
        }
    }
}

/** Daytime sky life: clouds sliding past and a pair of birds crossing. */
@Composable
fun DaySky(modifier: Modifier = Modifier) {
    val clock = rememberSkyClock()
    Canvas(modifier) {
        val t = clock()
        val d = density.toDouble()
        val width = size.width.toDouble()
        val height = size.height.toDouble()
        // height, speed, scale
        val clouds = arrayOf(doubleArrayOf(0.16, 0.06, 1.0), doubleArrayOf(0.3, 0.035, 0.7), doubleArrayOf(0.1, 0.05, 0.55))
        clouds.forEachIndexed { i, c ->
            val span = width + 160 * d
            val x = (((t * c[1] * width + i * span / 3) % span) - 80 * d).toFloat()
            val y = (c[0] * height).toFloat()
            val w = (62 * c[2] * d).toFloat()
            val cloud = Path().apply {
                addOval(Rect(x, y, x + w, y + w * 0.42f))
                addOval(Rect(x + w * 0.22f, y - w * 0.2f, x + w * 0.72f, y + w * 0.3f))
                addOval(Rect(x + w * 0.5f, y - w * 0.08f, x + w * 0.9f, y + w * 0.28f))
            }
            drawPath(cloud, Color.White.copy(alpha = 0.85f))
        }
        val span = width + 120 * d
        val bx = span - (t * 22 * d) % span - 60 * d
        val flock = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(22.0, 12.0), doubleArrayOf(-18.0, 18.0))
        flock.forEachIndexed { k, offset ->
            val flap = (sin(t * 7 + k) * 4 * d).toFloat()
            val x = (bx + offset[0] * d).toFloat()
            val y = (height * 0.2 + (offset[1] + sin(t * 1.2 + k) * 4) * d).toFloat()
            val u = d.toFloat()
            val bird = Path().apply {
                moveTo(x - 8 * u, y + flap)
                quadraticTo(x - 4 * u, y - 5 * u, x, y + 2 * u)
                quadraticTo(x + 4 * u, y - 5 * u, x + 8 * u, y + flap)
            }
            drawPath(bird, Ink.copy(alpha = 0.55f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
        }
    }
}

/** Sunbeams turning slowly behind the sun. */
@Composable
fun SunRays(modifier: Modifier = Modifier) {
    val clock = rememberSkyClock()
    Canvas(modifier) {
        val t = clock()
        val d = density
        val middle = Offset(size.width / 2, size.height / 2)
        val turn = Math.toDegrees(t * 0.25).toFloat()
        for (i in 0 until 12) {
            val reach = size.width / 2 * (0.82 + 0.18 * sin(t * 2 + i)).toFloat()
            withTransform({ translate(middle.x, middle.y); rotate(turn + i * 30f, Offset.Zero) }) {
                drawRoundRect(Sun.copy(alpha = 0.75f), Offset(-2.5f * d, -reach), Size(5 * d, reach * 0.26f), CornerRadius(2.5f * d))
            }
        }
    }
}

/** Today's goal as a ring that fills, with the count inside. */
@Composable
fun GoalRing(value: Double, size: Dp = 58.dp, modifier: Modifier = Modifier) {
    // spring(duration: 0.8, bounce: 0.3)
    val shown by animateFloatAsState(value.toFloat().coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 62f), label = "goal")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.28f), this.size.minDimension / 2, style = Stroke(8.dp.toPx()))
            if (value > 0 || shown > 0.001f) {
                drawArc(
                    Sun, -90f, 360f * max(0.001f, shown.coerceIn(0f, 1f)), useCenter = false,
                    style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Art(if (value >= 1) "star" else "bolt", size * 0.42f)
    }
}

/** The last seven days, with a flame on each day the learner practised. */
@Composable
fun WeekStrip(modifier: Modifier = Modifier) {
    val state = AppStore.state
    val today = LocalDate.now()
    val locale = Locale.forLanguageTag(state.ui.raw)
    Row(modifier.fillMaxWidth()) {
        for (back in 6 downTo 0) {
            val day = today.minusDays(back.toLong())
            val active = state.activeDays.contains(Game.dayKey(day))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = textFont(13, W.HEAVY), color = if (back == 0) SunShade else Muted,
                )
                Box(
                    Modifier.size(36.dp).background(if (active) SunTint else Surface, CircleShape)
                        .border(2.dp, if (back == 0) Sun else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Art(if (active) "flame" else "flameOff", 22.dp, Modifier.alpha(if (active) 1f else 0.5f))
                }
            }
        }
    }
}
