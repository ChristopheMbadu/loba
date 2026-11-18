package com.congolingo.loba.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The game's rules, kept identical to the website's (learn/src/game/rules.ts) so progress means the same on both. */
object Rules {
    // dates
    fun daysBetween(a: String, b: String): Int {
        fun date(key: String): LocalDate = try { LocalDate.parse(key) } catch (_: Exception) { LocalDate.ofEpochDay(0) }
        return ChronoUnit.DAYS.between(date(a), date(b)).toInt()
    }

    // hearts
    /** Applies passive heart regeneration up to `now`. */
    fun syncHearts(s: GameState, now: Double): GameState {
        if (s.hearts >= Game.maxHearts) return s.copy(heartsRefillAt = null)
        var hearts = s.hearts
        var at = s.heartsRefillAt ?: (now + Game.heartRegenMs)
        while (hearts < Game.maxHearts && now >= at) {
            hearts += 1
            at += Game.heartRegenMs
        }
        return s.copy(hearts = hearts, heartsRefillAt = if (hearts >= Game.maxHearts) null else at)
    }

    fun loseHeart(s: GameState, now: Double): GameState {
        val n = syncHearts(s, now)
        return n.copy(hearts = maxOf(0, n.hearts - 1), heartsRefillAt = n.heartsRefillAt ?: (now + Game.heartRegenMs))
    }

    // streak
    /** The streak as it should be shown today (0 if broken and not covered by freezes). */
    fun visibleStreak(s: GameState, today: String): Int {
        val last = s.lastActiveDay ?: return 0
        val gap = daysBetween(last, today)
        if (gap <= 1) return s.streak
        return if (gap - 1 <= s.freezes) s.streak else 0
    }

    fun recordActivity(s: GameState, today: String): GameState {
        if (s.lastActiveDay == today) return s
        val gap = s.lastActiveDay?.let { daysBetween(it, today) }
        var streak = s.streak
        var freezes = s.freezes
        if (gap == 1) streak += 1
        else if (gap != null && gap > 1 && gap - 1 <= freezes) {
            freezes -= gap - 1
            streak += 1
        } else streak = 1
        return s.copy(
            streak = streak, freezes = freezes, bestStreak = maxOf(s.bestStreak, streak), lastActiveDay = today,
            activeDays = (s.activeDays + today).takeLast(30),
        )
    }

    fun rollDaily(s: GameState, today: String): GameState = if (s.daily.day == today) s else s.copy(daily = Daily(today))

    // progression
    fun unitProgress(s: GameState, u: LessonUnit): Int = minOf(lessonsPerUnit, s.progress[u.id] ?: 0)
    fun unitDone(s: GameState, u: LessonUnit): Boolean = unitProgress(s, u) >= lessonsPerUnit
    fun unitUnlocked(s: GameState, units: List<LessonUnit>, i: Int): Boolean = i == 0 || unitDone(s, units[i - 1])
    fun currentUnitIndex(s: GameState, units: List<LessonUnit>): Int =
        units.indexOfFirst { !unitDone(s, it) }.let { if (it >= 0) it else maxOf(0, units.size - 1) }
    fun unlockedWords(s: GameState, units: List<LessonUnit>): List<Word> =
        units.withIndex().filter { unitUnlocked(s, units, it.index) }.flatMap { it.value.words }
    fun xpMultiplier(s: GameState, now: Double): Int = if (now < s.doubleXpUntil) 2 else 1

    enum class SessionKind { LESSON, PRACTICE }

    data class SessionResult(val kind: SessionKind, val unitId: String? = null, val lessonIndex: Int? = null, val mistakes: Int, val bestCombo: Int)

    data class Reward(val xp: Int, val gems: Int, val perfect: Boolean, val streakExtended: Boolean)

    fun lessonXp(r: SessionResult): Int = (if (r.kind == SessionKind.LESSON) 10 else 8) + (if (r.mistakes == 0) 5 else 0)

