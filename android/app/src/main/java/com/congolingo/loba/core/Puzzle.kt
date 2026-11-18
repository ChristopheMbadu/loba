package com.congolingo.loba.core

import android.content.Context
import org.json.JSONObject
import java.text.BreakIterator
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The word puzzle: words from a Studio unit hidden in a grid of letters.
 * Finding a word reveals what it means and a sentence that uses it.
 * A "letter" is a String: one user-perceived character, so a vowel keeps its tone mark.
 */
data class Puzzle(val unit: LessonUnit, val entries: List<Entry>, val grid: List<List<String>>) {
    data class Cell(val row: Int, val col: Int)

    data class Example(val text: String, val fr: String, val en: String) {
        fun translation(ui: UiLang): String = if (ui == UiLang.EN && en.isNotEmpty()) en else fr
    }

    data class Entry(val word: Word, val letters: List<String>, val cells: List<Cell>, val example: Example?) {
        val id: String get() = word.term
    }

    /** The hidden word these cells spell, read either way, among those not found yet. */
    fun match(cells: List<Cell>, found: Set<String>): Entry? {
        val spelled = cells.map { grid[it.row][it.col] }
        return entries.firstOrNull { it.id !in found && (it.letters == spelled || it.letters == spelled.reversed()) }
    }

    companion object {
        const val size = 8
        /** How many words one grid hides. */
        const val count = 6

        private fun graphemes(s: String): List<String> {
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            val out = mutableListOf<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                out += s.substring(start, end)
                start = end
                end = it.next()
            }
            return out
        }

        /** The letters a term takes in the grid: capitals, no spaces or punctuation. Null when it doesn't fit. */
        fun letters(term: String): List<String>? {
            if (term.split(" ").filter { it.isNotEmpty() }.size > 2) return null
            val letters = graphemes(Normalizer.normalize(term, Normalizer.Form.NFC).uppercase()).filter { Character.isLetter(it.codePointAt(0)) }
            return if (letters.size in 3..size) letters else null
        }

        private fun tokens(text: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            for (ch in text.lowercase()) {
                if (ch.isLetter() || Character.getType(ch) == Character.NON_SPACING_MARK.toInt() || ch == '\'' || ch == '’') cur.append(ch)
                else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
            }
            if (cur.isNotEmpty()) out += cur.toString()
            return out
        }

        /** The sentence written for this word in Studio; otherwise a sentence of the course that uses it. */
        fun example(word: Word, unit: LessonUnit, units: List<LessonUnit>): Example? {
            if (word.example.isNotEmpty()) return Example(word.example, word.exampleFr, word.exampleEn)
            val wanted = tokens(word.term)
            if (wanted.isEmpty()) return null
            for (candidate in listOf(unit) + units.filter { it.id != unit.id }) {
                for (sentence in candidate.sentences) {
                    val have = tokens(sentence.answer)
                    // A sentence that is only the word itself teaches nothing more.
                    if (have.size <= wanted.size) continue
                    if ((0..(have.size - wanted.size)).any { have.subList(it, it + wanted.size) == wanted }) {
                        return Example(sentence.answer, sentence.fr, sentence.en)
                    }
                }
            }
            return null
        }

        /** Words for a unit's puzzle: its own first, topped up from the units that follow when it has few that fit. */
        fun words(unit: LessonUnit, units: List<LessonUnit>, rng: Random = Random.Default): List<Word> {
            val picked = mutableListOf<Word>()
            val seen = mutableSetOf<String>()
            val start = units.indexOfFirst { it.id == unit.id }.coerceAtLeast(0)
            val others = units.indices.map { units[(start + 1 + it) % maxOf(1, units.size)] }.filter { it.id != unit.id }
            for (source in listOf(unit) + others) {
                for (word in source.words.shuffled(rng)) {
                    if (picked.size >= count) continue
                    val key = letters(word.term)?.joinToString("") ?: continue
                    if (!seen.add(key)) continue
                    picked += word
                }
                if (picked.size >= count) break
            }
            return picked
        }

        /** Steps a word can run in. Early puzzles read left to right and downwards; later ones also run backwards. */
        fun directions(level: Int): List<Pair<Int, Int>> {
            val d = mutableListOf(0 to 1, 1 to 0)
            if (level >= 1) d += listOf(1 to 1, -1 to 1)
            if (level >= 4) d += listOf(0 to -1, -1 to 0)
            if (level >= 8) d += listOf(-1 to -1, 1 to -1)
            return d
        }

