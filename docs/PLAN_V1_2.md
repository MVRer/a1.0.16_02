# v1.2 plan: faster dread, fog nights, someone behind you

Oct 16, 2026 · from the playtest of 2026-10-16 (Mariano and a friend, LAN, 70 minutes, nothing noticed)

Every worker reads `docs/ARCHITECTURE.md`, this file's section for their task, and the decisions below. The
decisions are final; numbers in them are the new defaults. Where this file and older decisions disagree, this file
wins and D-070 to D-075 record why.

## What the playtest showed

- The first stage lasted 1 to 3 hours at 0.3 ambients per hour, capped at 1, split between friends, and its only
  sound card needed the player alone and still. A 70-minute first session most likely gets nothing.
- The one thing they saw was a fog drift (3 to 8 s of close fog, then gone in 5 s). It read as a glitch.
- The dusk fog is faint at stage 0 (0.15), full for about 40 real seconds, then 55%, and vanilla's day timeline
  multiplies the fog color down to about 6% (`#0f0f16`) between 11867 and 13670, so any fog at night is near black
  and reads as plain darkness.
- No card plays footsteps in a mine; every mine sound unlocked at Proximity (5 to 6 h).
- Existing config files and worlds keep their old numbers: config sections are not versioned, and the director's
  stage points are rolled once and saved.

## Decisions

### D-070 Pacing v1.2: something in the first hour
Something small and deniable in the first 10 to 25 minutes, 2 to 3 moments in the first hour, evidence-level
events from hour 2. Silence is still designed, just shorter.

Pacing (core):
| Field | v1.1 | v1.2 |
| --- | --- | --- |
| tracesStartMinHours / Max | 1 / 3 | 0.4 / 0.9 |
| proximityStartMinHours / Max | 5 / 6 | 1.75 / 2.75 |
| tempoShift | 0.4 | 0.25 |
| aloneMaxAmbient | 1 | 3 |
| tracesAmbientPerHour | 1 | 2 |
| proximityMinorsPerHourMin / Max | 1 / 2 | 1.5 / 2.5 |
| proximityMajorEveryHoursMin / Max | 2 / 4 | 1.5 / 3 |
| firstAccidentMinHours | 6 | 3.5 |
| emptySessionChance | 0.2 | 0.1 |
| pityStartHours | 1 | 0.5 |
| fogDriftMinSeconds / Max | 3 / 8 | 4 / 10 |

Unchanged: joinGraceMinutes 5, minorGapMinutes 15, majorGapMinutes 60, minAnyGap 5 (the literal-hour tests stay).

DirectorConfig (director):
| Field | v1.1 | v1.2 |
| --- | --- | --- |
| aloneAmbientPerHour | 0.3 | 3.0 |
| proximityAmbientPerHour | 2.0 | 2.5 |
| tracesMinorsPerHour | 0.25 | 1.0 |
| stageTagWeights ALONE | SIGHTING 0.15, SCAR 0.3, DIG 0.3 | SIGHTING 0.3, SCAR 0.6, DIG 0.6 (ACCIDENT 0, TEXT 0 stay) |
| stageTagWeights TRACES | SIGHTING 0.35, TEXT 0.3 | SIGHTING 0.5, TEXT 0.3 |
| empty sessions | from Traces | from Proximity |

Atmosphere: `distant_cave_sound` alone radius 48 to 24, and underground it no longer needs the player still (the
surface keeps 8 s still). `chest_opens` and `footstep_late` move from Proximity to Traces. Fog drift fade 100 to 160
ticks.

Targets the director's sims must show (registered deck, every tempo): first fire (fakes count) by 30 minutes of
play in at least 75% of runs and by 45 minutes in at least 90%; at least 2 fires in the first 60 minutes in at least
70% of runs; never more than 5 in the first hour; Traces inside 0.4 to 0.9 h times the tempo factor; every hard limit
held over 20 h.

### D-071 Fog weather: fog nights, fog mornings, fog days
One sky for the world. Some nights are fog nights, most are not; a fog night can carry into a grey morning; rarely a
whole day is foggy (the canon screenshot is daytime with fog). The fog is what the player saw in the drift: close,
thick, staying. On a fog night or day the director is more likely to act, and he can be seen in daytime fog.

