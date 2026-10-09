# Task board

Status values: queued, running, review, fix, merged, blocked. Only the orchestrator merges. At most 5 workers run at once.

| ID | Task | Workstream | Branch | Milestone | Status | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| P0-1 | Scaffold: template, packages, core contracts, config, debug commands, game tests, dev world | core | feat/core-scaffold | v0.1-skeleton | merged | 2 review rounds (TraceService silence and view check). TraceService refuses edits that leave a falling block unsupported. P1-8 needs an opt-in for the gravel ceiling |
| P1-1 | Director: decks, gates, tension, forced quiet, pity, fakes, pacing limits, stages by time, timewarp sim | director | feat/director-core | (all) | merged | Follow-ups for P2-2: signature cards need a stage minimum; deck reshuffles early when the rest are gated; quiet check only after a director fire; no live-wiring test. Contract asks: `Director.timewarp` returns summary lines, `GameClock.dayTicks` |
| P1-2 | The figure: model, white eyes, fog-edge spawn out of view, all sighting variants, stare then leave, despawn | entity | feat/entity-figure | v0.2-figure | fix (round 3) | Rule: never despawn while in view | Starts from the HimEntity prototype (D-001) |
| P1-3 | Old scars (worldgen) and the live new-scar placer | world | feat/world-scars | v0.3-traces | running | Records sites in SiteRegistry for lore |
| P1-4 | Live diggers and the "Under you" network | dig | feat/dig-diggers | v0.3-traces | passed, fixing | Review PASS, fixing 5 notes (bed-shaft clearance, base torches, 3 lag sources) | Records TUNNEL_END and UNDER_BASE sites |
| P1-5 | Dread layer: fog, silence, music off, sound director, mobs acting wrong (client and server) | atmosphere | feat/atmosphere-dread | v0.4-dread | ready | Review PASS, notes fixed, rebased. Waits for v0.3 (D-021) | Also builds the real MobTamper (D-017) |
| P1-6 | Fragments: all 30 as data, placement by stage and profile, sites | lore | feat/lore-fragments | v0.5-fragments | running | Contract gap to expect: a protected "untouched grove" so new scars avoid it |
| P1-7 | Telling: sign and book watcher, blank signs, "Stop.", place not found, list updates, unbreakable F30 | lore | feat/lore-telling | v0.6-telling | queued, after P1-6 | |
| P1-8 | Accidents: planner, every trap, death marker (uses atmosphere's MobTamper) | accident | feat/accident-traps | v0.7-accidents | running | Gravel ceiling waits on a core opt-in. Rebases onto main once atmosphere's MobTamper lands |
| P2-1 | Endings A, B, C, D (full D chain) | ending | feat/ending-endings | v0.8-endings | queued, phase 2 | |
| P2-2 | Integration: wire all cards, close contract gaps, guardrail pass | (multi) | feat/integration-pass | v1.0 | queued, phase 2 | |
| P2-3 | Playtest tooling: dev overlay and a 20 h scripted timewarp log | debug | feat/debug-playtest | v1.0 | queued, phase 2 | |

## Milestones
| Tag | Closes when | Status |
| --- | --- | --- |
| v0.1-skeleton | P0-1 merged | tagged (no timewarp sim yet: no director) |
| v0.2-figure | P1-2 merged | |
| v0.3-traces | P1-3 and P1-4 merged | |
| v0.4-dread | P1-5 merged | |
| v0.5-fragments | P1-6 merged | |
| v0.6-telling | P1-7 merged | |
| v0.7-accidents | P1-8 merged | |
| v0.8-endings | P2-1 merged | |
| v1.0 | P2-2 and P2-3 merged, 20 h simulation passes | |

## Core contract batch (after entity and atmosphere merge)
- `Director.timewarp` returns summary lines; `/a1016 timewarp` prints them (director)
- `GameClock.dayTicks` (director)
- A shared fog-end helper in core, used by entity and atmosphere (entity)
- Document the flags `ending:last_sighting` and `entity:last_sighting_seen` (entity)
- `MobTamper`/`installMobTamper` Javadoc names atmosphere as the owner, and `MobTamper.isTampered(Mob)` (atmosphere)
- A TraceService opt-in that lets a falling block drop, for the gravel ceiling (accident)
- A protected "untouched grove" area that new scars avoid (lore, expected)
- `TraceService.restoreStack(level, ledgerEntry, toPos)` to move ledgered stacks into the network chest later (dig)
- `SiteRegistry.update(site, pos, size)` so the growing tunnel's TUNNEL_END stays current (dig)
