package com.congolingo.loba.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.svgPath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Mood { IDLE, HAPPY, CHEER, SAD, THINK, WAVE }

/**
 * Loba, the mascot: an African grey parrot (native to the Congo basin) with a flag-blue scarf.
 * Drawn and animated in code, from the same shapes as the website: it breathes, blinks, waves,
 * flaps when it cheers, shakes its head when it is sad, and hops when tapped.
 */
@Composable
fun LobaView(mood: Mood = Mood.IDLE, size: Dp = 140.dp, tappable: Boolean = true, modifier: Modifier = Modifier) {
    val clock = rememberAmbient()
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                // Lifted by 16% of its height, then leaning 7° about the middle of its base.
                val angle = -7f * hop.value
                val lift = this.size.height * 0.16f * hop.value
                val rad = Math.toRadians(angle.toDouble())
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationZ = angle
                translationX = (lift * sin(rad)).toFloat()
                translationY = (-lift * cos(rad)).toFloat()
            }
            .clearAndSetSemantics {}
            .then(
                if (!tappable) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        Sfx.play(Sfx.Cue.POP); Haptic.tap()
                        scope.launch {
                            // spring(duration: 0.2, bounce: 0.5) up, then spring(duration: 0.45, bounce: 0.65) back down
                            launch { hop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 987f)) }
                            delay(200)
                            hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 195f))
                        }
                    }
                },
            ),
    ) {
        val t = clock.value
        scale(this.size.width / 200f, this.size.height / 200f, Offset.Zero) {
            with(LobaArt) { draw(mood, t) }
        }
    }
}

/** The parrot itself, on a 200 × 200 canvas. */
private object LobaArt {
    private val grey = hexColor("98a2b8"); private val greyD = hexColor("77819a"); private val belly = hexColor("c9d0de")
    private val mask = hexColor("f6f8fc"); private val beak = hexColor("322e40"); private val red = hexColor("f0524f"); private val redD = hexColor("c93a3c")
    private val foot = hexColor("4b465b"); private val blush = hexColor("ff9aa8"); private val tearBlue = hexColor("6cc7ff"); private val mouth = hexColor("e8608a")

    // Parsed once, not on every frame.
    private val feathers: Path = svgPath("M86 128q7 6 14 0m0 0q7 6 14 0M79 144q7 6 14 0m0 0q7 6 14 0m0 0q7 6 14 0M86 160q7 6 14 0m0 0q7 6 14 0")
    private val crest: Path = svgPath("M84 34q4-12 10-4 5-12 10 0 7-8 10 6z")
    private val smile: Path = svgPath("M72 74q10-14 20 0M108 74q10-14 20 0")
    private val brows: Path = svgPath("M68 58l22-8M132 58l-22-8")
    private val tear: Path = svgPath("M133 86q5 8 0 12-5-4 0-12z")
    private val beakShape: Path = svgPath("M100 72c-12 0-17 14-11 24 4 7 11 11 11 11s7-4 11-11c6-10 1-24-11-24z")
    private val beakShine: Path = svgPath("M94 80q3-4 8-3")
    private val scarf: Path = svgPath("M60 106q40 26 80 0l3 12q-43 28-86 0z")
    private val scarfEnd: Path = svgPath("M126 118l14 24-16-4-6 10-4-26z")
    private val star: Path = svgPath("m100 120 1.8 3.8 4.2.5-3.1 2.9.8 4.1-3.7-2-3.7 2 .8-4.1-3.1-2.9 4.2-.5z")

    private fun round(width: Float) = Stroke(width, cap = StrokeCap.Round)
    private fun wave(x: Double): Float = sin(x).toFloat()

