# Ethereal chess engine

Hard and Master chess use a compact Ethereal UCI engine compiled by the
GitHub Actions workflow for `arm64-v8a` and `armeabi-v7a`. The generated
native binaries are intentionally not committed to this repository.

Source: https://github.com/AndyGrant/Ethereal
Pinned source revision: `0e47e9b67f345c75eb965d9fb3e2493b6a11d09a`
License: GNU GPL v3.0

The build embeds Ethereal's compact `pknet_224x32x2.net` evaluation data into
each binary, so the APK does not need to carry a separate large neural-network
asset. The engine is started through its UCI interface and is not used for
Easy or Medium, which retain the existing Kotlin AI behavior.

Ethereal source and license: https://github.com/AndyGrant/Ethereal