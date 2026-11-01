# ADR-0002: Trust boundaries for stream resolution in the playback core

Status: Accepted (2026-11-02)

## Context
Sub-project 2 ports Meld's extractor-based stream resolution, which needs a WebView-run BotGuard PoToken generator and cipher player configs that Meld fetches from a third-party GitHub repository. Both feed code or data that steers playback and sit on the app's network edge.

## Decision
1. The WebView runs hidden, origin-restricted, with one narrow validated JS bridge, no file access, and is destroyed after use; a static CI check enforces this.
2. Cipher player configs are hosted by Aria, Ed25519-signed and strictly validated, using the existing signed-registry verifier. Remote updates stay disabled until production keys exist; a bundled default is used meanwhile.
3. Pre-release builds are debug APKs kept as private Actions artifacts. No public release until the name/trademark and other release gates clear.

## Rationale
An unconfined WebView running remote script, or an unsigned config in a repo anyone with a token can write to, would let a single compromise change what every user's app executes or fetches. Reusing the registry verifier avoids new crypto.

## Consequences
- Extractor fixes ship through the signed channel, so we need to run key management and a signing step.
- The bundled default can go stale; the extractor must degrade gracefully when it does.
- Public pre-releases are blocked until the foundation's release gates pass.
