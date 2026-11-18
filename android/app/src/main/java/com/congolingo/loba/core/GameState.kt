package com.congolingo.loba.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The learner's progress. Stored as the same JSON the website uses, so one account works on both. */
data class WordStat(
    val level: Int, // 0–5, spaced-repetition box
    val misses: Int,
    val due: Double, // epoch ms
)

data class Daily(
    val day: String, // YYYY-MM-DD (local)
    val xp: Int = 0,
    val lessons: Int = 0,
    val bestCombo: Int = 0,
    val perfect: Int = 0,
    val claimed: List<String> = emptyList(),
)

data class GameState(
    val onboarded: Boolean = false,
    val ui: UiLang = UiLang.FR,
    val lang: String = "ln",
    val goal: Int = 20,
    val sound: Boolean = true,
    val voice: Boolean = true,
    val name: String = "",
    val avatar: String = "🦜",
    val xp: Int = 0,
    val gems: Int = 50,
    val hearts: Int = Game.maxHearts,
    val heartsRefillAt: Double? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: String? = null,
    val activeDays: List<String> = emptyList(),
    val freezes: Int = 0,
    val doubleXpUntil: Double = 0.0,
    val lessonsDone: Int = 0,
    /** unit id → lessons completed (0…lessonsPerUnit) */
    val progress: Map<String, Int> = emptyMap(),
    val chests: Set<String> = emptySet(),
    /** "lang:term" → stats */
    val words: Map<String, WordStat> = emptyMap(),
    val daily: Daily = Daily(Game.dayKey()),
) {
    /** The website's shape. */
    val json: JSONObject
        get() = JSONObject().apply {
            put("v", 1); put("onboarded", onboarded); put("ui", ui.raw); put("lang", lang); put("goal", goal)
            put("sound", sound); put("voice", voice); put("name", name); put("avatar", avatar); put("xp", xp)
            put("gems", gems); put("hearts", hearts); put("heartsRefillAt", heartsRefillAt ?: JSONObject.NULL)
            put("streak", streak); put("bestStreak", bestStreak); put("lastActiveDay", lastActiveDay ?: JSONObject.NULL)
            put("activeDays", JSONArray(activeDays)); put("freezes", freezes); put("doubleXpUntil", doubleXpUntil)
            put("lessonsDone", lessonsDone)
            put("progress", JSONObject().also { o -> progress.forEach { (k, v) -> o.put(k, v) } })
            put("chests", JSONObject().also { o -> chests.forEach { o.put(it, true) } })
            put("words", JSONObject().also { o ->
                words.forEach { (k, w) -> o.put(k, JSONObject().put("level", w.level).put("misses", w.misses).put("due", w.due)) }
            })
            put("daily", JSONObject().put("day", daily.day).put("xp", daily.xp).put("lessons", daily.lessons)
                .put("bestCombo", daily.bestCombo).put("perfect", daily.perfect).put("claimed", JSONArray(daily.claimed)))
        }

    companion object {
        private val dayPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val langPattern = Regex("^[a-z]{2,3}$")

        private fun int(v: Any?, fallback: Int, min: Int = 0, max: Int = 10_000_000): Int {
            val d = (v as? Number)?.toDouble() ?: return fallback
            return if (d.isNaN() || d.isInfinite()) fallback else d.coerceIn(min.toDouble(), max.toDouble()).toInt()
        }

        private fun day(v: Any?): String? = (v as? String)?.takeIf { dayPattern.matches(it) }
        private fun double(v: Any?): Double? = (v as? Number)?.toDouble()?.takeIf { !it.isNaN() && !it.isInfinite() }

        /** Reads progress saved by this app or by the website. Returns null when it isn't a progress object at all. */
        fun from(json: Any?): GameState? {
            val o = json as? JSONObject ?: return null
            val goal = int(o.opt("goal"), 20)
            val streak = int(o.opt("streak"), 0)
            val progress = buildMap {
                (o.opt("progress") as? JSONObject)?.let { p -> p.keys().forEach { k -> if (p.opt(k) is Number) put(k, int(p.opt(k), 0, max = lessonsPerUnit)) } }
            }
            val words = buildMap {
                (o.opt("words") as? JSONObject)?.let { all ->
                    all.keys().forEach { k ->
                        val w = all.opt(k) as? JSONObject ?: return@forEach
                        put(k, WordStat(int(w.opt("level"), 0, max = 5), int(w.opt("misses"), 0), double(w.opt("due")) ?: 0.0))
                    }
                }
            }
            val d = o.opt("daily") as? JSONObject
            val dailyKey = day(d?.opt("day"))
            return GameState(
                onboarded = o.opt("onboarded") as? Boolean ?: false,
                ui = UiLang.of(o.opt("ui") as? String) ?: UiLang.FR,
                lang = (o.opt("lang") as? String)?.takeIf { langPattern.matches(it) } ?: "ln",
                goal = if (goal in Game.goals) goal else 20,
                sound = o.opt("sound") as? Boolean ?: true,
                voice = o.opt("voice") as? Boolean ?: true,
                name = ((o.opt("name") as? String) ?: "").take(20),
                avatar = (o.opt("avatar") as? String)?.takeIf { it in Game.avatars } ?: "🦜",
                xp = int(o.opt("xp"), 0),
                gems = int(o.opt("gems"), 50),
                hearts = int(o.opt("hearts"), Game.maxHearts, max = Game.maxHearts),
                heartsRefillAt = double(o.opt("heartsRefillAt")),
                streak = streak,
                bestStreak = maxOf(streak, int(o.opt("bestStreak"), 0)),
                lastActiveDay = day(o.opt("lastActiveDay")),
                activeDays = (o.opt("activeDays") as? JSONArray).items().mapNotNull(::day).takeLast(30),
                freezes = int(o.opt("freezes"), 0, max = Game.maxFreezes),
                doubleXpUntil = double(o.opt("doubleXpUntil")) ?: 0.0,
                lessonsDone = int(o.opt("lessonsDone"), 0),
                progress = progress,
                chests = (o.opt("chests") as? JSONObject)?.keys()?.asSequence()?.toSet() ?: emptySet(),
                words = words,
                daily = if (d != null && dailyKey != null) Daily(
                    dailyKey, int(d.opt("xp"), 0), int(d.opt("lessons"), 0), int(d.opt("bestCombo"), 0), int(d.opt("perfect"), 0),
                    (d.opt("claimed") as? JSONArray).items().filterIsInstance<String>(),
                ) else Daily(Game.dayKey()),
            )
        }
    }
}

object Game {
    const val maxHearts = 5
    const val heartRegenMs: Double = 30.0 * 60 * 1000
    const val maxFreezes = 2
    val goals = listOf(10, 20, 40, 60)
    val avatars = listOf("🦜", "🦁", "🐘", "🦒", "🐆", "🦓", "🦩", "🐒", "🦅", "🐢", "🐊", "🦋", "🌍", "⚽", "🥁", "🎸", "🌺", "👑")
    const val chestReward = 25
    const val perfectReward = 5

    fun now(): Double = System.currentTimeMillis().toDouble()

    fun dayKey(date: LocalDate = LocalDate.now()): String = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
}
