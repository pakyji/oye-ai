package com.oyeai.control

import com.oyeai.social.SocialApp
import com.oyeai.social.SocialCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCommandParserTest {

    private fun parse(text: String) = PhoneCommandParser.parse(text)

    // ---- English ----------------------------------------------------------------------

    @Test
    fun opensAppsAndSettings() {
        assertEquals(PhoneCommand.OpenApp("youtube"), parse("OYE, YouTube kholo."))
        assertEquals(PhoneCommand.OpenApp("google maps"), parse("open Google Maps"))
        assertEquals(PhoneCommand.OpenApp("google"), parse("Google kholo"))
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.WIFI), parse("OYE, Wi-Fi settings par jao."))
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.MAIN), parse("settings kholo"))
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.NOTIFICATIONS), parse("notification settings kholo"))
    }

    @Test
    fun setsBrightnessAndVolume() {
        assertEquals(PhoneCommand.Brightness(40), parse("brightness 40 percent"))
        assertEquals(PhoneCommand.BrightnessStep(false), parse("brightness kam karo"))
        assertEquals(PhoneCommand.MediaVolume(20), parse("volume 20 percent"))
        assertEquals(PhoneCommand.VolumeStep(true), parse("volume badhao"))
        assertEquals(PhoneCommand.VolumeStep(false), parse("awaaz kam karo"))
    }

    @Test
    fun setsTimers() {
        assertEquals(PhoneCommand.Timer(15), parse("OYE, timer 15 minute ka lagao."))
        assertEquals(PhoneCommand.Timer(10), parse("10 minute ka timer lagao"))
    }

    @Test
    fun dialsAndWritesMessages() {
        assertEquals(PhoneCommand.Dial("ali"), parse("OYE, Ali ko call karne ke liye dialer kholo."))
        assertEquals(PhoneCommand.Dial("marco"), parse("call Marco"))
        assertEquals(
            PhoneCommand.Message("ali", "main 10 minute mein aa raha hoon."),
            parse("Ali ko message likho: main 10 minute mein aa raha hoon.")
        )
    }

    @Test
    fun messageBodyIsNotReadAsACommand() {
        // The text after the colon is the message, so \"stop everything\" in it does not stop anything.
        assertEquals(
            PhoneCommand.Message("ali", "stop everything"),
            parse("message Ali: stop everything")
        )
    }

    @Test
    fun navigatesAndStatus() {
        assertEquals(PhoneCommand.Back, parse("go back"))
        assertEquals(PhoneCommand.Home, parse("go home"))
        assertEquals(PhoneCommand.DeviceStatus, parse("what is my battery"))
        assertEquals(PhoneCommand.OpenDownloads, parse("show my downloads"))
        assertEquals(PhoneCommand.ClickLabel("login"), parse("click the Login button"))
        assertEquals(PhoneCommand.FindLabel("latest project"), parse("find latest project"))
        assertEquals(PhoneCommand.ScrollScreen(false), parse("scroll down"))
        assertEquals(PhoneCommand.ScrollScreen(true), parse("scroll up"))
        assertEquals(PhoneCommand.ScrollScreen(false), parse("scroll karo"))
        assertEquals(PhoneCommand.ScrollScreen(false), parse("OYE, scroll karo."))
        assertEquals(PhoneCommand.ScrollScreen(true), parse("scroll upar karo"))
        assertEquals(PhoneCommand.Remind(15, "paani pee"), parse("15 minute baad yaad dilao paani pee"))
        assertEquals(PhoneCommand.Remind(120, "call Mom"), parse("remind me in 2 hours to call Mom"))
    }

    @Test
    fun mediaKeys() {
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("pause the music"))
        assertEquals(PhoneCommand.Media(MediaAction.Next), parse("next song music"))
    }

    @Test
    fun stopAllPhrases() {
        assertEquals(PhoneCommand.StopAll, parse("OYE, stop everything."))
        assertEquals(PhoneCommand.StopAll, parse("cancel everything"))
        assertEquals(PhoneCommand.StopAll, parse("stop scrolling"))
        assertEquals(PhoneCommand.StopAll, parse("stop automation"))
    }

    @Test
    fun cancelTimerIsNotStopAll() {
        assertNull(parse("cancel the timer"))
    }

    // ---- Italian ----------------------------------------------------------------------

    @Test
    fun italian() {
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.WIFI), parse("apri le impostazioni del Wi-Fi"))
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.WIFI), parse("mostra le impostazioni del Wi-Fi"))
        assertEquals(PhoneCommand.MediaVolume(20), parse("imposta il volume al 20 per cento"))
        assertEquals(PhoneCommand.Timer(10), parse("imposta un timer di 10 minuti"))
        assertEquals(PhoneCommand.Dial("marco"), parse("chiama Marco"))
        assertEquals(PhoneCommand.Message("marco", "ci vediamo domani"), parse("scrivi a Marco: ci vediamo domani"))
        assertEquals(PhoneCommand.Back, parse("torna indietro"))
        assertEquals(PhoneCommand.DeviceStatus, parse("quanta batteria c'è"))
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("metti in pausa la musica"))
        assertEquals(PhoneCommand.StopAll, parse("ferma tutto"))
        assertEquals(PhoneCommand.Social(SocialCommand.Start), parse("avvia lo scorrimento"))
        assertEquals(PhoneCommand.StopAll, parse("ferma lo scorrimento"))
    }

    // ---- Urdu (script) ------------------------------------------------------------------

    @Test
    fun urduScript() {
        assertEquals(PhoneCommand.OpenApp("یوٹیوب"), parse("یوٹیوب کھولو"))
        assertEquals(PhoneCommand.OpenSettings(SettingsTarget.WIFI), parse("وائی فائی سیٹنگز کھولو"))
        assertEquals(PhoneCommand.MediaVolume(20), parse("آواز 20 فیصد کرو"))
        assertEquals(PhoneCommand.Timer(15), parse("ٹائمر 15 منٹ لگاؤ"))
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("میری موسیقی چلاؤ"))
        assertEquals(PhoneCommand.StopAll, parse("سب کچھ بند کرو"))
        assertEquals(PhoneCommand.Back, parse("واپس جاؤ"))
        assertEquals(PhoneCommand.DeviceStatus, parse("بیٹری کتنی ہے"))
    }

    // ---- Roman Urdu and Hindi (Devanagari) --------------------------------------------

    @Test
    fun romanUrduAndHinglish() {
        assertEquals(PhoneCommand.StopAll, parse("sab kuch band karo"))
        assertEquals(PhoneCommand.OpenApp("youtube"), parse("YouTube kholo"))
    }

    @Test
    fun hindiDevanagari() {
        assertEquals(PhoneCommand.OpenApp("यूट्यूब"), parse("यूट्यूब खोलो"))
        assertEquals(PhoneCommand.BrightnessStep(false), parse("स्क्रीन की चमक कम करो"))
        assertEquals(PhoneCommand.Timer(15), parse("टाइमर 15 मिनट का लगाओ"))
        assertEquals(PhoneCommand.StopAll, parse("सब कुछ रोको"))
    }

    // ---- Social routing and boundaries ------------------------------------------------

    @Test
    fun socialCommandsAreRoutedAsSocial() {
        assertEquals(PhoneCommand.Social(SocialCommand.Open(SocialApp.FACEBOOK, reels = false)), parse("Facebook kholo"))
        assertEquals(PhoneCommand.Social(SocialCommand.Next), parse("agla Reel"))
        assertEquals(PhoneCommand.Social(SocialCommand.Previous), parse("pichla"))
        assertEquals(PhoneCommand.Social(SocialCommand.Pause), parse("ruko"))
        assertEquals(PhoneCommand.StopAll, parse("stop"))
    }

    @Test
    fun wordsInsideOtherCommandsDoNotTriggerSocial() {
        // \"pichla\" and \"avanti\" mean something else inside longer sentences.
        assertNull(parse("pichla task continue karo"))
        assertEquals(PhoneCommand.Media(MediaAction.Next), parse("musica avanti"))
    }

    @Test
    fun ordinaryQuestionsGoToTheAssistant() {
        assertNull(parse("What is the capital of Italy?"))
        assertNull(parse("Tell me a joke"))
        assertNull(parse("pichla saal kya hua tha?"))
    }

    @Test
    fun everydayWordsDoNotTriggerCommands() {
        // Words like "home", "back", "batter", "April" or "find" inside normal sentences are not commands.
        assertNull(parse("how do I make homemade pizza"))
        assertNull(parse("best backup software for photos"))
        assertNull(parse("how do I make pancake batter"))
        assertNull(parse("what happened in April 2020"))
        assertNull(parse("find me a good pizza recipe for tonight with friends"))
    }

    @Test
    fun stopOrPauseMusicIsAMediaCommand() {
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("stop the music"))
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("pause music"))
        assertEquals(PhoneCommand.Media(MediaAction.PlayPause), parse("pausa musica"))
    }

    @Test
    fun tokenizeKeepsScriptMarks() {
        assertEquals(listOf("स्क्रीन", "की", "चमक"), PhoneCommandParser.tokenize("स्क्रीन की चमक!"))
        assertEquals(listOf("wi", "fi"), PhoneCommandParser.tokenize("Wi-Fi"))
    }
}