    fun completeSession(s: GameState, r: SessionResult, now: Double, today: String): Pair<GameState, Reward> {
        var n = rollDaily(s, today)
        val perfect = r.mistakes == 0
        val xp = lessonXp(r) * xpMultiplier(n, now)
        val gems = if (perfect && r.kind == SessionKind.LESSON) Game.perfectReward else 0
        val before = n.lastActiveDay
        n = recordActivity(n, today)
        var progress = n.progress
        if (r.kind == SessionKind.LESSON && r.unitId != null && r.lessonIndex != null) {
            // Replaying an earlier lesson never moves progress backwards.
            progress = progress + (r.unitId to minOf(lessonsPerUnit, maxOf(progress[r.unitId] ?: 0, r.lessonIndex + 1)))
        }
        n = n.copy(
            progress = progress, xp = n.xp + xp, gems = n.gems + gems, lessonsDone = n.lessonsDone + 1,
            daily = n.daily.copy(
                xp = n.daily.xp + xp, lessons = n.daily.lessons + 1, bestCombo = maxOf(n.daily.bestCombo, r.bestCombo),
                perfect = n.daily.perfect + (if (perfect) 1 else 0),
            ),
        )
        return n to Reward(xp, gems, perfect, before != today)
    }

    // spaced repetition
    private val intervalDays = doubleArrayOf(0.0, 1.0, 2.0, 4.0, 7.0, 14.0)
    fun wordKey(lang: String, term: String): String = "$lang:$term"

    fun recordWord(s: GameState, lang: String, term: String, correct: Boolean, now: Double): GameState {
        val key = wordKey(lang, term)
        val prev = s.words[key] ?: WordStat(0, 0, now)
        val level = if (correct) minOf(5, prev.level + 1) else maxOf(0, prev.level - 2)
        return s.copy(words = s.words + (key to WordStat(level, prev.misses + (if (correct) 0 else 1), if (correct) now + intervalDays[level] * 864e5 else now)))
    }

    /** Words seen before, weakest and most overdue first. */
    fun reviewWords(s: GameState, lang: String, pool: List<Word>, now: Double): List<Word> =
        pool.mapNotNull { w -> s.words[wordKey(lang, w.term)]?.let { w to it } }
            .filter { it.second.due <= now }
            .sortedWith(compareBy<Pair<Word, WordStat>> { it.second.level }.thenByDescending { it.second.misses }.thenBy { it.second.due })
            .map { it.first }

    fun wordsLearned(s: GameState, lang: String): Int = s.words.count { it.key.startsWith("$lang:") && it.value.level >= 1 }

    // quests
    enum class QuestId(val raw: String) { XP("xp"), LESSONS("lessons"), COMBO("combo"), PERFECT("perfect") }

    data class Quest(val id: QuestId, val goal: Int, val value: Int, val reward: Int) {
        val done: Boolean get() = value >= goal
    }

    fun dailyQuests(s: GameState, today: String): List<Quest> {
        val d = if (s.daily.day == today) s.daily else Daily(today)
        return listOf(
            Quest(QuestId.XP, s.goal, d.xp, 15),
            Quest(QuestId.LESSONS, 2, d.lessons, 15),
            Quest(QuestId.COMBO, 8, d.bestCombo, 20),
            Quest(QuestId.PERFECT, 1, d.perfect, 20),
        )
    }

    fun claimQuest(s: GameState, id: QuestId, today: String): GameState {
        val n = rollDaily(s, today)
        val q = dailyQuests(n, today).firstOrNull { it.id == id } ?: return s
        if (!q.done || id.raw in n.daily.claimed) return s
        return n.copy(gems = n.gems + q.reward, daily = n.daily.copy(claimed = n.daily.claimed + id.raw))
    }

    // shop
    enum class ShopItem(val price: Int) { REFILL(50), FREEZE(100), DOUBLE_XP(80) }

    fun canBuy(s: GameState, item: ShopItem, now: Double): Boolean {
        if (s.gems < item.price) return false
        return when (item) {
            ShopItem.REFILL -> syncHearts(s, now).hearts < Game.maxHearts
            ShopItem.FREEZE -> s.freezes < Game.maxFreezes
            ShopItem.DOUBLE_XP -> now >= s.doubleXpUntil
        }
    }

    fun buy(s: GameState, item: ShopItem, now: Double): GameState {
        if (!canBuy(s, item, now)) return s
        val n = s.copy(gems = s.gems - item.price)
        return when (item) {
            ShopItem.REFILL -> n.copy(hearts = Game.maxHearts, heartsRefillAt = null)
            ShopItem.FREEZE -> n.copy(freezes = n.freezes + 1)
            ShopItem.DOUBLE_XP -> n.copy(doubleXpUntil = now + 15 * 60 * 1000)
        }
    }

    fun openChest(s: GameState, u: LessonUnit): GameState {
        if (!unitDone(s, u) || u.id in s.chests) return s
        return s.copy(gems = s.gems + Game.chestReward, chests = s.chests + u.id)
    }
}
