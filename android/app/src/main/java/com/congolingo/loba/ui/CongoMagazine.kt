package com.congolingo.loba.ui

import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.congolingo.loba.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import android.util.LruCache
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

private data class CongoArticle(
    val id: String, val section: Int, val art: String, val place: MapText, val number: String,
    val title: MapText, val intro: MapText, val detail: MapText, val question: MapText, val answer: MapText,
    val source: String, val url: String, val match: String?,
)
private object MagazineContent {
    val all: List<CongoArticle> by lazy {
        try {
            val array = JSONArray(appContext.assets.open("congo-magazine.json").bufferedReader().use { it.readText() })
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                fun t(key: String): MapText { val v = o.getJSONObject(key); return MapText(v.getString("fr"), v.getString("en")) }
                CongoArticle(o.getString("id"), o.getInt("section"), o.getString("art"), t("place"), o.getString("number"),
                    t("title"), t("intro"), t("detail"), t("question"), t("answer"), o.getString("source"), o.getString("url"),
                    if (o.isNull("match")) null else o.getString("match"))
            }
        } catch (_: Exception) { emptyList() }
    }
}
private data class MagazinePage(val id: String, val title: String, val intro: String, val story: CongoArticle? = null, val date: String? = null, val emblem: String? = null) {
    val art get() = story?.art ?: "history"
    val color get() = hexColor(when (art) { "forest" -> "147c74"; "mountain" -> "5868a4"; "river" -> "168dcc"; "textile" -> "ad6744"; "music" -> "8761a5"; else -> "b17b28" })
}

@Composable
fun CongoMagazine() {
    var section by remember { mutableIntStateOf(0) }
    val ui = AppStore.state.ui
    val labels = listOf(L("Terres", "Places"), L("Histoire", "History"), L("Cultures", "Culture"), L("Repères", "Facts"))
    val featured = MagazineContent.all.filter { it.section == section }
    val originals = when(section) { 1 -> DRC.shared.history; 2 -> DRC.shared.culture; 3 -> DRC.shared.figures; else -> emptyList() }
    val replaced = featured.mapNotNull { it.match }.toSet()
    val pages = featured.map { MagazinePage(it.id, it.title(ui), it.intro(ui), it) } + originals.filter { it.title.en !in replaced }.map {
        MagazinePage(it.id, it.title(ui), it.text(ui), date = localizedMagazineDate(it.year, ui), emblem = it.emoji ?: localizedMagazineDate(it.year, ui))
    }
    Column(Modifier.fillMaxSize().background(Surface.copy(alpha = 0.5f)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(L("Le Congo se raconte", "Congo, page by page"), style = displayFont(22), color = Ink)
                Text(L("Un petit voyage à chaque page", "A little adventure on every page"), style = textFont(12), color = Muted)
            }
            Text("🇨🇩", fontSize = 28.sp)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { i, label ->
                TextButton(onClick = { section = i }, modifier = Modifier.weight(1f).background(if (i == section) SkyShade else Surface, RoundedCornerShape(14.dp)).semantics { selected = i == section; role = Role.Tab }, contentPadding = PaddingValues(vertical = 12.dp, horizontal = 2.dp)) {
                    Text(label, style = textFont(13, W.HEAVY), color = if (i == section) Color.White else Ink2)
                }
            }
        }
        key(section) {
            val pager = rememberPagerState(pageCount = { pages.size })
            val scope = rememberCoroutineScope()
            if (pages.isNotEmpty()) {
                HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { pages[it].id }) { n -> MagazineArticleView(pages[n], ui) }
                Row(Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0, modifier = Modifier.semantics { contentDescription = L("Page précédente", "Previous page") }) { Text("←", fontSize = 24.sp) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${pager.currentPage + 1} / ${pages.size} • " + L("Glisse pour explorer", "Swipe to explore"), style = textFont(12, W.BOLD), color = SkyShade)
                        LinearProgressIndicator(progress = { (pager.currentPage + 1f) / pages.size }, modifier = Modifier.fillMaxWidth(), color = Sky)
                    }
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < pages.lastIndex, modifier = Modifier.semantics { contentDescription = L("Page suivante", "Next page") }) { Text("→", fontSize = 24.sp) }
                }
            }
        }
    }
}
private fun localizedMagazineDate(value: String?, ui: UiLang): String? = if (ui != UiLang.EN) value else value
    ?.replace("Il y a environ 20 000 ans", "About 20,000 years ago")
    ?.replace("Avant le XVe – XIXe siècle", "Before the 15th–19th centuries")
    ?.replace("Vers ", "Around ")?.replace("juin", "June")?.replace("octobre", "October")?.replace("Décembre", "December")

