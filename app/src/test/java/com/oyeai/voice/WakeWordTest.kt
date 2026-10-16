package com.oyeai.voice

import com.oyeai.voice.WakeWord.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordTest {

    @Test
    fun commandAfterWakeWordIsExtracted() {
        assertEquals(Match.Command("apri YouTube"), WakeWord.parse("Oye, apri YouTube"))
        assertEquals(Match.Command("chiama Mario"), WakeWord.parse("  Oi, chiama Mario"))
    }

    @Test
    fun wakeWordAloneAsksForTheCommand() {
        assertEquals(Match.WakeOnly, WakeWord.parse("oye"))
        assertEquals(Match.WakeOnly, WakeWord.parse("Oye!"))
    }

    @Test
    fun commonMishearingsOfOyeAreAccepted() {
        assertEquals(Match.Command("apri YouTube"), WakeWord.parse("Oyee apri YouTube"))
        assertEquals(Match.Command("chiama Mario"), WakeWord.parse("Ooye, chiama Mario"))
        assertEquals(Match.WakeOnly, WakeWord.parse("Hoye"))
    }

    @Test
    fun wordsThatMerelyStartLikeTheWakeWordAreIgnored() {
        assertNull(WakeWord.parse("Oyester sauce"))
    }

    @Test
    fun wakeWordMustComeFirst() {
        assertNull(WakeWord.parse("ciao oye apri YouTube"))
    }

    @Test
    fun emptyOrSilentTextIsIgnored() {
        assertNull(WakeWord.parse(""))
        assertNull(WakeWord.parse("   "))
        assertNull(WakeWord.parse("..."))
    }

    @Test
    fun urduWakeWordIsSupported() {
        assertEquals(Match.Command("کال کرو"), WakeWord.parse("اوئے کال کرو"))
    }
}
