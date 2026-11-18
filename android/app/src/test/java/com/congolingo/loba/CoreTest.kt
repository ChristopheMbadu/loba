package com.congolingo.loba

import com.congolingo.loba.core.CatalogParser
import com.congolingo.loba.core.Exercise
import com.congolingo.loba.core.ExerciseKind
import com.congolingo.loba.core.Game
import com.congolingo.loba.core.GameState
import com.congolingo.loba.core.GoogleAuthRequest
import com.congolingo.loba.core.Grade
import com.congolingo.loba.core.Grading
import com.congolingo.loba.core.LessonBuilder
import com.congolingo.loba.core.LessonSession
import com.congolingo.loba.core.Puzzle
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.SessionStatus
import com.congolingo.loba.core.lessonsPerUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CoreTest {
    private val catalog by lazy { CatalogParser.bundled(File("src/main/assets/lessons.json").readText()) }

    // The same checks as ios/Tests/GoogleAuthRequestTests.swift.
    @Test
    fun googleAuthRequest() {
        val request = GoogleAuthRequest()
        val second = GoogleAuthRequest()
        assertTrue(request.verifier.length == 43 && request.challenge.length == 43 && request.state.length == 43)
        assertTrue(request.verifier != second.verifier && request.state != second.state)
        val start = request.startURL("https://congolingo.com")
        assertTrue(start.startsWith("https://congolingo.com/api/learn/google/start?"))
        assertTrue(start.contains("challenge=${request.challenge}"))
        assertFalse(start.contains(request.verifier))
        val code = "c".repeat(43)
        val valid = "loba://auth?code=$code&state=${request.state}"
        assertEquals(code, request.code(valid))
        val root = valid.replace("//auth?", "//auth/?")
        assertEquals(code, request.code(root))
        for (value in listOf("$valid#", "$root#", valid.replace("loba://auth", "LOBA://AUTH"))) assertEquals(code, request.code(value))
        for ((value, expected) in listOf(
            "$valid#fragment" to GoogleAuthRequest.CallbackIssue.FRAGMENT,
            valid.replace("//auth?", "//wrong?") to GoogleAuthRequest.CallbackIssue.HOST,
            valid.replace("//auth?", "//auth/unexpected?") to GoogleAuthRequest.CallbackIssue.PATH,
            "$valid&error=google_failed" to GoogleAuthRequest.CallbackIssue.PAYLOAD,
        )) {
            try { request.code(value); fail("Accepted invalid callback") } catch (e: GoogleAuthRequest.Failure.InvalidCallback) { assertEquals(expected, e.issue) }
        }
        for (value in listOf(
            valid.replace("loba:", "https:"), valid.replace("//auth?", "//wrong?"), valid.replace("//auth?", "//auth/unexpected?"),
            valid.replace(request.state, second.state), "$valid&state=${request.state}", "$valid&code=$code",
            "$valid&error=google_failed", "$valid#fragment", valid.replace("//auth?", "//user@auth?"), valid.replace("//auth?", "//auth:443?"),
            root.replace(request.state, second.state), valid.replace(code, "short"), "loba://auth?code=$code", "loba://auth?state=${request.state}",
        )) {
            try { request.code(value); fail("Accepted invalid callback: $value") }
            catch (_: GoogleAuthRequest.Failure.InvalidCallback) {} catch (_: GoogleAuthRequest.Failure.InvalidState) {} catch (_: GoogleAuthRequest.Failure.InvalidCode) {}
        }
        try {
            request.code("loba://auth?error=google_cancelled&state=${request.state}")
            fail("Cancellation was accepted as success")
        } catch (e: GoogleAuthRequest.Failure.Provider) { assertEquals("google_cancelled", e.reason) }
    }

    @Test
    fun bundledLessonsParse() {
        assertTrue(catalog.languages.map { it.code }.contains("ln"))
        val units = catalog.units("ln")
        assertTrue(units.size >= 4)
        assertTrue(units.all { it.words.size >= 4 })
        assertEquals(units.sortedBy { it.position }, units)
    }

    @Test
    fun grading() {
        assertEquals("matondi", Grading.normalize("  Matɔndi ! "))
        assertEquals(Grade.CORRECT, Grading.typed("matondi", "Matɔndi"))
        assertEquals(Grade.CORRECT, Grading.typed("Mbóte", "mbote"))
        assertEquals(Grade.TYPO, Grading.typed("matondo", "Matɔndi"))
        assertEquals(Grade.WRONG, Grading.typed("mai", "Mbote"))
        assertEquals(Grade.WRONG, Grading.typed("", "Mbote"))
        assertEquals(listOf("Nalingi", "yo"), Grading.toTiles("« Nalingi yo ! »"))
        assertEquals(Grade.CORRECT, Grading.tiles(listOf("nalingi", "yo"), "Nalingi yo."))
        assertEquals(3, Grading.levenshtein("kitten", "sitting"))
    }

    @Test
    fun progressJsonRoundTrips() {
        val unit = catalog.units("ln").first()
        var s = GameState(onboarded = true, name = "Chris")
        s = Rules.recordWord(s, "ln", unit.words[0].term, true, 1000.0)
        s = Rules.completeSession(s, Rules.SessionResult(Rules.SessionKind.LESSON, unit.id, 0, 0, 7), 2000.0, "2026-11-18").first
        s = Rules.loseHeart(s, 3000.0)
        val back = GameState.from(JSONObject(s.json.toString()))
        assertEquals(s, back)
        assertNull(GameState.from("nope"))
        // Out-of-range values written by anything else are clamped, not trusted.
        val odd = GameState.from(JSONObject("""{"hearts":99,"goal":7,"xp":-4,"progress":{"u":12},"avatar":"x","daily":{"day":"bad"}}"""))!!
        assertEquals(Game.maxHearts, odd.hearts); assertEquals(20, odd.goal); assertEquals(0, odd.xp)
        assertEquals(lessonsPerUnit, odd.progress["u"]); assertEquals("🦜", odd.avatar)
    }

    @Test
    fun rules() {
        val unit = catalog.units("ln").first()
        assertEquals(1, Rules.daysBetween("2026-02-28", "2026-03-01"))
        var s = GameState()
        val (after, reward) = Rules.completeSession(s, Rules.SessionResult(Rules.SessionKind.LESSON, unit.id, 0, 0, 3), 0.0, "2026-11-18")
        assertEquals(15, reward.xp); assertEquals(Game.perfectReward, reward.gems); assertTrue(reward.streakExtended)
        assertEquals(1, after.streak); assertEquals(1, after.progress[unit.id])
        // The next day extends the streak; a two-day gap breaks it unless a freeze covers it.
        assertEquals(2, Rules.recordActivity(after, "2026-11-19").streak)
        assertEquals(1, Rules.recordActivity(after, "2026-11-20").streak)
        val frozen = Rules.recordActivity(after.copy(freezes = 1), "2026-11-20")
        assertEquals(2, frozen.streak); assertEquals(0, frozen.freezes)
        assertEquals(0, Rules.visibleStreak(after, "2026-11-21"))
        // Replaying an earlier lesson never moves progress backwards.
        s = after.copy(progress = mapOf(unit.id to 3))
        assertEquals(3, Rules.completeSession(s, Rules.SessionResult(Rules.SessionKind.LESSON, unit.id, 0, 1, 0), 0.0, "2026-11-18").first.progress[unit.id])
        // Hearts come back one every half hour.
        val hurt = Rules.loseHeart(Rules.loseHeart(GameState(), 0.0), 0.0)
        assertEquals(3, hurt.hearts)
        assertEquals(4, Rules.syncHearts(hurt, Game.heartRegenMs).hearts)
        val full = Rules.syncHearts(hurt, Game.heartRegenMs * 2)
        assertEquals(5, full.hearts); assertNull(full.heartsRefillAt)
        // Shop and quests.
        assertFalse(Rules.canBuy(GameState(), Rules.ShopItem.REFILL, 0.0))
        assertEquals(5, Rules.buy(hurt, Rules.ShopItem.REFILL, 0.0).hearts)
        assertEquals(0, Rules.buy(hurt, Rules.ShopItem.REFILL, 0.0).gems)
        val claimed = Rules.claimQuest(after, Rules.QuestId.PERFECT, "2026-11-18")
        assertEquals(after.gems + 20, claimed.gems)
        assertEquals(claimed, Rules.claimQuest(claimed, Rules.QuestId.PERFECT, "2026-11-18"))
        assertEquals(after, Rules.claimQuest(after, Rules.QuestId.COMBO, "2026-11-18"))
    }

    @Test
    fun lessonsAndSession() {
        val units = catalog.units("ln")
        for (unit in units) for (index in 0 until lessonsPerUnit) {
            val lesson = LessonBuilder.lesson(unit, index, units.flatMap { it.words }, canListen = true, seen = emptySet())
            assertTrue("${unit.id}/$index", lesson.size >= 5)
            assertEquals(lesson.size, lesson.map { it.id }.toSet().size)
            for (e in lesson) when (val k = e.kind) {
                is ExerciseKind.Meaning -> assertTrue(k.word in k.options && k.options.map { it.fr.lowercase() }.toSet().size == k.options.size)
                is ExerciseKind.Term -> assertTrue(k.word in k.options)
                is ExerciseKind.Listen -> assertNotNull(k.word.audio)
                is ExerciseKind.Build -> assertEquals(Grade.CORRECT, Grading.tiles(Grading.toTiles(k.sentence.answer), k.sentence.answer))
                else -> {}
            }
        }
        var session = LessonSession(listOf(Exercise("a", ExerciseKind.Type(units[0].words[0])), Exercise("b", ExerciseKind.Type(units[0].words[1]))))
        session = session.grade(Grade.WRONG)
        assertEquals(SessionStatus.WRONG, session.status); assertEquals(3, session.queue.size); assertTrue(session.queue.last().retry)
        assertEquals(session, session.grade(Grade.CORRECT))
        session = session.next().grade(Grade.CORRECT).next().grade(Grade.TYPO).next()
        assertTrue(session.finished); assertEquals(1, session.mistakes); assertEquals(2, session.bestCombo); assertEquals(67, session.accuracy)
        assertEquals(1.0, session.progress, 0.0)
    }

    @Test
    fun puzzle() {
        assertEquals(listOf("M", "B", "O", "T", "E"), Puzzle.letters("Mbote"))
        // A vowel keeps its tone mark as one letter.
        assertEquals(4, Puzzle.letters("mɔ́tɔ")!!.size)
        assertNull(Puzzle.letters("na ndako na ngai"))
        assertEquals(listOf(Puzzle.Cell(0, 0), Puzzle.Cell(1, 1), Puzzle.Cell(2, 2)), Puzzle.line(Puzzle.Cell(0, 0), Puzzle.Cell(2, 2)))
        assertEquals(3, Puzzle.line(Puzzle.Cell(3, 3), Puzzle.Cell(3, 1)).size)
        val units = catalog.units("ln")
        var made = 0
        for (unit in units) {
            val puzzle = Puzzle.make(unit, units, Random(7)) ?: continue
            made += 1
            assertEquals(Puzzle.size, puzzle.grid.size)
            for (entry in puzzle.entries) {
                assertEquals(entry.letters, entry.cells.map { puzzle.grid[it.row][it.col] })
                assertEquals(entry.id, puzzle.match(entry.cells.reversed(), emptySet())?.let { found -> if (found.letters == entry.letters) entry.id else found.id })
            }
        }
        assertTrue(made >= units.size / 2)
        assertNotEquals(Puzzle.stars(0), Puzzle.stars(3))
    }
}
