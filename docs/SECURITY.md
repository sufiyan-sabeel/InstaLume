# Security — InstaLume V1.0.0

Unofficial patch project for the Instagram Android app. Modified APKs install outside Play Store; use at your own risk and only from official GitHub Releases or self-builds.

## Never

Collect/transmit/store passwords, log tokens/cookies, proxy traffic, harvest credentials, bypass account auth, bypass security checks, or disable Android protections.

## Notes

- Debug bridge (`--debug`) is dev-only and must never ship in release (exported receiver).
- In-app updater uses GitHub HTTPS + PackageInstaller; signing via apksigner; verify SHA256 in release notes.
- Signature-trust bypass from the foundation is flagged DISABLED-policy (see `docs/VERSION_COMPATIBILITY.md`).
- Report suspected issues privately via GitHub issues requesting a private channel; do not post sensitive details publicly.