- A fog spell: `FogSpell(fromClock, untilClock, level, quiet)` on the overworld clock (absolute
  `getDefaultClockTime()` ticks, so client and server agree and sleeping through a fog night lands in its morning).
  It eases in over `fogSpellRampTicks` (2500) from `from` and out over `fogSpellFadeTicks` (1500) before `until`
  (the same ease as the dusk fog, exponent 3). Its amount combines with the dusk fog (`Curves.combine`).
- Night spell: 11500 to 23500. Morning carry-over: the same spell runs on to 5500 of the next day. Day spell:
  23000 to 11500 (hands over to a night spell without a gap when both roll).
- Rolls (pure rule in core, chances installed from atmosphere's config; the director's sims use the same rule):
  - night 0 (the first night) is always clear; night 1 is foggy at 50%; if nights 0 to 2 had none, night 2 is
    foggy. After that `fogNightChanceByStage` {0.25, 0.33, 0.4, 0.45, 0.5}; never two fog nights in a row in Alone
    or Traces; after 4 clear nights in a row the next one is foggy.
  - morning carry-over: 0.35 of fog nights.
  - fog day: `fogDayChanceByStage` {0, 0.06, 0.1, 0.12, 0.12} per day not already covered; never two in a row; never
    in Alone.
  - level: night `fogNightLevelByStage` {0.45, 0.55, 0.65, 0.72, 0.8}; day `fogDayLevelByStage` {0.4, 0.45, 0.55,
    0.6, 0.65}. At a 12-chunk view a 0.45 spell ends the fog at about 75 blocks, 0.8 at about 37.
  - quiet: 0.25 of spells are quiet (no director boost), so a fog night is never a sure sign.
  - none once the world is quiet for good (Ending D's afterward, `Gates.quietForGood`), none in fixed-time dimensions.
- Look: at night the fog color is lifted from vanilla's near black toward `fogNightColor` (`#3b4048`) by
  `fogNightColorLift` (0.6) times the spell's amount, so the fog shows as a pale wall in the dark. By day it is
  desaturated toward `fogDayColor` (`#b9bec4`) by the amount. Normal nights keep the faint stage dusk fog as now.
  Iris shaderpacks draw their own fog; not supported (TRY_IT says so).
- Server and client fog stay the same math: `FogLimits.of(...)` takes the spell, so the figure's band follows a fog
  night even without a fresh client report.
- Director: during a spell that is not quiet, ambient and minor odds times `fogWeatherOddsFactor` (2.5), SIGHTING
  pick weight times `fogWeatherSightingWeight` (3), and a major slot due within `fogWeatherMajorPullMinutes` (60)
  may come now. Clear nights and days keep the normal odds.
- Sightings: a day spell or a morning carry-over is not clear daylight. In daytime fog only COW, WALKS_AWAY,
  BETWEEN_TRUNKS, ACROSS_WATER and CLOSE may come (RIDGE keeps its dusk sky, IN_THE_LIGHT its night).

### D-072 Someone behind you (the tunnel ladder)
Real vanilla footsteps behind a player mining or walking a straight player-dug tunnel. Each rung only after the one
before it happened to that player; it never leaves a block change (Mariano: no edits, keep it light).

- Where: underground (`DigTicker.underground`), in a straight 2-high run of player-dug air (footprint
  `wasDugByPlayer`, anyone's) at least `ladderMinRun` (16) cells long behind the player along their heading (the
  horizontal axis of their last 10 s of movement, else their look), floor solid, walls sealed on most cells, no fluid.
- Alone: no other participant within `sharedMomentRadius` (48), so a friend can't confirm it (and asks "was that
  you?"). Never within 30 s of combat, never riding, swimming or in fluid.
- Rung 1 `steps_behind_once` (AMBIENT, Alone): one or two steps 10 to 12 blocks back, then nothing. Fake: the
  player's own step, late.
- Rung 2 `steps_behind_coming` (MINOR, Traces; needs rung 1): 6 to 10 steps from about 16 blocks back closing to 4 to
  6 blocks, then silence. Walking cadence 8 ticks a step (a sneaking variant at 13 ticks, quieter). If the player
  turns to face back (look within 70 degrees of the tunnel behind) the rest stops at once. Fake: a few steps far off
  that fade away.
- Rung 3 `steps_behind_glimpse` (MINOR, Traces; needs rung 2 at least 20 minutes of the player's play ago): the steps
  come up to a spot 14 to 20 blocks back, past the torchlight (block light 3 or less), and stop; he is there
  (D-073). If the player turned before the last step, no figure, the steps just stop. Tag SIGHTING too, so it shares
  the one-sighting-a-day limit.
- Steps are the floor block's own step sound (`SoundType.getStepSound`, stone if not solid), `SoundSource.PLAYERS`,
  at the cell's floor, played with `SoundCues.playAt` (a step's range is 16 blocks, so friends 48 away never hear
  it). Volume rises as they near; tuned to be heard at 50% master volume.
- Memory per player (DigData): highest rung heard, the play tick of the last ladder event, the tunnel runs used (a
  run start within 24 blocks of a used one never fires again), the day of the last glimpse.
- Spacing: at most one ladder event per 25 minutes of the player's own play; rung 3 at most once per in-game day and
  2 h of play apart. Rungs 2 and 3 can repeat after their first time.
- Cost: the detector runs only for underground participants, every 10 ticks, walking back at most 32 cells; cues are
  packets only. No world edits at all.

### D-073 The glimpse: the one figure that vanishes in view
An exception to D-012, D-034, D-035 and DESIGN guardrail 4, for this one variant only. Underground the dark stands in
for the fog; you catch him at the edge of your eye and he is gone before you look straight at him.

- Variant `GLIMPSE` (entity): not a director card of its own (spawned only by rung 3 through core's `Figure`
  contract), spot given by the caller, ending VANISH, never rushes or goes under, no fake, silent, no particles.
- Eyes FLAT, per figure: a synced entity field carries the eye style; the glimpse is always FLAT, every other variant
  keeps the configured style. He is lit like the terrain, so past the last torch he is a dim shape.
- Distance: at least `glimpseMinDistance` (14) blocks from every player (the 24-block surface rule does not apply
  underground), spawned out of everyone's view as always.
- The vanish, client side: the renderer stops drawing a glimpse figure, for good, the first frame his center is
  within `glimpseHideDegrees` (28) of the camera's look or within 10 blocks. Server side: he is removed the tick the
  player he appeared for looks within `glimpseServerDegrees` (35), steps 2 blocks toward him, or has seen him for 3 s,
  and out of view after `glimpseWaitSeconds` (10) unseen. A frame-exact client hide means a fast turn still shows him
  for a few frames, a slow turn longer; nobody ever sees him centered.
- `seenWhenRemoved` is allowed only for GLIMPSE. A glimpse is not a stare: no STARED_AT_HIM, no `entity:stared`.

### D-074 Friends v1.2
Together is shared atmosphere; apart is where the personal things happen.
- Two or more players sharing a moment roll `groupRateFactor` (1.5) times one solo ambient and minor rate between them
  (v1.1: exactly one solo rate). Apart, each keeps the solo pace.
- `CardTag.PERSONAL`: a card for one player alone (the ladder). The director never draws it while the player has
  sharers, and a held PERSONAL card goes back into its deck as soon as a sharer comes close, so it never blocks its
  tier while friends stand together.

### D-075 Versioned config and world rules
- Every config part has a version: the file's root `configVersion` (Pacing and the root), and a `version` in each
  section. Missing means 0, which is v1.1 or older.
- On load each part is upgraded one version at a time; a step replaces a value that still equals the old default with
  the new default and keeps anything the player changed; then the new version is written. Every later default change
  adds a step.
- Worlds: the director's world memory gets a `rulesVersion`; a world saved before v1.2 re-rolls its stage points in
  the new windows on load (a stage already reached stays reached), and every player's session minor rate and next
  major slot are rolled again at their next session.

## Waves

Work happens in git worktrees, one branch per task, at most 4 workers at once. A worker only edits its own workstream
paths (ARCHITECTURE "Packages and ownership"); a core change it needs goes in its report as an ask. Every worker
finishes with `./gradlew build` green on its branch (all game tests), commits, and reports in at most 40 lines:
branch, commit, test count, what it built, asks, anything left open.

| Wave | Task | Who | Depends on |
| --- | --- | --- | --- |
| 0 | C-1 core batch and docs | one worker for the orchestrator | - |
| 1 | W-DIR director | worker | C-1 |
| 1 | W-ATMO atmosphere | worker | C-1 |
| 1 | W-ENT entity | worker | C-1 |
| 1 | W-DIG dig | worker | C-1 |
| 2 | Merge in order W-DIR, W-ATMO, W-ENT, W-DIG, build green after each; INT-1 integration; preview jar | orchestrator, one worker for INT-1 | wave 1 |
| 3 | Bug hunt R1 to R3 (read-only, adversarial, in parallel) | 3 reviewers | wave 2 |
| 3 | FIX-x per finding group | workers | the reviews |
| 4 | VERIFY: full check, 3/3 green, docs match code | one verifier | wave 3 |

## C-1 core batch (wave 0)

Core and docs only. Gives wave 1 every shared contract so the four workers never wait on each other.

1. Config versioning (D-075): root `configVersion` in `ModConfig`; `Pacing` upgrade step 0 to 1 with D-070's numbers
   (replace only values equal to the v1.1 default); a small public helper for sections
   (`ModConfig.Upgrades`: keep-or-new for double, float, int, long, boolean, String, float[] and the tag-weight map,
   with a float epsilon), and `ModConfig.saveSection(name, value)` if sections need it. Section owners write their
   own steps in wave 1. Test: a v1.1 file (no versions, old defaults, one hand-changed value) loads into the new
   defaults with the changed value kept, and is written back with `configVersion` 1.
2. Pacing defaults per D-070 (including the fog drift seconds).
3. Fog weather (D-071): `FogSpell` record (codec, stream codec), stored in `HerobrineState.Effects` as an optional
   field (old saves load as none); `ClientEffects.FogSpell` payload, `setFogSpell(server, spell|none)` (stored and
   sent to everyone), `Sync` carries it; `FogSpell.amount(clockTicks, rampTicks, fadeTicks, exponent)`;
   `FogWeatherRules` (pure: the night, morning and day rolls from a random, the night index, the stage and the recent
   history; chances, levels and the quiet share as an installable record like `FogLimits.installShape`, defaults
   equal to D-071); `FogLimits.of(...)` takes the spell and combines its amount with the dusk fog. Tests: codecs round
   trip, a v1.1 state loads with no spell, the roll rules (first night clear, third guaranteed, no two in a row in
   Alone, the 4-clear rule, the quiet share near 0.25 over 10 000 rolls), FogLimits with a spell.
4. `CardTag.PERSONAL` (D-074), Javadoc'd.
5. `core.Figure` contract (D-073): `boolean canGlimpse(ServerPlayer player, Vec3 feet)` and
   `FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw)`; `Figure.Stub` (false, NO_SPOT);
   `Services.figure()` / `Services.installFigure(...)`.
6. Docs: D-070 to D-075 into DECISIONS.md (one row each, from this file); ARCHITECTURE for config versions,
   FogSpell/FogWeatherRules/FogLimits, `CardTag.PERSONAL`, `Figure`; DESIGN.md: 4b pacing table and its bullets, the
   Stage 0 line, guardrail 4's exception, Sightings' distance note, new event catalog rows (fog nights, the three
   ladder rungs, the glimpse, far in the rock), the fog weather paragraph under "The world is wrong" or "Sightings";
   version `1.2.0-26.3` in gradle.properties; TASKS.md rows for every task here.

## W-DIR director (wave 1)

- DirectorConfig per D-070, with a section `version` and upgrade step 0 to 1 (`ModConfig.Upgrades`).
- Empty sessions from Proximity only.
- D-074: `groupRateFactor` (1.5) at the split in `DirectorBrain.due` (`perHour *= (sharing > 0 ? groupRateFactor : 1)
  / (1 + sharing)`), solo path untouched (solo sims byte-identical where the defaults allow; the defaults changed, so
  compare against a v1.2 solo baseline). PERSONAL: never drawn with sharers present; a held PERSONAL card goes back
  at once when a sharer appears.
- D-071 boost: an `Env` method for the fog spell state (live: HerobrineState effects and the overworld clock; sim:
  rolled per in-game day with core's `FogWeatherRules`), applied to the ambient and minor odds, the SIGHTING weight
  and the major pull-in; quiet spells give no boost.
- D-075 world part: `WorldMemory.rulesVersion`; a v0 world re-rolls `tracesAt` and `proximityAt` in the new windows
  (never moving back a stage already reached); `PacingMemory` re-rolls the session minor rate and next major slot at
  the next session.
- DirectorSim models fog spells and their boost.
- Tests: update every test the v1.1 numbers pinned (the 1.25 group upper bounds become 1.7, lower 0.7 becomes 1.2 for
  together; `softRatesLandAtTheLowEndOfFourB` to the new 4b; `DebugGameTests.limits()` fixture stays as it is unless
  it reads defaults); new: D-070's first-hour targets, fog spell hours at least 2x the fires per hour of clear hours,
  PERSONAL never drawn with a sharer and given back when one comes, a v0 world re-rolls into the new windows.
- Report the DESIGN 4b numbers the sims actually show (the orchestrator writes them into DESIGN).

## W-ATMO atmosphere (wave 1)

- AtmosphereConfig: section `version` and upgrade step 0 to 1 (the changed defaults below); new fields for D-071
  (chances, levels, quiet share, ramp and fade ticks, colors and lift), installed into core's `FogWeatherRules`.
- Fog weather live (D-071): roll at clock time 11000 (tonight, with the morning carry-over) and at 23000 (a fog day,
  when nothing carries over); history in AtmosphereData; store and send with `ClientEffects.setFogSpell`; nothing
  during Ending D's afterward or once quiet for good.
- Client: the spell's amount combined with the dusk fog in `ClientAtmosphere.applyFog`; the night color lift and the
  day desaturation on `FogData.color` at the same mixin point; continuous in every input (no pops). The client fog
  report already reads the final frame, so the figure follows it.
- Commands: `/a1016 fog` (the current spell, tonight's and today's roll, history), `/a1016 fog night|morning|day|clear
  [quiet]` (sets a spell now, for testing), op level 2 like the rest.
- D-070 atmosphere changes: cave sound alone radius 24 and no stillness underground; `chest_opens` and `footstep_late`
  to Traces; fog drift fade 160.
- New AMBIENT card `far_in_the_rock` (Alone, tag SOUND, no habits): underground, no other participant within 24, one
  vanilla sound of the rock around (a stone or deepslate hit or break, or a gravel slide) 18 to 28 blocks off, below
  or to the side inside solid rock, faint. Fake: the vanilla cave sound. Through `SoundOut`.
- Tests: the live roll schedule (first night clear and so on, using core's rule), the payload on join and on change,
  a v1.1 atmosphere section upgrades, the client curve is continuous across every boundary (pure `Curves` tests), the
  color lift is 0 with no spell and grows with it, FogLimits on the server matches the client with a spell, the new
  card's gates (alone 24, underground) and its sound lands inside rock.

## W-ENT entity (wave 1)

- GLIMPSE variant per D-073: excluded from card registration (update `everyVariantIsACard` and any loop over every
  variant), Spot given by the caller, Ending VANISH, `mayRush` and `mayGoUnder` false, Fake NONE.
- Implement and install core's `Figure`: `canGlimpse` (he is not out, the feet spot loaded and ticking, two cells of
  air on a solid floor, block light at his feet and eyes 3 or less, out of every player's view, at least 14 from
  everyone) and `glimpse` (spawns facing the player, `appearedFor` that player, no surface spacing record, his own
  last-glimpse day in EntityData).
- Per-figure eye style: a synced field (like `DATA_LOW`), copied into the render state; `HimEyesLayer` uses it before
  the global config; GLIMPSE sets FLAT.
- The vanish: client renderer hide latch (`glimpseHideDegrees` 28, 10 blocks); server removal rules of D-073;
  `seenWhenRemoved` expected only for GLIMPSE.
- Fog days (D-071): `SightingRules.clearDaylight` is false during a day spell or a morning carry-over (pure rule plus
  its test); the daytime variant list; FogEdge's server estimate through `FogLimits.of` with the spell.
- EntityConfig: new fields, version 3 to 4 (`ModConfig.Upgrades` or the existing pattern).
- Tests: canGlimpse refuses in light, in view, too close, while he is out; glimpse spawns FLAT and is removed when
  looked at within 35 degrees and when the player steps toward him, and out of view after the wait; the client hide
  angle math (pure); daytime fog lets COW and CLOSE through and keeps RIDGE out; clear daylight still blocks without
  a spell; two players: the glimpse spot is hidden from the friend too.

## W-DIG dig (wave 1)

- The ladder per D-072: a package-private tunnel detector (straight run, heading, floor cells; any player's dug
  footprint), the three cards (registered with the director; tags SOUND and PERSONAL, the glimpse also SIGHTING; no
  habits), memory in DigData per UUID (codec or NBT like its other per-UUID maps, old saves load), cancellable step
  cues (cancel the rest when the player turns to face back), and rung 3 calling `Services.figure().canGlimpse` before
  the steps and `glimpse` at the last step.
- Forced fires (`/a1016 fire <id>`) skip the rung order and spacing, never the alone or tunnel rules.
- DigConfig: section `version` and the new fields (step cadence, volumes, distances, spacing), upgrade step 0 to 1.
- Debug: `/a1016 dig steps [player]` prints that player's rung, last event, used runs and whether the detector sees a
  run now.
- Tests: the detector finds a built straight dug tunnel and refuses a natural cave, a bend, water and a short run;
  rung 1 to 3 order and spacing; the steps approach and stop 4 to 6 back; turning around cancels the rest; a friend
  within 48 blocks means no fire and a friend 60 away hears nothing; the same run never fires twice; rung 3 asks
  `canGlimpse` and calls `glimpse` at the last step's cell (a recording Figure in tests); no TraceService edit ever.

## Wave 2: merge, INT-1, preview jar

- Orchestrator merges each green branch into main in the order W-DIR, W-ATMO, W-ENT, W-DIG; `./gradlew build` after
  each merge; a red build goes back to that branch's worker.
- INT-1 (one worker, after the merges): the debug overlay lines (fog spell, the ladder rung for each player);
  ARCHITECTURE and DESIGN for what wave 1 built (from the workers' reports and the code); a TRY_IT section "v1.2"
  (force a fog night, fire each rung, the glimpse, check `/a1016 director` and `/a1016 fog`, a note that Iris
  shaderpacks hide the mod's fog); `./gradlew build` green.
- Preview jar `dist/a1016_02-1.2.0-preview.1-26.3.jar` from main.

## Wave 3: bug hunt and fixes

Three reviewers in parallel, read-only, each on the merged main, each finding backed by a failing game test or an
exact reproduction, sorted HIGH, medium, low:
- R1 core, director, config and world migration: the version steps (a hand-changed value kept, a missing section, a
  partial file), the re-roll, the group factor and PERSONAL with three players, the fog boost with quiet spells, the
  20 h sims, hard limits.
- R2 fog weather end to end and the daytime sightings: server and client agree, no pops at the spell edges or at
  sunrise and sunset, sleep through a fog night, relog during a spell, `/time set`, the Nether and End, Ending D's
  afterward, fog days and the figure's band.
- R3 the ladder and the glimpse: tunnel detection edge cases, two and three players, a friend arriving mid-steps,
  the vanish rules (never centered, never seen walking away, removed in every path), the one-figure rule, server cost
  per tick, guardrails (no edit, no spawned mob, timings from config).

Then one FIX worker per workstream with findings, in parallel, each with the failing test turned green.

## Wave 4: VERIFY

One fresh verifier: `./gradlew build` three times in a row (all green), every finding of wave 3 re-checked against
its test, docs against the code (ARCHITECTURE contracts, DECISIONS D-070 to D-075, DESIGN numbers, TRY_IT commands
work), a copy of a real v1.1 config file (`run/config/a1016_02.json`, read only) upgraded in a game test, and the
guardrail grep (every block edit through TraceService, no mob spawned, no hardcoded faster timings). Its report
says ready or lists what is not. Then a final preview jar.

## Out of scope
- Iris and other shaderpacks (they replace the fog).
- Pushing, tagging or a GitHub release: only when Mariano says.
