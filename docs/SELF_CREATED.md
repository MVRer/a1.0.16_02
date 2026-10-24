# Self-created: the world is a little off

Oct 16, 2026 · lead horror design for the branch `self-created` (from `99ddb51`, main with v1.3 and the playtest fixes)

**Spoilers. Mariano plays this layer blind. He must not read this file, the task branches' commits or the SC test
names.** Everything here lives on `self-created`; `main` is untouched until he says.

**The second wave** (features 16 to 31 and the upgrades 01+, 04+, 11+) is `docs/SELF_CREATED_2.md`. It builds on this
file and changes it only where it says so: `sc01` is version 4 there (01+, keys `o` and `p`), the `/a1016 sc` index runs
to 31 and the overlay's rows carry the second wave's arms, and its pacing (both waves in the decks, the dry runs' own
moments for its cards) is measured there. Read this file's spoiler rules, guardrails, core contracts and common gates
first; they hold for the second wave too.

## Vision

v1.0 to v1.3 built him: the fog, the figure, the scars, the list, the director that waits, the tunnel ladder, the
echoes, the screenshot. What is still missing is the feeling the 2010 thread gave: the world itself is slightly wrong,
you only notice on the second look, and after a few evenings you don't want to go back to certain places, or open the
world at all.

This layer adds no new thing to the world. It takes what the player already trusts, their house, their animals, their
torches, their way home, their bed, the night sky, their friend, the world list and the game's own messages and
records, and makes each one slightly wrong, once, out of sight, in a way that can be shrugged off the first time and
not the third. Fear of places is built on purpose: where something happened is where it happens again, the spot where
he stood keeps its fog, and the one place where nothing ever happened is where it finally does.

What holds over every feature:
1. **Vanilla only.** Blocks, sounds, mobs, particles and UI strings the game already has. Nothing new is drawn, spawned
   or made to glow. No jumpscare, no screamer, no gore, no chase, no new creature.
2. **Absence before presence.** Things go missing, turn, fall quiet, lose their names. He still never creates and never
   strikes (DESIGN guardrails 1 and 7 hold everywhere in this layer).
3. **Deniable first.** Every first time reads as worldgen, a mob, the player's memory or the game. Evidence comes with
   repetition and on the second look.
4. **Out of sight.** Every world change passes `TraceService`'s out-of-view rule; every sound comes from where the
   player can't see; every mob change goes through `MobTamper`.
5. **Rare and spaced.** Every card goes through the director's decks and gates. Everything that is not a card is capped
   per world and spaced in in-game days.
6. **The canon arc stays.** Nothing here explains him, names him, or changes an ending. It deepens Proximity and Telling
   and points back at the list, the Under-you network and the screenshot.

## Spoiler rules (D-080)

The point is that he finds these by playing. So nothing he sees in normal play or in his usual tools names a feature.
- Card ids are `sc_NN` (and `sc_NNb` for a second card), never descriptive. They show in `/a1016 state`,
  `/a1016 director`, `/a1016 fire` completion and the log.
- Debug commands live under `/a1016 sc <NN> ...` (each workstream adds its numbers to the same `sc` literal; Brigadier
  merges children of one literal). Under a number, only letters and the plain verbs `arm`, `force`, `fake`, `here`,
  `reset`, `clear` (FIX-SC-FINAL: `tonight`, `noshaft`, `nogap`, `anytime`, `trust`, `circle`, `last`, `version` and
  `seed` were renamed). Chat lines from these commands say what happened in plain words, as the dig arms do, but only to
  the op who ran them.
- Config: one section per feature, `sections.scNN`, owned by that feature's workstream (a `ModConfig.Versioned` class in
  `<ws>.sc`). The keys written are opaque letters (`a`, `b`, ...; each feature's Config line below maps them), never the
  thing (`bed`, `moon`, `name`); only the switch's `enabled` and each `version` are words. FIX-SC-FINAL renamed them:
  each field carries `@SerializedName(value = "<letter>", alternate = "<old key>")` and its section's version rose by
  one (`sc01` and `sc15` to 3, the others to 2), so a dev config keeps its hand edits under the old keys and is written
  back with the new ones.
- Docs: this file only. No rows in DESIGN.md, TRY_IT.md, DECISIONS.md or ARCHITECTURE.md until he has played it; SC-0
  adds one line to ARCHITECTURE: "Self-created layer (branch `self-created`): contracts and features in
  `docs/SELF_CREATED.md` (spoilers)". The core contracts below are documented here and in Javadoc.
- The debug overlay shows the layer's lines only after `/a1016 debug overlay sc` (dev only, SC-5).
- Log lines at INFO and above name only `sc_NN`. Game test classes are `SelfCreated<Ws>Tests` with method names that
  describe the rule; that is fine, he does not read test names.
- Held by `core.VerifyScSpoilerTests` with the layer's list of words that name a feature (`SelfCreatedDebugTests.NAMES`
  and a few more): every config key written (the class defaults and the file the test server wrote), every literal,
  argument and usage line under `/a1016 sc`, and every INFO, WARN and ERROR log line of the layer, read from the sources
  (its `sc` packages, its files outside them, and any line that names an `sc` id).

## Guardrails: held and bent

| Rule | In this layer |
| --- | --- |
| DESIGN 1, he never strikes | Held. Nothing damages the player. 15 removes a block only once nobody is on or near it, and only over a short drop onto plain ground (3 or less from their feet: no fall damage), never over or beside lava or water, never onto dripstone, magma or the like, never while they are hurt: it is never lethal on its own (review R9). 11's shaft is at a dead end only its trail reaches, past its sign, and a fall into it lands in water (FIX-SC-FINAL) |
| DESIGN 2, he never speaks or writes | Held. The only new in-world text is t0rch's sign, "brb", left by a person (11). The world list, `/seed` and the bed message are vanilla's own strings around the canon version and seed (D-081, D-082) |
| DESIGN 3, default Steve, no glow | Held. 02 hides a friend's name tag (canon: "no name over him"); it never changes anyone's skin or eyes |
| DESIGN 4, never in front of the camera | Held. 03 renders a frame from where he stood, never presented on screen |
| DESIGN 7, he only removes or moves | Held. Turned blocks are a state change of an existing block (08); prints are grass, snow, crops and plants removed (09, 10); the closed way back is two wall blocks moved (13); names are removed (07). t0rch's torches and sign were left by others (11) |
| ARCH 3, nothing changes in view | Held. The moon is switched while it is below the horizon (04); fog places ease in over a minute like weather (10) |
| ARCH 2, never touch the player | Bent once: 12 refuses one bed attempt with vanilla's own reason; the next attempt sleeps |
| "The mod never tells you" stays inside the world | **Bent (D-081).** The world list's last played time and version, the world icon, `/seed` and the statistics may lie. The mod itself writes only in the host's own world folder (vanilla's own backup button, offered by vanilla's question in 14 b, writes vanilla's zip to vanilla's backups folder, as for any world), only vanilla strings and the canon numbers, never a deletion, each once |
| Vanilla UI shows the truth | **Bent (D-082).** The bed's "monsters nearby" (12) and a friend's missing name tag (02). Each lie is true in the story: the shaft under the bed is real; the figure at the fog edge could be him |

## Core contracts (SC-0)

A small core batch so the four workers never wait on each other. Javadoc is the reference; this is the summary.

1. **`HerobrineEvents.FIGURE_LEFT`** `(ServerLevel level, Vec3 stoodAt, float yaw, List<Vec3> path, String variant,
   UUID appearedFor, boolean seen)`: fired by entity once when a figure is gone (`HimEntity.gone`). `stoodAt` is where
   his feet stood still (his spawn feet, or where he stopped before leaving), `path` his feet sampled every 2 blocks of
   his way out (at most 24, oldest first; empty if he vanished or went under), `seen` is his `everSeen`. For 10.
2. **`HerobrineEvents.TRACED`** `(ServerLevel level, TraceLedger.Entry entry, boolean placedByPlayer)`: fired by core
   after the ledger records an entry (each entry of a committed batch once, after the commit); `placedByPlayer` is
   `PlayerWatch.wasPlacedByPlayer` at the entry's position read before the edit. Never for `leave`/`leaveStack`.
   Listeners must be cheap. For 14 (d).
3. **Fog places** (D-085): `core.FogPlace(ResourceKey<Level> dimension, Vec3 center, float radius, float level,
   long fromClock, long untilClock, int rampTicks, int fadeTicks)` (record, `CODEC`, `STREAM_CODEC`; overworld clock
   ticks like `FogSpell`). `amount(Vec3 at, ResourceKey<Level> dim, long clock)` = `level` × the time ease (in over
   `rampTicks`, out over `fadeTicks`, `FogLimits.ease` exponent 3, 0 outside) × the space ease (`ease(1 - d / radius,
   2)` on the horizontal distance, 0 past the radius). `HerobrineState.Effects` gains `List<FogPlace> fogPlaces`
   (optional field, old saves load with none; the old constructors stay). `ClientEffects.addFogPlace(server, place,
   max)` (drops ended places, ends the oldest early with its fade if more than `max`, stores, sends to everyone) and
   `clearFogPlaces(server)`; payload `ClientEffects.FogPlaces(List<FogPlace>)` (`a1016_02:fog_places`); `Sync` carries
   them (a new constructor; the old ones give none); `ClientEffectsClient.Handler.fogPlaces(effect)` (default nothing).
   `FogLimits.placeAmount(places, dim, pos, clock)` combines places as `1 - Π(1 - a)`; `FogLimits.of(player)` combines
   `combine(dusk, combine(spell, place))` with the place weighted by `skyFactor` at the eyes like the spell (fog places
   are weather: none in a tunnel); with no places every result is bit for bit what it was. Places count where
   `FogLimits` counts (the figure's band, the screenshot's fog end), never as the screenshot's fog-on flag.
