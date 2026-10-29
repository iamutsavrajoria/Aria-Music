# ADR-0001: Build Aria as a fresh shell instead of forking Echo Music or Meld

- Status: Accepted
- Date: 2026-10-29
- Deciders: Utsav

## Context

Aria combines two GPL-3.0 Android music apps, Meld (FrancescoGrazioso/Meld) and Echo Music (EchoMusicApp/Echo-Music). Both descend from Metrolist but their git histories are unrelated, so a merge is not possible. Echo has the larger feature set and a modular layout; Meld has the deeper Spotify integration and a newer dependency stack. The app targets a public release.

Options considered:
1. Echo as base, with Meld's Spotify work ported in. Least UI porting, fastest to something shippable.
2. Meld as base, with Echo's UI and modules ported in. Most porting work, because Meld is one large module.
3. Fresh shell that ports selected modules from both.

The recommendation at the time was option 1. Utsav chose option 3.

## Decision

Start a new repository (Aria) with its own module layout, Room schema (v1), design system and build setup. Port and adapt code from Meld and Echo module by module, each with a provenance record and an upstream license audit.

## Rationale

- A public release needs a codebase whose provenance, privacy behavior and dependencies are known. Neither source app provides that: Echo's license headers cover 28 of 836 Kotlin files, its privacy policy describes Firebase analytics, and both depend on other people's servers and gists.
- Echo's module count overstates its modularity: the 5.6k-line `MusicService` and the download service live in `:app`. Forking would inherit that coupling.
- Starting fresh lets Aria set the module boundary rule (`:playback` depends only on `:domain` interfaces) from day one.

## Consequences

- Slower and costlier than a fork: every ported module needs an audit, a ledger row and integration work, and decomposing `MusicService` becomes the main risk of sub-project 2.
- No upstream sync. Fixes in Meld or Echo must be ported by hand, and the provenance ledger records the commit each port came from.
- The foundation has to prove its boundaries early, which is why a walking skeleton is part of its definition of done.
- Reversing this later (rebasing onto a fork of Echo) would mean redoing the module and schema work.

See `.internal/specs/2026-10-29-aria-foundation-design.md`.
