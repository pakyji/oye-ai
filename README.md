# OYE

> Developed by Professor

A personal AI assistant for Android. It listens or reads what you type, answers in
**Italian, English or Urdu**, and can act on your phone: open apps, call people,
prepare WhatsApp messages, and open links. It can keep running in the background.

## Install

1. Open the **Releases** page of this repository on your phone.
2. Download `app-release.apk` from the latest release.
3. Allow installing from this source when Android asks, then install.
4. Open OYE, go to **Settings**, paste at least one AI provider key, and choose it.

## AI providers

Add keys for as many providers as you like. OYE uses the selected provider first and
falls back to the others if one fails. Supported: Pollinations, OpenAI, Groq, Google Gemini,
OpenRouter, Mistral, DeepSeek and xAI. Models can be changed per provider in Settings.

## What it can do

| Request | How |
|---|---|
| "Open YouTube" | Opens the app |
| "Call Mario" | Calls a contact (needs Contacts & calls permission), or opens the dialer |
| "Send Ali on WhatsApp: I'm coming" | Opens the WhatsApp chat with the message ready. **You tap Send.** |
| "Close YouTube" | Stops its background work and returns to the home screen |
| "Open https://…" | Opens the link |

Calls, WhatsApp messages and closing apps ask for your confirmation first.

## Limits (be aware)

- Android does not let an app force-close another app you are using.
- OYE cannot tap or type inside other apps yet. WhatsApp sending needs you to tap Send.
- Offline mode (a model that runs on the phone) is not included in this version.
- Building web apps is not included in this version.
- Speech recognition and voices for Urdu depend on the Google app and voice data on your phone.
- Start-on-boot depends on Android's background limits and may be blocked on some phones.

## Build from source

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK
```

Release builds are signed when the signing secrets are present (see below).

## Release signing and GitHub Actions

The workflow in `.github/workflows/android.yml` runs tests, builds the release APK, and
publishes it to a GitHub Release when you push a tag such as `v0.1.0`.

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `OYEAI_KEYSTORE_BASE64` | `base64 -w0 oyeai-release.jks` |
| `OYEAI_KEYSTORE_PASSWORD` | keystore password |
| `OYEAI_KEY_ALIAS` | `oyeai` |
| `OYEAI_KEY_PASSWORD` | key password |

Never commit the keystore or passwords. Keep a backup: a lost key means installed copies
cannot be updated.

## License

MIT. See `LICENSE`.
