package com.oyeai.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialCommandsTest {

    private fun parse(text: String) = SocialCommands.parse(text)

    @Test
    fun opensInstagramAndFacebook() {
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false), parse("OYE, Instagram kholo."))
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = false), parse("OYE, Facebook kholo."))
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false), parse("apri Instagram"))
    }

    @Test
    fun opensReelsFeeds() {
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = true), parse("OYE, Instagram Reels kholo."))
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = true), parse("OYE, Facebook Reels kholo."))
    }

    @Test
    fun startsAutoScroll() {
        assertEquals(SocialCommand.Start, parse("OYE, auto scroll shuru karo."))
        assertEquals(SocialCommand.Start, parse("shuru karo"))
        assertEquals(SocialCommand.Start, parse("resume"))
    }

    @Test
    fun navigatesReels() {
        assertEquals(SocialCommand.Next, parse("OYE, agla Reel."))
        assertEquals(SocialCommand.Next, parse("agli reel"))
        assertEquals(SocialCommand.Next, parse("next reel"))
        assertEquals(SocialCommand.Previous, parse("OYE, pichla Reel."))
    }

    @Test
    fun stopsScrollingImmediately() {
        assertEquals(SocialCommand.Stop, parse("OYE, scrolling band karo."))
        assertEquals(SocialCommand.Stop, parse("OYE, stop."))
        assertEquals(SocialCommand.Stop, parse("stop"))
        assertEquals(SocialCommand.Stop, parse("ferma"))
    }

    @Test
    fun stopsAfterAMinuteLimit() {
        assertEquals(SocialCommand.StopAfter(10), parse("OYE, 10 minute ke baad scrolling rok do."))
        assertEquals(SocialCommand.StopAfter(5), parse("stop scrolling after 5 minutes"))
    }

    @Test
    fun pausesScrolling() {
        assertEquals(SocialCommand.Pause, parse("scrolling rok do"))
        assertEquals(SocialCommand.Pause, parse("ruko"))
        assertEquals(SocialCommand.Pause, parse("pause"))
    }

    @Test
    fun changesSpeed() {
        assertEquals(SocialCommand.Slower, parse("OYE, scrolling dheemi karo."))
        assertEquals(SocialCommand.Faster, parse("OYE, scrolling tez karo."))
    }

    @Test
    fun recognisesItalianWords() {
        assertEquals(SocialCommand.Pause, parse("scorrimento pausa"))
        assertEquals(SocialCommand.Next, parse("reel avanti"))
        assertEquals(SocialCommand.Next, parse("Reel successivo"))
        assertEquals(SocialCommand.Start, parse("OYE, avvia lo scorrimento."))
        assertEquals(SocialCommand.Stop, parse("OYE, ferma lo scorrimento."))
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = false), parse("apri Facebook"))
    }

    @Test
    fun ignoresOrdinaryRequests() {
        assertNull(parse("What's the weather like?"))
        assertNull(parse("Instagram"))
        assertNull(parse("Continue."))
        assertNull(parse("Fix this"))
        assertNull(parse("start a new project for my shop"))
        assertNull(parse(""))
    }

    @Test
    fun hindiAndUrduCommands() {
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false), parse("इंस्टाग्राम खोलो"))
        assertEquals(SocialCommand.Start, parse("स्क्रॉल शुरू करो"))
        assertEquals(SocialCommand.Stop, parse("स्क्रॉल बंद करो"))
        assertEquals(SocialCommand.Pause, parse("स्क्रॉल रुको"))
        assertEquals(SocialCommand.Slower, parse("स्क्रॉल धीरे करो"))
        assertEquals(SocialCommand.StopAfter(10), parse("10 मिनट बाद स्क्रॉलिंग रोक दो"))
        assertEquals(SocialCommand.Next, parse("अगला"))
        assertEquals(SocialCommand.Previous, parse("पिछला"))
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false), parse("انسٹاگرام کھولو"))
        assertEquals(SocialCommand.Start, parse("اسکرول شروع کرو"))
        assertEquals(SocialCommand.Stop, parse("اسکرول بند کرو"))
    }

    @Test
    fun wordsFromOtherCommandsAreNotSocial() {
        assertNull(parse("pichla task continue karo"))
        assertNull(parse("musica avanti"))
    }

    @Test
    fun understandsMisheardFacebookNames() {
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = false), parse("OYE, face book kholo."))
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = false), parse("OYE, fesbuk kholo."))
        assertEquals(SocialCommand.Open(SocialApp.INSTAGRAM, reels = false), parse("OYE, insta gram kholo."))
    }

    @Test
    fun opensFacebookReels() {
        assertEquals(SocialCommand.Open(SocialApp.FACEBOOK, reels = true), parse("OYE, Facebook reels kholo."))
    }

    @Test
    fun normalChatIsNotASocialCommand() {
        assertNull(parse("OYE, kese ho?"))
        assertNull(parse("OYE, Facebook ke baare mein batao"))
    }
}
