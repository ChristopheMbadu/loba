package com.congolingo.loba.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.UiLang
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Masapo: the story book. A Kongo tale from the mouth of the Congo river, retold from the version
 * written down by R. E. Dennett in "Notes on the Folklore of the Fjort" (1898).
 */
object Tale {
    data class Entry(val fr: String, val en: String, val forms: Map<String, String>)
    data class Page(val fr: String, val en: String)

    val words: Map<String, Entry> = mapOf(
        "croc" to Entry("le crocodile", "the crocodile", mapOf("ln" to "Ngando", "sw" to "Mamba")),
        "hen" to Entry("la poule", "the hen", mapOf("ln" to "Nsoso", "sw" to "Kuku")),
        "river" to Entry("le fleuve", "the river", mapOf("ln" to "ebale", "sw" to "mto")),
        "sibling" to Entry("frère, sœur", "brother, sister", mapOf("ln" to "Ndeko", "sw" to "Ndugu")),
        "water" to Entry("l’eau", "water", mapOf("ln" to "mai", "sw" to "maji")),
        "eggs" to Entry("les œufs", "eggs", mapOf("ln" to "maki", "sw" to "mayai")),
    )
    val order = listOf("croc", "hen", "river", "sibling", "water", "eggs")

    val pages: List<Page> = listOf(
        Page("Chaque matin, {hen} la poule descendait au bord du fleuve, {river}, pour picorer son déjeuner. Le soleil se levait, l’eau chantait, et tout allait bien.",
            "Every morning, {hen} the hen walked down to the river, {river}, to peck at her breakfast. The sun came up, the water sang, and all was well."),
        Page("Mais un jour, l’eau se mit à bouger. Deux yeux. Un long museau. C’était {croc} le crocodile ! « Petite poule, dit-il en ouvrant grand la gueule, je vais te manger ! »",
            "But one day, the water began to stir. Two eyes. One long snout. It was {croc} the crocodile! “Little hen,” he said, opening his jaws wide, “I am going to eat you!”"),
        Page("{hen} ne s’enfuit pas. Elle cria de toutes ses forces : « {sibling} ! Mon frère, ne fais pas ça ! » {croc} referma la gueule, tout étonné. Son frère ? Il replongea sans dire un mot.",
            "{hen} did not run. She cried with all her might: “{sibling}! My brother, don’t do that!” {croc} shut his jaws, astonished. Her brother? He sank back without a word."),
        Page("Le lendemain il revint, bien décidé à la croquer. Mais la poule cria encore : « {sibling} ! Ne fais pas ça ! » « Comment serais-je son frère ? grogna-t-il. Elle vit sur la terre, et moi dans l’eau, {water} ! »",
            "The next day he came back, determined to gobble her up. But the hen cried again: “{sibling}! Don’t do that!” “How can I be her brother?” he grumbled. “She lives on land, and I live in the water, {water}!”"),
        Page("Cette question l’empêchait de dormir. Alors {croc} sortit du fleuve et partit demander la réponse à Nzambi, qui sait toutes choses. En chemin, il croisa son ami Mbambi, le grand lézard.",
            "The question kept him awake. So {croc} left the river and set off to ask Nzambi, who knows all things. On the way he met his friend Mbambi, the great lizard."),
        Page("« N’y va pas, dit Mbambi, tu montrerais seulement ton ignorance ! Écoute : le canard vit dans l’eau et pond des œufs, {eggs}. La tortue aussi. Moi aussi, je ponds des œufs. La poule en pond. Et toi aussi, mon nigaud d’ami ! »",
            "“Don’t go,” said Mbambi, “you will only show how little you know! Listen: the duck lives in the water and lays eggs, {eggs}. So does the turtle. I lay eggs too. The hen lays them. And so do you, my silly friend!”"),
        Page("« Alors, d’une certaine façon, nous sommes tous frères. » {croc} rentra au fleuve en souriant. Et voilà pourquoi, depuis ce jour, le crocodile ne mange pas la poule.",
            "“So, in a way, we are all brothers.” {croc} went home to the river, smiling. And that is why, from that day to this, the crocodile does not eat the hen."),
    )

    /** Sky behind the book on each page: dawn, day, dusk, night. */
    val skies: List<Color> = listOf("fde3c0", "fde3c0", "d9eefc", "d9eefc", "d9eefc", "f9c9a0", "f9c9a0", "1c1d52", "1c1d52").map(::hexColor)
}

private val storyBrown = hexColor("a8763e")
private val storyDash = hexColor("e2cfa0")

/** What the text panel shows: it slides when any of these change. */
private data class StoryLeaf(val page: Int, val ui: UiLang, val code: String)