4. **Names** (D-086): `MobTamper.unname(Mob mob, String cause)` (default false; atmosphere implements: refused if the
   mob has no custom name or its box is in anyone's view; removes `CustomName`, keeps nothing visible) and
   `TraceLedger.Kind.NAME` with `TraceLedger.recordName(ServerLevel, Mob, Component oldName, String cause)` (pos = the
   mob's block, `entity` = its UUID, `blockEntity` = `{name: <component>}`). `TraceUndo` NAME: the mob loaded in that
   level, out of view and without a name now gets it back (DONE); not loaded UNLOADED (never loads a chunk); named
   since BLOCKED; in view IN_VIEW. Ending D's afterward then gives names back with no ending change.
5. **Sounds**: `SoundCues.stepType(ServerLevel level, BlockPos floor)` (the floor's step `SoundType` with vanilla
   26.3's cover rule: a block in `#inside_step_sound_blocks` or `#combination_step_sound_blocks` above wins; the same
   rule as dig's package-private `SoundCards.stepSound`, which dig may switch to later) and `SoundCues.alone(ServerPlayer
   player, double radius)` (no sharer and no other player in the level within `radius`, spectators and the dead
   included: dig's `Ladder.alone` rule over `SoundCues`' listeners).
6. Docs: the ARCHITECTURE pointer line; version `1.4.0-26.3` in `gradle.properties` (the orchestrator's call).

Tests: the event fires once per gone figure with the stand spot and path (a recording listener; entity's real call is
SC-4's, so core tests fire it by hand and check the listener contract); TRACED once per ledgered entry, once per batch
entry after commit, never for `leave`, `placedByPlayer` true for a removed placed torch; FogPlace codecs round trip, amount 0 outside time and radius, combine with two
places, a v1.3 `state.dat` loads with none, `FogLimits.of` unchanged without places; NAME ledger round trip and undo
(DONE, UNLOADED, BLOCKED, IN_VIEW), an old `traces.dat` loads; `stepType` matches dig's rule on snow, leaf litter, a
carpet; `alone` counts a spectator within the radius.

As built (SC-0, `SelfCreatedCoreTests`):
- **The switch** (not in the design above): `sections.sc.enabled` (default true, core's section `sc` v1). Every feature
  asks `SelfCreated.enabled()` (any thread, both sides) first and does nothing while it is false; core's
  `ClientEffects.addFogPlace` and the contract of `MobTamper.unname` already refuse. Register what puts your visible
  changes back with `SelfCreated.onSwitchedOff("scNN", server -> ...)` in init (idempotent, cheap, no chunk loads):
  it runs at every server start while the switch is off and at once on `SelfCreated.setEnabled(server, false)`. A
  client-only change shown with no server (the world list) checks `enabled()` itself.
- **Files**: write only with `SelfCreated.writeInWorld(SelfCreated.worldFolder(server) or the summary's folder,
  "relative/path", bytes)` (refuses anything that leaves the world's folder; whole or nothing; never deletes).
- `FIGURE_LEFT`'s `appearedFor` is nullable; the invoker cuts the path to `HerobrineEvents.FIGURE_PATH_MAX` (24).
  Both new events log a throwing listener and go on.
- `TRACED` also fires for `recordName` (placed false) and for stack and sign edits; never for an undo's closed reverse
  edits (only what an undo leaves ledgered fires, after it).
- `TraceLedger.recordName` returns the entry, or null (nothing recorded: keep the name); call it before clearing.
  `TraceLedger.storedName(level, entry)` reads it back. NAME undo: not in the level is UNLOADED only while the
  entities of its recorded chunk are not loaded, BLOCKED once they are (so Ending D's chunk clusters never wait
  forever). NAME entries are saved under `traces.dat`'s `names` key, so a v1.3 jar still loads a file written now.
- `FogPlace` adds `timeAmount`, `spaceAmount`, `fadingBy` and `endedEarly`; `amount` takes a `double` clock. The cap
  in `addFogPlace` counts places not yet fading; at most `ClientEffects.FOG_PLACES_MAX` (16) are stored.
  `FogLimits.weatherAmount(spell, place, sky)` is the combine both sides use (`combine(spell * sky, place * sky)`).

## Features

Each feature: cards, tier and earliest stage, what the player experiences, why it is scary, how it stays deniable,
guardrails, rules, friends, config, build notes, cost and saves, tests, and how the verifier forces it. A card whose
fire "arms" counts in the director when it arms (like `the_screenshot`): the gaps, caps and tension are spent then,
whether or not the moment comes. Arms are kept by UUID: a short one (minutes of play) in memory, ending on
PARTICIPATION false; a long one (in-game nights, the world list) saved. Only the debug commands ever say an arm exists.

Common gates (every card and rule, unless it says otherwise): the overworld only; nothing once the story is quiet for
good (`Gates.quietForGood`), on path C, after `ending:ended` or `ending:d_complete`, or during `ending:last_minute`;
nothing while `director:obeyed_after_stop` is set (Ending A's false peace is real quiet); never within 30 s of the
target's combat; UUIDs across ticks (D-062).

---

### director

#### 01 Places remember (no card; `sections.sc01`)
- **Experience.** Nothing directly. The places where something happened to you are where things happen again: the east
  tunnel of your mine, the ridge where you saw him, the corner of your house where the chest opened. And the one place
  you spend the most time without anything ever happening (your crafting room, your farm) gets something, once.
- **Why scary.** Dread of places is conditioning. The player starts avoiding spots, and every return is tense because
  it is, in fact, more likely there. It is also the "smart system" Mariano asked for: it escalates with play, not spam.
- **Deniable.** Invisible. Players feel it as bad luck.
- **Rules.**
  - Memory per player (`PacingMemory.places`, at most `maxPlaces` 24, oldest out): one entry per real fire for them
    (not fake, not forced) and per felt fire at the sharer's own position: dimension, block pos, play tick, day, card
    tier, SIGHTING tag or not.
  - Weight: AMBIENT `weightAmbient` 0.15, MINOR `weightMinor` 0.3, MAJOR and SIGNATURE `weightMajor` 0.5, plus
    `weightSighting` 0.2 for a SIGHTING card, halved every `halfLifeDays` 4 in-game days.
  - Dread factor at the player's position: `min(maxFactor 1.8, 1 + Σ weights of places within radius 32)` (3D, same
    dimension).
  - The trusted place: dwell per chunk of the player's own play, sampled each director tick (at most 64 chunks, halved
    every hour of play). The chunk with the most dwell, at least `trustedMinHours` 1 h, with no remembered place within
    32 blocks of its centre, is trusted; while the player stands in it the factor is `trustedFactor` 1.6. A real fire
    there makes it a remembered place like any other (and so it stops being trusted until it decays).
  - The factor multiplies the ambient and minor per-hour rates in `DirectorBrain.due`, beside the fog factor, before
    the refractory wait (D-074's place). Traces and later only; never the first hour (no places yet), never a gap, cap,
    major slot or hard limit. `awayFactor` (1.0) multiplies the rate where the factor is exactly 1, for tuning.
- **Friends.** Each player's own places and dwell; a felt fire records at the sharer's own position.
- **Config `sc01` (v3; keys `a` to `n`).** `a` maxPlaces 24, `b` radius 32, `c` maxFactor 1.8, `d` halfLifeDays 4,
  `e` weightAmbient 0.15, `f` weightMinor 0.3, `g` weightMajor 0.5, `h` weightSighting 0.2, `i` trustedFactor 1.6,
  `j` trustedMinHours 1, `k` awayFactor 0.9 (1.0 in v1; SC-5's step lowers it where it is still 1.0, see "As built
  (SC-5)"), `l` simDreadShare 0.3, `m` simDreadFactor 1.4, `n` simTrustedShare 0.05. Step 2 to 3: the keys opaque.
  Version 4 (the second wave's 01+, docs/SELF_CREATED_2.md) adds `o` remainsFactor 1.5 and `p` simRemainsShare 0.03.
- **Build.** `director.sc.Places` (pure: weights, decay, factor, trusted chunk); `PacingMemory.places` and `dwell`
  (saved, optional; old `director.dat` loads empty); `DirectorBrain.Env.placeFactor()` (default 1.0; live env reads the
  player's position); `DirectorSim` models a dread share (`simDreadShare` of ticks at `simDreadFactor`,
  `simTrustedShare` at `trustedFactor`); `DirectorDebug` prints the factor, the nearest places and the trusted chunk.
- **Cost and saves.** One small list walk per decision; dwell map update once per director tick.
- **Tests.** Factor math (radius, dimension, decay, cap); a fire records for the target and a felt sharer at their own
  spots; fakes and forced fires don't; the trusted chunk needs 1 h and no place near; `due` multiplies only ambient
  and minor from Traces; first-hour cap and gaps unchanged; old `director.dat` loads; sims with the factor keep D-070's
  first-hour targets and Traces/Proximity rates within 15% of DESIGN 4b (else lower `awayFactor`).
- **Force it.** `/a1016 sc 01 [player]` prints the factor now, the places and the trusted chunk; `/a1016 sc 01 a`
  makes the chunk you stand in the trusted place for an hour.

---

### entity

#### 02 Nameless (card `sc_02`; AMBIENT, Traces; FOG + PERSONAL; WATCHER; no fake; `sections.sc02`)
Only with friends.
- **Experience.** Your friend went off to chop wood. At the edge of the fog, a figure stands still with no name over it.
  You ask where they are. They answer. You look again and the figure is walking, and the name is back. Or it is gone
  into the fog.
- **Why scary.** Canon: "no name over him". For twenty minutes you cannot tell your friend from him at a distance, and
  every real sighting after that becomes "was that you?".
- **Deniable.** Vanilla hides the names of sneaking players and of players behind blocks; fog; lag.
- **Guardrails.** D-082 (a vanilla UI element wrong). Client only; nothing the friend sees changes; never within 24.
- **Rules.**
  - Fits when another participant (not a spectator) is in the target's level `friendMinAtFire` 48 to `maxDistance` 64
    blocks away (outside the shared radius, so nobody feels a moment they don't see), the target is outdoors (sky
    light at the eyes 10 or more) and a dusk fog or a counting fog spell is on at the target
    (`ScreenshotRules.fogAmount` at least 0.1).
  - Fire arms the target's client for `armMinutes` 20 of play, or until it has hidden names for `hideSecondsTotal` 30.
  - While armed, the client does not draw the name tag of another player who is `minDistance` 24 to `maxDistance` 64
    blocks away, past `fogShare` 0.5 of the way from the visible fog start to its end, and has stood still (their
    client-side position moved under 0.1 blocks) for `stillSeconds` 1.5. They move, come inside 24 or out of the fog,
    and the name is back at once.
- **Friends.** That is the feature; never for a player alone.
- **Config `sc02` (v2; keys `a` to `g`).** `a` friendMinAtFire 48, `b` minDistance 24, `c` maxDistance 64, `d` fogShare
  0.5, `e` stillSeconds 1.5, `f` armMinutes 20, `g` hideSecondsTotal 30.
- **Build.** `entity.sc.NamelessCard`, arm memory like `TheScreenshot.LIVE`; payload `a1016_02:entity/sc02`
  (armed ticks left, or off); client `entity.client.NamelessClient` (still timers per remote player UUID) and mixin
  `entity.mixin.client.AvatarRendererMixin` on `AvatarRenderer.shouldShowName(AvatarlikeEntity, double)` returning
  false when `NamelessRules.hides(distance, fogStart, fogEnd, stillTicks, armed)` (pure).
- **Cost and saves.** A map lookup per remote player per frame; nothing saved.
- **Tests.** Card fits only with a friend 48 to 64 in fog outdoors; arm, run out, disarm on leave; the payload;
  `NamelessRules` (distance band, fog share, still time, total). A two-client checklist for the verifier (LAN).
- **Force it.** `/a1016 sc 02 arm [player] [minutes]` (skips the fit, keeps the client rules).

#### 03 His view (no card; rides a sighting; `sections.sc03`)
- **Experience.** Next time you open the world list, your world's picture has changed. It is small, so you lean in: it
  is you, seen from out in the fog, at night, standing by your house. From about where something stood that night.
- **Why scary.** A world file that is haunted. His point of view, outside the game, where you check your worlds.
- **Deniable.** It is 64 pixels; many will not notice for days. "Does the icon update?" (vanilla never re-takes it.)
- **Guardrails.** D-081. The frame is rendered from a camera that is not the player's and never presented.
- **Rules.**
  - An integrated server only (singleplayer or LAN host), for the host only (the server's singleplayer owner); once per
    world (`EntityData.hisViewDone`, set when the report comes).
  - From Proximity (`fromStage` 2). A surface sighting set up for the host (any card variant but the glimpse,
    LAST_ONE and the Nether and End ones), in the overworld, the figure within `maxBaseDistance` 96 of the host's base
    and the host within `hostBaseDistance` 48 of it, at night (13000 to 23000) or in a counting fog spell.
  - The server arms the host's client with that figure's entity id (payload `a1016_02:entity/sc03`). Once he has stood
    still `standSeconds` 1 on the client, the client renders one extra frame (never presented, the screenshot's way:
    `ScreenshotClient`'s extra-frame path) from his eyes toward the host's eyes, his own model left out of that frame
    and the host's model drawn as a detached camera would; normal fog, no Iris shaderpack (else no capture, arm ends).
    It grabs the frame, crops and scales it as vanilla's world icon (64 x 64), copies the world's current `icon.png`
    to `data/a1016_02/sc03_before.png` (only if that copy does not exist), writes the new `icon.png`, and reports
    (`a1016_02:entity/sc03_taken`). A frame that fails cleans up as the screenshot does and the arm waits for the next
    such sighting.
  - Ending D's afterward (`ending:d_complete`) puts `sc03_before.png` back as `icon.png` (server side, the world
    folder; once).
- **Friends.** Host only; a guest has no world list for this world.
- **Config `sc03` (v2; keys `a` to `e`).** `a` fromStage 2, `b` maxBaseDistance 96, `c` hostBaseDistance 48, `d`
  standSeconds 1.0, `e` iconSize 64.
- **Build.** Server `entity.sc.HisView` (arm on spawn, report, restore); client `entity.client.HisViewClient` and mixin
  `entity.mixin.client.CameraMixin` (override the camera's position and rotation for that one frame; detached).
  Section culling may lag for a camera 40 blocks off: render up to 3 unpresented frames, or turn smart culling off for
  them, whatever the dev client shows works. Icon path: the integrated server's `LevelResource.ICON_FILE`.
- **Cost and saves.** One arm per world; three frames once.
- **Tests.** The arm rules (host only, integrated only, once per world, stage, the two distances, night or fog, not
  the glimpse); the report sets the flag; the restore copies the file back once (test world folder); the pure camera
  math (eye position, yaw and pitch toward the host). Dev client checklist.
- **Force it.** `/a1016 sc 03 arm` (skips stage and distances for the next sighting), then
  `/a1016 entity spawn close` near your base at night; `/a1016 sc 03 reset` (forgets it, puts the old icon back).

---

### atmosphere

#### 04 An empty night (no card; a night roll like fog weather; `sections.sc04`)
- **Experience.** A clear night, and the moon is not in the sky. The stars are. Nothing hostile comes all night: no
  groan, no rattle, no spider. Your animals make no sound. Dawn comes. The next night the moon is back, one phase on.
- **Why scary.** The brightest thing at night is taken, and the noise of the night with it. He takes things; tonight
  he took the sky's light and everything that walks in the dark stayed away from you.
- **Deniable.** Vanilla has a new moon; quiet nights happen. Second look: last night it was full.
- **Guardrails.** None bent: the moon is switched while it is below the horizon.
- **Rules.**
  - Rolled by atmosphere's `FogWeather` right after tonight's night roll (`FogWeatherRules.NIGHT_ROLL`, 10000), only
    for a night with no fog spell over 13000 to 23000, only when the night's vanilla moon phase is FULL_MOON,
    WANING_GIBBOUS or WAXING_GIBBOUS (`brightPhasesOnly`), never in Alone, never within `minNightsApart` 6 nights of
    the last one, at most `maxPerWorld` 5. Chance `chanceByStage` {0, 0.2, 0.25, 0.25, 0.15}.
  - The sky: from 11000 of that day to 1000 of the next (the moon is below the horizon at both ends), clients draw
    `MoonPhase.NEW_MOON` in the overworld. Everyone online sees it (one sky).
  - From 12000 to 23500: no natural spawn of a `MobCategory.MONSTER` mob within `spawnRadius` 96 blocks (horizontal)
    of any participant in the overworld, no phantoms (`noPhantoms`), and passive animals within `quietRadius` 48 of a
    participant are silenced (`MobTamper.silence`, renewed every 10 s, ending at 23500). Mobs alive at dusk stay as
    they are, and are never silenced (a silent creeper would be an unmarked death).
  - A clock set back past the roll undoes it, as `FogWeather.Undo` does for fog.
- **Friends.** One sky for the world; the spawn rule around every participant.
- **Config `sc04` (v2; keys `a` to `h`).** `a` chanceByStage {0, 0.2, 0.25, 0.25, 0.15}, `b` minNightsApart 6, `c`
  maxPerWorld 5, `d` brightPhasesOnly true, `e` spawnRadius 96, `f` quietRadius 48, `g` quietAnimals true, `h`
  noPhantoms true.
- **Build.** `atmosphere.sc.EmptyNight` (pure roll and window; memory in `AtmosphereData`: nights rolled, last one,
  count; old files load with none); payload `a1016_02:atmosphere/sc04` (`Optional` from and until clock), sent on
  change and on join to everyone, spectators included; client mixin on the sky's extraction where vanilla fills
  `SkyRenderState.moonPhase` (`atmosphere.mixin.client.SkyMoonMixin`), pure `EmptyNight.moonFor(clock, window,
  vanilla)`; the spawn refusal beside dead mountains in `SpawnPlacementsMixin` (`EmptyNight.refusesSpawn`); phantoms in
  a mixin on `PhantomSpawner.tick` (returns 0 spawned during the window).
- **Cost and saves.** The spawn check is a radius test against at most a few participants; the silence pass is one
  entity query per participant every 10 s during the window.
- **Tests.** The roll (never a fog night, never Alone, bright phases only, spacing, cap); a monster spawn within 96
  refused in the window, allowed outside it and after; phantoms refused; animals within 48 silenced and released at
  23500; the payload on join; the window math; an undo on `/time set` back.
- **Force it.** `/a1016 sc 04 arm [force]` (tonight, or now if it is night; `force` skips fog and phase),
  `/a1016 sc 04 clear`, `/a1016 sc 04` (state and the next eligible night).

#### 05 Coming home (cards `sc_05` someone home: MINOR, Traces, SOUND + PERSONAL, VISITOR + WATCHER, fake; `sc_05b` they were waiting: AMBIENT, Traces, MOB, WATCHER + VISITOR, fake; `sections.sc05`)
- **Experience (sc_05).** You come back after a long time out. A few blocks from your door you hear someone inside:
  three or four steps on your own floor, walking away from the door, then one of your chests closing. Then nothing. You
  open the door. Nobody. Nothing moved.
- **Experience (sc_05b).** You come home and every animal in your pens, and your sitting pets inside, stands still
  facing one spot inside your house. Nothing is there. When you walk in, or look at the spot, they go back to grazing.
- **Why scary.** Your house is the one place that should be yours. Something was in it, or still is.
- **Deniable.** A mob inside? A sound glitch? Animals look around. Fakes: one step at half volume (sc_05); the animals
  face you instead (sc_05b).
- **Guardrails.** None bent. Sounds only (sc_05); `MobTamper` face and freeze, bound to the group (sc_05b).
- **Rules.**
  - Both cards fit while the target is away: at least `awayDistance` 48 from their base (`PlayerWatch.base`) for
    `awayMinutes` 10 of their play. Fire arms them for `armMinutes` 60 of play (one coming-home arm per player at a
    time; a second card waits).
  - The house (`atmosphere.sc.Home`, cheap, at most 13 x 7 x 13 cells): covered floor cells (a solid block within 6
    above) within `houseRadius` 6 of the base, at least `minCoveredCells` 4, and a hand door, trapdoor or gate a player
    placed within 8 of the base.
  - sc_05 plays when the target is `minDistance` 6 to `maxDistance` 16 from the door, coming closer, with every sound
    cell out of every player's view: `stepsMin` 3 to `stepsMax` 5 steps `stepTicks` 8 apart on interior floor cells,
    starting 2 to 3 cells inside the door and moving away from it, each the floor's own step (`SoundCues.stepType`,
    `SoundSource.PLAYERS`) at `stepVolume` 1.6 (heard to 25.6, so clear at 16); then after `closeDelayMin` 15 to
    `closeDelayMax` 30 ticks the close sound of the player-placed container inside nearest the last step
    (`ChestOpensCard.sounds`) at `closeVolume` 1.6. Alone: `SoundCues.alone(target, 48)` at the first sound; the rest
    stop if anyone comes within 48, opens the door or the target looks into the house.
  - sc_05b plays when the target comes within `approachDistance` 32 of the base: every animal and sitting pet within
    `mobRadius` 24 of the base not already tampered (at least `minMobs` 3) is frozen and faces the base's bed (or the
    base point) for up to `holdSecondsMax` 12, released when the target is within 3 of the spot, looks at it for
    `lookSeconds` 2 or the time runs out (`MobTamperImpl.bind` to the target and everyone sharing the moment).
- **Friends.** sc_05 is PERSONAL (a friend inside would know). sc_05b is a shared moment: friends arriving together see
  the same animals.
- **Config `sc05` (v2; keys `a` to `s`).** `a` awayDistance 48, `b` awayMinutes 10, `c` armMinutes 60, `d` houseRadius
  6, `e` minCoveredCells 4, `f` minDistance 6, `g` maxDistance 16, `h` stepsMin 3, `i` stepsMax 5, `j` stepTicks 8,
  `k` stepVolume 1.6, `l` closeDelayMin 15, `m` closeDelayMax 30, `n` closeVolume 1.6, `o` approachDistance 32, `p`
  mobRadius 24, `q` minMobs 3, `r` holdSecondsMax 12, `s` lookSeconds 2.
- **Build.** `atmosphere.card.SomeoneHomeCard`, `TheyWereWaitingCard`, `atmosphere.sc.Home`, an approach watcher in
  atmosphere's tick (every 10 ticks, armed players only); episodes like `EchoCard`'s; `AnimalsFaceFogCard`'s facing.
- **Cost and saves.** Distance checks for armed players only; nothing saved but the away timers (memory).
- **Tests.** Fits only after 10 minutes away; arm runs out; steps inside on the floor's own sound, moving away from the
  door, then the nearest container's own close; out of view; a friend within 48 or a spectator stops it; looking in
  stops it; the fake is one quieter step. sc_05b freezes and faces every animal in range, releases on approach, look or
  time, never a tampered mob, bound to the group; its fake faces the player.
- **Force it.** `/a1016 sc 05 arm [b] [fake] [player]` (skips the 10 minutes away; the distance band and view rules
  stay), then walk away 20 blocks and come back.

#### 06 In step (card `sc_06`; AMBIENT, Alone; SOUND + PERSONAL; WATCHER; fake; `sections.sc06`)
- **Experience.** Walking alone across open ground at night, or in the fog: after a few seconds a second set of steps
  joins yours, a few blocks behind, on the same ground, a beat late, in step with you. Stop, and it takes one more step.
  Turn round: the fog, nothing.
- **Why scary.** Being followed in the open. It is your own sound, so you doubt yourself, and the open is no longer
  safe at night.
- **Deniable.** "My own footstep echoing." Fake: only your own step once more after you stop.
- **Guardrails.** None bent. Sounds only.
- **Rules.**
  - On the surface (sky light at the feet 10 or more), at night (13000 to 23000) or with a dusk fog or counting fog
    spell on at the player, on foot on the ground (not riding, swimming, flying or gliding), walking now (moved 2+ blocks
    in the last 3 s), alone (`SoundCues.alone(player, 48)`), once per in-game night per player.
  - Fire starts the episode: after `walkSecondsBeforeStart` 3 s more of walking, each of the player's own step sounds
    (server side: `Entity.playStepSound` for that player, see Build) is answered `lagTicksMin` 4 to `lagTicksMax` 6
    ticks later by one step `behindMin` 6 to `behindMax` 9 blocks behind them along their movement, on the ground there,
    the floor's own step (`SoundCues.stepType`), `SoundSource.PLAYERS`, at `stepVolume` 1.2, for `stepsMin` 6 to
    `stepsMax` 14 steps. When the player stops (no step for 10 ticks), one last step, then the end. Sneaking makes no
    step, so the follower is silent too. It ends at once if the player looks within `lookBackDegrees` 70 of behind,
    anyone comes within 48, or they leave the ground.
- **Friends.** PERSONAL.
- **Config `sc06` (v2; keys `a` to `i`).** `a` walkSecondsBeforeStart 3, `b` behindMin 6, `c` behindMax 9, `d`
  lagTicksMin 4, `e` lagTicksMax 6, `f` stepsMin 6, `g` stepsMax 14, `h` stepVolume 1.2, `i` lookBackDegrees 70.
- **Build.** `atmosphere.card.InStepCard`; common mixin `atmosphere.mixin.EntityStepMixin` at `Entity.playStepSound`
  (reached through `walkingStepSound` from `applyMovementEmissionAndPlaySound`; check on 26.3 that the server reaches
  it for a player's own movement, else count the walk distance the server sees, as vanilla's `moveDist` and
  `nextStep` do), a UUID map lookup only for players with an episode.
- **Cost and saves.** A map lookup per step of players with an episode; nothing saved but the night (memory per UUID).
- **Tests.** Each own step is answered with the lag, behind, on the ground's own sound; looking back ends it; one more
  after a stop; no follower while sneaking; underground, daylight without fog, riding refuse; a spectator within 48
  refuses; once per night; the fake is the late own step.
- **Force it.** `/a1016 sc 06 arm [fake] [force]` (the next 2 minutes; `force` skips night and fog), then walk.

#### 07 The name is gone (card `sc_07`; MINOR, Proximity; MOB; COLLECTOR + STRIPPER; no fake; `sections.sc07`)
- **Experience.** The dog you named is just a wolf again. No name over it. The name tag is not on the ground. Nothing
  else changed.
- **Why scary.** He has no name. Now he takes the names of the things you love. Canon: "no name over him", turned
  around.
- **Deniable.** Barely, and that is fine: it is quiet, and the first time a player with several pets wonders if they
  ever named that one.
- **Guardrails.** A removal (D-086), ledgered; Ending D gives the names back.
- **Rules.** A mob with a custom name within `baseRadius` 64 of a known base in the target's level (prefer a pet the
  target owns, then the named mob nearest the target's base), not riding or ridden, not tampered, its box out of every
  player's view, nobody within `nobodyWithin` 16 of it, the target at least `awayDistance` 48 from it or asleep; never
  a mob whose owner is online within 48 of it. `MobTamper.unname(mob, "atmosphere:sc07")`. At most `maxPerWorld` 3,
  `minDaysApart` 3 in-game days (world-wide, saved in the tick it happens).
- **Friends.** One world record; a friend's pet only when the friend is away too.
- **Config `sc07` (v2; keys `a` to `e`).** `a` baseRadius 64, `b` nobodyWithin 16, `c` awayDistance 48, `d` maxPerWorld
  3, `e` minDaysApart 3.
- **Build.** `atmosphere.card.NameGoneCard`; `MobTamperImpl.unname` (clears `CustomName` and `CustomNameVisible`,
  calls `TraceLedger.recordName`).
- **Cost and saves.** One entity query near the base per look; count and day in `AtmosphereData`.
- **Tests.** Unnames an out-of-view named pet near the base and ledgers it; refuses in view, someone near, the target
  near, the owner near; prefers the target's pet; the caps; core undo restores it.
- **Force it.** `/a1016 fire sc_07`; `/a1016 sc 07` lists the candidates and why each is refused.

#### Fog places, client side (for 10)
- `ClientAtmosphere.applyFog` adds core's place amount at the camera (`FogLimits.placeAmount`, weighted by the camera's
  smoothed sky weight like the spell) into the same combine and the same tint as the spell; `WeatherFog` keeps the
  places from the payload and `Sync`, reset on disconnect. Continuous in every input (no pop at a place's edge or ends).
- Tests: pure `WeatherFog` tests (a place adds nothing outside its radius or time, grows toward its centre, combines
  with a spell); server and client `FogLimits` agree with a place.

#### As built (SC-1, `SelfCreatedAtmosphereTests` with its half `SelfCreatedAtmosphereNightTests`, 25 tests)
- **Wiring.** `atmosphere.sc.AtmosphereSc` (from `AtmosphereInit`/`AtmosphereServer`): the four sections (`Sc04Config`
  to `Sc07Config`, v1), the payload, `/a1016 sc 04|05|06|07` (`ScCommands`; no `executes` on `sc` itself, SC-5's
  index), the reverts, the ticks. Common gates in `ScGates` (switch, the six stop flags, overworld, 30 s of combat); a
  forced fire skips a card's fit, never the story or the overworld. Debug lines go only to the op (`ScDebug`).
- **04** (`sc.EmptyNight`, `EmptyNightPayload`). Rolled by `FogWeather.check` right after tonight's fog roll, only while
  the clock is before 11000 of that day (a late fog roll gives no night), with the phase read from vanilla's
  `MOON_PHASE` attribute (on the default timeline, phase index = clock day mod 8; a test pins it). A forced night counts
  like a rolled one. The quiet is `MobCategory.CREATURE` only, `MobTamper.silence` for 220 ticks every 200, capped at
  23500, never on a mob something else holds. `PhantomSpawnerMixin` also holds `VillageSiege` (both are
  `CustomSpawner.tick`); the spawn rule reads a snapshot (window and participants) refreshed every second. Memory
  `sc04` in `atmosphere.dat` (lenient, not written at start).
- **05** (`card.ComingHome`, `SomeoneHomeCard`, `TheyWereWaitingCard`, `sc.Home`). Away timers every 20 ticks; a
  base's survey (house, animals) cached 600 ticks, taken only while loaded, and the cards fit only on a survey. The
  band plays only after the player was out of it (past `maxDistance` of the door, or past 30 of the base) and is 0.2
  closer than 10 ticks before. The walk starts 2 to 3 in (nearer in a shallow house) and never steps back toward the
  door; the close plays only with a player-placed chest, barrel, shulker box or ender chest in the house's box. The
  animals' spot is the base block's centre (a bed respawn is the bed's block); sitting pets are tame ones ordered to
  sit; they go at 3 blocks, a look within 20 degrees for `lookSeconds`, or `holdSecondsMax`. Too few animals keeps the
  arm (leave and come back).
- **06** (`sc.InStep`, `InStepCard`, `EntityStepMixin` at the head of vanilla's private `Entity.walkingStepSound`,
  which the server reaches for a player's own move packets; sneaking has no movement emission, tested). Fog is a dusk
  fog of 0.1 or more or a counting spell; the night key is the clock's 24000-tick period from 12000; walking is 2 blocks
  over 60 ticks by 10-tick samples; a fired episode waits 2 minutes for its walk. The ground behind is the highest
  walkable cell 2 above to 4 below; leaving the ground is no longer on foot or off the surface.
- **07** (`NameGoneCard`, `MobTamperImpl.unname`). Known bases are `world.Bases.all`; the owner is `OwnableEntity`'s;
  a forced fire skips the world's caps. Record `sc07` in `atmosphere.dat`.
- **Fog places** (`WeatherFog.frame(..., dimension, camera)`): the place amount at the camera joins the spell in
  `FogLimits.weatherAmount`, so the haze and tint follow; none while this client's switch is off.
- **Reverts.** sc04 drops the night (count kept), tells everyone, clears the snapshot, lets its quiet animals go; sc05
  drops arms and timers and lets held animals go; sc06 ends the episodes; sc07 gives names back through the ledger's
  undo, retried every 30 s while the switch stays off.
- **Client check.** Dev client launched and joined with the new client mixins (no visual pass of the moon yet).

#### Fixed in review (FIX-SC-D: R8 L5, R8 L6's Iris part)
- **10's fog place seen from outside (R8 L5).** The place's amount was only taken at the camera, so from outside its
  radius (from the house) nothing showed. A place is now also seen along the view. Core (pure, both sides):
  `FogLimits.placeViews(places, dim, eye, yaw, clock)`: per place giving fog, the line of sight within
  `VIEW_CONE_DEGREES` 20 of the yaw that passes nearest its centre; when that passes inside the radius and ahead of the
  eye, `PlaceView(near, amount)`, `near` where it enters the place (0 inside) and `amount` all the places' amount at
  that nearest point; and `FogLimits.seen(eye, views, ...)`: the nearest of the eye's fog end and each view's `near`
  plus the fog end with its amount in place of the eye's (as if one stood where the view enters it). The client
  (`WeatherFog.seen` along the camera's yaw, `WeatherFog.shape(..., seen, scratch, cfg)`, `Curves.Fog.seen`) shapes the
  same fog with each view's amount and moves only the render-distance fog and the sky's end in to it, `near` blocks
  further out, wherever nearer: nothing nearer than the place gets more fog, the fog stands over the spot and hides
  what is behind it; inside, the view toward the centre is the thicker, and looking away from it inside is exactly as
  before. Sky-weighted like the rest (none in a tunnel or a closed house). The frame's color follows a seen place by
  `1 - near / cameraEnd` (`WeatherFog.tintAmount`), so at night it reads as a pale wall. The server's
  `FogLimits.of(player)` takes the same views along the player's yaw, so the figure's band and the screenshot's fog end
  are the drawn end (test: every stage, view distance, yaw and place time, with a spell). Numbers (16 chunks, noon,
  the place at full, its centre 100 off): clear to 110, complete at 148 (its far side 140); whole within 20 degrees of
  it, thinning out to nothing by about 43; walking in toward it the fog end comes in a block per block, never back, no
  pop at the radius. Dev client (1708 x 960, 16 chunks, 50 above the ground): looking at the place from 100 off at
  noon, the water and the island behind it are gone in fog where they showed without it; 50 degrees aside the view is
  the plain one; from 60 off thicker; at midnight a faint pale wall that way where the plain night is dark. Tests
  `aFogPlaceIsSeenFromOutsideItAsAFogStandingOverIt`, `theSeenFogPlaceIsTheServersFogEndAndHasNoPop`.
- **04 under an Iris shaderpack (R8 L6).** A pack lights and draws the night from the game's own phase (its uniform
  reads the `MOON_PHASE` attribute, not `SkyRenderState`), so the new moon would hang over a full moon's light. While a
  pack is in use (`atmosphere.client.ShaderPacks`: Iris's API by reflection, as the screenshot's `IrisShaders`; unknown
  counts as in use) the sky keeps vanilla's moon (`EmptyNight.moonFor(clock, window, vanilla, shaderPack)`); the
  night's quiet stays. Test `theSkyKeepsVanillasMoonWhileAShaderpackDrawsTheNight`. Not run with Iris (not in the dev
  setup).

---

### world

#### 08 Turned (card `sc_08`; MINOR, Traces; SCAR; VISITOR + COLLECTOR; no fake; `sections.sc08`)
- **Experience.** Back home, one thing faces the wrong way. Your furnace. Next time a chest, a sign by the door, the
  jack o'lantern. Later you notice they all face the same way: where you sleep.
- **Why scary.** Your things are watching your bed.
- **Deniable.** "Did I place it like that?" Placement facing is easy to forget.
- **Guardrails.** A state change of an existing block (`TraceService.convert`), ledgered; Ending D turns them back.
- **Rules.**
  - Player-placed blocks (`placedNear(base, baseRadius 12, ...)`) with a horizontal facing: furnace, smoker, blast
    furnace, single chest (never a double half), barrel (horizontal only), carved pumpkin, jack o'lantern, lectern,
    loom, stonecutter, beehive; and standing signs, banners and heads by rotation (`signs` true). Never a redstone part
    (dispenser, dropper, observer, piston, hopper, repeater, comparator, crafter), a bed, a door, stairs or rails.
  - The new facing points at the base's bed (the respawn point; else the base point); already facing it: another.
  - Out of every player's view, nobody within `nobodyWithin` 16, the target at least `awayDistance` 32 away or asleep.
    `perFireTraces` 1 block in Traces, `perFireLater` 2 from Proximity, `minDaysApart` 2 per base, `maxPerBase` 8.
- **Friends.** The target's base; never a block within 16 of anyone.
- **Config `sc08` (v2; keys `a` to `h`).** `a` baseRadius 12, `b` nobodyWithin 16, `c` awayDistance 32, `d`
  perFireTraces 1, `e` perFireLater 2, `f` minDaysApart 2, `g` maxPerBase 8, `h` signs true.
- **Build.** `world.sc.Turned` + card in `WorldInit`; `world.Bases` for the base and bed; one `TraceService.batch`
  (`world:sc08`).
- **Tests.** Turns a placed furnace to face the bed (ledgered CONVERT); never a double chest or a redstone part; a sign
  by rotation; refuses in view or near; Proximity turns two; undo turns it back.
- **Force it.** `/a1016 fire sc_08`; `/a1016 sc 08` lists the candidates and refusals.

#### 09 Tracks (card `sc_09`; MINOR, Traces; SCAR; VISITOR + WATCHER; fake; `sections.sc09`)
- **Experience.** In the morning a line of footprints crosses the grass to your door: single blocks of bare dirt, two
  apart, left, right, coming from out there, from the fog side, stopping at your door. In snow, the snow is gone in
  prints. Across your farm, the farmland is trampled to dirt in a line and those crops are gone. Later, the prints go
  all the way round your house, close to the walls, and back out the way they came.
- **Why scary.** Someone walked up to your door while you slept, and around your house.
- **Deniable.** Animals trample farmland; dirt patches. Second look: in a line, two apart, to the door. Fake: three to
  five prints in a curve near the animals, like a cow's.
- **Guardrails.** Removals (grass, snow, crops, plants); vanilla grass grows back over the dirt within days: the clue
  fades (D-052).
- **Rules.**
  - The target's base with a hand door a player placed within 8 of the base; the path ends on the ground cell outside it.
  - It starts `lengthMin` 14 to `lengthMax` 28 blocks out on the open side (away from the house, toward the nearest
    open ground or treeline) and comes straight in with a wander of one block every 4; a print every `stride` 2 cells,
    alternating one block left and right of the line.
  - A print (`world.sc.Prints`, shared with 10): grass, podzol or mycelium to dirt; the top snow layer removed; farmland
    to dirt with its crop removed; short grass, ferns, flowers and leaf litter on it removed. Stone, sand and gravel take
    no print (the stride goes on). Fewer than `minPrints` 5 prints: NO_SPOT.
  - From Proximity, at `circleChance` 0.4, the prints go round the house 1 to 2 blocks from its walls once and back out
    parallel to the way in, 2 blocks to its side.
  - One `TraceService.batch` (`world:sc09`), all out of view, nobody within `nobodyWithin` 16 of a print, the target
    at least `awayDistance` 32 from the door, asleep, or inside the house. `minDaysApart` 2 per base, `maxPerBase` 4.
- **Friends.** The target's base; nobody near any print.
- **Config `sc09` (v2; keys `a` to `i`).** `a` lengthMin 14, `b` lengthMax 28, `c` stride 2, `d` minPrints 5, `e`
  circleChance 0.4, `f` nobodyWithin 16, `g` awayDistance 32, `h` minDaysApart 2, `i` maxPerBase 4.
- **Build.** `world.sc.Prints` (pure plan: which edit a surface cell takes), `world.sc.Tracks` + card; `LiveTerrain` for
  surface tops.
- **Tests.** The path ends at the door's outside cell; prints alternate; each surface's print; stone skipped; refused in
  view; all or nothing; the loop goes round the house; the fake is short and curved.
- **Force it.** `/a1016 fire sc_09 [fake]`; `/a1016 sc 09 b` (the loop).

#### 10 Where he stood (no card; on `FIGURE_LEFT`; `sections.sc10`)
- **Experience.** After you see him, if you walk out to where he stood: the grass under his feet is bare dirt in two
  prints, the flowers and grass around it gone in a small circle; in snow, two footprints. Sometimes a line of prints
  where he went. And for a day or two the fog sits over that spot: walk toward it and it gets thicker, whatever the
  weather. You can see it from your house.
- **Why scary.** He was real, and the places he stood become places you can see from far away and don't want to go.
- **Deniable.** Dirt patches are common; fog drifts.
- **Guardrails.** Removals, out of view; fog places are weather (D-085).
- **Rules.**
  - On `FIGURE_LEFT` in the overworld, on the surface (sky light at his feet 10 or more), `seen` true, any variant but
    the glimpse, LAST_ONE and the Nether and End ones.
  - Prints at `standChance` 0.7: the cell under his feet and the one beside it across his facing, plus short grass,
    flowers and ferns within `clearRadius` 2; at `runChance` 0.35 also a print every 2 cells along `path`, at most
    `maxRunPrints` 12. Queued and tried every 5 s until all out of view (he left at a distance and the player is
    looking that way), dropped after `retryMinutes` 10. Cause `world:sc10`.
  - The fog place at `fogChance` 0.6: `ClientEffects.addFogPlace` at `stoodAt`, radius `fogRadius` 40, level
    `fogLevel` 0.45, ramp and fade `fogRampTicks` 1200, for `fogDaysMin` 1 to `fogDaysMax` 3 in-game days, at most
    `maxFogPlaces` 4 at once. Ending D's last minute takes them with the rest of the fog: each fades out over its own
    fade, and none comes again (review R9).
- **Friends.** World-wide like weather; everyone sees the place.
- **Config `sc10` (v2; keys `a` to `l`).** `a` standChance 0.7, `b` runChance 0.35, `c` clearRadius 2, `d`
  maxRunPrints 12, `e` retryMinutes 10, `f` fogChance 0.6, `g` fogRadius 40, `h` fogLevel 0.45, `i` fogRampTicks 1200,
  `j` fogDaysMin 1, `k` fogDaysMax 3, `l` maxFogPlaces 4.
- **Build.** `world.sc.WhereHeStood` (listener, pending list in memory: losing it on a restart is fine), `Prints` from
  09. Entity's one call is in SC-4; atmosphere's render is in SC-1.
- **Tests.** A synthetic event gives prints at the spot and the cleared plants, only once out of view, dropped after 10
  minutes; run prints along the path; the fog place with the configured numbers, capped at 4; nothing underground, in
  the Nether, unseen, or for the glimpse.
- **Force it.** `/a1016 sc 10 here` (as if he stood, seen, on the block you look at); or a real sighting with
  `/a1016 fire sighting_cow`.

#### 11 t0rch's torches (card `sc_11`; MINOR, Traces; LIGHT + SCAR; MOURNER + CARVER; no fake; once per world; `sections.sc11`)
- **Experience.** In a cave you are exploring for the first time, there are torches already. On the right-hand wall,
  every ten blocks or so, going deeper, into a branch you have not been down. You follow them. A few steps before the
  end, on the wall, a sign: "brb". They end in a dead end at a 1x1 hole straight down in the floor, a torch on the
  floor before it. Far down, the dark. No torches come back out. (FIX-SC-FINAL: whoever falls in lands in water at the
  bottom, t0rch's own landing; the lava is under its floor.)
- **Why scary.** "This is singleplayer." Someone was here before you and did not come back. The list says
  "t0rch . lava" (F23; F06 "t0rch . gone"). After this, every cave might have someone's torches in it.
- **Deniable.** It reads as an old player's mine, a used world. Alone it names nobody (guardrail 8).
- **Guardrails.** Left by others (D-088): torches, the shaft, its water and the sign are a person's,
  `world:left/t0rch/...`, never his trace, never undone. Never an unmarked death (guardrail 1; see the rules). "brb" is human text, lowercase.
- **Rules.**
  - From a cave spot a participant stood in (world's `LoneTorch.trackCaves` spots), a natural cave branch nobody visited
    (chunks with `lastVisitDay` -1), at least `minDepthBelowSurface` 12 under the surface: walk natural cave air (not
    player-dug, walkable floor, bounded at 6000 cells) `pathMin` 30 to `pathMax` 90 cells to a floor cell under which
    natural stone runs straight down `shaftMin` 8 to `shaftMax` 30 blocks to lava (a source within 3 below the
    column's end, or a lava lake). Never within `baseClearance` 64 of any known base, nobody within `nobodyWithin` 32,
    all out of view, loaded, not protected.
  - Never an unmarked death (DESIGN guardrail 1, FIX-SC-FINAL). The hole is at a dead end only the trail reaches: from
    the sign's walk cell on, every cell of the walk to the hole is walled in at its feet and head on every side but the
    walk's, and roofed (`TorchTrail.enclosedFrom`), so nobody comes on it from the cave's far side, from above or from
    beside it, and whoever reaches it passed the sign. The fall is survived: the column's last stone stays as the
    shaft's floor over the lava and the lowest dug cell holds a water source (vanilla asks for water before a fall's
    damage, so one block of it saves any height). From above it is the same dark 1x1 drop.
  - Leave wall torches on the right-hand wall going in (from the known spot), every `spacingMin` 8 to `spacingMax` 12
    path cells, the first within 6 cells of the branch mouth (seen from the known cave); dig the shaft down to the
    column's last stone and leave water at its foot (`world:left/t0rch/shaft`); a floor torch beside the hole (at a
    dead end, the cell before it); an oak wall sign 2 to 5 cells before the hole (never read from the hole's lip) with
    line 1 "brb", not waxed (`world:left/t0rch/sign`). Text from
    `data/a1016_02/world/sc/t0rch.json`, word for word.
  - Once per world (`maxPerWorld` 1, saved in the tick it is left). Not a site in `SiteRegistry` (lore must not fill it).
- **Friends.** World-wide once.
- **Config `sc11` (v2; keys `a` to `j`).** `a` pathMin 30, `b` pathMax 90, `c` spacingMin 8, `d` spacingMax 12, `e`
  shaftMin 8, `f` shaftMax 30, `g` minDepthBelowSurface 12, `h` nobodyWithin 32, `i` baseClearance 64, `j` maxPerWorld
  1.
- **Build.** `world.sc.TorchTrail` + card; `LoneTorch`'s cave spots; `TraceService.leave` and `remove` with left-by-others
  causes; one batch.
- **Cost and saves.** One bounded search per fire; the spot in `WorldData`.
- **Tests.** In a built cave corridor over a lava pocket: torches on the right wall at the spacing, the shaft goes down
  to its floor over the lava with water at its foot, the sign reads "brb", every cause is left by others; once per
  world; refused near a base, in a player-dug tunnel, without lava in reach, in view; the hole reached from nowhere
  but the trail through the sign's cell, and an end open beside, past or over the hole, or beside the cell before it,
  refused; a cow that falls in is unhurt in the water while the same dry fall hurts.
- **Force it.** `/a1016 sc 11 here` (from the cave spot nearest you, the visit rule skipped, view rules kept);
  `/a1016 fire sc_11`.

As built (SC-3, `SelfCreatedWorldTests` and `SelfCreatedWorldTrailTests`, structure `world/sc/cave`):
- **Code.** `world.sc`: `ScGates` (the common gates), `ScEnv` (live or a test's own players, state, data, view and fog),
  `ScMarks` (in `world.dat` under the optional, lenient key `sc`: per base its turned and tracks counts with their last
  day, and the trails left), `Prints`, `Turned`, `Tracks`, `WhereHeStood`, `TorchTrail` (each with its `Config`,
  sections `sc08` to `sc11` v1, and its `Card`), `WorldSc` (init, reverts) and `ScCommands` (`/a1016 sc 08`, `09
  [circle]`, `10 [here]`, `11 [here|reset]`). Common gates: the switch, every story-quiet flag
  (`ScGates.QUIET_FLAGS`, with `ending:last_minute` and `director:obeyed_after_stop`), the overworld, 30 s of the
  target's combat. A forced fire skips the combat gate, the days apart and the target's own distance; never a cap,
  `nobodyWithin` or the view.
- **08.** The facing points at the base cell (the respawn point, a bed when it is one). Signs and banners face their
  rotation, a head the other way (vanilla places it by the player's own yaw). An asleep target is not counted in
  `nobodyWithin`.
- **09.** Each column's ground is `Prints.ground` near the last one found (`LiveTerrain` reads a roof over the door).
  The open side: the door's outward way turned 0, ±25 or ±50 degrees, the turn with the most prints kept. A print
  alternates between the line's cell and the one to its right. The house is the covered cells you can walk through,
  flood-filled from inside the door (at most 8 from the base); the loop runs 2 cells out from their box, alternating
  with 3. An asleep target or one inside is not counted in `nobodyWithin`. Snow loses one layer (a single layer goes).
  The fake needs an animal within 32 of the base and is not counted for the base.
- **10.** Also only for a figure that appeared for a known player of the story (nobody's, or a test's own player's,
  leaves nothing). Stand, plants and run go in one batch; a stand column not loaded waits, run columns not loaded are
  left out. `sc 10 here` forces every roll.
- **11.** One batch, cause `world:left/t0rch/shaft`: only the shaft is ledgered (the torches and the sign are left, so
  the batch's cause is theirs). Natural stone is `#base_stone_overworld` and the ore replaceables, never an ore; the
  shaft's sides solid and dry. The mouth is the walk's first cell in a never-visited chunk, every later cell too.
  Torches at head height (else the feet) on the first wall up to 4 cells right; the last window before the hole may
  stay dark. The sign at head height over a walk cell 2 to 5 before the hole, on the left-hand wall (else the right,
  else one ahead), its column and the one it faces at least 2 from the hole's (review R9: it used to hang over the
  hole, read from the lip); the floor torch beside the hole (else the cell before). A fire
  tries the 4 nearest loaded cave spots within 160. `sc 11 reset` forgets the record (the trail stays).
- **Switched off.** sc09 and sc10 give back through `TraceService.undo` what is loaded now (sc10 also drops its queue;
  core clears the fog places), with another pass while a pass gave something back and something was blocked (a tall
  plant's upper half after its lower); what waits (in view, not loaded) is tried again every 30 s while the layer stays
  off, 08's too (review R7); sc11 nothing (left by others). sc08 turns back by itself (`WorldSc.turnBack`: a block
  still that block with only its facing changed, both entries then leave the ledger), because core's CONVERT undo
  answers BLOCKED for every player-placed block ("built since"), so **Ending D never turns 08 back** until core blocks
  only a block that changed since (core ask; done in SC-5: core's undo now takes a conversion back while the block is
  exactly what it left, Ending D turns 08 back, and `turnBack` goes through core's undo for such entries).

---

### dig

#### 12 Not safe to rest (card `sc_12`; MINOR, Proximity; DIG; CARVER + WATCHER; no fake; `sections.sc12`)
- **Experience.** At night you lie down in your own bed and the game says, as it does: "You may not rest now; there are
  monsters nearby". Nothing is around: nothing in the house, nothing outside. You try again and you sleep. Days later,
  digging under the house, you find a 2x2 tunnel and a shaft that stops one block under your bed.
- **Why scary.** The game admits something is there. And it is: the payoff is the Under-you network you find later.
- **Deniable.** Vanilla checks a box around the bed for monsters, caves under the floor included: "a zombie in a cave".
- **Guardrails.** D-082 (vanilla's message lies, but it is true in the story); ARCH 2 bent once (a refused attempt, the
  next sleeps).
- **Rules.**
  - Fits only when the target's respawn point is a bed in the overworld and the Under-you network under that base has
    its shaft done with its top within `shaftBelowMax` 2 blocks under the bed (`Network.shaftDone`, `shaftTop`). The
    monster is real (D-089).
  - Fire arms the target for `armNights` 3 in-game nights (`GameClock.dayTicks`). While armed, their next attempt to
    sleep in that bed that vanilla would allow (night or thunder, near, not obstructed, and vanilla's own monster check
    finds none) is refused with `BedSleepingProblem.NOT_SAFE`, vanilla's message where vanilla shows it. The arm is
    spent; the next attempt is vanilla's. At most `maxPerWorld` 3. The bed is matched by either half (FIX-SC-FINAL:
    the respawn point may be the foot while vanilla's attempt names the head, `RestRefusal.sameBed`,
    `Network.isBed`).
- **Friends.** Everyone whose bed is within `sharedBedRadius` 8 of that bed is refused the same way for
  `sharedSeconds` 20 after the first refusal (one monster would refuse them all).
- **Config `sc12` (v2; keys `a` to `e`).** `a` shaftBelowMax 2, `b` armNights 3, `c` sharedBedRadius 8, `d`
  sharedSeconds 20, `e` maxPerWorld 3.
- **Build.** `dig.sc.RestRefusal` + card; mixin `dig.mixin.ServerPlayerSleepMixin` on `ServerPlayer.startSleepInBed`
  at vanilla's monster check (make the list of monsters read as not empty while armed, so vanilla returns NOT_SAFE
  itself); arm in `DigData` per UUID (until, bed pos, count; old files load with none).
