# Oye AI roadmap (requested features)

Each phase is one build, one APK, and one test checklist. Safety rules from
`docs/PHONE_CONTROL.md` and `docs/SOCIAL_CONTROL.md` apply to every phase.

## Safety rules that stay in force

- Never like, follow, comment, or send messages automatically.
- Scroll only when the user asks. Never read or store screen or message text.
- Confirm before sending, deleting, or pushing code.
- Never enable Accessibility by code. Never bypass locks or protected dialogs.
- GitHub tokens are typed only in the app's Settings. They are never pasted in chat,
  never logged, and stored encrypted on the device. The user can delete them at any time.

## Phase 1: Providers and apps

- Custom AI provider: the user enters a name, a base URL, a model, and any API key.
  Any OpenAI-compatible service can be added this way, with no limit on the number.
- Open any installed app by name: "<app name> kholo". Match against installed app labels.
  Ambiguous names must ask the user which app is meant.
- Acceptance: a custom provider answers a question. "Calculator kholo" opens the calculator.

## Phase 2: Streaming media control

- Scroll in YouTube and Netflix on request ("neeche scroll karo"). No like or subscribe actions.
- The "castle" request is treated as a movie and web-series app (Netflix-like). Scroll only.
- Acceptance: scroll works on YouTube and on the user's chosen movie app. Failures are reported.

## Phase 3: Reminders and timers

- "15 minute baad yaad dilao", "kal 9 baje meeting yaad dilao".
- Acceptance: notification fires on time, and survives a phone restart.

## Phase 4: GitHub from the app

- Settings → GitHub: the user enters a fine-grained token, limited to one repository,
  with contents read and write only.
- Each push shows the list of changed files, and the user confirms before anything is pushed.
- Acceptance: a test commit to a test branch works. No token appears in logs or chat.

## Phase 5: Web app builder and coding agent

- Built and tested in the workspace, then committed to GitHub.
- Acceptance: the generated web app builds without errors, and the user checks it on the phone.

## Phase 6: Offline AI mode

- Uses `lost-partial-edits.patch` as a starting point. Needs a native build and about 1 GB of storage.
- Acceptance: a question is answered with airplane mode on.

## Open questions

- Movie app: "Castle" (installed app, label "Castle"). Confirmed by the user from the home screen.
- Which phase comes first? The user has not set the order yet.
