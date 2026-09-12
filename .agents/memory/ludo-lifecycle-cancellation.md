---
name: Ludo lifecycle cancellation
description: Non-obvious Android animation and dialog lifecycle constraints for stopping Ludo work safely.
---

Ludo gameplay can continue behind an in-app dialog because opening a Dialog does not pause the Activity. Gameplay callbacks therefore need both an Activity lifecycle gate and an explicit dialog-open gate.

**Why:** Android `ValueAnimator.cancel()` still invokes animation-end listeners. Without a generation check, canceling a dice or token animation can commit a move, schedule the next AI turn, or play a win sound after the user has left the game.

**How to apply:** When adding delayed Ludo actions or animations, route delayed work through the active-game guard and invalidate the generation before canceling any animator. Stop active Ludo SoundPool streams when the Activity pauses or a gameplay dialog opens.

Delayed no-move callbacks must also be scoped to the roll they came from, including the player and die value, rather than relying only on the current Activity state.

**Why:** A callback from an earlier roll can fire after the board has rebuilt its legal moves for a later roll; treating it as current can skip a valid six launch.

**How to apply:** Capture a roll generation when a die result commits and require that generation, player, and die value to still match before advancing the turn.