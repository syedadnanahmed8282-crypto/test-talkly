package com.family.talkly

import com.family.talkly.ui.components.parseUrlSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAwareMessageTextTest {

    @Test
    fun testOnlyUrl() {
        val input = "https://example.com"
        val segments = parseUrlSegments(input)
        assertEquals(1, segments.size)
        assertTrue(segments[0].isUrl)
        assertEquals("https://example.com", segments[0].text)
        assertEquals("https://example.com", segments[0].url)
    }

    @Test
    fun testUrlEmbeddedInsideNormalText() {
        val input = "Check this https://example.com now"
        val segments = parseUrlSegments(input)
        assertEquals(3, segments.size)

        assertEquals("Check this ", segments[0].text)
        assertFalse(segments[0].isUrl)

        assertEquals("https://example.com", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals("https://example.com", segments[1].url)

        assertEquals(" now", segments[2].text)
        assertFalse(segments[2].isUrl)

        // Concatenated segments must match original string character-for-character
        assertEquals(input, segments.joinToString("") { it.text })
    }

    @Test
    fun testBengaliTextWithUrl() {
        val input = "এই ওয়েবসাইটটা দেখো https://example.com এখন"
        val segments = parseUrlSegments(input)
        assertEquals(3, segments.size)

        assertEquals("এই ওয়েবসাইটটা দেখো ", segments[0].text)
        assertFalse(segments[0].isUrl)

        assertEquals("https://example.com", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals("https://example.com", segments[1].url)

        assertEquals(" এখন", segments[2].text)
        assertFalse(segments[2].isUrl)

        assertEquals(input, segments.joinToString("") { it.text })
    }

    @Test
    fun testTwoUrlsInOneMessage() {
        val input = "Visit https://site1.com and https://site2.org for details."
        val segments = parseUrlSegments(input)
        assertEquals(5, segments.size)

        assertEquals("Visit ", segments[0].text)
        assertFalse(segments[0].isUrl)

        assertEquals("https://site1.com", segments[1].text)
        assertTrue(segments[1].isUrl)

        assertEquals(" and ", segments[2].text)
        assertFalse(segments[2].isUrl)

        assertEquals("https://site2.org", segments[3].text)
        assertTrue(segments[3].isUrl)

        assertEquals(" for details.", segments[4].text)
        assertFalse(segments[4].isUrl)

        assertEquals(input, segments.joinToString("") { it.text })
    }

    @Test
    fun testNormalTextWithoutUrls() {
        val input = "Hello, this is a plain message with no links!"
        val segments = parseUrlSegments(input)
        assertEquals(1, segments.size)
        assertFalse(segments[0].isUrl)
        assertEquals(input, segments[0].text)
    }

    @Test
    fun testMultilineTextWithUrl() {
        val input = "First line\nhttps://example.com/page\nThird line"
        val segments = parseUrlSegments(input)
        assertEquals(3, segments.size)

        assertEquals("First line\n", segments[0].text)
        assertFalse(segments[0].isUrl)

        assertEquals("https://example.com/page", segments[1].text)
        assertTrue(segments[1].isUrl)

        assertEquals("\nThird line", segments[2].text)
        assertFalse(segments[2].isUrl)

        assertEquals(input, segments.joinToString("") { it.text })
    }

    @Test
    fun testTrailingPunctuationStrippedFromUrl() {
        val inputs = listOf(
            "Check https://example.com." to ("https://example.com" to "."),
            "Is this https://example.com?" to ("https://example.com" to "?"),
            "Wow https://example.com!" to ("https://example.com" to "!"),
            "See https://example.com, and more" to ("https://example.com" to ", and more"),
            "Look https://example.com;" to ("https://example.com" to ";"),
            "Source: https://example.com:" to ("https://example.com" to ":"),
            "Really?! https://example.com?!" to ("https://example.com" to "?!"),
            "(https://example.com)" to ("https://example.com" to ")")
        )

        for ((input, expected) in inputs) {
            val segments = parseUrlSegments(input)
            val urlSegment = segments.first { it.isUrl }
            assertEquals(expected.first, urlSegment.url)
            assertEquals(input, segments.joinToString("") { it.text })
        }
    }

    @Test
    fun testUrlWithValidQueryParamsAndPunctuationPreserved() {
        val input = "https://example.com/path?key=value&id=123,456#section.1"
        val segments = parseUrlSegments(input)
        assertEquals(1, segments.size)
        assertTrue(segments[0].isUrl)
        assertEquals(input, segments[0].url)
    }

    @Test
    fun testCaseInsensitiveScheme() {
        val input = "Check HTTPS://EXAMPLE.COM/Path and HTTP://google.com"
        val segments = parseUrlSegments(input)
        assertEquals(4, segments.size)
        assertEquals("Check ", segments[0].text)
        assertEquals("HTTPS://EXAMPLE.COM/Path", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals(" and ", segments[2].text)
        assertEquals("HTTP://google.com", segments[3].text)
        assertTrue(segments[3].isUrl)
    }

    @Test
    fun testLongUrl() {
        val input = "Here is a very long url: https://subdomain.example.com/a/very/long/path/with/many/segments/and?query=parameter1&other=parameter2&filter=active#results-page-3 right here"
        val segments = parseUrlSegments(input)
        assertEquals(3, segments.size)
        assertEquals("Here is a very long url: ", segments[0].text)
        assertEquals("https://subdomain.example.com/a/very/long/path/with/many/segments/and?query=parameter1&other=parameter2&filter=active#results-page-3", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals(" right here", segments[2].text)
    }

    @Test
    fun testYouTubeUrl() {
        val input = "Check out this video: https://youtube.com/watch?v=dQw4w9WgXcQ"
        val segments = parseUrlSegments(input)
        assertEquals(2, segments.size)
        assertEquals("Check out this video: ", segments[0].text)
        assertFalse(segments[0].isUrl)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", segments[1].url)
    }

    @Test
    fun testInstagramUrl() {
        val input = "Follow on Instagram: https://instagram.com/talkly_app"
        val segments = parseUrlSegments(input)
        assertEquals(2, segments.size)
        assertEquals("Follow on Instagram: ", segments[0].text)
        assertFalse(segments[0].isUrl)
        assertEquals("https://instagram.com/talkly_app", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals("https://instagram.com/talkly_app", segments[1].url)
    }

    @Test
    fun testWhatsAppUrl() {
        val input = "Message me on WhatsApp: https://wa.me/1234567890"
        val segments = parseUrlSegments(input)
        assertEquals(2, segments.size)
        assertEquals("Message me on WhatsApp: ", segments[0].text)
        assertFalse(segments[0].isUrl)
        assertEquals("https://wa.me/1234567890", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals("https://wa.me/1234567890", segments[1].url)
    }

    @Test
    fun testNormalWebsiteUrl() {
        val input = "Visit https://example.com for more info"
        val segments = parseUrlSegments(input)
        assertEquals(3, segments.size)
        assertEquals("Visit ", segments[0].text)
        assertFalse(segments[0].isUrl)
        assertEquals("https://example.com", segments[1].text)
        assertTrue(segments[1].isUrl)
        assertEquals(" for more info", segments[2].text)
        assertFalse(segments[2].isUrl)
    }

    @Test
    fun testMultipleAppAndWebUrlsMultiline() {
        val input = "Links:\nhttps://youtube.com/watch?v=123\nhttps://instagram.com/profile\nhttps://wa.me/9876\nhttps://example.com"
        val segments = parseUrlSegments(input)
        val urlSegments = segments.filter { it.isUrl }
        assertEquals(4, urlSegments.size)
        assertEquals("https://youtube.com/watch?v=123", urlSegments[0].url)
        assertEquals("https://instagram.com/profile", urlSegments[1].url)
        assertEquals("https://wa.me/9876", urlSegments[2].url)
        assertEquals("https://example.com", urlSegments[3].url)
        assertEquals(input, segments.joinToString("") { it.text })
    }
}
