---
name: Chess challenge catalogue validation
description: Durable constraints for authored chess challenge data and regression checks.
---

The challenge catalogue must be validated after list filtering and appended authored entries are applied, not by scanning every intermediate declaration. Every effective level needs a legal, complete line that ends in checkmate, and authored positions should be unique.

**Why:** The data file previously contained extra out-of-range declarations and later replacements, so a source-level scan could pass while the loaded level was still unplayable.

**How to apply:** When changing challenge data, replay the effective levels with the project chess engine and keep count, title, duplicate-position, objective-length, and checkmate checks together.