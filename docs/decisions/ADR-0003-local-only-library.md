# ADR-0003: The library is local-only; no YouTube account sync in v1 of the UI shell

Status: Accepted (2026-11-08)

## Context
The ported schema carries `liked`, `inLibrary`, `libraryAddToken` and `libraryRemoveToken` on songs. The tokens exist to sync a signed-in YouTube account's library. The UI shell (sub-project 3) needs a definition of what the Library tab shows and where likes live.

## Decision
- Library Songs are the songs with `inLibrary != null`. A like also sets `inLibrary`; "Add to library" sets it without a like; plays alone stay in history.
- Songs are upserted on first like, add, playlist-add or play. Albums and artists follow the same rule.
- All of this is local. No account login and no sync; the two token columns stay unused and reserved.

## Rationale
No account means no stored credentials, no cookies, and no account-restriction risk from unofficial endpoints. The Library works offline and never depends on parser health.

## Consequences
- Users cannot see their YouTube Music library in Aria until a later sub-project adds login. Adding sync later must reconcile local `inLibrary` data with remote state, and may require a migration of the semantics of `inLibrary`.
- Backup and restore (sub-project 6) is the only way users move their library between devices, which is why it is not cuttable.
