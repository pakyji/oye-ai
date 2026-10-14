package com.oyeai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyParserTest {

    @Test
    fun parsesJsonWithLanguageAndActions() {
        val r = ReplyParser.parse(
            """{"lang":"it","reply":"Lo apro.","actions":[{"type":"open_app","app":"YouTube"}]}"""
        )
        assertEquals("it", r.lang)
        assertEquals("Lo apro.", r.text)
        assertEquals(listOf<Action>(Action.OpenApp("YouTube")), r.actions)
    }

    @Test
    fun stripsCodeFences() {
        val r = ReplyParser.parse("```json\n{\"reply\":\"Hi\",\"actions\":[]}\n```")
        assertEquals("Hi", r.text)
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun plainTextBecomesTheReply() {
        val r = ReplyParser.parse("Hello there")
        assertEquals("Hello there", r.text)
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun malformedJsonFallsBackToRawTextWithNoActions() {
        val raw = "Sure { not json"
        val r = ReplyParser.parse(raw)
        assertEquals(raw, r.text)
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun unknownLanguageIsIgnored() {
        val r = ReplyParser.parse("""{"lang":"fr","reply":"Bonjour","actions":[]}""")
        assertEquals(null, r.lang)
        assertEquals("Bonjour", r.text)
    }

    @Test
    fun unknownActionIsKeptButMarkedUnknown() {
        val r = ReplyParser.parse("""{"reply":"ok","actions":[{"type":"fly","to":"moon"}]}""")
        assertEquals(listOf<Action>(Action.Unknown("fly")), r.actions)
    }

    @Test
    fun onlyHttpsUrlsAreAccepted() {
        val r = ReplyParser.parse("""{"reply":"ok","actions":[{"type":"open_url","url":"http://x.com"}]}""")
        assertTrue(r.actions.isEmpty())
        val ok = ReplyParser.parse("""{"reply":"ok","actions":[{"type":"open_url","url":"https://x.com"}]}""")
        assertEquals(listOf<Action>(Action.OpenUrl("https://x.com")), ok.actions)
    }

    @Test
    fun whatsappNeedsNameAndMessage() {
        val r = ReplyParser.parse("""{"reply":"ok","actions":[{"type":"send_whatsapp","name":"Ali"}]}""")
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun riskyActionsNeedConfirmation() {
        assertTrue(Action.CallContact("Mario").needsConfirmation)
        assertTrue(Action.SendWhatsApp("Mario", "ciao").needsConfirmation)
        assertTrue(Action.CloseApp("YouTube").needsConfirmation)
        assertFalse(Action.OpenApp("YouTube").needsConfirmation)
    }
}
