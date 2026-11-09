package com.andhiewong.birdtime

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDataTest {
    @Test
    fun packagedQuestionsHaveUniqueNonblankRemarksAndAnswers() {
        val records = Json.decodeFromString<List<Question>>(File("src/main/assets/questions.json").readText(Charsets.UTF_8))
        assertEquals(496, records.size)
        val remarks = mutableSetOf<String>()
        for ((index, record) in records.withIndex()) {
            val remark = record.remark
            val answer = record.answer
            assertTrue("Blank remark at index $index", remark.isNotBlank())
            assertTrue("Blank answer at index $index", answer.isNotBlank())
            assertTrue("Duplicate remark: $remark", remarks.add(remark))
        }
        assertEquals(496, remarks.size)
    }
}
