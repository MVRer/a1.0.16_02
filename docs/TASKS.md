# Task board

Status values: queued, running, review, fix, merged, blocked. Only the orchestrator merges. At most 5 workers run at once.

| ID | Task | Workstream | Branch | Milestone | Status | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| P0-1 | Scaffold: template, packages, core contracts, config, debug commands, game tests, dev world | core | feat/core-scaffold | v0.1-skeleton | merged | 2 review rounds (TraceService silence and view check). TraceService refuses edits that leave a falling block unsupported. P1-8 needs an opt-in for the gravel ceiling |
| P1-1 | Director: decks, gates, tension, forced quiet, pity, fakes, pacing limits, stages by time, timewarp sim | director | feat/director-core | (all) | queued, wave 1 | WorldProfile roll lives in core (D-003, D-008) |
| P1-2 | The figure: model, white eyes, fog-edge spawn out of view, all sighting variants, stare then leave, despawn | entity | feat/entity-figure | v0.2-figure | queued, wave 1 | Starts from the HimEntity prototype (D-001) |
| P1-3 | Old scars (worldgen) and the live new-scar placer | world | feat/world-scars | v0.3-traces | queued, wave 1 | Records sites in SiteRegistry for lore |
| P1-4 | Live diggers and the "Under you" network | dig | feat/dig-diggers | v0.3-traces | queued, wave 1 | Records TUNNEL_END and UNDER_BASE sites |
| P1-5 | Dread layer: fog, silence, music off, sound director, mobs acting wrong (client and server) | atmosphere | feat/atmosphere-dread | v0.4-dread | queued, wave 1 | Also builds the real MobTamper (D-017) |
| P1-6 | Fragments: all 30 as data, placement by stage and profile, sites | lore | feat/lore-fragments | v0.5-fragments | queued, wave 2 | |
| P1-7 | Telling: sign and book watcher, blank signs, "Stop.", place not found, list updates, unbreakable F30 | lore | feat/lore-telling | v0.6-telling | queued, after P1-6 | |
| P1-8 | Accidents: planner, every trap, death marker (uses atmosphere's MobTamper) | accident | feat/accident-traps | v0.7-accidents | queued, wave 2 | |
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
