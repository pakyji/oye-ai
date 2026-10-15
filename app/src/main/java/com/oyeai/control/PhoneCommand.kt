package com.oyeai.control

import com.oyeai.social.SocialCommand

/** Every phone action OYE can take from a voice or chat command. */
sealed class PhoneCommand {
    data class Social(val command: SocialCommand) : PhoneCommand()
    data class OpenApp(val name: String) : PhoneCommand()
    data class OpenSettings(val target: SettingsTarget) : PhoneCommand()
    data class Brightness(val percent: Int) : PhoneCommand()
    data class BrightnessStep(val up: Boolean) : PhoneCommand()
    data class MediaVolume(val percent: Int) : PhoneCommand()
    data class VolumeStep(val up: Boolean) : PhoneCommand()
    object VolumePanel : PhoneCommand()
    data class Media(val action: MediaAction) : PhoneCommand()
    data class Timer(val minutes: Int) : PhoneCommand()
    data class Remind(val minutes: Int, val text: String) : PhoneCommand()
    data class Dial(val target: String) : PhoneCommand()
    data class Message(val target: String, val body: String) : PhoneCommand()
    object OpenDownloads : PhoneCommand()
    object DeviceStatus : PhoneCommand()
    object Back : PhoneCommand()
    object Home : PhoneCommand()
    data class ClickLabel(val label: String) : PhoneCommand()
    data class FindLabel(val label: String) : PhoneCommand()
    /** [up] true scrolls the content upward (towards the previous item). */
    data class ScrollScreen(val up: Boolean) : PhoneCommand()
    object StopAll : PhoneCommand()
}

enum class MediaAction { PlayPause, Next, Previous }

enum class SettingsTarget {
    MAIN, WIFI, BLUETOOTH, HOTSPOT, DISPLAY, SOUND, BATTERY, NOTIFICATIONS,
    ACCESSIBILITY, APP_PERMISSIONS, DEFAULT_APPS,
    LANGUAGE, KEYBOARD, STORAGE, LOCATION
}
