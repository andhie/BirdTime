package com.andhiewong.birdtime

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

@Serializable
data class Question(
    val remark: String,
    val answer: String,
    val character: String = "",
)

data class Match(
    val question: Question,
    val score: Double,
    val strong: Boolean,
)

class QuestionMatcher(questions: List<Question>) {
    private val candidates = questions.map { it to normalize(it.remark) }

    fun match(text: String): Match? {
        val normalized = normalize(text)
        if (normalized.length < MIN_USEFUL_TEXT_LENGTH) return null

        var bestQuestion: Question? = null
        var bestScore = -1.0
        var secondScore = 0.0
        for ((question, remark) in candidates) {
            val score = 1.0 - distance(normalized, remark).toDouble() /
                maxOf(normalized.length, remark.length)
            if (score > bestScore) {
                secondScore = bestScore.coerceAtLeast(0.0)
                bestScore = score
                bestQuestion = question
            } else if (score > secondScore) {
                secondScore = score
            }
        }
        return bestQuestion?.let {
            Match(
                question = it,
                score = bestScore,
                strong = bestScore >= STRONG_SCORE_THRESHOLD &&
                    bestScore - secondScore >= STRONG_MARGIN_THRESHOLD,
            )
        }
    }

    private fun distance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    companion object {
        const val MIN_USEFUL_TEXT_LENGTH = 20
        // Calibration defaults: change only after checking target-phone samples.
        const val STRONG_SCORE_THRESHOLD = 0.62
        const val STRONG_MARGIN_THRESHOLD = 0.08

        private val separators = Regex("[^\\p{L}\\p{N}]+")

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace(separators, " ")
                .trim()
    }
}
