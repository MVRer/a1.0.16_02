# a1.0.16_02: Architecture

Contracts and ownership. Every worker reads this file and then only the DESIGN.md sections named in its prompt. The orchestrator is the only one who changes `core`. If you need something here changed, say so in your report.

## Build
- MC 26.3, Java 25, Loom 1.18-SNAPSHOT, loader 0.19.5, Fabric API 0.162.0+26.3. These come from the official template. Do not change them.
- The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`, Homebrew `openjdk@25`).
- Build: `./gradlew build`. Game tests: `./gradlew runGameTest` (the scaffold confirms the exact task). Dev client: `./gradlew runClient`.

## Packages and ownership (`com.forzacode.a1016_02.*`)
| Owner | Paths |
| --- | --- |
| Orchestrator only | `build.gradle`, `gradle.properties`, `settings.gradle`, both `fabric.mod.json`, entrypoints (`A1016_02`, `client.A1016_02Client`, `A1016_02DataGenerator`), `assets/a1016_02/lang/en_us.json`, `docs/`, the `core` package |
| Each workstream `<ws>` | `<ws>/**` (Java), `src/gametest/java/.../<ws>/**`, `a1016_02.<ws>.mixins.json`, `data/a1016_02/<ws>/`, `assets/a1016_02/<ws>/`, and a `<ws>/` subfolder in any fixed registry folder (`data/a1016_02/worldgen/placed_feature/world/x.json` gives id `a1016_02:world/x`) |

Workstreams: `core director entity atmosphere world dig lore accident ending debug`.
- Common init: `<ws>.<Ws>Init.init()`, called by the main entrypoint after `CoreInit`.
- Client init: `<ws>.client.<Ws>ClientInit.init()`, called by the client entrypoint. Client-only classes live in `<ws>.client`.
- Mixins: `<ws>.mixin` (and `<ws>.client.mixin` for client mixins), listed only in `a1016_02.<ws>.mixins.json`.
- Game tests: `<ws>.<Ws>GameTests` in the gametest source set, already registered. Add methods or classes there.
- Lang: put the keys and English text in your report. The orchestrator adds them. Fragment text goes in lore's data files, never in lang.
- Private persistence: your own `SavedData` in your package, with id `a1016_02_<ws>`. Shared facts live in `HerobrineState`.

## Code guardrails (the reviewer checks every one)
1. Every block, item or light change that "he" makes goes through `TraceService`. Every mob manipulation goes through `MobTamper`. The only exemption is worldgen features and structures, which place blocks during chunk generation where nobody can see them.
2. Never damage, target or touch the player. Never spawn a mob. The figure entity (owned by entity) is the only entity the mod spawns. Fake sightings use mobs that already exist.
3. Nothing changes in the player's view: use `TraceService.isOutOfView`.
4. All timings come from `Pacing` or your config section. No hardcoded faster timings. `devFastMode` defaults to false.
5. In-game text never names real people, companies or forums. Use only "the developer", "the studio", "the team".
6. Fragment text matches DESIGN.md word for word.
7. Every accident is deniable and leaves exactly one clue.

## Core contracts (package `core`)
`Services` holds one static instance of each service. Stubs are the default. The owning workstream installs its real implementation in its `init()`.

| Contract | Real impl | Job |
| --- | --- | --- |
| `Director` | director | Tick loop, decks, gates, tension, quiet, pity, fakes, stages by time |
| `TraceService` | core (real) | Every world edit "he" makes, plus the out-of-view check |
| `PlayerWatch` | core (real) | What the subject player is doing and has done |
| `SiteRegistry` | core (real) | Places where world and dig built things that lore can fill |
| `MobTamper` | accident | Freeze, face, silence or move existing mobs |
| `FragmentService` | lore | Fragment placement and read tracking |
| `AccidentPlanner` | accident | One armed trap at a time |
| `DeathMarker` | accident | Marked deaths: cross, list cause, events |

### HerobrineState
Per-world `SavedData` on the overworld, id `a1016_02_state`. Change it only through its methods, which mark it dirty.
- `stage`: `Stage` {ALONE 0, TRACES 1, PROXIMITY 2, TELLING 3, REMOVAL 4}. `setStage` fires `STAGE_CHANGED`.
- `attention`, `tension`: double, 0 to 100, clamped. Change them through `Attention.raise/lower(server, amount, reason)`.
- `profile`: `WorldProfile`. `salt`: a random long made on the first load and kept hidden.
- `subject`: UUID and name of the first player who joins. The mod is singleplayer-first (D-002).
- Flags: `stopFired`, `listRead`, `tellingStarted`. `fragmentsRead: Set<String>` ("F01".."F30"). `fragmentsPlaced: Map<String, GlobalPos>`.
- `markedDeaths: List<MarkedDeath(cause, GlobalPos, day)>`.
- `firstBlocks`: first placed block, first crafting table, first chest. Each is a `GlobalPos` plus a `BlockState`, or null. `PlayerWatch` records them.
- `effects`: `musicOff`, `duskFogLevel`. These are sent to the client again on every join.
- `flags: Set<String>` holds free namespaced flags (`"lore:f04_done"`). Use it instead of asking for a new field.

### WorldProfile (record)
`habits` (2 of {CARVER, STRIPPER, WATCHER, MOURNER, COLLECTOR, VISITOR}), `density` {SPARSE, NORMAL, HEAVY}, `tempo` {EARLY, SLOW_BURN, VERY_LATE}, `fragments: Set<String>`, `signature` {STILL_BURNING, HOUSE_ELSEWHERE, CROSS_ROW}. `WorldProfile.roll(seed, salt)` is deterministic. Each tempo has a `paceFactor` (0.6 / 1.0 / 1.4) that scales the stage times. The fragment roll rules are in DECISIONS.md D-003.

### EventCard
```java
String id();                  // unique snake_case, e.g. "fog_drift", "sighting_cow"
Tier tier();                  // AMBIENT, MINOR, MAJOR, SIGNATURE
Stage earliestStage();
Set<Habit> habits();          // weighting; empty = neutral
Set<CardTag> tags();          // SOUND SIGHTING SCAR FOG MOB ACCIDENT ITEM LIGHT TEXT DIG
boolean hasFake();            // can fire as a false positive
boolean contextFits(ServerPlayer player, ServerLevel world);
FireResult fire(FireContext ctx);  // ctx: player, level, fake, forced, random; FIRED | NO_SPOT | SKIPPED
```
Register cards in `init()` with `Director.register(card)`, which stores them in `CardRegistry` and works before the director is installed. When `fire` gets `NO_SPOT` (no out-of-view place exists right now), the director keeps the card for later.

### Director (`Services.director()`)
`tick(server)` runs every server tick and the impl picks its own cadence (30 s). `fire(server, cardId, fake)` is for debug: it skips gates and pacing but never the out-of-view rule. Also `timewarp(server, days)`, `ticksSinceTag(CardTag)` (Long.MAX_VALUE if the tag never fired), `inQuiet()`, `debugLines()`. The stub's `fire` calls the card directly and its `timewarp` advances `GameClock`.

### TraceService (real, in core)
- `isOutOfView(level, BlockPos | AABB)`: false if any player is within 3 blocks of it, or has line of sight to it inside a 160° cone within view distance.
- `remove(level, pos, cause)`: no drops, particles or sound. `move(level, from, to, cause)`. `convert(level, pos, newState, cause)` (grass to dirt). `leave(level, pos, state, cause)` places things "left by others" (fragments, builds, lights) and is not undone. `removeStack` and `moveStack` move items between containers.
- `TraceBatch` handles large edits with one view check on the batch's AABB.
- Every call returns false and changes nothing if the target is in view. Tests can use `force`.
- Removals and moves are written to a ledger (`a1016_02_traces`) so Ending D can undo them.

### PlayerWatch (real, in core)
`subject(server)`, `stillTicks`, `ticksSinceCombat`, `ticksSinceJoin`, `isSleeping`, `base(player)` (respawn point, otherwise first placed block), `lastVisitDay(level, ChunkPos)` (-1 if never visited), and the footprint: `wasPlacedByPlayer(pos)`, `wasDugByPlayer(pos)`, `placedNear(pos, radius, block|tag)`, `dugNear(pos, radius)`.

### GameClock
`playTicks()` counts real play time while the subject is online, plus timewarp. `day(server)` is overworld day time / 24000, plus timewarp days.

### SiteRegistry (real, in core; thread-safe so worldgen can write to it)
`record(SiteType, dimension, pos, size)`, `find(type, near, radius)`, `claim(site, fragmentId)`. SiteType values: RUINED_HUT, ABANDONED_BUILD, TUNNEL_END, OCEAN_PYRAMID, BARE_GROVE, STAIR_BOTTOM, PANIC_TOWER, EMPTIED_HOUSE, CROSS, LONE_LIGHT, DEAD_MOUNTAIN, CUT, HOUSE_COPY, UNDER_BASE. World and dig record sites. Lore fills them.

### MobTamper / FragmentService / AccidentPlanner / DeathMarker (interfaces)
- MobTamper: `freeze(mob, ticks)`, `face(mob, Vec3, ticks)`, `silence(mob, ticks)`, `moveOutOfView(mob, BlockPos)`, `release(mob)`.
- FragmentService: `isEnabled(id)`, `place(id, level, hint)`, `markRead(player, id)`, `placed(id)`.
- AccidentPlanner: `armed()`, `arm(player, TrapType)`, `disarm()`, `causedBy(player, DamageSource)`.
- DeathMarker: `mark(player, cause, pos)`, `count()`.

### Events (`core.HerobrineEvents`, Fabric `Event`)
`STAGE_CHANGED(server, old, new)`, `TELLING(player, text, pos, namesHim)` (fired by lore), `FRAGMENT_READ(player, id)`, `MARKED_DEATH(player, cause, pos)`, `CARD_FIRED(player, cardId, fake)`.

### ClientEffects (payloads in core; atmosphere does the client side)
`FogSurge(strength, rampTicks, holdTicks, fadeTicks)`, `Silence(ticks, fadeTicks)`, `MusicOff(off)`, `DuskFog(level)`, and `Sync` on join from `HerobrineState.effects`. The server API is `ClientEffects.fogSurge(player, ...)` and so on. `SoundCues.playTo(player, sound, pos, vol, pitch)` plays a vanilla positional sound for one player only.

### Config
`config/a1016_02.json`. `Pacing` holds every number from DESIGN.md "The director, 4b" and related rules. Its real-time accessors divide by `devFastDivisor` when `devFastMode` is true (default false). Workstream tunables go in `ModConfig.section("<ws>", Type.class, Type::new)` and are stored under `sections.<ws>`.

### Commands (op level 2)
Core: `/a1016 state`, `stage <n>`, `fire <cardId> [fake]`, `timewarp <days>`, `profile reroll`. Workstreams add `/a1016 <ws> ...` through `CommandHooks.register((root, ctx) -> ...)`.
