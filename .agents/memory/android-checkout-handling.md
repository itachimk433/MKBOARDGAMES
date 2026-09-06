---
name: Android checkout handling
description: Git-root handling for Android repository maintenance in this workspace
---

A directory that looks like a cloned Android project may not contain its own `.git` metadata; Git can resolve to the workspace parent instead. Always confirm `git rev-parse --show-toplevel` and the remotes before fetch, reset, commit, or push.

**Why:** Resetting from a nested path can act on the parent repository rather than the intended project checkout.

**How to apply:** Use the repository root reported by Git for all version-control operations, and remove duplicate nested copies only after confirming the root contains the intended project files.

If GitHub Actions reports a workflow path at `.github/workflows/...`, verify that the pushed commit still has the Android project and workflow at repository root. A commit based on the outer workspace can silently move them under `Still-NexBoard/`, which disables the expected workflow.

**Why:** The Android build workflow only reran after the branch was restored to the commit layout with root-level `.github` and `app` directories.

**How to apply:** Before pushing build fixes, compare `git ls-tree --full-tree HEAD` with the workflow run’s checkout paths and verify the remote branch points to the intended project layout.