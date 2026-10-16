# OYE

> Developed by Professor

A personal AI assistant for Android. It listens for "Oye" or reads what you type, answers in
**Italian, English and Urdu**, and can act on your phone: open apps, set reminders and timers,
scroll apps on request, prepare WhatsApp messages, and open links. It can keep listening in the background.

**Current version: 0.2.0 (test build).** Not yet tested on every phone. See the Limits section.

Phone commands are also understood in Hindi and Roman Urdu (Hinglish).

## Install

1. Open the **Releases** page of this repository on your phone. Every change to `main` publishes a new signed build there automatically.
2. Download `app-release.apk` from the latest release (for example `v0.2.0`).
3. Allow installing from this source when Android asks, then install.
   If you already have OYE, installing over it keeps your settings.
4. Open OYE, go to **Settings**, add at least one AI provider key, and choose it.

## AI providers

Add keys for as many providers as you like. OYE uses the selected provider first and
falls back to the others if one fails. Built in: Pollinations, OpenAI, Groq, Google Gemini,
OpenRouter, Mistral, DeepSeek and xAI. Models can be changed per provider in Settings.

**Add your own provider:** in Settings, use "Add your own AI provider". Enter a name, a base URL
that starts with `https://` (for example `https://api.example.com/v1`), a model and your API key.
Any service with an OpenAI-compatible chat endpoint works. There is no limit on the number
of providers. Each one can be deleted again.

## Listening for "Oye"

Turn on **Listen for "Oye"** in Settings. OYE then runs as a notification, and listens for
sentences that start with "Oye". Its status is shown in the notification, for example
"Listening" or the reason it stopped.

For best results, set OYE to **Unrestricted** in your phone's battery settings, and keep the
notification visible. After a restart, Android does not let a microphone service start by itself, so OYE shows a notification: tap it once and listening starts again.

## What it can do

| Request | How |
|---|---|
| "Open YouTube", "Open Castle" | Opens any installed app by its name |
| "Remind me in 15 minutes to drink water" or "15 minute baad yaad dilao paani pee" | Shows a reminder notification. Hours work too. Alarms can be a few minutes late |
| "Scroll down" / "Scroll up" | Scrolls Instagram, Facebook, YouTube or Castle, one step, only when you ask |
| "Next Reel" (Instagram) | Moves to the next item |
| "Stop all" | Stops scrolling, speech and pending actions |
| "Call Mario" | Calls a contact (needs Contacts & calls permission), or opens the dialer |
| "Send Ali on WhatsApp: I'm coming" | Opens the WhatsApp chat with the message ready. **You tap Send.** |
| "Open https://…" | Opens the link |

Calls, WhatsApp messages and closing apps ask for your confirmation first.

**Scrolling and tapping inside apps** needs the OYE accessibility service. You turn it on yourself in
Android Settings. OYE never turns it on by itself. In Instagram and Facebook, OYE never likes,
follows, comments or sends messages, and it never reads or stores what is on the screen.

## GitHub from the app (test)

In **Settings → GitHub**, enter a fine-grained token that can only write to one repository
(Contents: read and write), the repo (`owner/name`) and the branch. Then choose a file path,
content and a commit message, and tap **Push file**. OYE shows a confirmation first, then makes one commit.

- The token is saved only on your phone, encrypted. Never paste it in chat.
- Voice commands never push code.

## Limits (be aware)

- Android does not let an app force-close another app you are using.
- OYE cannot tap or type inside other apps yet. WhatsApp sending needs you to tap Send.
- "Listen for Oye" uses Android's speech recognizer, not a dedicated always-on wake-word engine.
  It may pause between sentences, and some phones stop background microphones when the screen is off.
- Reminders only accept "in N minutes/hours". Dates like "tomorrow at 9" are not supported yet.
- Scrolling works one step at a time in YouTube and Castle. The automatic scroll session is only tested in Instagram and Facebook.
- Offline mode (a model that runs on the phone) is not included in this version.
- Building web apps and the coding agent are not included in this version.
- Speech recognition and voices for Urdu and Hindi depend on the Google app and voice data on your phone.
- Start-on-boot depends on Android's background limits and may be blocked on some phones.

## Build from source

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK
```

Release builds are signed when the signing secrets are present (see below).

## Release signing and GitHub Actions

The workflow in `.github/workflows/android.yml` runs tests, builds the release APK, and
publishes it to a GitHub Release when you push a tag such as `v0.2.0`.

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `OYEAI_KEYSTORE_BASE64` | `base64 -w0 oyeai-release.jks` |
| `OYEAI_KEYSTORE_PASSWORD` | keystore password |
| `OYEAI_KEY_ALIAS` | `oyeai` |
| `OYEAI_KEY_PASSWORD` | key password |

Never commit the keystore or passwords. Keep a backup: a lost key means installed copies
cannot be updated.

## Changelog

- **0.2.0 (test):** background "Oye" listening with status, restart fixes, custom AI providers,
  YouTube and Castle scrolling, reminders, GitHub file push from Settings, Hindi commands.
- **0.1.0:** first build with phone control, social media control and multilingual commands.

## License

MIT. See `LICENSE`.
