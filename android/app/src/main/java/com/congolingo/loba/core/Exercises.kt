package com.congolingo.loba.core

import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.roundToInt

// Grading (learn/src/game/grading.ts)

enum class Grade { CORRECT, TYPO, WRONG }

object Grading {
    private val marks = Regex("\\p{Mn}+")
    private val quotes = Regex("[’`]")
    private val punctuation = Regex("[?!.,;:…«»\"¿¡()\\-]")
    private val spaces = Regex("\\s+")
    private val tileEdges = Regex("^[«\"(¿¡]+|[?!.,;:…»\")]+$")

    /** Lower-case, strip accents and punctuation, collapse whitespace. */
    fun normalize(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        t = t.replace("ɛ", "e").replace("ɔ", "o")
        t = t.replace(quotes, "'")
        t = t.replace(punctuation, " ")
        t = t.replace(spaces, " ")
        return t.trim()
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, prev[j] + (if (a[i] == b[j]) 0 else 1))
            prev = cur
        }
        return prev[b.length]
    }

    /** Typed answers forgive case, accents, punctuation and one small typo. */
    fun typed(input: String, expected: String): Grade {
        val a = normalize(input)
        val b = normalize(expected)
        if (a.isEmpty()) return Grade.WRONG
        if (a == b) return Grade.CORRECT
        val allowed = if (b.length >= 10) 2 else if (b.length >= 5) 1 else 0
        return if (levenshtein(a, b) <= allowed) Grade.TYPO else Grade.WRONG
    }

    /** Word-bank answers must match exactly (ignoring case and punctuation). */
    fun tiles(tiles: List<String>, expected: String): Grade =
        if (normalize(tiles.joinToString(" ")) == normalize(expected)) Grade.CORRECT else Grade.WRONG

    /** Splits an answer into tappable tiles, dropping punctuation-only fragments. */
    fun toTiles(sentence: String): List<String> =
        sentence.split(spaces).map { it.replace(tileEdges, "") }.filter { it.isNotEmpty() }
}

// Exercises (learn/src/game/exercises.ts)

sealed class ExerciseKind {
    data class Meaning(val word: Word, val options: List<Word>, val isNew: Boolean) : ExerciseKind()
    data class Term(val word: Word, val options: List<Word>) : ExerciseKind()
    data class Listen(val word: Word, val options: List<Word>) : ExerciseKind()
    data class Match(val words: List<Word>) : ExerciseKind()
    data class Build(val sentence: Sentence, val tiles: List<String>) : ExerciseKind()
    data class Type(val word: Word) : ExerciseKind()
}

data class Exercise(val id: String, val kind: ExerciseKind, val retry: Boolean = false) {
    /** The word an exercise trains, for spaced-repetition tracking. */
    val word: Word?
        get() = when (kind) {
            is ExerciseKind.Meaning -> kind.word
            is ExerciseKind.Term -> kind.word
            is ExerciseKind.Listen -> kind.word
            is ExerciseKind.Type -> kind.word
            is ExerciseKind.Match, is ExerciseKind.Build -> null
        }
}

enum class PracticeMode { REVIEW, LISTEN }

object LessonBuilder {
    private var counter = 0
    private fun make(kind: ExerciseKind): Exercise {
        counter += 1
        return Exercise("e$counter", kind)
    }

    /** Picks 3 distractors whose term and meaning differ from the answer, so no two options look identical. */
    private fun options(word: Word, pool: List<Word>): List<Word> {
        val terms = mutableSetOf(word.term.lowercase())
        val meanings = mutableSetOf(word.fr.lowercase())
        val distractors = mutableListOf<Word>()
        for (w in pool.shuffled()) {
            val t = w.term.lowercase()
            val m = w.fr.lowercase()
            if (t in terms || m in meanings) continue
            terms += t
            meanings += m
            distractors += w
            if (distractors.size == 3) break
        }
        return (listOf(word) + distractors).shuffled()
    }

    private fun tiles(s: Sentence): List<String> {
        val answer = Grading.toTiles(s.answer)
        val lower = answer.map { it.lowercase() }.toSet()
        return (answer + s.distractors.filter { it.lowercase() !in lower }.take(4)).shuffled()
    }

