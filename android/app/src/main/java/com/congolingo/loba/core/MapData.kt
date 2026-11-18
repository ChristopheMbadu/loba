package com.congolingo.loba.core

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Region
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import com.congolingo.loba.ui.hexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Mboka na biso: the content of the map game. It is the website's own file, data/drc-map.json, served at
 * /api/v1/map: the app downloads it at launch and uses it from the next launch on. The copy bundled with the
 * app is that same file, for the first launch and when offline.
 */
data class MapText(val fr: String, val en: String) {
    operator fun invoke(ui: UiLang): String = if (ui == UiLang.EN && en.isNotEmpty()) en else fr
}

data class Province(
    val id: String, val name: String, val capital: String, val former: String, val language: String, val emoji: String,
    val peoples: List<String>, val borders: List<String>,
    val area: Int,
    val fact: MapText, val hint: MapText,
    /** The outline, as SVG path data in map units. */
    val d: String,
    val centre: Offset,
    val bounds: Rect,
    val shapeArea: Double,
)

data class MapRegion(val id: String, val name: MapText, val color: String, val provinces: List<String>) {
    val tint: Color get() = hexColor(color)
}

data class MapCard(val emoji: String?, val year: String?, val title: MapText, val text: MapText) {
    val id: String get() = title.fr
}

/** Turns SVG path data ("M10,20L30,40…Z") into a path. */
fun svgPath(d: String): Path = try { PathParser().parsePathString(d).toPath() } catch (_: Exception) { Path() }

class DRC private constructor(file: Content) {
    data class View(val width: Double, val height: Double, val lon: Double, val lat: Double, val zoom: Double)

    private class Content(
        val view: View, val regions: List<MapRegion>, val provinces: List<Province>,
        val history: List<MapCard>, val culture: List<MapCard>, val figures: List<MapCard>,
    )

    val view: View = file.view
    val regions: List<MapRegion> = file.regions
    val history: List<MapCard> = file.history
    val culture: List<MapCard> = file.culture
    val figures: List<MapCard> = file.figures
    /** Every province, in region order. */
    val ids: List<String> = file.regions.flatMap { it.provinces }
    private val byId: Map<String, Province> = file.provinces.associateBy { it.id }
    private val regionById: Map<String, MapRegion> = file.regions.flatMap { r -> r.provinces.map { it to r } }.toMap()
    /** Outlines in map units, parsed once. */
    val paths: Map<String, Path> = file.provinces.associate { it.id to svgPath(it.d) }
    val whole: Rect = Rect(0f, 0f, file.view.width.toFloat(), file.view.height.toFloat())

    // Hit-testing works on whole numbers, so the outlines are enlarged first.
    private val hitScale = 8f
    private val hits: Map<String, Region> by lazy {
        val clip = Region(0, 0, (whole.width * hitScale).toInt(), (whole.height * hitScale).toInt())
        val grow = Matrix().apply { setScale(hitScale, hitScale) }
        paths.mapValues { (_, path) ->
            val big = android.graphics.Path(path.asAndroidPath()).apply { transform(grow) }
            Region().apply { setPath(big, clip) }
        }
    }

    fun province(id: String): Province = byId.getValue(id)
    fun region(of: String): MapRegion = regionById.getValue(of)
    fun color(id: String): Color = regionById.getValue(id).tint

    /** Which province a point of the map (in map units) falls in. */
    fun province(at: Offset): String? = ids.firstOrNull { hits.getValue(it).contains((at.x * hitScale).toInt(), (at.y * hitScale).toInt()) }

    /** A square window on the map that frames these provinces with some of their neighbours. */
    fun frame(ids: List<String>): Rect {
        val boxes = ids.map { province(it).bounds }
        val box = Rect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
        val side = minOf(whole.width, maxOf(box.width, box.height) + 70)
        val x = maxOf(0f, minOf(whole.width - side, box.center.x - side / 2))
        val y = maxOf(0f, minOf(whole.height - side, box.center.y - side / 2))
        return Rect(x, y, x + side, y + side)
    }

