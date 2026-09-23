# Version Compatibility

Tested IG base: **446.0.0.49.77** (prefix `446.0.0.49.` accepted).

## Pipeline

```
Instagram update → Version detection → Compatibility scan →
Fingerprint validation → Patch registry → Compatible enabled →
Incompatible disabled → Diagnostic report
```

## Rules

- Unknown IG = Unsupported until fingerprinted. No silent patching.
- Developer → Version Compatibility shows `VersionCompatibility.statusFor(ig)`.
- Developer → Patch Diagnostics shows per-patch id/name/target/fingerprint/status/reason (`PatchRegistry.diagnose()`).
- Build-time truth: `build/patch-report.json` (CLI fails fast on first failure).

## Security note

The foundation's signature-trust bypass conflicts with the no-bypass policy. V1.0.0 flags `sig_bypass` as DISABLED-policy in diagnostics; deep links may fall back to home on re-signed builds. Do not re-enable without an explicit documented exception.