@Composable
fun StoryView(onClose: () -> Unit) {
    val ui = AppStore.state.ui
    val last = Tale.pages.size + 1
    // Debug builds can open the book at any page.
    var page by remember { mutableIntStateOf(min(last, max(0, DebugFlags.page ?: 0))) }
    var code by remember { mutableStateOf(if (Tale.words.getValue("croc").forms[AppStore.state.lang] != null) AppStore.state.lang else "ln") }
    var meaning by remember { mutableStateOf<String?>(null) }
    var forward by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val drift: State<Float> = if (Motion.still) remember { mutableFloatStateOf(1.1f) }
    else rememberInfiniteTransition(label = "drift").animateFloat(1.05f, 1.1f, infiniteRepeatable(tween(9000, easing = EaseInOut), RepeatMode.Reverse), label = "drift")

    fun word(key: String, code: String): String = Tale.words[key]?.forms?.get(code) ?: Tale.words[key]?.forms?.get("ln") ?: key

    fun go(to: Int) {
        val next = max(0, min(last, to))
        if (next == page) return
        forward = next > page
        if (page == 0) Sfx.play(Sfx.Cue.CALL) else Sfx.play(Sfx.Cue.PAGE, next)
        if (next == 2) scope.launch { delay(600); Sfx.play(Sfx.Cue.SPLASH) }
        Haptic.tap()
        meaning = null
        page = next
    }

    val sky by animateColorAsState(Tale.skies[min(page, Tale.skies.size - 1)], tween(600, easing = EaseInOut), label = "sky")
    val glass = RoundedCornerShape(13.dp)
    Column(
        Modifier.fillMaxSize().background(sky).systemBarsPadding().readable(min(680, (LocalConfiguration.current.screenHeightDp * 0.62f).toInt()).dp)
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { _, amount -> dx += amount.x; dy += amount.y },
                    onDragEnd = { if (hypot(dx, dy) >= 40.dp.toPx() && page > 0 && abs(dx) > abs(dy)) go(page + if (dx < 0) 1 else -1) },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(glass).background(Color.White.copy(alpha = 0.75f)).tappable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L("Fermer", "Close"), Modifier.size(22.dp), tint = Ink)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(glass).background(Color.White.copy(alpha = 0.75f)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in listOf("ln", "sw")) {
                    LangBadge(c, if (c == "ln") Sky else Leaf, Modifier.alpha(if (code == c) 1f else 0.45f).tappable { Sfx.play(Sfx.Cue.TAP); code = c })
                }
            }
            UiSwitch()
        }

        val leafShape = RoundedCornerShape(18.dp)
        Column(
            Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                .drawBehind {
                    val r = CornerRadius(28.dp.toPx())
                    drawRoundRect(Cocoa.copy(alpha = 0.55f), Offset(0f, 8.dp.toPx()), size, r)
                    drawRoundRect(Sand, cornerRadius = r)
                }
                .border(4.dp, Cocoa, RoundedCornerShape(28.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(
                min(page, 7),
                Modifier.fillMaxWidth().aspectRatio(330f / 264f).clip(leafShape).border(3.dp, Cocoa, leafShape),
                transitionSpec = { (fadeIn(tween(600, easing = EaseInOut)) + scaleIn(tween(600, easing = EaseInOut), initialScale = 1.04f)) togetherWith fadeOut(tween(600, easing = EaseInOut)) },
                label = "stage",
            ) { p ->
                Box(Modifier.fillMaxSize()) {
                    key(p) { StoryStage(p, drift, "${word("sibling", code)} !") }
                    if (p == 0) StoryCover(Modifier.align(Alignment.TopCenter))
                }
            }
            KubaBand(Modifier.clip(CircleShape), height = 12.dp)
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(leafShape).background(Paper)
                    .drawWithContent {
                        drawContent()
                        val inset = 9.dp.toPx()
                        drawRoundRect(
                            storyDash, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(11.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))),
                        )
                    }
                    .border(3.dp, Cocoa, leafShape),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AnimatedContent(
                        StoryLeaf(page, ui, code), Modifier.fillMaxWidth(),
                        transitionSpec = {
                            (slideInHorizontally(tween(600, easing = EaseInOut)) { if (forward) it else -it } + fadeIn(tween(600, easing = EaseInOut))) togetherWith fadeOut(tween(300))
                        },
                        label = "leaf",
                    ) { leaf ->
                        Box(Modifier.fillMaxWidth().padding(18.dp)) {
                            when (leaf.page) {
                                0 -> StoryOpening { go(1) }
                                last -> StoryEnding(leaf.ui, { word(it, leaf.code) }, { go(0) }, onClose)
                                else -> StoryReading(leaf.page, leaf.ui, { word(it, leaf.code) }, meaning) { key ->
                                    Sfx.play(Sfx.Cue.TILE, max(0, Tale.order.indexOf(key))); Haptic.tap()
                                    meaning = if (meaning == key) null else key
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            StoryArrow(Icons.Rounded.ChevronLeft, L("Page précédente", "Previous page"), disabled = page == 0) { go(page - 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..last) {
                    Box(
                        Modifier.size(if (i == page) 13.dp else 8.dp).clip(CircleShape)
                            .background(if (i == page) Sun else if (i < page) Cocoa else Color.Transparent)
                            .border(2.dp, if (i == page) SunShade else Cocoa.copy(alpha = 0.6f), CircleShape),
                    )
                }
            }
            StoryArrow(Icons.Rounded.ChevronRight, L("Page suivante", "Next page"), disabled = page == last || page == 0) { go(page + 1) }
        }
    }
}

@Composable
private fun StoryArrow(icon: ImageVector, label: String, disabled: Boolean, action: () -> Unit) {
    Box(
        Modifier.alpha(if (disabled) 0.35f else 1f).padding(bottom = 5.dp).size(60.dp)
            .drawBehind {
                drawCircle(SunShade, center = center + Offset(0f, 5.dp.toPx()))
                drawCircle(Sun)
            }
            .clip(CircleShape).tappable(enabled = !disabled, onClick = action),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(34.dp), tint = Ink) }
}

@Composable
private fun StoryCover(modifier: Modifier) {
    Column(
        modifier.padding(18.dp).clip(RoundedCornerShape(16.dp)).background(Paper.copy(alpha = 0.92f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(L("Un conte kongo", "A Kongo tale").uppercase(), style = textFont(13, W.HEAVY), color = Cocoa, letterSpacing = 1.5.sp)
        Text(L("Pourquoi le crocodile ne mange pas la poule", "Why the Crocodile Does Not Eat the Hen"), style = displayFont(23), color = Ink, textAlign = TextAlign.Center)
    }
}

/** Coral letters on a hard yellow shadow. */
@Composable
private fun StoryHeadline(text: String, size: Int) {
    val shadow = with(LocalDensity.current) { Shadow(Sun, Offset(2.dp.toPx(), 3.dp.toPx()), 0f) }
    Text(text, style = displayFont(size).copy(shadow = shadow), color = Coral, textAlign = TextAlign.Center)
}

/** The storyteller calls "Lisapo !" and everyone answers "Onge !". */
@Composable
private fun StoryOpening(onAnswer: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StoryHeadline("Lisapo !", 40)
        Text(
            L("Chez nous, le conteur lance « Lisapo ! » et tout le monde répond « Onge ! ». Réponds pour ouvrir le conte.", "Back home, the storyteller calls “Lisapo!” and everyone answers “Onge!”. Answer to open the tale."),
            style = textFont(16), color = Cocoa, textAlign = TextAlign.Center,
        )
        ChunkyButton("Onge !", kind = Chunky.SUN, onClick = onAnswer)
    }
}

@Composable
private fun StoryReading(page: Int, ui: UiLang, word: (String) -> String, meaning: String?, onWord: (String) -> Unit) {
    val leaf = Tale.pages.getOrNull(page - 1) ?: return
    val raw = if (ui == UiLang.FR) leaf.fr else leaf.en
    // The underline takes the word's colour: Android cannot colour it separately (it is yellow on iOS).
    val chip = TextLinkStyles(SpanStyle(color = SkyShade, fontSize = 21.sp, fontFamily = nunito(W.BLACK), fontWeight = FontWeight(W.BLACK.value), textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        raw.split('{', '}').forEachIndexed { i, part ->
            if (i % 2 == 0) append(part)
            else withLink(LinkAnnotation.Clickable(part, chip) { onWord(part) }) { append(word(part)) }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${L("Page", "Page")} $page / ${Tale.pages.size}".uppercase(), style = textFont(13, W.HEAVY), color = storyBrown, letterSpacing = 1.5.sp)
        Text(text, style = textFont(20, W.BOLD), color = hexColor("3a2a1c"), lineHeight = 34.sp)
        val entry = meaning?.let { Tale.words[it] }
        if (meaning != null && entry != null) {
            key(meaning) {
                val pop = remember { Animatable(0f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.5f, 438f)) }
                Row(
                    Modifier
                        .graphicsLayer {
                            val s = 0.7f + 0.3f * pop.value
                            scaleX = s; scaleY = s; alpha = pop.value.coerceIn(0f, 1f); transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(CircleShape).background(SunTint).border(2.dp, Sun, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(word(meaning), style = textFont(17, W.BLACK), color = SkyShade)
                    Text("=", fontSize = 17.sp, color = Muted)
                    Text(if (ui == UiLang.FR) entry.fr else entry.en, style = textFont(17, W.BOLD), color = Ink)
                }
            }
        } else if (page == 1) {
            Text(L("Touche les mots en couleur.", "Tap the coloured words."), style = textFont(14, W.BOLD), color = storyBrown)
        }
    }
}

@Composable
private fun StoryEnding(ui: UiLang, word: (String) -> String, onAgain: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StoryHeadline("Lisapo esili.", 32)
        Text(
            L("Avant de te fâcher, cherche ce qui te rapproche de l’autre.", "Before you get angry, look for what you share with the other."),
            style = textFont(18, W.HEAVY), color = SkyShade, textAlign = TextAlign.Center,
        )
        Text(L("Les mots du conte", "Words from the tale").uppercase(), Modifier.padding(top = 4.dp), style = textFont(13, W.HEAVY), color = storyBrown, letterSpacing = 1.5.sp)
        val tile = RoundedCornerShape(14.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (pair in Tale.order.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (key in pair) {
                        val entry = Tale.words.getValue(key)
                        Column(
                            Modifier.weight(1f).clip(tile).background(Color.White).border(2.dp, storyDash, tile).padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(word(key), style = textFont(18, W.BLACK), color = SkyShade, textAlign = TextAlign.Center)
                            Text(if (ui == UiLang.FR) entry.fr else entry.en, style = textFont(14), color = Cocoa, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        ChunkyButton(L("Relire", "Read again"), kind = Chunky.OUTLINE, onClick = onAgain)
        ChunkyButton(L("Fermer le livre", "Close the book"), kind = Chunky.GREEN, onClick = onClose)
        Text(
            L("Conte kongo, raconté d’après la version notée par R. E. Dennett en 1898.", "A Kongo tale, retold from the version written down by R. E. Dennett in 1898."),
            style = textFont(12), color = storyBrown, textAlign = TextAlign.Center,
        )
    }
}

/** One page's picture: the landscape, a living sky, and the characters, who arrive, breathe and jump when tapped. */
@Composable
private fun StoryStage(page: Int, drift: State<Float>, shout: String) {
    val arrived = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val clock = rememberAmbient()
    val even = page % 2 == 0
    val night = page >= 7
    val dusk = page == 5 || page == 6
    LaunchedEffect(Unit) { delay(250); arrived.animateTo(1f, spring(0.65f, 49f)) }

    Box(
        // Slightly enlarged so the slow drift never shows an edge.
        Modifier.fillMaxSize().graphicsLayer {
            scaleX = drift.value; scaleY = drift.value
            transformOrigin = TransformOrigin(if (even) 0f else 1f, 1f)
        },
    ) {
        val back = drawableId("story-back-$page")
        if (back != 0) Image(painterResource(back), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (night) NightSky(Modifier.matchParentSize()) else DaySky(Modifier.matchParentSize().alpha(if (dusk) 0.6f else 1f))
        val cast = drawableId("story-cast-$page")
        if (cast != 0) {
            Image(
                painterResource(cast), null,
                Modifier.matchParentSize()
                    .graphicsLayer {
                        val t = clock.value
                        translationX = (1f - arrived.value) * (if (even) -70 else 70).dp.toPx()
                        translationY = (sin(t * 2.2) * 2.2).toFloat().dp.toPx() + hop.value.dp.toPx()
                        rotationZ = (sin(t * 1.1) * 0.6).toFloat()
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        alpha = arrived.value.coerceIn(0f, 1f)
                    }
                    .tappable {
                        Sfx.play(if (even) Sfx.Cue.DRUM else Sfx.Cue.POP, page); Haptic.thud()
                        scope.launch {
                            withTimeoutOrNull(180) { hop.animateTo(-16f, spring(0.6f, 1218f)) }
                            hop.animateTo(0f, spring(0.3f, 158f))
                        }
                    },
                contentScale = ContentScale.Crop,
            )
        }
        // The hen's cry, in the learner's language.
        if (page == 3 || page == 4) {
            val halo = with(LocalDensity.current) { Shadow(Color.White, Offset.Zero, 3.dp.toPx()) }
            Text(
                shout, style = displayFont(26).copy(shadow = halo), color = Coral,
                modifier = Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val text = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            text.place((constraints.maxWidth * 0.62f - text.width / 2f).roundToInt(), (constraints.maxHeight * 0.4f - text.height / 2f).roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val s = 0.2f + 0.8f * arrived.value
                        scaleX = s; scaleY = s; rotationZ = -8f; alpha = arrived.value.coerceIn(0f, 1f)
                    },
            )
        }
    }
}
