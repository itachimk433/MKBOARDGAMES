---
name: Android checkout handling
description: Git-root handling for Android repository maintenance in this workspace
---

A directory that looks like a cloned Android project may not contain its own `.git` metadata; Git can resolve to the workspace parent instead. Always confirm `git rev-parse --show-toplevel` and the remotes before fetch, reset, commit, or push.

**Why:** Resetting from a nested path can act on the parent repository rather than the intended project checkout.

**How to apply:** Use the repository root reported by Git for all version-control operations, and remove duplicate nested copies only after confirming the root contains the intended project files.