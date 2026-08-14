---
name: GitHub repository auth
description: Secure GitHub authentication behavior for repository maintenance in this environment
---

Use GitHub personal access tokens for Git operations through the standard basic-auth header, while keeping the token referenced only through the secure environment variable.

**Why:** GitHub rejected bearer-style and unauthenticated attempts, while the standard `x-access-token:<token>` basic-auth form worked for both clone and push.

**How to apply:** Build the authorization header in memory or a shell variable, never print it, never embed the token in a remote URL, and verify the final branch status after pushing.