    /**
     * Builds one lesson. Each unit has four lessons that ramp up:
     *  0 – meet the first half of the words   1 – meet the second half
     *  2 – mix everything, first sentences    3 – recall: typing and full sentences
     */
    fun lesson(unit: LessonUnit, index: Int, pool: List<Word>, canListen: Boolean, seen: Set<String>): List<Exercise> {
        val all = unit.words + pool.filter { it !in unit.words }
        val half = ceil(unit.words.size / 2.0).toInt()
        val focus = if (index == 0) unit.words.take(half) else if (index == 1) unit.words.drop(half) else unit.words
        // A short half still works; anything under 3 borrows from the whole unit.
        val words = if (focus.size >= 3) focus else unit.words

        fun meaning(w: Word) = make(ExerciseKind.Meaning(w, options(w, all), w.term !in seen))
        fun term(w: Word) = make(ExerciseKind.Term(w, options(w, all)))
        fun listen(w: Word) = if (canListen && w.audio != null) make(ExerciseKind.Listen(w, options(w, all))) else term(w)
        fun match(ws: List<Word>) = make(ExerciseKind.Match(ws.shuffled().take(5)))
        fun build(s: Sentence) = make(ExerciseKind.Build(s, tiles(s)))
        fun type(w: Word) = make(ExerciseKind.Type(w))
        fun sentences(n: Int) = unit.sentences.shuffled().take(n)
        fun slice(ws: List<Word>, from: Int, to: Int) = ws.subList(minOf(from, ws.size), minOf(to, ws.size))

        if (index <= 1) {
            // Introduce each word, then reinforce. Recall always comes after its introduction.
            val order = words.shuffled()
            val a = order[0]
            val b = order[1]
            val rest = order.drop(2)
            val out = mutableListOf(meaning(a), meaning(b), term(listOf(a, b).random()))
            out += rest.map(::meaning)
            out += listOf(match(words), listen(order.random()), term(rest.ifEmpty { order }.random()))
            return out
        }

        val ws = words.shuffled()
        if (index == 2) {
            val builds = sentences(2).map(::build)
            val fill = maxOf(0, 2 - builds.size)
            val mix = (slice(ws, 0, 2 + fill).map(::term) + slice(ws, 2 + fill, 4 + fill).map(::listen) + slice(ws, 4 + fill, 6 + fill).map(::meaning)).toMutableList()
            mix += type(ws[6 % ws.size])
            return (mix + builds).shuffled() + match(ws)
        }

        val builds = sentences(3).map(::build)
        val fill = maxOf(0, 3 - builds.size)
        val mix = slice(ws, 0, 3 + fill).map(::type) + slice(ws, 3 + fill, 5 + fill).map(::listen) + slice(ws, 5 + fill, 7 + fill).map(::term)
        return (mix + builds + match(ws)).shuffled()
    }

    /** A hearts-free practice session from words due for review. */
    fun practice(words: List<Word>, pool: List<Word>, mode: PracticeMode, canListen: Boolean): List<Exercise> {
        val p = if (pool.size >= 4) pool else words
        val ws = words.take(8)
        val optionsPool = ws + p
        if (mode == PracticeMode.LISTEN && canListen) {
            return words.filter { it.audio != null }.take(8).shuffled().map { make(ExerciseKind.Listen(it, options(it, optionsPool))) }
        }
        val out = ws.mapIndexed { i, w ->
            if (i % 3 == 2) make(ExerciseKind.Type(w))
            else if (i % 3 == 1) make(ExerciseKind.Term(w, options(w, optionsPool)))
            else make(ExerciseKind.Meaning(w, options(w, optionsPool), false))
        }.toMutableList()
        if (ws.size >= 4) out.add(2, make(ExerciseKind.Match(ws.take(5))))
        return out
    }
}

// Session (learn/src/game/session.ts)

enum class SessionStatus { ANSWERING, CORRECT, TYPO, WRONG }

/** Immutable: `grade` and `next` return the session that follows. Hold it in a `mutableStateOf`. */
data class LessonSession(
    val queue: List<Exercise>,
    val index: Int = 0,
    val total: Int = queue.size,
    val solved: Int = 0,
    val status: SessionStatus = SessionStatus.ANSWERING,
    val mistakes: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
) {
    val current: Exercise? get() = queue.getOrNull(index)
    val finished: Boolean get() = index >= queue.size
    val progress: Double get() = if (total == 0) 0.0 else minOf(1.0, solved.toDouble() / total)
    val accuracy: Int
        get() {
            val attempts = solved + mistakes
            return if (attempts == 0) 100 else (solved.toDouble() / attempts * 100).roundToInt()
        }

    fun grade(g: Grade): LessonSession {
        val again = current
        if (status != SessionStatus.ANSWERING || again == null) return this
        if (g == Grade.WRONG) {
            // A missed exercise comes back at the end, flagged as a previous mistake.
            return copy(
                queue = queue + again.copy(id = again.id + "-r${queue.size}", retry = true),
                status = SessionStatus.WRONG, mistakes = mistakes + 1, combo = 0,
            )
        }
        return copy(
            status = if (g == Grade.TYPO) SessionStatus.TYPO else SessionStatus.CORRECT,
            solved = solved + 1, combo = combo + 1, bestCombo = maxOf(bestCombo, combo + 1),
        )
    }

    fun next(): LessonSession = if (status == SessionStatus.ANSWERING) this else copy(index = index + 1, status = SessionStatus.ANSWERING)
}
