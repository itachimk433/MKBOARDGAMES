---
name: Snakes & Ladders movement behavior
description: Gameplay and animation rules for Snakes & Ladders pieces
---

Snakes & Ladders pieces remain off-board at position zero until a six is rolled; that six animates entry to square one and grants the normal extra turn. Numbered movement should use Ludo's linear hop cadence and trigger one movement sound per hop.

**Why:** The board uses an off-board start state, and faster or accelerating animation makes the token and movement audio visibly fall out of sync.

**How to apply:** Preserve the six-only launch rule and Ludo timing/sound cadence when changing Snakes & Ladders movement or turn handling.