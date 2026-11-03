# ADR-0002: Trust boundaries for stream resolution in the playback core

Status: Accepted (2026-11-02)

## Context
Sub-project 2 ports Meld's extractor-based stream resolution, which needs a WebView-run BotGuard PoToken generator and cipher player configs that Meld fetches from a third-party GitHub repository. Both feed code or data that steers playback and sit on the app's network edge.

## Decision
1. The WebView runs hidden, origin-restricted, with one narrow validated JS bridge, no file access, and is destroyed after use; a static CI check enforces this.
2. Cipher player configs are hosted by Aria, Ed25519-signed and strictly validated, using the existing signed-registry verifier. Remote updates stay disabled until production keys exist. Aria bundles no config: the Task 0 spike showed InnerTubeX v0.5.2 only reads configs fetched from `MetrolistGroup/faraday` hosts, so the extractor runs in the library's disabled mode (built-in solver, no network call) and the signed-config path is staged for a later release.
3. Pre-release builds are debug APKs from a manual workflow, kept 1 day. The repo is public, so these artifacts are downloadable by anyone signed in to GitHub; treat them as public and share nothing before the release gates clear. No GitHub release until the name/trademark and other gates clear.

## Rationale
An unconfined WebView running remote script, or an unsigned config in a repo anyone with a token can write to, would let a single compromise change what every user's app executes or fetches. Reusing the registry verifier avoids new crypto.

## Consequences
- Extractor fixes ship through the signed channel, so we need to run key management and a signing step.
- Without a config the extractor relies on its built-in solver and can break when YouTube changes; the staged signed channel is the planned fix once InnerTubeX allows injection.
- Public pre-releases are blocked until the foundation's release gates pass.
