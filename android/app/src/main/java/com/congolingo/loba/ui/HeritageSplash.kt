package com.congolingo.loba.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.congolingo.loba.core.L
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The user's laughing owl animation is the focus of the launch screen. */
@Composable
fun SplashView() {
    val label = L("Mbote! Loba. Chargement. Les langues du Congo.", "Mbote! Loba. Loading. The languages of Congo.")
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val owl = min(maxWidth * 0.78f, 340.dp)
        val raffia = drawableId("RaffiaSplash")
        if (raffia != 0) {
            // The raffia fades in from the top, under the status bar too.
            Image(
                painterResource(raffia), null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 0.14f to Color.Transparent, 0.22f to Color.Black, 1f to Color.Black),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(owl)) {
                HeritageLottie("owl-laughing", playing = !Motion.still, modifier = Modifier.fillMaxSize())
                Bubble(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-15).dp)) {
                    Text("Mbote!", style = textFont(16, W.BOLD), color = Ink)
                }
            }
            Text("Loba", style = displayFont(44), color = Ink)
            Text(L("Les langues du Congo", "The languages of Congo"), style = textFont(15, W.BOLD), color = Muted)
            WavingFlag(Modifier.padding(top = 6.dp))
        }
    }
}

/** Renders the original Lottie vectors natively, looping; it rests on the first frame when not playing. */
@Composable
fun HeritageLottie(name: String, playing: Boolean, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("$name.json"))
    val progress by animateLottieCompositionAsState(composition, isPlaying = playing, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition, progress = { if (playing) progress else 0f }, modifier = modifier, contentScale = ContentScale.Fit)
}

/** The flag patch, rippling like cloth: it is drawn in 50 upright strips, each lifted along a travelling wave. */
@Composable
private fun WavingFlag(modifier: Modifier = Modifier) {
    val clock = rememberAmbient()
    Canvas(modifier.size(50.dp, 44.dp)) {
        val seconds = clock.value % 2.0
        val d = density
        val width = size.width / 50
        val top = (size.height - 37.5f * d) / 2
        val flag = Size(50 * d, 37.5f * d)
        for (index in 0 until 50) {
            val fraction = index / 49.0
            val lift = if (Motion.still) 0f else (2.5 * fraction * sin(6 * fraction - seconds * PI)).toFloat() * d
            // Whole pixels, so no seams show between the strips.
            clipRect((index * width).roundToInt().toFloat(), 0f, ((index + 1) * width).roundToInt().toFloat(), size.height) {
                translate((size.width - flag.width) / 2, top + lift) { congoFlagBadge(flag) }
            }
        }
    }
}

/** A jersey-style flag patch, drawn in a fixed 4:3 coordinate space. */
private fun DrawScope.congoFlagBadge(area: Size) {
    val patch = Path().apply { addRoundRect(RoundRect(0f, 0f, area.width, area.height, CornerRadius(2.dp.toPx()))) }
    clipPath(patch) {
        scale(area.width / 40f, area.height / 30f, Offset.Zero) {
            drawRect(Color(0f, 0.50f, 1f), Offset.Zero, Size(40f, 30f))
            val gold = Color(1f, 0.84f, 0f)
            drawLine(gold, Offset(-8f, 36f), Offset(48f, -6f), strokeWidth = 9f)
            drawLine(Color(0.81f, 0.13f, 0.17f), Offset(-8f, 36f), Offset(48f, -6f), strokeWidth = 6f)
            val star = Path()
            for (i in 0 until 10) {
                val angle = i * PI / 5 - PI / 2
                val radius = if (i % 2 == 0) 6.0 else 2.3
                val x = (7 + cos(angle) * radius).toFloat()
                val y = (7 + sin(angle) * radius).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, gold)
        }
    }
}