    companion object {
        val shared: DRC by lazy {
            val saved = try { cache.takeIf { it.exists() }?.readText() } catch (_: Exception) { null }
            DRC(valid(saved) ?: valid(appContext.assets.open("drc-map.json").bufferedReader().use { it.readText() })!!)
        }

        private val cache: File get() = File(appContext.filesDir, "drc-map.json")

        private fun text(v: Any?): MapText? = (v as? JSONObject)?.let { o -> (o.opt("fr") as? String)?.let { MapText(it, o.opt("en") as? String ?: "") } }
        private fun strings(o: JSONObject, key: String): List<String> = o.optJSONArray(key).items().filterIsInstance<String>()
        private fun numbers(o: JSONObject, key: String): List<Float> = o.optJSONArray(key).items().mapNotNull { (it as? Number)?.toFloat() }

        private fun cards(o: JSONObject, key: String): List<MapCard>? = o.optJSONArray(key)?.items()?.map {
            val c = it as? JSONObject ?: return null
            MapCard(c.opt("emoji") as? String, c.opt("year") as? String, text(c.opt("title")) ?: return null, text(c.opt("text")) ?: return null)
        }

        /** Content is only accepted whole: every province of every region, with an outline. */
        private fun valid(data: String?): Content? {
            val o = try { JSONObject(data ?: return null) } catch (_: Exception) { return null }
            val v = o.optJSONObject("view") ?: return null
            fun num(key: String): Double? = (v.opt(key) as? Number)?.toDouble()
            val view = View(num("width") ?: return null, num("height") ?: return null, num("lon") ?: return null, num("lat") ?: return null, num("zoom") ?: return null)
            val regions = o.optJSONArray("regions")?.items()?.map {
                val r = it as? JSONObject ?: return null
                MapRegion(r.opt("id") as? String ?: return null, text(r.opt("name")) ?: return null, r.opt("color") as? String ?: return null, strings(r, "provinces"))
            } ?: return null
            val provinces = o.optJSONArray("provinces")?.items()?.map {
                val p = it as? JSONObject ?: return null
                fun s(key: String): String? = p.opt(key) as? String
                val c = numbers(p, "c")
                val box = numbers(p, "box")
                val d = s("d") ?: return null
                if (!d.startsWith("M") || c.size != 2 || box.size != 4) return null
                Province(
                    s("id") ?: return null, s("name") ?: return null, s("capital") ?: return null, s("former") ?: return null,
                    s("language") ?: return null, s("emoji") ?: return null, strings(p, "peoples"), strings(p, "borders"),
                    (p.opt("area") as? Number)?.toInt() ?: return null, text(p.opt("fact")) ?: return null, text(p.opt("hint")) ?: return null,
                    d, Offset(c[0], c[1]), Rect(box[0], box[1], box[2], box[3]), (p.opt("shapeArea") as? Number)?.toDouble() ?: return null,
                )
            } ?: return null
            val known = provinces.map { it.id }.toSet()
            val wanted = regions.flatMap { it.provinces }
            if (wanted.size < 20 || wanted.toSet() != known || wanted.size != known.size) return null
            return Content(view, regions, provinces, cards(o, "history") ?: return null, cards(o, "culture") ?: return null, cards(o, "figures") ?: return null)
        }

        /** Fetches the current content from the server and keeps it for the next launch. */
        suspend fun refresh() {
            val data = Api.fetch(Api.url("/api/v1/map")) ?: return
            withContext(Dispatchers.IO) {
                if (valid(String(data, Charsets.UTF_8)) == null) return@withContext
                try {
                    val tmp = File(appContext.filesDir, "drc-map.json.tmp")
                    tmp.writeBytes(data)
                    tmp.renameTo(cache)
                } catch (_: Exception) {}
            }
        }
    }
}

/** Stars and discovered provinces, kept on this phone. */
object MapProgress {
    private const val starsKey = "loba-map-stars"
    private const val stampsKey = "loba-map-stamps"
    val stars: Map<String, Int> get() = Prefs.intMap(starsKey)
    val stamps: List<String> get() = (Prefs.store.getString(stampsKey, null) ?: "").split("\n").filter { it.isNotEmpty() }
    fun record(level: String, stars: Int) {
        val all = this.stars.toMutableMap()
        all[level] = maxOf(all[level] ?: 0, stars)
        Prefs.setIntMap(starsKey, all)
    }
    fun stamp(id: String) {
        if (id !in stamps) Prefs.store.edit().putString(stampsKey, (stamps + id).joinToString("\n")).apply()
    }
    fun clear() = Prefs.store.edit().remove(starsKey).remove(stampsKey).apply()
}

/**
 * The Congo seen from space: one Mapbox image of exactly the view the outlines were projected to.
 * The public token is read from assets/Mapbox.txt, which is not committed; without it the map is drawn plain.
 */
object Satellite {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    private var asked = false

    fun load() {
        if (asked) return
        asked = true
        val token = try { appContext.assets.open("Mapbox.txt").bufferedReader().use { it.readText() }.trim() } catch (_: Exception) { return }
        if (!token.startsWith("pk.")) return
        val v = DRC.shared.view
        val url = "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/static/${v.lon},${v.lat},${v.zoom},0/${v.width.toInt()}x${v.height.toInt()}@2x?attribution=false&logo=false&access_token=$token"
        MainScope().launch {
            val data = Api.fetch(url) ?: return@launch
            val picture = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size) } ?: return@launch
            image = picture.asImageBitmap()
        }
    }
}
