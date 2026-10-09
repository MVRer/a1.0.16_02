# a1.0.16_02: Architecture

Contracts and ownership. Every worker reads this file and then only the DESIGN.md sections named in its prompt. The orchestrator is the only one who changes `core`. If you need something here changed, say so in your report. Javadoc on the `core` classes is the detailed reference.

## Build
- MC 26.3, Java 25, Loom 1.18-SNAPSHOT, loader 0.19.5, Fabric API 0.162.0+26.3. These come from the official template. Do not change them.
- The Gradle daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`, Homebrew `openjdk@25`).
- Build: `./gradlew build`. It also runs the game tests (`check` depends on `runGameTest`).
- Game tests only: `./gradlew runGameTest`. Headless server; the exit code is the number of failed required tests (non-zero fails the build). JUnit report: `build/gametest-report.xml`.
- Dev client: `./gradlew runDevClient` ("Dev Client"). Opens the singleplayer world `run/saves/a1016_dev`, creating it on first launch: creative, cheats on, user `Dev`, seed `-4180345853331630785` (spawn in a Dappled Forest; cold ocean and snowy slopes ~90 blocks, frozen peaks ~100). Delete the folder to start over. Plain `./gradlew runClient` still works.
- Server: `./gradlew runServer` (dir `run/`, `eula.txt` already there). The console takes commands on stdin, e.g. `a1016 state`, then `stop`.

## Packages and ownership (`com.forzacode.a1016_02.*`)
| Owner | Paths |
| --- | --- |
| Orchestrator only | `build.gradle`, `gradle.properties`, `settings.gradle`, both `fabric.mod.json`, entrypoints (`A1016_02`, `client.A1016_02Client`, `A1016_02DataGenerator`), `assets/a1016_02/lang/en_us.json`, `docs/`, the `core` package |
| Each workstream `<ws>` | `<ws>/**` (Java), `src/gametest/java/.../<ws>/**`, `a1016_02.<ws>.mixins.json`, `data/a1016_02/<ws>/`, `assets/a1016_02/<ws>/`, and a `<ws>/` subfolder in any fixed registry folder (`data/a1016_02/worldgen/placed_feature/world/x.json` gives id `a1016_02:world/x`) |

Workstreams: `core director entity atmosphere world dig lore accident ending debug`.
- Common init: `<ws>.<Ws>Init.init()`, called by the main entrypoint after `CoreInit`.
- Client init: `<ws>.client.<Ws>ClientInit.init()`, called by the client entrypoint. Client-only classes live in `<ws>.client`.
- Mixins: config package is `<ws>.mixin`. Common mixins go in `<ws>.mixin` (list `"Foo"` under `mixins`); client mixins in `<ws>.mixin.client` (list `"client.Foo"` under `client`). Mixin classes cannot live outside the config package.
- Game tests: `<ws>.<Ws>GameTests` in `src/gametest/java`, already registered. Add `@GameTest public void x(GameTestHelper h)` methods (Fabric's `net.fabricmc.fabric.api.gametest.v1.GameTest`) that end with `h.succeed()`; use a superclass to split them up.
- Lang: put the keys and English text in your report. The orchestrator adds them. Fragment text goes in lore's data files, never in lang.
- Private persistence: your own `SavedData` in your package, `new SavedDataType<>(A1016_02.id("<ws>"), ...)` on `server.getDataStorage()` (file `data/a1016_02/<ws>.dat`). Shared facts live in `HerobrineState`.

## Code guardrails (the reviewer checks every one)
1. Every block, item or light change that "he" makes goes through `TraceService`. Every mob manipulation goes through `MobTamper`. The only exemptions are worldgen features and structures, which place blocks during chunk generation where nobody can see them, and D-047 (see TraceService).
2. Never damage, target or touch the player. Never spawn a mob. The figure entity (owned by entity) is the only entity the mod spawns. Fake sightings use mobs that already exist.
3. Nothing changes in the player's view: use `TraceService.isOutOfView`. The only in-view changes: the figure's dig-under (D-030), the fall after `removeLettingFall` (D-038), and Ending D's last minute giving back (`restoreVisibly`/`regrowVisibly`, D-048).
4. All timings come from `Pacing` or your config section. No hardcoded faster timings. `devFastMode` defaults to false.
5. In-game text never names real people, companies or forums. Use only "the developer", "the studio", "the team".
6. Fragment text matches DESIGN.md word for word.
7. Every accident is deniable and leaves exactly one clue.

## Core contracts (package `core`)
`Services` holds one static instance of each service. Stubs (`X.Stub`) are the default; the owning workstream calls `Services.installDirector / installMobTamper / installFragments / installAccidents / installDeathMarker` in its `init()`. Server thread only unless noted.

| Contract | Real impl | Job |
| --- | --- | --- |
| `Director` | director | Tick loop, decks, gates, tension, quiet, pity, fakes, stages by time |
| `TraceService` | core (real) | Every world edit "he" makes, plus the out-of-view check |
| `PlayerWatch` | core (real) | What the subject player is doing and has done |
| `SiteRegistry` | core (real) | Places where world and dig built things that lore can fill |
| `ProtectedAreas` | core (real) | Areas his scars and edits leave alone (the untouched grove) |
| `FogLimits` | core (static) | Fog end and render limit for a player, the same math on server and client |
| `MobTamper` | atmosphere (D-017) | Freeze, face, silence or move existing mobs |
| `FragmentService` | lore | Fragment placement and read tracking |
| `AccidentPlanner` | accident | One armed trap at a time |
| `DeathMarker` | accident | Marked deaths: cross, list cause, events |

### HerobrineState (`HerobrineState.get(server)`, file `data/a1016_02/state.dat`)
One per world. Change it only through its methods, which mark it dirty. The first `get` rolls the profile with a new random salt.
- `stage()`: `Stage` {ALONE 0, TRACES 1, PROXIMITY 2, TELLING 3, REMOVAL 4}. `setStage(server, stage)` fires `STAGE_CHANGED`.
- `attention()`, `tension()`: double, 0 to 100, clamped. Change them only through `Attention`.
- `profile()`: `WorldProfile`; `reroll(seed, salt)`. The salt is hidden (package-private). `worldgenSalt()`: derived from it once, then fixed for the world (a reroll keeps it); read it at server start for worldgen hashing.
- `subject()`: `Optional<Subject(uuid, name)>`, the first player who joins (D-002).
- `stopFired / listRead / tellingStarted` with setters. `tellingCount()` + `incrementTellingCount()` (lore counts every telling, D-039; the increment keeps the flag `lore:telling_count=<n>` in sync for older readers). `fragmentsRead()` + `markFragmentRead(id)`. `fragmentsPlaced()` + `setFragmentPlaced(id, GlobalPos|null)`.
- `markedDeaths()` + `addMarkedDeath(MarkedDeath(cause, GlobalPos, day))`.
- `firstBlocks()`: `FirstBlocks(block, craftingTable, chest)`, each a `PlacedBlock(GlobalPos, BlockState)` or null. Recorded by `PlayerWatch`.
- `effects()`: `Effects(musicOff, duskFogLevel)`. Change through `ClientEffects`; sent again on every join.
- `flags()`, `hasFlag(f)`, `setFlag(f, bool)`: free namespaced flags (`"lore:f04_done"`). Use them instead of asking for a new field. Shared ones: `ending:last_sighting` (set by ending: Ending A's last sighting may happen), `entity:last_sighting_seen` (set by entity: that figure was seen and is gone), `lore:still_burning` (set by world when its one still-burning camp is built, D-004; lore then puts F21 in that camp's emptied house only), `ending:path=<A|B|C|D>`, `ending:ended` (the story is over) and `ending:d_complete` (set by ending; the house copy stops for good after either of the last two), `ending:last_minute` (set by Ending D only while a real last minute gives back in view, D-048; never by the debug preview, cleared on reset and server stop), `director:silence_forever` (set by the endings; while it or `ending:path=C` is set, no atmosphere fog surge or drift and the house copy waits), `director:obeyed_after_stop` (set by the director while OBEYED_AFTER_STOP has fired and no telling came since; Ending A's obey rule), `entity:stared` (set by entity the first time someone stares him down).

### Attention
`Attention.trigger(server, AttentionTrigger.X)` applies the configured weight of a Triggers row (`DISC_13_UNDERGROUND`, `NAMED_HIM`, `WROTE_NEAR_TRACES`, `ENTERED_TUNNEL`, `DUG_INTO_PYRAMID`, `REPLANTED_GROVE`, `CARRYING_LIST`, `RULES_BOOK_NEAR_BASE`, `LOW_RENDER_DISTANCE`, `SLEPT`, `STARED_AT_HIM`, `DESTROYED_OWN_WRITING`, `OBEYED_AFTER_STOP`, `AVOIDED_TRACES`, `LEFT_GROVES_ALONE`, `STOPPED_DISC_13`, `DAYLIGHT_OPEN_AREAS`, `LIST_IN_LAVA`). Also `raise/lower(server, amount, reason)` and `raiseTension/lowerTension(server, amount, reason)`.

### WorldProfile (record)
`habits` (2 of `Habit` {CARVER, STRIPPER, WATCHER, MOURNER, COLLECTOR, VISITOR}), `density` {SPARSE, NORMAL, HEAVY}, `tempo` {EARLY, SLOW_BURN, VERY_LATE}, `fragments: Set<String>`, `signature` {STILL_BURNING, HOUSE_ELSEWHERE, CROSS_ROW}. `WorldProfile.roll(seed, salt)` is deterministic: `FIXED_FRAGMENTS` (10) plus 11 to 13 rolled, with `DEPENDENCIES` (D-003). `tempo.paceFactor()` is 0.6 / 1.0 / 1.4. `hasStillBurning()` (D-004) and `hasHouseCopy()` (D-005) fold in F21 and F27.

### EventCard
```java
String id();                  // unique snake_case, e.g. "fog_drift", "sighting_cow"
Tier tier();                  // AMBIENT, MINOR, MAJOR, SIGNATURE
Stage earliestStage();
Set<Habit> habits();          // weighting; empty = neutral
Set<CardTag> tags();          // SOUND SIGHTING SCAR FOG MOB ACCIDENT ITEM LIGHT TEXT DIG
boolean hasFake();            // can fire as a false positive
boolean contextFits(ServerPlayer player, ServerLevel world);
FireResult fire(FireContext ctx);  // record(player, level, fake, forced, random); FIRED | NO_SPOT | SKIPPED
```
Register cards in `init()` with `Director.register(card)` (stored in `CardRegistry`: `get(id)`, `all()`, `ids()`, `byTier(t)`), which works before the director is installed. When `fire` gets `NO_SPOT` (no out-of-view place exists right now), the director keeps the card for later. Sample: `debug_ping`.

### Director (`Services.director()`)
`tick(server)` runs every server tick; the impl picks its own cadence (`Pacing.directorTickTicks()`). `FireResult fire(server, cardId, fake)` is for debug: it fires for the subject, skips gates and pacing but never the out-of-view rule. Also `List<String> timewarp(server, days)` (summary lines; `/a1016 timewarp` prints them), `ticksSinceTag(server, CardTag)` (Long.MAX_VALUE if never), `inQuiet(server)`, `List<String> debugLines(server)`. The stub's `fire` calls the card directly (and fires `CARD_FIRED`); its `timewarp` calls `GameClock.warp`. Dry runs reach Telling only when the subject names him at a set hour (`simTellingAtHour`, `/a1016 director sim <h> telling <atHour>`); the director never enters it alone (D-041).

### TraceService (`Services.traces()`, real)
- `isOutOfView(ServerLevel, BlockPos | AABB | Collection<BlockPos>)`: false if any player is within 3 blocks of it, or has line of sight inside a 160° cone within the server view distance. Only opaque full blocks (`isSolidRender`) block sight; glass, leaves, ice and doors do not. The collection form checks every block with a see-through neighbour. Unloaded points count as unseen. D-027: the 3-block rule skips a block strictly below a player's feet, in the columns their hitbox overlaps, while they look 30°+ up and it is outside the cone. Geometry for tests: `static isOutOfView(level, box, viewers, near, cone)`, `positionsOutOfView(level, positions, viewers, near, cone)`, `Viewer.of(player, chunks)`.
- `remove(level, pos, cause)` (keeps a waterlogged block's water), `move(level, from, to, cause)` (block entity data moves too; `to` must be replaceable), `convert(level, pos, newState, cause)`, `leave(level, pos, state, [blockEntityData,] cause)` (only into air, replaceable plants, snow layer or fluid without a block entity; `saveCustomOnly` data such as chest items or sign text; never undone), `removeStack(level, pos, slot, count, cause)` (false if `count <= 0` or the slot is empty), `moveStack(level, from, slot, to, cause)`, `leaveStack(level, containerPos, stack, cause)` (first empty slot; never undone).
- `removeLettingFall(level, pos, cause)`: removes the block and lets the sand or gravel column on it, or the stalactite under it, fall by the game's rules. Only the removed block (and its own dependents) must be out of view; the fall and landing may be seen (D-038). Vetoes cover every cell. Only the removed block is ledgered. Refused if sand or gravel would break into an item.
- Taking back: `restoreBlock(level, removeEntry, toPos)` (its spot or up to 2 blocks off; the entry is closed, or rewritten as a MOVE), `restoreStack(level, removeStackEntry, containerPos)` (closes the entry), `equipFromLedger(level, stackEntry, mob, slot, cause)` (a REMOVE_STACK stack, or a MOVE_STACK stack taken back out of its chest, into a mob's empty slot; guaranteed drop, undamaged; the entry becomes EQUIP). All out of view; none creates items.
- `undo(level, entry[, cause])` takes one ledger entry back exactly once, out of view (REMOVE/REMOVE_STACK through the restores, MOVE/CONVERT/MOVE_STACK/sign text by the reverse edit; both entries closed). `UndoResult` DONE, IN_VIEW, UNLOADED (never loads a chunk) or BLOCKED (taken or built over, worn by a mob). Which entries to undo is the caller's choice (Ending D's `Undo.skipReason`).
- D-048, ending-only: `restoreVisibly(level, removeEntry, cause)` (its own spot, entry closed) and `regrowVisibly(level, leaves, cause)` (leaves into air, not ledgered) work in view, but only while `TraceService.LAST_MINUTE_FLAG` (`ending:last_minute`) is set, never into a cell a player or mob stands in.
- `editSign(level, pos, frontLines, backLines, cause)`: null or empty blanks a side; ledgered as BLOCK_ENTITY with the old text; waxed signs only for causes starting `lore:left/F30`.
- `startFigureDig(level, column, cause)` then `figureDig(level, dig, pos)`, `figureDug(level, dig)`, `figureFill(level, dig, entry, pos)`: ONLY for the figure's dig-under exit (D-030), allowed in view, within 3 blocks of the dig's column. Dig refuses fluids, bedrock, block entities and player-placed blocks; fill puts back one of that dig's own entries in its recorded state (ledgered as a MOVE).
- `addVeto(TraceVeto)`: `vetoes(level, pos)` is asked for every position every edit would change (dependents, falls, containers, signs, figure edits included); one true refuses the edit. Keep it cheap.
- Edits are silent: no drops, particles, sounds or spilled containers. Neighbours that would break (torches, signs, plants, rails, door halves) are removed silently and ledgered as `<cause>/dependent`; neighbours that only change shape are view-checked too. A falling block left without support, or an item frame, painting or leash knot on a changed block, refuses the edit.
- `batch(level, cause)` returns a `TraceBatch`: `.remove(pos) .move(a, b) .convert(pos, s) .leave(pos, s)` then `commit()`: one plan, one view check over every affected block, all or nothing (retry later if it returns false).
- Every edit returns false and changes nothing if anything it changes is in view. Tests and debug use `Services.traces().forced()`.
- Everything except `leave` and `leaveStack` goes to `TraceLedger.get(server)` (`data/a1016_02/traces.dat`): `entries()` oldest first (kind REMOVE, MOVE, CONVERT, REMOVE_STACK, MOVE_STACK, BLOCK_ENTITY or EQUIP; cause, day, pos, to, old state, block entity, stack, slots, entity), `remove(entry)` once undone. Causes are `<ws>:<what>`; any cause containing `:left/` (`lore:left/F07`, the team's stair `lore:left/ending-d/stair`, `world:left/...`) is what others left: `TraceLedger.isLeftByOthers(cause)`, never his trace for lore, never undone by Ending D.
- D-047, the only exceptions to "every change goes through TraceService": the F10 and F23 list books update their page text when opened or read (not ledgered; Ending D keeps every cause), and lore puts an invisible custom-data marker on the player's own written books to remember what they wrote. D-049 adds Ending D's markers on items (the cairn's first block, grove planks).

### PlayerWatch (`Services.watch()`, real)
`Optional<ServerPlayer> subject(server)`, `isSubject(player)`, `stillTicks(player)` (position only), `ticksSinceCombat(player)`, `ticksSinceJoin(player)`, `isSleeping(player)`, `Optional<GlobalPos> base(player)` (respawn point, else the subject's first block), `lastVisitDay(level, ChunkPos)` (-1 if never; sampled every 5 s, 3x3 chunks). Footprint (all players, capped per dimension): `wasPlacedByPlayer(level, pos)`, `wasDugByPlayer(level, pos)`, `placedNear(level, center, radius, Block | TagKey<Block> | Predicate<BlockState>)` and `dugNear(level, center, radius)` return `List<BlockPos>`. Placement comes from core's `BlockItemMixin`.

### GameClock
`playTicks(server)` counts server ticks while the subject is online, plus timewarp. `day(server)` is overworld clock time / 24000 plus timewarp days (day 0 is the first). `dayTicks(server)` is `day * 24000` plus the time of day. `warp(server, days)` adds days and 24000 play ticks per day.

### SiteRegistry (`Services.sites()`, real; thread-safe so worldgen can write to it)
`Site record(SiteType, ResourceKey<Level>, BlockPos, size)`, `find(type, GlobalPos near, radius)` (horizontal, nearest first), `findUnclaimed(...)`, `boolean claim(site, fragmentId)`, `Optional<Site> update(site, pos, size)` (keeps id, type and claim), `all()`. `Site(id, type, dimension, pos, size, Optional claimedBy)`. SiteType: RUINED_HUT, ABANDONED_BUILD, TUNNEL_END, OCEAN_PYRAMID, BARE_GROVE, STAIR_BOTTOM, PANIC_TOWER, EMPTIED_HOUSE, CROSS, LONE_LIGHT, DEAD_MOUNTAIN, CUT, HOUSE_COPY, UNDER_BASE. World and dig record sites (world records its one hut and the core pyramid once per world, as soon as they are planned at server start). Lore fills them.

### ProtectedAreas (`Services.protectedAreas()`, real; thread-safe, file `data/a1016_02/protected.dat`)
`protect(id, dimension, BoundingBox)` (same id moves it), `unprotect(id)`, `isProtected(dimension, pos)`, `intersects(dimension, box)`, `all()`. World's new-scar placer skips protected columns. Lore protects the untouched grove.

### FogLimits (static, both sides)
`FogLimits.of(player)` (server) or `of(clientChunks, serverChunks, duskFogLevel, dayTime)` gives `Result(chunks, renderLimit, fogEnd)`: the un-pulled render limit (`min(client, server) * 16`) and the fog end after the dusk fog, by atmosphere's client curve (`duskWeight`, `fogEnd`). Atmosphere owns the shape: `installShape(() -> AtmosphereConfig.get().fogShape())`.

### MobTamper / FragmentService / AccidentPlanner / DeathMarker (interfaces)
- MobTamper (atmosphere installs it): `boolean freeze(mob, ticks)`, `face(mob, Vec3, ticks)`, `silence(mob, ticks)`, `moveOutOfView(mob, BlockPos)`, `void release(mob)`, `isTampered(mob)` (any effect on it now).
- FragmentService: `isEnabled(server, id)`, `place(id, level, hint)`, `markRead(player, id)`, `Optional<GlobalPos> placed(server, id)`.
- AccidentPlanner: `Optional<TrapType> armed()`, `arm(player, TrapType)`, `arm(player, TrapType, BlockPos center)` (spots looked for around the center, nearest first), `disarm()`, `causedBy(player, DamageSource)`. `TrapType(String id)` is a record, so accident defines its own.
- DeathMarker: `mark(player, cause, pos)`, `count(server)`, `lastCrossPos(server)` (the post bottom of the newest cross built for a marked death), `crossFor(server, death)` (that cross, only if it stands for the death at exactly that spot; Ending A's sign goes there).

### Events (`core.HerobrineEvents`, Fabric `Event`)
`STAGE_CHANGED(server, old, new)`, `TELLING(player, text, pos, namesHim)` (fired by lore), `FRAGMENT_READ(player, id)`, `MARKED_DEATH(player, cause, pos)`, `CARD_FIRED(player, cardId, fake)`. Entity's own: `FigureApi.STARED(player, him)` when STARED_AT_HIM fires (Ending C counts it as staring into the fog).

### Cross-workstream APIs (not core)
- world `HouseCopyApi`: `exists`, `finish` (Ending B), `cancelFinish` (B left or the story ended), `finished`, `site`, `moved`.
- entity `FigureApi`: `spawnAt`, `spawnAtFogEdge`, `walkAway(him[, noRun])`, `setNoRun(him, true)` (Ending D's last minute: never runs, rushes or goes under), `anyOut`, `STARED`.
- lore `LoreApi`: `tellingCount`, `stopSign`, `finishF10`, `placeF20`, `moveStopSignToCross`, `twinSigns(server)` (F30's grove and bedrock signs).
- ending `EndingApi`: `path`, `progress`/`setProgress`, `ended`, `commitD`, `endStory` (Ending D's last minute commits D and ends the story through it).

### ClientEffects (payloads in core; atmosphere does the client side)
Payloads `FogSurge(strength, rampTicks, holdTicks, fadeTicks)`, `Silence(ticks, fadeTicks)`, `MusicOff(off)`, `DuskFog(level)`, `Sync(musicOff, duskFogLevel)`. Server: `ClientEffects.fogSurge(player, ...)`, `silence(player, ticks, fade)`, `setMusicOff(server, off)` and `setDuskFog(server, level)` (stored and sent to all), `sync(player)` (on join). Client: atmosphere calls `core.client.ClientEffectsClient.install(handler)` with a `ClientEffectsClient.Handler`; callbacks run on the client thread. `SoundCues.playTo(player, sound, pos, vol, pitch)` plays a vanilla positional sound for one player only.

### Config (`config/a1016_02.json`)
`ModConfig.pacing()` is `Pacing`: every number from DESIGN.md "The director", 4b, Triggers and related rules, in natural units, plus `...Ticks()` accessors (and `TickRange tracesStart(tempo)` etc.) that divide by `devFastDivisor` when `devFastMode` is true (default false). Use `ModConfig.realTicks(seconds)` for your own real-time values; cost cadences and chunk-load timeouts are plain ticks, named and commented. Workstream tunables: `ModConfig.section("<ws>", Type.class, Type::new)` (public fields, no-arg constructor), stored under `sections.<ws>`.

### Commands (op level 2)
Core (in `debug`): `/a1016 state`, `stage <n>`, `fire <cardId> [fake]`, `timewarp <days>` (prints the director's summary), `profile reroll`. Workstreams add `/a1016 <ws> ...` with `CommandHooks.register((root, ctx) -> root.then(Commands.literal("<ws>")...))`.