- **Tests.** Armed, shaft done, night: NOT_SAFE once, then sleep; no shaft: the card does not fit; a real monster:
  vanilla's refusal, arm kept; a friend's bed within 8 refused within 20 s; the arm runs out after 3 nights.
- **Force it.** `/a1016 sc 12 arm [force] [player]` (`force` skips the shaft); grow the network with `/a1016 dig network
  grow <nights>`.

#### 13 The way back (card `sc_13`; MAJOR, Proximity; DIG + PERSONAL; CARVER + STRIPPER; no fake; `sections.sc13`)
- **Experience.** Deep in a natural cave you lit on the way in, you turn back. Your torch trail leads to a stone wall
  where the passage was. Two blocks of the cave's own stone close it. Beside it, a hollow in the wall that was not
  there. Dig through: the passage is right behind, and your next torch.
- **Why scary.** The way you came is gone. Caves become places you may not get out of.
- **Deniable.** Caves are confusing: "I came another way". The torches leading into the wall are the second look.
- **Guardrails.** Moves only (the hollow is the clue), ledgered; Ending D puts them back.
- **Rules.**
  - Underground, the player has walked at least `pastBlocks` 40 path blocks past the point. The point is on their own
    trail (a ring of positions sampled every `trailSampleTicks` 20 underground, `trailLength` 96), in natural cave (not
    `wasDugByPlayer`), a passage exactly 1 wide and 2 high, with the same natural stone in a side wall (2 blocks, solid
    behind them, so the hollow opens nowhere), a torch a player placed between the point and the player, and one within
    `nextTorchWithin` 6 beyond it.
  - The player carries a pickaxe. Alone (`Ladder.alone`), nobody within `nobodyWithin` 16 of the point, the four cells
    out of view; one `TraceService.batch` (`dig:sc13`) of two moves. `maxPerWorld` 1.
