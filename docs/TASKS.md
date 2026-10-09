# Task board

Status values: queued, running, review, fix, merged, blocked. Only the orchestrator merges. At most 5 workers run at once.

| ID | Task | Workstream | Branch | Milestone | Status | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| P0-1 | Scaffold: template, packages, core contracts, config, debug commands, game tests, dev world | core | feat/core-scaffold | v0.1-skeleton | merged | 2 review rounds (TraceService silence and view check). TraceService refuses edits that leave a falling block unsupported. P1-8 needs an opt-in for the gravel ceiling |
| P1-1 | Director: decks, gates, tension, forced quiet, pity, fakes, pacing limits, stages by time, timewarp sim | director | feat/director-core | (all) | merged | Follow-ups for P2-2: signature cards need a stage minimum; deck reshuffles early when the rest are gated; quiet check only after a director fire; no live-wiring test. Contract asks: `Director.timewarp` returns summary lines, `GameClock.dayTicks` |
| P1-2 | The figure: model, white eyes, fog-edge spawn out of view, all sighting variants, stare then leave, despawn | entity | feat/entity-figure | v0.2-figure | merged | 3 review rounds. Rule: never despawn while in view. Contract asks: shared fog-end helper, document ending:last_sighting | Starts from the HimEntity prototype (D-001) |
| P1-2b | Eye style toggle (flat / bright / glow), switchable live | entity | feat/entity-eyes | v0.6 | merged | Default BRIGHT plus eye fog resistance (D-023). Also sighting visibility and approach tuning (D-029) |
| P1-2d | Real fog distance from the client; closer close band (D-035); adaptive outrun speed (D-036) | entity | feat/entity-fogdistance | v0.6 | merged | Playtest fix. A flying chaser can still catch him; D-037 covers that |
| P1-2c | "Goes under" exit (D-030), Nether and End sightings (D-034), close-chase rush past (D-037) | entity | feat/entity-goes-under | v0.6 | queued | Needs P1-9 (figureDig/figureFill) |
| P1-3 | Old scars (worldgen) and the live new-scar placer | world | feat/world-scars | v0.3-traces | merged | 2 review rounds. Later task: signatures (still burning, house elsewhere, cross row). Follow-ups: dead mountain silence and no animals (atmosphere), new pyramid when the list goes into lava (lore-telling) |
| P1-4 | Live diggers and the "Under you" network | dig | feat/dig-diggers | v0.3-traces | merged | 2 review rounds plus a flaky-test fix. Contract asks: restoreStack, SiteRegistry.update | Records TUNNEL_END and UNDER_BASE sites |
| P1-5 | Dread layer: fog, silence, music off, sound director, mobs acting wrong (client and server) | atmosphere | feat/atmosphere-dread | v0.4-dread | merged | Real MobTamper is on main. Follow-up: dead mountain silence and no animals | Also builds the real MobTamper (D-017) |
| P1-6 | Fragments: all 30 as data, placement by stage and profile, sites | lore | feat/lore-fragments | v0.5-fragments | merged | Text verified word for word on all 30. Fixing: idempotent place, chunk loads, F19 vs F07, unbreakable F30 | Contract gap to expect: a protected "untouched grove" so new scars avoid it |
| P1-7 | Telling: sign and book watcher, blank signs, "Stop.", place not found, list updates (F30 unbreakable moved to P1-6) | lore | feat/lore-telling | v0.6-telling | review | Uses editSign, veto and ProtectedAreas. Contract asks: an int telling count in HerobrineState, a ledgered book-text edit |
| P1-8 | Accidents: planner, every trap, death marker (uses atmosphere's MobTamper) | accident | feat/accident-traps | v0.7-accidents | ready | Fixing false-positive kills (dark corner, house fire, no bed), unloaded window, debug mark preview, torch undo | Gravel ceiling and dripstone are written, but spring only after P1-9 lands `removeLettingFall`. Debug `mark` counts toward Ending B |
| P1-3b | World signatures: still burning, your house elsewhere, row of crosses with a fresh one. Plus the rare redstone torch card (D-033) | world | feat/world-signatures | v0.8 | running | Follows D-004 and D-005 |
| P1-9 | Core contract batch (see list below) plus D-022 fog defaults | core (narrow edits in director, atmosphere, world) | feat/core-contracts | (all) | merged | Added: figureDig/figureFill (D-030), restoreBlock (accident), veto hook (lore F30, replaces the lore mixin into TraceEdit) |
| P1-8b | "The zombie has your sword" (D-032), plus void and lava bridge accidents and the enderman on your bridge (D-034) | accident | feat/accident-sword | v0.7 | queued | Needs P1-9 (`equipFromLedger`) and P1-8 merged |
| P1-4b | Dig follow-ups: restoreStack into the network chest, SiteRegistry.update | dig | feat/dig-followups | - | running | |
| P1-5b | Atmosphere follow-ups: FogLimits.installShape, dead mountain silence, no animals there | atmosphere | feat/atmosphere-followups | - | running | |
| P2-1 | Endings A, B, C, D (full D chain) | ending | feat/ending-endings | v0.8-endings | queued, phase 2 | |
| P2-2 | Integration: wire all cards, close contract gaps, guardrail pass | (multi) | feat/integration-pass | v1.0 | queued, phase 2 | |
| P2-3 | Playtest tooling: dev overlay and a 20 h scripted timewarp log | debug | feat/debug-playtest | v1.0 | queued, phase 2 | |

## Milestones
| Tag | Closes when | Status |
| --- | --- | --- |
| v0.1-skeleton | P0-1 merged | tagged (no timewarp sim yet: no director) |
| v0.2-figure | P1-2 merged | tagged (pacing check: director's 20 h fixed-seed game tests green) |
| v0.3-traces | P1-3 and P1-4 merged | tagged (pacing check: director 20 h game tests green) |
| v0.4-dread | P1-5 merged | tagged (pacing check: director 20 h game tests green) |
| v0.5-fragments | P1-6 merged | tagged (pacing check: director 20 h game tests green) |
| v0.6-telling | P1-7 merged | |
| v0.7-accidents | P1-8 merged | |
| v0.8-endings | P2-1 merged | |
| v1.0 | P2-2 and P2-3 merged, 20 h simulation passes | |

## Tuning from playtests (apply as code defaults on the next atmosphere touch)
- `fogDriftStrengthMin` 0.45, `fogDriftStrengthMax` 0.7 (D-022). Already set in Mariano's run/config.

## Core contract batch: MERGED (P1-9). Remaining follow-ups per workstream:
- entity: FogEdge uses `FogLimits` (or the client-reported fog end); goes-under uses `startFigureDig`/`figureDug` (vertical limit is horizontal-only; keep fills within the column)
- atmosphere: `FogLimits.installShape(...)`; dead mountain silence
- accident: `removeLettingFall`, `restoreBlock`, `equipFromLedger`, flip CoreGaps
- lore: ProtectedAreas for the untouched grove, `editSign`, `addVeto` replaces TraceEditMixin, `leave` with block entity data
- dig: `restoreStack`, `SiteRegistry.update`

## Core contract batch (original list)
- `Director.timewarp` returns summary lines; `/a1016 timewarp` prints them (director)
- `GameClock.dayTicks` (director)
- A shared fog-end helper in core, used by entity and atmosphere. Entity's FogEdge imports atmosphere's Curves until then (entity)
- A public `ModConfig.save()` (entity's EntityConfig copies the private writer for now) so `/a1016 entity tune` and `eyes` persist (entity)
- `Pacing.stareSeconds` is unused now that entity has its own stare tuning; remove or redirect it (entity)
- Document the flags `ending:last_sighting` and `entity:last_sighting_seen` (entity)
- `MobTamper`/`installMobTamper` Javadoc names atmosphere as the owner, and `MobTamper.isTampered(Mob)` (atmosphere)
- A TraceService opt-in that lets a falling block drop, for the gravel ceiling (accident)
- A protected "untouched grove" area that new scars avoid: world's new-scar placer must skip `UntouchedGrove.contains` (lore)
- `leave` with block entity data plus `leaveStack`; flag `lore:still_burning` for D-004 (lore)
- Ending D's undo must skip ledger causes `lore:left/*` (lore, for P2-1)
- world records its hut and core-pyramid sites at server start, not only when generated (lore)
- `TraceService.restoreStack(level, ledgerEntry, toPos)` to move ledgered stacks into the network chest later (dig)
- `SiteRegistry.update(site, pos, size)` so the growing tunnel's TUNNEL_END stays current (dig)
- Read access to the profile salt for worldgen hashing; world keeps its own salt for now (world)
- `leave` with block entity data (chest contents), and a way to take back what was left (world)
