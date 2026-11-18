package com.congolingo.loba.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The application context, set once in LobaApplication. */
lateinit var appContext: Context

/** Lessons per unit before the unit's chest unlocks. */
const val lessonsPerUnit = 4

enum class UiLang(val raw: String) {
    FR("fr"), EN("en");

    companion object {
        fun of(raw: String?): UiLang? = entries.firstOrNull { it.raw == raw }
    }
}

data class Word(
    val term: String,
    val fr: String,
    val en: String,
    /** Id of a teacher's recording, played from /api/v1/audio/:id. */
    val audio: String?,
    val guessWord: Boolean = true,
    /** A sentence using the word, written in Studio, with its translations. Shown in the word puzzle. */
    val example: String = "",
    val exampleFr: String = "",
    val exampleEn: String = "",
) {
    fun meaning(ui: UiLang): String = if (ui == UiLang.EN && en.isNotEmpty()) en else fr
}

data class Sentence(val fr: String, val en: String, val answer: String, val distractors: List<String>) {
    fun prompt(ui: UiLang): String = if (ui == UiLang.EN && en.isNotEmpty()) en else fr
}

/** A unit of the course (`Unit` in the iOS app; renamed because Kotlin has its own Unit). */
data class LessonUnit(
    val id: String,
    val language: String,
    val position: Double,
    val title: String,
    val titleEn: String,
    val icon: String,
    val tip: String,
    val words: List<Word>,
    val sentences: List<Sentence>,
) {
    fun name(ui: UiLang): String = if (ui == UiLang.EN && titleEn.isNotEmpty()) titleEn else title
}

/** A learning language, as configured in Loba Studio. */
data class Language(
    val code: String,
    val nameFr: String,
    val nameEn: String,
    val greeting: String,
    val cheer: String,
    val regionFr: String,
    val regionEn: String,
    val color: String,
) {
    fun name(ui: UiLang): String = if (ui == UiLang.EN) nameEn else nameFr
    fun region(ui: UiLang): String = if (ui == UiLang.EN) regionEn else regionFr
}

/** Everything learners can study: visible languages that have at least one usable unit. */
data class Catalog(val languages: List<Language>, val curriculum: Map<String, List<LessonUnit>>) {
    fun units(lang: String): List<LessonUnit> = curriculum[lang] ?: emptyList()
}

// Parsing. Anything malformed is dropped, never shown.

private fun str(v: Any?, max: Int = 500): String = (v as? String)?.trim()?.take(max) ?: ""

internal fun JSONArray?.items(): List<Any> = if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it)?.takeIf { v -> v != JSONObject.NULL } }

object CatalogParser {
    private val langCode = Regex("^[a-z]{2,3}$")
    private val audioId = Regex("^[0-9a-f-]{36}$")
    private val hexColor = Regex("^#[0-9a-fA-F]{6}$")

    val bundledLanguages = listOf(
        Language("ln", "lingala", "Lingala", "Mbote", "Malamu mingi !", "Kinshasa, Brazzaville, l’Équateur", "Kinshasa, Brazzaville, Équateur", "#1c9bef"),
        Language("sw", "swahili", "Swahili", "Jambo", "Vizuri sana !", "Lubumbashi, Goma, Bukavu, Kisangani", "Lubumbashi, Goma, Bukavu, Kisangani", "#2bb673"),
    )

    fun word(v: Any?): Word? {
        val o = v as? JSONObject ?: return null
        val term = str(o.opt("term"))
        val fr = str(o.opt("fr"))
        if (term.isEmpty() || fr.isEmpty()) return null
        val audio = (o.opt("audio") as? String)?.takeIf { audioId.matches(it) }
        val example = str(o.opt("example"), 300)
        return Word(
            term, fr, str(o.opt("en")), audio, o.opt("guessWord") as? Boolean ?: true, example,
            if (example.isEmpty()) "" else str(o.opt("exampleFr"), 300), if (example.isEmpty()) "" else str(o.opt("exampleEn"), 300),
        )
    }

    fun sentence(v: Any?): Sentence? {
        val o = v as? JSONObject ?: return null
        val answer = str(o.opt("answer"))
        val fr = str(o.opt("fr"))
        if (answer.isEmpty() || fr.isEmpty()) return null
        val distractors = o.optJSONArray("distractors").items().map { str(it, 60) }.filter { it.isNotEmpty() }
        return Sentence(fr, str(o.opt("en")), answer, distractors)
    }

    fun unit(id: String, v: Any?): LessonUnit? {
        val o = v as? JSONObject ?: return null
        val language = (o.opt("language") as? String)?.takeIf { langCode.matches(it) } ?: return null
        val words = o.optJSONArray("words").items().mapNotNull(::word)
        if (words.size < 4) return null
        val icon = str(o.opt("icon"), 8)
        return LessonUnit(
            id, language, (o.opt("position") as? Number)?.toDouble() ?: 999.0, str(o.opt("title"), 120), str(o.opt("titleEn"), 120),
            icon.ifEmpty { "📘" }, str(o.opt("tip"), 1000), words, o.optJSONArray("sentences").items().mapNotNull(::sentence),
        )
    }

    /** Studio's language rows (snake_case). */
    fun language(v: Any?): Language? {
        val o = v as? JSONObject ?: return null
        val code = (o.opt("code") as? String)?.takeIf { langCode.matches(it) } ?: return null
        val en = str(o.opt("name_en"), 60)
        if (en.isEmpty()) return null
        val fr = str(o.opt("name_fr"), 60)
        val regionFr = str(o.opt("region_fr"), 120)
        val regionEn = str(o.opt("region_en"), 120)
        val color = (o.opt("color") as? String)?.takeIf { hexColor.matches(it) } ?: "#1c9bef"
        return Language(code, fr.ifEmpty { en }, en, str(o.opt("greeting"), 40), str(o.opt("cheer"), 60), regionFr, regionEn.ifEmpty { regionFr }, color)
    }

    /** Only languages that have at least one usable unit are offered, in Studio's order. */
    fun catalog(languages: List<Language>, units: List<LessonUnit>): Catalog {
        val byLang = units.groupBy { it.language }.mapValues { (_, list) -> list.sortedBy { it.position } }
        val known = languages.filter { !byLang[it.code].isNullOrEmpty() }
        return Catalog(known, byLang.filterKeys { key -> known.any { it.code == key } })
    }

    /** The curriculum shipped inside the app (a JSON array), so it works offline and on first launch. */
    fun bundled(json: String?): Catalog {
        val list = try { JSONArray(json ?: "") } catch (_: Exception) { return Catalog(emptyList(), emptyMap()) }
        return catalog(bundledLanguages, list.items().mapNotNull { unit(str((it as? JSONObject)?.opt("id")), it) })
    }

    fun bundled(): Catalog = bundled(try { appContext.assets.open("lessons.json").bufferedReader().use { it.readText() } } catch (_: Exception) { null })

    /** The response of GET /api/v1/lessons. */
    fun published(data: String): Catalog? {
        val body = try { JSONObject(data) } catch (_: Exception) { return null }
        val units = body.optJSONArray("lessons").items().mapNotNull { row -> (row as? JSONObject)?.let { unit(str(it.opt("id")), it.opt("content")) } }
        val languages = body.optJSONArray("languages")?.items()?.mapNotNull(::language) ?: bundledLanguages
        val result = catalog(languages, units)
        return if (result.languages.isEmpty()) null else result
    }
}
