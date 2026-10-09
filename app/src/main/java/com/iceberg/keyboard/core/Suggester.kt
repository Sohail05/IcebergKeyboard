package com.iceberg.keyboard.core

import kotlin.math.ln

/**
 * Word completion + correction engine.
 *
 * Base vocabulary comes from a frequency-ranked word list (rank 0 = most common). Words the user
 * types are learned with a count; learned words get a frequency boost and are never "corrected".
 * Everything is in-memory: ~10k words -> a sorted array for prefix lookup plus a hash map.
 */
class Suggester(rankedWords: List<String>) {

    enum class Kind { COMPLETION, CORRECTION, EXACT }

    data class Candidate(val word: String, val kind: Kind, val score: Double)

    private val baseFreq: HashMap<String, Int> = HashMap(rankedWords.size * 2)
    private val userCounts: HashMap<String, Int> = HashMap()
    @Volatile private var sorted: Array<String>

    init {
        rankedWords.forEachIndexed { rank, w ->
            val word = w.trim().lowercase()
            // Zipf-like: frequency proportional to 1/(rank+1), scaled into an Int.
            if (word.isNotEmpty() && word !in baseFreq) baseFreq[word] = (1_000_000.0 / (rank + 1)).toInt().coerceAtLeast(1)
        }
        // Web corpora strip apostrophes ("dont", "im"); swap those for the real contraction so they get corrected.
        for ((bare, proper) in CONTRACTIONS) {
            val f = baseFreq.remove(bare) ?: continue
            baseFreq[proper.lowercase()] = maxOf(f, baseFreq[proper.lowercase()] ?: 0)
        }
        sorted = baseFreq.keys.toTypedArray().also { it.sort() }
    }

