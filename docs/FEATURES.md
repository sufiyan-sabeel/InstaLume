# InstaLume Features V1.0.0

Honest states: **Implemented** / **Version dependent** / **Unavailable**.

## Implemented

- Distraction blocks (feed/explore/reels/stories/instants/notes/suggested/ads), nav visibility, swipe guard, landing page, following-feed rewrite, SDR pin, popup suppression.
- Themes & UI persistence (system/light/dark, iOS/Universal glass, opacity, blur, corners, nav size/position, icon size/spacing, reduced motion, compact UI).
- Backup & Restore JSON schema v1 (`settings/theme/navigation/privacy/features`), validate, reject corrupt/oversize, merge with/without overwrite, clear dev settings, credential denylist.
- Diagnostics (patch + feature status, redacted export), update checker (off by default, manual check, PackageInstaller handoff).

## Version dependent (disabled with reason in UI)

- Ghost story-seen / typing / activity; media quality/autoplay/save-destination/viewer gestures; DM theming; follow indicators; chat background; mark-as-seen; Saved Copies.

## Unavailable (no safe fingerprint in V1.0.0)

- Ghost read-status prevention. Never faked; registry marks `UNAVAILABLE`.
