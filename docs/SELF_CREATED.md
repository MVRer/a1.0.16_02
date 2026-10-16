# Self-created: the world is a little off

Oct 16, 2026 · lead horror design for the branch `self-created` (from `99ddb51`, main with v1.3 and the playtest fixes)

**Spoilers. Mariano plays this layer blind. He must not read this file, the task branches' commits or the SC test
names.** Everything here lives on `self-created`; `main` is untouched until he says.

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
  merges children of one literal). Chat lines from these commands say what happened in plain words, as the dig arms do,
  but only to the op who ran them.
- Config: one section per feature, `sections.scNN`, owned by that feature's workstream (a `ModConfig.Versioned` class in
  `<ws>.sc`). Field names are numbers and plain rules (`minDistance`, `chance`, `maxPerWorld`), never the thing
  (`bed`, `moon`, `name`).
- Docs: this file only. No rows in DESIGN.md, TRY_IT.md, DECISIONS.md or ARCHITECTURE.md until he has played it; SC-0
  adds one line to ARCHITECTURE: "Self-created layer (branch `self-created`): contracts and features in
  `docs/SELF_CREATED.md` (spoilers)". The core contracts below are documented here and in Javadoc.
- The debug overlay shows the layer's lines only after `/a1016 debug overlay sc` (dev only, SC-5).
- Log lines at INFO name only `sc_NN`. Game test classes are `SelfCreated<Ws>Tests` with method names that describe the
  rule; that is fine, he does not read test names.

## Guardrails: held and bent

| Rule | In this layer |
| --- | --- |
| DESIGN 1, he never strikes | Held. Nothing damages the player. 15 removes a block only once nobody is on or near it, and only where the hole is plain to see or the fall is short (lava glows, water, an air drop of 6 or less) |
| DESIGN 2, he never speaks or writes | Held. The only new in-world text is t0rch's sign, "brb", left by a person (11). The world list, `/seed` and the bed message are vanilla's own strings around the canon version and seed (D-081, D-082) |
| DESIGN 3, default Steve, no glow | Held. 02 hides a friend's name tag (canon: "no name over him"); it never changes anyone's skin or eyes |
| DESIGN 4, never in front of the camera | Held. 03 renders a frame from where he stood, never presented on screen |
| DESIGN 7, he only removes or moves | Held. Turned blocks are a state change of an existing block (08); prints are grass, snow, crops and plants removed (09, 10); the closed way back is two wall blocks moved (13); names are removed (07). t0rch's torches and sign were left by others (11) |
| ARCH 3, nothing changes in view | Held. The moon is switched while it is below the horizon (04); fog places ease in over a minute like weather (10) |
| ARCH 2, never touch the player | Bent once: 12 refuses one bed attempt with vanilla's own reason; the next attempt sleeps |
| "The mod never tells you" stays inside the world | **Bent (D-081).** The world list's last played time and version, the world icon, `/seed` and the statistics may lie. Only the host's own world folder is touched, only vanilla strings and the canon numbers, never a deletion, each once |
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
- **Config `sc01` (v1).** `maxPlaces` 24, `radius` 32, `maxFactor` 1.8, `halfLifeDays` 4, `weightAmbient` 0.15,
  `weightMinor` 0.3, `weightMajor` 0.5, `weightSighting` 0.2, `trustedFactor` 1.6, `trustedMinHours` 1,
  `awayFactor` 1.0, `simDreadShare` 0.3, `simDreadFactor` 1.4, `simTrustedShare` 0.05.
- **Build.** `director.sc.Places` (pure: weights, decay, factor, trusted chunk); `PacingMemory.places` and `dwell`
  (saved, optional; old `director.dat` loads empty); `DirectorBrain.Env.placeFactor()` (default 1.0; live env reads the
  player's position); `DirectorSim` models a dread share (`simDreadShare` of ticks at `simDreadFactor`,
  `simTrustedShare` at `trustedFactor`); `DirectorDebug` prints the factor, the nearest places and the trusted chunk.
