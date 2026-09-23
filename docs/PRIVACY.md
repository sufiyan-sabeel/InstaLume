# Privacy — InstaLume V1.0.0

InstaLume does not collect, proxy, store, or transmit Instagram usernames, passwords, session tokens, or cookies. Auth stays in Instagram's own flow.

- No analytics or telemetry. Update check is OFF by default.
- Backups (`instalume-backup.json`) exclude password/token/auth/cookie/session/secret/credential/mqtt keys.
- Diagnostics are redacted: versions + patch/feature states only, never prefs dumps or paths with private data.
- Site loads no third-party trackers. Build from source if preferred.
- Not affiliated with Instagram, Meta, or third parties.
