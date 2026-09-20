# Stockfish chess engine

Master chess uses a compact Stockfish UCI engine compiled by the GitHub
Actions workflow for `arm64-v8a` and `armeabi-v7a`. The generated native
binaries are intentionally not committed to this repository.

Source: https://github.com/official-stockfish/Stockfish
Pinned source revision: `cb4a62311985f685ba6f5457851527a3289073e6`
Network: `nn-37f18f62d772.nnue`
License: GNU GPL v3.0

The workflow builds Stockfish with its small NNUE architecture, disables
embedded network data, and ships one shared 3.5 MB network asset for both
ABIs. The engine is started through its UCI interface and is only used for
Master; Hard continues to use the compact classic Ethereal engine.

Stockfish source and license: https://github.com/official-stockfish/Stockfish