- **Cost and saves.** One small list walk per decision; dwell map update once per director tick.
- **Tests.** Factor math (radius, dimension, decay, cap); a fire records for the target and a felt sharer at their own
  spots; fakes and forced fires don't; the trusted chunk needs 1 h and no place near; `due` multiplies only ambient
  and minor from Traces; first-hour cap and gaps unchanged; old `director.dat` loads; sims with the factor keep D-070's
  first-hour targets and Traces/Proximity rates within 15% of DESIGN 4b (else lower `awayFactor`).
- **Force it.** `/a1016 sc 01 [player]` prints the factor now, the places and the trusted chunk; `/a1016 sc 01 trust`
  makes the chunk you stand in the trusted place for an hour.

---

### entity

#### 02 Nameless (card `sc_02`; AMBIENT, Traces; FOG; WATCHER; no fake; `sections.sc02`)
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
- **Config `sc02` (v1).** `friendMinAtFire` 48, `minDistance` 24, `maxDistance` 64, `fogShare` 0.5, `stillSeconds` 1.5,
  `armMinutes` 20, `hideSecondsTotal` 30.
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
- **Config `sc03` (v1).** `fromStage` 2, `maxBaseDistance` 96, `hostBaseDistance` 48, `standSeconds` 1.0,
  `iconSize` 64.
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
- **Config `sc04` (v1).** `chanceByStage` {0, 0.2, 0.25, 0.25, 0.15}, `minNightsApart` 6, `maxPerWorld` 5,
  `brightPhasesOnly` true, `spawnRadius` 96, `quietRadius` 48, `quietAnimals` true, `noPhantoms` true.
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
- **Force it.** `/a1016 sc 04 tonight [force]` (tonight, or now if it is night; `force` skips fog and phase),
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
- **Config `sc05` (v1).** `awayDistance` 48, `awayMinutes` 10, `armMinutes` 60, `houseRadius` 6, `minCoveredCells` 4,
  `minDistance` 6, `maxDistance` 16, `stepsMin` 3, `stepsMax` 5, `stepTicks` 8, `stepVolume` 1.6, `closeDelayMin` 15,
  `closeDelayMax` 30, `closeVolume` 1.6, `approachDistance` 32, `mobRadius` 24, `minMobs` 3, `holdSecondsMax` 12,
  `lookSeconds` 2.
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
- **Config `sc06` (v1).** `walkSecondsBeforeStart` 3, `behindMin` 6, `behindMax` 9, `lagTicksMin` 4, `lagTicksMax` 6,
  `stepsMin` 6, `stepsMax` 14, `stepVolume` 1.2, `lookBackDegrees` 70.
- **Build.** `atmosphere.card.InStepCard`; common mixin `atmosphere.mixin.EntityStepMixin` at `Entity.playStepSound`
  (reached through `walkingStepSound` from `applyMovementEmissionAndPlaySound`; check on 26.3 that the server reaches
  it for a player's own movement, else count the walk distance the server sees, as vanilla's `moveDist` and
  `nextStep` do), a UUID map lookup only for players with an episode.
- **Cost and saves.** A map lookup per step of players with an episode; nothing saved but the night (memory per UUID).
- **Tests.** Each own step is answered with the lag, behind, on the ground's own sound; looking back ends it; one more
  after a stop; no follower while sneaking; underground, daylight without fog, riding refuse; a spectator within 48
  refuses; once per night; the fake is the late own step.
- **Force it.** `/a1016 sc 06 arm [fake] [anytime]` (the next 2 minutes; `anytime` skips night and fog), then walk.

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
- **Config `sc07` (v1).** `baseRadius` 64, `nobodyWithin` 16, `awayDistance` 48, `maxPerWorld` 3, `minDaysApart` 3.
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
- **Config `sc08` (v1).** `baseRadius` 12, `nobodyWithin` 16, `awayDistance` 32, `perFireTraces` 1, `perFireLater` 2,
  `minDaysApart` 2, `maxPerBase` 8, `signs` true.
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
- **Config `sc09` (v1).** `lengthMin` 14, `lengthMax` 28, `stride` 2, `minPrints` 5, `circleChance` 0.4,
  `nobodyWithin` 16, `awayDistance` 32, `minDaysApart` 2, `maxPerBase` 4.
