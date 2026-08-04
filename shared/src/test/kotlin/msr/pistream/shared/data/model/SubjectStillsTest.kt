package msr.pistream.shared.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectStillsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `stills as single object decodes to one cover`() {
        val raw = """{"subjectId":"1","stills":{"url":"https://x/a.jpg","width":1704,"format":"jpg"}}"""
        val subject = json.decodeFromString<Subject>(raw)
        assertEquals(1, subject.stillsList.size)
        assertEquals("https://x/a.jpg", subject.stillsList[0].url)
    }

    @Test
    fun `stills as list decodes to multiple covers`() {
        val raw = """{"subjectId":"1","stills":[{"url":"a.jpg"},{"url":"b.jpg"}]}"""
        val subject = json.decodeFromString<Subject>(raw)
        assertEquals(2, subject.stillsList.size)
    }

    @Test
    fun `stills absent yields empty list`() {
        val subject = json.decodeFromString<Subject>("""{"subjectId":"1"}""")
        assertEquals(0, subject.stillsList.size)
    }

    @Test
    fun `stills as plain json still yields empty list`() {
        val subject = json.decodeFromString<Subject>("""{"subjectId":"1","stills":42}""")
        assertEquals(0, subject.stillsList.size)
    }
}