    @Synchronized
    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        return w in baseFreq || (userCounts[w] ?: 0) >= USER_KNOWN_THRESHOLD
    }

    /** Records a committed word. Returns true if the user dictionary changed. */
    @Synchronized
    fun learn(word: String, weight: Int = 1): Boolean {
        if (word.length < 2 || word.length > 32 || !TextRules.isAllLetters(word)) return false
        val w = word.lowercase()
        val wasNew = w !in userCounts && w !in baseFreq
        userCounts[w] = (userCounts[w] ?: 0) + weight
        if (wasNew) rebuildIndex()
        return true
    }

    @Synchronized
    fun forget(word: String): Boolean {
        val removed = userCounts.remove(word.lowercase()) != null
        if (removed) rebuildIndex()
        return removed
    }

    @Synchronized
    fun exportUserWords(): String = userCounts.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    @Synchronized
    fun importUserWords(text: String) {
        text.lineSequence().forEach { line ->
            val cols = line.split('\t')
            val w = cols[0].trim().lowercase()
            val c = cols.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            if (w.isNotEmpty()) userCounts[w] = maxOf(userCounts[w] ?: 0, c)
        }
        rebuildIndex()
    }

    @Synchronized
    fun userWordCount() = userCounts.size

    private fun rebuildIndex() {
        val all = HashSet<String>(baseFreq.keys)
        all.addAll(userCounts.keys)
        sorted = all.toTypedArray().also { it.sort() }
    }

    private fun freq(w: String): Double {
        val base = baseFreq[w] ?: 0
        val user = userCounts[w] ?: 0
        if (base == 0 && user < USER_KNOWN_THRESHOLD) return 0.0
        return base + user * USER_WORD_BOOST.toDouble()
    }

    /**
     * Up to [limit] candidates for the partially typed [typed] word, best first, with the user's
     * capitalisation applied. The typed word itself is never returned.
     */
    @Synchronized
    fun suggest(typed: String, limit: Int = 3): List<Candidate> {
        if (typed.isEmpty() || typed.length > 32) return emptyList()
        val lower = typed.lowercase()
        val best = HashMap<String, Candidate>()
        fun offer(word: String, kind: Kind, score: Double) {
            if (word == lower) return
            val cur = best[word]
            if (cur == null || cur.score < score) best[word] = Candidate(word, kind, score)
        }

        // Completions: words starting with the typed prefix.
        val arr = sorted
        var i = lowerBound(arr, lower)
        var scanned = 0
        while (i < arr.size && arr[i].startsWith(lower) && scanned < MAX_PREFIX_SCAN) {
            val w = arr[i]
            val f = freq(w)
            if (f > 0) {
                // Prefer completions that are close in length to what was typed.
                val extra = w.length - lower.length
                offer(w, Kind.COMPLETION, ln(f) - extra * 0.35)
            }
            i++; scanned++
        }

        // Corrections: single edits, weighted by keyboard proximity for substitutions.
        if (lower.length >= 2) {
            for ((w, penalty) in edits1(lower)) {
                val f = freq(w)
                // While typing, a word that starts with what you typed beats a guessed typo.
                if (f > 0) offer(w, Kind.CORRECTION, ln(f) - penalty - CORRECTION_VS_COMPLETION)
            }
        }

        return best.values.sortedByDescending { it.score }.take(limit).map { it.copy(word = applyTypedCase(typed, it.word)) }
    }

    /**
     * The word [typed] should be auto-corrected to, or null. Deliberately conservative:
     * only unknown, letters-only words with a strong single-edit candidate.
     */
    @Synchronized
    fun autocorrect(typed: String): String? {
        if (typed == "i") return "I"
        if (typed.length < 2 || !TextRules.isAllLetters(typed) || isKnown(typed)) return null
        val lower = typed.lowercase()
        // Common contractions: "dont" -> "don't".
        CONTRACTIONS[lower]?.let { return applyTypedCase(typed, it) }
        var bestWord: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for ((w, penalty) in edits1(lower)) {
            val f = freq(w)
            if (f <= 0) continue
            val s = ln(f) - penalty
            if (s > bestScore) { bestScore = s; bestWord = w }
        }
        if (bestWord == null || bestScore < MIN_AUTOCORRECT_SCORE) return null
        return applyTypedCase(typed, bestWord)
    }

    /** All strings one edit away, paired with a cost (lower = more plausible typo). */
    private fun edits1(w: String): Sequence<Pair<String, Double>> = sequence {
        val n = w.length
        for (i in 0 until n) {
            // deletion (extra letter typed)
            yield(w.removeRange(i, i + 1) to 1.6)
            // transposition
            if (i < n - 1) {
                val c = w.toCharArray(); val t = c[i]; c[i] = c[i + 1]; c[i + 1] = t
                yield(String(c) to 1.0)
            }
            // substitution (cheaper when the keys are neighbours)
            for (ch in ALPHABET) {
                if (ch == w[i]) continue
                val cost = if (Keyboard.areNeighbours(w[i], ch)) 1.2 else 3.0
                yield(w.substring(0, i) + ch + w.substring(i + 1) to cost)
            }
        }
        // insertion (missed letter)
        for (i in 0..n) for (ch in ALPHABET) {
            yield(w.substring(0, i) + ch + w.substring(i) to 2.0)
        }
    }

    private fun lowerBound(arr: Array<String>, key: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** QWERTY adjacency used to weight substitutions. */
    object Keyboard {
        private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val pos: Map<Char, Pair<Int, Float>> = buildMap {
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(ch, r to c + r * 0.5f) } }
        }

        fun areNeighbours(a: Char, b: Char): Boolean {
            val pa = pos[a] ?: return false
            val pb = pos[b] ?: return false
            return kotlin.math.abs(pa.first - pb.first) <= 1 && kotlin.math.abs(pa.second - pb.second) <= 1.01f
        }
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
        private const val MAX_PREFIX_SCAN = 400
        private const val CORRECTION_VS_COMPLETION = 1.5
        const val USER_KNOWN_THRESHOLD = 2
        private const val USER_WORD_BOOST = 2_000
        /** ln(freq) - penalty; ~ rank 5000 with a neighbouring-key typo. */
        private const val MIN_AUTOCORRECT_SCORE = 4.0

        private val CONTRACTIONS = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "didnt" to "didn't",
            "doesnt" to "doesn't", "wasnt" to "wasn't", "arent" to "aren't", "couldnt" to "couldn't",
            "shouldnt" to "shouldn't", "wouldnt" to "wouldn't", "havent" to "haven't", "hasnt" to "hasn't",
            "im" to "I'm", "youre" to "you're", "theyre" to "they're", "thats" to "that's",
            "whats" to "what's", "ive" to "I've",
        )
    }
}