- **Build.** `world.sc.Prints` (pure plan: which edit a surface cell takes), `world.sc.Tracks` + card; `LiveTerrain` for
  surface tops.
- **Tests.** The path ends at the door's outside cell; prints alternate; each surface's print; stone skipped; refused in
  view; all or nothing; the loop goes round the house; the fake is short and curved.
- **Force it.** `/a1016 fire sc_09 [fake]`; `/a1016 sc 09 circle` (the loop).

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
    `maxFogPlaces` 4 at once.
- **Friends.** World-wide like weather; everyone sees the place.
- **Config `sc10` (v1).** `standChance` 0.7, `runChance` 0.35, `clearRadius` 2, `maxRunPrints` 12, `retryMinutes` 10,
  `fogChance` 0.6, `fogRadius` 40, `fogLevel` 0.45, `fogRampTicks` 1200, `fogDaysMin` 1, `fogDaysMax` 3,
  `maxFogPlaces` 4.
- **Build.** `world.sc.WhereHeStood` (listener, pending list in memory: losing it on a restart is fine), `Prints` from
  09. Entity's one call is in SC-4; atmosphere's render is in SC-1.
- **Tests.** A synthetic event gives prints at the spot and the cleared plants, only once out of view, dropped after 10
  minutes; run prints along the path; the fog place with the configured numbers, capped at 4; nothing underground, in
  the Nether, unseen, or for the glimpse.
- **Force it.** `/a1016 sc 10 here` (as if he stood, seen, on the block you look at); or a real sighting with
  `/a1016 fire sighting_cow`.

#### 11 t0rch's torches (card `sc_11`; MINOR, Traces; LIGHT + SCAR; MOURNER + CARVER; no fake; once per world; `sections.sc11`)
- **Experience.** In a cave you are exploring for the first time, there are torches already. On the right-hand wall,
  every ten blocks or so, going deeper, into a branch you have not been down. You follow them. They end at a 1x1 hole
  straight down in the floor, a torch on the floor beside it. On the wall above the hole a sign: "brb". Far down, lava.
  No torches come back out.
- **Why scary.** "This is singleplayer." Someone was here before you and did not come back. The list says
  "t0rch . lava" (F23; F06 "t0rch . gone"). After this, every cave might have someone's torches in it.
- **Deniable.** It reads as an old player's mine, a used world. Alone it names nobody (guardrail 8).
- **Guardrails.** Left by others (D-088): torches, the shaft and the sign are a person's, `world:left/t0rch/...`, never
  his trace, never undone. "brb" is human text, lowercase.