- **Friends.** PERSONAL.
- **Config `sc13` (v2; keys `a` to `f`).** `a` pastBlocks 40, `b` trailSampleTicks 20, `c` trailLength 96, `d`
  nextTorchWithin 6, `e` nobodyWithin 16, `f` maxPerWorld 1.
- **Build.** `dig.sc.WayBack` + card; the trail ring in `DigTicker` (underground participants only); `Blind.isBlindLight`
  and `wasPlacedByPlayer` for the torches.
- **Tests.** A built 1x2 corridor with torches: after 40 past, it closes with the wall's own blocks and leaves the
  hollow, ledgered MOVE; refused in view, in a dug tunnel, without a pickaxe, with a friend within 48, in a 2-wide
  passage; undo opens it.
- **Force it.** `/a1016 sc 13 arm [player]` (within 10 minutes, the first point that fits); `/a1016 sc 13` prints the
  best point and why the others fail.

---

### lore

#### 14 The record (card `sc_14`, MINOR, Traces, TEXT + UNSHARED, WATCHER + VISITOR, no fake; and three rules; `sections.sc14`)
The game's own records lie, once each (D-081).
- **(a) Last played (the card).** Fire arms the world. The next time the host opens the world list after at least
  `gapHours` 6 real hours away, the world says it was last played at a time between when they quit and now: at night,
  local time `nightFrom` 01:00 to `nightTo` 04:30, if the gap holds one, else the middle of the gap. Not when they
  played. Open the world and it is right again (vanilla writes the real time).