@Composable
private fun MagazineArticleView(page: MagazinePage, ui: UiLang) {
    var revealed by remember(page.id) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    // A tablet on its side: the picture stays on the left and the article scrolls beside it.
    val wide = LocalConfiguration.current.let { it.screenWidthDp >= 840 && it.screenWidthDp > it.screenHeightDp }
    val credit: @Composable () -> Unit = {
        Text(L("Illustration éditoriale • Mboka na biso", "Editorial illustration • Mboka na biso"), style = textFont(10), color = Muted)
    }
    val words: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            page.date?.let { Text(it, style = textFont(12, W.HEAVY), color = page.color) }
            Text(page.title, style = displayFont(28), color = Ink)
            Text(page.intro, style = textFont(18, W.BOLD), color = Ink2)
            page.story?.let { story ->
                Box(Modifier.width(48.dp).height(4.dp).background(page.color.copy(alpha = 0.25f)))
                Text(story.detail(ui), style = textFont(16).copy(lineHeight = 25.sp), color = Ink2)
                Column(Modifier.fillMaxWidth().background(Sun.copy(alpha = 0.16f), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(L("✦ À toi de découvrir", "✦ Your discovery moment"), style = textFont(12, W.HEAVY), color = page.color)
                    Text(story.question(ui), style = displayFont(18), color = Ink)
                    if (revealed) Text(story.answer(ui), style = textFont(16, W.BOLD), color = Ink2)
                    TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) L("Masquer la réponse", "Hide answer") else L("Révéler la réponse", "Reveal answer"), style = textFont(14, W.HEAVY), color = SkyShade) }
                }
                TextButton(onClick = { uri.openUri(story.url) }) { Text("↗ ${story.source}", style = textFont(12, W.BOLD), color = SkyShade) }
            }
        }
    }
    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            MagazineCover(page, ui)
            page.story?.let { Text(it.place(ui), style = textFont(12, W.HEAVY), color = page.color) }
            credit()
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) { words() }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).readable(700.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        MagazineCover(page, ui)
        page.story?.let { Text(it.place(ui), style = textFont(12, W.HEAVY), color = page.color) }
        words()
        credit()
    }
}

/** One distinct illustration per article, addressed by stable content identity. */
private data class MagazineArtwork(val sheet: String, val tile: Int, val columns: Int, val rows: Int)
private object MagazineArtworkStore {
    val all: Map<String, MagazineArtwork> by lazy {
        val root = JSONObject(appContext.assets.open("magazine-art.json").bufferedReader().use { it.readText() })
        root.keys().asSequence().associateWith { key ->
            val o = root.getJSONObject(key)
            MagazineArtwork(o.getString("sheet"), o.getInt("tile"), o.getInt("columns"), o.getInt("rows"))
        }
    }
    // Bound decoded artwork memory while paging; PNGs remain bundled offline.
    private val cache = LruCache<String, ImageBitmap>(3)
    suspend fun load(sheet: String): ImageBitmap = withContext(Dispatchers.IO) {
        cache.get(sheet) ?: appContext.assets.open("$sheet.png").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }.also { cache.put(sheet, it) }
    }
}

@Composable
private fun MagazineCover(page: MagazinePage, ui: UiLang) {
    val art = MagazineArtworkStore.all[page.id] ?: return
    val bitmap by produceState<ImageBitmap?>(null, art.sheet) { value = MagazineArtworkStore.load(art.sheet) }
    Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(26.dp)).background(Surface)) {
        Canvas(Modifier.fillMaxSize()) {
            bitmap?.let { image ->
                val tileW = image.width / art.columns
                val tileH = image.height / art.rows
                drawImage(image,
                    srcOffset = IntOffset((art.tile % art.columns) * tileW, (art.tile / art.columns) * tileH),
                    srcSize = IntSize(tileW, tileH),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            }
        }
        Text("LOBA • EXPLORER", style = textFont(10, W.HEAVY).copy(letterSpacing = 1.sp), color = Ink,
            modifier = Modifier.padding(12.dp).background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(30.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
