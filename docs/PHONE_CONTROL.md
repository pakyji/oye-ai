# OYE Phone Control (phase 1)

Voice and chat commands that open apps and settings, set brightness and volume, start
timers, prepare calls and messages, check battery and network, navigate, and stop everything.

This is a subset of the full brief. Anything not listed under "Implemented" is not done.

## Implemented

| Area | Commands (examples) | How it works | Verified |
|---|---|---|---|
| Universal router | Voice (ListenService) and chat (ChatViewModel) both call `PhoneCommandParser`, then `PhoneCommandRunner`. Social commands run through the same path. | Pure parser, unit tested. | Parser: 17 phone tests + 12 social tests. Routing on device: not tested. |
| Open app | "YouTube kholo", "open Google Maps" | Matches an installed launcher app by name. Not found: says so. | Parser only. |
| Settings shortcuts | "Wi-Fi settings par jao", "apri le impostazioni del Wi-Fi", "notification settings" | Opens the matching Settings screen by intent. | Parser only. |
| Brightness | "brightness 40 percent", "kam karo" | Needs WRITE_SETTINGS, which the user grants in Settings. Reads back the stored value before saying it changed. | Not tested on device. |
| Media volume | "volume 20 percent", "awaaz badhao" | Sets the music stream and reads back the index. | Not tested on device. |
| Media keys | "pause the music", "next song" | Sends play/pause, next or previous. | Sent only. OYE cannot see the result. |
| Timer | "timer 15 minute ka lagao" | `AlarmClock.ACTION_SET_TIMER` with SKIP_UI. | Handoff only. Clock app must support it. |
| Dialer | "Ali ko call karo" | `ACTION_DIAL` fills in the number. The user presses call. | Handoff only. |
| SMS compose | "scrivi a Marco: ci vediamo" | `smsto:` with body. The user reads it and presses Send. OYE never sends. | Handoff only. |
| Downloads | "show my downloads" | Opens the Downloads screen. | Handoff only. |
| Device status | "what is my battery" | Battery level, charging state, network type (read from the system). | Parser only. |
| Back and home | "go back", "torna indietro" | Global accessibility action. Needs the service on. | Not tested on device. |
| Click and find | "click the Login button", "find latest project" | Matches one label the user named. Works only in Instagram and Facebook (see limits). | Not tested on device. |
| Scroll | "scroll down", "neeche scroll karo" | Same checked path as the social scroll. Instagram and Facebook only. | Not tested on device. |
| Stop All | "stop everything", "sab kuch band karo" | Stops auto-scroll, speech, and pending notification actions. Reports each one. Says what it cannot change. | Parser only. |

Languages: English, Italian, Urdu (script and Roman), Hindi (Devanagari and Roman). Reply strings
exist in English, Italian and Urdu. Hindi replies fall back to English.

## Safety rules applied

- **Honest results.** Intents only hand off to another app. The reply says "opened", never "done".
  Results OYE can check (brightness, volume, scroll, click) are read back first.
- **Confirmation before sending.** SMS is compose-only. Click refuses labels such as Send, Buy,
  Pay, Share, Delete, Like, Follow, Comment, Confirm, Call, Log out and Reset, in every supported
  language, and tells the user to tap it themselves.
- **Screen text.** Only the label the user named is matched. Nothing is read aloud, stored or logged.
- **No secret actions.** No calls are placed without the user pressing call. No purchases, sharing,
  recording or data collection.
- **No bypass.** No locks, biometrics or protected dialogs are touched.
- **Accessibility.** Never enabled by OYE. The user turns it on in Android settings. The
  accessibility settings screen can be opened by voice, but it is not turned on.
- **Background.** When OYE is not on screen, Android blocks it from opening screens. OYE then posts a
  notification the user taps. Reply text says so.

## Limits (read these before testing)

- **Screen actions are limited to Instagram and Facebook.** The accessibility service config
  (`res/xml/oye_accessibility_service.xml`) names only those two packages. Click, find and scroll
  return "work only in Instagram and Facebook" in other apps. Widening this would give the
  service access to every app's windows. That is a privacy decision for the user, not made here.
  Back and home are global actions. Whether Android delivers them while the service is filtered
  to two packages is untested.
- **Stop All cannot undo** apps already opened or timers already set. The reply says so.
- **Timers, dialer, SMS and downloads** depend on the receiving app honouring the intent. OYE
  cannot confirm they started.
- **Media keys** are sent. OYE cannot confirm the player responded.
- **Brightness** needs WRITE_SETTINGS granted by the user in Android settings. OYE opens that
  screen; it cannot grant it.
- **Contacts** need READ_CONTACTS for name lookups. A typed or spoken number needs no permission.

## Not in this pass

Routines, notification listener, file management, camera and screen capture, clipboard, SAF,
the accessibility onboarding screen, the full Professor dashboard (orb, waveform, history), the
coding agent, and a home-screen control card for phone commands. None of these is done.

## Files

- `control/PhoneCommand.kt`: sealed command model and enums.
- `control/PhoneCommandParser.kt`: multilingual parser (pure, tested).
- `control/PhoneCommandRunner.kt`: runs commands and returns the reply.
- `control/Launcher.kt`: opens screens directly, or posts a notification when OYE is in the background.
- `voice/ActionNotifier.kt`, `voice/ActionActivity.kt`: launch notifications.
- `voice/Speaker.kt`: speaker registry used by Stop All.
- `social/OyeAccessibilityService.kt`: global actions, label find and click, scroll.
- Tests: `app/src/test/java/com/oyeai/control/PhoneCommandParserTest.kt`,
  `app/src/test/java/com/oyeai/social/SocialCommandsTest.kt`.

## Manual checks still to do on a phone

1. Each command in the table, in English, Italian, Urdu and Hindi, with OYE in front and in background.
2. Brightness and volume change, and the reply matches the setting.
3. Timer, dialer, SMS and downloads open the right screen. SMS does not send.
4. Stop All while scrolling, while speaking, and with a pending notification.
5. Click refuses Send, Like and Buy in Instagram.
6. Accessibility off: screen commands say so and nothing is tapped.