- **(b) The version (a rule).** From Telling (`versionFromStage` 3), at least `versionAfterDays` 2 in-game days after (a)
  was shown, and the host quit at night (in-game 13000 to 23000): the next time the list shows the world, its version
  reads `a1.0.16_02`, and Play asks vanilla's own question, "Do you really want to load this world?", "This world was
  last played in version a1.0.16_02; you are on version 26.3. Please make a backup in case you experience world
  corruption.", with vanilla's buttons doing what they do. Once; the next list says 26.3. A hardcore world whose story
  ended with the host's line finished (`ending:line_finished=<host>`) reads `a1.0.16_02` for good (no prompt).
- **(c) The seed (a rule).** From Proximity (`seedFromStage` 2), the first `/seed` anyone runs answers vanilla's
  "Seed: [478868574082066804]" (the canon seed, clickable to copy as vanilla's). Once per world; every later `/seed` is
  the real one.
- **(d) The statistics (a rule).** Every block he removes that a player placed counts in "Times Mined" for that block,
  and every stack he takes from a container counts in "Times Dropped" for that item, in the statistics of the online
  participant whose base is nearest within `statsRadius` 64 (else the nearest participant within 64). "Placed" is
  `TRACED`'s `placedByPlayer`. Never for left-by-others causes, the figure's dig, Ending D, give-backs or accidents (a trap's taken block is its one clue).
- **Why scary.** The game itself is lying, outside the world: when the world was opened, which version it is, which
  seed, what you did. A world file that is haunted; "he comes with the world".
- **Deniable.** The clock; the version is the mod's name; "maybe I did mine those torches".
- **Guardrails.** D-081. Only vanilla strings and the canon version and seed (guardrail 12 exact).
- **Rules.** (a) and (b): integrated servers only (the host's world list); (a)'s card fits only for the host. The
  server writes `data/a1016_02/sc14.dat` (tiny NBT: the arms with a nonce each, `versionForever`, the stop's epoch ms and
  time of day) on arm and on server stop. The client's world list reads it per world (the level folder is the summary
  icon's parent); when it shows a beat it writes the nonce into `data/a1016_02/sc14_seen.dat` in that folder (no server
  holds the world then). On load the server reads `sc14_seen.dat`: a shown beat is done, else it stays armed. The fake
  time is a pure function of the nonce, the real last played and now. (c) and (d) work on any server.
- **Friends.** (a) and (b) are the host's; (c) the world's once; (d) per credited player.
- **Config `sc14` (v2; keys `a` to `h`).** `a` gapHours 6, `b` nightFrom "01:00", `c` nightTo "04:30", `d`
  versionFromStage 3, `e` versionAfterDays 2, `f` seedFromStage 2, `g` stats true, `h` statsRadius 64.
- **Build.** Server `lore.sc.TheRecord` (files, arms, `HerobrineEvents.TRACED` listener awarding `Stats.BLOCK_MINED` /
  `Stats.ITEM_DROPPED`), mixin `lore.mixin.SeedCommandMixin` (the success component); client `lore.client.WorldListMarks`
  and mixin `lore.mixin.client.LevelSummaryMixin` (`getLastPlayed`, `getWorldVersionName`, `backupStatus` as
  `UPGRADE_TO_SNAPSHOT`; check that vanilla's prompt reads the version through `getWorldVersionName`, else mixin the
  prompt's argument).
- **Tests.** The file written on stop with the right fields; arm, seen and clear (pure); the fake time (in the gap,
  prefers 01:00 to 04:30, deterministic); `/seed` gives the canon seed once from Proximity, then the real one; stats
  credited to the right player for a REMOVE of a placed torch and REMOVE_STACK, never for left-by-others or give-back
  causes. Dev client checklist for the list.
- **Force it.** `/a1016 sc 14 arm a|b [force]` ((a) or (b); `force` skips the 6 hours and the night), `/a1016 sc 14 c`
  ((c)), `/a1016 sc 14 reset`; then quit to the title and open Singleplayer.

As built (SC-4: 02, 03, 10's call, 14; `SelfCreatedEntityTests`, `SelfCreatedLoreTests`):
- **10's call.** `HimEntity` keeps where he stood (his feet each tick he is IDLE, RISING or STARE_BACK on the ground) and
  his way out (every `WAY_OUT_STEP` 2 blocks while LEAVING, HIDING or RUSH, the first 24) and fires `FIGURE_LEFT` once,
  after `discard`, from `gone` and from the frozen sweep (`FigureApi.sweep` now calls `sweptAway`), only while the switch
  is on; the path is empty for going under and the glimpse. The first tick also calls `HisView.LIVE.onAppeared`.
- **02.** `entity.sc`: `NamelessCard`, `Nameless` (arms by UUID on the player's own play clock; ends on PARTICIPATION
  false, a spectator, the story, the switch's `disarmAll`), `NamelessRules` (pure), `ScGates` (the common gates).
  Payloads `entity/sc02` (ticks left, hide ticks; 0 is off) and `entity/sc02_spent` (the client used its 30 s: the arm
  ends). `NamelessClient` keeps a still anchor per remote player (0.1 blocks), reads the frame's `FogData` visible start
  and end once a tick, counts a hidden tick whenever any name is hidden, and reads its own `sc02` numbers;
  `AvatarRendererMixin` at the head of `shouldShowName(Avatar, double)`. `/a1016 sc 02` prints each participant's fit.
- **03.** `HisView` (server), `HisViewRules` (pure: the arm, the look, vanilla's crop, a plain PNG encoder, no AWT),
  `HisView.writePicture` (the files: refused while the world has no `icon.png`, so putting it back never needs a
  deletion), `EntityData.sc03Done`/`sc03Restored` (optional). Payloads `entity/sc03` (the figure's entity id) and
  `entity/sc03_taken` (host only, after an arm). Put back by Ending D's afterward (`afterward`, every 100 ticks), the
  switch and `sc 03 reset`. Client: `CameraMixin` at the end of `alignWithEntity`; `SectionOcclusionGraphAccessor`. Three
  frames left holes in the dev client (sections the player never saw were not compiled), so the frames draw with
  `smartCull` off and no fade-in and go on until nothing is left to compile (3 to 40 frames, at most 1.5 s with the frame
  back to the player's camera, FIX-SC-FINAL's `HisViewRules.Settle`; the dev client: 8 frames, 0.7 s, once per world). `iconSize` reads as 64 whatever its value (vanilla's `FaviconTexture` takes only 64).
  Checked in the dev client: the frame is from his eyes with the host at the middle, the copy is vanilla's old icon (kept
  across a reset and a second picture), and the world list shows his view.
- **14.** `lore.sc`: `TheRecord`, `RecordCard`, `RecordRules` (pure), `RecordData` (`data/a1016_02/lore_sc.dat`, opaque
  keys), `ScGates`. `sc14.dat` (keys `a`, `b`, `f`, `stopMs`, `stopTime`) is written on an arm, at a start that found a
  shown beat and at SERVER_STOPPING, on integrated servers only, with no beat while the switch is off (its revert writes
  it so). `WorldListMarks` (client) rereads a world's files every 2 s; a beat shown once keeps showing for the rest of that
  run until the world is opened; it is marked seen when vanilla first builds that summary, which `WorldOpenFlows` also
  does when a world is opened another way (the dev client's direct open asked vanilla's question too). The debug's
  `force` (a) (was `nogap`) picks its time as if the host had been away a day (last night shows at once). (b) for good: hardcore,
  `ending:ended` and `ending:line_finished=<host>`; no question then. (c) `SeedCommandMixin` wraps
  `ComponentUtils.copyOnClickText` in `/seed`'s lambda: only the shown text changes (the result stays the real seed); a
  player's `/seed` only. (d) on `TRACED`: a REMOVE of a placed block adds 1 to "Times Mined", a REMOVE_STACK its count
  to "Times Dropped"; never `:left/`, `entity:` (the figure's dig), `ending:` or `core:give_back`; overworld and the story
  gates; credit by horizontal distance; what was given is kept per player and taken back by the switch's revert (online)
  or at their next join while it is off. Checked in the dev client: the list's italic `a1.0.16_02`, vanilla's backup
  question with it, and last night's time.
- **For SC-5.** One-line states: `Nameless.LIVE.line`, `HisView.LIVE.line`, `TheRecord.LIVE.line`; overlay reads:
  `Nameless.LIVE.ticksLeft`, `HisView.LIVE.armedFigure`/`forcing`, `RecordData` getters. Reverts registered: `sc02`,
  `sc03`, `sc14`. Verifier: a LAN checklist for 02 (a friend 24 to 64 off in a fog night, standing 1.5 s).

#### Fixed in review (FIX-SC-B: entity and lore, R7 to R9)
- **02 arms only where its client can hide a name (R8 M1).** The client reads its drawn fog's own cover of a friend
  (`ScreenshotSpot.Fog.value`, each fog on its own as FIX-PLAIN does: at night the environmental fog runs from the camera
  to the dusk fog's end) instead of the visible start, and while the layer is on and another player is in its level it
  tells the server where that fog covers `fogShare` of a player (`NamelessRules.reach`, payload `entity/sc02_reach`, every
  second and at once on a 2-block change, fresh for 5 s). `Nameless.fit` refuses without a band at least 4 blocks deep from
  the farther of 24 and that distance to 64 (`NamelessRules.bandRefusal`; no fresh report, no band), since vanilla draws no
  name past 64. By the drawn dusk fog of the R8 test: at 12 chunks Proximity's dusk fog arms from dusk until it fades
  before dawn and Traces' around deep dusk only; at 16 chunks Proximity's around deep dusk only and Traces' never; fog
  spells and fog places as thick as they draw.
- **The hide time counts on screen only (R8 M2).** `AvatarRendererMixin` acts at the return of vanilla's rule and only
  where vanilla would draw the name; vanilla asks only for an avatar it extracts for that frame (in view, within 64), so a
  tick counts toward `hideSecondsTotal` only if a name was left out on screen, and never while the game is paused.
- **The locator bar (R8 M3).** While the rule hides a friend (the same rule, on screen or not), `ClientWaypointManagerMixin`
  leaves their dot off vanilla's locator bar (`forEachWaypoint`, which only the bar walks); when theirs is the only
  waypoint, `hasWaypoints` reads false, so the experience bar shows as vanilla's does for a player it is not sent. Client
  only, nothing untracked; the dot is back the moment the name is. His own sightings keep vanilla's bar (he has no dot):
  noted, not changed.
- **02 with three players (R9 M6, R7 L4).** The card fits only with nobody but the friend in the band, none nearer than
  `friendMinAtFire` (48), and nobody sharing the moment (`PlayerWatch.sharingWith`); it is PERSONAL (never drawn beside a
  sharer); and the client hides nothing while another player besides that friend stands within 48 of it
  (`NamelessRules.witnessed`: they would see the name).
- **14 (a) is the host's alone (R7 L4).** Core's new `CardTag.UNSHARED` (ARCHITECTURE): a card whose moment only its target
  has fires beside sharers and nobody else feels it (`DirectorBrain.feeling`: no felt fire, the sharers' gates do not hold
  it, a sharer having had it does not pass it over). `sc_14` is TEXT and UNSHARED, so a LAN guest beside the host spends
  nothing on it. The two-player dry runs compare what both felt without UNSHARED cards.
- **03 follows the switch in the world list (R8 M4 = R7 M3).** `WorldListEntryMixin` gives vanilla's entry
  `HisView.listedIcon`: the world's `sc03_before.png` while this client's switch is off (its old icon, or the same picture
  once put back), so the picture follows the switch before the world is opened again (its next server start writes it back,
  as before); never a write. The client asks the switch again right before it writes the picture (off: nothing written,
  the arm ends), and `HisView.report` asks it too: a picture whose report lands after the switch went off is recorded and
  put back at once.
- **14 (d) and accidents (R9 L7).** `RecordRules.causeCounts` never counts an `accident:` cause: a trap's taken block is
  its one clue (ARCHITECTURE's guardrail 7), and 15's block is natural anyway.
- **Rule 1's wording (R7 L5).** ARCHITECTURE's layer rule 1, the guardrail table and D-081 say it as built: the mod itself
  never writes outside the world's folder; vanilla's own backup button, which 14 (b)'s question offers, writes vanilla's zip
  to vanilla's backups folder as it does for any world.
- **Tests.** The reviewers' `R8SelfCreatedClientTests` (its check now asks the card's whole fit, its rules and then the
  band from the client's report, and the client's rule with the drawn fog), `SelfCreatedR9EntityTests`,
  `SelfCreatedR9LoreTests` and R7's guest test as `SelfCreatedR7FeltReviewTests`; `SelfCreatedEntityTests` (the fit beside
  a sharer, the band and the report; the client's rule by the drawn fog's cover, each fog on its own, and a witness;
  `sc_02` PERSONAL; the list's picture follows the switch, a report after the switch is put back), `sc_14`'s shape, the
  pacing table's rows, 14 (d)'s causes.

---

### accident

#### 15 The block you stood on (card `sc_15`; MINOR, Proximity; DIG + PERSONAL; CARVER + STRIPPER; no fake; `sections.sc15`)
- **Experience.** You walk a path you take often. Seconds later, behind you, one block of the ground you just walked
  over is gone: a clean 1x1 hole, two or three blocks down into a pocket of the cave under your path. You walked right
  over it.
- **Why scary.** The ground is not safe, and he let you cross. DESIGN's lore: his pass "left the block someone was
  standing on". The care is almost still there. Every accident after this lands harder.
- **Deniable.** "Was that hole always there?" Caves under paths are common.
- **Guardrails.** None bent: a removal out of view, nobody on or near it, never a fatal drop.
- **Rules.**
  - Fire arms the target for `armMinutes` 20 of play. While armed, accident's route sampler looks at the cell the
    target's feet stood on `delaySecondsMin` 3 to `delaySecondsMax` 8 s ago, now at least `behindMin` 5 blocks behind
    them and out of view, on one of their routes (`RouteBook`, at least `minPasses` 3 passes), a natural full block
    (`Scan.takeable`, not player-placed, not a bridge), not protected, outside every armed trap's zone; under it a
    natural cavity of air onto a plain floor, the fall from their feet at most `airDropMax` 3 (never more than vanilla's
    safe fall of 3: no fall damage). Never lethal on its own (review R9): no lava or water under it, in the drop or
    beside the block or the drop; never onto or through pointed dripstone, magma, cactus, powder snow, fire, a campfire,
    berries, a wither rose or a lava cauldron; never while the target has less than `minHealth` 10 health.
  - Alone (`SoundCues.alone(target, 48)`), nobody within `nobodyWithin` 16 of the cell. `TraceService.remove`
    (`accident:sc15`). At most once per in-game day per player, `maxPerWorld` 6.
- **Friends.** PERSONAL; never with anyone within 16 of the cell.
- **Config `sc15` (v3; keys `a` to `i`).** `a` armMinutes 20, `b` minPasses 3, `c` delaySecondsMin 3, `d`
  delaySecondsMax 8, `e` behindMin 5, `f` airDropMax 3, `g` minHealth 10, `h` nobodyWithin 16, `i` maxPerWorld 6 (step 1
  to 2: airDropMax 6 to 3 where still v1's, the old `cavityDepthMax` is gone; step 2 to 3: the keys opaque).
- **Build.** `accident.sc.StoodOn` + card; a short ring of the last 10 s of standing cells per armed player in
  `RouteSampler`.
- **Tests.** Removes a natural block over a short drop 5+ behind after the delay (ledgered); refused for a placed
  block, in view, a friend within 48, no cavity, lava or water under or beside it, a floor that hurts, a fall over 3,
  a hurt target, a trap zone; once per day.
- **Force it.** `/a1016 sc 15 arm [player]` (drops `minPasses`), then walk over ground with a low cave pocket under
  it (two or three blocks from your feet to its floor).

#### As built (SC-2: 01, 12, 13, 15; `SelfCreatedDirectorTests`, `SelfCreatedDigTests`, `SelfCreatedAccidentTests`)
- **01.** `director.sc.Places` (pure), `PlaceMemory` (kept in `PacingMemory.places()`, saved under `sc01` only when
  not empty), `Sc01Config`, `PlacesCommands`. The brain asks `Env.placeFactor()` (default 1) through
  `DirectorBrain.placeFactor(env, stage, clock)` (1 in Alone and in the player's own first hour) and hands every fire
  that is neither fake nor forced to `Env.remember(m, card, clock, felt)` (default nothing), for the target and each
  sharer. A place is forgotten (counts nowhere, keeps no chunk from trust) below `Places.FORGOTTEN` 0.05. Dwell halves at
  each whole hour of the player's play clock, so steady presence makes a chunk trusted about 1.5 h in. The live factor
  is 1 while the layer is off or the story is quiet (the common gates, `Places.storyQuiet`).
  `DirectorSim.Params.places(cfg)` turns the dread share on (own dice, off by default, so every older run is unchanged);
  `/a1016 director sim` and timewarp's summary use it while the layer is on. The debug lines are `/a1016 sc 01`'s only, never `/a1016 director`'s.
  Measured (192 seeds x 3 tempos): with the share, Traces ambients +5 to +11%, Traces minors +7 to +13%, Proximity
  ambients +4 to +6%, minors +3 to +5%, majors within 2%, the first hour fire for fire the same: `awayFactor` stays 1
  (SC-5, with every card of the layer in the deck: 0.9, section v2).
- **12.** `dig.sc.RestRefusal` (+ card), `dig.mixin.ServerPlayerSleepMixin` (`@ModifyExpressionValue` on the monster
  list's `isEmpty` in `ServerPlayer.startSleepInBed`, so creative never sees it, as vanilla skips the check there),
  `Sc12Config`. Arms in `DigData.sc()` (`DigScMemory`, saved under `sc`). The cap counts refusals; an arm is dropped when
  it runs out, at the cap, or once the shaft no longer stands under that bed; combat within 30 s keeps it. The shared
  windows are memory. `/a1016 sc 12 [player]` prints the arm, the shaft and the count.
- **13.** `dig.sc.WayBack` (+ card, trail, debug arm), `Sc13Config`. The trail keeps a sample per block moved, starting
  again after a jump over 12 blocks. The two wall blocks are taken from beside the next cell further in (toward the
  player), so the hollow opens onto the passage the player comes back along. Lights are torches and lanterns a player
  placed: one within 4 of a later trail sample and further in, one within 6 on the way out. Count in `DigData.sc()`.
- **15.** `accident.sc.StoodOn` (+ card, arms in memory), `StoodOnData` (`data/a1016_02/accident_sc.dat`: days, count),
  `Sc15Config`; the ring is `RouteSampler.Stand` (server ticks, 10 s, on the ground, only for armed players). The
  card's moment is standing on a route cell of 3+ passes; behind is 3D; `nobodyWithin` is about the others (the
  player's own distance is `behindMin`); never a cell flagged as a bridge, ladder or water of theirs.
- Every part has an `Env` record (the live one is `Env.live(server)`), so tests run on their own data, players and
  clocks. Reverts: `sc01` debug trusts, `sc12` arms and windows, `sc13` trails and arms, `sc15` arms. The edits of 13
  and 15 are ordinary ledgered traces (Ending D gives them back); the switch does not undo them.

#### Fixed in review (FIX-SC-C: R9 H1, M2, M3, M4, M5 and R7 L2; `SelfCreatedR9AccidentTests`, `SelfCreatedR9WorldTests`, `SelfCreatedR9EndingTests`, `SelfCreatedR7WorldReviewTests`)
- **15 was an unmarked death** (R9 H1, M2, M3): a 1x1 hole over lava two down on their own route (no way out, no trap
  to claim the death: in hardcore the run ended with no cross and no cause, at Proximity before any accident may
  come), a lava source beside the block flowing in, and an "air drop of 6" counted from under the block (a 7-block fall,
  maybe onto a stalagmite), at any health. 15 stays a scare, never an accident: `StoodOn.under` takes only air onto a
  plain floor, the fall from the feet over the block (the floor's top from its collision shape) at most `airDropMax` 3
  and never past `StoodOn.SAFE_FALL` 3 (vanilla's safe fall: walking into it does no damage); no fluid under
  it, in the drop, beside the block or beside any cell of the drop; nothing that `StoodOn.hurts` in the drop or as the
  floor (pointed dripstone, magma, cactus, powder snow, fire, campfires, berries, a wither rose, a lava cauldron); the
  gate refuses a target under `minHealth` 10. The cavity is `Cavity.AIR` only; `Under.depth` is the fall from the feet.
  `sc15` is version 2 (step 1 to 2: `airDropMax` 6 to 3 where still 6; `cavityDepthMax` is gone, an old file's value is
  skipped). `SelfCreatedAccidentTests` follow the rule (drops of 2, 3 and 4, lava, water, magma, a hurt target, the
  section's step).
- **11's sign was read from the lip** (R9 M4): it hung over the hole, so reading it was standing over an 8 to 30 block
  shaft to lava. `TorchTrail.sign`: over a walk cell 2 to 5 before the hole, the left-hand wall first, its column and
  the one it faces at least 2 from the hole's; none, no trail.
- **Ending D left the fog places** (R9 M5): `LastMinute`'s fog going now also fades them out (`ClientEffects.fadeFogPlaces`:
  each ends early with its own fade, so nothing pops; a preview sends the faded list to the one previewing),
  `Afterward.keepEffects` keeps them gone, and core's `addFogPlace` refuses from `ending:last_minute` on and after
  `ending:d_complete` (`ClientEffects.fogGoneForGood`).
- **The switch gave back half a plant** (R7 L2): `WorldSc.giveBack` makes another pass while a pass gave something back
  and something was blocked (a tall plant's upper half stands once its lower half is back), and the switch's give-backs
  of 08, 09 and 10 keep what waits (in view, not loaded; `WorldSc.Back`) and try it again every `WorldSc.RETRY_TICKS`
  600 while the layer stays off, as 07 does.

---

### ending
No task. Every card and rule here stops with the common gates (quiet for good, path C, ended, last minute, Ending A's
peace). Ending D's afterward gives back through the ledger what is his: turned blocks (08), prints (09, 10), the closed
way back (13), the block you stood on (15), names (07, core's NAME undo); t0rch's trail stays (left by others) and the
world icon is put back (03). The last minute's fog going takes the fog places (10) with it: each fades out, core
refuses new ones from the last minute on, and the afterward keeps them gone (review R9). The record's version reads `a1.0.16_02` for good after a finished hardcore line (14 b).

### debug (SC-5)
`/a1016 sc` with no number lists the numbers and each one's state in one line (armed, done, counts), never names;
`/a1016 debug overlay sc` (dev only) adds per participant: the place factor (01), any arm (02, 03, 05, 06, 12, 13, 14,
15) with time left, tonight's empty night (04), fog places (10). The second wave's lines 16 to 31 and its overlay parts
are in docs/SELF_CREATED_2.md (SC2-9).

#### As built (SC-5, integration: `SelfCreatedIntegrationTests`, `SelfCreatedPacingTests`, `SelfCreatedDebugTests`)
- **The merge.** SC-0 to SC-4 together compiled and registered cleanly (12 cards, 16 reverts, every `sc <NN>` under one
  `sc` literal). One test failed: SC-2's dry runs with the dread share, now over the whole layer's deck, put a very late
  world's Traces minors 21% over the plain runs (17% over DESIGN 4b's v1.3 numbers). Fixed by the design's knob
  (below), nothing else.
- **Core: a conversion of a block a player placed** (SC-3's ask). `TraceLedger.Entry` gains `after`, the block a
  CONVERT left (saved under its own key `after`, read leniently: v1.3 skips it, an older entry loads without it; the
  older constructors give none). `TraceUndo`'s CONVERT is BLOCKED only when the block now is not exactly `after` (or a
  block entity stands there that the old block never had); a block a player placed goes back while it is still what
  the conversion left. An entry without `after` keeps the old rule (a placed block or a dug cell is theirs). So Ending
  D's afterward turns 08 back; `WorldSc.turnBack` (the switch) goes through core's undo for entries with `after` and
  keeps its facing-only rule for older ones, so a block the player turned again since stays theirs.
- **Seams.** `FIGURE_LEFT`'s `appearedFor` is the sighting's own player (`FigureApi.spawnAtFogEdge` and the glimpse set
  it), as 10 needs: no change. 14 (d) also skips an undo's own edits and their dependents (`core:undo...`, the switch's
  give-backs) and 08's turning back (`world:sc08/back`, `RecordRules.TURNED_BACK`); t0rch's shaft is `:left/` already.
  The fog places' one producer is 10 (its live env is `ClientEffects.addFogPlace`); SC-1's client draws them. The six
  `ScGates` copies and `Places.storyQuiet` hold the same story flags (checked; left where they are).
- **`/a1016 sc`** (`debug.ScIndex`): the switch, then one line per number, `NN: ...`, in counts, days, "armed",
  "waiting", "done" and time left only (`SelfCreatedDebugTests` holds a list of words that would name a feature and
  checks the index and the overlay against it). 01 each participant's factor where they stand (or why it is 1 now) and
  how many places and whether a chunk is trusted; 02, 03, 14 their parts' own lines; 04 the count and the one set; 05,
  06, 13, 15 each player's arm (13's is the debug arm) with time left; 07 the count; 08, 09 per base; 10 what waits and
  the world's places; 11 the trails left; 12 the refusals and the arms. `/a1016 sc on|off` is the switch, live
  (`SelfCreated.setEnabled`; off runs every revert at once).
- **Overlay.** `/a1016 debug overlay sc` turns the overlay on with the layer's rows (`on` alone shows none; `off` drops
  both): `sc 04`, `sc 10`, then one row per participant (the viewer first, at most 6): `01 x..` and each arm with its
  time left (02, 03 and 14 for the host only, 05, 06, 12, 13, 15). `OverlayPayload.MAX_ROWS` is 40.
- **Readers added** (read-only): `ComingHome.ticksLeft`/`armedCard`, `InStep.armTicksLeft`/`spent`,
  `WayBack.armTicksLeft`, `ScMarks.bases`.
- **Sims.** `DirectorSim.Params.fitChanceOf` (a card's own chance a look, on the same dice; null: `fitChance` for all)
  models a rare moment. `SelfCreatedPacingTests` checks every card's row of the table above and runs 192 seeds x 20 h
  per tempo in four cases against the deck without the layer: every card at the usual dice; with `sc01`'s dread share;
  every card of the layer never fitting (dread share); every one fitting 2% a look (dread share; the way back holds the
  major tier for its long hold, then parks). Every case keeps D-070's first hour, every hard limit and every rate within
  15%. `sc01.awayFactor` is 0.9 (section v2): at 1.0 the dread share put very late Traces minors 21% over the plain runs.
- **Switched off** works as each part built it; `/a1016 sc off` is the live path the verifier can use.

#### Fixed in review (R7, core and director; `SelfCreatedR7ReviewTests`, `SelfCreatedR7DirectorReviewTests`)
- **M1, L3: the CONVERT undo.** SC-5's "exactly `after`" left a turned furnace lit since (a hive's honey, a lectern's
  book, water in a chest) turned for good, and let Ending D close a door the player had closed and opened again. Now
  the undo compares what the conversion changed (the block, and each property it set) and keeps vanilla's drift of
  the rest when it turns back (a lit furnace stays lit). A change of what it set by anything but `TraceService` (a
  player, redstone, a piston) marks the entry for good (`TraceLedger.changedSince`, fed by core's `Level.setBlock`
  hook; core's own edits never count), so the block stays theirs even once it is just what the conversion left
  again. The marks are saved under `traces.dat`'s `changed` key (v1.3 skips it). This is core's rule, not behind the
  switch; entries without `after` keep the old rule.
- **M2: where 01 remembers a moment.** The home cards fire while the player is away, so the house was never
  remembered and stayed trusted. `Places.AT_HOME` (sc_05, sc_05b, sc_07, sc_08, sc_09, sc_12) is remembered at the
  home: the base (`PlayerWatch.base`) in that dimension, else the middle of the chunk they dwell in most there; a felt
  one at the target's home. `Places.AT_TRACE` (sc_15, cause `accident:sc15`) is not remembered when it arms but where
  its trace is ledgered (the director's `TRACED` listener), once, for the player it was armed for; an arm that runs
  out leaves no place.
- **L1: files.** `writeInWorld` makes the target's folders one at a time, each only once the folder it goes in
  resolves inside the world's folder, so a link never gets a folder made outside before the write is refused.

## Pacing

The second wave's cards and the dry runs with both waves: docs/SELF_CREATED_2.md, "Pacing and director integration"
(its `SelfCreated2PacingTests`; this section's table and `SelfCreatedPacingTests` are the first wave's).

New cards join the decks. Alone gains one ambient (sc_06), Traces four minors and two ambients, Proximity three minors
and one major. Rates per tier are unchanged; the decks spread over more kinds of moment. Cards that need a base,
animals, a friend or a cave park when their moment never comes (`unfitParkMinutes`), so they never hold a tier. SC-5
re-runs the 192-seed sims with every card registered and `sc01`'s dread share: D-070's first-hour targets, the hard
limits over 20 h, and Traces and Proximity rates within 15% of DESIGN 4b, else it lowers `sc01.awayFactor`.

| Card | Feature | Tier | Stage | Tags | Habits | Fake |
| --- | --- | --- | --- | --- | --- | --- |
| sc_02 | 02 | AMBIENT | Traces | FOG, PERSONAL | WATCHER | no |
| sc_05 | 05 | MINOR | Traces | SOUND, PERSONAL | VISITOR, WATCHER | yes |
| sc_05b | 05 | AMBIENT | Traces | MOB | WATCHER, VISITOR | yes |
| sc_06 | 06 | AMBIENT | Alone | SOUND, PERSONAL | WATCHER | yes |
| sc_07 | 07 | MINOR | Proximity | MOB | COLLECTOR, STRIPPER | no |
| sc_08 | 08 | MINOR | Traces | SCAR | VISITOR, COLLECTOR | no |
| sc_09 | 09 | MINOR | Traces | SCAR | VISITOR, WATCHER | yes |
| sc_11 | 11 | MINOR | Traces | LIGHT, SCAR | MOURNER, CARVER | no |
| sc_12 | 12 | MINOR | Proximity | DIG | CARVER, WATCHER | no |
| sc_13 | 13 | MAJOR | Proximity | DIG, PERSONAL | CARVER, STRIPPER | no |
| sc_14 | 14 | MINOR | Traces | TEXT, UNSHARED | WATCHER, VISITOR | no |
| sc_15 | 15 | MINOR | Proximity | DIG, PERSONAL | CARVER, STRIPPER | no |

Not cards (capped and spaced on their own): 01 (a rate factor), 03 (once, on a sighting), 04 (a night roll, at most 5),
10 (on a sighting), 14 (b), (c), (d).

Measured (SC-5, `SelfCreatedPacingTests`: the registered deck with every card above and `sc01`'s dread share at
`awayFactor` 0.9, 192 seeds of 20 h per tempo; in brackets the same runs without the layer's cards, DESIGN 4b's v1.3
table):

| | Early | Slow burn | Very late |
| --- | --- | --- | --- |
| Alone lasts | 0.49 h | 0.65 h | 0.81 h |
| Alone: ambients per hour | 2.68 (2.66) | 2.89 (2.86) | 2.88 (2.85) |
| Traces: ambients per hour | 2.29 (2.19) | 2.32 (2.20) | 2.32 (2.20) |
| Traces: minors per hour | 0.92 (0.86) | 0.86 (0.82) | 0.84 (0.78) |
| Proximity: ambients per hour | 1.43 (1.38) | 1.46 (1.39) | 1.50 (1.46) |
| Proximity: minors per hour (active) | 1.04 (1.03), 1.91 | 1.06 (1.05), 1.93 | 1.10 (1.09), 1.94 |
| Proximity: a major every | 1.82 h (1.78) | 2.25 h (2.23) | 2.75 h (2.73) |
| First accident: median (earliest) | 4.43 h (2.70 h) | 5.50 h (3.52 h) | 6.38 h (4.52 h) |
| First hour: first event by 30 / 45 minutes | 80% / 94% | 81% / 95% | 81% / 95% |
| First hour: two or more events | 90% | 92% | 95% |

The first hour is the plain runs' fire for fire (the factor never applies there). Every card at the usual dice
without the dread share moves no rate more than 4% (very late Traces minors 0.75). With every card of the layer never
fitting (a player alone with no house, pets or caves of the right shape): Alone's ambients 2.50 / 2.77 / 2.80 (their
holds cost up to 6%, like the blind cards'), Traces 2.13 / 2.19 / 2.20 ambients and 0.85 / 0.87 / 0.81 minors,
Proximity 1.45 / 1.48 / 1.50 and 1.00 / 1.03 / 1.05, a major every 1.86 / 2.31 / 2.88 h, the first event by 30 minutes
in 78 to 79% of runs, by 45 in 93 to 95%. Fitting 2% a look: Proximity's ambients 1.28 / 1.32 / 1.35 (5 to 8% under), minors
0.96 / 1.02 / 1.07, a major every 1.81 / 2.28 / 2.72 h. No hard limit is broken in any run.

### Verifier checklist (SC-7; dev client, a LAN pair for 02)
Not seen in a client yet, each with its debug: 04's sky and quiet (`/a1016 sc 04 arm force` before 11000: no moon
from 11000 to 1000, no monster spawning within 96 from 12000, animals silent, the next night as vanilla); 10's fog place
(`/a1016 sc 10 here` on open ground: the fog thickens toward it over a minute, its haze and tint, none in a tunnel or a
closed house, gone after its days; `/a1016 sc` line 10 counts it); 02 with two clients (`/a1016 sc 02 arm` on one, the
other 24 to 64 off in a fog night standing still 1.5 s: no name tag and no locator dot; moves or comes within 24: both
back). Seen by their workers and worth a second look: 03 (`/a1016 sc 03 arm`, `/a1016 entity spawn close` near the base at night: the world
list's picture, the host's 0.7 s pause once), 14 (`/a1016 sc 14 arm a force` and `arm b force`, quit to the title:
last night's time, the italic version and vanilla's backup question; `/a1016 sc 14 c` then `/seed`), 12 (`/a1016 sc
12 arm force`, sleep at night: vanilla's message once, then sleep). The switch: `/a1016 sc off` puts back 08 (a
turned block a player turned again stays), 09, 10, 07's names, 03's picture, 14's statistics and record, the empty
night and every arm; `/a1016 sc` then says off; `/a1016 sc on`. The spoiler grep: no feature words in card ids, command
literals and usage, section names and config keys, `/a1016 sc`'s index or INFO and WARN logs (`VerifyScSpoilerTests`).

#### Verified (SC-7, `verify/sc` from `dd4680f`)
- **Dev client** (1708 x 960, 12 chunks, a temporary uncommitted hook, no mixin error): 04 (full moon, then the new
  moon from `sc 04 tonight force`, then full again on `clear`), 03 (armed by a close sighting, 10 frames in 445 ms, the
  list shows his picture), 10 (`sc 10 here`: haze toward the place), 12 (once its respawn is the bed's head, as sleeping
  sets it: vanilla's "monsters nearby" once, then sleep), 14 (the canon seed once, then the real one; the list's
  3:07 a.m. and italic `a1.0.16_02`; vanilla's backup question; on the next start both read as shown), the switch (the
  list's old picture, real time and 26.3 while this client's switch is off; `sc off` puts the picture back, says
  "already off" the second time). Not seen: 02 (needs a LAN pair), anything under Iris or Sodium (not in the dev setup).
- **No test, and why.** R8 M2 (the hide time counts on screen only) and R8 M3 (the locator dot) live in client mixins
  (`AvatarRendererMixin`, `ClientWaypointManagerMixin`) that the server-side game tests cannot load, and need a second
  client: left to 02's LAN checklist. R8 L6's Sodium part (03's settle loop trusting `hasRenderedAllSections`) is not
  addressed: Sodium is not in the dev setup; at worst the picture has holes (03's capture is caught and ends the arm).
- **Tests added** (`core.VerifyScSwitchTests` and `VerifyScCostTests` in test environments of their own, green): the
  real `/a1016 sc off` runs every revert (08, 09, 10, 07's name, the fog place, 04's night, 03's picture, the arms),
  again is "already off" and changes nothing, an off switch at a server start reverts; the cost of the hot hooks.
  `core.VerifyScSpoilerTests` fails: config field names and `/a1016 sc` literals name features (D-080's own list).
  (The commands above are the ones run then; FIX-SC-FINAL renamed them: `sc 04 arm force`, `sc 14 arm a|b force`,
  `sc 14 c`, `sc 12 arm force`.)

#### Fixed in review (FIX-SC-FINAL: the spoiler rule, 11's shaft, R8 and R9 lows; `fix/sc-final` from `verify/sc`)
- **The spoiler rule (D-080, blocking).** The config keys written are opaque letters per section (each Config line above
  maps them); the fields keep their names in the code with `@SerializedName(value = "<letter>", alternate = "<old
  key>")`, and core's lenient reading takes a field's key, else its first alternate the file has (ARCHITECTURE's
  config section), so a dev config's hand edits under the old keys are carried and only the new keys are written back;
  every section's version rose by one with that step (`sc01` and `sc15` 3, the others 2; `sc` keeps `enabled`). The
  literals that named a part are letters or the plain verbs: `sc 01 a` (was `trust`), `sc 04 arm [force]` (`tonight`),
  `sc 06 arm [fake] [force]` (`anytime`), `sc 09 b` (`circle`), `sc 12 arm [force]` (`noshaft`), `sc 14 arm a|b
  [force]` (`last`, `version`, `nogap`), `sc 14 c` (`seed`). Log lines: 04's "set (n)", 11's text file lines by its
  id, 03's report no longer logs the restore's words, Iris' unreadable API "a shader pack counts as in use".
  `VerifyScSpoilerTests` holds the keys (defaults and the file the server wrote, after every section is read), every
  old key's hand edit carried (`everyOldKeyIsCarriedToItsNewKey`), every literal, argument and usage line, and every
  INFO, WARN and ERROR line of the layer read from the sources, to the list and a few more words (`sky`, `trust`,
  `circle`, `loop`, `phantom`, `sign`, `spawn`, `mob`, `gap`, `behind`, `hollow`, `list`, `water`, `landing`);
  planting a moon in a log line, `noshaft` under `sc 04` and `bedRadius` in `sc12` made all three fail.
- **11 was an unmarked death (guardrail 1, medium).** Its 1x1 shaft could open in the middle of a cave, so a player
  coming from its far side could step in: an 8 to 30 block fall into lava with no clue. Now the hole is at a dead end
  (the walk's cells from the sign's on walled in on every side but the walk's at feet and head, and roofed:
  `TorchTrail.walledIn`, `enclosedFrom`; an end open anywhere else is refused, "can be walked into from beside the
  trail", and the sign must hang before that dead end), and the fall is survived: the stone column's last block stays
  as the floor over the lava and the lowest dug cell is a water source left with the trail (`Plan.water`, `floor`;
  `MIN_COLUMN` 3). Tests `t0rchsHoleIsADeadEndOnlyTheTrailReaches` (a walk from the hole without the sign's cell
  reaches only the trail's last cells; open beside, past, over the hole or beside the cell before it: refused) and
  `aFallIntoT0rchsShaftIsSurvived` (a cow falls in and is unhurt in the water; the same dry fall hurts one).
- **12 by either half (R9 low).** The arm's bed and the network's bed match either half of the bed vanilla's attempt
  names (`RestRefusal.sameBed`, `Network.isBed`); test `theArmedBedIsMatchedByEitherHalf`.
- **03's picture when the server never heard (R8 low).** `HisView.writePicture` sets the world's marker
  `data/a1016_02/sc03_taken` ("1") before it writes the picture, and the restore turns it to "0"; the switch's revert
  (live or at the next start), Ending D's afterward and the arm's once-per-world rule read it as taken
  (`HisView.marked`, `taken`), so a picture whose report was lost is still put back and counted. Tests
  `hisViewPutsBackAPictureTheServerNeverHeardOf` and the files test.
- **Lenient flags (low).** `EntityData`'s `sc03Done` and `sc03Restored` are read leniently like the layer's other saved
  parts (`entityDataReadsTheLayersFlagsLeniently`).
- **The capture's cap (low).** The frames, their waits and the frame back to the player's camera keep to 1.5 s
  (`HisViewRules.Settle`: every wait cut to what is left, no frame past the minimum after 1.3 s, the last graph wait cut
  to the cap) while each frame renders in under 100 ms; slower frames add their own time. Test
  `hisViewCaptureKeepsToItsCap` (the client's loop on a clock, every wait at its bound).
- **ARCHITECTURE.** The layer's section is the D-080 pointer with its contracts in neutral words (no backup button, UI
  or files named; the sections' versions point here); `CardTag.UNSHARED` without "client or files".

## Considered and left out
- A mannequin or any player-shaped entity: spawning one breaks ARCH 2; it is also fanon.
- Deleting or adding screenshots, or any file outside the world folder: the player's own data is never touched.
- "X joined the game" or names from the list in chat: the 2011 fake-video cliché.
- Changing a friend's skin or eyes: guardrail 3, and it would be him on screen.
- The title screen panorama: it would leak into every world, not this one.
- Moving items in the player's inventory or hotbar: that is touching the player.
- Silencing monsters on the empty night: a silent creeper would be an unmarked death.

## Decisions

| ID | Decision | Why |
| --- | --- | --- |
| D-080 | The self-created layer is a surprise: it lives on `self-created`, every player-visible id is opaque (`sc_NN` cards, `/a1016 sc <NN>` with letters and plain verbs under it, `sections.scNN` with letter keys), its docs are this file only, no DESIGN, TRY_IT, DECISIONS or ARCHITECTURE rows (one pointer line) until Mariano has played it | He asked not to know. He reads `/a1016` output, the config and the log |
| D-081 | Out-of-world records may lie: the world list's last played time and version (with vanilla's own backup prompt), the world icon, `/seed` and the statistics. The mod itself writes only in the host's own world folder (vanilla's own backup button may write vanilla's zip to vanilla's backups folder), only vanilla strings and the canon version and seed, never a deletion, each once (the version for good only after a finished hardcore line), the old icon kept and put back by Ending D | "What would make someone afraid to open their world again?" A haunted world file, a version that should not be |
| D-082 | Vanilla UI may say something untrue only when it is true in the story: the bed's "monsters nearby" only once the Under-you shaft stands under that bed; a friend's name tag hidden only at the fog edge, where he could be | The game admitting he is there, without the mod ever writing |
| D-083 | No new in-world text by him. The only new text is "brb" on t0rch's sign, left by a person | Guardrail 2 |
| D-084 | Places remember: each player's real fires leave places; the ambient and minor rate rises near them (up to 1.8x) and once in the trusted place (1.6x). Never in Alone, the first hour, a gap, a cap or a major slot | Dread of places, built by the director instead of by spectacle |
| D-085 | Fog places: a fog over one spot for 1 to 3 in-game days after he stood there, a core contract beside the fog spell, weather-like (sky-weighted, clears underground), counted where `FogLimits` counts | The spot you saw him becomes a spot you can see from home |
| D-086 | Names are removable things: `MobTamper.unname`, ledger kind NAME, undone by Ending D | He has no name; he takes them |
| D-087 | Entity fires `FIGURE_LEFT` with where he stood and the way he left; world leaves prints and the fog place | He was really there; one event, no cross-workstream call |
| D-088 | t0rch's torches [ours]: once per world, a trail of torches on the right-hand wall into an unexplored branch, ending at a 1x1 shaft toward lava with a sign "brb" before it. Left by others, never undone, not a SiteRegistry site. The hole is at a dead end only the trail reaches, and its foot is water on the stone over the lava, so a fall is survived (FIX-SC-FINAL, guardrail 1) | F23 "t0rch . lava"; "this is singleplayer" |
| D-089 | The bed refusal is armed only while the network's shaft top is within 2 blocks under that bed, and refuses once per arm; friends' beds within 8 are refused for 20 s | The monster is real; one monster refuses every bed near it |
| D-090 | The empty night: on a bright-moon clear night from Traces, the moon is drawn new and nothing hostile spawns within 96 of anyone, phantoms included; animals near players are silent; monsters alive are left as they are | Absence in the sky; a silent creeper would be unfair |
| D-091 | A card whose moment cannot be waited for by the director (coming home, the block you stood on, the bed, a friend in the fog, the world list) arms the player and counts when it arms, as `the_screenshot` does | The director ticks every 30 s; these moments last seconds, or happen outside the game |

## Tasks

Branches from `self-created`, merged back into `self-created` only. Same process as v1.3 (`docs/PLAN_V1_3.md`,
"Waves"): worktrees, one branch per task, a worker edits only its task's workstream paths, `./gradlew build` green,
reports of at most 40 lines, core asks in the report. SC-1 to SC-4 run in parallel (4 at most); each needs only SC-0.

| ID | Workstream(s) | Features | Files / areas | Depends on | Size |
| --- | --- | --- | --- | --- | --- |
| SC-0 | core, docs | contracts 1 to 5 (for 05, 06, 07, 10, 14 d, 15), ARCHITECTURE pointer, version | `core/HerobrineEvents`, new `core/FogPlace`, `HerobrineState.Effects`, `ClientEffects` and `core.client.ClientEffectsClient`, `FogLimits`, `MobTamper`, `TraceLedger`, `TraceUndo`, `TraceService` (TRACED), `SoundCues`; `gradle.properties`; one ARCHITECTURE line | - | M |
| SC-1 | atmosphere | 04, 05 (sc_05, sc_05b), 06, 07, fog places client side (10) | `atmosphere/sc/**`, `atmosphere/card/` (new cards), `MobTamperImpl.unname`, `AtmosphereData`, `FogWeather` (the night roll hook), `SpawnPlacementsMixin`, new `mixin/EntityStepMixin`, `mixin/PhantomSpawnerMixin`, `mixin/client/SkyMoonMixin`, `ClientAtmosphere`/`WeatherFog` | SC-0 | L |
| SC-2 | dig, accident, director | 12, 13, 15, 01 | `dig/sc/**`, `dig/mixin/ServerPlayerSleepMixin`, `DigTicker` (trail ring), `DigData`; `accident/sc/**`, `RouteSampler` (standing ring); `director/sc/Places`, `PacingMemory`, `DirectorBrain` (`Env.placeFactor`, `due`), `DirectorSim`, `DirectorDebug` | SC-0 | L |
| SC-3 | world | 08, 09, 10 (prints, fog place), 11 | `world/sc/**` (`Prints`, `Turned`, `Tracks`, `WhereHeStood`, `TorchTrail`), `WorldInit`, `WorldData`, `data/a1016_02/world/sc/t0rch.json` | SC-0 | L |
| SC-4 | entity, lore | 02, 03, 10 (fires `FIGURE_LEFT`), 14 | `entity/sc/**`, `entity/client/` (`NamelessClient`, `HisViewClient`), `entity/mixin/client/` (`AvatarRendererMixin`, `CameraMixin`), `HimEntity.gone` (the event, stand spot and path); `lore/sc/**`, `lore/mixin/SeedCommandMixin`, `lore/mixin/client/LevelSummaryMixin`, `lore/client/WorldListMarks` | SC-0 | L |
| SC-5 | debug, docs (INT) | all: `/a1016 sc` index, overlay toggle, sims with every card and the dread share, "as built" notes here, the verifier list, preview jar | `debug/**`, `docs/SELF_CREATED.md` | SC-1 to SC-4 | M |
| SC-6 | reviewers R7 to R9 (read-only) | R7 world edits and guardrails (07, 08, 09, 10, 11, 13, 15); R8 client and out-of-world (02, 03, 04, 14, fog places); R9 director, pacing, friends, cost (01, 05, 06, 12) | read-only | SC-5 | - |
| SC-7 | VERIFY | 3/3 green, every review finding re-checked, this file against the code, the spoiler grep (no feature words in card ids, command literals, section names or INFO logs), the guardrail grep | - | SC-6 and its fixes | S |

Every task registers its debug commands under `/a1016 sc <NN>` and its config as `sections.scNN` (version 1,
`ModConfig.Versioned`, steps with `ModConfig.Upgrades.keepOrNew` for any later default change).
