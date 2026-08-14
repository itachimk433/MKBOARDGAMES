---
name: Fox & Geese rendering
description: UI initialization and theme behavior for the custom Fox & Geese board
---

Initialize the selected game engine and its starting state before showing the mode dialog. Custom board renderers must read the active shared theme rather than using fixed board colors.

**Why:** The generic BoardView state is an empty 8x8 board, so displaying the dialog before game initialization makes Fox & Geese appear over a chess board. Its custom cross-board renderer otherwise bypasses per-game themes.

**How to apply:** In new game activities, assign the engine, initial state, and active theme before the first dialog. In Fox & Geese drawing code, derive the surface, lines, points, and accents from `SettingsManager.currentTheme(context)`.