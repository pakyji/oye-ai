# OYE Social Media Control

Voice and button control for Instagram and Facebook. Scrolling is done by an optional
Android accessibility service that the user turns on in Android settings.

## What it does

- Opens Instagram or Facebook (and their Reels) by voice or with the card on the home screen.
- Starts and stops auto-scroll. Pause, resume and stop work by voice and by button.
- Scrolls forward or backward one item on request ("agla Reel", "pichla Reel").
- Changes speed (slow, normal, fast) and sets a session limit ("10 minute ke baad scrolling rok do").
- Shows an ongoing notification with a Stop button while scrolling.

## What it never does

- Does not read, store or send messages, posts or screen text.
- Does not like, follow, comment, message or type.
- Does not record the screen or touch other apps.
- Does not start scrolling on its own. Enabling the service alone scrolls nothing.

## How scrolling works

1. The request is checked by the parser (`SocialCommands`) and then by the session engine (`SessionEngine`).
2. The accessibility service checks the active window. If it is not Instagram or Facebook, nothing happens.
3. It tries the scrollable view's accessibility action first (`ACTION_SCROLL_FORWARD` or `ACTION_SCROLL_BACKWARD`).
4. If no such view exists, it sends one short swipe gesture.
5. OYE reports "scrolled" only when step 3 or 4 reports success.

## Limits

- Scrolling may not work in every app version or screen. Open the Reels feed yourself first.
- Android does not let a background service open screens. When OYE is not on screen, opening an
  app shows a notification to tap.
- Voice commands are heard only while voice listening is on (Settings). The card and chat work without it.
- While OYE speaks a reply, it is not listening, so "stop" must be said after the reply ends.
- Session limits and the scroll interval are set in Settings. Limits count from the start, including pauses.
- Five failed scroll attempts in a row stop the session with a message.

## Acceptance checklist

| # | Requirement | How it is checked |
|---|---|---|
| 1 | Instagram and Facebook launch when installed | Manual: install both apps, say "Instagram kholo" with OYE on screen, then in the background. Code: `SocialActions.open`. |
| 2 | Supported voice commands are recognised | Automated: `SocialCommandsTest` (10 tests, covers every example command plus Italian). |
| 3 | Accessibility can be enabled and disabled in Android settings | Manual: card button and Settings button open the screen; status changes to On or Off. Code: `SocialControl.accessibilityStatus`. |
| 4 | Scrolling starts only after the user asks | Automated: `SessionEngineTest.doesNothingUntilStarted`, `scrollsAtTheConfiguredIntervalOnlyAfterStart`. |
| 5 | Pause and stop work | Automated: `pauseStopsScrollingAndResumeContinues`, `stopEndsTheSessionImmediately`. Manual on device. |
| 6 | Session limits stop automation | Automated: `sessionLimitStopsAutomation`, `voiceTimeLimitReplacesTheSessionEnd`. |
| 7 | Permission denied and unsupported screens give useful errors | Automated: `repeatedFailuresStopTheSessionWithAReason`. Manual: deny accessibility, open another app, then say "scroll" (see messages `social_need_accessibility`, `social_unsupported`). |
| 8 | OYE reports exactly what it did | Automated: results are only `Done` after the platform reports success (`ScrollResult`). Manual: check the notes on the card. |

Status at the time of writing: items 2, 4, 5, 6 and the logic in 7 and 8 are covered by JVM unit
tests and pass. Items 1, 3 and the on-device parts of 5, 7 and 8 need a phone or emulator with Instagram
and Facebook installed. These have not been run yet, so they are not claimed as passed.