    private fun DrawScope.ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, turn: Float = 0f, opacity: Float = 1f) {
        withTransform({ translate(cx, cy); rotate(turn, Offset.Zero) }) {
            drawOval(color, Offset(-rx, -ry), Size(rx * 2, ry * 2), alpha = opacity)
        }
    }

    /** Draws turned about a point, like SVG's transform-origin. */
    private fun DrawScope.pivot(x: Float, y: Float, degrees: Float, dy: Float = 0f, block: DrawScope.() -> Unit) {
        withTransform({ translate(0f, dy); rotate(degrees, Offset(x, y)) }, block)
    }

    fun DrawScope.draw(mood: Mood, t: Double) {
        val wingsUp = mood == Mood.CHEER
        val smiling = mood == Mood.HAPPY || mood == Mood.CHEER
        val open = mood == Mood.CHEER || mood == Mood.WAVE
        val jump = if (mood == Mood.CHEER) -abs(wave(t * 5)) * 10f else 0f
        val breathe = 1f + wave(t * 2.1) * 0.012f
        // Blink every few seconds; twice in a row now and then.
        val phase = t % 3.7
        val blinking = phase < 0.13 || (phase > 0.3 && phase < 0.4 && (t / 3.7).toInt() % 3 == 0)

        ellipse(100f, 190f, 46f + jump * 0.8f, 6f, Color.Black, opacity = 0.08f)

        withTransform({
            translate(0f, jump)
            scale(2f - breathe, breathe, Offset(100f, 186f))
            rotate(if (mood == Mood.HAPPY) wave(t * 2.4) * 2.5f else 0f, Offset(100f, 186f))
        }) {
            // tail, wagging a little
            pivot(128f, 164f, wave(t * 3) * (if (smiling) 7f else 3f)) {
                ellipse(148f, 170f, 10f, 24f, redD, turn = -58f)
                ellipse(144f, 176f, 10f, 24f, red, turn = -74f)
            }
            ellipse(82f, 184f, 13f, 7f, foot)
            ellipse(118f, 184f, 13f, 7f, foot)

            // raised wings sit behind the body
            if (wingsUp) {
                val flap = wave(t * 10) * 13f
                pivot(60f, 118f, flap) { ellipse(40f, 96f, 15f, 34f, greyD, turn = -38f) }
                pivot(140f, 118f, -flap) { ellipse(160f, 96f, 15f, 34f, greyD, turn = 38f) }
            }
            if (mood == Mood.WAVE) pivot(140f, 118f, wave(t * 7) * 15f - 4f) { ellipse(160f, 96f, 15f, 34f, greyD, turn = 38f) }

            ellipse(100f, 130f, 52f, 56f, grey)
            ellipse(100f, 140f, 35f, 40f, belly)
            drawPath(feathers, grey, alpha = 0.7f, style = round(3f))
            if (!wingsUp) ellipse(52f, 134f, 15f, 34f, greyD, turn = 10f)
            if (!wingsUp && mood != Mood.WAVE) ellipse(148f, 134f, 15f, 34f, greyD, turn = -10f)

            // head
            val tilt = when (mood) {
                Mood.SAD -> -5f + wave(t * 9) * (if (t < 0.9) 7f else 0f) // a quick "no", then it hangs its head
                Mood.THINK -> 5f + wave(t * 1.6) * 2f
                Mood.WAVE -> wave(t * 3.5) * 3f
                else -> wave(t * 1.3) * 1.5f
            }
            pivot(100f, 110f, tilt, dy = if (mood == Mood.SAD) 3f else 0f) {
                drawPath(crest, greyD)
                ellipse(100f, 74f, 46f, 46f, grey)
                ellipse(80f, 72f, 23f, 25f, mask)
                ellipse(120f, 72f, 23f, 25f, mask)
                ellipse(66f, 88f, 7f, 7f, blush, opacity = 0.55f)
                ellipse(134f, 88f, 7f, 7f, blush, opacity = 0.55f)
                if (smiling) {
                    drawPath(smile, beak, style = round(5.5f))
                } else {
                    val up = mood == Mood.THINK
                    // The eyes wander slowly, as if following something.
                    val lx = wave(t * 0.7) * 2.2f + (if (up) 3f else 0f)
                    val ly = cos(t * 0.9).toFloat() * 1.2f + (if (up) -4f else if (mood == Mood.SAD) 3f else 0f)
                    for (cx in floatArrayOf(82f, 118f)) {
                        if (blinking) {
                            drawLine(beak, Offset(cx - 9f + lx, 71f + ly), Offset(cx + 9f + lx, 71f + ly), strokeWidth = 4.5f, cap = StrokeCap.Round)
                        } else {
                            ellipse(cx + lx, 71f + ly, 9.5f, 9.5f, beak)
                            ellipse(cx + 3f + lx * 1.3f, 67f + ly * 1.3f, 3.2f, 3.2f, Color.White)
                        }
                    }
                }
                if (mood == Mood.SAD) {
                    drawPath(brows, beak, style = round(4.5f))
                    val fall = ((t * 0.7) % 1.0).toFloat()
                    withTransform({ translate(0f, fall * 16f) }) { drawPath(tear, tearBlue, alpha = 1f - fall) }
                }
                // beak: it opens and closes when Loba talks or cheers
                val gape = if (open) 3f + abs(wave(t * (if (mood == Mood.CHEER) 9 else 6))) * 3f else 0f
                if (open) ellipse(100f, 104f, 8f, 4f + gape, mouth)
                withTransform({ translate(0f, -gape) }) {
                    drawPath(beakShape, beak)
                    drawPath(beakShine, Color.White.copy(alpha = 0.35f), style = round(3f))
                }
            }

            // scarf, its end fluttering
            drawPath(scarf, Sky)
            pivot(126f, 118f, wave(t * 4) * (if (wingsUp) 10f else 5f)) { drawPath(scarfEnd, SkyShade) }
            drawPath(star, Sun)
        }
    }
}