- **Rules.**
  - From a cave spot a participant stood in (world's `LoneTorch.trackCaves` spots), a natural cave branch nobody visited
    (chunks with `lastVisitDay` -1), at least `minDepthBelowSurface` 12 under the surface: walk natural cave air (not
    player-dug, walkable floor, bounded at 6000 cells) `pathMin` 30 to `pathMax` 90 cells to a floor cell under which
    natural stone runs straight down `shaftMin` 8 to `shaftMax` 30 blocks to lava (a source within 3 below the shaft's
    end, or a lava lake). Never within `baseClearance` 64 of any known base, nobody within `nobodyWithin` 32, all out
    of view, loaded, not protected.
  - Leave wall torches on the right-hand wall going in (from the known spot), every `spacingMin` 8 to `spacingMax` 12
    path cells, the first within 6 cells of the branch mouth (seen from the known cave); dig the shaft
    (`world:left/t0rch/shaft`); a floor torch beside the hole; an oak wall sign on the wall over it with line 1 "brb",
    not waxed (`world:left/t0rch/sign`). Text from `data/a1016_02/world/sc/t0rch.json`, word for word.
  - Once per world (`maxPerWorld` 1, saved in the tick it is left). Not a site in `SiteRegistry` (lore must not fill it).
- **Friends.** World-wide once.
- **Config `sc11` (v1).** `pathMin` 30, `pathMax` 90, `spacingMin` 8, `spacingMax` 12, `shaftMin` 8, `shaftMax` 30,
  `minDepthBelowSurface` 12, `nobodyWithin` 32, `baseClearance` 64, `maxPerWorld` 1.
- **Build.** `world.sc.TorchTrail` + card; `LoneTorch`'s cave spots; `TraceService.leave` and `remove` with left-by-others
  causes; one batch.
- **Cost and saves.** One bounded search per fire; the spot in `WorldData`.
- **Tests.** In a built cave corridor over a lava pocket: torches on the right wall at the spacing, the shaft reaches
  lava, the sign reads "brb", every cause is left by others; once per world; refused near a base, in a player-dug
  tunnel, without lava in reach, in view.
- **Force it.** `/a1016 sc 11 here` (from the cave spot nearest you, the visit rule skipped, view rules kept);
  `/a1016 fire sc_11`.

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
    spent; the next attempt is vanilla's. At most `maxPerWorld` 3.
- **Friends.** Everyone whose bed is within `sharedBedRadius` 8 of that bed is refused the same way for
  `sharedSeconds` 20 after the first refusal (one monster would refuse them all).
- **Config `sc12` (v1).** `shaftBelowMax` 2, `armNights` 3, `sharedBedRadius` 8, `sharedSeconds` 20, `maxPerWorld` 3.
- **Build.** `dig.sc.RestRefusal` + card; mixin `dig.mixin.ServerPlayerSleepMixin` on `ServerPlayer.startSleepInBed`
  at vanilla's monster check (make the list of monsters read as not empty while armed, so vanilla returns NOT_SAFE
  itself); arm in `DigData` per UUID (until, bed pos, count; old files load with none).
- **Tests.** Armed, shaft done, night: NOT_SAFE once, then sleep; no shaft: the card does not fit; a real monster:
  vanilla's refusal, arm kept; a friend's bed within 8 refused within 20 s; the arm runs out after 3 nights.
- **Force it.** `/a1016 sc 12 arm [noshaft] [player]`; grow the network with `/a1016 dig network grow <nights>`.

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
- **Config `sc13` (v1).** `pastBlocks` 40, `trailSampleTicks` 20, `trailLength` 96, `nextTorchWithin` 6,
  `nobodyWithin` 16, `maxPerWorld` 1.
- **Build.** `dig.sc.WayBack` + card; the trail ring in `DigTicker` (underground participants only); `Blind.isBlindLight`
  and `wasPlacedByPlayer` for the torches.
- **Tests.** A built 1x2 corridor with torches: after 40 past, it closes with the wall's own blocks and leaves the
  hollow, ledgered MOVE; refused in view, in a dug tunnel, without a pickaxe, with a friend within 48, in a 2-wide
  passage; undo opens it.
- **Force it.** `/a1016 sc 13 arm [player]` (within 10 minutes, the first point that fits); `/a1016 sc 13` prints the
  best point and why the others fail.

---

### lore

#### 14 The record (card `sc_14`, MINOR, Traces, TEXT, WATCHER + VISITOR, no fake; and three rules; `sections.sc14`)
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
  `TRACED`'s `placedByPlayer`. Never for left-by-others causes, the figure's dig, Ending D or give-backs.
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
- **Config `sc14` (v1).** `gapHours` 6, `nightFrom` "01:00", `nightTo` "04:30", `versionFromStage` 3,
  `versionAfterDays` 2, `seedFromStage` 2, `stats` true, `statsRadius` 64.
