package com.andhiewong.birdtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QuestionMatcherTest {
    private val spear = Question(
        remark = "Do you think I'll ever be better with a spear than my dad?",
        answer = "Nod.",
    )
    private val hideAndSeek = Question(
        remark = "I bet it'd be fun to play hide-and-seek with you!",
        answer = "Flap your wings.",
    )
    private val matcher = QuestionMatcher(listOf(spear, hideAndSeek))

    @Test
    fun normalizesPunctuationApostrophesCaseAndWhitespace() {
        assertEquals(
            "i ll find café 123",
            QuestionMatcher.normalize("  I’LL...  FIND\n Cafe\u0301! １２３ "),
        )
    }

    @Test
    fun marksAnExactQuestionAsStrong() {
        val match = requireNotNull(matcher.match(spear.remark))
        assertEquals(spear, match.question)
        assertEquals(1.0, match.score, 0.0)
        assertTrue(match.strong)
    }

    @Test
    fun selectsCorrectAnswerWithAnOcrWordErrorAndMissingPunctuation() {
        val match = requireNotNull(
            matcher.match("Do you think Ill ever be better with a spcar than my dad"),
        )
        assertEquals("Nod.", match.question.answer)
        assertTrue(match.strong)
    }

    @Test
    fun returnsAWeakMatchForAPartialUsefulQuestion() {
        val match = requireNotNull(matcher.match("be better with a spear than my dad"))
        assertEquals(spear, match.question)
        assertFalse(match.strong)
    }

    @Test
    fun rejectsFewerThanTwentyNormalizedCharacters() {
        assertNull(matcher.match("  Hello!!! "))
        assertNull(matcher.match("abcdefghijklmnopqrs"))
        assertNull(matcher.match("?! \n\t"))
    }

    @Test
    fun acceptsAMatchingQuestionOfTwentyNormalizedCharacters() {
        val question = Question("Will you fly with me?", "Nod.")
        assertNotNull(QuestionMatcher(listOf(question)).match(question.remark))
    }

    @Test
    fun rejectsUnrelatedTextEvenWhenItIsLongEnough() {
        assertNull(matcher.match("ABCDEFGHIJKLMNOPQRST"))
    }

    @Test
    fun keepsCloseCandidatesWeakEvenForAnExactQuestion() {
        val close = Question(
            remark = "Do you think I'll ever be better with a spear than my mom?",
            answer = "Gaze intently.",
        )
        val match = requireNotNull(QuestionMatcher(listOf(spear, close)).match(spear.remark))
        assertEquals(spear, match.question)
        assertEquals(1.0, match.score, 0.0)
        assertFalse(match.strong)
    }

    @Test
    fun returnsNoMatchForAnEmptyDataset() {
        assertNull(QuestionMatcher(emptyList()).match(spear.remark))
    }

    @Test
    fun leavesTiedCandidatesWeak() {
        val match = requireNotNull(QuestionMatcher(listOf(spear, spear)).match(spear.remark))
        assertFalse(match.strong)
    }

    @Test
    fun matchesCompatibilityCharactersAndCanonicalAccents() {
        val question = Question("The café has 123 lovely little birds", "Nod.")
        val match = requireNotNull(
            QuestionMatcher(listOf(question)).match("THE CAFE\u0301 HAS １２３ LOVELY LITTLE BIRDS"),
        )
        assertEquals(1.0, match.score, 0.0)
        assertTrue(match.strong)
    }
}
