---
name: Android build environment
description: Local Android compilation constraints for this repository
---

The workspace does not provide Java or the Android SDK; Android builds are intentionally performed by the repository's GitHub Actions workflow.

**Why:** The user explicitly asked not to install Android tooling, and the local Gradle wrapper cannot start without Java.

**How to apply:** Make source and asset changes locally, run source-level checks where possible, and rely on the GitHub workflow for APK/AAB compilation.