- **Build.** Server `lore.sc.TheRecord` (files, arms, `HerobrineEvents.TRACED` listener awarding `Stats.BLOCK_MINED` /
  `Stats.ITEM_DROPPED`), mixin `lore.mixin.SeedCommandMixin` (the success component); client `lore.client.WorldListMarks`
  and mixin `lore.mixin.client.LevelSummaryMixin` (`getLastPlayed`, `getWorldVersionName`, `backupStatus` as
  `UPGRADE_TO_SNAPSHOT`; check that vanilla's prompt reads the version through `getWorldVersionName`, else mixin the
  prompt's argument).
- **Tests.** The file written on stop with the right fields; arm, seen and clear (pure); the fake time (in the gap,
  prefers 01:00 to 04:30, deterministic); `/seed` gives the canon seed once from Proximity, then the real one; stats
  credited to the right player for a REMOVE of a placed torch and REMOVE_STACK, never for left-by-others or give-back
  causes. Dev client checklist for the list.
- **Force it.** `/a1016 sc 14 arm last|version [nogap]` (`nogap` skips the 6 hours and the night), `/a1016 sc 14 seed`,
  `/a1016 sc 14 reset`; then quit to the title and open Singleplayer.

---

### accident

#### 15 The block you stood on (card `sc_15`; MINOR, Proximity; DIG + PERSONAL; CARVER + STRIPPER; no fake; `sections.sc15`)
- **Experience.** You walk a path you take often. Seconds later, behind you, one block of the ground you just walked
  over is gone: a clean 1x1 hole, lava's glow at the bottom, or a drop into a cave. You walked right over it.
- **Why scary.** The ground is not safe, and he let you cross. DESIGN's lore: his pass "left the block someone was
  standing on". The care is almost still there. Every accident after this lands harder.
- **Deniable.** "Was that hole always there?" Caves under paths are common.
- **Guardrails.** None bent: a removal out of view, nobody on or near it, never a fatal drop.
- **Rules.**
  - Fire arms the target for `armMinutes` 20 of play. While armed, accident's route sampler looks at the cell the
    target's feet stood on `delaySecondsMin` 3 to `delaySecondsMax` 8 s ago, now at least `behindMin` 5 blocks behind
    them and out of view, on one of their routes (`RouteBook`, at least `minPasses` 3 passes), a natural full block
    (`Scan.takeable`, not player-placed, not a bridge), not protected, outside every armed trap's zone; under it within
    `cavityDepthMax` 6 a natural cavity: lava (glows), water, or air with a floor at most `airDropMax` 6 below.
  - Alone (`SoundCues.alone(target, 48)`), nobody within `nobodyWithin` 16 of the cell. `TraceService.remove`
    (`accident:sc15`). At most once per in-game day per player, `maxPerWorld` 6.
- **Friends.** PERSONAL; never with anyone within 16 of the cell.
- **Config `sc15` (v1).** `armMinutes` 20, `minPasses` 3, `delaySecondsMin` 3, `delaySecondsMax` 8, `behindMin` 5,
  `cavityDepthMax` 6, `airDropMax` 6, `nobodyWithin` 16, `maxPerWorld` 6.
- **Build.** `accident.sc.StoodOn` + card; a short ring of the last 10 s of standing cells per armed player in
  `RouteSampler`.
- **Tests.** Removes a natural block over lava 5+ behind after the delay (ledgered); refused for a placed block, in
  view, a friend within 48, no cavity, an air drop over 6, a trap zone; once per day.
- **Force it.** `/a1016 sc 15 arm [player]` (drops `minPasses`), then walk over ground with a cave under it.

---

### ending
No task. Every card and rule here stops with the common gates (quiet for good, path C, ended, last minute, Ending A's
peace). Ending D's afterward gives back through the ledger what is his: turned blocks (08), prints (09, 10), the closed
way back (13), the block you stood on (15), names (07, core's NAME undo); t0rch's trail stays (left by others) and the
world icon is put back (03). The record's version reads `a1.0.16_02` for good after a finished hardcore line (14 b).

### debug (SC-5)
`/a1016 sc` with no number lists the numbers and each one's state in one line (armed, done, counts), never names;
`/a1016 debug overlay sc` (dev only) adds per participant: the place factor (01), any arm (02, 03, 05, 06, 12, 13, 14,
15) with time left, tonight's empty night (04), fog places (10).

## Pacing

New cards join the decks. Alone gains one ambient (sc_06), Traces four minors and two ambients, Proximity three minors
and one major. Rates per tier are unchanged; the decks spread over more kinds of moment. Cards that need a base,
animals, a friend or a cave park when their moment never comes (`unfitParkMinutes`), so they never hold a tier. SC-5
re-runs the 192-seed sims with every card registered and `sc01`'s dread share: D-070's first-hour targets, the hard
limits over 20 h, and Traces and Proximity rates within 15% of DESIGN 4b, else it lowers `sc01.awayFactor`.

| Card | Feature | Tier | Stage | Tags | Habits | Fake |
| --- | --- | --- | --- | --- | --- | --- |
| sc_02 | 02 | AMBIENT | Traces | FOG | WATCHER | no |
| sc_05 | 05 | MINOR | Traces | SOUND, PERSONAL | VISITOR, WATCHER | yes |
| sc_05b | 05 | AMBIENT | Traces | MOB | WATCHER, VISITOR | yes |
| sc_06 | 06 | AMBIENT | Alone | SOUND, PERSONAL | WATCHER | yes |
| sc_07 | 07 | MINOR | Proximity | MOB | COLLECTOR, STRIPPER | no |
| sc_08 | 08 | MINOR | Traces | SCAR | VISITOR, COLLECTOR | no |
| sc_09 | 09 | MINOR | Traces | SCAR | VISITOR, WATCHER | yes |
| sc_11 | 11 | MINOR | Traces | LIGHT, SCAR | MOURNER, CARVER | no |
| sc_12 | 12 | MINOR | Proximity | DIG | CARVER, WATCHER | no |
| sc_13 | 13 | MAJOR | Proximity | DIG, PERSONAL | CARVER, STRIPPER | no |
| sc_14 | 14 | MINOR | Traces | TEXT | WATCHER, VISITOR | no |
| sc_15 | 15 | MINOR | Proximity | DIG, PERSONAL | CARVER, STRIPPER | no |

Not cards (capped and spaced on their own): 01 (a rate factor), 03 (once, on a sighting), 04 (a night roll, at most 5),
10 (on a sighting), 14 (b), (c), (d).

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
| D-080 | The self-created layer is a surprise: it lives on `self-created`, every player-visible id is opaque (`sc_NN` cards, `/a1016 sc <NN>`, `sections.scNN`), its docs are this file only, no DESIGN, TRY_IT, DECISIONS or ARCHITECTURE rows (one pointer line) until Mariano has played it | He asked not to know. He reads `/a1016` output, the config and the log |
| D-081 | Out-of-world records may lie: the world list's last played time and version (with vanilla's own backup prompt), the world icon, `/seed` and the statistics. Only the host's own world folder, only vanilla strings and the canon version and seed, never a deletion, each once (the version for good only after a finished hardcore line), the old icon kept and put back by Ending D | "What would make someone afraid to open their world again?" A haunted world file, a version that should not be |
| D-082 | Vanilla UI may say something untrue only when it is true in the story: the bed's "monsters nearby" only once the Under-you shaft stands under that bed; a friend's name tag hidden only at the fog edge, where he could be | The game admitting he is there, without the mod ever writing |
| D-083 | No new in-world text by him. The only new text is "brb" on t0rch's sign, left by a person | Guardrail 2 |
| D-084 | Places remember: each player's real fires leave places; the ambient and minor rate rises near them (up to 1.8x) and once in the trusted place (1.6x). Never in Alone, the first hour, a gap, a cap or a major slot | Dread of places, built by the director instead of by spectacle |
| D-085 | Fog places: a fog over one spot for 1 to 3 in-game days after he stood there, a core contract beside the fog spell, weather-like (sky-weighted, clears underground), counted where `FogLimits` counts | The spot you saw him becomes a spot you can see from home |
| D-086 | Names are removable things: `MobTamper.unname`, ledger kind NAME, undone by Ending D | He has no name; he takes them |
| D-087 | Entity fires `FIGURE_LEFT` with where he stood and the way he left; world leaves prints and the fog place | He was really there; one event, no cross-workstream call |
| D-088 | t0rch's torches [ours]: once per world, a trail of torches on the right-hand wall into an unexplored branch, ending at a 1x1 shaft to lava with a sign "brb". Left by others, never undone, not a SiteRegistry site | F23 "t0rch . lava"; "this is singleplayer" |
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