        fun make(unit: LessonUnit, units: List<LessonUnit>, rng: Random = Random.Default): Puzzle? {
            val level = units.indexOfFirst { it.id == unit.id }.coerceAtLeast(0)
            val words = words(unit, units, rng)
            if (words.size < 3) return null
            val steps = directions(level)
            repeat(40) {
                val grid = Array(size) { arrayOfNulls<String>(size) }
                val entries = mutableListOf<Entry>()
                // Longest first: they are the hardest to fit.
                for (word in words.sortedByDescending { letters(it.term)?.size ?: 0 }) {
                    val letters = letters(word.term) ?: continue
                    var placed: List<Cell>? = null
                    var tries = 0
                    while (tries < 160 && placed == null) {
                        tries += 1
                        val (dr, dc) = steps.random(rng)
                        val r = rng.nextInt(size)
                        val c = rng.nextInt(size)
                        val endR = r + dr * (letters.size - 1)
                        val endC = c + dc * (letters.size - 1)
                        if (endR !in 0 until size || endC !in 0 until size) continue
                        val cells = letters.indices.map { Cell(r + dr * it, c + dc * it) }
                        if (cells.zip(letters).all { (cell, letter) -> grid[cell.row][cell.col] == null || grid[cell.row][cell.col] == letter }) placed = cells
                    }
                    val cells = placed ?: break
                    cells.zip(letters).forEach { (cell, letter) -> grid[cell.row][cell.col] = letter }
                    entries += Entry(word, letters, cells, example(word, unit, units))
                }
                if (entries.size != words.size) return@repeat
                // The rest is filled with letters of the hidden words, so the grid looks like the language.
                val pool = entries.flatMap { it.letters }
                val filled = grid.map { row -> row.map { it ?: pool.random(rng) } }
                // Show the clues in the order the unit teaches them, not by length.
                val order = words.withIndex().associate { it.value.term to it.index }
                return Puzzle(unit, entries.sortedBy { order[it.id] ?: 0 }, filled)
            }
            return null
        }

        private val steps8 = listOf(0 to 1, 1 to 1, 1 to 0, 1 to -1, 0 to -1, -1 to -1, -1 to 0, -1 to 1)

        /** The straight line of cells from where the finger started to where it is, snapped to the nearest of 8 directions. */
        fun line(a: Cell, b: Cell): List<Cell> {
            val dr = b.row - a.row
            val dc = b.col - a.col
            if (dr == 0 && dc == 0) return listOf(a)
            val sector = (atan2(dr.toDouble(), dc.toDouble()) / (PI / 4)).roundToInt()
            val step = steps8[((sector % 8) + 8) % 8]
            val length = maxOf(abs(dr), abs(dc))
            return (0..length).map { Cell(a.row + step.first * it, a.col + step.second * it) }.filter { it.row in 0 until size && it.col in 0 until size }
        }

        /** Three stars without help, two with a little, one otherwise. */
        fun stars(hints: Int): Int = if (hints == 0) 3 else if (hints <= 2) 2 else 1
    }
}

/** Small things kept on this phone only (the iOS app's UserDefaults). */
object Prefs {
    val store by lazy { appContext.getSharedPreferences("loba", Context.MODE_PRIVATE) }

    fun intMap(key: String): Map<String, Int> = try {
        val o = JSONObject(store.getString(key, null) ?: "{}")
        o.keys().asSequence().associateWith { o.optInt(it) }
    } catch (_: Exception) { emptyMap() }

    fun setIntMap(key: String, value: Map<String, Int>) = store.edit().putString(key, JSONObject(value).toString()).apply()
}

/** Best stars per puzzle, kept on this phone. */
object PuzzleScores {
    private const val key = "loba-puzzle-stars"
    val all: Map<String, Int> get() = Prefs.intMap(key)
    fun record(unit: String, stars: Int) {
        val scores = all.toMutableMap()
        scores[unit] = maxOf(scores[unit] ?: 0, stars)
        Prefs.setIntMap(key, scores)
    }
    fun clear() = Prefs.store.edit().remove(key).apply()
}
