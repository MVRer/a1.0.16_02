# Self-created 2: his places

Oct 24, 2026 · lead design for the second wave of the self-created layer · branch `sc2/plan`, from `self-created`
(`6e811ab`, 834 game tests green)

**Spoilers. Mariano plays this blind. He must not read this file, the `sc2/*` branches' commits, the gallery
PNGs or the SC2 test names.** Everything here lives on `self-created` and its `sc2/*` branches; `main` is untouched.
`docs/SELF_CREATED.md` (the first wave, features 01 to 15) stays the reference for everything this file does not
change; read its spoiler rules, guardrail table, core contracts and common gates first.

## Status

Built through wave 3 (SC2-0a to SC2-9) on `self-created`, with SC2-H's audit of the places against the world's height
and later worldgen (its "As built" at the end); reviewed (wave 4, fixed per the "Fixed in review" notes) and verified
(wave 5, SC2-V): READY after FIX-FINAL, with a few checks left for Mariano's real install ("Verified (SC2-V) and
FIX-FINAL", after the verifier's checklist). Each feature's
section names the task whose "As built" note (under "Tasks") says what was built and where it differs from the plan. Tasks, waves, reviews and the verifier's checklist are at the end. Every worker reads
`docs/ARCHITECTURE.md`, `docs/SELF_CREATED.md` ("Spoiler rules", "Guardrails", "Core contracts", the common gates
under "Features"), this file's sections "Principles", "Spoilers", "Guardrails", "Multiplayer", "Fair danger",
"The gallery", the section of each feature in their task, and the decisions D-092 to D-107.

## Vision

The first wave made the world slightly wrong, close to you, once each: a furnace facing your bed, a moon that is not
there, a name gone from your dog. Mariano noticed: "Its feeling fine, i can notice things, but it has so much
potential to be a really good horror mod. The structures are still pretty basic." Noticing is not fear. What he asks
for next is the other half of the 2010 feeling: something much bigger than you is in this world, has been here
longer than you, works to a plan you can almost see, and can actually kill you. "Stuff that will make you feel
frightened by a bigger force." "Like the world has something really wrong."

This wave adds three things and turns one more against the players.

- **His places (the remains).** Enormous things built from the oldest blocks the game has: a brick pyramid as tall as
  a mountain with its own negative dug out under it, a record room of bookshelves cut into the rock and lit by
  glowstone that has no business in the overworld, a mountain with nothing inside it, a black line from bedrock to the
  sky, a square of the world flattened like a test floor with a little house in the middle. Seen from far away, laid out
  on lines from the world's origin, their doors facing where the world began. Dark or too bright inside, and they
  answer when you go in. They are not deniable. What happens to you inside them is.
- **His instruments.** The animals and the monsters, moved like pieces. A ring of sheep walking widdershins under the
  moon, nose to tail, a snake chasing its own tail. Every mob around you, zombies mid-chase included, stopping at once
  to look the same way while the world goes silent. A cow that keeps up with you across the map and stands at your
  window at night. A file of animals walking into a pyramid and never coming out.
- **His teeth.** Hunts with vanilla mobs: a wolf pack that howls, stalks you at the fog edge for a minute and a half,
  and comes for you if you are still in the open; a fog night when every monster near you knows where you are. Told
  before, seen, escapable, and lethal if you do nothing. Fair in hardcore, marked like every death of his.
- **The friend you play with.** Seen standing on a hill at night with their name over them while they swear they are in
  their mine. Your things in their chest. The door they found, gone when you go to look. Their pickaxe heard in the rock
  where they are not.

### Principles (they hold over every feature)
1. **Vanilla only.** Blocks of the first versions (bricks, cobblestone, mossy cobblestone, oak planks, bookshelves,
   glowstone, obsidian, glass, wool) and today's vanilla blocks for structure; vanilla mobs behaving within what a
   vanilla mob can do; vanilla sounds, loud enough to be noticed at about 10 blocks (volume 1.0 or more, heard to 16).
   No custom mob, sound, texture, particle, GUI or model. No jumpscare, screamer, chase music, lightning, red or glowing
   eyes, "joined the game", teleporting stalker, or monster of our own (the clichés of the fog and dweller mods).
2. **Scale is the scare for places, behaviour for mobs, consequence for danger.** A remains is a thing you walk toward
   for minutes; you choose to go in; it answers. A mob behaviour is wrong because a player knows mobs; it needs no
   effect. A hunt is a real chance to die, with every chance to live.
3. **He darkens a place before he appears** (Mariano's rule, kept from the first wave). Every sighting in his places
   comes after the light there goes.
4. **Fair danger** (D-097, below). Nothing unmarked, nothing unavoidable, nothing without a tell and a way out.
5. **Multiplayer first** (D-098, below). Every feature says who sees it, who sets it off, what a friend standing next to
   them sees, and why "did you see that?" "no" does not end it.
6. **Nothing in Alone, nothing in a player's first hour.** No new card is earlier than Traces, no remains generate in
   chunks decided before Traces; D-070's first hour is measured unchanged.
7. **What shaders cannot hide** (D-102). Mariano plays with Iris and Sodium. The mod's fog and sky drawing are hidden or
   kept vanilla under a shaderpack, so this wave's main moments are blocks, mobs, items, sounds and light levels. Fog
   is a bonus where it shows.
8. **Runtime is reversible, worldgen stays** (D-093). Every runtime edit goes through `TraceService` (ledgered, undone
   by Ending D and the switch where the first wave's rules say so) or `MobTamper`; the remains are worldgen and stay.
9. **The gallery decides** (D-099). No visual feature is done until its own screenshots, taken in a flat test world,
   are genuinely striking, and the art director agrees.

## What is weak now (diagnosis)

Blunt, so the wave fixes the right things.

1. **The layer makes you notice; it never makes you afraid.** Every first-wave feature is small, close and deniable by
   design: one furnace turned, a name gone, a step behind you, a moon missing, a bed refused once, a hole behind you.
   That was the brief, and it works (he noticed). But there is no awe and no dread of a place you have not been yet.
   Nothing is seen from far away, nothing is worth a journey, nothing is bigger than the player.
2. **The structures are basic, and the layer has none of its own.** The base mod's scars are small on purpose (huts,
   crosses, canon-sized sand pyramids, 2x2 tunnels, a stair, dead mountains, bare groves): each reads as odd worldgen.
   None has an inside worth entering, none is dangerous to enter, none answers you, none has scale. The first wave
   added only edits to the player's own things.
3. **Mobs are only held still.** `MobTamper` can freeze, face, silence and move a mob out of view. So every mob moment
   is "they stood still and looked". No group behaviour, no formation, nothing that says the animals are being used.
4. **No fear for your life.** The layer may never strike, and the accidents are rare (the first after 3.5 h of play
   at the earliest), deniable and one-shot. A hardcore player is almost never hunted, never has to run, never hears
   something coming. The Under-you network is the one lethal setup, and it is hidden under the floor.
5. **Multiplayer is handled defensively.** PERSONAL, the alone rule and the shared moments keep friends from seeing
   what they should not. Only 02 (a friend's missing name) uses the friend. Nothing sets friends against each other or
   makes one doubt the other, and splitting up costs nothing.
6. **No intelligence.** Each feature is a one-off. Nothing points at anything else, nothing is laid out, nothing
   suggests a plan.
7. **Half of it is invisible on his real install.** Iris draws its own fog and sky: the fog nights, the fog places
   (10), 02's fog band and 04's new moon (kept vanilla under a shaderpack) mostly do not reach him.
8. **Nobody looked at it.** There was no way to see a feature on its own: everything had to be forced in a survival
   world, so the visual quality was never judged. This wave starts with a flat gallery and screenshots (SC2-0c).

## Research notes (brief)

- **The first versions.** Infdev 20100227-1 generated brick pyramids (to supply bricks, and to test large structures;
  reported around 127 by 127 at the base and 64 tall, empty inside, removed a month later) and obsidian walls along the
  X and Z zero lines as debugging markers, from before the F3 coordinates. Indev spawned the player in a small wooden
  house. Glowstone came with Alpha's Halloween update and has never generated in the overworld. Bookshelves, mossy
  cobblestone, bricks and glass are Indev blocks still in the game. ([Minecraft Wiki: Infdev
  20100227-1](https://minecraft.fandom.com/wiki/Java_Edition_Infdev_20100227-1), [Brick
  Pyramid](https://minecraft-archive.fandom.com/wiki/Brick_Pyramid), [removed
  structures](https://sportskeeda.com/minecraft/minecraft-forgotten-structures-the-history-removed-structures)). These
  are our remains: the developer's test builds, still in the seed (DESIGN: "he comes with the world").
- **The creepypasta** (DESIGN's canon table): fog, a figure that is not a cow, empty eyes, 2x2 tunnels, sand pyramids,
  leafless trees, deletions, "Stop.", a list, "he is no longer with us". Nothing in the canon is a monster or an attack;
  the fear was that the world itself was altered by someone. This wave keeps the figure as rare as ever and makes the
  alterations big.
- **Liminal spaces and analog horror.** Familiar but empty, too regular, too long, too bright, transitional, made
  for a purpose that is gone; repetition until you are lost; an instructional, institutional voice. In vanilla terms:
  repeated identical cells, an aisle longer than it should be, a lit room deep underground, a flattened square with a
  house in it, perfect geometry in the dark, exact coordinates.
- **What the other horror mods do, to avoid it.** Cave Dweller: a custom creature that hunts by sound, climbs walls,
  breaks doors, jumpscares. The Man From The Fog: a custom stalker with its own sound, then lightning and an attack.
  From The Fog: a Herobrine with fanon trappings. ([CurseForge blog: horror
  mods](https://blog.curseforge.com/5-best-minecraft-horror-mods-on-curseforge-to-play-right-now/)). We do none of it:
  no creature, no custom sound, no lightning, no shrine (the gold and netherrack shrine is excluded fanon; this wave
  avoids redstone torches on mossy cobblestone for the same reason).

## Spoilers for this wave (D-103)

D-080 holds and is extended:
- Feature numbers continue: 16 to 31. Cards `sc_21`, `sc_22`, `sc_23`, `sc_24`, `sc_27`, `sc_28`, `sc_29`, `sc_30`,
  `sc_31`, `sc_31b`, `sc_31c`; the two hunts are accident trap kinds `new TrapType("sc25")` and `"sc26"`.
- Registry ids are opaque: structure type `a1016_02:world/sc2`, piece type `a1016_02:world/sc2p`, placement type
  `a1016_02:world/sc2r`, structures `a1016_02:world/s16` (and `s16d` for a variant set if needed), `s17`, `s18`, `s20`,
  feature `a1016_02:world/s19`, structure sets and biome tags under the same `world/sNN` names. `/locate structure`
  tab completion then shows only `a1016_02:world/sNN`. The rays placement is not one vanilla's locate can follow
  (fine: nothing points the player at them).
- Commands: `/a1016 sc <NN> ...` with letters and the plain verbs `arm`, `force`, `fake`, `here`, `reset`, `clear`.
  The gallery is `/a1016 sc g ...`, registered only in a development environment.
- Config: `sections.sc16` to `sections.sc31`, keys `a`, `b`, ... (each feature's Config line maps them), `version`.
  The structures' rarity lives in their structure set JSON (data, opaque path), the rest in the section.
- Log lines at INFO and above name only `sc_NN` / `sNN`. Gallery exhibit ids are the numbers (`16`, `16s`, ...), view
  names are camera words (`far_day`, `in_core`), PNGs go to `build/gallery/<exhibit>/<view>.png` (never committed).
- `core.VerifyScSpoilerTests`' word list gains this wave's words (SC2-0a), matched as the test already matches: pyramid,
  brick, archive, library, record, bookshelf, glowstone, hollow, mountain, obsidian, ring, circle, snake, procession,
  breath, follower, wolf, wolves, pack, howl, hunt, horde, friend, elsewhere, ghost, borrow, apex, aisle, trunk, pit,
  marker, plus the generic ones (line, wall, floor, house, file, call, chest, book, map, door, steps) only if no existing
  layer line trips them; a word dropped for that reason is named in SC2-0a's report and watched by hand by R13. Every new
  key, literal, usage line and log line must pass it.
- Gallery view and frame names in code are camera words (`far_day`, `far_dusk`, `far_night`, `far_fog`, `mid`, `near`,
  `mouth`, `aerial`, `section`, `plan_*`) or numbered (`in_1`, `survey_1`, `f_1`); the descriptive names this file uses
  for them (`in_core`, `ring_top`, ...) are descriptions, written beside each file in `build/gallery/<id>/index.txt`,
  so the dev-only `sc g` arguments pass the spoiler test too.
- Docs: this file only, plus SC2-0a's one pointer line in ARCHITECTURE ("and `docs/SELF_CREATED_2.md`").

## Guardrails: held and bent

| Rule | In this wave |
| --- | --- |
| DESIGN 1, he never strikes | **Bent (D-096, D-097) for the two hunts only (25, 26).** He still never touches anyone; wolves and monsters do what they do, set on one player as an armed accident trap, with tells, a way out and a marked death. Everything else holds: the remains' dangers are vanilla (darkness spawns monsters; a closed tunnel can be dug) |
| DESIGN 2, he never writes | Held. The archive's records are blank written books titled with the list's names and each known player's own name (D-100, the `[PLAYER NAME]` precedent of F06 and F23). A player's own book is moved, never copied or edited |
| DESIGN 3, 4 | Held. 27 shows a friend, not him, and never his look. His sightings at his places follow the existing variants (31: the ridge's stare-and-leave at the apex; D-073's glimpse in the dark of the archive and the pit, D-105) |
| DESIGN 7, he only removes or moves | Held, read for the remains (D-094): the brick pyramid, the obsidian line, the record room and the test floor are the developer's half, things put in, the first versions' test builds; his marks on them are removals (the pit under the pyramid, the emptied shelves, the cut faces, the breaches, the hollow mountain). At runtime he only removes (glowstone, torches) and moves (blocks into a tunnel, a torch to another cell, a stack to another chest, a book to his lectern, mobs). Nothing is spawned or created |
| ARCH 1, every change through `TraceService` | Held. Worldgen exempt as before (code guardrail 1) |
| ARCH 2, never target the player; never spawn | **Bent (D-096)**: `MobTamper.hunt` may set a mob on a player, called only by accident's hunts (a source test holds the callers). Never spawn: held (the gallery's dev-only staging summons test mobs; no game code does) |
| ARCH 3, nothing changes in view | Held. Mobs walking in a ring or a file are mobs walking (visible by design); every block edit and every mob teleport is out of view |
| SC rule 3, reversible when switched off | **Bent (D-093) for the remains only**: what generated stays (taking back a 300 000-block structure needs the terrain that was there); the switch stops new ones generating and stops every behaviour in and around them. Every runtime edit is ledgered and given back as the first wave's reverts do |
| D-082, vanilla UI lies only when true in the story | **Bent (D-098) for 27**: a friend's name tag where they are not. The locator bar stays honest, so the lie can be caught and never resolved |
| Layer rule "deniable first" | **Bent for the remains (D-092)**: the bigger force is not deniable. What happens inside them and around you is, the first time |
| Common gates: never within 30 s of the target's combat | **Bent for 23 only (D-101)**: the held breath may come mid-fight; it only gives the player seconds |
| DESIGN 4b, the first armed accident not before hour 3.5 | **Bent for the two hunts only (D-107, R10-4)**: their own clock, from 2.5 h of play times the tempo and never before Proximity, one per 2 h, the major gap. Every other trap keeps 3.5 h; the tier rates hold (each hunt is a major, and a later one takes a major slot due within the hour) |

## Fair danger (D-097)

Every threat of this wave (the hunts 25 and 26, and the answers in his places: a closed way in, darkness behind you)
keeps all of these. Each is a game test in its task.

1. **Told.** A hunt never turns hostile before at least 60 s of tells, two of them audible at 16 blocks or more
   (vanilla sounds), and at least one of its instruments visible to the target at some moment (inside the fog end,
   inside their view cone). A darkening is seen as darkness coming, bay by bay; a closed tunnel is found, not felt.
2. **A way out.** A hunt starts only if the target carries a way out right then (2+ placeable full blocks, or a pickaxe,
   shovel or axe) and its instruments cannot reach a player 2+ blocks above the ground they stand on (wolves) or behind
   a closed iron door (monsters). A way in is closed only if every player inside carries a tool that digs the material
   (a pickaxe for brick and stone); darkness never falls on the cell a player stands in or the next one ahead.
3. **A health floor.** A hunt never starts under 14 health; a closing never happens to a player under 10.
4. **Marked.** A death in a hunt is a marked death (`DeathMarker`: the cross, the cause on the list, the events): the
   hunt is the target's armed trap (`AccidentPlanner`, a `TrapType`), so `causedBy` claims it.
5. **One at a time, paced.** A hunt is the player's one armed trap, on its own clock (FIX-SC2-PACE, R10-4: from `sc01 x`
   2.5 h of play times the tempo, never before Proximity, instead of `firstAccidentMinHours`; one per `sc01 y` 2 h; the
   major gap; once per session by the planner); never two hunts within 128 blocks of each other; a player's answers in his places
   come at most one of each kind per in-game day and never in their director quiet (`Director.inQuiet(server, uuid)`;
   review R10-7: a player's first time of each answer comes in their quiet too).
6. **Home is safe.** No hunt starts within 64 of the target's base; nothing hunts inside a house.
7. **No hidden falls.** (Review R12-3, R13-3: where the design keeps an edge over 3, 20's cliffs and built-up faces and
   16's cut to its way in, a lip or kerb a block over the land coming to it, so walking off takes a deliberate jump;
   tested by walking, `world.Edges`; R13-8: breaking into a place from outside above its foot can open over its void,
   the player's own choice as a mine opens into a ravine; dug in at the foot, a closed pyramid opens onto its rim.)
   Nothing in a remains drops a player more than 3 blocks without water at the bottom, lit or not
   (the pit is stepped 1 by 1; the archive's taken bay is a tunnel, not a shaft).

## Multiplayer (D-098)

Mariano plays LAN with a friend who hosts; dedicated servers must work too. Multiplayer is part of the design.

### Rules this wave uses
- **Shared moments** (D-060, `PlayerWatch.sharingWith`, `sharedMomentRadius`): a card fires only if every sharer could
  have had it, and counts as felt for them. The remains, the ring (21), the held breath (23) and the procession (22) are
  shared: everyone near sees the same thing, and seeing it together confirms it ("the world has something wrong").
- **PERSONAL** (D-074): never drawn beside a sharer. Used by 29 (a friend's sounds where they are not).
- **UNSHARED** (first wave): fires beside sharers, felt only by its target. Used by 24 (the follower), 27 (a friend where
  they are not), 30 (the map): the friend may see the follower follow you; their pacing is not spent.
- **The alone rule** (`SoundCues.alone(player, 48)`, spectators count as witnesses): the hunts pick a player alone,
  so splitting up is what gets you hunted.
- **Sounds are heard by everyone in range** (`SoundCues.playAt`): a howl is the whole group's warning.
- **The group factor** (1.5 for friends together) and the per-player pacing stand as they are.
- **Bound mob scenes** (`MobTamper.bind`): a scene ends once nobody it is for is present and nobody sees its mobs.

### Exploiting the friend (what this wave adds)
| Feature | One player experiences | The friend | Why "did you see that?" "no" does not end it |
| --- | --- | --- | --- |
| 27 Where you weren't | Sees the friend (their skin, their name tag, their held item) standing still at night or in fog 36 to 64 off | Is 160+ away, underground or in another dimension, and sees nothing | The friend can prove where they were (coordinates, the locator bar pointed at them the whole time). The viewer saw them anyway: doubt, never an answer |
| 28 Borrowed | A stack is gone from their chest | Finds it in their own chest days later | Neither can prove they did not take it. Each "visited" the other's base, which is when it happens |
| 17c The other door | Finds the archive's door | Comes to look while the finder is away: solid rock; another door opens elsewhere | It really is gone. The finder's screenshot shows a hole where the rock is |
| 29 Not you | Hears the friend's own mining, block for block, in the rock where the friend is not | Was mining, elsewhere | The rhythm and blocks are the friend's; the locator bar says they are the other way |
| 25 The pack | Is hunted while alone | Arrives: the wolves ignore them and keep on the target | "Why only you?" A friend who hits a wolf gets that wolf (vanilla), so helping is possible and costs |
| 24 The follower | Is followed across the map by one cow | Sees it follow the target, never them | Confirmed by the friend, unexplained, and pointed at one of them |
| 30 The blank map | Their map shows their house gone | A different map shows it (or the same, if they cloned it) | Maps that disagree |
| 26 The call | Every monster near comes for them | Sees the monsters walk past to the target | As the pack |

Never: a fake chat line or tab entry, a friend's skin, name or eyes changed, harm done through a friend, anything one
player does to the other's client without the server deciding it.

### Network and sync
- Server-side only: the remains and their answers, the instruments, the hunts, 28. Client-only effects go through
  payloads in the existing patterns (registered in common init, handled in `<ws>.client`): 27 `a1016_02:entity/sc27`
  (to the viewer only: the friend's UUID, the spot, the yaw, the life, the main-hand item) and `entity/sc27_end`; 23
  uses the existing `ClientEffects.silenceShared`; 30 changes vanilla map data, which vanilla sends.
- **Dedicated server safety:** no common class references a client class. SC2-0a adds `core.VerifySc2SideTests`: no
  `net.minecraft.client` import in any class outside `*.client` packages and `*.mixin.client`, across every source.
- **Late joiners:** remains are world data; arms are by UUID; nothing client-side needs a replay except 27, which only
  exists for a viewer online at the time.
- **Other dimensions:** every feature is overworld only; a friend in the Nether or the End counts as "far" for 27 and 29.
- **Leaving mid-event:** arms end on `PARTICIPATION` false; mob scenes are bound (released once none of their players is
  present and nobody sees them); a hunt ends and its wolves calm when its target leaves, dies or changes dimension; 27
  ends when the friend leaves (their skin is gone) or the viewer does.
- **Host and guest on LAN:** nothing in this wave is host-only; every client effect works the same for both. (The world
  list and icon parts of the first wave stay host-only.)
- **Tests:** each task lists its multiplayer game tests, built on the existing patterns: mock players with a private
  `PlayerWatch.Source` (`accident.Group`, `world.Friends`, `dig.Crew`, the director's multiplayer tests), two to four
  players, spectators as witnesses, a leave in the middle. The verifier's LAN pair checklist is at the end.

## Core and kits (wave 0)

Three workers in parallel, each with files nobody else touches in that wave.

### SC2-0a core contracts: instruments
- **`MobTamper.steer(Mob mob, Vec3 to, double speed, int ticks)`** (default false): the mob walks to `to` by its own
  navigation at `speed` (its walk speed factor), its goals and target selector skipped while steered (they do not run:
  no grazing, panic, tempting or wandering), its look along its path unless a `face` is on; a new `steer` call replaces
  the target (the path is recomputed when the target moved more than 1.5 blocks, at most every 10 ticks). Ends at the
  deadline, `release`, or a freeze (freeze wins). Refused for a mob in anyone's leash or vehicle, a tamed or owned mob
  whose owner is online within 48, a boss, a mob driven by a brain (villagers, goats, axolotls, frogs, camels, sniffers,
  armadillos, allays, piglins, hoglins, wardens, breezes) and while the switch is off. `isSteered(mob)`.
- **`MobTamper.hunt(Mob mob, ServerPlayer target, int ticks)`** (default false): sets the mob on that player: a
  `NeutralMob` gets the persistent anger timer and target (vanilla's angry wolf: angry face, attacks), a `Monster` gets
  the target and keeps it within twice its follow range. Refused for tamed or owned mobs, villagers and golems, a target
  that is not a living participant in the mob's level, creative or spectator, and while the switch is off. Bound to the
  target (released when they are gone). **`MobTamper.calm(Mob mob)`**: ends a hunt (anger and target cleared, the mob's
  own AI back). `isHunting(mob)`. Callers: only `accident.sc2` (held by `VerifySc2SideTests`).
- Atmosphere's `MobTamperImpl`, `TamperState` and `mixin.MobMixin` implement both (SC2-0a owns these three files in
  this wave): steering runs in `serverAiStep` instead of the goal and target selectors (navigation, move, look and jump
  controls only), as freezing already does.
- `VerifyScSpoilerTests`: the new words (above). `VerifySc2SideTests`: the side rule and the callers of `hunt`.
- Docs: the ARCHITECTURE pointer line. Version: `1.5.0-26.3` (the orchestrator's call).
- Tests: a cow steered to a point 10 blocks off arrives within 200 ticks and stops at the deadline; a steered sheep
  never grazes or panics when hit; steer refused for a villager, a leashed mob, the switch off; freeze overrides steer;
  `hunt` makes a wild wolf angry at a mock player and it deals damage within 100 ticks; `calm` stops it; refused for a
  tamed wolf and a villager and with the switch off; a bound hunt calms when the target leaves (two mock players).

### SC2-0b core contracts and the structure kit: remains
- **`core.Remains`** (installed by world, `Services.remains()`, stub: nothing known):
  - `enum Kind { PYRAMID, ARCHIVE, HOLLOW, LINE, FLOOR }` (code names; debug prints the numbers).
  - `record Place(Kind kind, ResourceKey<Level> dimension, BoundingBox box, BlockPos anchor, BlockPos entrance,
    Direction entranceFacing, Optional<BlockPos> apex, long seed, int variant)`.
  - `List<Place> known(ServerLevel)`; `Optional<Place> containing(ServerLevel, BlockPos)` (inside the place's
    interior: each kind registers its own cheap interior test); `Optional<Place> nearest(ServerLevel, BlockPos,
    Set<Kind>, int radius)`; `boolean visited(UUID player, Place place)`; `long trespassedAt(UUID player)` (world clock
    tick of the player's last time inside any interior, -1 never).
- **`HerobrineEvents.REMAINS_ENTERED`, `REMAINS_LEFT`** `(ServerLevel, Place, ServerPlayer)` and
  **`REMAINS_ANSWERED`** `(ServerLevel, Place, String answer, UUID player)` (answer ids `16b`, `17b`, ...): fired by
  world; listeners cheap (entity arms its sightings on them, director remembers places).
- **The kit (`world.sc2.kit`)**, used by every structure task:
  - `Shape` (a procedural structure: `BoundingBox box()`, `void fill(Writer w, BoundingBox clip)`, writing only inside
    `clip`; `Writer` wraps a `WorldGenLevel` in worldgen and a `ServerLevel` for the gallery and tests: `set`, `get`,
    `blockEntity` data such as chest items and books, `ground(x, z)`), `Terrain` (worldgen: the noise's base heights,
    thread-safe like `world.gen.NoiseTerrain`; live: the level's heightmaps), so worldgen and the gallery build exactly
    the same thing. No vanilla terrain adaptation (the beardifier only acts in worldgen noise): each shape makes its own
    foundation, clearing and seal.
  - `Sc2Structure extends Structure` (one type, `world/sc2`, a `kind` field in its codec) with a per-kind `Siting`
    (`findGenerationPoint` over the `GenerationContext` and the snapshot) and `Sc2Piece extends StructurePiece` (one
    piece type `world/sc2p` that saves only `kind`, `variant`, `seed`, `origin`, `facing` and rebuilds its `Shape`
    deterministically, so a piece's NBT is tiny and old starts load).
  - `RaysPlacement` (`world/sc2r`, a `StructurePlacement`): candidate chunks along `rays` rays from the origin (0, 0),
    the first bearing a hash of the world seed and the salt, the others evenly spread with up to `jitterDegrees`; slots
    at `first + step * k`, each moved up to `jitterBlocks` along and across; the slot's chunk is the one placement chunk.
    Supports vanilla's `exclusion_zone`.
  - `Snapshot` (immutable, published every 100 ticks and at `SERVER_STARTING`, cleared at stop, as `ScarContext` does):
    the switch, the stage, the world spawn, the seed, the worldgen salt and each kind's config numbers. Siting reads it
    and refuses while it is null, the switch is off or the stage is under the kind's `fromStage`.
  - `Recorder`: a thread-safe queue; the first piece of a start to be post-processed records its `Place`; the server
    thread drains it into `WorldData` (optional key `sc2`, lenient) and `Remains.known`.
  - `Visits` (every 20 ticks per participant, a chunk-grid index of places per level, O(1)): enter and leave events,
    `visited`, `trespassedAt`; `Attention.trigger(ENTERED_TUNNEL)` once per visit through an entrance tunnel.
  - `Answers`: a scheduler for answer edits (a planned `TraceService.batch`, tried every 5 s until out of view, dropped
    after a time limit), the per-player and per-place caps, the fair-danger checks (tools, health, quiet), the
    `REMAINS_ANSWERED` event, a small tension raise (`Attention.raiseTension(server, uuid, 4)`).
  - `Live.place(ServerLevel, Shape)` for the gallery, tests and dev commands only (never game code).
  - Empty registration classes so later tasks never touch the same file: `world.sc2.WorldSc2` (calls `S16.init()` to
    `S20.init()` and `S28.init()`), `atmosphere.sc2.AtmosphereSc2` (`S21` to `S24`, `S29`), `accident.sc2.AccidentSc2`
    (`S25`, `S26`), `entity.sc2.EntitySc2` and `entity.client.sc2.EntitySc2Client` (`S27`, `S31`), `lore.sc2.LoreSc2`
    (`S30`), each `SNN` an empty class in its own package (`world.sc2.s16`, ...). One line each in `WorldInit`,
    `AtmosphereInit`, `AccidentInit`, `EntityInit`, `LoreInit`, `EntityClientInit`.
- Tests: the rays (same seed, same chunks; three bearings 120 ± 10 degrees apart; slot distances and jitter; an
  exclusion zone honoured); a dev-only demo kind `K0` (a 20 x 12 x 20 shape crossing four chunks) generated by worldgen
  in its own test environment `a1016_02:world/sc2_kit` and compared block by block with `Live.place`: identical; siting
  refuses with no snapshot, the switch off, under `fromStage`, too near the origin; the recorder fills `known` once per
  start; `Visits` with two mock players (enter, leave, visited per player, trespass time); `Answers` retries until out of
  view, drops after its limit, refuses a player in their quiet, under the health floor, without the tool; the stub.

### SC2-0c the gallery and the screenshot harness
See "The gallery" below. Owns `core.Gallery`, `core.GalleryProps`, `debug.gallery.**`, `debug.client.gallery.**`,
`debug.client.DevWorld`, `build.gradle`, the gametest `fabric.mod.json` and `src/gametest/java/.../debug/gallery/**`.

## The gallery (SC2-0c, D-099)

Builders and reviewers must be able to see every structure and every staged behaviour on its own, from fixed cameras,
in day, dusk, night and fog. Development only: nothing here is registered unless
`FabricLoader.isDevelopmentEnvironment()`, and nothing here changes the shipped jar's behaviour.

### Exhibits (server side)
- `core.Gallery` (a dev-only registry; `register` is ignored outside a development environment):
  `Exhibit(String id, String feature, Placer placer)`; `Placer.place(ServerLevel level, BlockPos origin, long seed)`
  returns `Placed(BoundingBox box, List<View> views, List<Frames> frames)`;
  `View(String name, Vec3 eye, float yaw, float pitch, long dayTime, Sky sky, boolean survey)` with
  `Sky { CLEAR, DUSK, FOG_NIGHT, FOG_DAY }` (`survey`: night vision on the camera, for judging geometry in the dark, never
  the only shot of a dark place); `Frames(String name, int count, int everyTicks, View camera, Consumer<ServerLevel>
  start)` for time-lapses of behaviours.
- `core.GalleryProps` (dev-only helpers every exhibit may use): `clear(level, box)`; `rock(level, box)` (solid stone
  with a few ore-free strata, for anything underground); `hill(level, center, radius, height)` (a smooth stone hill with
  dirt and grass skin, for the hollow and for tunnel mouths); `sea(level, box, depth)`; `cutaway(level, box, plane)`
  (removes everything on the camera's side of a vertical plane through the box, for section views of underground
  parts); `scaleFigure(level, pos)` (an armor stand in leather armor with a player head beside every entrance, a human
  scale cue); `summon(level, type, pos, count)` (test mobs for staging behaviours: the gallery's only spawns, never game
  code); `fogNow(server, level)` (sets a fog spell at full at once, ramp 0, through `ClientEffects`), `dusk`, `clear`,
  `time`.
- Each structure task registers its exhibits from its own init, in dev only: `16` (default 95), `16x` (127), `16s`
  (sealed), `16l` (apex light, night), `16d` (in the sea), `17`, `17l` (long hall), `17n` (after lights out), `18`,
  `18w` (high window), `19`, `20`, and behaviours `21`, `22`, `23`, `24`, `25`, `26`, `27`, `31`. Demo `00` (SC2-0c's own:
  a 9 x 9 cobblestone hut and six sheep walking a staged loop) proves the pipeline.
- Command `/a1016 sc g` (dev only): lists exhibits; `sc g <id>` places it 48 blocks ahead of the player and lists its
  views; `sc g <id> <view>` puts the player (spectator) at that view; `sc g <id> f <frames>` starts a staged behaviour;
  `sc g <id> plan` writes the blueprint renders; `sc g all` lays every exhibit out along +X, 640 apart.
- **Blueprint renders** (server-side, headless, no AWT: a plain PNG encoder like `HisViewRules`'): for a placed box, a
  top plan, four elevations and a north-south and an east-west section through the middle, one pixel per block from
  each block's `MapColor` (air transparent, darker with depth), into `build/gallery/<id>/plan_*.png`. Exact geometry for
  the art director and a fallback if the client path fails.

### The client run (screenshots)
- Fabric's client game test API is in the Gradle cache for this Fabric API (`fabric-client-gametest-api-v1`
  6.0.8+4be74c3f5d in 0.162.0+26.3): `FabricClientGameTest`, `ClientGameTestContext` (`runOnClient`, `waitTicks`,
  `takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(dir).withSize(w, h).disableCounterPrefix())`),
  `worldBuilder()` (flat worlds by default, `adjustSettings`), `TestSingleplayerContext.getServer().runOnServer /
  runCommand`, `getConnection().waitForChunksRender()`. Screenshots default to `<gameDir>/screenshots`; ours go to
  `<project>/build/gallery/<exhibit>/`.
- `build.gradle`: `enableClientGameTests = true` in `configureTests` (Loom then creates `runClientGameTest`; check with
  `./gradlew build --dry-run` that `check` and `build` do not depend on it, and take the dependency off if a Loom version
  adds it), plus a `gallery` task that runs it with `-Da1016_02.gallery=<list>` from `-Pgallery=16,17` (`all` for every
  exhibit). `./gradlew build` must never open a window.
- `src/gametest/resources/fabric.mod.json` gains the `fabric-client-gametest` entrypoint
  `com.forzacode.a1016_02.debug.gallery.GalleryClientTest` (and `fabric-client-gametest-api-v1` in `depends`).
- `GalleryClientTest`: creates a flat world (preferably a preset of 100 stone layers under 3 dirt and grass, ground
  at Y 40, through `adjustSettings`; else vanilla's flat and `GalleryProps.rock` where needed), cheats on, the player in
  spectator; options: render distance 16, simulation distance 12, FOV 70, GUI hidden, gamma 0.5, clouds off, size
  1280 x 720. For each exhibit in the list: place it at (i x 640, ground, 0) on the server, then for each view: set the
  sky and time (`/time set`, `GalleryProps.fogNow`, dusk or clear), teleport and rotate the camera, `waitForChunksRender`,
  wait 40 ticks (fog and light settle), screenshot to `build/gallery/<id>/<view>.png`; for each `Frames`: start it, then
  a screenshot every `everyTicks` to `build/gallery/<id>/<frames>/f00.png` ...; then the blueprint renders. A
  `build/gallery/index.txt` lists every file with its exhibit, view, time and sky.
- **Fallback A** (if the client test API cannot create the world or capture on this machine): `./gradlew
  runDevGallery`, a run config inheriting the dev client with `-Da1016_02.devWorld=a1016_gallery
  -Da1016_02.devFlat=deep -Da1016_02.gallery=<list>`: `DevWorld` creates the deep flat world, and
  `debug.client.gallery.GalleryAutopilot` runs the same script on client ticks (commands through the integrated
  server, vanilla `Screenshot.grab` into `build/gallery`, `hasRenderedAllSections` before each shot), then quits.
- **Fallback B** (no client at all): the blueprint renders from a server game test that runs only when
  `-Da1016_02.gallery` is set (it succeeds at once otherwise), plus `sc g <id> <view>` by hand in the dev client.
- SC2-0c proves the path with exhibit `00` on this machine and reports which path works. If the client test API path
  fails, A becomes the gallery's path and the tasks below use `./gradlew runDevGallery -Pgallery=...` instead.

### How tasks use it (every visual task)
1. Register exhibits for every variant, with the standard views below and the frames the feature needs.
2. Run `./gradlew gallery -Pgallery=<ids>`, open every PNG with the image-reading tool, write a judgment per view
   (what reads, what is basic, what is wrong) in `build/gallery/<id>/NOTES.txt` (not committed), fix, run again. At least
   three rounds; stop only when the set is genuinely striking by the bar below. The report lists the final PNG paths and
   one line per round.
3. The bar (the art director's too): from far, the silhouette alone says "built, enormous, wrong" in a second; a scale
   cue (trees, the scale figure) is in frame; materials are 2 to 4 per structure, each with a job; no flat unbroken face
   over 24 x 24 without a deliberate rhythm (steps, erosion, a cut), and no noise for its own sake; the interior has one
   focal point and one sightline you do not want to walk; light only where designed (dark where dark, a lit grid even);
   the night or fog shot is the scariest of the set. A screenshot that looks like a generic mod build, a Minecraft
   "showcase" build or a jumpscare fails.

Standard views for structures: `far_day` (200 off, eye at ground + 1.7, noon, clear), `far_dusk` (200, time 12600),
`far_night` (200, 18000), `far_fog` (200, fog night at full), `mid` (140, 30 degrees aside, noon), `near` (40), `mouth`
(6 in front of the entrance, day and night), interior views per feature (`in_*`, true light; plus `survey_*` with night
vision), `aerial` (180 out, 160 up, 45 degrees down, noon), `section` (after `cutaway`, survey) and the blueprint renders.

---

## Features

Each feature: tier, cards or rules, what the player experiences, why it is scary, deniability, guardrails, rules with
numbers, multiplayer, switch off, config (opaque keys), build (packages), cost, tests (multiplayer included), gallery,
and how to force it. The first wave's common gates hold for every card and rule here (the overworld; nothing once the
story is quiet for good, on path C, after `ending:ended` or `ending:d_complete`, during `ending:last_minute`, while
`director:obeyed_after_stop` is set; never within 30 s of the target's combat, but 23; UUIDs across ticks).

Tier 1 (built this round): 16, 17, 18, 21, 23, 25, 27, 28, 31 (a, b). Tier 2 (built this round): 19, 20, 22, 24,
26, 29, 30, 31 (c), and the upgrades 01+, 04+, 11+. Tier 3: the backlog at the end.

### Remains: common rules (16 to 20)
- **Worldgen only** (D-093): vanilla structures (`Sc2Structure`, one start per place) or, for the line, one placed
  feature; nothing is ever placed at runtime in game code. A start is decided when its chunk reaches the structure
  starts stage, which happens while a player comes within about 8 chunks past their view distance; so a world gets
  remains only in land first approached from the kind's `fromStage` on (Traces by default), never near spawn, never in
  chunks generated before this version, never over a player's build (a chunk is built before anyone can stand in it).
- **The developer's half** (D-094): every remains but the hollow was "put in" and then worked through by his pass; the
  marks of his pass on them are removals: cuts, breaches, emptied shelves, the pit.
- **Laid out from the origin** (D-095): the pyramids stand on three rays from (0, 0); the line runs on the axes; every
  entrance faces the origin (the face or side whose outward direction is nearest the bearing to (0, 0)).
- **Every shape** seals itself: within 2 blocks of an interior surface, fluid, air from caves and falling blocks become
  the structure's own material, so no cave opens into it unplanned, no sand falls, no lava or water runs in. Every
  structure has its own foundation (down to the ground) and clearing. Ores exposed inside stay.
- **Answers** (16b, 16c, 17b to 17e, 18b) go through `world.sc2.kit.Answers`: out of view, ledgered (`world:sNN/...`
  causes), the fair-danger checks, per player at most one of each kind per in-game day, never in their quiet, never in
  their first hour; Ending D gives every answer back through the ledger (as the first wave's edits); the switch gives
  back what is loaded and retries every 30 s while off (as `WorldSc.giveBack`).
- **Switch off:** no new start (the snapshot carries the switch), no answers, no sightings at them, no instruments
  aimed at them; what generated stays (D-093).
- **Multiplayer:** the remains are world objects: everyone sees the same pyramid. Answers are per visit: they happen to
  whoever is inside, shared by everyone inside.
- **Cost (all remains):** `findGenerationPoint` at most 81 base-height samples, under 1 ms; `postProcess` under 4 ms
  median and 15 ms worst per chunk on the dev Mac (measured, below); no chunk loads; nothing but the snapshot read off
  the server thread.

---

### 16 The pyramid (structure `world/s16`; rays placement; tier 1; `sections.sc16`)

*As built: SC2-1 (16, 16b, 16c and 31b's pit glimpse), under "Tasks".*

- **Experience.** Far across the plain at dusk, something on the horizon that is not a mountain: its edges are
  straight. It takes minutes to walk to. It is brick, the old red brick, stepped, as tall as the hills, standing on a
  ring of dead brown ground where nothing grows and the trees around it have no leaves. One face has a 2x2 hole at the
  bottom, and the hole faces back the way you came, toward where the world started. Inside it is black and enormous:
  your torch shows a floor and nothing else; the walls step up into the dark over your head. A few steps in, the floor
  ends in a stepped edge going down: the same pyramid, upside down, dug into the ground, its steps lined with the same
  brick, so deep your torch does not reach the bottom. Far down, a single point of light. If you go down, the torches you
  placed on the way are gone when you look back up, and the way you came in may be wall.
- **Why scary.** Scale and geometry: something enormous was built and something enormous was taken. The door faces the
  origin, the next pyramid stands on the same line further out: a plan. The inside is the dark of a mine with no walls.
- **Deniable.** Not the structure (D-092). The answers are: torches go out in mods and caves; "I came in another way".
- **Guardrails.** The pyramid is put in (the developer's test build, D-094); the pit, the cut faces and the erosion are
  his removals; at runtime he removes torches and moves bricks.
- **Rules (structure).**
  - Rays: `rays` 3, `first` 768, `step` 896, `jitterDegrees` 8, `jitterBlocks` 48 (structure set JSON; review R10-3:
    4, 640, 640); exclusion zone the villages set, 8 chunks (review: 4). Siting: the snapshot, stage at least `a`
    fromStage 1 (Traces), the start at least `b` 640 (review: 560) from the origin, the biome at the centre in
    `#a1016_02:world/s16` (plains, sunflower plains, savanna, savanna plateau, desert, badlands, snowy plains, meadow;
    review: and forest, flower forest, birch forest, taiga, snowy taiga, both old growth taigas, windswept savanna) or
    `#a1016_02:world/s16d` (the oceans: the sea variant); on land, the ground spread (9 samples over the footprint, max
    minus min) at most `c` 12 (review: 16); the largest size that fits, by the weights `d` {0.5, 0.35, 0.15} (review
    R11-9: {0.1, 0.45, 0.45}) for base 63, 95, 127 (height 32, 48, 64: every layer one block in on every side).
  - Shell 3 thick, all bricks; the base on the ground at the centre (the median sample), a brick foundation down to the
    ground under the footprint. The core: the pyramid's shape inset by 3, air, from the floor (the ground, grass and
    podzol to dirt, plants removed) to 3 under the apex: a stepped black hall.
  - The pit (land only): under the core, an inverted stepped pyramid of air: opening side `B - 12` (51, 83, 115), every
    level one down and one in on every side, its steps (tread and riser) lined with one layer of brick, down to a 3 x 3
    floor (depth 24, 40, 56). A 3-wide rim walkway of floor stays along the core's inner walls. Sealed (common rules).
    At the bottom, one cobblestone block in the middle with a torch on it ("someone went down and left a light"). Every
    step is 1 high: walking down is never a fall (fair danger 7). (Review R11-17, shape version 3: from the floor a
    dead end 2 wide and 2 high runs 8 on under the steps toward the way in's side, unlit, the seal round it: someone
    went further. Its mouth takes the two lowest treads of its 2-wide strip, a drop of 3 at most; the other steps stay
    1 high.)
  - The entrance: a 2x2 tunnel at ground level through the middle of the face toward the origin, onto the rim. At `e`
    0.25 (land) the pyramid is sealed: no entrance (breaking one of its bricks is `AttentionTrigger.DUG_INTO_PYRAMID`).
  - The apex: one glowstone at `f` 0.35 (a light point on the horizon at night, DESIGN's lone light), else brick.
  - His marks: 1 to 3 dead-end 2x2 cuts into the faces (4 to H/2 up, 6 to 20 deep, never reaching the core); 1 in 300
    face bricks missing.
  - The grounds: within `B/2 + 24` of the centre, grass and podzol to dirt, plants and snow layers removed, leaves
    removed from every tree (bare trunks), and at half density out to `B/2 + 40`; a sharp edge. Spawn overrides:
    `creature` empty over the full box (no animals there); monsters as vanilla (the core and the pit are dark: they fill).
  - The sea variant (`16d`): standing on the ocean floor, needing `H >= sea level - floor + 12` (else no start), always
    sealed, no pit, the core dry and dark (digging in floods it, vanilla), the apex light at 0.5.
- **16b The way in closes (answer).** A participant has been inside the core or pit for `g` 90 s and is at least `h` 20
  from the tunnel's inner mouth, nobody is within 16 of the mouth, the mouth's cells are out of everyone's view, every
  player inside carries a pickaxe and has 10+ health: one batch moves 4 bricks from the core's inner face 2 to 6 blocks
  beside the mouth (the hollows they leave are the clue) into the mouth's 2x2 (`world:s16/close`). At most once per
  place per in-game day and `i` 3 per place ever. Dig out: bricks, a pickaxe, a few seconds.
- **16c Your torches go (answer).** A participant at least `j` 12 steps down the pit with torches or lanterns a player
  placed in the core or pit above them, 12+ blocks away and out of everyone's view: one is removed every `k` 8 to `l` 12
  s (`world:s16/dark`, not dropped), at most `m` 6 per visit, never the last light within 6 of any player, never one
  within 3 of a player. After `n` 4, while the player is in the pit's lower half: `Services.figure().glimpse(player,
  feet, yaw)` on a dark step 14 to 20 above them (D-105; `canGlimpse` holds every glimpse rule, so it often does not
  come).
- **Multiplayer.** Everyone sees the pyramid. 16b never closes on a friend outside near the mouth; with two inside it
  closes on both ("we're sealed in"). 16c takes any player's torches (a friend's trail goes too). The glimpse is for
  one player, as the ladder's.
- **Switch off.** Common rules: no new pyramids; 16b and 16c given back through the ledger.
- **Config `sc16` (v1; keys `a` to `n`; v2 since the review: `b` 560, `c` 16, `d` {0.1, 0.45, 0.45}, `e` 0.15, `f` 0.6).**
  `a` fromStage 1, `b` minOriginDistance 640, `c` maxGroundSpread 12, `d`
  sizeWeights {0.5, 0.35, 0.15}, `e` sealedChance 0.25, `f` apexLightChance 0.35, `g` closeAfterSeconds 90, `h`
  closeMinDistance 20, `i` closePerPlaceMax 3, `j` darkStepsMin 12, `k` darkEveryMin 8, `l` darkEveryMax 12, `m`
  darkPerVisit 6, `n` glimpseAfter 4.
- **Build.** `world.sc2.s16`: `PyramidShape`, `PitShape` (or one shape), `PyramidSiting`, `PyramidAnswers`, interior
  test (core or pit by local coordinates), `Sc16Config`, `/a1016 sc 16` (the nearest known one: distance, bearing,
  variant, visited, answers spent; `sc 16 a` the next ray slots from where you stand; `sc 16 arm` runs 16b and 16c's
  timers for you now, rules kept); data `worldgen/structure/world/s16.json`, `s16d.json`,
  `worldgen/structure_set/world/s16.json`, biome tags `world/s16`, `world/s16d`; exhibits.
- **Tests.** Siting (stage, switch, distance, spread, biome, the size fallback); every slot on the three rays, the
  entrance facing the origin for each face; the shape (the shell, the core air, the pit's steps exactly 1 down and 1 in,
  its lining, the 3 x 3 bottom and its torch, no fluid or cave air within 2 of any interior surface, the rim walkway);
  worldgen equals `Live.place` for a 63 (test environment `world/s16`, chunks loaded first as ARCHITECTURE says);
  `Remains.containing` inside the core and the pit, not on the outer steps; 16b closes only with the tool, the health,
  the distance, nobody at the mouth, out of view, the caps, and is undone; 16c removes only placed lights out of view,
  keeps the last light near a player, caps per visit, calls the glimpse after 4; two mock players: a friend at the
  mouth stops 16b, both inside closes on both, a friend's torch counts; the sea variant's height rule.
- **Gallery.** `16`, `16x`, `16s`, `16l`, `16d`: every standard view; `in_core` (from the tunnel's end, true light, a
  torch in hand: the dark), `survey_core`, `in_rim` (from the rim across the pit: the far light below), `in_pit_up`
  (from the bottom looking up the steps), `section` (the pyramid and its negative), `aerial`, `far_night` of `16l` (the
  apex light alone on the horizon). Must show: a silhouette that reads as a monument at 300 (`16` at least 10% of the
  frame's height, `16x` 14%), the dead ring and bare trunks around it, the tunnel as a black square in a red face, the
  pit's geometry, the light far below.
- **Force it.** Find one: `/a1016 sc 16 a` then travel to the slot (new chunks, Traces or later). In dev:
  `/a1016 sc g 16`.

### 17 The archive (structure `world/s17`; random spread; tier 1; `sections.sc17`)

*As built: SC2-2 (17, 17a to 17e, 31b's aisle glimpse, 11+), under "Tasks".*

- **Experience.** A perfectly square 2x2 hole in a hillside, going down at a slope into the dark. A long way down, a
  glow. The tunnel ends in a small room of cobblestone with a plank floor and one glowstone in the ceiling. Doorways on
  every side lead into identical rooms. You mark the way with a torch. The rooms go on; some have a lectern, some a chair
  facing the wall. Then a doorway opens into a hall: bookshelves seventeen blocks high on both walls, rows of shelves
  across the floor, a walkway on top of them with fence rails, glowstone in a perfect grid on the ceiling, bright,
  silent, and empty. Most of the chiseled shelves are empty: the books were taken. At the far end, up three steps of
  mossy stone, a lectern, and behind it a wall of books with names on them: kirbyfan_92, stoneslab, t0rch, oreo_tm,
  dyl4n_m, pk_alpha. And yours. Every page blank. When you have been inside a while, the far end behind you goes dark,
  one row of lights at a time, coming toward you. In the dark at the end of the nave, something stands. The room where
  you left your torch has no torch. Another room, identical, does.
- **Why scary.** Liminal: too long, too bright, too regular, nobody. The record theme: the people who saw him, their
  records emptied, yours already there. Then the light, the one safe thing, is taken, and the dark fills with what the dark
  fills with. The torch you marked the way with moved.
- **Deniable.** Not the archive. The torch: "I put it in the other room".
- **Guardrails.** The archive is put in (the team's record room, lit by people: glowstone does not exist in the
  overworld); the empty shelves, the taken bay and his tunnel are removals; the records are human-titled blank books
  (D-100); at runtime he removes glowstone and moves torches, books and stone.
- **Rules (structure).**
  - Random spread spacing 40, separation 18 chunks (31 and 14 since SC2-9: SC2-H's dry, flood-safe ways in made an
    archive about 60 % as common; the tighter set gives the planned frequency back; 20 and 8 since the review, R10-3); exclusion zone the trial chambers
    set, 4 chunks. Siting: stage at
    least `a` fromStage 1, at least `b` 500 from the origin, land biomes (no ocean, river, deep dark, mushroom fields),
    the hall's ceiling at least 16 under the noise ground at every corner, the hall floor between Y -24 and Y 40 and at
    least 16 above the world's bottom.
  - **The hall:** interior 63 long (95 at `c` 0.25), 25 wide, 17 high; a cobblestone shell 2 thick; the inner wall
    face bookshelves floor to ceiling; floor oak planks; ceiling cobblestone with glowstone on a 4 x 4 grid (6 a row).
    Stacks from each long wall: rows 8 deep, 2 thick, 9 high, every 6 blocks (aisles 4 wide), from 6 after the entry wall
    to 8 before the dais, leaving a nave 9 wide. On top of the stacks a walkway (planks over each row and 2 wide along
    each wall), oak fence on the nave side, ladders at the four corners and every other row end. 1 in 6 aisle-face shelf
    blocks are chiseled bookshelves holding 0 to 2 books (most slots empty). An empty lectern at the nave end of every
    third row. His bay: one aisle's shelves and floor taken down to the shell, and a 2x2 tunnel out through the side wall
    sloping down 30 blocks to a dead end.
  - **The dais and records:** at the far end, three steps of mossy cobblestone up to a 7 x 5 platform, an empty lectern
    in its middle; behind it a wall of chiseled bookshelves 7 wide and 4 high. The middle rows hold the records: one
    written book per list name (F06's six), one blank page each, titled with the name, author empty, generation 0.
  - **The annex:** a grid of cells (5 x 3, 7 x 5 with the long hall), each 7 x 7 x 4 inside, walls cobblestone 1 thick
    (`d` 0.08 of them infested cobblestone: the walls are alive, as in a stronghold), floor oak planks, ceiling
    cobblestone with one glowstone in the middle; 1 x 2 doorways in the middle of walls, opened by a random spanning tree
    over the cells plus 25% more (loops: you go round in circles); 1 in 6 cells holds one detail (a lectern, an oak stair
    "chair" facing a wall, one bookshelf, a crafting table, an empty chest), the same details repeating, so cells look
    alike. The hall's door opens from one annex cell; the outer entrance cell is on the far side.
  - **The way in:** from the outer entrance cell, a 2x2 tunnel rising one block every two to the surface (at least 24
    long, unlit; a mouth in a slope or cliff where one is within reach, else a 2x2 opening in the ground with the slope
    going down), the mouth facing the origin. Two more tunnels from other outer cells end 3 blocks short of the surface
    (sealed mouths, for 17c). Everything lit to at least 8 at generation (no spawns while the lights stand).
- **17a Records (rule).** The first time a known player enters an archive whose records hold no book with their name,
  a blank written book titled with their name is left (`TraceService.leaveStack`, cause `world:left/s17/record`, never
  undone) in the first empty record slot, before they reach the dais (out of view; retried while they are in the hall).
- **17b Lights out (answer).** A participant at least `f` 24 blocks in from the entrance cell for `e` 60 s: out of
  everyone's view, the glowstone is removed row by row (a hall row of 6, or an annex cell), one row every `h` 160
  ticks (8 s), starting from the rows nearest the way out behind the player and coming toward them, until
  a dark span at least 30 blocks long lies between the player and the way out (light from the next lit row must not
  reach its middle: at least `g` 8 hall rows or a 3 x 3 block of cells), never the row the player stands under nor the
  next one ahead, never a row within 6 of any player, at most 64 glowstone per visit, `i` 1 in-game day apart per
  archive (`world:s17/dark`, ledgered, not dropped). With players apart inside, the dark falls between them where it
  can (rows out of both views). After `j` 4 rows: `Services.figure().glimpse(player, feet, yaw)` at the far end of the
  dark span on the nave floor, 20 to 40 from the player (31b, D-105). The dark then spawns what the dark spawns (vanilla).
- **17c The other door (answer, tier 2).** When a player who has never been inside this archive comes within `l` 24 of
  its open mouth while every player who has been inside is at least `k` 64 away, nobody is within 16 of either mouth,
  and both are out of everyone's view: the 12 blocks of a sealed mouth's plug are moved into the open mouth's first 3
  cells (`world:s17/door`): the mouth that was there is rock, a new one is open elsewhere. Alone: a player coming back
  to the mouth after 1+ in-game day away may find it moved (chance `p` 0.3). At most `m` 2 moves per archive.
- **17d The marker moves (answer).** A torch or lantern a player placed in an annex cell, with that cell out of
  everyone's view and every player 2+ cells away: moved (`TraceService.move`, `world:s17/marker`) to the same spot in
  another cell that looks the same (same details) and that no player has stood in, 2+ cells from every player. At most
  `n` 2 per visit, once per cell.
- **17e Your book (answer, tier 2).** A written book of a player's own about him (lore's own-writing marker; SC2-2 adds
  a public reader to `LoreApi` if none exists), in a container at least `o` 64 from its owner and out of everyone's
  view, nobody within 16: moved (`TraceService.moveStack`, `world:s17/book`) onto the dais lectern (or, if `TraceService`
  cannot put a stack in a lectern, into a chest beside it) of the archive nearest that player, before they first reach
  that dais. Once per archive. Ending D gives it back.
- **11+ t0rch went in (upgrade, tier 2).** When t0rch's trail (11) is searched and an annex cell lies within `r` 64 of a
  reachable cave walk, at `q` 0.5 the trail leads there instead of to the shaft: the torches on the right-hand wall, the
  "brb" sign 2 to 5 cells before a 1x2 hole t0rch dug through the annex wall (`world:left/t0rch/breach`, never undone),
  the lit cell beyond. Every first-wave rule of 11 holds (once per world, the walk, the view rules).
- **Multiplayer.** Shared world object. 17a gives every known player their own record (two friends find two books with
  their names; a friend who joins later gets theirs on their first visit). 17b falls between friends apart inside. 17c is
  the friend's door (the table above). 17d moves anyone's markers ("you said you marked it"). 17e is the writer's alone.
- **Switch off.** Common rules; 17a's records stay (left by others); 17b to 17e given back through the ledger.
- **Config `sc17` (v1; keys `a` to `r`).** `a` fromStage 1, `b` minOriginDistance 500, `c` longHallChance 0.25, `d`
  infestedShare 0.08, `e` darkAfterSeconds 60, `f` darkDepth 24, `g` darkRowsMin 8, `h` darkRowTicks 160, `i`
  darkDaysApart 1, `j` glimpseAfterRows 4, `k` doorFinderAway 64, `l` doorNewcomerNear 24, `m` doorMovesMax 2, `n`
  markerMovesPerVisit 2, `o` bookAwayDistance 64, `p` doorMovedChance 0.3, `q` t0rchChance 0.5, `r` t0rchRadius 64.
- **Build.** `world.sc2.s17`: `HallShape`, `AnnexShape` (the maze from the seed), `ArchiveSiting`, `Records`,
  `ArchiveAnswers`, interior test (hall and annex boxes), `Sc17Config`, `/a1016 sc 17` (nearest known, visited, records,
  answers spent; `sc 17 arm` runs 17b to 17e's timers for you now; `sc 17 b` runs 17c's move now, view rules kept);
  `world/sc/TorchTrail.java` (11+ only); `lore/LoreApi.java` (one reader, if needed); data files; exhibits.
- **Tests.** Siting (depth, floor range, biome, distance); the maze (every cell reachable from the entrance, loops
  present, the same seed the same maze); light at least 8 on every floor cell at generation; seal (a cave beside it stays
  shut); worldgen equals `Live.place` for the short hall; 17a: one record per known player, never twice, the list's six
  present; 17b: rows go behind the player toward them, never the player's row or the next, never in view, the span
  dark enough that a zombie may spawn in its middle (light 0 measured), caps and days, undone; the glimpse call after 4
  rows; 17c: only for a newcomer with every finder 64+ away, material conserved (12 moved), caps, undone; 17d: same-looking
  cell, not visited, caps; 17e: only the writer's marked book, out of view, once, undone; 11+: the trail ends at the
  breach with the sign before it, every rule of 11 kept. Multiplayer: two players apart inside (the dark between them),
  three players' records, 17c with a finder and a newcomer, a friend's marker moved.
- **Gallery.** `17`, `17l`, `17n` (lights out forced): `mouth` (a square hole in a hill: `GalleryProps.hill` over the
  tunnel), `in_tunnel` (looking down toward the glow), `in_annex` (one cell), `in_annex_long` (through three doorways in
  a line: repetition), `in_hall` (from the hall's door down the nave), `in_hall_top` (from the walkway), `in_dais`
  (the records wall), `in_dark` (`17n`: the nave from the dais with the near half lit and the far half black),
  `section` (cutaway: the hall and annex as a dollhouse), `aerial` of the cutaway, plans. Must show: the scale of the
  hall against the scale figure, the even glowstone grid, the repetition of the annex, the darkness span as a real wall
  of black.
- **Force it.** `/a1016 sc 17` (where), `/a1016 sc 17 arm` inside; dev `/a1016 sc g 17`.

### 18 The hollow (structure `world/s18`; random spread; tier 1; `sections.sc18`)

*As built: SC2-5 (18, 18b), under "Tasks".*

- **Experience.** A mountain like any other. At its foot, facing back toward where the world started, a 2x2 hole. You
  go in five blocks and the walls fall away: you are inside the mountain and the mountain is empty. Your torch lights
  the stone floor around your feet and nothing else; when you throw light up, there is no ceiling to see. Stone pillars
  stand in the dark like a cathedral's. In the middle of the floor, on dirt, a stand of trees without leaves, grown in a
  place that has never seen light. Sometimes, a hundred blocks above you, one small square of daylight: a hole cut in
  the mountain's side. Things move in the dark in there, lots of them.
- **Why scary.** The biggest removal in the mod: he took the inside of a mountain. Then the dark: a monster pit with no
  walls you can see, and the trunks that cannot be.
- **Deniable.** Not the hollow (huge caves exist, but not this clean, not with a door).
- **Guardrails.** All removal (his alone, D-094): carved stone, the trunks are left (wood that was there, like his bare
  groves: placed in worldgen, as scars place trunks).
- **Rules (structure).**
  - Random spread spacing 48, separation 24 (review R10-3: 24, 10); siting: stage at least `a` 1, at least `b` 700 from the origin, mountain
    biomes (jagged, frozen and stony peaks, snowy slopes, grove, windswept hills and gravelly hills, badlands plateau,
    meadow beside them); over a 9 x 9 grid of noise samples (16 apart) at least 40% must rise `c` 36 or more above the
    floor level. The floor `Yf` is the lowest noise surface around the core's rim plus 2, at least sea level + 1.
  - Carve: every column in the box whose surface stands at least 6 above `Yf + 1` after the shell (`d` 7 under the
    surface at the top, `e` 5 at the sides where the slope is steep) is air from `Yf + 1` up to there; so the cavity follows
    the mountain's own shape and its ceiling is up to about 120 high. Pillars: 3 x 3 columns of the natural stone left
    every `f` 14 blocks on a grid, and 1-wide stubs 1 to 4 long hanging from the ceiling here and there. Seal (common
    rules: no water or lava runs in, no gravel or sand in the 2 layers over the cavity). The floor is the stone at `Yf`.
  - The grove: under the highest part, a 15 x 15 patch of coarse dirt and podzol with `g` 9 to `h` 13 bare trunks (oak
    or spruce logs by the biome, 5 to 11 tall, at least 3 apart, no leaves).
  - The way in: a 2x2 tunnel at `Yf + 1` through the shell on the side facing the origin. At `i` 0.5 a high window: a
    2x2 hole through the shell near the cavity's top on a face that is a cliff (a square of sky from inside; a dark square
    on the cliff from far).
  - Spawns: vanilla (it is dark: it fills; the vanilla monster cap keeps the count sane).
- **18b The way in closes (answer).** As 16b with `j` 120 s inside, `k` 24 from the mouth, `l` 2 per place ever, 4
  blocks of the shell's inner face beside the mouth moved in (`world:s18/close`); only if every player inside carries a
  pickaxe and has 10+ health.
- **31c** (tier 2, entity) may stand among the grove's trunks.
- **Multiplayer.** Shared world object; 18b closes on everyone inside, never with a friend at the mouth.
- **Switch off.** Common rules.
- **Config `sc18` (v1; keys `a` to `l`; v2 since the review: `c` 28, `i` 0.9).** `a` fromStage 1, `b` minOriginDistance 700, `c` minRise 36, `d` shellTop 7,
  `e` shellSide 5, `f` pillarEvery 14, `g` trunksMin 9, `h` trunksMax 13, `i` highWindowChance 0.5, `j`
  closeAfterSeconds 120, `k` closeMinDistance 24, `l` closePerPlaceMax 2.
- **Build.** `world.sc2.s18`: `HollowShape` (columns from a coarse height grid kept in the piece's seed and the
  terrain, so worldgen and the gallery agree), `HollowSiting`, the grove, the answer, interior test (box and the
  stored height grid), `Sc18Config`, `/a1016 sc 18`; data files; exhibits.
- **Tests.** Siting (rise, biome, distance); the carve keeps at least the shell everywhere (no hole to the outside but
  the tunnel and the window), the floor continuous (no drop over 3 anywhere inside), no fluid within 2 of the cavity, no
  falling block over it; the grove's trunks have no leaves; worldgen equals `Live.place` on a test hill; 18b's rules; two
  mock players (a friend at the mouth, both inside, one without a pickaxe refuses).
- **Gallery.** `18`, `18w` on `GalleryProps.hill` (radius 90, height 100): `far_day` (a plain mountain), `mouth`,
  `in_mouth` (from the tunnel's end into the black, true light), `survey_in` (night vision: pillars and the cavity),
  `in_grove` (the trunks lit by the player's torch only), `in_window` (`18w`: the square of sky from the floor),
  `far_window` (the dark square on the cliff), `section`, plans.
- **Force it.** `/a1016 sc 18`; dev `/a1016 sc g 18`.

### 19 The line (feature `world/s19`; tier 2; `sections.sc19`)

*As built: SC2-8 (19), under "Tasks".*

- **Experience.** From a hilltop, a perfectly straight black line runs across the land to the horizon: a wall of
  obsidian one block thick, from bedrock to the sky, cutting through a forest, a lake, a mountain. At its foot every
  forty-some blocks, a 2x2 hole through it at ground level. Somewhere along it, a gap nine blocks wide from top to
  bottom. If you look at your coordinates, it stands exactly on zero.
- **Why scary.** The world is a test, marked out by someone; the marker is older than your world and something chewed
  through it.
- **Guardrails.** Put in (the developer's debug marker, D-094); the holes are his.
- **Rules.** A placed feature (top layer step) per chunk: for each of the four half-axes (x = 0 with z > 0 or z < 0;
  z = 0 with x > 0 or x < 0) a segment from `b` 1024 plus up to `c` 256 (a hash of the seed) to `d` 256 to `e` 768
  blocks further: in every chunk the segment crosses, obsidian on the axis line from the world's bottom + 1 to its top -
  1 (bedrock stays), through terrain, trees, water and caves; every `f` 48 blocks a 2-wide 2-high hole at the ground or
  water surface; one `g` 9-wide full-height gap per segment. Only in chunks decorated while the snapshot allows (switch,
  `a` fromStage 1): a line begun in older chunks has missing stretches (they read as more breaches). Records one `Place`
  per segment.
- **Multiplayer.** World object.
- **Switch off.** No new segments; what generated stays (D-093).
- **Config `sc19` (v1; keys `a` to `g`).** `a` fromStage 1, `b` startMin 1024, `c` startSpread 256, `d` lengthMin 256,
  `e` lengthMax 768, `f` holeEvery 48, `g` gapWidth 9.
- **Build.** `world.sc2.s19`: the feature, its JSON, its biome modification, `Sc19Config`, `/a1016 sc 19` (the four
  segments' ends); exhibit.
- **Tests.** The segments per seed; a column is obsidian except bedrock, holes and the gap; holes at the surface; only
  on the axis; nothing with the switch off or before Traces; a chunk's work stays in that chunk.
- **Gallery.** `19` (a 400-block segment): `far_side` (200 off, square on), `along` (down its length from the ground),
  `up` (from its foot into the fog), `hole`, `gap`, `far_fog`.
- **Force it.** `/a1016 sc 19`; dev `/a1016 sc g 19`.

### 20 The floor (structure `world/s20`; random spread; tier 2; `sections.sc20`)

*As built: SC2-8 (20, 20b), under "Tasks".*

- **Experience.** In hilly country, a perfect square of grass, 64 by 64, cut flat into the hills: cliffs on the uphill
  sides, a raised earth edge on the downhill ones. Nothing grows on it. In its exact middle, a tiny plank house with a
  cobblestone base, a doorway with no door facing back toward the origin, glass windows, and inside two torches still
  burning. Cows stand on the grass. When you come near, they all turn to face the house.
- **Why scary.** A test world inside your world: the first house of the first versions, kept lit for nobody.
- **Guardrails.** Put in (the developer's test floor and first house, D-094).
- **Rules.** Random spread spacing 56, separation 28 (review R10-3: 36, 16); siting: stage at least `a` 1, `b` 900 (review: 700) from the origin, plains,
  forest, birch forest, meadow, savanna, taiga; chunk-aligned `c` 64 x 64 at the noise ground of its centre: above it
  everything removed inside the square (vertical cuts), below it dirt filled up (vertical earth sides), grass on top, no
  plants. The house: 7 x 7, a cobblestone base course, oak plank walls 3 high, glass panes one per side, a flat plank roof,
  a 1 x 2 doorway toward the origin, two wall torches on the back wall, mossy cobblestone floor. Spawn overrides:
  `creature` cows only (count 4) on the full box. Answer `20b`: a player within `d` 32 of the house: every cow on the floor
  faces the house for `e` 20 s (`MobTamper.face`, silent), once per player per day.
- **Multiplayer.** World object; 20b for everyone near.
- **Config `sc20` (v1; keys `a` to `e`; v2 since the review: `b` 700).** `a` fromStage 1, `b` minOriginDistance 900, `c` side 64, `d` faceRadius 32,
  `e` faceSeconds 20.
- **Build.** `world.sc2.s20`: shape, siting, answer, `Sc20Config`, `/a1016 sc 20`; data; exhibit.
- **Tests.** Siting; the square's flatness and edges; the house's blocks and torches; cows only spawn there (a natural
  spawn attempt in the box picks a cow); 20b faces every cow on the floor, never off it.
- **Gallery.** `20` on `GalleryProps.hill` terrain: `aerial` (the square), `far_day` from a hill, `near` the house at
  dusk (the torchlit windows), `in_house`, frames `20b` (cows turning).

---

### 21 The ring (card `sc_21`; MINOR, Traces; MOB; WATCHER + VISITOR; fake; tier 1; `sections.sc21`)

*As built: SC2-3 (21 and 04+), under "Tasks".*

- **Experience.** At dusk, at night or in fog, out in the field: the sheep are walking in a circle. Nose to tail, a
  dozen of them, round and round, the way a snake would chase its tail, walking widdershins, without a sound. You watch
  it for a minute. It does not stop. When you walk toward them, they stop dead, every head turns to you, and then they
  are sheep again. In the morning there is a perfect ring of bare dirt in the grass, with one bare patch in its middle.
- **Why scary.** Mariano's own seed. Every player knows how sheep behave; this is not it. The ring is evidence.
- **Deniable.** First sight: "they're following each other". The ring of dirt: "they trampled it". The fake: a herd
  milling together, all facing one way.
- **Guardrails.** `MobTamper.steer`, `face`, `silence`; the dirt is removal (`Prints`-like, `TraceService.batch`).
- **Rules.**
  - Fits when the target is outdoors (sky light 10+) at dusk (12000 to 13000), at night (13000 to 23000) or in a counting
    fog; a cluster of at least `a` 7 animals of one kind (cow, sheep, pig, chicken, horse, donkey, llama; not leashed,
    tamed, ridden, named or tampered) within 24 of a point `b` 24 to 64 from the target, with open walkable ground for the
    ring (no water, no block over 1 high in the ring's path).
  - The ring: N = the cluster up to `c` 14; radius = N x `d` 3.2 / 2 pi, clamped 4 to 9 (R11-10); centred on the cluster's middle
    snapped to a grass cell. Each animal is steered to its slot (assembly up to 30 s, walking), then every 10 ticks to the
    point 25 degrees ahead of it on the circle, counter-clockwise seen from above (widdershins), at walk speed, silenced,
    for `e` 90 to `f` 180 s.
  - Ends: the target (or any sharer) within `g` 12 of the centre: every animal stops and faces them for `h` 3 s, then is
    released; or the time runs out (released where they are); an animal hurt, fed or leashed is released, and under 5 left
    all are.
  - Evidence: once the ring is out of everyone's view, its path's grass (at most `k` 48 cells) and the centre cell turn to
    dirt in one batch (`atmosphere:s21`), tried every 5 s for 10 minutes. Grass grows back over days (the clue fades).
  - The fake: the cluster frozen and facing one point for 20 s.
  - At most `i` 6 per world, `j` 2 in-game days apart, one per night.
- **04+ (upgrade, tier 2).** On an empty night (04), at `l` 0.5, a ring forms without a card within 96 of a participant
  where a cluster allows (once that night; the empty night's own rules unchanged).
- **Multiplayer.** A shared moment: everyone near sees the same ring and the same ring of dirt; any sharer within 12
  breaks it. Seeing it together confirms it.
- **Switch off.** Released at once; the dirt given back through the ledger (retried while off).
- **Config `sc21` (v2; keys `a` to `l`).** `a` minMobs 7, `b` maxDistance 64, `c` maxMobs 14, `d` spacing 3.2, `e`
  circleSecondsMin 90, `f` circleSecondsMax 180, `g` breakDistance 12, `h` stareSeconds 3, `i` maxPerWorld 6, `j`
  minDaysApart 2, `k` printCells 72, `l` emptyNightChance 0.5.
- **Build.** `atmosphere.sc2.s21`: `RingCard`, `Ring` (pure: slots, radius, the point ahead, widdershins), the scene
  tick (every 10 ticks, only while a ring runs), `Sc21Config`, `/a1016 sc 21 [force|fake]` (here and now, the fit's
  distance kept, time and cluster size relaxed with `force`); the 04+ hook (one call from `atmosphere.sc.EmptyNight`).
- **Cost.** One animal query per look (radius 64); up to 14 steered mobs while it runs.
- **Tests.** The pure ring math (radius clamp, widdershins, the point ahead); the fit (cluster size, kind, distance,
  time, ground); a staged herd of 8 sheep in a test structure forms a ring and circles (positions on the circle within 1
  block after the assembly), silenced; the break at 12 with the stare; release on hurt; the dirt only out of view, undone
  by the switch; caps; the fake. Multiplayer: two sharers see one scene (felt), either breaks it; a leave of both
  releases it (bound).
- **Gallery.** `21`: frames `ring_top` (28 up over the centre, every 20 ticks, 40 frames: assembly and circling),
  `ring_ground` (from 30 off at night, every 20 ticks, 20 frames), `ring_dirt` (the next morning, aerial). Must show a
  clean circle, nose to tail, widdershins.
- **Force it.** `/a1016 sc 21 force` near 7+ sheep at night; dev `/a1016 sc g 21 f ring_top`.

### 22 The procession (card `sc_22`; MINOR, Traces; MOB; WATCHER + MOURNER; no fake; tier 2; `sections.sc22`)

*As built: SC2-3, under "Tasks".*

- **Experience.** At dusk, animals of every kind walk past you in single file, a cow, two sheep, a pig, a horse, in a
  dead straight line across the land, not grazing, not looking at you. Follow them and they lead you to a pyramid, walk
  into its tunnel and do not come out. Days later, inside, at the bottom of the pit, a cow stands in the dark.
- **Why scary.** They are going somewhere, and they are going to him.
- **Rules.** Fits at dusk, night or fog, the target outdoors, a remains (pyramid, hollow, floor or the archive's mouth)
  within `a` 600, at least `b` 5 passive animals within 96 (up to `c` 9). They are moved out of view to a start 48 to 64
  from the target on the side away from the destination, then steered in single file (each 3.5 blocks behind the one
  ahead) along the bearing to the destination, passing `e` 24 to `f` 40 from the target, silent, not tempted, not
  stopping for anyone, for up to `d` 120 s; then, out of everyone's view for 10 s, moved inside the destination (the
  pit's bottom, the hollow's grove, the floor's grass, the archive's first cell) and released. At most `g` 3 per world,
  `h` 3 days apart.
- **Multiplayer.** Shared moment. **Switch off.** Released at once.
- **Config `sc22` (v2; keys `a` to `h`).** `a` destinationRadius 1500, `b` minMobs 5, `c` maxMobs 9, `d` walkSecondsMax
  120, `e` passMin 24, `f` passMax 40, `g` maxPerWorld 3, `h` minDaysApart 3.
- **Build.** `atmosphere.sc2.s22`; uses `Remains.nearest`. **Tests.** The fit, the file spacing, the bearing, the pass
  distance, the move inside out of view, two sharers. **Gallery.** `22`: frames `file_side` (a file crossing the view)
  and `file_top`. **Force it.** `/a1016 sc 22 force`.

### 23 Held breath (card `sc_23`; MINOR, Proximity; MOB; WATCHER; fake; tier 1; `sections.sc23`)

*As built: SC2-3, under "Tasks".*

- **Experience.** You are fighting a zombie in the field; two cows graze nearby; a skeleton rattles behind the trees.
  Then everything stops. The zombie with its arms up stops, turns its back on you and looks away. The cows look the same
  way. The skeleton too. Every sound in the world is gone: no wind of sound, no steps, no groans. Eight seconds. Then the
  zombie turns back and comes on, the cows graze, the sound comes back. They were all looking toward the same place on
  the horizon. Later you find out what is there.
- **Why scary.** Even the monsters stop for it. The bigger force, in eight seconds, and a bearing to follow.
- **Deniable.** Barely; the fake is only the animals facing (the old "facing the fog").
- **Guardrails.** `MobTamper.freeze`, `face`, `silence`; `ClientEffects.silenceShared`. **Bent gate (D-101):** may fire
  within 30 s of combat (it only gives the player time).
- **Rules.** Fits from Proximity, at least `a` 8 mobs (any mob but bosses, players, the figure, tamed pets sitting)
  within `b` 48 of the target, at least 3 of them in the target's view. The bearing: the nearest remains within `e` 2048
  (its anchor), else where he last stood (10's record), else the origin (0, 0). Every mob within 48 of the target and of
  each sharer (up to `c` 40, nearest first) is frozen, faces a point 200 blocks along that bearing, 80 over the eyes (R11-11: every head tilted up; zombies with their arms up; the nearest monster with its back to the target), and is
  silenced, all in the same tick, for `d` 8 s; everyone sharing gets `silenceShared` for the same time (1 s fade). All
  released in one tick. Once per in-game day per player (the director's deck does the rest).
- **Multiplayer.** Shared moment over the union of mobs near each sharer; everyone sees and hears the same nothing.
- **Switch off.** Released at once.
- **Config `sc23` (v1; keys `a` to `f`).** `a` minMobs 8, `b` radius 48, `c` maxMobs 40, `d` seconds 8, `e`
  remainsRadius 2048, `f` duringCombat true.
- **Build.** `atmosphere.sc2.s23`: `HeldBreathCard`, `Sc23Config`, `/a1016 sc 23 [force|fake]`.
- **Cost.** One entity query; 40 tampered mobs for 8 s.
- **Tests.** Every mob in range frozen, facing the bearing (yaw within 10 degrees) and silenced, released together;
  hostiles included and mid-attack; the bearing order (remains, stand spot, origin); the fit counts; fires within 30 s of
  combat; the cap of 40; the fake faces animals only. Multiplayer: mobs near a second sharer included; both get the
  silence payload.
- **Gallery.** `23`: frames `breath` (a staged field of 6 cows, 3 zombies, 2 skeletons around the camera at dusk, every
  10 ticks for 16 frames: before, during, after).
- **Force it.** `/a1016 sc 23 force`.

### 24 The follower (card `sc_24`; AMBIENT, Traces; MOB + UNSHARED; WATCHER + VISITOR; no fake; tier 2; `sections.sc24`)

*As built: SC2-3, under "Tasks".*

- **Experience.** A cow is behind you. Twenty blocks back. You walk on; it walks on. You stop; it stops, and looks at
  you. You ride half a mile and when you get off, there it is at the edge of the trees. Your friend says: "why is that cow
  following you?" At night it stands outside your window, facing in.
- **Rules** (as reshaped by FIX-SC2-ACTORS, R10-10 and R11-14). Fits for a passive animal (cow, pig, sheep, chicken; not
  owned, named, leashed or tampered): the target's own stock first (within 32 of their base, loaded, out of view), else one
  within 48 of them; the one easiest to know again first. For at most 5 minutes: steered to stay `a` 28 to `b` 40 behind
  the target, never nearer than `c` 20, stopping when they stop, facing them while they look at it; when it is out of
  everyone's view and over `d` 56 behind, moved up on their way (out of view), never after a rider or a runner (it is left
  behind). The target underground: it waits at the cave mouth facing in. Then it walks off and goes back where it was taken.
  That night, the target at home indoors: it (or another of theirs) stands 3 to 5 outside the nearest window or door, on
  its axis, frozen, facing in. Ends `e` 0.5 days after it began, carried on to dawn, or when the target leaves: every animal
  it took from their home goes back there; another is moved out of view 150+ away. At most `f` 4 per world, `g` 4 days
  apart.
- **Multiplayer.** UNSHARED: only the target is followed; a friend sees it follow the target and never them; the
  friend's pacing is not spent.
- **Switch off.** Released. **Config `sc24` (v2; keys `a` to `g`).** `a` followMin 28, `b` followMax 40, `c` nearest
  20, `d` catchUpAt 56, `e` days 0.5 (to dawn), `f` maxPerWorld 4, `g` minDaysApart 4. (FIX-SC2-ACTORS reshaped it: see
  "Fixed in review (FIX-SC2-ACTORS)".)
- **Build.** `atmosphere.sc2.s24`. **Tests.** Distance band, stop, face on look, catch-up only out of view, the cave
  mouth, the window at night, the end; a friend near does not change the target; the UNSHARED felt rule.
  **Gallery.** `24`: frames `follow` (a staged walk). **Force it.** `/a1016 sc 24 force`.

---

### 25 The pack (accident trap `sc25`; Proximity; tier 1; `sections.sc25`)

*As built: SC2-4 (25 and 26 as the accident planner's trap kinds), under "Tasks".*

- **Experience.** Night, a taiga, alone, far from home. A howl, long, somewhere behind you. Then, at the edge of what
  you can see, a wolf. Two. They stop when you stop. Another howl, closer. When you turn toward them they growl. They keep
  their distance, thirty blocks, a minute, more. If you are still in the open when it ends, they come, all of them, angry,
  fast. If you got two blocks up, they circle below you and leave at dawn. If your friend runs to you, the wolves go past
  your friend to you.
- **Why scary.** Real danger in hardcore, told long enough to act on. Wolves never do this: they were sent.
- **Deniable.** A pack of wolves at night; hit one and the pack turns. The clue: nobody hit one.
- **Guardrails.** **Bent (D-096, D-097):** `MobTamper.steer` and `hunt` on existing wolves, an armed trap of the
  target's (`AccidentPlanner`, `TrapType("sc25")`), a marked death.
- **Rules.**
  - Trap context: Proximity, the hunts' own clock open for the target (FIX-SC2-PACE: `sc01 x` to `z`, instead of
    `firstAccidentMinHours`; the major gap, their session's trap not spent), night (13000 to 23000) or a counting fog,
    tonight's roll `m` (1.0 since sc25 version 2: every dark night may, the own clock paces), the target outdoors (sky light 10+) on foot, at
    least `a` 64 from their base, alone (no participant or spectator within 48), health at least `b` 14, carrying a way
    out (2+ placeable full blocks, or a pickaxe, shovel or axe), at least `c` 3 wild adult wolves (not tamed, angry or
    tampered) loaded within `d` 128 and none in anyone's view. Twice as likely (`l` 2.0) for a player who trespassed
    in a remains within the last 2 in-game days (`Remains.trespassedAt`).
  - Gather: the pack is 3, plus 1 at armor 10+, plus 1 at armor 15+ (at most `e` 5); moved out of view to points 40 to
    56 behind the target, bound to them.
  - Stalk, `f` 60 to `g` 120 s: steered to stay 16 to 24 from the target at night (in a fog 0.55 to 0.7 of the drawn fog
    end), within 40 degrees of straight behind them, stopping when they stop; from 15 s until one was plainly seen (and
    whenever none was seen for 30 s) one walks into the middle of their view 17 to 20 out, against the sky where it can;
    the last 20 s (the closing) at 12 to 16, each growling as it steps in. Tells: a long tell at the start (the wolves'
    own whines, then a growl, volume `h` 3.0: heard to 48), another 30 to 45 s in, a third as the closing begins, growls
    (volume 2) whenever the target faces a wolf within 32. (FIX-SC2-ACTORS, R10-5: see "Fixed in review".)
  - Attack: at the end of the stalk, if the target is still in the open (not covered, not 2+ above the ground they
    stand on relative to every wolf's reachable ground, not in water 2+ deep, not in a boat), every wolf `hunt(target,
    i 40 s)`: vanilla angry wolves.
  - End: dawn, the attack time over, the target 2+ up for 10 s, covered (sky light under 10 with a roof), in deep water,
    2 wolves dead, the target gone (left, dead, dimension). The wolves are calmed, steered 60 away and, out of view,
    released. A hunt that ends before the attack (the player took shelter) counts as spent: the tells worked.
  - Death: damage from a hunting wolf is `causedBy` the trap: a marked death, the cross, the list's cause `wolves`.
  - At most once per `j` 5 in-game days per player and `k` 3 per world; never two hunts within 128 of each other.
- **Multiplayer.** The target is alone at the start; a friend arriving later changes nothing (the pack stays on the
  target; a friend who hits a wolf gets that wolf, vanilla). Nobody else's pacing is spent. A friend near the target at
  the start means no hunt (splitting up is what gets you hunted).
- **Switch off.** Every hunt ends at once, wolves calmed and released.
- **Config `sc25` (v1; keys `a` to `l`).** `a` fromBase 64, `b` minHealth 14, `c` minWolves 3, `d` gatherRadius 128,
  `e` packMax 5, `f` stalkSecondsMin 60, `g` stalkSecondsMax 120, `h` howlVolume 3.0, `i` attackSecondsMax 40, `j`
  daysApart 5, `k` maxPerWorld 3, `l` trespassFactor 2.0.
- **Build.** `accident.sc2.s25`: `PackTrap` (in the planner's catalogue beside the other traps), `Pack` (the scene, a
  10-tick check), `Sc25Config`, `DeathCauses` (the cause), `/a1016 sc 25 [arm] [force]` (arms it now; `force` relaxes
  time, distance from base and pacing, never the way out, the health floor or the tells).
- **Cost.** At most 5 wolves, one check every 10 ticks while a hunt runs.
- **Tests.** Context (each refusal: pacing, base distance, not alone with a spectator within 48, health, no way out, too
  few wolves, a wolf in view at gather); the gather out of view; the stalk band; at least two howls before any attack and
  60 s minimum (fair danger 1, measured); the attack only in the open; a player on a 2-high pillar is never reached and
  the pack leaves; the end conditions; `causedBy` and the marked death's cause; caps; trespass doubles the chance.
  Multiplayer: a friend within 48 at the start refuses; a friend arriving keeps the hunt on the target; the friend never
  hunted (only vanilla anger if they hit one); the target leaving ends it and calms the wolves; two players far apart
  never get two hunts within 128.
- **Gallery.** `25`: frames `stalk` (a staged night taiga: the camera as the player, the pack at the fog edge, every 20
  ticks for 30 frames), `attack` (from a 2-high pillar: the pack circling below).
- **Force it.** `/a1016 sc 25 arm force` at night among wolves.

### 26 The call (accident trap `sc26`; Proximity; tier 2; `sections.sc26`)

*As built: SC2-4, under "Tasks".*

- **Experience.** A fog night. Suddenly no sound at all. Twenty seconds of nothing. Then groans, from everywhere at once,
  coming closer. Every zombie, skeleton and spider for eighty blocks is walking toward you, and only you.
- **Rules.** Trap context: Proximity, the hunts' own clock open (FIX-SC2-PACE), a counting fog spell at night or any night
  within one in-game day of the target's trespass in one of his places (R10-4: that night needs no fog), the target outdoors or at home,
  at least `b` 4 zombies, husks, skeletons, strays or spiders within `a` 80 (never creepers or witches: a silent creeper
  would be an unmarked death). Up to `c` 6 (never more than six) are steered toward the target's position (refreshed every 20 ticks) at their
  walk speed, silenced for the first `d` 20 s while everyone sharing gets `silenceShared`, then left loud; each is
  released to vanilla's own targeting at `e` 16. Never more than `i` 4 zombies toward one door. Ends after `f` 180 s or
  dawn. Death marked (cause by the killer's kind). At most once per `g` 7 days per player, `h` 2 per world.
- **Multiplayer.** Steered toward the target only; friends see them walk past (vanilla targeting takes over at 16 and
  may pick whoever is nearest: that is vanilla). **Switch off.** Ends at once.
- **Config `sc26` (v2; keys `a` to `i`).** `a` radius 80, `b` minMobs 4, `c` maxMobs 6 (never over 6, `Call.HARD_MAX`), `d` silenceSeconds 20, `e`
  releaseAt 16, `f` secondsMax 180, `g` daysApart 7, `h` maxPerWorld 2, `i` perDoorMax 4.
- **Build.** `accident.sc2.s26`. **Tests.** Kinds (never a creeper), the silence then sound, the release at 16, the door
  cap, caps, a marked death; a friend near is not steered at. **Gallery.** `26`: frames `call_top` (aerial at night,
  monsters converging). **Force it.** `/a1016 sc 26 arm force`.

---

### 27 Where you weren't (card `sc_27`; MINOR, Traces; FOG + UNSHARED; WATCHER; no fake; tier 1; `sections.sc27`)

*As built: SC2-6, under "Tasks".*

- **Experience.** Night, on your own on a hillside. Your friend said they were going down the mine. Fifty blocks away,
  at the edge of the trees, your friend is standing. Their skin, their name over their head, the pickaxe in their hand.
  Not moving, facing you. You look at them for a few seconds and they turn and walk off into the dark. You ask in chat.
  "I'm at Y -40." The locator bar had their dot behind you the whole time.
- **Why scary.** Canon turned on the friend: a figure in the distance that should not be there. The friend can prove
  where they were; you saw them anyway.
- **Deniable.** Lag, a desync ghost, multiplayer being multiplayer.
- **Guardrails.** **Bent D-082 (D-098):** vanilla's name tag over a copy of the friend, drawn only on the viewer's
  client; nothing exists on the server, nothing is spawned, the locator bar stays honest.
- **Rules.**
  - Fits for a viewer outdoors (sky light 10+) at night, dusk, or in a counting fog, alone (no participant or spectator
  within 48), with a friend (another participant, not a spectator) who is at least `a` 160 away, or in another
  dimension, or underground (sky light at their eyes under 4 and `b` 12+ under the surface) and 48+ away.
  - The spot: on solid ground with sky over it, `c` 36 to `d` 64 from the viewer (at night as far as the dark hides the
    model's detail; with a fog, at 0.6 to 0.8 of the drawn fog end the viewer's client reports, the same report 02 uses),
    40 to 80 degrees off the viewer's facing, out of their view (it is there when they turn). The server sends
    `entity/sc27` to the viewer only: the friend's UUID, the spot, a yaw facing the viewer, life `e` 25 s, look `f` 3 s,
    near `g` 28, the friend's main-hand item.
  - Client: a render-only copy of the friend (their skin from the viewer's own player list, their name tag as vanilla
    draws it, the item, the idle stance, facing the viewer); never collidable, targetable or known to the server. Ends:
    the copy near the screen's middle third for 3 s: it turns and walks away at walking speed into the dark or fog and is
    gone once out of the viewer's view; the viewer within 28: gone the first frame it is out of their view (if it never
    is, it walks away); 25 s; the friend within 100 of the viewer or in their view, the friend leaving, the switch
    (`entity/sc27_end`). It never vanishes in view.
  - Once per `h` 3 in-game days per viewer, `i` 4 per world, counted once the viewer's client saw it (R10-8: one never
    seen may come again; never seen, it stands up to 40 s).
- **Multiplayer.** The feature. Only the viewer sees it; the friend sees nothing. Host or guest alike; works on a
  dedicated server (the server only picks and sends).
- **Switch off.** `entity/sc27_end` to everyone; no new ones.
- **Config `sc27` (v1; keys `a` to `i`).** `a` farDistance 160, `b` undergroundDepth 12, `c` distanceMin 36, `d`
  distanceMax 64, `e` lifeSeconds 25, `f` lookSeconds 3, `g` nearLimit 28, `h` daysApart 3, `i` maxPerWorld 4.
- **Build.** `entity.sc2.s27` (card, spot, payloads), `entity.client.sc2` (the copy: a client-only `RemotePlayer` with
  a negative id added to the client level, or the screenshot's render-only figure path with the friend's skin; pick
  after a spike and prove it in the gallery), `Sc27Config`, `/a1016 sc 27 arm [player]` (the next fitting moment within
  10 minutes; the friend's distance rule kept).
- **Cost.** One extra rendered player on one client for 25 s.
- **Tests.** The fit (alone with a spectator within 48 refuses; a friend at 100 on the surface refuses; underground at
  -12 below the surface and 60 away fits; in the Nether fits); the spot rules (distance, off-facing, out of view, ground
  with sky); the payload goes to the viewer only and carries the item; the end payload when the friend comes within 100,
  leaves, or the switch goes off; caps. (The client copy has no server test: the gallery and the LAN checklist.)
- **Gallery.** `27`: the staging uses a dev-only override that lets the local player's own profile stand in as the
  friend (named "Friend"): `night_far` (the copy at 50 at night, name tag showing), `fog` (in fog), `walk` frames (it
  turning away). Must read as a real player at a distance, the name tag clear, nothing glowing.
- **Force it.** Two clients: `/a1016 sc 27 arm` for the viewer while the friend is in a mine.

### 28 Borrowed (card `sc_28`; MINOR, Traces; ITEM; COLLECTOR + VISITOR; fake; tier 1; `sections.sc28`)

*As built: SC2-7, under "Tasks".*

- **Experience.** Your stack of iron is not in your chest. A week later your friend says: "why is there iron in my
  chest? I don't have iron." Or you find in yours a stack of their spruce.
- **Why scary.** Something moves between your houses, and the only explanation anyone can say out loud is the friend.
- **Deniable.** Completely: a friend took it.
- **Guardrails.** `TraceService.moveStack` (ledgered MOVE_STACK; Ending D gives it back).
- **Rules.** Two participants A and B, each with a known base (`world.Bases`), B within `b` 16 of A's base at some time
  in the last `a` 2 in-game days (a visit tracker in `world.sc2.s28`, sampled every 5 s); one stack (at most `g` 64
  items; food, building blocks, ores and ingots, never tools, weapons, armor, enchanted, named or written items, never the
  story's first blocks) from a player-placed container in A's base, never the container whose contents are worth the
  most, into an empty slot of a player-placed container in B's base; both containers out of everyone's view, nobody
  within `c` 16 of either, A and B each at least `d` 32 from their own base or asleep. Directions alternate (A to B, then
  B to A). The fake: a stack moved between two of A's own containers. At most `e` 3 per world, `f` 3 days apart.
- **Multiplayer.** Needs two bases; parks alone (the director's unfit park). Felt by nobody (an absence found later).
- **Switch off.** Given back through the ledger (retried while off).
- **Config `sc28` (v1; keys `a` to `g`).** `a` visitDays 2, `b` visitRadius 16, `c` nobodyWithin 16, `d` awayDistance
  32, `e` maxPerWorld 3, `f` minDaysApart 3, `g` maxCount 64.
- **Build.** `world.sc2.s28`: card, visit tracker, item rules, `Sc28Config`, `/a1016 sc 28 [force|fake]`.
- **Tests.** Moves between two mock players' bases after a visit; refuses without a visit, with someone near, in view,
  the richest container, a tool or a named item; alternates; the fake within one base; undone by the switch and Ending D's
  undo; caps.
- **Force it.** `/a1016 sc 28 force` with two bases.

### 29 Not you (card `sc_29`; AMBIENT, Traces; SOUND + PERSONAL; WATCHER; no fake; tier 2; `sections.sc29`)

*As built: SC2-7, under "Tasks".*

- **Rules.** Fits for a target alone (48) underground or at night, with a friend in the same level `e` 40 to `f` 200
  away who has a mining burst (`PlayerWatch.recentBursts`) from the last `a` 5 minutes: the burst is replayed (the
  echo's player: the friend's own blocks, hits and gaps) from inside solid rock `b` 18 to `c` 28 from the target, at a
  bearing at least `d` 100 degrees from the friend's true bearing.
- **Multiplayer.** The target hears the friend where the friend is not (the table above). PERSONAL.
- **Config `sc29` (v1; keys `a` to `f`).** `a` burstMinutes 5, `b` distanceMin 18, `c` distanceMax 28, `d` bearingOff
  100, `e` friendMin 40, `f` friendMax 200.
- **Build.** `atmosphere.sc2.s29` (reuses `EchoMiningCard`'s player). **Tests.** The bearing rule, the friend's own
  burst replayed (blocks and gaps), heard only within range, PERSONAL refusals. **Force it.** `/a1016 sc 29 force`.

### 30 The blank map (card `sc_30`; MINOR, Proximity; ITEM + UNSHARED; STRIPPER + COLLECTOR; no fake; tier 2; `sections.sc30`)

*As built: SC2-7, under "Tasks".*

- **Rules.** Fits for a target carrying a filled map (not locked) whose area holds their base, at least `c` 64 from the
  base. The map's pixels over a square of `a` 9 to `b` 15 map pixels around the base become unexplored (blank): the
  house is gone from the map. The old pixels are kept in lore's data by map id; vanilla fills them back when the map's
  holder walks there again (the clue heals as you go home). From Telling (`d` 3), a second form: a remains the map's
  area holds that the target never visited is drawn in (its blocks' map colours at its real place): the map knows a place
  you have not been. At most `e` 3 per world.
- **Multiplayer.** Each map its own: friends' maps disagree; a cloned map shares the change (same id).
- **Switch off.** The kept pixels written back where vanilla has not redrawn them.
- **Config `sc30` (v1; keys `a` to `e`).** `a` sizeMin 9, `b` sizeMax 15, `c` awayDistance 64, `d` showFromStage 3,
  `e` maxPerWorld 3.
- **Build.** `lore.sc2.s30` (map data edits and their store), `/a1016 sc 30 force`. **Tests.** The blank square over the
  base at each scale, kept and restored, the drawn remains, cloned maps, caps. **Gallery.** `30`: the map in hand before
  and after (a staged base and map).

### 31 Seen at his places (cards `sc_31`, `sc_31b` via 17b, `sc_31c`; SIGHTING; tier 1 for a and b, 2 for c; `sections.sc31`)

*As built: SC2-6 (31, 31c; 31b is SC2-2's); the trunks' gallery shot inside an `18` is SC2-9's, under "Tasks".*

- **31 The apex** (card `sc_31`, MINOR, Traces, SIGHTING, WATCHER, fake ZOMBIE). Dusk (the ridge's dusk sky rule), a
  pyramid's apex `a` 56 to `b` 110 from the target (64 to 85 and the sunset behind it preferred), inside the server view distance and out of the target's view at
  spawn: he stands on the apex, still, facing the target; the ridge's ending (look away and back, the apex is empty; he
  steps down the far side out of view and is gone). The fake: a zombie moved onto the apex out of view, held still.
  Entity's `Variant` gains it with the GIVEN spot from `Remains`; every sighting rule holds (one a day, one figure, the
  day's sighting).
- **31b The aisle** is 17b's glimpse (D-105): no card; `Figure.glimpse` at the dark span's far end.
- **31c Among the trunks inside** (card `sc_31c`, MINOR, Proximity, SIGHTING, STRIPPER; tier 2): the target inside a
  hollow (18) within 40 of its grove: BETWEEN_TRUNKS with a GIVEN trunk 18 to 40 off, eyes FLAT (visible in the dark,
  never glowing), the variant's own ending.
- **Darkness first.** The apex: the dusk is the dark, and 31 never uses a pyramid whose apex is a light (`16l`); the
  aisle and the pit come after their dark; the trunks are in the dark.
- **Multiplayer.** As every sighting (shared moments, D-060's figure rules).
- **Config `sc31` (v2; keys `a` to `c`).** `a` apexMin 56, `b` apexMax 110 (64 to 85 preferred), `c` trunksMax 40.
- **Build.** `entity.sc2.s31`, `entity.Variant` and `FigureApi` (the GIVEN spots), `Sc31Config`, `/a1016 sc 31 [b|c]
  [fake]`. **Tests.** The apex spot from a recorded place, the dusk rule, out of view at spawn, the ending; the trunk
  spot; the day cap shared with every sighting. **Gallery.** `31`: `apex_dusk` (the silhouette on a `16` exhibit from
  180 at 12600), `trunks` (inside an `18`). **Force it.** `/a1016 sc 31` at dusk within 256 of an apex.

### 01+ His places remember (upgrade, director; tier 2)

*As built: SC2-9, under "Tasks".*
Being inside a remains' interior or within 32 of its entrance gives a place factor of at least `o` 1.5 (never past
`maxFactor`); `Env.remember` takes `REMAINS_ANSWERED` as a place for that player at the answer's spot. `sc01` v4 (keys
`o` remainsFactor 1.5; step 3 to 4 only adds it); `DirectorSim` models `simRemainsShare` (`p`, 0.03) of play there.

---

## Tier 3 (backlog, not this round)
- The bell: a village bell rings at 3 a.m. with nobody near; every villager runs inside and stands facing a wall.
- The mirage: a pyramid on the horizon drawn on one client only (client-side blocks past 128), gone when they get there:
  the structure one finds and the other cannot (the strongest multiplayer idea, held back for its client risk).
- A buried pyramid (half in a hillside), a brick pyramid that is only its pit (the negative alone), an archive under a
  pyramid joined by the pit.
- Explorer maps from camp chests that lead to a remains (DESIGN's camp map lure).
- Clouds that stop moving for a day; missing time (the clock jumps while a menu is open).
- An enderman holding a block of your house; squid in a ring in a lake; bats pouring out of a 2x2 tunnel at dusk.
- A giant panic tower (a dirt pillar to the build limit with a 1x1 shelter on top); a stair of cobblestone up into the
  clouds ending in nothing.
- Lore fragments placed in the remains (needs new fragment text: DESIGN first).

## Pacing and director integration

No new card is earlier than Traces; the remains do not exist before Traces; the first hour is unchanged by
construction (D-070's targets re-measured by INT). Rates per tier are unchanged; the decks spread over more kinds of
moment.

| Card | Feature | Tier | Stage | Tags | Habits | Fake |
| --- | --- | --- | --- | --- | --- | --- |
| sc_21 | 21 | MINOR | Traces | MOB | WATCHER, VISITOR | yes |
| sc_22 | 22 | MINOR | Traces | MOB | WATCHER, MOURNER | no |
| sc_23 | 23 | MINOR | Proximity | MOB | WATCHER | yes |
| sc_24 | 24 | AMBIENT | Traces | MOB, UNSHARED | WATCHER, VISITOR | no |
| sc_27 | 27 | MINOR | Traces | FOG, UNSHARED | WATCHER | no |
| sc_28 | 28 | MINOR | Traces | ITEM | COLLECTOR, VISITOR | yes |
| sc_29 | 29 | AMBIENT | Traces | SOUND, PERSONAL | WATCHER | no |
| sc_30 | 30 | MINOR | Proximity | ITEM, UNSHARED | STRIPPER, COLLECTOR | no |
| sc_31 | 31 | MINOR | Traces | SIGHTING | WATCHER | yes |
| sc_31c | 31 | MINOR | Proximity | SIGHTING | STRIPPER | no |

Not cards: the remains (worldgen), their answers (rules, capped per player per day, a small tension raise; a player's
first time of each answer comes even in their quiet, R10-7), 04+ (on the empty night), 11+ (inside 11), 01+ (a factor).
The hunts are accident trap kinds on a clock of their own (below).

**How the director paces the wave** (FIX-SC2-PACE, D-107; `director.sc.ScPacing`, `sc01` `q` to `z`; R10-1, R10-2,
R10-4). Rarity is not the goal: the wave must be met, in the sessions Mariano will play, without a tier's rate moving.
It takes a share of the draws its tiers already make:
- **The group.** From Proximity, 30% of the ambient and 75% of the minor tier's due draws (`s`) look first at the
  wave's cards, by weight (`q`: 22, 31, 31c 3; 21, 23 2; the rest 1; habits never under 1, so 23, 27 and 31 no longer
  weigh 0.4 in two worlds of three), and take one only if its moment is there at that look: a moment that is not there
  is never held and never parked, and the draw goes to the rest of the deck. The wave cycles by itself (its cycle comes
  back when none of its cards fits), the same card not within 2 h of play (`u`), and two of its moments a player goes
  through, their own or felt, are at least 30 minutes apart (`t`; hunts included): never more than two in an hour. In
  Traces the shares are 0 (`r`): its cards are drawn as any card there.
- **The first beat.** From each player's Proximity the held breath (then, if its moment never came in 90 minutes of
  active play, the ring for 60, `v`) is the minor tier's next draw at the first look its moment fits while the tier may
  fire: ahead of the deck and of its roll, through every gate, at dusk or night for its first 45 minutes (`w`), never
  a fake, never held. Quiets and empty sessions do not spend its windows. A beat felt beside a friend ends it.
- **The hunts' own clock.** Never drawn from the major deck: from 2.5 h of play times the tempo (`x`, never before
  Proximity; the other traps keep 3.5 h), a hunt comes at the first look its moment fits, as the major it is (the major
  gap, the first day), at most one per 2 h (`y`); the player's first whenever it comes; a later one only while the
  major slot is due within 60 minutes (`z`), which it takes at the slot's own time. A trespass in one of his places
  calls one that night (one in-game day): it may come whenever its moment does, and the call needs no fog spell. The
  pack's day roll is gone (`sc25 m` 1.0): the clock paces it.
- **Two players.** The group and the beat are shared moments (every sharer's gates, the gap and the repeat for each);
  the beat is drawn only when no friend beside them had it; the hunts need their player alone.

Measured (FIX-SC2-PACE, `SelfCreated2PacingTests`: the registered deck with both waves' cards, the dry runs' own
moments (`director.sc.SimFits`: the wave's moments at a look's chance as their gates have them in a world, the hunts at
night on three nights in ten a player is out far from home, six after a visit to one of his places; the denser places of
FIX-SC2-WORLD folded in), `sc01`'s dread share with 01+'s share at his places, 192 seeds of 20 h per tempo; in brackets
the same runs without the layer's cards, DESIGN 4b's v1.3 runs):

| | Early | Slow burn | Very late |
| --- | --- | --- | --- |
| Alone lasts | 0.49 h | 0.65 h | 0.81 h |
| Alone: ambients per hour | 2.68 (2.66) | 2.89 (2.86) | 2.88 (2.85) |
| Traces: ambients per hour | 2.24 (2.19) | 2.26 (2.20) | 2.31 (2.20) |
| Traces: minors per hour | 0.93 (0.86) | 0.82 (0.82) | 0.85 (0.78) |
| Proximity: ambients per hour | 1.43 (1.38) | 1.44 (1.39) | 1.51 (1.46) |
| Proximity: minors per hour (active) | 1.08 (1.03), 2.01 | 1.09 (1.05), 2.03 | 1.13 (1.09), 2.04 |
| Proximity: a major every | 1.79 h (1.78) | 2.14 h (2.23) | 2.47 h (2.73) |
| First accident on the accident clock: median (earliest) | 4.73 h, 2.68 h (4.18, 2.64) | 5.95 h, 3.52 h (5.13, 3.52) | 7.80 h, 4.48 h (6.43, 4.39) |
| First accident of any kind (the hunts included): median | 4.06 h | 4.62 h | 5.52 h |
| First hour: first event by 30 / 45 minutes | 80% / 94% | 81% / 95% | 81% / 95% |
| First hour: two or more events | 90% | 92% | 95% |
| The second wave: a played hour from Proximity (real, no fake) | 0.47 (0.40) | 0.48 (0.40) | 0.49 (0.42) |
| The second wave: a played hour in Traces | 0.36 | 0.32 | 0.33 |
| The second wave: its share of every fire | 14.3% | 14.4% | 14.5% |
| The first real second-wave moment by Proximity + 1.5 h | 86% | 83% | 82% |
| The first hunt: median over every player | 10.0 h | 8.2 h | 7.5 h |

SC2-9's tuning gave the second wave 0.19 / 0.20 / 0.20 a played hour from Traces (6.2 to 6.4% of every fire), its first
real moment 2.9 to 3.6 h into Proximity (by Proximity + 1.5 h in 13 to 15% of runs) and a hunt within 20 h to a third of
the runs. The first hour holds D-070's targets in every case and tempo; Traces' and Proximity's rates and the majors stay
within 15% of the deck without the layer in the first wave's four cases (every card at its own dice, with the dread
share, the layer never fitting, every one fitting 2% a look); no hard limit is broken in any run (the group's gap and
the own clock included); never more than two of the wave's moments in an hour. The traps on the accident clock never
come earlier, and their first one 13 to 21% later (a hunt's major gap and the quiet its tension buys), while the first
accident of any kind comes earlier. Two players together feel 0.98 / 1.00 / 1.02 of one player's minors
(`DirectorMultiplayerTests`, bound 0.95) and share their first beat (both feel the one that comes, never a fake); apart
each has their own.

What a player meets (the share of the runs that met each feature for real, no fake, in their first 3 or 5 sessions of
45 to 180 minutes; one player, out on three nights in ten; early / slow burn / very late; SC2-9's in brackets):

| Feature | 3 sessions | 5 sessions |
| --- | --- | --- |
| 21 | 31 / 29 / 21% | 42 / 41 / 37% (16 / 21 / 13) |
| 22 | 19 / 21 / 22% | 32 / 36 / 36% (16 / 22 / 19) |
| 23 | 95 / 89 / 83% | 99 / 95 / 98% (8 / 9 / 6) |
| 24 | 30 / 28 / 36% | 40 / 38 / 44% (74 / 79 / 79) |
| 25 | 22 / 25 / 19% | 34 / 39 / 42% (6 / 4 / 3) |
| 26 | 9 / 13 / 13% | 19 / 23 / 27% (5 / 5 / 4) |
| 30 | 0 / 0 / 1% | 0 / 0 / 1% |
| 31 | 15 / 13 / 12% | 23 / 22 / 23% (16 / 15 / 14) |
| A hunt (25 or 26) | 29 / 36 / 31% | 48 / 55 / 64% (10 / 9 / 7) |
| Features met, on average | 2.2 / 2.2 / 2.1 | 2.9 / 2.9 / 3.1 (1.4 / 1.6 / 1.4) |

27, 28 and 29 need a friend (28: about a third of two players together in five sessions; 27 needs its player alone and
29 is one player's, so neither comes while the two stand together). The hunts follow how often a player is out far from
home at night: a hunt in five sessions for 35 / 41 / 47% of players out on 15% of nights, 62 / 70 / 76% of those out on
half. The places themselves (16 to 20) come on top, as the world generates them (FIX-SC2-WORLD's presence numbers).

What the days should feel like (re-measured, R10-13):
- **Traces (hour 0.5 to 2.5).** The first wave's noticing is the bulk; about one second-wave moment in three Traces
  hours, mostly a ring at dusk, a cow that keeps up with you, a far procession; a friend on a hill or their iron in your
  chest when there is a friend. The places begin to exist 560 and more out.
- **Proximity (from hour 2).** Within the first hour and a half, four players in five feel the world hold its breath
  (the first beat); then one of the wave's moments about every two hours of play, never two within half an hour: the
  held breath again, the ring, the procession that leads to a place, the sightings at his places. From half an hour into
  Proximity the hunts may come on a night far from home, and a trespass in one of his places calls one that night.
- **Telling and on.** Your own book on his lectern; the map draws his places; fewer sightings, worse places.
- **The places (review R10-3, measured over 20 default worlds).** An open pyramid within 900 of the spawn in about two
  worlds of three, an archive within 700 in seven of ten, a floor within 1 500 in seven of ten, a hollow within 1 500 in
  two of five (where there are mountains), the line on the axes from 1 024: a first long trip from home meets one.

## Performance budget (D-104)
- **Worldgen.** `findGenerationPoint` at most 81 base-height samples (under 1 ms); `postProcess` under 4 ms median,
  15 ms worst per chunk on the dev Mac for every kind and size (SC2-9 and R12 measure it in `VerifySc2CostTests`, own
  test environment); each piece writes only inside the chunk box it is given; no chunk is loaded or read outside the
  generation region; nothing off-thread but the immutable snapshot and the recorder queue. (Review R12-17, accepted:
  a fill that must read the land under every column, 16 on land and 20, measured 6.7 / 24.3 and 6.8 / 24.6 ms a chunk
  into SC2-H's overlay, the noise's own read being 4 to 6 ms of it; their budget is 8 ms median, 25 ms worst, for the
  few dozen chunks of a place, on the serial worldgen executor as every structure's.)
- **Runtime, idle.** Under 20 microseconds a server tick for the whole wave with nothing running (measured like
  `VerifyScCostTests`): `Visits` every 20 ticks per participant through a chunk-grid index (O(1)), cards only at
  director looks, answers only while a player is inside.
- **Mobs.** No per-tick pass over all mobs anywhere: entity queries only at a card's look or fire (bounded radius) and
  every 10 ticks over a scene's own mobs. At most 14 steered in a ring, 9 in a procession, 1 follower, 40 held for 8 s,
  5 wolves, 8 called; at most 48 steered in the world at once (a new scene waits).
- **Answers.** At most one batch a second per place, 64 edits per visit.
- **Client.** 27: one extra rendered player; no per-frame allocation in any new client code.
- **Saves.** `WorldData`'s `sc2` key at most 2 KB per known place; every new saved field optional and lenient (layer
  rule 2); piece NBT only parameters, never block lists.

## Config sections (summary)
| Section | Version | Keys | Owner |
| --- | --- | --- | --- |
| sc16 | 2 | a to n | world (SC2-1; review) |
| sc17 | 1 | a to r | world (SC2-2) |
| sc18 | 2 | a to l | world (SC2-5; review) |
| sc19, sc20 | 1, 2 | a to g, a to e | world (SC2-8; review) |
| sc21 to sc24 | 2, 2, 1, 2 | as listed | atmosphere (SC2-3; sc21, sc22, sc24 v2: FIX-SC2-ACTORS) |
| sc25, sc26 | 2, 2 | a to n, a to i | accident (SC2-4; sc25 v2: `m` 1.0, FIX-SC2-PACE; sc26 v2: `c` 6, FIX-SC2-ACTORS) |
| sc27, sc31 | 1, 2 | a to i, a to c | entity (SC2-6; sc31 v2: FIX-SC2-ACTORS) |
| sc28 | 1 | a to g | world (SC2-7) |
| sc29 | 1 | a to f | atmosphere (SC2-7) |
| sc30 | 1 | a to e | lore (SC2-7) |
| sc01 | 5 | v4 adds o, p (SC2-9); v5 adds q to z, the second wave's pacing (FIX-SC2-PACE) | director |

Each a `ModConfig.Versioned` class in its package (`SECTION`, `VERSION`, `@SerializedName` letters, an `upgrade()`
with `ModConfig.Upgrades.keepOrNew` for any later default change), read through the snapshot where worldgen needs it.

## Decisions

| ID | Decision | Why |
| --- | --- | --- |
| D-092 | Wave 2 of the self-created layer is "a bigger force": big remains seen from far away, mobs used as instruments, and fair hunts. The remains are not deniable; what happens inside them and around you is | "Stuff that will make you feel frightened by a bigger force." Noticing was not fear |
| D-093 | Remains are worldgen only: vanilla structures through one structure type and one piece type, and one placed feature for the line, in chunks whose starts are decided from the kind's `fromStage` (Traces) on, far from the origin; never placed at runtime by game code. The switch stops new ones and every behaviour around them; what generated stays (SC rule 3 bent) | A runtime 300 000-block build would pop in or need the terrain it replaced; worldgen is "always there" |
| D-094 | The remains are the developer's half, the first versions' test builds (brick pyramid, obsidian marker line, the record room, the test floor and first house), worked through by his pass; the hollow mountain is his alone. Guardrail 7 holds: his marks are removals; at runtime he removes and moves | The Infdev pyramids and obsidian walls were real test structures; "he comes with the world" |
| D-095 | Laid out from the origin: pyramids on three rays from (0, 0), the line on the axes, every entrance facing the origin. Nothing explains it | An intelligence the player can discover with F3 |
| D-096 | Instruments: `MobTamper.steer`, `hunt`, `calm`. Steering is a walk the mob could make; `hunt` only from accident's hunts, as armed traps; never a spawn | Mob behaviour is the scare; hunts are the danger Mariano asked for ("a horde of angry wolves") |
| D-097 | The fair-danger contract: told (60 s, two audible tells, a visible instrument), a way out (carried; reachable heights; tools for closings), health floors, marked deaths, one at a time and accident-paced, home safe, no hidden falls | Hardcore is the intended way to play; DESIGN's guardrail 1 in spirit |
| D-098 | Multiplayer first: each feature's multiplayer rules; allowed exploits (a friend seen where they are not with an honest locator bar, a friend's things in your chest, a door one found and the other cannot, a friend's sounds elsewhere, maps that disagree, hunts on the one alone); never fake chat or tab entries, never a friend's look changed, never harm through a friend | Mariano plays LAN; the friend makes the doubt |
| D-099 | The gallery: dev-only exhibits in a flat world, a client game test run as its own task (`./gradlew gallery`), PNGs in `build/gallery`, blueprint renders; every visual task iterates on its screenshots; the art director judges them | Nobody looked at the first wave's builds |
| D-100 | The archive's records: blank written books titled with the list's six names and each known player's own name (left by others); a player's own book about him may be moved onto his lectern (ledgered). No new text | Guardrail 2 and the `[PLAYER NAME]` precedent |
| D-101 | The held breath (23) may fire within 30 s of combat; it is the only card that may | It only gives the player time; mid-fight is when it is strongest |
| D-102 | This wave's main moments never depend on the mod's fog or sky drawing | Iris draws its own on Mariano's install |
| D-103 | D-080 extended to registry ids, structure sets, biome tags, the gallery and the new spoiler words | He must not learn it from `/locate`, the config or the log |
| D-104 | The performance budget above | Worldgen cost is felt by everyone, every chunk |
| D-105 | His places may call `Figure.glimpse` in their dark (the archive's dark span, the pyramid's pit), every glimpse rule kept | D-073's dark stands in for the fog; he darkens a place before he appears |
| D-106 | Answers in the remains are rules, not cards: capped per player per kind per in-game day, never in a quiet or the player's first hour, each a small tension raise and a remembered place | They are what a player chose by going in; the director still buys its silence after |
| D-107 | The second wave is paced to be met (round 2, R10-1, R10-2, R10-4): from Proximity it takes a share of the ambient and minor draws for its moments that are there at the look (never held, never parked), cycling by itself, the same card not within 2 h, two of its moments at least 30 min apart; the held breath (then the ring) is each player's first beat in Proximity, at its first fitting look, never a fake; the hunts run on their own clock and a trespass calls one that night. D-070's first hour, DESIGN 4b's tier rates and the silences stay | Mariano's first playtest noticed nothing in 70 minutes; the wave as tuned was 0.2 an hour, a first contact left to chance and a danger past 10 h. Rarity is not the goal: meeting it is |

## Tasks

Branches from `self-created`, merged back into `self-created` only. Same process as the first wave and v1.3
(`docs/PLAN_V1_3.md`, "Waves"): git worktrees, one branch per task, at most 4 workers at once, a worker edits only its
task's owned paths (below; a path not listed is someone else's: ask in the report), `./gradlew build` green on the
branch (every game test), commit, a report of at most 40 lines (branch, commit, test count, what was built, the final
gallery PNG paths and one line per gallery round for visual tasks, asks, anything open). Each wave branches from
`self-created` after the previous wave's merges. Game test classes are `SelfCreated2<Name>Tests` (method names describe
the rule; he does not read test names); test environments and structure templates a task adds are new files of its own.

To keep parallel tasks off each other's files, wave 0 creates every shared seam: the registration classes (SC2-0b) and
the empty game test classes listed in the gametest `fabric.mod.json` (SC2-0c: `world.SelfCreated2PyramidTests`,
`world.SelfCreated2ArchiveTests`, `world.SelfCreated2HollowTests`, `world.SelfCreated2LineTests`,
`world.SelfCreated2BorrowedTests`, `atmosphere.SelfCreated2InstrumentTests`, `atmosphere.SelfCreated2NotYouTests`,
`accident.SelfCreated2HuntTests`, `entity.SelfCreated2EntityTests`, `lore.SelfCreated2MapTests`,
`director.SelfCreated2PacingTests`, `core.VerifySc2CostTests`); each later task owns its class's file.

| ID | Branch | Owned packages and files | Depends on | Size | Implements |
| --- | --- | --- | --- | --- | --- |
| SC2-0a | `sc2/core` | `core/MobTamper`, `atmosphere/mob/MobTamperImpl`, `atmosphere/mob/TamperState`, `atmosphere/mixin/MobMixin`; tests `core/VerifyScSpoilerTests` (words), new `core/VerifySc2SideTests`, `core/SelfCreated2CoreTests`; the ARCHITECTURE pointer line; `gradle.properties` | - | M | steer, hunt, calm; the spoiler words; the side rule |
| SC2-0b | `sc2/kit` | `core/Remains`, `core/Services`, `core/HerobrineEvents`; `world/sc2/**` (kit, `RemainsImpl`, `Visits`, `Answers`, `Snapshot`, `Recorder`, `WorldSc2`, empty `s16` to `s20`, `s28`); the stub classes `atmosphere/sc2/AtmosphereSc2` (+ empty `s21` to `s24`, `s29`), `accident/sc2/AccidentSc2` (+ `s25`, `s26`), `entity/sc2/EntitySc2` (+ `s27`, `s31`), `entity/client/sc2/EntitySc2Client`, `lore/sc2/LoreSc2` (+ `s30`); one line in each of `WorldInit`, `AtmosphereInit`, `AccidentInit`, `EntityInit`, `LoreInit`, `EntityClientInit`; `world/WorldData` (key `sc2`); test env `world/sc2_kit`; tests `world/SelfCreated2KitTests` | - | L | the remains contract and the structure kit |
| SC2-0c | `sc2/gallery` | `core/Gallery`, `core/GalleryProps`, `debug/gallery/**`, `debug/client/gallery/**`, `debug/client/DevWorld`, `debug/DebugInit` (one registration line), `build.gradle`, `src/gametest/resources/fabric.mod.json`, `src/gametest/java/.../debug/gallery/**`, the empty test classes above | - | L | the gallery, its client run, the fallbacks, blueprint renders, exhibit `00` |
| SC2-1 | `sc2/pyramid` | `world/sc2/s16/**`; `data/a1016_02/worldgen/structure/world/s16*.json`, `worldgen/structure_set/world/s16.json`, `tags/worldgen/biome/world/s16*.json`; test env `world/s16`; `world/SelfCreated2PyramidTests` | 0a, 0b, 0c | L | 16, 16b, 16c (and 31b's pit glimpse) |
| SC2-2 | `sc2/archive` | `world/sc2/s17/**`; data `world/s17*`; `world/sc/TorchTrail.java` (11+ only); `lore/LoreApi.java` (one reader, if none is public); test env `world/s17`; `world/SelfCreated2ArchiveTests` | 0a, 0b, 0c | L | 17, 17a to 17e, 31b (the aisle glimpse), 11+ |
| SC2-3 | `sc2/instruments` | `atmosphere/sc2/s21/**` to `s24/**`; `atmosphere/sc/EmptyNight.java` (the one 04+ call); `atmosphere/SelfCreated2InstrumentTests` | 0a, 0b, 0c | L | 21, 22, 23, 24, 04+ |
| SC2-4 | `sc2/hunts` | `accident/sc2/**`; `accident/DeathCauses.java` (two causes); the accident trap catalogue (one entry each where the trap kinds are listed); `accident/SelfCreated2HuntTests` | 0a, 0b, 0c | L | 25, 26 |
| SC2-5 | `sc2/hollow` | `world/sc2/s18/**`; data `world/s18*`; test env `world/s18`; `world/SelfCreated2HollowTests` | 0b, 0c | L | 18, 18b |
| SC2-6 | `sc2/entity` | `entity/sc2/**`, `entity/client/sc2/**`, new client mixins `entity/mixin/client/Sc27*` and their lines in `a1016_02.entity.mixins.json`, `entity/Variant.java` and `entity/FigureApi.java` (the GIVEN spots of 31 and 31c only); `entity/SelfCreated2EntityTests` | 0b, 0c; gallery shots need wave 1's `16` and wave 2's `18` merged | M | 27, 31, 31c |
| SC2-7 | `sc2/mp` | `world/sc2/s28/**`, `atmosphere/sc2/s29/**`, `lore/sc2/**`; `world/SelfCreated2BorrowedTests`, `atmosphere/SelfCreated2NotYouTests`, `lore/SelfCreated2MapTests` | 0a, 0b, 0c | M | 28, 29, 30 |
| SC2-8 | `sc2/line` | `world/sc2/s19/**`, `world/sc2/s20/**`; data `world/s19*`, `world/s20*` (feature, placed feature, structure, structure set, biome tags); `world/SelfCreated2LineTests` | 0b, 0c | M | 19, 20 |
| SC2-9 | `sc2/int` | merges; `debug/ScIndex.java` (lines 16 to 31), the overlay rows; `director/sc/**` and `DirectorSim` (01+), `director/SelfCreated2PacingTests`; `core/VerifySc2CostTests` (own test env `verify/sc2_cost`); `docs/SELF_CREATED_2.md` ("As built" notes) | waves 1 and 2 | M | 01+, the index, pacing, cost, the full gallery run, docs |

### SC2-0a `sc2/core` (wave 0)
- **Implements.** "SC2-0a core contracts: instruments" (above): `steer`, `hunt`, `calm`, `isSteered`, `isHunting` in
  `MobTamper` (defaults that refuse) and in atmosphere's implementation and mob mixin; the spoiler words; the side test
  (no client import outside client packages; `hunt` called only from `accident.sc2`); the ARCHITECTURE pointer line
  ("Self-created layer ... `docs/SELF_CREATED.md` and `docs/SELF_CREATED_2.md` (spoilers)") and the `MobTamper` row.
- **Tests.** As listed in that section, plus: steer and freeze on one mob do not fight (`isTampered` true for both);
  `release` ends a steer and a hunt; a hunt on a monster keeps its target beyond its follow range up to twice it.
  Multiplayer: a hunt bound to one of two mock players calms when that one leaves, not when the other does.
- **Done.** Green build; every existing tamper test unchanged; the side test passes on the current sources.

#### As built (SC2-0a, `SelfCreated2CoreTests` 11, `VerifySc2SideTests` 3, `VerifyScSpoilerTests` +1)
- **`MobTamper`** (defaults refuse or do nothing; the stub inherits them): `steer(mob, to, speed, ticks)`,
  `isSteered`, `hunt(mob, ServerPlayer, ticks)`, `isHunting`, `calm(mob)`; `isTampered` counts both; `release` ends both
  (a hunt calmed). Atmosphere's `MobTamperImpl` adds `steerRefusal(mob, on, steeredElsewhere)` and
  `huntRefusal(mob, target, on)` (why, in plain words, or null; never shown to players), `steeredCount()`,
  `MAX_STEERED` 48, `huntTargetOf(mob)` and `expire(mob, on)` (the tick's step for one mob, for tests).
- **Steer.** The mixin's steered AI step runs only navigation, move, look (a `face` first) and jump controls; no goal or
  target selector and no mob-specific step (a steered sheep's eating, a wolf's anger timer). The first `steer` stops every
  running goal of both selectors (each its own `stop`, through `TamperedMob.a1016_02$stopGoals`); a later one replaces
  the target and the deadline (`now + ticks`, not the longer of the two) and the speed. The path goes to the target's
  own block (reach 0, so a ring slot is reached, not a block short), made again when the target moved over 1.5 or the
  walk ended short (not in the target's block nor within 1.0 across), at most every 10 ticks; the mob floats in water as
  `FloatGoal` would. Refused also: a mob without AI, a passenger or one carrying any passenger, a frozen mob (freeze
  wins: `freeze` ends a steer), a hunting one, past 48 steered (a mob already steered may be steered again). "Driven by a
  brain" is `!mob.getBrain().isBrainDead()` (covers every brain mob, later ones too); a boss is the dragon or the wither;
  the owner rule reads `OwnableEntity.getOwner()` (a player in the mob's level within 48).
- **Hunt.** Refused also: a mob neither `NeutralMob` nor `Enemy` (it cannot attack), any owner reference (tamed or owned,
  owner online or not), a boss, a brain mob, one without AI; the target is "a living participant in the mob's level" as
  an object: not removed (a disconnect removes it), alive, in that level, neither creative nor spectator, so mock
  players work in tests. On: the target set, a `NeutralMob` gets `setPersistentAngerTarget` and `setTimeToRemainAngry`,
  the follow range doubled by a transient modifier (`a1016_02:sc_r` since R13-11, never saved), a steer ended. Each AI step
  (not frozen): the player put back as target while within the doubled range (and a neutral mob's anger kept), let go
  past it (the hunt goes on and takes them back). A hurt from another player after the hunt began ends it uncalmed, so
  vanilla's hurt-by goal turns the mob on that friend (the pack's "a friend who hits a wolf gets that wolf"). Calmed (anger,
  target, last attacker cleared, running goals stopped, range back) at its deadline, on `calm`, on `release`, when its
  player is not a valid target any more (every tick), at once on that player's `PARTICIPATION` false (`releaseFor`, in
  view or not), and while the layer is off (every steer ends and every hunt calms within a tick; the tick asks
  `SelfCreated.enabled()`). `calm` works on any mob, hunted or not, so a hunt's end calms the wolves a friend angered too.
- **Side test** (`core.VerifySc2SideTests`, over `src/main`, comments and strings aside): no `net.minecraft.client`,
  `com.mojang.blaze3d` or `net.fabricmc.fabric.api.client` outside a package with a `client` segment; no mixin of a client
  package on the common or server side of its config and every client-side one in a client package; client entrypoints
  in client packages, main ones not; `hunt(` only in `accident/sc2/` (and where it is declared). The game test sources
  are not scanned (development only; the gallery's client test lives there).
- **Spoiler words** (`VerifyScSpoilerTests.SC2`, `SC2_GENERIC`): every word of D-103's list but `file` (03's existing log
  line "could not read the file from before" uses it: `SC2_DROPPED`, for R13 by hand); `record`, `hollow`, `circle`,
  `friend`, `wall`, `house`, `chest`, `door`, `step` were already held. Iris's phrase "shader pack" (ShaderPacks' WARN) is
  taken out before matching (`PLAIN_PHRASES`), so `pack` stays held. The log check now covers the `sc2` packages too, and
  the config checks find every sc2 section by itself: a class `ScNNConfig` in an `sc2` package (its `SECTION`, else
  `scNN`; opaque from v1, no old keys), so no later task edits the spoiler test; a section in the file that is neither
  listed nor found fails with that hint.
- Outside the owned files: `atmosphere/mob/TamperedMob.java` (one method, `a1016_02$stopGoals`) and the gametest
  `fabric.mod.json` (the two new classes, after `SelfCreatedCoreTests`). Version `1.5.0-26.3`.

### SC2-0b `sc2/kit` (wave 0)
- **Implements.** "SC2-0b core contracts and the structure kit" (above), with these fixed: for HOLLOW, `Place.anchor`
  is the grove's centre; for PYRAMID, `Place.apex` is the apex block; for ARCHIVE, `Place.anchor` is the dais lectern;
  for LINE, one place per segment with `box` its column span; `entrance` is the outer mouth cell (the open one).
  `Live.place` builds chunk by chunk with light updates and never in game code (a source test: callers only in tests,
  gallery and `/a1016 sc g`).
- **Tests.** As listed, plus: a piece's NBT holds only its parameters (under 200 bytes) and a saved start reloads to the
  same shape; the snapshot is cleared on server stop (two server starts in one JVM do not leak); `Remains.containing`
  through the chunk-grid index matches a brute-force search over 200 random points. Multiplayer: `Visits` with three
  mock players, one a spectator (spectators are never "visited"), one leaving inside (a leave event).
- **Done.** Green; the demo kind `K0` generates in its test environment and matches `Live.place`; nothing registers
  `K0` outside a development environment.
- **As built (SC2-0b).** Core: `core.Remains` (`Kind` with `number()` 16 to 20; `Place` as planned, its box a copy each
  call, `distanceTo`, `boxHolds`; `known`, `containing` (oldest first), `nearest` (by distance to the box), `visited`,
  `trespassedAt` (overworld game time), `Stub`), `Services.remains()` / `installRemains`, `HerobrineEvents.REMAINS_ENTERED`,
  `REMAINS_LEFT` (`RemainsVisit`: level, place, player) and `REMAINS_ANSWERED` (`RemainsAnswered`: level, place, answer,
  uuid), each listener isolated. The kit, `world.sc2.kit`:
  - **A structure task registers** in its `SNN.init()`: `Sc2Kind.register(new Sc2Kind("s16", Remains.Kind.PYRAMID,
    Sc16Config::get, siting, PyramidShape::new, Sc2Kind.Interiors.FROM_SHAPE))` (its config implements `Sc2Kind.Gates`:
    `fromStage()`, `minOriginDistance()`), `Answers.giveBackWhenOff("sc16", "world:s16")`, and its data: a structure JSON
    `{"type": "a1016_02:world/sc2", "kind": "s16", "biomes": ..., "step": ..., "spawn_overrides": ...}` and a structure
    set (`a1016_02:world/sc2r` for the rays: `salt`, `rays`, `first`, `step`, `jitter_degrees`, `jitter_blocks`,
    optional `slots` (64), vanilla's `exclusion_zone`; or vanilla's random spread). One JSON per kind id (`s16`, `s16d`).
  - **The kit's gates** (`Snapshot.refusal`, before the kind's `Siting.find(Query)`): a snapshot, the switch on, the
    kind's config, the stage at least `fromStage`, the candidate chunk's middle at least `minOriginDistance` from (0, 0)
    **and from the world spawn**. Vanilla then checks the JSON's `biomes` at the ground of the origin's column (so an
    underground origin is judged by its surface biome). A start whose box reaches past `Sc2Structure.MAX_REACH` (8)
    chunks from its start chunk is refused (vanilla places pieces only within 8). `Siting.Query`: `chunk`, `terrain` (the
    noise, thread-safe), `snapshot` (`config(id, Type.class)`, `salt()`), `seed(kind)`, `random`, `center()`,
    `groundGrid(x, z, half, perSide)`; statics `spread`, `median`, `fromOrigin`, `facingToward`, `facingToOrigin`.
  - **`Start(origin, facing, variant, seed)`** is all a piece saves (with the kind id: `k v s o f`; with vanilla's keys
    under 200 bytes) and all a `Shape` is rebuilt from (`Sc2Kind.Shapes.create(start, terrain)`). **`Shape`**: `box()`,
    `fill(Writer, clip)`, `anchor()`, `entrance()`, `entranceFacing()`, `apex()`, `interior()`, `extra()` (a small tag
    kept with the place). Its Javadoc holds the rules: write only in the clip, any chunk order and thread, no shared
    random, terrain only through the constructor's numbers or `Writer.ground/surface/terrain`, decide everything spanning
    chunks in the constructor, seal before opening.
  - **`Writer`** (worldgen: `Writer.worldgen(level, clip, terrain)`, public for the line's feature; gallery: `Live`):
    `set/get/setIf` (reads outside the clip are `VOID_AIR`), `ground/surface/terrain`, `contents(pos, stacks)` (any
    container, worldgen-safe through components), `blockEntity(pos, Type.class, edit)`, `written()`. Both paths set
    blocks alike (no neighbour updates, no onPlace, no drops, fluids ticked) and mark fences, panes, bars, walls, stairs,
    gates, ladders, torches and lanterns, updated from their final neighbours when the chunk is finished (worldgen) or
    after the last chunk (`Live`). **`Draw`**: `fill`, `paint`, `shell`, `hollow`, `depth`, `columns`, `column`,
    `foundation`, `clear`, `deadGround`, `tunnel` (rising or falling 1 in n, 3 of headroom at each step), `chest`,
    `shelf` (chiseled, slot states set), `lectern`, `Mix` (weighted materials), `unit/chance/between` (per-cell
    randomness: pass cells relative to the origin so a start looks the same anywhere), `intersection`. **`Seal.around(w,
    clip, columns, REACH, material)`** over a per-column interior (`Seal.range(lo, hi)` / `NONE`). **`Frame`**
    (`Frame.square(min, side, facing)`, `pos(r, u, f)`, `box`, `turn(state|direction)`): design facing NORTH, turned.
    **`Terrains`**: `noise(level | generator, randomState, height)`, `Frozen` (the level before the shape).
  - **`RaysPlacement`**: bearings from the world's seed and the set's `salt` only (not the world's random salt), so
    `slots(seed, n)`, `slot`, `slotChunk`, `bearing`, `isSlotChunk` answer where they are (`sc 16 a`); each ray but the
    first turned by up to half of `jitter_degrees`, so neighbouring rays stay within `jitter_degrees` of even;
    `jitter_blocks` is kept under `step / 2 - 16`.
  - **`Snapshot`** (record: `enabled`, `stage`, `spawn`, `seed`, `salt`, `configs`), `Recorder` (`offer(kind, start,
    dimension, remains, shape)` or `offer(Entry)` for a feature's segment; `drain(Sc2Data)`), **`Sc2Data`** (`WorldData`'s
    optional key `sc2`, read entry by entry leniently: places with kind id, start, extra, visitors and each answer's counts;
    each player's trespass time and answer counts), **`RemainsImpl`** (`LIVE`, `over(data)` for tests, `record(place)`,
    `atEntrance`), **`Visits`** (`LIVE`; a look every 20 ticks; `placeOf`, `inside(server, place)`, `stopped`; the way-in
    rule: `ENTERED_TUNNEL` once per visit that entered within 30 s of standing within 3 of the place's mouth; nothing while
    switched off), **`Answers`** (`LIVE.submit(level, place, Rules.of("16b", "world:s16/close").perPlaceEver(3).health(10)
    .tool(Answers::pickaxe).limitSeconds(120), planner)` returns DONE, WAITING or REFUSED with the reason; the planner
    returns an `Edit.of(cells, traces -> traces.batch(...)...commit())` each try; `Rules.part()` for a later edit of an
    answer already counted (16c's next torch); first-hour, quiet, health, tool and the three caps checked for everyone
    inside; `carries`, `pickaxe`, `digs(state)`; `waiting`, `whyWaiting`). Per-visit caps (64 edits, 6 torches) stay each
    answer's own.
  - **`Live`** (gallery, tests, `/a1016 sc g` only; `liveIsCalledOnlyFromTestsAndTheGallery` allows the game test
    sources, `debug/gallery/**`, `debug/client/gallery/**` and files named `*Exhibit*` or `*Gallery*`): `place(level,
    kind, start)` returns `Result(box, place, shape, kind, start, written)` (the place's anchor, entrance and apex are
    the gallery's camera marks; `result.known()` makes it known, for staged answers); `settleLight(level, box)` before a
    test reads light; worldgen's own path for tests: `generate(level, structure, chunk)` (siting included), `startOf(level,
    structure, start)` (no siting: a flat test world fits none), `reload(level, start)` (the NBT round trip),
    `placeStart(level, start)` (each chunk's writable column, then vanilla's chunk finish). The pattern for a "worldgen
    equals `Live.place`" test is `SelfCreated2KitTests.theDemoBuiltByWorldgenEqualsLivePlace`.
  - Registration: `world.sc2.WorldSc2` (types `world/sc2`, `world/sc2p`, `world/sc2r`; the service; the lifecycle; the
    revert `sc2` that drops pending answers and visits) calls `S16`..`S20`, `S28`; `AtmosphereSc2` (`S21`..`S24`, `S29`),
    `AccidentSc2` (`S25`, `S26`), `EntitySc2` (`S27`, `S31`), `EntitySc2Client` (`entity.client.sc2.s27.S27Client`),
    `LoreSc2` (`S30`), one line each in the six inits.
  - Demo kind `k0` (`DemoKind`): a 20 x 12 x 20 house with a cellar, the worked example of every rule; registered only
    in a development environment, its JSON only in the game tests' resources, its siting answers only armed chunks, its
    places recorded as `FLOOR`. Test environment `a1016_02:world/sc2_kit`.
  - Notes for the structure tasks: a structure's step decides what of its own chunk's decoration comes after it
    (`top_layer_modification` puts it last); a neighbouring chunk decorated later can still reach in, as with vanilla's
    structures. Outside the owned files: one line in the game tests' `fabric.mod.json` (`SelfCreated2KitTests`).
  - Tests (`world.SelfCreated2KitTests`, 19): the rays (same seed, 120 within 10 degrees over 200 seeds, slot distances
    and jitter, only slot chunks, an exclusion zone, the JSON form); the gates; the demo built by worldgen (siting, NBT
    round trip, chunk by chunk, finished) and by `Live` 256 blocks away block for block, block entity and light alike,
    the seal taking a cave, water and sand, the recorder knowing it once across two loads; the piece's NBT (only its keys,
    under 200 bytes), its reload and `startOf`; a sloped tunnel's headroom; the recorder; the stop; the index against a
    brute-force search at 200 points and `nearest`; visits with two players (the way in, trespass, the switch off) and three
    (a spectator, a leave inside, a player turned spectator); answers (waiting in view, done out of view for two, dropped
    at the limit; quiet, health, tool, first hour, nobody inside, the three caps, a part); the stub; `Live`'s callers;
    the demo only in development; `Sc2Data` and `WorldData`'s `sc2` round trip and leniency.

### SC2-0c `sc2/gallery` (wave 0)
- **Implements.** "The gallery" (above): the registry and props, `/a1016 sc g`, the blueprint renders and PNG writer,
  `GalleryClientTest`, `build.gradle` (client game tests enabled, a `gallery` task with `-Pgallery`, `build` and `check`
  not depending on them), Fallback A (`runDevGallery`, `GalleryAutopilot`, `DevWorld`'s deep flat option), Fallback B,
  exhibit `00`, the empty test classes, `build/gallery/index.txt`.
- **Tests.** Server: exhibit `00` places and every view's eye is inside the loaded area; the PNG encoder round-trips a
  small image; `Gallery.register` does nothing outside a development environment; the gallery literals pass the
  spoiler words. Client: one run of `./gradlew gallery -Pgallery=00` on this machine.
- **Gallery.** `00`: `far_day`, `far_dusk`, `far_fog`, `near`, `aerial`, `section` (of a staged rock block), frames
  `loop` (12 frames); the blueprint renders. Open them; the fog shot shows the mod's fog, the dusk shot is dusk.
- **Done.** `./gradlew build --dry-run` lists no client game test; `./gradlew gallery -Pgallery=00` writes the PNGs and
  the index (or, if the client test API cannot run here, Fallback A does, and the report says which path works and the
  exact command every later task uses).

#### As built (SC2-0c, `debug.gallery.SelfCreated2GalleryTests`, 6 tests)
- **The client game test path works on this machine** (macOS, Apple Silicon): `./gradlew gallery -Pgallery=00` opens a
  game window for the run, writes 12 views, 12 frames, 7 blueprint renders and the index in about 90 s, and quits.
  Fallback A (`./gradlew runDevGallery`) works here too (the same PNGs at the window's size, 1708 x 960 on this
  display, in about 60 s), and so does Fallback B (`./gradlew runGalleryPlans`, the renders only). `./gradlew build
  --dry-run` lists no client task (Loom wires only `runGameTest` into `check`); `build` never opens a window.
- **Code.** `core.Gallery` (the registry; `Exhibit`, `Placer`, `Placed`, `View`, `Frames`, `Sky`; `standardViews`,
  `sectionView`, `look`, `ground`), `core.GalleryProps` (the props below). `debug.gallery`: `GalleryInit` (dev only:
  exhibit `00` and `/a1016 sc g`; one line in `DebugInit`), `GalleryCommands`, `GalleryRun` (output folder, layout,
  placing, a view's sky and camera, the index and the PNG check), `Blueprint`, `Png` (RGBA writer and reader), `Exhibit00`.
  `debug.client.gallery`: `GalleryScript` (the one script both drivers run), `GalleryWorlds` (the deep flat),
  `GalleryAutopilot` (Fallback A); `debug.client.DevWorld` gains `-Da1016_02.devFlat=deep` and starts the autopilot.
  Game test source set: `debug.gallery.GalleryClientTest` (the `fabric-client-gametest` entrypoint) and
  `debug.client.gallery.ClientTestDriver`; test environment `a1016_02:debug/gallery`; the twelve empty seam classes,
  listed in the gametest `fabric.mod.json`.
- **Differences from the plan.** `00`'s time-lapse is named `top`, not `loop` ("loop" is a spoiler word in
  `VerifyScSpoilerTests.MORE`); `00` has the whole standard set plus `near_fog` (the fog proof), `in_1` and `section`.
  `GalleryProps.clearSky` (a plain `clear` empties a box). `View` and `Frames` carry an optional `note`, the plain
  description written only beside the PNG in the index (D-103). `fabric-client-gametest-api-v1` is under `suggests`,
  not `depends`: the gametest mod also loads on the dedicated test server, where that client module is absent. The
  gallery world is at Traces (`GalleryRun.STAGE`), so its dusk fog and fog-night levels are Traces'. A blueprint draws
  each block 1 to 8 pixels square (the longest side about 768 px).
- **Found on the way.** The test API's `waitForChunksRender` waits for the whole square of chunks, whose corners the
  server never sends, so above a few chunks it always times out; the harness waits on `GalleryScript.chunksReady`
  (vanilla's round view rule and every section built). The server keeps sending the view distance the client asked for
  at join (5) until the options are broadcast (`Options.broadcastOptions`). A camera 300 out unloads the exhibit's
  chunks and their mobs come back as new entities, so a time-lapse finds its mobs again by tag (`GalleryProps.staged`);
  a navigation target a block or two off gives an empty path, so the staged walk aims 4 blocks ahead, and a mob cutting
  a corner never comes near the corner's point, so its place on the path is the nearest of the next points. Game tests
  of one batch run side by side: a test ends only its own walk (the handle `walk` returns), never `stopWalks`.
- **Open.** At the plan's render distance (16 chunks) the client draws nothing past about 256 blocks, so the standard
  `far_*` views (300 out) show no exhibit at all. Either the far distance comes in to about 220 or the runs that judge
  silhouettes use `-Pgallery.rd=24` (the index records the render distance of each run).

#### How to use the gallery
- **Run it.** `./gradlew gallery -Pgallery=16,16s` (exhibit ids, comma-separated; `all` for every exhibit; `00` when
  none is given). `-Pgallery.rd=24` sets the render distance (default 16). A window opens for the run and closes at the
  end; each exhibit takes about a minute and a half. Never part of `./gradlew build`.
- **Read it.** `build/gallery/<id>/<view>.png` (1280 x 720, FOV 70, hud hidden, gamma 0.5, no clouds),
  `build/gallery/<id>/<frames>/f00.png` ..., `build/gallery/<id>/plan_top.png`, `plan_north|south|east|west.png`,
  `plan_ns.png`, `plan_ew.png`; `build/gallery/<id>/index.txt` and `build/gallery/index.txt` (tab-separated: file,
  exhibit, what, time, sky, light, check, note). The check flags `BLACK?` (nearly all black), `FLAT?` (one colour: a
  camera inside a block) and `CHUNKS?` (shot before the chunks were ready); the log has one line per view. A new run
  empties the exhibit's folder of PNGs and frame folders first; `NOTES.txt` stays. Open every PNG with the image tool.
- **Fallbacks.** A: `./gradlew runDevGallery -Pgallery=<ids>` (the dev client, its own folder `run/gallery`, a fresh deep
  flat world each time; screenshots at the window's size). B: `./gradlew runGalleryPlans -Pgallery=<ids>` (headless:
  the blueprint renders only; `-Pgallery.tests=all` runs all of the gallery's server tests instead).
- **Under shaders** (Fallback A only, opt in): `./gradlew runDevGallery -Pgallery=31 -Pgallery.iris -Pgallery.rd=18`
  copies Iris and Sodium (`mods/iris-*.jar`, `mods/sodium-*.jar`), the shaderpacks with their settings and
  `config/iris.properties` (shaders turned on) from a real install into `run/gallery`, and writes the PNGs to
  `build/gallery-iris/<id>/`. The install is Mariano's launcher folder (`~/Library/Application Support/minecraft`, else
  `~/.minecraft`) or `-Pgallery.iris=<a minecraft folder>`. A later run without the flag takes the two mods out again.
  It touches only that run: never `./gradlew build`, the game tests or the jar. After the run, search the log for
  `(Iris)` lines at ERROR and for `override list` (a pipeline Iris was not told about), and open the shots: any new
  render pipeline or shader of the mod's own is checked this way (`core.VerifySc2FinalTests` lists them).
- **By hand** in the dev client (op, dev only): `/a1016 sc g` lists the exhibits; `sc g <id>` places one 48 blocks
  ahead and lists its cameras; `sc g <id> <shot>` makes you a spectator at that camera with its sky and time (F1, F2);
  `sc g <id> f <frames>` sets a time-lapse's camera and starts its behaviour; `sc g <id> plan` writes the renders;
  `sc g all` lays every exhibit out along +X, 640 apart.
- **Add an exhibit** from your task's own init: `Gallery.register(new Gallery.Exhibit("16", "16", S16Gallery::place))`
  (ignored outside a development environment). The placer builds at `origin`, the first air cell above the ground
  (exhibit `i` of a run stands at x = `i * 640`, z = 0; the world is a deep flat: bedrock, stone from Y -63 to 36,
  dirt, grass at Y 40, so `origin` is Y 41), with `Live.place(level, shape)` for a structure (SC2-0b), and returns
  `new Gallery.Placed(box, views, frames)`: the box the blueprints cover; `Gallery.standardViews(level, box, entrance,
  facing)` (`far_day`, `far_dusk`, `far_night`, `far_fog`, `mid`, `near`, `mouth`, `mouth_night`, `aerial`) plus your
  own (`Gallery.View.looking(name, eye, at, time, sky, survey)`; `.withSurvey(true)` for `survey_*`;
  `GalleryProps.cutaway` then `Gallery.sectionView` for `section`). Names are camera words or numbers; what a view
  shows goes in `.noted("...")`. Every camera's chunk is loaded after placing; keep eyes out of blocks (the test of `00`
  checks its own; copy it for yours).
- **Stage a behaviour.** Summon test mobs with `GalleryProps.summon` (tagged, persistent), then a
  `new Gallery.Frames(name, count, everyTicks, camera, level -> ...)`: the camera is set first, `start` runs once on the
  server, a frame is shot at once and then every `everyTicks`. Inside `start`, find the mobs again with
  `GalleryProps.staged(level, area, type)`, never keep them from placing. `GalleryProps.walk(mobs, path, speed, ticks,
  loop)` with `GalleryProps.rectangle` is the gallery's own staged walk (it returns the walk's own stop; the run ends
  every walk at its end); a behaviour that is the feature's own goes through the feature's code (`MobTamper` once
  SC2-0a is merged). The gallery world's time and weather stand still, no mob spawns by itself, the difficulty is normal
  and the camera is a spectator, so the director never acts on it.
- **Props** (`core.GalleryProps`, dev only): `set`, `fill`, `clear(level, box)`, `rock`, `hill(level, center, radius,
  height)`, `sea(level, box, depth)`, `cutaway(level, box, side[, plane])`, `scaleFigure(level, feet, yaw)`, `tree(level,
  base, "minecraft:oak")`, `command(level, at, command)`, `summon`, `staged`, `walk`, `stopWalks`, `rectangle`; the sky:
  `time`, `fogNow(server, level[, amount])`, `dusk`, `clearSky`, `sky(server, level, Sky, time)`.

### SC2-1 `sc2/pyramid` (wave 1)
- **Implements.** 16 (all variants), 16b, 16c with the pit glimpse; `sc16`; `/a1016 sc 16 [a|arm]`; exhibits `16`,
  `16x`, `16s`, `16l`, `16d`.
- **Tests.** 16's list (siting, rays, shape invariants, worldgen equals live, interior test, 16b, 16c, the sea height
  rule) and its multiplayer tests (a friend at the mouth, both inside, a friend's torch); the fair-danger tests: 16b
  only with a pickaxe on every player inside and 10+ health; the pit never drops more than 1 per step anywhere.
- **Gallery.** Every view listed under 16, three rounds at least. **Done.** Green; the art bar met in the worker's own
  judgment, PNG paths in the report; `postProcess` timing for each size logged in the report (median and worst).

#### As built (SC2-1, `world.SelfCreated2PyramidTests`, 11 tests)
- **Kinds and data.** `world.sc2.s16.S16.init()`: kinds `s16` (land) and `s16d` (sea), both `PYRAMID`, `Sc16Config::get`,
  `PyramidSiting.LAND` / `SEA`, `PyramidShape::new`, interiors `FROM_SHAPE`; `Answers.giveBackWhenOff("sc16", "world:s16")`.
  One structure set `world/s16` holds both structures on the rays (vanilla tries one by weight, then the other when the
  biome refuses it), exclusion zone villages 8; both structures at step `top_layer_modification` (the chunk's own trees
  are in when it builds, so they are stripped), `creature` spawns emptied over the full box. Tags `world/s16` (the eight
  biomes) and `world/s16d` (`#minecraft:is_ocean`). Variant bits: size 0 to 2, closed 4, light 8, sea 16.
- **Siting** (`PyramidSiting`): the weighted size, then each smaller, then each larger; 9 ground samples (corners, edge
  middles, centre of the footprint, spread at most `c`); on land at most 1 of 9 under water; at sea the centre under
  water and `median + R >= water top + 12`; the lowest course at the median + 1, the way in toward the origin; closed at
  `e` (always at sea), a light at `f` (0.5 at sea). At most 56 samples.
- **Shape** (`PyramidShape`), as planned (shell 3, core inset 3, rim 3, pit opening `R - 6`, one down and one in, a
  brick lining with a brick kerb on the rim's edge, the 3 x 3 floor `R - 7` steps down with its cobblestone and torch),
  and:
  - The core is the mass inset, so the rim lies under its stepped overhang (1 to 3 high: the way in comes out under it)
    and the hall opens over the pit. The way in's floor is dirt; a 4 x 3 x 5 approach in front is cleared where the
    land is higher than the base.
  - His pass, decided from the seed: wear in vertical streaks (a slow value noise running down each face), more at the
    arrises and higher up: a step worn to a brick stair (high side in), to a slab, or gone; the top 4 courses sharp; 1
    face brick in 300 missing. A damp band of mud bricks and packed mud on the lowest 1 to 5 courses, its top in drips.
    1 to 3 dead-end cuts, the first always in the face of the way in, a third to half way up and 4 to 12 to one side;
    under each cut a dark run (brown terracotta, mud bricks) down the steps, as if it had wept.
  - **Cuts 6 to 10 deep, not 20**, the core kept 2 from them (the seal's reach, not 3): the masonry round a cut's end
    stands into the vault, and at 20 deep these read as beams across the hall (gallery round 3).
  - **The seal covers the pit and the floor only.** Above ground the 3-thick shell is the seal; the kit's Chebyshev reach
    of 2 measured round the core would have added a brick on every step of the 45 degree faces.
  - The grounds: dead (dirt, coarse nearer the base, leaves stripped 32 up) to `R + 24.5` with a sharp circular edge that
    cuts the trees standing on it; past it a dithered fringe to `R + 40.5` (85 % dying down to 15 %, half on average)
    where grass dies but trees keep their leaves. At sea: kelp, seagrass and pickles become water to `R + 24.5`.
  - The sea form: no way in, no pit, a sand floor; whatever is taken (wear, missing bricks, cuts) fills with water,
    stairs and slabs under the surface are waterlogged (the water's top is read once, in the constructor).
  - The place: anchor the pit's floor (the sea form: the floor's centre), entrance the outer mouth cell (where it would
    be on a closed one), apex the top block.
- **16b and 16c** (`PyramidAnswers`; visits from `REMAINS_ENTERED` and `REMAINS_LEFT`, a look every 20 ticks only while
  someone is inside a pyramid): as planned. 16b: `perPlaceEver(i)`, health 10, a pickaxe, 60 s to be done out of view; a
  refused ask is asked again after 30 s; the edit is planned again at each try (the mouth open, everyone inside `h` or
  more from it, no player but a spectator within 16 of it, inside or out, four bricks beside it, nearest first). 16c:
  one a player a day (a visit's first light counted, the rest its parts), 8 s each to be done; the lights are torches
  (every `BaseTorchBlock`) and lanterns any player placed (the footprint), in the core or pit, above the player's feet,
  the farthest first. The glimpse: the side middles and corners of the pit's rings 14 to 20 above, once a visit (when
  `glimpse` says FIRED). `/a1016 sc 16 [a|arm]` as planned.
- **The dig.** A block of a closed one's mass broken by a non-spectator: `DUG_INTO_PYRAMID`, once a place (kept as `16s`
  in the place's counts).
- **Exhibits** (`Sc16Gallery`): `16` (95), `16x` (127), `16s`, `16l`, `16d` (a basin dug into the flat), the way in
  facing east so the far cameras look into the sunset; trees in four bands (bare inside the ring, cut on its edge, alive
  beyond), the scale figure; every standard view plus `near_fog`; `16l` adds `mid_night` and `near_night` (at Traces the
  mod's night haze hides the top light at 200; a shaderpack's does not); `16` and `16x` add `in_1` to `in_3` (true
  light) with `survey_1` to `survey_4` (night vision) and `section` (a second one built 480 north and cut open).
- **Tests** (`SelfCreated2PyramidTests`, own spots 163 000 to 175 000): the siting (the gates, the spread and the size
  fallback, dry land, the sea's rule, the chances, the biomes); the rays and every way in toward the origin; the shape
  built over rock with three leaks (shell, core, every pit level, lining, kerb, rim, the floor and its light, the seal,
  no drop over 1 between neighbouring floors); worldgen equals `Live.place` and is recorded once (env `world/s16`); the
  inside (and the index); 16b alone (time, tool, health, distance, view, the ledger and its undo, the day cap); 16b
  with two (a friend at the mouth, a friend inside without a pickaxe, both closed in); 16c with three (depth, view,
  placed lights only, a friend's light taken, never near a friend or a friend's last light, the cap, farthest first, no
  drop, ledgered, the glimpse once after 4 on a step 14 to 20 up); the dig marked once; the sea form dry after its
  water is made to flow; the cost per chunk (only with `-Da1016_02.s16cost=true`).
- **Cost** (worldgen's own path over solid rock, this Mac, others building at the same time): base 63: 100 chunks,
  median 0.08 ms, worst 6.8 ms; 95: 144, 0.07, 4.0; 127: 196, 0.14, 5.1.

### SC2-2 `sc2/archive` (wave 1)
- **Implements.** 17 (both hall lengths), 17a to 17e, the aisle glimpse, 11+; `sc17`; `/a1016 sc 17 [arm|b]`; exhibits
  `17`, `17l`, `17n`.
- **Tests.** 17's list and its multiplayer tests; fair danger: the dark never takes the player's row or the next; no
  shaft or fall over 3 anywhere in the shape; 17c conserves material; 11+ keeps every rule of 11
  (`t0rchsHoleIsADeadEndOnlyTheTrailReaches` and the rest stay green).
- **Gallery.** Every view listed under 17. **Done.** As SC2-1.

#### As built (SC2-2, `world.SelfCreated2ArchiveTests`, 14 tests)
- **Code.** `world.sc2.s17`: `S17` (the kind `s17`, `Answers.giveBackWhenOff("sc17", "world:s17")`, the rules' tick, 11+'s hook,
  the commands, the dev exhibits), `Sc17Config` (`sc17` v1, keys `a` to `r` as planned), `ArchivePlan` (the geometry, pure,
  from the start and the three ways' lengths, which the place keeps in its `extra` as `t`: so the interior and the answers
  rebuild it without terrain), `ArchiveShape` (every block a pure function of local coordinates, and of the column's ground
  for the ways' lining), `ArchiveSiting`, `ArchiveRules` (17a to 17e and 31b at runtime, session state only), `LightsOut`
  (17b's plan, pure), `OtherDoor` (17c), `Records` (17a), `Sightings` (17e's index), `ArchiveTrail` (11+), `ArchiveEnv` (the
  live world's or a test's), `ArchiveCommands`, `ArchiveGallery`. Local coordinates: `r` right, `u` up from the hall's floor
  (the start's Y), `f` toward the world's origin; the start's origin is the middle of the hall's entry wall. Data:
  `worldgen/structure/world/s17.json` (step `top_layer_modification`), `worldgen/structure_set/world/s17.json` (random
  spread 40 / 18, 31 / 14 since SC2-9, salt 1716203, the trial chambers set excluded within 4),
  `tags/worldgen/biome/world/s17.json` (land).
  Test environment `world/s17`; the tests' bodies are in the game test source set's `world/sc2/s17/ArchiveTestKit` (the
  archive's package, to reach its parts), the seam class calls each.
- **The hall, as the gallery decided** (beyond the plan's text): the stacks' nave ends are oak log piers carrying one
  gallery deck a side over the stacks and their aisles (open only over the bay), one oak fence rail along the nave and the
  deck's ends (the plan's rails round every row read as a paddock), ladders at the deck's four corners and at the back
  cell of every other row's pier; oak ribs up the long walls above the deck and oak beams across under the ceiling on every
  row line; a spruce runner 3 wide down the nave; the end wall all chiseled shelves left empty (the records wall in its
  middle), the long walls more emptied the nearer the end (up to 55% chiseled and empty); lamp posts either side of the
  nave on the rows' rhythm across the open floor before the dais and at its steps. Cobblestone shell 2 thick (8% mossy).
- **Light**: glowstone only, so 17b can take it all: the ceiling grid (4 x 4, 6 a row), a sconce at `u` 3 in each pier,
  two in the entry wall, two in the long walls' pilasters in the entry and on the rows' rhythm before the dais, the
  posts' tops, two lamps either side of the records wall; one in each cell's ceiling. **The plan's "at least 8 on every
  floor cell" cannot hold** (a ceiling 17 up lights nothing on the floor, and its own 7 x 7 cells with one light read 5 in
  their corners): the rule kept and tested is vanilla's spawn rule since 1.18, block light at least 1 on every floor one
  can stand on, so nothing spawns while the lights stand (the nave reads 7 to 14). A hall row for 17b is its six in the
  ceiling and every other light in its span (`f` from `-4k` to `-4k - 3`): the short hall's 8 rows are 62 lights, so the
  per-visit cap is 80 (the plan's 64 counted 6 a row).
- **The dais and the records**: three mossy cobblestone stair rows up to a 7 x 5 mossy platform 3 high, the empty lectern;
  behind it the 7 x 4 records wall; the list's six in its upper middle row, one in each shelf beside the middle one;
  records go into the first record shelf with room (the upper middle row from the middle out, then the lower, the bottom
  and the top rows); the lower middle shelf is kept for 17e. **17e's book goes into that shelf, not a lectern or a chest**:
  the ledger moves a stack only between containers, a lectern is none, and the shelf right behind it is the container
  beside it (no chest stands on the dais).
- **The cells**: 5 x 3 or 7 x 5, cobblestone (the infested share is baked into the start's variant, so worldgen does not
  read the config after siting), a little mossy damp at the foot of the inner walls, the maze by Kruskal over the seed's
  weights plus a quarter of the other doorways (loops), details 1 in 6 at one fixed spot each, none in the hall's door cell
  nor the open way's cell.
- **The ways in**: from three different outer cells, 2 x 2, rising 1 in 2, cobblestone-lined only 2 or more under the
  ground (the mouth is a clean square cut). A way's `mouth` is its first cell whose head reaches the ground, its `exit` the
  first cell one stands outside in; the open way is carved to its exit; a closed way is carved but for its plug, the 3
  cells (12 blocks of the land, sealed solid) just inside its mouth, with its notch outside cut too. Those 12 places of
  each way are the same for 17c: it moves a closed way's plug into the open way's. After a move `Remains.Place.entrance`
  still names the first mouth (the kit's way-in rule then counts that one only).
- **His bay**: one inner aisle on one side: its floor taken down to the shell, the shelves of its wall end gone (a
  full-height scar of bare cobble), the facing shelves taken from the floor up to a ragged height, no deck over it (railed
  round), a raw unlined 2 x 2 tunnel through the shell down 1 in 2 for 30 to a dead end.
- **Siting**: floor = the lower of 40 and the lowest of 6 corners (the hall's 4, the cells' outer 2) minus 36, minus 0 to
  6; refused under Y -24 or within 18 of the bottom, in the deep dark at the hall's depth, when a way finds no surface in
  150 cells, or when the box leaves the kit's reach (one try moved to centre it). One plan takes about 50 ground samples
  (6 corners; each way a sparse look for a valley, then from just before where flat land lets it out; tested at most 81);
  a candidate makes one or two plans, and the kit one more.
- **Rules**: every 20 ticks for each archive with someone inside: 17a (known players, by the story's list, before they
  are within 10 of the dais lectern), 17b (depth walked: 8 a cell from the open way's cell, then into the hall; 60 s at 24
  or deeper; a unit every 160 ticks through `Answers` (`17b` once a place a day, the later units `part()`), from the door
  inward behind the deepest player in the hall, never the row a player stands under nor either next one, nor a light
  within 6 of anyone; 8 rows; a player only in the cells has the cells on their way out darkened, never theirs nor the one
  before it, up to 9; `darkDaysApart` from the place's last day), 31b after the 4th row (on the nave's floor in the dark
  rows, 20 to 40 from that player, as near the way out as it can, `Figure.glimpse` facing them); every 100 ticks 17d
  (`PlayerWatch.placedNear` torches and lanterns in the cells, moved to the same spot in a cell with the same detail
  nobody stood in this visit, 2+ cells from everyone, a player in the hall counting as by the door cell; `17d` one a
  player a day, the visit's second as `part()`) and 17e (the writer's marked book seen in a container round any participant,
  3 x 3 chunks every 100 ticks, never in one of his places, session only; its chunk held loaded a minute by a ticket if
  need be; 64+ from the writer, nobody within 16; `17e` once per place). 17c for every known archive whose way in is
  loaded with someone within 256: a newcomer within 24 of the open mouth with everyone who was inside 64+ from it, or
  (alone) one coming back after a day, rolled once per return; nobody within 16 of either mouth, nobody inside or in a
  way, out of view, 2 per place, one a player a day; counted in the kit's data with `REMAINS_ANSWERED` and the tension
  (not through `Answers`: nobody is inside then).
- **11+**: a hook in `world/sc/TorchTrail` (`Breach`, set by `S17`): after 11's walk, a way from a walk cell (pathMin to
  pathMax steps, feet level with a cell's floor or one above) straight through at most 6 of land and the cells' walls into
  a cell, the shortest then the furthest; its chance rolled only then, so 11's own searches draw the same randoms.
  `TorchTrail.Result` gains `breach()` and `found()`; `BreachPlan` is the walk with 11's torches and sign and the hole;
  `leaveBreach` (cause `world:left/t0rch/breach`, never undone); once per world as 11. 11's tests are unchanged and green.
- **Commands**: `/a1016 sc 17` (the nearest known: how far, its size, visited, its wall, the answers, the open way, the
  lights), `sc 17 arm` (inside one: looks now; 17b starts now if you are deep enough), `sc 17 b` (17c now at the nearest,
  out of view and within its caps).
- **Tests** (`world.SelfCreated2ArchiveTests`, 14): mazes (every cell reached from every way, loops, the same seed the
  same); siting (the floor's range and depth, the deep dark, a way too long, facing the origin, the reach, 81 samples);
  the short hall in rock (every floor lit, walked from the door with no drop over 3, the gallery, the dais and the far
  cells reached, the list, the door, the ways, a water and a cave pocket sealed); the long hall; the interior; worldgen
  equals `Live.place`; records for three friends, never twice, none at the dais; the lights (order, never near a player,
  light 0 in the span, the glimpse, not again the day, given back); the lights waiting in view; two friends apart and one
  in the cells; the other door (a newcomer, the finder near or someone at a mouth refuse, 12 conserved, 2 at most, given
  back); markers (a friend's too, a cell that looks the same, 2 a visit); a book of one's own (only the writer's, once,
  given back); 11+ into the cells.
- **Outside the owned files**: `world/sc/TorchTrail.java` (11+) and `lore/LoreApi.java` (`bookAboutHimWriters`), as
  planned. **Open**: an archive can generate in the gallery's deep flat world (its ground at 40 fits a hall) away from the
  origin, where it may show in another exhibit's far views; worldgen cost per chunk is SC2-9's to measure.

### SC2-3 `sc2/instruments` (wave 1)
- **Implements.** 21, 22, 23, 24 (cards, configs, commands), 04+; exhibits `21`, `22`, `23`, `24`.
- **Tests.** Each feature's list, multiplayer included (two sharers per shared card, UNSHARED felt rule for 24, bound
  release on leave); the card shapes (tier, stage, tags, habits, fake) match the pacing table.
- **Gallery.** The frames listed per feature. The ring must read as a ring from above and as a procession of animals
  from the ground; the held breath's frames must show every mob turned one way. **Done.** Green; frames judged.

#### As built (SC2-3, `atmosphere.SelfCreated2InstrumentTests`, 28 tests)
- **Shared (`atmosphere.sc2.s21`, used by 22 to 24).** `Herds` (the kinds: 21's seven, 22's passive animals without
  rabbits, 24's four; `free`: alive, no leash, no rider or ride, no name, not tamed or owned, not tampered, the empty
  night's quiet aside; dusk 12000 to 13000, night 13000 to 23000, a counting fog spell, outdoors at sky light 10, `feet`:
  a standing cell over a sturdy dry top, never loading a chunk). `Sc2xData` (saved data `a1016_02:sc2x`, every field
  optional and lenient): each card's world caps (count, day, night), each player's last day of 23, where he last stood
  (from `FIGURE_LEFT` in the overworld: 23's second bearing), 04+'s roll. `FitMemo`: a card's `contextFits` answer kept
  40 ticks per player (the fire always looks again). Every scene manager has a `LIVE` instance ticked from its `S2x.init`
  and a public constructor for tests; each ends (its mobs released) on the switch, the server stop, and its own end.
- **21** (`Ring` pure, `Cluster` pure, `Rings`, `RingCard`, `S21`, `S21Gallery`). The herd: the most animals of one kind
  within 24 of a point 24 to `b` off (4 when forced), at most `c`, nearest the middle. The site: the middle on a grass
  cell within 3 if one fits, every point of the way round (and 0.8 inside and out) on ground within 1 of the middle's,
  each step at most 1. The slots go round widdershins at a fixed pace (0.085 blocks a tick); each animal is walked every
  10 ticks to the point 25 degrees (at least 2.6 blocks) ahead of its slot, at the speed factor that walks it there at
  the slot's pace (from its own movement speed; faster when behind, slower when ahead; never a run), silenced, so the
  path never ends between two updates and the spacing holds (the plan said ahead of the animal itself: that let them
  bunch). The assembly ends when all are within 1.5 of their slots or after 30 s. Anyone (any participant, not only a
  sharer) within `g` of the middle: every animal frozen facing that player's eyes for `h`, then let go. Hurt (health
  down or hurt time), fed (in love), leashed, ridden or taken by anything else: that one is let go; under 5 all are. The
  ground: only after 20 s of going round; the cells whose centre is within half a block of the circle (at most `k`) and
  the middle, grass to dirt with what grew on it gone (`world.sc.Prints`), one batch (`atmosphere:s21`), once the ring's
  area is out of everyone's view and the batch's own view check passes, tried every 5 s for 10 minutes; the switch gives
  it back through the ledger (retried every 30 s while off). The fake: the herd frozen facing one point 48 off on a
  random bearing for 20 s. A forced fire skips the time and the caps. 04+: `EmptyNight.check` calls `S21.emptyNight`
  every second inside its quiet; at night, one roll a night at `l`; on a won night a herd 24 to 96 off any participant
  is looked for every 10 s; it is that night's one (the card waits for the next night) and not one of the card's count.
- **22** (`Line` pure, `Processions`, `ProcessionCard`, `S22`, `S22Gallery`). The way is a tangent from the place's
  mouth (`Place.entrance`) to the circle of the pass distance round the target, its start 56 from the target on the far
  side; either tangent, whichever has its start cells out of view on standing ground and a walkable first 128 blocks
  (no water, no step over 2). The place: the nearest pyramid, hollow, floor or archive within `a` and at least 80 off.
  The animals: passive, free, quick enough (movement speed 0.166 or more: the slowest horses are left out), out of
  everyone's view, kinds taking turns, moved out of view to their cells (2 apart). The leader walks the line 10 ahead of
  itself at 0.8 of the slowest one's own walk (at most 0.095 blocks a tick); each next one walks after the one ahead at
  the pace that keeps 2 between them, and waits a step when nearer than 1.3 (the plan's "along the leader's trail" let the
  last ones bunch). After `d` or at the mouth each one out of everyone's view for 10 s is moved inside (the archive's
  fourth cell in from its mouth, else the place's anchor, a free cell within 4) and let go; at 400 s whatever is left is
  let go where it is.
- **23** (`HeldBreath`, `HeldBreathCard`, `S23`, `S23Gallery`). As planned. Never held: a boss, the figure (by its
  entity id, `a1016_02:him`), a pet ordered to sit, a mob something else holds, a creeper already swelling (frozen it
  would still go off). The silence goes to each sharer (`ClientEffects.silence`, 1 s fade); every mob's freeze outlasts
  the hold by 2 ticks and the scene lets all of them go in its own tick. The day is kept for the target and every
  sharer. A forced fire skips the day and the counts.
- **24** (`Trail` pure, `Followed`, `Follows`, `FollowerCard`, `S24`, `S24Gallery`). The player is a `Followed` looked
  up by UUID each step (the gallery's is a staged walker). The way they came is sampled every block (160 points); the
  animal keeps the middle of the band (22) back along it, or that far on its own side while the way is shorter; nearer
  than `c` it backs off to `a`; standing at its point and looked at (the player's view cone and sight), it is frozen
  facing their eyes; a steer is refused while frozen, so it lets go of its own freeze before it walks again. Over `d`
  and out of everyone's view it is moved 28 back along the way. Indoors 5 s with an outdoor point on the way: it waits 3
  outside the last one, frozen, facing the first one in. At night, indoors within 12 of their base: the nearest door
  (lower half), glass or glass pane within 10 with open sky on one side gets it 6 to 10 out on that side, frozen,
  facing it (looked for again every 10 s while there is none). The end (the day by the level's clock, or they left): it
  walks away and, out of everyone's view, is moved 160 off and let go; after 60 s of trying it is let go where it is,
  out of view. Bound to the player alone (UNSHARED); the staged walker binds nothing.
- **Commands** (only to the op): `/a1016 sc 21 [force|fake]`, `sc 22 [force]`, `sc 23 [force|fake]`, `sc 24 [force]`
  (no argument: the caps, what runs and the answer for you; `force` fires through the director, forced).
- **Gallery** (`./gradlew gallery -Pgallery=21,22,23,24`; camera words only, D-103: the plan's `ring_top`, `ring_ground`,
  `ring_dirt`, `file_side`, `file_top`, `breath` and `follow` are descriptions, in each index). `21`: `top` (28 up at
  dusk, 40 frames), `ground` (30 off at night, 12), `rise` (15 off from 3 up at night, 16), `aerial` (the next noon,
  the ground it leaves); `22`: `side` (12 off at dusk, 26), `top` (22 up, 16); `23`: `near` (26 frames every 10 ticks at
  nightfall); `24`: `side` (a stand-in walks, stops, looks back; 40), `out_1` and `in_1` (night, a hut). Six rounds: the
  scene never started (its first step took every animal for lost), then the ring formed; the night ground view showed a
  blob, so `rise`; the morning shot moved the herd off and the ground became a clean one-cell ring; 23's mobs came nearer
  and are put back on their spots when it starts; the file's spacing went from the trail to the one ahead; the follower
  stopped following after its first stare (the freeze refused the next steer); 24 gained the window shots.
- **Tests** (28): the ring's geometry, the herd, the caps and gates, the four cards' shapes; a staged ring of eight goes
  round widdershins on its circle, silent; bound to both sharers, A first, and either breaks it with the stare; hurt
  drops out and under five all go; a leave of both lets it go (bound, out of view only); the ground only out of view,
  given back by the switch; the fake; 04+ once a night; the file's way (pass distance, far side, toward the place); a
  file walks in single file bound to both; the plan's start out of view on the far side; moved inside only out of view;
  the bearing order; held facing the bearing, silent, the zombie mid-attack harmless, all let go together; the fit (8
  near, 3 in view, mid-fight; a swelling creeper never held); a second sharer's mobs held and both silenced, at most 40
  nearest first, the fake animals only; the follower's band, stop and stare; never nearer than 12; catch-up only out of
  view; the mouth; the window at night; moved off and let go when they leave; a friend beside it changes nothing
  (UNSHARED, bound to its player only); the trail; the switch lets every scene go. The scenes that walk mobs run in two
  test environments of their own (`atmosphere/sc2_a`, `sc2_b`), so a batch stays under the world's 48 steered.
- **Found on the way.** The live tamper lets go, every second, of mobs bound to players the live server does not know,
  so a game test with mock players checks a binding in the tick it is made (or rebinds to nobody). A steer is refused
  on a frozen mob (freeze wins), so a scene that alternates standing and walking releases its own freeze first; a
  `MobTamper.unfreeze` would keep the bind (not needed now). Registering three shared minors moves the director's
  two-player sims: SLOW_BURN's pair now feels 0.93 of one player's minors (`DirectorMultiplayerTests`' lower bound 0.95
  is 0.9 until SC2-9 re-tunes the new cards' sim fit chances). Outside the owned files: the one 04+ line in
  `atmosphere.sc.EmptyNight.check`; the two test environment files; `director.SelfCreatedPacingTests`' table (the four
  new rows) and that bound.

### SC2-4 `sc2/hunts` (wave 1)
- **Implements.** 25, 26 as accident trap kinds (`TrapType("sc25")`, `"sc26"`), their scenes, configs, commands, causes.
- **Tests.** Each feature's list; the fair-danger contract one test per clause (told: 60 s and two tells before any
  hostile act, measured; a way out: refused without one; the pillar: a player 2 up is never reached; health floor;
  marked death with its cause; one at a time with another armed trap refused; home: refused within 64 of the base).
  Multiplayer as listed (alone with a spectator; a friend arriving; the friend never hunted; leave ends it; two hunts
  never within 128).
- **Gallery.** `25` `stalk` and `attack`; `26` `call_top`. **Done.** Green.

#### As built (SC2-4, `accident.SelfCreated2HuntTests`, 20 tests)
- **Code.** `accident.sc2`: `Hunts` (the fair floors as constants a config never loosens: `TOLD_TICKS` 1200, health 14,
  64 from the base, alone within 48, 128 apart; the shared gate `gate`; `wayOut` (2 full blocks, sand counts, or a
  pickaxe, shovel or axe), `dark` (night 13000 to 23000 or a fog spell that `counts`), `outdoors` and `covered` (sky light
  10 at the head), `deepWater`, `seen` (a 90 degree cone, a clear line) within `sight` (0.85 of the fog's end the server
  reckons for that player, `FogLimits.of`, so "seen" never counts one the fog hides), `stand`; the running scenes by target, stepped
  every 10 ticks from one server tick hook, forgotten at server stop, ended at once by the switch), `HuntScene` (the
  player looked up each step; the trap it belongs to; tells with `heardBefore` (a long tell counts once, if the player
  was within vanilla's range of it) and `told`; `seenAt`, `hostileAt`; `setTrap` marks the trap set the moment anything
  turns, `endTrap` runs it out; `lifetime` for the gallery), `Prey` (the player now, or the gallery's stand-in), `Reach`
  (out of reach: a wolf's walk over collision-top floors within 5, from where they are and the area's edge; every spot
  within 3 of the column at least 2.0 under the player's floor), `HuntData` (`accident_sc2.dat`: last day per kind and
  player, count per kind, lenient), `HuntCommands` (`/a1016 sc 25|26 [player] | arm [force] [player] | reset | clear`).
  `s25`: `PackTrap`, `Pack`, `Sc25Config`, `S25Commands`, `S25Gallery`; `s26`: `CallTrap`, `Call`, `Sc26Config`,
  `S26Commands`, `S26Gallery`. Both trap kinds are live, `anywhere`, in `Traps.ALL` (`SC25`, `SC26`), so their cards are
  the planner's own (`accident_sc25`, `accident_sc26`: MAJOR, Proximity, never fake) and the accident pacing holds. Each
  reads an `Env` (the live game, or a test's own through `use(player, env)`; a forced arm uses it too).
- **Hunt range (the orchestrator's question).** The band stays 28 to 40 (steering does not use the follow range); the
  pack is steered in before it is set on: a close of at most 3 s (in to 10 blocks at speed 1.4, a growl from each), and
  the hunt starts once one is within 16 or the 3 s are up, so every wolf is inside twice its follow range (32) from the
  first tick of the hunt. All of them over 32 off for 5 s ends it ("lost": outrunning them is a way out too).
- **Wolf sounds in 26.3.** There is no howl at all (no sound event, no file). The long tell is each wolf's own sound
  variant's whine (`entity.wolf.whine`, `entity.wolf_big.whine`, ... through `DataComponents.WOLF_SOUND_VARIANT`) at
  pitch 0.5 to 0.63, three of them 9 ticks apart, volume `h`; the growl is the variant's `growl` at 1.5.
- **25 as built.** As planned, and: the bearing the pack keeps follows behind the player only while they move (still,
  they stand still), one nearer than 27 backs off, all face the player; with none seen for 30 s one rounds the player at 32
  (never across) to the highest spot with a clear line 30 to 36 out in their view cone (nearer in a fog or a short view, to
  be inside what they can see, never under 16). At the end of the first part it must be
  told (60 s, two long tells heard, one seen), else up to 30 s more, else they go. Out of reach (`Reach`) they wait: calmed,
  circling at 4.5 to 6 below, growling, at most `n` seconds, and set on at once if the player comes down; the plan's "2 up
  for 10 s" became this wait. The player hitting one in the first part sets them all on at once (their own doing: the one
  way the 60 s can be short); a friend's hit lets that one go to vanilla's anger. Going: calmed, a last long tell, walked
  60 off and released out of everyone's view (at most a minute). Config `sc25` adds `m` nightChance 0.5 (a roll per player
  per in-game day, so the trespass factor `l` has a chance to double) and `n` siegeSeconds 30.
- **26 as built.** Outdoors and 64 from the base only (D-097.6 wins over "at home"). Quiet `d` s (the mobs silenced, the
  player's ambient sound through `silenceShared`), each walking to a ring 20 to 24 off along the way it came and facing the
  player; their voices at once at `d` and again at 40 s (volume 2); one walked into view if none was seen by 45 s; from 60 s
  (only if told, up to 30 s more) they walk in and each is released at `e`; at most `i` zombies and husks toward a player
  by a wooden door, counting those already released within 12. It ends at dawn or `f`; the trap stays set a minute more
  for the ones already released. The list's word is the killer's: `DeathCauses.byKiller` (shot, bitten, else killed).
- **DeathCauses.** `WOLVES`, `SHOT`, `UNLISTED` and `byKiller`; the two words are not in `KNOWN`, since
  `/a1016 accident mark` offers `KNOWN` as suggestions and would name them before they happen.
- **Tests** (`SelfCreated2HuntTests`, 20; flat overworld patches about 18 million out, mock players in a private
  `Group`): the shared rules (way out, reach, the list's words); 25's gate refusal by refusal, the alone rule with a
  spectator, the trespass roll; the gather through the planner; the band; told, measured through the planner with the
  default numbers; open, pillar and water at the end of the first part; vanilla wolves never bite a player 2 up; a marked
  death; one trap at a time; every end (dawn, time, water, roof, two dead, the player leaving); a friend arriving, a
  friend's hit; never two within 128; 26's kinds, its quiet, voices, release at 16 and door cap, its gate and marked
  deaths; the switch. The world steers at most 48 mobs, so the heavy ones run in three test environments of their own
  (`accident/sc2_hunts_a`, `_b`, `_c`: a batch each).
- **Gallery.** `25`: `aerial` (the staged taiga at dusk), `f_1` (the first part on a fog night from the player's eyes:
  they come in to the fog's edge and hold, facing you), `f_3` (the same at dusk, one on the rise), `f_2` (the wait from the
  top of a two-high pillar, night), `f_4` (the same from behind and above, a figure on the pillar, the pack circling
  below). `26`: `aerial`, `f_1` (the player's place on a fog night: they come out of the fog to the ring, stand, then come
  in), `f_2` (from above at night with night vision: the convergence and the ring). Frame names are numbers (D-103: the
  plan's `stalk`, `attack`, `call_top` are descriptions; `call` is a held word). The staging runs the scenes themselves
  around a stand-in (`Prey.standIn`, no trap, a `lifetime`); a time-lapse finds its mobs with `GalleryProps.staged`.
- **Outside the owned files.** `accident/Traps.java` (the two catalogue entries), `accident/AccidentGameTests.java` (the
  catalogue's count, 21 to 23), `accident/SelfCreatedAccidentTests.java` (`theStandingRingAndTheCardsMoment` assumed the
  server's tick was under 10 000 when it ran; its two stands are now 10 000 after the tick it runs at), and the three new
  test environment files.

### SC2-5 `sc2/hollow` (wave 2)
- **Implements.** 18, 18b; `sc18`; `/a1016 sc 18`; exhibits `18`, `18w` (on a gallery hill).
- **Tests.** 18's list and multiplayer tests; fair danger: the floor has no drop over 3; 18b needs a pickaxe on every
  player inside. **Gallery.** Every view listed under 18. **Done.** As SC2-1.

#### As built (SC2-5, `world.SelfCreated2HollowTests`, 8 tests)
- **Kind and data.** `world.sc2.s18.S18.init()`: kind `s18` (`HOLLOW`, `Sc18Config::get`, `HollowSiting`, factory
  `HollowSiting::create`, interiors from the record's extra), `Answers.giveBackWhenOff("sc18", "world:s18")`, 18b's
  events and tick, `/a1016 sc 18 [arm]`, exhibits. Structure `world/s18` at `top_layer_modification` (every feature of
  its own chunk is in when it builds: no spring, lichen or dripstone of that chunk lands inside; a neighbour decorated
  later can still reach a few cells in at the chunk's edge, as with vanilla's structures; the seal shuts fluids), spawns
  as vanilla; set `world/s18` random spread 48/24, salt 180457; biome tag `world/s18` (jagged, frozen and stony peaks,
  snowy slopes, grove, windswept hills, gravelly hills and forest, wooded badlands, meadow). Variant bits: opening 1,
  spruce 2, `d` 2-5, `e` 6-9, `f` 10-14, the stems' count 15-19 (the config's geometry is kept in the start, so a config
  change never moves a cell of a recorded one).
- **Siting** (`HollowSiting`): the biome at the chunk's middle first (1 sample, so other land costs nothing more), the
  9 x 9 ground grid 16 apart (80 more), the floor `Yf` two over the lowest of the nine on the side facing the origin
  (where the way in comes out; at least sea + 1), 40 % of the 81 at `c` or more over it; then the shape is made once
  (siting needs it: no square or no way in, no start) and handed to the factory through a thread-local (made once per
  start). **The world's height:** a start whose box comes within 6 of the level's lowest block or 8 of its highest is
  refused, never clipped (`withinHeight`; the level's own numbers, so a data pack's height is followed). On the real
  noise a start's box runs from the floor - 20 (at least sea + 1 - 20) to the tallest cell + 4 (jagged peaks: about 257).
- **Shape** (`HollowShape`), from the start and the terrain only: the land's top on a 4-block grid (the noise's cell
  width) within 120 of the middle (about 2 800 samples; an 8-block grid left real cliffs under 3 blocks of rock); each
  grid point's ceiling `d` under its top (up to 3 more where the land is steep: the real noise dips between points)
  and under every top within `e` + 2 by the side shell's share; a dome closes it 108 out (rising 1.6 a block inward);
  the height cut in terraces (6, then every 5): the ceiling is the mountain's own shape from under it, stepped. Only the
  part joined (eight ways) to the square's grid point is taken. The square (15 x 15 coarse dirt, podzol, rooted dirt)
  under the tallest part (the most height a grid step round it, at least 21, within 72 of the middle); its stems 6 to 13
  tall (the plan's 5 to 11 looked like posts under a 100-high ceiling), a third pale (stripped: no bark without light),
  0 to 3 bare limbs, a few dead bushes. **Columns:** every `f` 21 (the plan's 14 made a column forest that hid the
  scale, gallery round 1), 3 wide under 32, 5 under 64, else 7, each widening into the ceiling in three steps with a step
  at its foot; none within 15 of the square (a clearing) nor in **the nave**: the two rows either side of the square's
  line from the square to the way in, so the way in opens on one long empty floor with the square at its end (round 3).
  Stubs 1 to 4 long hang from 1 ceiling cell in 70. **The way in:** a 2 x 2 tunnel at `Yf + 1` from the inside's edge
  on the square's own line toward the facing (else the shortest of the lanes between the column rows, 21 apart) through
  the rock while 3 of it stand over the opening, at most 48 long, then a 2-wide cut with vertical sides (at most 24, the
  land's own walls; the plan's 4-wide cut read as a player's mine) and steps a block down each where the land falls
  further (at most 8): never a drop over 1 out of it; dry (no water over the land from the mouth to past the last step);
  no sand or gravel over or beside it. **The high opening** (`i` 0.5): not a 2 x 2 through a cliff (gallery rounds 3
  and 4: a level hole through 7 blocks of rock shows no sky from the floor and lets in a faint glow; a 45 degree shaft
  showed a 12-pixel sky), but a 3 x 3 shaft straight up through the shell from the top of a tall part of the inside
  (40 or more over the floor) where the side falls (the steepest up to 12 over 4 first; 40 candidates checked against
  the terrain: 3 to 16 of rock over every column), every column open to the floor: daylight falls undimmed onto one
  square of the floor far below and the sky (the noon sun) shows overhead; a pool under it (water 2 deep, up to 5 x 5,
  at least the 3 x 3 under it, lined) catches whoever steps into the hole from the mountain (fair danger 7); from
  outside, a black notch in the side. No start fails for want of one. **Seal** (stone, deepslate under 0) round the
  inside and the tunnel but its last three cells (the seal would fill the air in front of the mouth), before anything
  opens. The place: anchor the square's middle a cell over the floor, entrance the tunnel's outermost cell, extra `y`
  (the floor), `c` (the height on an 8 x 8 grid over the box, 32 x 32 unsigned bytes, a little generous at the edges)
  and `m` (the inner mouth): the interior after a restart and 18b's mouth need no terrain.
- **18b** (`HollowAnswers`): as 16b with `j` 120 s, `k` 24 from the inner mouth, `l` 2 a place ever, health 10, a
  pickaxe on everyone inside, 60 s to be done out of view, a refused ask asked again after 30 s; nobody but a spectator
  within 16 of the inner mouth **or the outer one** (a friend outside at the mouth holds it open); four blocks of the
  inside's wall beside the inner mouth (2 to 6 either side, the lowest three cells, the first block a wooden pickaxe
  digs going out from the inside's air) moved into its 2 x 2 (`world:s18/close`). `/a1016 sc 18` (distance, bearing,
  mouth, floor, height, opening wanted, visited, 18b today and ever, your visit and why 18b waits), `sc 18 arm`.
- **Exhibits** (`Sc18Gallery`): `18`, `18w` in a mountain of the gallery's own (a massif 104 high with a second peak,
  a shoulder and a far ridge, ridges and gullies, a sheer side to a bench; stone with granite, diorite and andesite
  blobs, gravel and dirt pockets, coal, iron and copper; grass and spruce low, bare stone and gravel runs higher, snow on
  top; oak, birch and spruce round it, none in the far cameras' lines); the scale figure at the mouth; zombies,
  skeletons and creepers standing about inside (no spider or enderman: glowing eyes). Views: the standard set;
  `survey_1` (down the nave from the tunnel's end), `survey_2` (the square), `survey_3` (high behind the square),
  `survey_4` (up into the terraces); the true-light `in_1` (a torch set down at the tunnel's end, a zombie at the edge of
  its light) and `in_2` (the square by a torch) are one-frame time-lapses (`in_1/f01.png`) so the torch is set only
  after the outside views; `18w` adds `in_3`, `survey_5` (the daylit pool from 28 off), `in_4` (straight up the shaft)
  and `far_2`, `far_3` (the hole from over the mountain's shoulder); `18` adds `section` (a second one 480 north, cut
  along the way in). The placer logs, at INFO, any inside cell daylight reaches away from the mouth and the shaft (none).
  Seven gallery rounds.
- **Tests** (`SelfCreated2HollowTests`, own spots 233 000 to 247 000 on x, -233 000 to -241 000 on z): the siting (the
  gates, the biome, flat land, the floor and the facing, the variant, the factory's shape, the sea, the world's bottom
  and top and their margins, a short 0 to 255 world, the biomes, the set); the shape on a mountain built in the world
  with a cave, gravel, water and lava planted (taken, no floor over 1 from its neighbours, the seal, a search through
  every open cell from the square that never reaches the open but by the way in, the square and its bare stems, the
  columns and the empty nave, the way in and the way out, the inside and the index from the extra); the opening (the
  shaft open, the pool 2 deep, daylight 15 on it, the shell whole otherwise); worldgen equals `Live.place` and is
  recorded once, the piece's NBT gives back its start (env `world/s18`); 18b alone (time, tool, health, distance, view,
  ledger, undo, the day cap, two a place); 18b with two (a friend at the outer mouth, a friend inside without a pickaxe,
  both closed in); the real overworld noise (seed -4180345853331630785, near its stony and jagged peaks): 3 starts in 41
  chunks, the rock over the inside at least 3 everywhere and under 5 in 1 column of 8 253 (a pit the noise's caves open
  in the land), 3 to a steep side; and the cost (only with `-Da1016_02.s18cost=true`).
- **Cost** (this Mac, other workers building at the same time): a start 0.5 to 0.8 s once on a worldgen thread (the 81
  samples and the shape's 2 800: about 150 microseconds a sample here), never on the server thread (the interior,
  18b and the command read the record); per chunk on worldgen's own path over the real noise's rock, the 7 x 7 chunks of
  the tallest part, median 3.8 ms, worst 15.0 ms (in a live test level, where every set also relights; a proto-chunk
  does not).

### SC2-6 `sc2/entity` (wave 2)
- **Implements.** 27 (card, spot, payloads, the client copy), 31 (the apex variant and card), 31c; `sc27`, `sc31`;
  commands; exhibits `27`, `31`.
- **Tests.** 27's and 31's lists (server side), multiplayer included (the viewer-only payload; the friend's leave ends
  it; a spectator within 48 refuses; host and guest share the code path: no host check anywhere).
- **Gallery.** `27` `night_far`, `fog`, `walk`; `31` `apex_dusk` on a `16` exhibit (merged in wave 1), `trunks` in an
  `18` (SC2-5 runs in the same wave: if it is not merged yet, SC2-9's full run takes that shot and SC2-6 stages the
  trunks on a gallery hill). The copy must read as a real player at a distance, the name tag clear. **Done.** Green; the
  copy verified in the gallery; a LAN note for the verifier.

#### As built (SC2-6, `entity.SelfCreated2EntityTests`, 19 tests)
- **27 on the server** (`entity.sc2.s27`): `S27` (registrations), `Sc27Config` (`sc27` v1, keys `a` to `i` as planned),
  `Sc27Rules` (pure: away, still away, the hour, the fit, the caps, the band), `Sc27` (the showings by viewer UUID, in
  memory; `Hooks` for tests; fit, fire, end, the debug arm), `Sc27Spot` (the spot and the way off), `Walk` (one walker over
  any `BlockGetter`: the server tries ways with it, the client walks the copy with it), `Sight` (the line of sight both sides
  use: visual shapes, so leaves and anything solid hide, glass, grass and flowers do not), `CopyRules` (the copy's client
  rules, pure, tested here), `Sc27Data` (`data/a1016_02/entity_sc2.dat`: each viewer's last day, the world's count; lenient),
  `Sc27Card`, `Sc27Payload` (`entity/sc27`), `Sc27EndPayload` (`entity/sc27_end`), `Sc27Commands`, `Sc27Gallery`.
- **Fit** (`sc_27`, MINOR, Traces, FOG and UNSHARED, WATCHER, no fake): the common gates; the viewer alive, awake, playing,
  none of theirs on; sky light at the eyes 10+; night (13000 to 23000), dusk (11800 to 14000) or a fog spell that counts;
  `SoundCues.alone(viewer, 48)` (spectators and the dead count); another participant alive and away: another dimension,
  `a` 160+ off (3D), or no sky at their eyes (under 4) with `b` 12+ to the surface over them **and 72+ off**: vanilla draws a
  player's name within 64 of the camera through blocks, so a friend under the ground nearer than that would show their real
  name through the rock beside the copy's (the plan's 48 for this case is 72); caps `h` 3 days a viewer, `i` 4 a world,
  counted when it is sent (the arm's rule, D-091).
- **The band and the name.** Vanilla draws a name within 64 of the camera, and readable only nearer: the copy's feet stand
  within 56 of the viewer's eyes (`Sc27Rules.NAME_REACH`). Without a fog that limits it, the far 16 of `c` 36 to the nearer
  of `d` 64 and 56 (so 40 to 56); with one, 0.6 to 0.8 of the drawn fog end (the client's report through `FogEdge`), none
  under a fog end of 47.5.
- **The spot**: 40 samples 40 to 80 degrees off the viewer's facing (the client puts it there only off the screen),
  `SpotFinder.standAt` ground, open sky over it (sky light 15: never under a canopy), within the name's reach, head and
  chest in plain sight from the eyes. Scored: a trunk 2 to 6 off (the edge of the trees) 1, above the viewer up to 0.4, no
  block light 0.3. **The way off**: straight away from the viewer or turned 25, 50 or 75 degrees either side, whichever
  hides a walker soonest once past the name's range (out of plain sight, or in 0.95 of the fog end), tried with the walk
  itself over at most 44 steps; it never loads a chunk.
- **The payload** (viewer only): key, the other's UUID, spot, yaw (facing the viewer), the way off, life `e` 25 s, look `f`
  3 s, near `g` 28, and, beyond the plan, what they wear as they are: main hand, off hand and the four armour slots, their
  model customisation bits (hat layer, jacket, sleeves...) and main arm.
- **The end** (every 10 ticks, and at once on `PARTICIPATION` false of either): the story or the switch ends them all;
  the viewer gone, dead, asleep, spectating or in another level; the other gone or spectating; or, when they were away at
  the fire, no longer away by a looser rule (another dimension, 100+ off, or still under the ground by half the depth and
  68+ off) or in the viewer's view (`TraceService.sees`, the viewer alone). Forgotten 120 s past its life. The debug arm
  (`sc 27 arm [player]`): every 20 ticks for 10 minutes the fit (caps aside), then `Director.fire` (forced); the op is told
  when it fires or runs out. Nothing asks who hosts.
- **The copy** (`entity.client.sc2.s27`): `FriendCopy`, a `RemotePlayer` on the viewer's client only, a negative id, a UUID
  of its own (the real friend may be tracked too), its `getPlayerInfo` the friend's entry in this client's own player list:
  skin, cape, slim or wide model, name, team colours and below-name score are vanilla's own; always drawn as a player at play
  (never a spectator's head); not pickable, pushable, attackable or hittable, pushes nothing. `S27Client` puts it into the
  `ClientLevel` (`addEntity`) once its spot is 0.1 of the frame off the screen and the skin had 12 ticks to load (it gives up
  after 12 s). `Showing` moves it before vanilla's own tick (walk animation, body turn, cape: vanilla's). Standing: the body
  still, the head following the viewer (4 degrees a tick, a shoulder's turn either way). Seen: on the screen and either its
  name (within 64: through anything, as vanilla) or its body (plain sight, the frame's fog over it under 0.92, not lost in the
  dark: past 64, light 4 or less, 14 px or less). Looked at: its chest in the middle third and seen, counted in all. At `f`,
  at the end of its life, at the server's end, or with the viewer within 28 while seen: a pause of 7 to 16 ticks (2 near), a
  quick turn (32 degrees a tick, the body after the head), a walk at 4.317 a second (a jump up a block, down 3 at most),
  gone the first tick unseen. Chased (within 28 and closing): a sprint, sprint-jumps within 18; caught up with within 6: gone
  even in view, as a desync ghost goes (never touched or walked through). The friend's entry leaving the player list takes it
  at once (vanilla's look of a disconnect). The client's own switch off ends it.
- **Dev gallery**: `Sc27.stage` sends a `staged` payload (the viewer's own profile as "Friend", at once); a client outside a
  development environment ignores it. The gallery hides the HUD, which hides every name (vanilla's F1):
  `entity.mixin.client.Sc27StandInNameMixin` keeps the stand-in's name, and only its (dev only).
- **31** (`entity.sc2.s31`): `Variant.APEX` (`s31`: GIVEN, STARE_LEAVE, WALK, fake ZOMBIE, never goes under) and
  `Variant.INSIDE_TRUNKS` (`s31c`: GIVEN, HIDE, head tracking, Proximity, eyes FLAT), neither a `sighting_*` card;
  `FigureApi.spawnGiven` (one him, the spawn rule, `appearFor`, the sighting recorded as every sighting) and
  `FigureApi.behindGivenTrunk` (the trunk's shadow edge, `standNear`, `halfVisible`; works under a ceiling). `Sc31Card`
  makes `sc_31` and `sc_31c` with the plan's shapes; `Sc31Config` (`sc31` v1, `a` to `c` as planned). `Apex`: the common
  gates, the stage, busy, one him, a sharer's combat, the base, one a day, not the same variant twice, 300 from the last, a
  dusk sky (rain too, as the ridge), not indoors; the nearest known top `a` 96 to `b` 256 off whose block gives no light,
  sturdy, two cells of air over it, entity-ticking 16 in, head and chest in plain sight from the player, out of everyone's
  view and 12 from everyone. The fake: the nearest husk (any time) or zombie (only while the sun does not burn it) within 96
  of the top, held, moved onto it out of view, facing and bound to the player. `InsideTrunks`: the same gates but daylight and
  indoors; the player inside a HOLLOW place within `c` 40 of its anchor; its trunks (log columns 2+ tall on a floor within 10
  and 4 up or down of the anchor) 18 to 40 off (entity's trunk band), half of him behind one, ticking, hidden, **raw light at
  his eyes 3 to 7** (never the full dark, where nobody would see him; never by a torch), the light's edge (5 to 7) and the
  band's middle scored best.
- **Commands**: `/a1016 sc 27 [arm [player] | reset | clear]`; `/a1016 sc 31 [fake | here | b | c [here]]` (`sc 31` and
  `sc 31 c` fire through the director, forced; `b` says 31b is 17b's).
- **Exhibits**: `27` (`night_far` 48 off, `night_near` 40, `dusk`, `fog` at 0.65 of the drawn fog end, `walk`, `near_day`
  a check in full light) and `31` (`apex_dusk` 180, `apex_mid` 110, `apex_near` 96 on a `16`; `trunks` and `trunks_survey`
  in a dome carved in a gallery hill: the hollow was not merged).
- **Outside the owned files**: `entity/SightingRuleGameTests.java` (one assertion: the cards are every variant whose spot
  is not given), `director/SelfCreatedPacingTests.java` (its design-row test checks the first wave's cards, `sc_01` to
  `sc_15`; the second wave's are `SelfCreated2PacingTests`', SC2-9; the first card of the second wave tripped it) and one
  line of `a1016_02.entity.mixins.json`.
- **Open**: 31 stands only where entities tick, so with simulation distance 12 its top is at most about 176 off (the plan's
  256 needs a larger one); at 180 at dusk the mod's dusk haze leaves him a few pixels, at 96 to 110 he reads. 31c's shot is
  staged until SC2-5's hollow is merged (SC2-9's full run). 02, armed at the same time, may leave the copy's name undrawn
  when it stands still in the fog band (02 treats it as any player); rare, as 02 needs the friend within 64 and 27 far.
- **LAN note for SC2-V** (item 3 below; host A, guest B, then swapped): A in a mine 12+ under the surface and 72+ from B (or
  160+ on the surface, or in the Nether); B outdoors at night, alone; `sc 27 arm B` (the line says why it does not fit yet).
  B: A's copy 40 to 56 off, 40 to 80 degrees off B's facing (turn to find it): A's skin, name, held item and armour; the
  locator bar's dot for A stays at A's real bearing; A sees nothing and gets no packet. B looks at it 3 s: it pauses, turns,
  walks off, gone once out of sight. Again, B runs at it: it sprints off; within 6 it is gone. Again, A walks within 100 of B
  (or climbs out of the mine within 68): B's copy walks off. Again, A quits: B's copy is gone at once with "A left the
  game". Again, `/a1016 sc off`: B's copy walks off.

### SC2-7 `sc2/mp` (wave 2)
- **Implements.** 28, 29, 30; configs; commands; exhibit `30`.
- **Tests.** Each feature's list; every one has a two- or three-player test (28 needs two bases; 29 the friend's own burst
  and the bearing; 30 a cloned map). **Done.** Green.

#### As built (SC2-7, `world.SelfCreated2BorrowedTests` 13, `atmosphere.SelfCreated2NotYouTests` 5, `lore.SelfCreated2MapTests` 10)
- **28** (`world.sc2.s28`: `S28` init, tick and commands, `S28Card`, `Moves` the rules, `Visitors` the tracker, `S28Data`
  `world_sc28.dat`, `S28Env` the seam, `Sc28Config`). `sc_28` as the table (MINOR, Traces, ITEM, COLLECTOR + VISITOR,
  fake). The tracker: every 5 s each participant within `b` (horizontal) of another known player's base (`world.Bases`,
  offline players' too) visited it, kept by UUID (forgotten after 16 days). A pair A (gives) and B (gets): both
  participants with a base in the overworld, the bases at least `2b` apart (one shared house is one base: nothing
  between them), B came by A's within `a` days, not from the base the last real move came from (the turns), each `d` off
  their base, asleep or in another dimension. Containers: chests (copper and trapped too) and barrels a player placed
  within `b` of the base, loaded (the base's whole area, else none: nothing loads a chunk), a double chest one container,
  never a known player's first chest or a protected one; A needs two or more and never gives from the one worth the most
  (`Moves.worth`: gear, enchanted, rare and precious things far over stone). The stack: food, a full building block
  without a block entity (not TNT), raw ores, ingots, nuggets, coal, redstone, lapis, quartz, amethyst, flint, clay;
  common, stackable, at most `g`; never a tool, weapon, armor, named, enchanted, written, marked (custom data: the
  story's items), a container item, a map, golden apples or precious blocks. Nobody (spectators included) within `c` of
  either container but its owner asleep; both out of view (the plan's own check, then `TraceService.moveStack`'s);
  ledgered MOVE_STACK `world:sc28`; Ending D's undo and the switch (`WorldSc.giveBackAll`, tried every 600 ticks while
  off and something waits) give it back; a stack taken since stays the player's (BLOCKED). The fake (forced or the
  director's): one stack between two of the target's own containers, same rules. Caps `e` and `f` count fakes; forced
  skips the visit, the turns and the caps, never the stack, containers, distances or view. `contextFits` plans for real
  (kept 100 ticks a player). `/a1016 sc 28 [player] | force [player] | fake [player] | reset`.
- **29** (`atmosphere.sc2.s29`: `S29`, `S29Card`, `Sc29Config`). `sc_29` as the table. The target alone (`SoundCues.alone`
  48, spectators and the dead counted), underground (`WorldScan.underground`) or at night (13000 to 23000), no combat 30
  s; another participant in the same level `e` to `f` away (3D) whose newest burst (`recentBursts`) is all mined rock or
  earth (`EchoMiningCard.mined`) and at most `a` minutes old. The spot: rock buried on six sides, never placed, loaded,
  `b` to `c` from the target's feet, 14 below to 2 above, at least `d` degrees from the friend's bearing, out of view,
  and heard (at the louder of echo mining's two volumes) by nobody but the target; never within 6 of the level's floor
  nor at its top (`getMinY`/`getMaxY`, SC2-H). The replay is echo mining's player (`EchoMiningCard.digTicks`, `hits`):
  the friend's blocks, hits and gaps, `SoundCues.playAt` through `SoundOut`, dropped once the target is gone. Forced skips
  the dark, combat and the run's age (the newest), never being alone, the distances, bearing or view. Nothing to give
  back. `/a1016 sc 29 [player] | force [player]`.
- **30** (`lore.sc2.s30`: `S30`, `S30Card`, `Maps` the rules, `MapPixels`, `S30Data` `lore_sc30.dat`, `KnownPlaces`,
  `S30Gallery`, `Sc30Config`). `sc_30` as the table. The target in the overworld, `c` from their base
  (`PlayerWatch.base`), carries a filled map of the overworld, not locked, holding the base with it drawn; nobody holds
  that map id in a hand, a cartography table or a frame within 64 of them (never changed in sight). Form 1: a square of
  `a` to `b` pixels round the base blank (byte 0) with vanilla's broken edge one pixel round it; vanilla draws it back as
  the holder walks home with it in hand (tested: 30's colours equal `MapItem.update`'s pixel for pixel). Form 2, from `d`
  (half the time when both can): a known place the map holds, never visited by the target, at least 0.6 of its pixels
  blank, is drawn in from the world as vanilla draws it (`MapPixels.colour`: loaded chunks only), a disc round its box
  with vanilla's broken edge, only over blank pixels; what is not loaded waits, a pass every 200 ticks while anyone loads
  it, for 3 days. Each map takes each form once; every pixel kept with what it held; the switch writes back each one
  vanilla has not drawn since (a map in sight waits, tried every 600 ticks while off). A copy (one id) shares the change,
  another map of the same land does not; a late joiner gets the changed map in vanilla's first packet. Not ledgered (map
  data is no block or stack) and not given back by Ending D. `/a1016 sc 30 [player] | force [player] | b [player] | reset`.
- **Heights (SC2-H).** 28 picks no position (it moves stacks between containers that exist); 29's spot keeps 6 above the
  level's floor and under its top (tests at both ends); 30 reads columns from the chunk's heightmap down to the level's
  floor as vanilla does (tests: a block at the build limit, a column emptied to the floor).
- **Multiplayer.** No host check anywhere (a source test); nothing client-side (vanilla sends map data). Leaves: 28 needs
  both online (a visit is kept by UUID for a late join), 29's replay ends when its target goes, 30 is instant. Other
  dimensions: 28 counts a friend there as away, 29 needs the same level, 30 the overworld.
- **Gallery `30`** (three rounds): a staged home on the deep flat (cabin, path, field, pond and stream, a hill, a wood) and
  a real `s16` 250 off; two maps drawn from it in frames on boards (`near`, `near_2`, `mid`, `aerial`; `f_1` form 1,
  `f_2` form 2) and, the shots that matter, each map as held on its paper (`plan_held_1`/`_1b`, `plan_held_2`/`_2b`):
  the cabin becomes a paper square with a dotted edge, the path running into nothing; a second explored disc with the red
  stepped pyramid in its dead ring where nobody went. In a frame a blank pixel shows the frame's leather (vanilla).
- **Outside the owned files.** `atmosphere/card/EchoMiningCard.java` (`HIT_EVERY`, `mined`, `hits`, `digTicks` made
  public, for 29's replay); `director/SelfCreatedPacingTests.java` (three rows); the test environment
  `atmosphere/sc2_notyou` (29's rock); `lore.sc2.s30.S30Gallery` reads `world.sc2.kit.Live`, `world.sc2.s16` and
  `debug.gallery` (development only).

### SC2-8 `sc2/line` (wave 2)
- **Implements.** 19, 20, 20b; configs; commands; exhibits `19`, `20`.
- **Tests.** 19's and 20's lists. **Gallery.** Every view listed. **Done.** As SC2-1.

#### As built (SC2-8, `world.SelfCreated2LineTests`, 15 tests)
- **19, code.** `world.sc2.s19`: `S19` (feature type and placed feature `world/s19`, added to every overworld biome at
  `top_layer_modification` through Fabric's biome modifications; the kind `s19`, which no structure JSON names: it is
  registered so worldgen reads `Sc19Config` from the kit's snapshot, its recorded segments get their inside, and `Live`
  builds it), `Sc19Config` (`sc19` v1, `a` to `g` as planned), `LinePlan` (pure: the four segments from the world's seed
  and worldgen salt, and each one's layout), `LineShape`, `LineFeature`, `LineCommands`, `LineGallery`. Data:
  `worldgen/feature/world/s19.json`, `worldgen/placed_feature/world/s19.json`.
- **19, as the gallery decided.** The plan's plain plane read at 200 as an untextured slab, a render glitch, not a built
  thing. It is laid out on the world's own grid: a rib on every chunk edge along it (three thick, five on every multiple
  of 128), a one-thick obsidian panel between each two; it begins and ends on a rib, and its last 2 to 4 bays at each end
  are ribs alone (the panels gone: from far the end dissolves into lines into the sky). The ribs are obsidian up to 20
  over the ground under the plane and crying obsidian above: from far a ruled grid, at night faint lines that survive the
  haze, but black within reach and never lit in a cave. Openings two by two in the middle of every `f / 16`th bay
  (others: every `f`, never across a chunk edge or on a rib); the cut, `g` wide, centred on a rib in the middle half of
  the panels, so it goes through the grid. The ground at the panels' foot dies two out on both sides (grass, podzol,
  mycelium, dirt to coarse dirt, which grass never takes back; the plants on it gone).
- **19, heights and terrain.** From the level's lowest block + 6 to its highest - 6 (read from the level: a datapack's
  height moves it, never clips it); a level with under 64 between gets none. Where the land lies under its bottom (a flat
  world's ground at its fourth layer) it reaches down to stand on it, through air and water only, never below the
  lowest block + 1. It goes through everything to the sky: mountains, oceans to their floor, caves, aquifers, lava. An
  opening's lower cell is one over the highest ground of its six columns, or the water's top in a wet column (that cell
  water). Later worldgen: the feature runs last in each chunk and builds every segment cell in the chunk being decorated
  and in each neighbour not finished yet (a neighbour's later lake, geode, dungeon or tree that wrote into the plane is
  put back by the last pass round it); a finished chunk is never touched.
- **19, the rest.** Gates: a snapshot, the switch, the stage at least `a`, the overworld (land made before then has
  missing stretches). One `LINE` place per segment, offered once a run (the kit's data keeps one per start): its box the
  plane (one block thick), anchor the cut's middle at its ground, entrance the opening nearest the origin, facing along
  the axis toward the origin (D-095); its inside the plane (`Interior.box(place.box())`: one is in it only in an opening
  or the cut). The start's variant keeps the length (low 16 bits) and the height. `/a1016 sc 19`: the four segments'
  ends, cut, openings, whether each is known and whether new land gets them now.
- **20, code.** `world.sc2.s20`: `S20` (kind `s20`, `FLOOR`, interiors from the shape), `Sc20Config` (`sc20` v1, `a` to
  `e`), `FloorSiting`, `FloorShape`, `FloorAnswers` (20b), `FloorCommands` (`sc 20`; `sc 20 arm`: 20b now where you stand,
  caps lifted), `FloorGallery`. Data: structure `world/s20` (step `top_layer_modification`, `creature` override cows, count
  4, over the full box), structure set `world/s20` (random spread 56 / 28, salt 2016220, villages excluded within 6),
  biome tag `world/s20` (plains, sunflower plains, forest, flower forest, birch forest, meadow, savanna, savanna plateau,
  taiga).
- **20, as the gallery decided.** **The side is 96 by default, not 64** (`c`, whole chunks 32 to 128): 64 read small from
  200. The floor is grass on two dirt on one course of bedrock (the first flat world's cross-section: where a side is
  built up, a dark line of bedrock runs along it three under the top); under it earth to the ground (its outer face dirt
  with coarse and rooted dirt); everything over it taken to 40 over the highest ground (the trees on it); the cut sides
  sealed two deep outside the square up to the land's top (air and fluid under the ground, water over it: dirt near the
  top, stone deeper), and the leaves over the land there taken (a tree the cut went through leaves no crown floating
  over the cliff). The building as planned (a cobblestone ring and course, a mossy cobblestone floor, oak plank walls
  three high, one pane a side at eye height, a flat plank roof, a 1 x 2 doorway with no door toward the origin, two wall
  torches on the back wall). Its inside: the air over the square, 16 high.
- **20, siting and heights.** The square's middle is the candidate chunk's corner; 25 ground and 25 surface samples on the
  shape's own grid; the relief an eighth to a half of the side (12 to 48 for 96); at most one wet sample, never the
  middle; the floor at the median. Refused, never clipped, if the box (16 under the lower of the lowest ground and the
  bedrock course's footing, 40 over the highest ground) comes within 6 of the level's lowest or highest block. The kit's
  gates first (`a`, `b` 900 from the origin and the spawn).
- **20b.** Visits from `REMAINS_ENTERED` / `LEFT`; a look once a second while someone is on a square; a player within `d`
  of the building's middle asks (at most every 10 s a place) through `Answers` (`20b`, cause `world:s20/face`, no block
  edit so nothing waits on the view rule, one a player a day counted for everyone inside, any number a place a day,
  quiet and first hour, 30 s); every free animal on the floor (within 3 over it; not tamed, leashed, riding, ridden or
  held already) frozen, silenced and facing the building's middle for `e` s, bound to the nearest player, let go
  together; the switch lets go at once. **Every animal, not only cows**: a chunk's animals at generation are its biome's
  (vanilla's generation spawn reads no structure override); the override makes every later creature spawn there a cow.
- **Exhibits.** `19` (a 400 segment north to south through a gallery hill, a lake and a wood; judge with
  `-Pgallery.rd=24`): `far_day` (square on), `far_dusk`, `far_night`, `far_fog`, `rise` (from a rise 300 off, the length of
  it), `rise_dusk`, `rise_night`, `mid`, `along`, `near_1` (an end, the bare ribs), `near_1_dusk`, `up`, `near_2` (an
  opening), `near_2_night`, `near_3` (the cut), `near_4` (the hill), `near_5` (the lake), `near_6` (the wood), `aerial`.
  `20` (a 96 square on a hill's flank, doorway west): `aerial`, `far_day` (from a rise 200 off) to `far_fog`, `mid`,
  `near_1` (the built-up side), `near_2` (the cut), `near`, `near_dusk`, `near_night`, `near_fog`, `mouth`, `mouth_night`,
  `in_1`, `in_2`, `near_3` (night, the cows), frames `f_1` (dusk: the cows wander, then turn) and `f_2` (night, turned).
  Six rounds for 19 and four for 20, judged in `build/gallery/<id>/NOTES.txt`.
- **Tests** (`SelfCreated2LineTests`, 15; each in its own patch far out and random for the run, the test world being kept
  between runs): the segments per seed (half-axes, distances, lengths, ribs, bare ends, openings, cut, start); the height
  for four level heights and a short one; a segment built through rock, a cave, lava and a pond (every cell, the
  openings dry and wet, the ribs, the cut, the bare bays, standing on low land, the bedrock, the dead ground); the
  feature over every chunk equal to `Live` and recorded once with its mouth and inside; only its chunks and its axis,
  nothing without a snapshot, switched off, before Traces, under its stage or in the Nether; an opening's height; the
  siting (hills, dry, the median, the facing, both ends of two heights, 81 samples, the kit's gates); the square over a
  slope (flat, its courses, nothing over it, built up with no air under it, straight sides, the cut sealed, the rock past
  the reach left, a tree taken); the building; worldgen equal to `Live` and recorded once, its inside; the cows; 20b on
  every animal on the floor and none off it, facing, let go, refused again that day; two players (a far corner does not
  ask, both counted and told, the switch, the next day, a newcomer in their first hour); and with
  `-Da1016_02.s19cost=true` the cost. **The flat game-test world's noise is not its blocks** (its noise ground is Y 3,
  its blocks' Y -61): the worldgen-equals-`Live` tests build both over the noise.
- **Cost** (this Mac, others building at the same time): 19, a chunk away from every segment 0.0014 ms; a chunk of a
  segment the first time 1.39 ms median, 1.66 worst, again 0.07 (a decoration touches up to three; noise samples, about
  20 a chunk of segment, come on top once a run in real terrain); 20, a 96 square: 64 chunks, 1.58 ms median, 2.13 worst.
- **Outside the owned files**: only the two new test environments `world/s19` and `world/s20`. **Open**: a neighbour
  decorated later can still put a flower or a leaf at a square's edge (as with vanilla's structures); the floor's first
  animals are its biome's.

### SC2-9 `sc2/int` (wave 3)
- Merge in order: SC2-0a, 0b, 0c (wave 0, before wave 1 starts); SC2-4, SC2-3, SC2-1, SC2-2 (wave 1); SC2-8, SC2-5,
  SC2-7, SC2-6 (wave 2); `./gradlew build` after each merge, a red build back to that branch's worker.
- Then: 01+ (`sc01` v4, `Env.placeFactor` with remains, the sims' `simRemainsShare`); `/a1016 sc` index lines for 16 to
  31 (counts, "armed", time left; never names: `SelfCreatedDebugTests`' word list holds), overlay rows (each player's
  arm or scene for 21 to 31, the place they are inside as its number); `SelfCreated2PacingTests` (every card registered,
  192 seeds x 20 h x 3 tempos, the four cases of the first wave's pacing tests, D-070's first hour, hard limits, rates
  within 15%); `VerifySc2CostTests` (worldgen per chunk per kind and size; idle tick cost); a full
  `./gradlew gallery -Pgallery=all`; "As built" notes per feature in this file; the preview jar (the orchestrator).
- **Done.** Green 3/3; the gallery run complete with its index; the sims within targets (or the new cards' fit chances
  tuned, said in the notes).

#### As built (SC2-9, `director.SelfCreated2PacingTests` 7, `core.VerifySc2CostTests` 5, `debug.SelfCreatedDebugTests` +1)
- **Merges.** Done by the orchestrator, a full build after each (wave 0, 1 and 2 in the planned order); SC2-9 started from
  `self-created` at `ad12a75` and merged it again with SC2-H's audit (`fa8dfd8`) before its last builds.
- **17's frequency** (the orchestrator's call after SC2-H): the dry, flood-safe ways in made an archive about 60 % as
  common as planned; its structure set's random spread is 31 / 14 (from 40 / 18: about x0.77, the frequency going with
  1 / spacing squared, so x1.67), the trial chambers' exclusion, the salt and the siting's distances from the origin and
  the spawn unchanged. The dry runs need nothing for it: `simRemainsShare` is the planned share of play at his places, which
  the planned frequency gives back.
- **01+** (`director.sc`): `Sc01Config` version 4 (`o` remainsFactor 1.5, `p` simRemainsShare 0.03; the step from 3 to 4
  only adds them, opaque from the start). `Places.factor(..., atRemains, cfg)`: at his places the factor is at least
  `remainsFactor`, never past `maxFactor` (a stronger place's dread stands; exactly 1 still reads as `awayFactor`).
  `DirectorImpl.atRemains(level, pos, reach)`: inside a place's interior (`Remains.containing`) or within `radius` (32,
  3D) of a known place's way in, in that level (one indexed lookup and a walk over the level's known places, tens); the
  live factor, `/a1016 sc 01` ("at a place of 16 to 20: at least x1.50") and the index's line 01 use it. `REMAINS_ANSWERED`
  is remembered for that player (`DirectorImpl.onAnswered`, `Places.rememberAnswer`): a minor's place (0.3, no sighting)
  where they stand when it answers (they are inside), else at the place's way in; nothing while the layer is off, the
  story is quiet, or the director keeps no pacing for them. `DirectorSim.Params.places(cfg)` rolls `simRemainsShare` of
  the director ticks at `remainsFactor` (a third band after the dread and trusted shares, on the same dice, so a run
  without it is unchanged). Measured: a dry run always at his places has 1.42 times the Traces minors of one away (0.74 to
  1.05 an hour, slow burn, 48 seeds of 12 h). `VerifyScSpoilerTests`' sections carry a version and their fields newer than
  the opaque step (`sc01`'s `o` and `p`: no old key, read at their defaults from an old file, written under their letter).
- **The index** (`debug.Sc2Index`, called by `ScIndex`): `/a1016 sc` prints 01 to 31. 16 to 20: each kind's known places,
  how many were visited, who is inside now, and each answer's count today and ever by its number (`16b 1/3`); 21, 22, 24:
  the world's count of its cap and the last day, what runs and for whom (phase, mobs, time left), 21's cells waiting to
  be bared; 23: who spent theirs today, what is held and its seconds left; 25, 26: the world's count of its cap, who has
  it armed, what runs (seconds, hostile or not); 27: the count, what shows and the debug arms with minutes left; 28: the
  count, the last day, the pairs met, whether something waits to be given back; 29, 31, 31c: each participant's real
  fires in their pacing's history and the last day (they keep no counts of their own); 30: the count, the maps' kept
  pixels, what waits to be drawn. Words: `SelfCreatedDebugTests` holds the index and the overlay to the first wave's list
  and to D-103's (`VerifyScSpoilerTests.SC2` and `SC2_GENERIC`, now public): no "line", "map", "ring", "pit" ... even
  inside a word. Readers added (read-only): `Follows.Follow.untilClock`, `Processions.Procession.sharers`,
  `Sc27.armTicksLeft`.
- **The overlay**: each participant's row gains the place they are inside as its number (`in 18`) and their scenes and
  arms: `21 round`, `22 walk`, `23 6.5s`, `24 0.62d`, `25 armed` or `25 going 40s`, `27 18s` or `27 armed 8.0m`, `31 out`
  or `31c out` while the figure stands for them (28, 29 and 30 have none: a move, a few seconds of sound, a map changed).
- **Pacing** (`SelfCreated2PacingTests`): every second-wave card has its row (the table above, plus `accident_sc25` and
  `accident_sc26`: MAJOR, Proximity, ACCIDENT, never fake) and a dry-run chance of its own; the dry runs (192 seeds x 20 h
  x 3 tempos, the first wave's four cases over both waves against the deck without the layer) keep D-070's first hour,
  every hard limit, the 4b rates and the first accident within 15%, the second wave never before its stage nor in Alone,
  at most 3 of its cards in an hour of a run and at most 12% of every fire (measured above). The second wave's rows left
  `SelfCreatedPacingTests`' table (its design-row test is the first wave's again).
- **What was tuned** (`director.sc.SimFits`, used by `DirectorSim.Params.from`, so every dry run, `/a1016 director sim`
  and the tests): at the usual dice (0.35 a look for every card) two players together felt 0.92 / 0.93 / 0.95 of one
  player's minors (the bound 0.95; SC2-3 had lowered it to 0.9). Probing (up to 768 seeds a table) found two causes: the
  moments one player has and the friend beside them does not feel (UNSHARED: 27, 30, the follower 24), which hold the
  friend's minors back by the target's own minor gap and are not felt by the friend (2% a look of 27 and 30 cost the
  pair 1 to 4% over 576 seeds, up to 8% on the 192); and shared moments that rarely fit, which hold both players' decks
  (a pair's cycles fill twice as fast and stall on cards held and parked: shared cards at 2 to 5% a look cost up to 3%).
  Lowering the new cards' chances
  alone made it worse. So the dry runs model the moments as their gates have them: `Params.needsAlone` (27 and the two
  hunts never fit beside a sharer: their own alone-within-48 rule, the shared radius), `Params.needsCompany` (27, 28,
  29 never fit in a one-player run) and each card's chance a look (`CHANCES`: 21, 22, 23, 28 at 0.15, about four draws in
  five; 24 at 0.07; 31 and 31c at 0.05; the hunts at 0.05 and 0.025; 30 at 0.002). The pair then feels 0.96 / 0.98 / 0.97
  (0.96 to 0.98 over 576 seeds) and `DirectorMultiplayerTests`' bound is 0.95 again; the fog hours (`DirectorV12Tests`,
  at least twice a clear hour) stay at 2.03 / 2.11 / 2.17 (rarer cards dilute the fog's boost: 1.97 early with them
  rarer). No real gate changed, nor any director knob.
- **Cost** (`VerifySc2CostTests`, own environment `verify/sc2_cost`, own patches at x 7.7 million, z -7.7 million; this
  Mac with other builds running; two runs): idle, every server tick hook of the wave once a tick for 1200 ticks with two
  real players taking part: 5.8 to 6.1 microseconds a tick (median 2.6 to 2.8; the kit's visits and answers 2.1 to 2.3,
  17's rules 1.6, 28's visitors 0.9, each other at most 0.5; budget 20); the visits' look over 200 known places with nine
  players: 140 to 220 ns a participant (a look every 20 ticks); a ring of 14 steered, its scene 5.8 microseconds a tick;
  a held breath of 40: gathered in 0.4 to 0.5 ms, held in one tick in 0.3 ms, 0.3 microseconds a tick while held; a pack
  of five stalking a stand-in (stepped by the test as the hunts' tick steps it), 9 to 11 microseconds a tick (91 to 111 a
  step every 10 ticks); 27's payload 89 bytes with a full hand and armour, about a microsecond an encode. Worldgen per
  chunk and kind is SC2-H's (its own test class).
- **The far patches' waits** ("the patch is not loaded yet" at tick 202, under load): the test server's ticks run as
  fast as they can, so a wait counted in ticks raced the chunk system. Every far patch now waits in two steps, both in real
  time: SC2-3's own (SC2-H gave it to the core tests, `Away` and the hunts' field): the server's pending work run until
  the chunks are entity-ticking by their tickets, at most 30 s; then `core.FarPatches.until` for the entities, which
  `SelfCreated2InstrumentTests` (it had the first step and still failed at tick 202) showed still raced the entity storage:
  20 ticks free (a patch is usually ready in a few), then each tick still waiting lets the server run its own pending work
  for 50 ms growing to a second, so the storage on its threads catches up while the test spends few of its ticks; at most
  120 s. Used by `SelfCreated2CoreTests`, `SelfCreated2InstrumentTests`, `SelfCreated2HuntTests`, `Away`,
  `SelfCreated2LineTests`' floor rig and `VerifySc2CostTests`; a ready patch costs nothing.
- **Gallery** (`./gradlew gallery -Pgallery=all`, render distance 16, 43 minutes on this Mac; run again in full after
  SC2-H's merge, whose kit and shapes changed what 16 and 17 build): all 22 exhibits (00, 16,
  16d, 16l, 16s, 16x, 17, 17l, 17n, 18, 18w, 19, 20, 21 to 27, 30, 31), 815 files in `build/gallery/index.txt` (199
  views, 462 time-lapse frames, 154 blueprint renders; 30's four held maps beside them, its own), none flagged `BLACK?`,
  `FLAT?` or `CHUNKS?` (the night shots are dark by design: luma 0.04 to 0.08). 31's trunks were retaken inside a real
  `18` (`Sc18Gallery.eighteen`, 320 south of the `16`; the camera 21 down the nave, a torch beside it and one among the
  square's front stems, so the light gives out among the stems behind: the first run's torch at the square's front left
  no spot of light 3 to 7 behind a stem 18 or more off, and he did not come): half of him behind a bare stem at the edge
  of the light, the nave and the stepped ceiling round it (`31/trunks`, `31/trunks_survey`).
- **Docs**: the "As built" pointer under each feature, the status, the pacing table, this note; `docs/SELF_CREATED.md`
  points to this file where the second wave changes it (its header, 01's section, the index and the pacing).
- **Outside the owned files**: `director/DirectorImpl.java` and `DirectorInit.java` (01+'s factor and the answers'
  listener), the three read-only readers above, `world/sc2/s18/Sc18Gallery.java` (public, `eighteen`: exhibit 18's
  mountain and place for 31's shot), `entity/sc2/s31/Sc31Gallery.java` (the trunks inside an `18`); tests:
  `core/VerifyScSpoilerTests` (the sections' versions and newer fields; `SC2` and `SC2_GENERIC` public),
  `director/SelfCreatedDirectorTests` (`sc01` at version 4), `director/SelfCreatedPacingTests` (the second wave's rows
  out), `director/DirectorMultiplayerTests` (the bound 0.95), `debug/SelfCreatedDebugTests` (01 to 31, the second wave's
  words, the remains lines), the new `core/FarPatches` and its three users, the test environment `verify/sc2_cost`.

## Waves

| Wave | Tasks (parallel) | Who | Depends on |
| --- | --- | --- | --- |
| 0 | SC2-0a core, SC2-0b kit, SC2-0c gallery | 3 workers | this plan |
| 1 | SC2-1 pyramid, SC2-2 archive, SC2-3 instruments, SC2-4 hunts | 4 workers | wave 0 merged |
| 2 | SC2-5 hollow, SC2-6 entity, SC2-7 multiplayer, SC2-8 line and floor | 4 workers | wave 1 merged |
| 3 | SC2-9 integration, full gallery run, preview jar | 1 worker, orchestrator | wave 2 merged |
| 4 | R10 horror critic, R11 art director, R12 bugs, performance and multiplayer, R13 spoilers and guardrails (read-only, in parallel); then FIX-SC2-x per finding group | 4 reviewers, then workers | wave 3 |
| 5 | SC2-V verify | 1 verifier | wave 4 and its fixes |

## Reviews (wave 4)

Read-only, adversarial, on the merged `self-created` after SC2-9, each finding sorted HIGH, medium, low, each backed by
a failing game test, an exact reproduction, or (R10, R11) a named PNG or scene and a concrete change.
- **R10 horror critic.** Reads this file and the first wave's, runs the gallery frames and the dev client's force
  commands, and judges dread against cheapness: does each moment frighten or merely surprise; clichés (jumpscare,
  screamer, chase, custom creature, lightning, glow, fake chat); whether the remains feel like a bigger force with a
  plan (the rays, the doors facing the origin, the held breath's bearing); whether the danger is felt as danger and fair;
  pacing across the first three hours (the sims, the stage table above); whether multiplayer moments make friends doubt
  each other without feeling like a trick. Output: findings, and a ranked list of the three weakest moments with fixes.
- **R11 art director.** Opens every PNG in `build/gallery` (and the blueprint renders), and judges each against the bar
  in "The gallery": silhouette, scale, material, rhythm, light, focal point, the night or fog shot. May run `./gradlew
  gallery -Pgallery=<id>` for more views. Every finding names the PNG path and the change (a size, a material, a cut, a
  light). Anything "basic" is at least medium.
- **R12 bugs, performance and multiplayer.** Worldgen: chunk safety (writes only inside the chunk box, no chunk loads,
  thread safety of the snapshot and recorder), per-chunk cost against D-104 (`VerifySc2CostTests` and its own runs),
  starts saved and reloaded, a world with remains opened by the first wave's preview jar and by vanilla 26.3 (unknown
  starts skipped, nothing lost). Runtime: tick cost idle and busy, the 48-steered cap, scene cleanup on every end path,
  the ledger and the switch's give-backs, Ending D's afterward over every answer. Multiplayer: two to four players,
  sharers, PERSONAL and UNSHARED, the alone rule with spectators, a friend arriving and leaving mid-scene, dimension
  changes, late joiners, host versus guest, a dedicated server (the side test; `runServer` once if Mariano has accepted
  the EULA in `run/eula.txt`), two hunts, two answers at once in one place, a death mid-hunt and its mark.
- **R13 spoilers and guardrails.** D-080/D-103: config keys, command literals and usage, log lines, registry ids,
  `/locate` tab completion, resource paths, the `sc` index and overlay, against the extended word list; guardrails:
  every runtime block edit through `TraceService` (grep `setBlock`/`setBlockAndUpdate` in the new packages outside the
  kit's writer, `Live` and the gallery), every mob change through `MobTamper`, no spawn outside the gallery, `hunt`
  callers, the fair-danger tests present for 16b, 16c, 17b, 18b, 25, 26, no new in-world text but the records' titles.

Then one FIX worker per finding group, in parallel by owned paths, each with the failing test turned green (or, for
R10 and R11, the new PNGs).

#### Fixed in review (FIX-SC2-REST: R12 and R13 outside the world workstream, branch `fix/sc2-rest`)
The orchestrator's decisions on R12 and R13 for core, accident, atmosphere, entity, lore, the layer's logging, the docs
and the side and spoiler tests. FIX-WORLD has world/**; SC2-9 the index lines, the overlay rows, `director/sc` and the
pacing and cost tests.
- **R12-4** (participation off the server thread). Fabric runs DISCONNECT on the network thread for a client that quits,
  crashes or times out. `PlayerWatch.onDisconnect(server, player)` hands such a leave to the server thread's next turn
  (skipped once another object is listed for the player, or the server is no longer running); core's new mixin
  `core.mixin.PlayerListMixin` (head of `PlayerList.remove`, always the server thread, the player still listed) handles it
  there at the latest (`PlayerWatch.onRemoving`), so PARTICIPATION false fires once, on the server thread, with the player
  listed. The live source re-queues any off-thread call. Any DISCONNECT listener that touches what the tick touches runs
  through `PlayerWatch.afterDisconnect(server, player, work)` (atmosphere's `ActiveEffects` and `DeadMountains` do;
  entity's two use concurrent maps).
- **R12-6, R13-13** (deaths a hunt only drove a player into; a friend's death). `PackTrap.claims` and `CallTrap.claims`
  also take a death no entity dealt (a fall, lava, water) within `Hunts.CREDIT_TICKS` (100, vanilla's own memory of who
  last hurt a mob) of a hit by one of their own (`Hunts.credited`), the target's or anyone's who ran in; the list's word
  is then the damage's own ("fell"), a bite keeps the killer's.
- **R12-7** (the call's way out). Its gate asks for a pickaxe or a shovel (`Hunts.digOut`: dig in and cover); two blocks
  stop no arrow or spider, an axe digs no ground.
- **R12-8, R13-13** (the gates while a hunt runs). At the turn `Hunts.turnRefusal` asks the floors again: health 10
  (`TURN_HEALTH`), the kind's own way out, 64 from the base (relaxed for a forced arm, `HuntScene.relaxBase`), and, before
  the first hostile act, no combat in the last 5 s. The pack holds through its 30 s extension and goes ("held"); an
  unprovoked attack start (after the close, or coming down from the siege) asks again and goes. The call goes at once when
  the player is within 64 of their base at any point ("home"), and under a roof at the turn or after ("sheltered"), and
  holds at the turn like the pack.
- **R12-11** (core's part). `TraceEdit` refuses a move, a left block and a restore of a block with a collision shape into a
  cell a living entity stands in, forced or not (torches and plants pass). The planners' own checks are FIX-WORLD's.
- **R12-28** (hunt anger saved). A mob saved while hunting carries the mark `a1016_02:h` (the mob mixin's save hook);
  `MobTamperImpl.onLoad` (ENTITY_LOAD) calms one that loads with it. The class Javadoc says what reaches the disk.
- **R12-29** (fits near big farms). 23 asks a line of sight of at most 48 mobs, 22's view rule at most 64 animals, 21's
  herd search at most 64 of each kind, the nearest.
- **R12-31**: 22 never plans a file of none (a minimum of 0 threw in the server tick).
- **R12-33**: the ring's fake is tracked by `Rings` (`heldFake`) and let go by the switch; 29's replay stops while off.
- **R12-35**: 24's end is over after `Follows.END_HARD_TICKS` (6000) even when someone keeps it in view.
- **R12-36**: `CallTrap.tick` keeps a set window on (200 ticks at a time, at most `KEEP_MAX` 10 minutes after it was set)
  while one of its own let go to vanilla's targeting is still after a player.
- **R12-37**: a pack or call instrument whose steer is refused (the world's 48, something else holds it) is let go at
  once, loud, and is no instrument of the scene.
- **R12-38**: 27's server sends `Sc27EndPayload` when the viewer changes level; the client drops a waiting copy whose level
  changed before it stood.
- **R12-39**: the apex's line of sight is asked only where every chunk on its way is loaded (`Apex.loadedAlong`).
- **R12-40**: the switch keeps a drawn (form 2) pixel where a participant has been since it was drawn (the edit keeps its
  day, `S30Data` key `d`, read leniently); a blanked pixel always goes back.
- **R12-41, R12-42**: the side test flags the mod's own client classes named in common code and knows 26.3's other client
  packages (`com.mojang.renderpearl`, Realms, Fabric's client implementation).
- **R12-43**: `Sc27StandInNameMixin` applies only in a development environment (`entity.EntityMixinPlugin`, the entity
  mixin config's plugin).
- **R12-44** (this scope): the exhibits of 21 to 26, 27, 30 and 31 are built only in a development environment.
- **R13-1**: an `sc` trap kind's clue is never written to the arm line, the history or the log
  (`AccidentPlannerImpl.quietClue`); `/a1016 accident` prints the kind's id in its place.
- **R13-2**: ARCHITECTURE's lines name no wave contract (`MobTamper`'s row and line, the layer's pointer line); the manual
  (docs/README.md) lists its own commands and no longer sends readers there.
- **R13-4**: README's two stanzas are one neutral stanza ("it is the same world. look at it again tomorrow.").
- **R13-5, R13-9** (core's lines). `core.ScLog.warn/error` (SLF4J's format, the throwable last): the caller's neutral
  line and the exception's simple name if it is the JDK's or the game's, else `error`; the trace at DEBUG. Core's listener
  lines are neutral ("sc: a listener failed (f)", "(t)", "s2: ... (v)", "(a)"); the switch's and the file writer's, the
  hunts', 03's, 04's, 12's and Iris's lines go through it. The spoiler test refuses a throwable handed to the logger by any
  WARN or ERROR line of the layer; `THROWN_PENDING` lists the world workstream's six files until FIX-WORLD moves them.
- **R13-6**: 21, 22, 23, 29 and 30 log the id and bare numbers at INFO; 29's spot and 30's map id at DEBUG. (28's line is
  FIX-WORLD's.)
- **R13-7, R12-30** (animals taken from a pen). 22 and 24 ledger each animal they take from where it stood, once per
  animal and scene (`TraceLedger.Kind.MOVE_MOB`, `recordMobMove`, stored apart under `mobs` so v1.3 and older layer jars
  read the file; causes `atmosphere:s22`, `atmosphere:s24`). Its undo puts the mob back where it stood out of view
  (BLOCKED once it is someone's: leashed, ridden, owned, named), so the switch gives it back (`Herds.giveBack`, retried
  every 600 ticks while off) and Ending D's afterward too, as it gives names back.
- **R13-11**: the hunt's follow range modifier is `a1016_02:sc_r`.
- **R13-16**: the spoiler test reads `ScLog`'s lines too, holds the layer's log lines to R13-6's nouns (held, sound, pixel,
  formed, "of them"), and checks the suggestions outside `/a1016 sc` (accident arm, candidates and mark, fire, entity
  spawn); a runtime test arms both hunts and reads every log and history line for their clues.
- **Left, with reasons**: R12-32 (cosmetic, one effect's clear wipes another's on the same mob); R12-34 (22's walk ends
  far beyond loaded chunks: a design change); R12-20's core part (`PlayerWatch.placedNear` reads unloaded cells: base-mod
  semantics for 36 callers; the remains' planners are FIX-WORLD's); R13-12 (D-080 allows plain words to the op); R13-10
  (the gallery's classes stay in the jar, inert: the orchestrator's decision).
- **Tests**: `core.SelfCreated2ReviewCoreTests` (7: R12-4 three, R12-11, R13-5, R13-7 two); `accident.SelfCreated2HuntTests`
  +8 (test environment `accident/sc2_hunts_d`); `atmosphere.SelfCreated2InstrumentTests` +5 (`atmosphere/sc2_c`);
  `entity.SelfCreated2EntityTests` +2; `lore.SelfCreated2MapTests` +1; `core.VerifySc2SideTests` +1;
  `core.VerifyScSpoilerTests` +2; the test helper `core.LogCapture`.

#### Fixed in review (FIX-SC2-PACE: R10-1, R10-2, R10-4 and the pacing SHOULDs, branch `fix/sc2-pace`)
The orchestrator's round-2 decisions on the horror critic's pacing findings (D-107): the wave must be met in the first
sessions, about one of its moments every 1.5 to 2.5 h of play from Proximity, with D-070's first hour, DESIGN 4b's tier
rates and the silences kept. Supersedes SC2-9's pacing (its "As built" above); the section "Pacing and director
integration" holds the rules and the measured tables.
- **Before / after** (192 seeds x 20 h, early / slow burn / very late). The wave a played hour: 0.19 / 0.20 / 0.20 from
  Traces before; now 0.47 / 0.48 / 0.49 from Proximity (0.40 / 0.40 / 0.42 real) and 0.36 / 0.32 / 0.33 in Traces, 14.3
  to 14.5% of every fire (6.2 to 6.4%), never more than two in an hour (three before). The first real moment by
  Proximity + 1.5 h: 13 / 15 / 14% before, 86 / 83 / 82% now. Five-session players: the held breath 8 / 9 / 6% before,
  99 / 95 / 98% now; a hunt 10 / 9 / 7% before, 48 / 55 / 64% now (out far from home on three nights in ten); features
  met 1.4 to 1.6 before, 2.9 to 3.1 now. D-070 and 4b within 15% in the four cases; two players together 0.98 to 1.02 of
  one player's minors.
- **R10-1** (the share and the weights). The group (`ScPacing`, `DirectorRules.groupCards`, `groupShare`, `cardWeights`,
  `groupHabitFloor`, `groupGap`, `groupRepeat`; `DirectorBrain.drawGroup`): from Proximity 30% of the ambient and 75% of
  the minor draws look first at the wave, taking a card only when its moment is there at that look (a held card whose
  moment rarely comes cost its tier five minutes a draw and dragged Proximity's minors to 0.77 of the plain deck in the
  rare-fit case); the weights `q`, a habit floor of 1 (23 and 27 never at 0.4), the same card not within 2 h, the wave's
  moments 30 min apart. In Traces the share is 0: drawn as any card (held for their looks) the wave's Traces cards came
  oftener than at one look each, and a share there pushed a very late world's Traces minors 16% over the plain deck.
  **The caps R10 asked for** (23 `maxPerWorld` 4 and `minDaysApart` 3, 21 `i` 6 to 3) are left to their sections: the
  director's repeat (2 h), gap (30 min) and the wave's own cycle space them for each player now (23 at most every 2 h,
  besides its own once an in-game day), and those sections are FIX-ACTORS' to version. The pacing test asserts the
  experience instead of rarity (`theSecondWaveIsMetInAPlayersFirstSessions`); R10's share bounds (18 to 30% of
  Proximity's fires) became the hour's targets the decisions name. The dry runs model 22 and 31 at their reach (`SimFits`).
- **R10-2** (the first beat). `DirectorRules.beats`, `beatDarkFirst`; `PacingMemory` `beatFrom`, `beatAge`, `beatDone`,
  `beatHeld` (saved under `scp`): 23, then 21, at the first fitting look while the minor tier may fire, dusk or night
  first, never a fake, never held (a beat whose moment never comes held the minor tier for its whole window and dropped
  Proximity's minors to 0.69 of the plain deck in the never-fits case: it is a look now), its windows on active play.
- **R10-4** (the hunts' own clock and the call). `DirectorRules.ownClockCards`, `ownClockFrom`, `ownClockGap`,
  `ownClockPull`; `DirectorBrain.ownClock`, `majorFired`; `Env.called` (live: `Remains.trespassedAt` within one in-game
  day); `CallTrap.dark` and `calls` (the gate's entry point: a called night needs no fog); `sc25` version 2 (`m` 0.5 to
  1.0, keepOrNew). A hunt spending a far major slot pushed a very late world's first trap 21% later; one leaving every
  slot alone made majors 18% oftener: a hunt now takes only a slot due within the hour, at that slot's own time. **Not
  done here:** the first long tell (25) or the first voices (26) from the bearing of the place they were in (the scene's,
  FIX-ACTORS). **Not met:** R10's "the first hunt by 4 h in half the runs" (10 to 26% by 4 h, the median player at 7.5
  to 10 h): the designed silences (quiets and empty sessions, 42 to 44% of Proximity), the major gap and the 5 minutes
  after any fire hold a hunt's night as they hold every major.
- **R10-13** (the days). "What the days should feel like" re-measured.
- **The dry runs** (`SimFits`, `DirectorSim.Params`): the wave's chances are a look's; the hunts dark (`Params.dark`,
  `nightOut`, `calledNightOut`, `needsFog`, `calledChanceOf`, a night's roll then `DARK_LOOK`), their world caps
  (`worldCap`), visits inside his places (`visitsPerHour` from `sc01 p`); FIX-SC2-WORLD's denser places (`fix/sc2-world`:
  four rays at 640 and every 640, archives 20 / 8, hollows 24 / 10, floors 36 / 16) folded in as `sc01 p` 0.03 to 0.06
  (dry runs only, keepOrNew in the step to 5) and 22, 31 and 31c's reach (0.08, 0.05, 0.03 a look).
- **Checks.** `DirectorSim.check`, `DirectorTestSupport.feltHardLimits`, `debug.PlaythroughCheck` (its `Limits` carry the
  own clock): the group's gap, the hunts' clock and gap, every other trap's first-accident time; an own-clock card is
  outside the deck's no-repeat rule. `/a1016 director` prints a layer line per player (the beat, the gap, the own clock;
  ids only).
- **Tests** (`director.SelfCreated2PacingTests`, 14; 1074 green): the design's rows and the pacing's names; the
  guardrails in the four cases; the experience; two players share their first beat and never a hunt together; the
  beat, the group and the own clock on cards of their own; the trespass's call; `sc01` 3 and 4 to 5, `sc25` 1 to 2.
  Updated: `DirectorTestSupport` (the own clock in the hard limits and the first accident), `VerifyScSpoilerTests`
  (`sc01`'s newer fields), `SelfCreatedDirectorTests` (`sc01 p`), `SelfCreated2HuntTests` (the doubling test sets `m`
  0.5 itself), `DebugGameTests` (the limits).
- **Open.** The hunts follow how often a player is out at night far from home (35 to 47% of five-session players at 15%
  of nights, 62 to 76% at half); the dry runs' chances are reasoned, not measured in play. The traps on the accident clock
  come 13 to 21% later (the test holds them within a quarter). The early tempo's first-hunt median sits on its bound (10.0
  h). FIX-ACTORS may raise `sc25`'s version too: the steps then join (1 to 2 here, theirs after).

#### Fixed in review (FIX-SC2-ACTORS: R10 and R11 on the scenes and figures, branch `fix/sc2-actors`)
The orchestrator's round 2 for 21 to 27, 29 and 31: what the instruments, the hunts and the sightings do when they fire.
FIX-PACE has when they fire (weights, fits, guaranteed beats, the hunts' clock and trespass call); FIX-WORLD has world/**.
- **R10-5** (MUST, 25: perceived, not geometric). `Pack`: at night without a fog the band is 16 to 24 (`NIGHT_MIN`,
  `NIGHT_MAX`; it was 28 to 40, a few grey pixels at render distance 12 under the night haze); in a fog that counts 0.55 to
  0.7 of the fog end the player's client draws (`Hunts.fogEnd`: `FogEdge`, as 27), at night never past the night band, by
  day at most 30 (`Pack.band`). The rest hold within 40 degrees of straight behind the player, so one turn shows two or
  three. The **closing**: the last 20 s of the first part (and any hold past it) the band is 12 to 16, each wolf growls once
  (volume 1.5) as it steps in, and the third long tell plays as it begins. The long tell is the three whines and then the
  nearest one's growl at pitch 0.6, as loud (heard to 48). A looked-at growl reaches 32 (volume 2). From 15 s in until one
  was plainly seen, and whenever none was seen for 30 s, one walks round (never across) into the middle of the view 17 to 20
  out (nearer in a fog, inside what is perceived), on a spot against the sky first (`Hunts.skyline`), stands 3 s once
  plainly seen and goes back. A wolf going back to its place round the other side rounds the player at the band, never
  across. Told (`HuntScene.told`, both hunts) now also needs one **plainly seen** (`Hunts.plain`: the middle third of the
  view, within 20 and what is perceived), and "seen" is perceived (`Hunts.perceive`: a mob in the dark, light under 8 at
  its head, counts within 20 at most; in a fog 0.6 of the fog end; else 0.85 of it). The second long tell always comes
  before the closing's. Nothing of the attack changed: the turn still asks the floors again (R12-8).
- **R10-12** (both hunts): the perceived "seen" and the plain sighting above; 26's one walked into the middle of the view
  stands **14 to 16** out (never a spider), from 25 s until it stood there plainly seen, and 26 is told only once it did
  (`Call.told`, `shownThere`: one of the ring 20 off, seen in passing, is not enough).
- **R10-19 and the horde risk** (26): `Call.HARD_MAX` 6 (the trap's `CallTrap.most`: never more, whatever `c` says; the
  scene keeps the first six it is given); `sc26` v2: `c` 8 to 6 (keepOrNew). They stand on the ring at least
  `RING_WAIT` 20 s before the walk-in (counted from when every one is on it, or 40 s in).
- **R10-4, the scene's side** (25, 26; FIX-PACE schedules the call): a hunt armed within one in-game day of a trespass
  (`Hunts.calledFrom`: the nearest of his places in the player's level they visited, within 3000) plays its first tell
  from that place's bearing: the pack's first long tell (`Pack.howlFrom`: the whines spread 9 degrees round the bearing,
  40 out, then the growl) and the pack keeps to that side at first; the call's first voices when its quiet ends
  (`Call.chorusFrom`: 26 out, spread round the bearing). Set by the traps' `onArmed` (`HuntScene.calledFrom`).
- **R11-15** (26): a spider never holds on the part of the ring within 70 degrees of the player's look
  (`Call.outOfLook`; it waits behind them and comes in from there) and is never the one walked into view.
- **R11-3** (25, C): the gallery's `f_1` and `f_3` (the pack behind the camera, one walking round into the middle of the
  view), `f_5` (a clear night, the camera turned round onto the pack: the band, then the closing) and `f_6` (the same turn
  in a fog), at render distance 12 (`-Pgallery.rd=12`) under the gallery's haze.
- **R11-10** (21): `Ring` radius 4 to 9; `sc21` v2: `d` 2.2 to 3.2 and `k` 48 to 72 (every cell of the widest ring, one
  wide). The point ahead is on the circle of `radius / cos(lead)`, so an animal walking at it stays on the circle (a
  pursuit of a point on the circle settles a block inside it on the wider ring). The gallery's morning shot puts back the
  staging's own grazing first, so it shows exactly what the scene bares. Steered and frozen animals never grazed (their
  goals are stopped): the stray dirt was the staging's idle herd.
- **R10-9** (21, 23): from Proximity at night, a herd within 48 of the target's base comes first (`RingCard.plan`, their
  own field; any herd otherwise). From Telling, 23's bearing for a target 200 or more from their base is their base
  (`HeldBreath.From.HOME`; nothing is done there).
- **R11-11** (23): every held mob looks 80 over the eyes at the point 200 out (15 to 30 degrees of pitch at once,
  `HeldBreath.LOOK_UP`); zombies hold their arms up (`MobTamperImpl.raiseArms`: vanilla's aggressive pose, put back at the
  release); the nearest monster within 24 turns its back on the player fully (`HeldBreath.awayFrom`).
- **R10-6** (22, 23): `sc22` v2: `a` 600 to 1500 (keepOrNew); 23 pointing at a place 300 or more off is remembered for
  each sharer (`Sc2xData` key `p`, lenient) and their next file walks there over a nearer place (`ProcessionCard.destination`),
  then it is forgotten. A file someone follows (a participant within 48 of its last animal) walks on past `d` to the place,
  up to its 400 s (`Processions.followed`).
- **R12-34** (22): a file whose place is not loaded keeps its walkers, out of view, walking on toward it, until it loads
  (then each goes inside as before) or the 400 s are up; none is let go in the field for it.
- **R11-13** (22): 3.5 between animals (`Sc22Config.SPACING`, from 2), the biggest and the smallest in turn
  (`ProcessionCard.bigAndSmall`), and of the two tangents the one that passes in front of the target first
  (`ProcessionCard.sides`), so the file crosses the view at a slant and goes away along it.
- **R10-10 and the comedy risk** (24): the follow is a glimpse, never an escort: at most 5 minutes (`Follows.FOLLOW_TICKS`)
  at 28 to 40 (`sc24` v2: `a` 28, `b` 40, `c` 20, `d` 56, `e` 0.5 days, keepOrNew), standing and staring when looked at;
  never after a rider or a runner (it is left behind: a cow that keeps up with a horse is a mod); then it walks off and,
  out of view, goes back where it was taken. The animal is the player's own stock first (a free one within 32 of their base,
  ledgered from the start, `Follows.BASE_NEAR`), else one near them. That night, with them indoors at home, it (or another
  of theirs) stands at their window; its time runs to dawn (`Follows.until`), and at dawn every animal it took from their
  home is back there (the ledger's undo; the switch and Ending D as before). "Prefer it within a day of a trespass" is when it
  fires: FIX-PACE's.
- **R11-14** (24): the animal easiest to know again first (`FollowerCard.recognisable`: a sheep of a colour, else the odd
  one of its kind round it); at the window it stands 3 to 5 out on the window's axis (`WINDOW_NEAR`, `WINDOW_FAR`), a window
  before a door. The gallery's follower is a black sheep, in the field and at the window.
- **R10-8** (27): counted when its viewer's client saw the copy (`Sc27SeenPayload`, `a1016_02:entity/sc27_seen`, once a
  showing; `Sc27.seen`), never when sent: one never seen may come again another dark. Never seen, it stands up to 40 s
  (`CopyRules.life`, `UNSEEN_LIFE_TICKS`); seen late, long enough to be looked at. The spot prefers the side the viewer is
  turning or walking toward and the near edge of the band (`Sc27Spot.TURNING_SIDE`, `NEAR_EDGE`). Not R10's 25 to 45
  degrees: the copy is put there only off the screen (guardrail 3), and at 25 to 45 it is on it; 40 to 80 stays.
- **R11-4** (31, C) and **R10-15**: `sc31` v2: `a` 96 to 56, `b` 256 to 110 (keepOrNew); of the tops in reach, one 64 to 85
  off first (`Apex.READS_FROM`, `READS_TO`), then one with the sunset behind it (west of the player), then the nearest
  (`Apex.score`). The gallery shoots it at 110, 90 and 70, and at 13100 (`apex_late`, `apex_late_mid`): at 70 he is a person
  about 10 pixels tall with his eyes, at 90 and 110 a bump; under the gallery's haze the late dusk tints him like the brick
  and reads no better, so the window stays the ridge's dusk (without the mod's fog, as under Mariano's shaderpack, he is a
  dark shape against the evening). The shape is FIX-WORLD's (with R11-1): the point taken off, the top a 3 x 3 level. On
  it he stands at the **edge toward the player** (`Apex.stand`, `Apex.towardEdge`: the top's block one of the eight round its
  middle, when it gives no light and has room; a single-block point keeps him on it; a lit middle still refuses the top):
  from the middle the top's own rim hid his legs from below, a head and shoulders over the brick at 70
  (`G/31/apex_near_middle`); at the edge he is a whole person on the top against the sky (`G/31/apex_near`). (FIX-ACTORS-2.)
- **Left, with reasons**: R10-18 (COULD: a struck held mob turning only its head: vanilla's body rotation pulls the body after
  the head on a frozen mob, not cheap to hold apart; the client computes a mob's body from its head, so holding them apart
  needs client code); R10-11 (28: not this worker's); R10-1's caps for 21 and 23 and the trespass preferences of 24 and 31
  (when cards fire: FIX-PACE's); 29 (R10 asks nothing of it); R10-6's "the next 24 prefers 23's bearing" (the follower is
  now the player's own animal at their window, R10-10: it has no bearing to keep; 22 keeps it); R10-14 (COULD: the moments
  under Iris: the dev client has no shaderpack, so it is the verifier's look on the real install); R11-20's 27 near_day
  check view (27 is A: left as it is).
- **The three risks** (mandatory checks): 26 is never a horde (`Call.HARD_MAX` 6 whatever `c` says, tested with ten near and
  twenty asked; `G/26/f_2`: six on the ring); the 24 animal never reads as comedy (a glimpse at 28 to 40, never an escort,
  never after a rider, then the player's own black sheep at their window: `G/24/in_1`); nothing reads as a mod effect (no
  particle or light on any of them; the pack's one walks round, never across, and the follower and the copy are moved only
  out of view; 31 stands on the brick, never on a lit top).
- **FIX-ACTORS-2** (branch `fix/sc2-actors2`: FIX-ACTORS stopped while merging FIX-PACE). The merge compiles and keeps both:
  FIX-PACE's clock, day roll and the call's gate (`CallTrap.dark`) with this work's scenes (`calledFrom`, the told rule, the
  cap). `sc25` is at 2 (FIX-PACE's step only) and `sc26` at 2 (this work's step only): no step to chain. No debug code was
  left in. R10-5 and R11-3 checked again at render distance 12 under the gallery's haze (`-Pgallery.rd=12`): on a fog night
  the one walked round from behind the player stands in the middle of the view on the crest, a pale wolf facing them
  (`G/25/f_1/f25`); turned round, three in the band at once in the fog (`G/25/f_6/f19`); the clear night's band, then the
  closing at 12 to 16 (`G/25/f_5/f30` to `f39`); at dusk one side-on by the crest (`G/25/f_3/f26`). 26's six on the ring,
  one in view (`G/26/f_1/f15`, `f_2`); 21 to 24 as listed above (`G/21/aerial/f01`, `G/22/side`, `G/23/near`, `G/24`).
- **Tests**: `accident.SelfCreated2HuntTests` +5 in a batch of their own (`accident/sc2_hunts_e`): the first tell from a
  trespassed place's way (both kinds); the bands, the sight, the
  cap and the spider's place (pure); the pack's tells first, plain, the closing and its growls, then the bite, and beside it
  the pillar's escape after the closing; the call's six of ten, one in the middle of the view at 14 to 16 (never a spider),
  spiders off the look, 20 s on the ring; the same show for the gallery's stand-in; and (FIX-ACTORS-2) the one shown walks
  round from straight behind a player who never turns, never nearer than the band's floor, plainly seen within 45 s
  (measured 23 s; `theOneShownWalksRoundFromBehindIntoTheMiddleOfTheView`); 21's bared-ground test no longer races the
  ring's own print tick (its predicate says in view until it looks). `atmosphere.SelfCreated2InstrumentTests`
  +5 (`atmosphere/sc2_d`): their own animal home after its follow, at the window that night, home at dawn, nothing left in
  the ledger; the animal easiest to know again; the ring in their own field from Proximity; the file big and small, in
  front, walking on while followed and holding for an unloaded place; the world pointing again, and home from Telling.
  Changed with the numbers: the pack's band, the call's show (one may stand at 14 to 16), the ring's clamp and cells, the
  follower's band, nearest, catch-up (now with the rider: `...NeverAfterARider`) and window, the file's gap, the held
  breath's look up, arms and back; `entity.SelfCreated2EntityTests`: 27 counted only when seen, its unseen life and its
  turning side; 31's band and score, and its stance at the flat top's edge (FIX-ACTORS-2). Outside the owned files: none
  of world/** or the director; the two test environment
  files; `atmosphere.mob.MobTamperImpl` and `TamperState` (the arms' pose, `raiseArms`, put back by `release`); accident's
  `Hunts` reads entity's `FogEdge` (the fog end the client draws, as 27 does).

#### Fixed in review (FIX-SC2-WORLD: R10, R11, R12 and R13 in the world workstream, branches `fix/sc2-world` and `fix/sc2-world2`)
The orchestrator's decisions on the findings whose code is under world/** (the kit, 16 to 20, 28, 11+), round 2's world
exhibits (R11) and the places' reach (R10-3). Every shape change is versioned first (R12-2); nothing had shipped, so each
kind's first geometry is its version 1, the review's its version 2 (16 also 3).
- **R12-2** (shape versions). `Start` carries the version of its kind's shape it was made with (`LATEST` 0 for one not
  tied yet: the gallery's, the tests', siting's new starts; `FIRST` 1 for one saved before versions); `Sc2Kind` its
  shape's current version (`resolve`, `shape`); the kit ties a start when siting made it, `Sc2Piece` saves it (`g`, absent
  1), `Sc2Data` keeps it with every place (`g`, absent 1), and every rebuild (worldgen, a reload, an inside, the answers'
  shapes, `Live`) builds that version: a place half generated when a shape changed is finished as it was begun. Each
  changed shape keeps its earlier versions reachable. The line (19) saves no start: `Plans` (the kit; `Sc2Data` key
  `plans`) fixes a segment's whole layout and its shape's version the first time it builds anything in a world (by the
  world's seed, worldgen salt and arm), read before the snapshot at start and drained every tick and at stop.
- **R12-1** (18's landing froze). Version 2: where water under the high opening could freeze (any biome vanilla's lookup
  may pick for a landing cell, cold enough to snow), the landing is a drift of powder snow on the floor (vanilla never
  freezes, melts or snows over it, it stops a fall of any height, one walks out of it); elsewhere the pool of water.
  Tested over frozen peaks after worldgen's own freeze step and the weather's rules, a cow stepping into the opening.
- **R12-3, R13-3** (fair edges). 20 keeps its edges, the design's cliffs and built-up faces: version 2 raises an edge
  whose land falls 2 or more away by a block (a kerb of turf) and puts a lip on a cut side over 3, a block over the land
  coming down to it; 16 has none over 3 (version 2 on land: a plinth, its own steps going on down a block out each course
  to the land, a lip round its cut to the way in where a side stands over 3). D-097.7 says so. Tested by walking without
  a jump (`world.Edges`) on slopes, version 1 failing the same walk.
- **R13-8** (breaking in). Kept as the player's choice (D-097.7): a closed pyramid dug into at its foot opens onto the rim
  walkway or the first tread (tested, every face and size); higher up it opens over the vault as a mine opens into a
  ravine. 18's rock over a tall inside was already 5 or more in all but 1 of 8 253 columns measured (SC2-5).
- **R12-5** (18's start cost). The land's top read every other grid point first, then only round points high enough for
  the inside, and not at all past that when no square can fit: the same shape (tested against reading every point), 87 %
  of the reads on the real peaks' starts. The last 8 shapes made over the noise are kept (`HollowSiting`). Left: the
  shape still needs the land's top on a 4-block grid (an 8-block grid left cliffs under 3 of rock, SC2-5).
- **R12-9**: a waiting row of 17b asks again at each try that nobody stands under it or next to it (`stillClear`).
- **R12-10**: nothing of the archive's own once the story holds the layer back (`Answers.held`); 17a never in a player's
  first hour.
- **R12-11**: 16b, 18b and 17c never move a block into a cell a living thing stands in (`Answers.occupied`; core's
  `TraceEdit` refuses it too).
- **R12-12**: 17e's look runs only while an archive of that level still has its 17e to come, never reading a container
  whose loot is not rolled, the places' boxes listed once a look.
- **R12-13**: the guard keeps a chunk's bits a 16-high section at a time, only for the sections a place touched (a data
  pack's 4 064-high level costs what the default does), at most 2 048 chunks (4 096), and asks the thread's own depth
  only for a guarded cell.
- **R12-14**: what worldgen recorded or fixed after the last tick's drain is kept at `SERVER_STOPPING`, before the last
  save.
- **R12-15**: no start is decided with a generator other than the live server's overworld's (`Snapshot.inOverworld`).
- **R12-16**: a hollow's record without its stored inside counts its box, never a shape built on the server thread.
- **R12-17** (accepted): D-104 amended for the fills that read the land under every column (16 on land, 20).
- **R12-19**: the noise terrain keeps one cache a thread for every terrain of one land, not three ThreadLocals each.
- **R12-20**: 16b, 16c and 18b plan only over loaded cells. **R12-21**: an inside made once is kept across the index's
  rebuild; 20's inside comes from its record (never the noise on the server thread).
- **R12-22**: t0rch's hole (11+) is never dug beside water or lava. **R12-23**: 17c asks who is at a mouth first.
- **R12-24**: a waiting 16b or 18b closes only on someone inside whose own time ran (a newcomer inherits nothing).
- **R12-25**: nothing that needs a tool happens to one who may not break blocks (adventure mode).
- **R12-26**: 16c never takes a redstone torch; 18b never moves a block a player set.
- **R12-27**: the debug's forced door and `sc 17 arm` keep the switch, the story and nobody inside.
- **R12-44**: the world's exhibits (16, 18, 19, 20) are built only in development, as 17's.
- **R13-5, R13-9**: the world's error lines go through `core.ScLog` (no frames above DEBUG; `THROWN_PENDING` is empty), in
  neutral words ("s2: the numbers could not be read", "s2: sNN (i) failed, using its box", the kind's id, never its
  `Remains.Kind`).
- **R13-6**: 28 logs its id and form only, where it moved a stack at DEBUG; no INFO, WARN or ERROR line of the second wave
  hands the logger a position (`VerifyScSpoilerTests.noLogLineOfTheSecondWaveGivesAPosition`).
- **R10-3** (the places within reach). Placement, gates and siting (configs keep a value a player changed: `keepOrNew`
  where still version 1's), before to after, and measured (`SelfCreated2HeightsTests.presenceNearTheOriginWhenAsked`,
  `-Da1016_02.sc2presence=true`: 20 default worlds, worldgen's own path over the real noise, nearest candidate first, the
  first place of each kind; 16 an open land one, neither sealed nor the sea form; distances from the origin, the default
  spawn; this branch's run):

  | kind | placement, before to after | gates and siting, before to after | before: median, within target | after: median, within target |
  | --- | --- | --- | --- | --- |
  | 16 | rays 3, first 768, step 896 to rays 4, first 640, step 640 (jitter 8 degrees, 48 blocks); villages kept 8 to 4 chunks off | `b` 640 to 560; `c` spread 12 to 16; wet samples 1 to 3 of 9; biomes + forest, flower forest, birch forest, taiga, snowy taiga, both old growth taigas, windswept savanna; `e` sealed 0.25 to 0.15; `f` lit 0.35 to 0.6; `d` sizes {0.5, 0.35, 0.15} to {0.1, 0.45, 0.45} | none within 2 400; 1 of 20 within 900 | 646; 13 of 20 within 900 (65 %, R10's target 70 %) |
  | 17 | random spread 31 / 14 to 20 / 8 (trial chambers kept 4 chunks off) | `b` 500 (kept) | 685; 11 of 20 within 700 | 633; 14 of 20 within 700 (70 %, target 70 %) |
  | 18 | random spread 48 / 24 to 24 / 10 | `c` minRise 36 to 28; `i` high window 0.5 to 0.9 | none within 2 400; 1 of 20 within 1 500 | 1 901; 8 of 20 within 1 500 (40 %, target 40 % of the worlds with a mountain within 1 500) |
  | 19 | four segments 1 024 to 2 048 out on the axes (unchanged) | unchanged | | |
  | 20 | random spread 56 / 28 to 36 / 16 | `b` 900 to 700; a square of 128 tried first, then 64 | 1 741; 8 of 20 within 1 500 | 961; 14 of 20 within 1 500 |

  16's misses are its siting (29 to 31 of the 35 or so near slots that make nothing fail the land's spread, wetness or
  the foundation's reach, 5 or 6 the biome): a wider spread (20) or a fifth ray measured no better over the same 20 worlds,
  so R10's numbers stand. The answers' gates: every one as before (out of view, ledgered, fair danger for everyone inside,
  the caps, the story's quiet), plus R10-7 (a player's first time of each answer, and its parts that day, even in their
  quiet), R12-25 (no tool answer to one who may not dig), R12-10 (the story's hold), the occupied and loaded checks.
- **R10-6**: 16's light more often (`f` 0.6) and a 3 x 3 lit top; 17's open way has a glowstone in its lintel.
- **R10-17**: 19's ribs are plain obsidian to the top (no weeping block; R13-14's purple grid).
- **R11** (the world's exhibits; before: the integrated run, after: this workstream's rounds). R11-1 and R11-4's place
  side (16l, 31's standing spot): the point taken off, the top a 3 x 3 level, glowstone there on a lit one (`Place.apex`
  its middle; one stands a block over it). R11-2 (19): panels stop in the horizon's band (the land under the bay plus 64
  to 80, ragged in whole bays, under Y 192), ribs to the top. R11-5 (20): the first house's proportions (two plank courses
  over the cobblestone, the roof on them), a two-block window in each wall, its torches on the side walls by the doorway
  (the light out of it: a lit house seen from the floor's edge at midnight, `near_3`), the wider square, a far view from
  low ground (`far_2`). R11-6 (17, 17l): the shelves within reach mostly taken, more toward the dais, full above the
  deck, a few gone to planks, no books but the records. R11-7 (16, 16x, 16s): the grounds block by block, trees judged by
  their trunks. R11-8 (18, 18w): the opening 5 x 5 over a 7 x 7 landing, near certain, the one nearest the nave first, a
  plain stone floor. R11-9 (16): sizes {0.1, 0.45, 0.45}. R11-12 (17): the mouths notches open to the sky with the land
  for sides, no lining in view, dead ground before the open one. R11-16 (16, 16s): the damp in dark drips; the way in by
  a cut 2 wide. R11-17 (16, 16x; version 3): from the pit's floor a dead end 2 x 2, 8 on under the steps toward the way
  in's side, unlit, sealed round; its mouth takes the two lowest treads of its strip (a drop of 3 at most), the gallery's
  `in_5` and `survey_5`. R11-19 (17): plank ceilings in the cells. R11-20: 16's `in_3` camera off the bottom torch (a plain
  torch); the real-terrain audit's cameras look at 19's panels' band and take no mouth or inside view of it.
- **Left, with reasons**: R11-17's landings every 8th level (a landing 3 deep shortens the pit by 2 a landing, and the
  siting's height rules, the anchor and 16c's step count read its depth; the dead end gives the descent its mark, so 16c's
  glimpse stays where it was); R11-18 (optional: 16d's way in at the waterline needs a fair stair down a dry core); R12-18
  (going back to an older jar: that jar's own save drops the key and its chunks drop the unknown starts; downgrade only);
  R10-16 (20b putting the house's torches out: a new ledgered answer, a design addition for a later round); R10-20 (doors
  toward the spawn: D-095's design); R10-11 (28's choice of stack: outside 16 to 20, this round's scope).
- **Tests**: `world.SelfCreated2KitTests` +4, `SelfCreated2LineTests` +2, `SelfCreated2HollowTests` +2,
  `SelfCreated2PyramidTests` +4, `SelfCreated2ArchiveTests` +1, `SelfCreated2HeightsTests` +2 (one when asked),
  `core.VerifyScSpoilerTests` +1, the helper `world.Edges`; the tests the new numbers and shapes changed are updated
  (rays, biomes, spreads, the shore, the house, the line's columns, the pit's checks, a kind refused or moved near the
  height's ends, never clipped). Outside world/**: `core.VerifyScSpoilerTests` (`THROWN_PENDING` emptied, the position
  rule), docs.

## Verifier checklist (SC2-V)

- `./gradlew build` three times in a row, green; `./gradlew build --dry-run` runs no client game test;
  `./gradlew gallery -Pgallery=all` (or Fallback A) produces every exhibit's PNGs and the index.
- Every wave 4 finding re-checked against its test or PNG.
- This file against the code: every feature's numbers against its config defaults and structure set JSON; the pacing
  table against the registered cards; the owned paths against the diff of each merge.
- The pacing (FIX-SC2-PACE): `SelfCreated2PacingTests`' numbers against the tables of "Pacing and director integration";
  in the dev client, `/a1016 director` prints each player's layer line (the beat and its window, the group's gap, the own
  clock), and a world stepped into Proximity (`/a1016 stage 2`) gets the held breath at its first dusk or night with mobs
  about, real, before any other second-wave card.
- The spoiler grep (`VerifyScSpoilerTests` green with the new words; a hand look at `/locate structure a1016_02:` tab
  completion and `config/a1016_02.json`); the side test; the guardrail greps (R13's list).
- Save compatibility: a world from the first wave's preview jar opens and gets remains only in new chunks; a world with
  remains opens in the first wave's jar without errors beyond vanilla's "unknown structure" lines.
- **Dev client** (singleplayer, `runDevClient`): find a pyramid with `sc 16 a`, walk in, `sc 16 arm` (16b, 16c), and the
  same for 17 and 18 (`sc 17 arm`, `sc 18`); force 21, 22, 23, 24 near animals; 25 among wolves at night (`sc 25 arm
  force`: two howls, the stalk, a pillar is safe); 26 on a fog night; 30 with a map; 31 at dusk near an apex. The switch:
  `/a1016 sc off` gives back every answer, releases every scene, ends every hunt; `/a1016 sc on`.
- **LAN pair** (two clients on one machine or two, the host and a guest; then swap roles for 3 and 5):
  1. A ring (`sc 21 force`) seen by both from two sides; the ring of dirt the next morning for both.
  2. The held breath (`sc 23 force`) with both near: every mob frozen facing one way, silence on both clients.
  3. Where you weren't: A at least 12 below the surface in a mine (and 72+ from B), B outdoors at night, `sc 27 arm B`: B
     sees A's copy with A's name tag and item, A's locator dot elsewhere; B looks at it for 3 s, it walks off; A sees
     nothing. The rest of the steps: SC2-6's LAN note (its "As built").
  4. Borrowed: a stack in A's chest, both 32+ from their bases, `sc 28 force`: it is in B's chest; `sc off` gives it back.
  5. The pack: B alone in a taiga at night, `sc 25 arm force` for B: howls heard by B (and by A if within range), the
     stalk, the attack; A runs in: the wolves stay on B; A hits one: that one turns on A.
  6. The other door: A enters an archive and goes 64+ away; B comes to the mouth: rock; another mouth is open.
  7. Not you: A mines; B alone 60 away: B hears A's mining from the wrong side (`sc 29 force`).
  8. Leaving mid-event: B quits during the pack's stalk (the wolves calm and go); A quits during B's 27 (the copy ends).
  9. Dimensions: A in the Nether, B on the surface at night: 27 shows A's copy.
  10. A late join: B joins while a ring circles near A and sees it circling.
  11. Borrowed, both ways (SC2-7): two bases 40+ apart, two chests at A's (one rich), one at B's; B stands by A's base,
     then both walk 32+ from home, `sc 28 force` for either: a plain stack from A's other chest in B's; the next real one
     comes from B's base after A visits B's; `sc 28 fake` moves one inside one base; `sc off` gives each back.
  12. Not you, host and guest swapped (SC2-7): the guest mines a few blocks of stone 60+ from the host, the host alone
     underground or at night runs `sc 29 force`: the guest's rhythm from inside the rock on the side away from the
     guest's locator dot; the guest hears nothing. Swap roles; then the guest quits mid-replay (it ends).
  13. Maps that disagree (SC2-7): A makes a map at home and a copy; B makes their own map of the same land; A walks 64+
     off with both copies in the pack (not in hand), `sc 30 force` for A: both copies show the house gone, B's does
     not; A walks home holding one and it draws back; from Telling, `sc 30 b` with a known place on the map never visited
     draws it in. `sc off` writes back whatever vanilla has not drawn.

## Verified (SC2-V) and FIX-FINAL

**SC2-V** (Oct 25, 2026, on `self-created` at `0e5c216`): NOT READY, narrowly, on one HIGH (V-1); everything the wave
owns passed. Three `./gradlew build` in a row green (one `--rerun-tasks`), 1 102 game tests each, no flaky test;
`--dry-run` wires no client game test; `gallery -Pgallery=all` 22 exhibits, 900 PNGs, no `BLACK?`, `FLAT?` or `CHUNKS?`;
the spoiler surface (config, commands, suggestions, logs, chat, the jar's resources and classes) clean; the fair-danger,
dedicated-server side and multiplayer tests present and green; the pacing numbers equal to this file's tables; Mariano's
own 1.4.0 config upgraded with his values kept. Counts: BLOCKING 0, HIGH 1, MEDIUM 2, LOW 6. **READY after FIX-FINAL.**

**FIX-FINAL** (branch `fix/sc2-final`; `./gradlew build` green twice in a row, 1 104 game tests each, the mod's log
lines now kept to the end of the run):
- **V-1** (HIGH, Iris). The eyes' own pipeline (`a1016_02:pipeline/entity/him_eyes`, first wave) was unknown to Iris:
  under Mariano's Iris 1.11.7 + Sodium 0.9.2 + Complementary Reimagined r5.9.3 the log got `Missing program ... in
  override list` at ERROR and a thin purple line crossed the screen near him (`build/gallery-iris/31/apex_near`,
  `apex_late`, `apex_mid`, `apex_near_middle`), and his eyes were not drawn at all. Now, while a shaderpack is in use
  (`IrisShaders.packInUse()`, Iris's public API by reflection, read every frame), BRIGHT draws through vanilla's eyes
  render type, which Iris maps to the pack's emissive entity program as a spider's; the pack draws its own fog, so the
  fog resistance has no say there. No compile or runtime dependency on Iris; without it nothing changes. (Iris's
  `assignPipeline` would also do, but it logs a WARN naming the pipeline at every launch.) Checked with
  `runDevGallery -Pgallery.iris` on 31, then 25, 26, 27 and 00: no `(Iris)` ERROR and no `override list` line, the line
  gone from every shot that had it (0 of its pixels where there were 35 to 246), the eyes white on the apex. It is the
  mod's only pipeline and only shader in both waves; `core.VerifySc2FinalTests` keeps it so (a pipeline or shader of the
  mod's own must ask whether a pack is in use, and a new shader fails the test until it is checked under Iris).
- **The Iris dev flag**: `-Pgallery.iris` on `runDevGallery` ("How to use the gallery"), dev only, never in `build`.
- **V-3** (LOW, tests). `core.LogCapture` cut the mod's logger off the log files for the rest of a test run: log4j's
  `Logger.addAppender` gave it a config of its own copying the root's additivity, which is false. The first capture in
  now sets that config additive and the last one out removes it; tested before, inside, after and nested. The pacing
  info lines (`sc2 beats: ...` and the rest) show in the test run's log again.
- **V-6** (LOW). The planner's arm line for the layer's own kinds (`sc25`, `sc26`) ends before the position at INFO
  (`accident: day 0: sc25 (<player>'s) watching`); the history keeps the place for the op's status. Tested in
  `theHuntsCluesStayOutOfTheLogTheHistoryAndTheStatus`.
- **V-8** (LOW, docs). The status line at the top.
- **Left**: V-4 (16l's light faint at 140 and gone at 200 in vanilla's haze; plain at 200 under Complementary, which
  Mariano plays with; a second lit course stays optional for a later round). V-7 (R10-11, 28's choice of stack: neither
  fixer took it; left for a later round). V-9 (what GitHub shows: the orchestrator's, at push time). V-2: the checks are
  split below.

**Accepted (the orchestrator), V-5 MEDIUM**: the first danger moment reaches about half of players within five sessions
(a hunt in 5 sessions 48 / 55 / 64%, R10-4's "first hunt by 4 h in half the runs" not met). Accepted as deliberate
rarity: the base layer's accidents and the first wave keep danger present between the hunts.

**Left for Mariano's real install** (spoiler-free; give him only this list):
1. Shaders: the new jar in his real `mods` folder with Iris and Sodium, his shaderpack on, a throwaway creative world
   for five minutes, quit, and search `logs/latest.log` for `a1016_02` lines at ERROR and for `override list`. Expect
   none.
2. LAN smoke test: his friend hosts with the very same jar file (compare size or hash), he joins, a normal session; once
   the guest quits and rejoins, once the host saves, quits to title and reopens. Expect no disconnect, no mod mismatch,
   no `a1016_02` ERROR in either latest.log; send both logs if anything is odd, without reading them for content.
3. Dedicated server (only if they will use one): he accepts the EULA himself (`eula=true`), starts it with the jar and
   Fabric API, joins, stops it; the same log check.
4. Config: nothing to do (his 1.4.0 `config/a1016_02.json` upgrades itself and keeps his values); keep a backup anyway.
5. A new hardcore world for the new layer, as the README says.

**For a tester who is not Mariano** (spoils): the checklist's dev-client block and the LAN pair's 13 steps above, host
and guest swapped for steps 3 and 5; under Iris + Complementary at render distance 18 and gamma 0.92: 21 at dusk, 25's
stalk on a clear and a fog night, 27's copy, 31 at an apex. Not run anywhere yet: a 1.4.0 world opened in 1.5.0 and back
(R12-18), and `/a1016 director`'s per-player layer line with a real participant.

## Considered and left out
- A creature of our own, custom sounds, jumpscares, lightning, glowing or red eyes, chase music: the clichés of the fog
  and dweller mods; vanilla only.
- Runtime-built structures near the player: pop-in, guardrail 7, ARCH 3. The remains are worldgen (D-093).
- Remains near spawn or in the first hour: the first hour stays as D-070 measured it.
- The fanon shrine (gold, netherrack, fire, redstone torches on mossy cobblestone): excluded fanon (DESIGN). The pit's
  light is a plain torch on cobblestone; the apex light is a lone glowstone.
- A hunt without tells, without a way out, at home, or with creepers in it: unmarked or unavoidable deaths (D-097).
- A fake player on the server for 27: everyone would see it and could hit it; the client-only copy is the deniable form.
- Fake "joined the game", tab entries or chat lines: the 2011 cliché (the first wave's rule).
- Changing a friend's skin, name or eyes: guardrail 3's spirit; 27 shows the friend as they are.
- Deleting a player's items: 28 moves them, ledgered.
- An endless maze: server cost and no exit; the annex is a finite grid with loops, always diggable.
- A shaft in the archive's floor: a hidden fall once the lights go (fair danger 7); his bay is a tunnel.
- Vanilla terrain adaptation (the beardifier): it acts only in worldgen noise, so the gallery would not show what
  generates; every shape makes its own foundation and seal.
- Reverting the remains with the switch: it needs the terrain that was there (D-093).
- The mod's fog as this wave's main device: Iris hides it on his install (D-102).
- Missing time (the clock jumping): one clock for every player, phantoms, crops, furnaces; too many side effects
  (backlog at most).
- Squid and bat swarms in tier 1: little payoff for the cost (backlog).

**Orchestrator decision (after SC2-0c): far views at 200.** At render distance 16 the client draws nothing past about
256 blocks, so every `far_*` view (and `19`'s `far_side`) is 200 out, not 300 (`Gallery.standardViews`). A player at
12 chunks sees about 192, so 200 is what a real approach shows. Judge long silhouettes with `-Pgallery.rd=24` on top
when a structure is meant to be seen from farther.

**Orchestrator decision (Mariano, mid wave 2): height limits and terrain extremes (SC2-H).** No structure may be cut
off or broken by the world's limits. Heights are never hardcoded (the level's minY/maxY; datapacks change them; default
-64 to 319); a start that would reach the bedrock layers (keep 6 above minY) or past the top (keep a margin under maxY)
is refused or moved, never clipped; siting handles jagged peaks (surface about 260), deep oceans, cliffs, caves,
aquifers and lava below 0; the cells that matter are safe from later decoration steps (trees, lakes, geodes, ores,
dripstone). SC2-5, SC2-7 and SC2-8 got this rule while building. After every structure is merged, task SC2-H audits the
kit and all of 16 to 20 against it (the kit refuses out-of-range boxes centrally; game tests near both ends and at a
shorter custom height; amplified and large-biome worldgen; a gallery pass on an extreme site) before SC2-9 closes.

#### As built (SC2-H, `world.SelfCreated2HeightsTests`, 11 tests)
- **The kit's height rule** (`world.sc2.kit.Heights`, `Sc2Structure.site`). Every start goes through `Sc2Structure.site`:
  the kind's siting, its shape (the one siting handed over with `Siting.handOver`, else made now), then the kit's own
  rules on the shape's final box, which no kind can forget: within `MAX_REACH` chunks, and within the level's height,
  6 over the lowest block worldgen fills (`Heights.lowest`: the level's own, or the noise's where a data pack starts the
  noise higher) and 6 under the level's highest (`Heights.highest`). Refused, never clipped; `site` says why (`siting`,
  `reach: ...`, `height: ...`), so tests read it. `findGenerationPoint` passes the level's own numbers to the query. A
  kind keeps a stricter rule of its own (the hollow keeps 8 under the top; the line's feature uses 6 and 6 as well).
- **Tests over the real noise** (`Sc2RealNoise`: a preset's generator and random state for any seed and any height,
  without a level; the structure placements; worldgen's own `Structure.generate` with that height): every registered kind
  is either named by a structure (so `site` holds it) or a feature with its own height tests (`s19`); every kind's first
  real start, asked again with the level's bottom or top moved to one block inside the margin, is refused, and at exactly
  the margin is not refused for height; and over the default world, a 0 to 255 world, 0 to 319, -64 to 191, amplified and
  large biomes, no start of any kind leaves the height through worldgen's own path. Kinds are read from the registry: a
  kind added later is covered without a change.
- **Terrain extremes** (a survey, `-Da1016_02.sc2survey=true`: four seeds of the default world, two amplified, one of
  large biomes and a short one, every candidate the placements pick round seven centres; each start built chunk by chunk
  into an `Overlay` over the noise's own blocks, `NoiseVolume`, caves, aquifers and lava included, and read by
  `Sc2Audit`: water or lava inside, water or lava beside an open cell the place wrote, the inside opened to a cave or to
  the open, a part over open air that shows from outside, the way in blocked, without headroom or over a drop). Found and
  fixed:
  - **17, the ways in.** The walk began near the flat-land estimate after a look every 16 cells, so a way crossed a valley
    or a lake's bed as a cobblestone tube in the air or the water; and nothing kept a mouth out of water: about half of the
    starts siting made had their open way come out into a river, lake or sea bed (the rooms flood at the first block update next to
    it) or cross one, and a few exits stood over a drop or inside the land. `ArchivePlan.measure` now walks from the first cell with a step
    of half the rock over the way's head (4 to 16) and goes back cell by cell: the mouth is the first cell that reaches the
    ground. The open way must have its mouth and exit dry on both columns, an exit within 16 past its mouth, and its exit
    cell at most `EXIT_DROP` 2 over the land. A closed way needs only its mouth (its plug holds whatever is outside);
    `OtherDoor` (17c) opens a closed way only if its cells are dry and its exit has land within 2 under it (live blocks).
    **An archive is now about 60 % as common on the land surveyed** (100 starts of 427 candidates against 167 before; of
    the refused, 158 had the open way's mouth in water): the set's spacing is the orchestrator's call (SC2-9: 31 / 14). The siting's bottom rule is the
    kit's (`BELOW_FLOOR` 18 under the floor, his bay's tunnel, plus 6), not the floor 18 over the bottom: a short world kept
    the bay's tunnel in its bedrock. The siting hands its plan's shape to the kit (a third fewer samples).
  - **16, the land under it.** Nine samples let a valley or a sea trench pass between them, and the foundation (24) hung
    over open air; for the size taken, 16 more (a 5 x 5 grid) must stand no lower than where the foundation ends, else no
    start. A size whose pit or apex would leave the level's height is passed over for the next (a 0 to 255 world gets a
    smaller one, never a clipped one). The surface samples are asked only once the ground's spread fits (a candidate that
    fits no size costs 27 samples, not 54; at most 70). **Shape change:** the approach in front of the way in gets earth under its floor down
    to the land (it was a one-block slab in the air where the land in front is lower).
  - Left as they are: a hall's floor or a hollow's lintel over a natural crevasse that opens to the sky (the noise's open
    pits; a cobblestone or stone face shows in it, as a stronghold shows in a ravine); one sea form in 17 over a trench
    between its 25 samples; a way's exit with the land rising again in front of it (one in 100).
- **Later worldgen** (`Guard`, `world.mixin.WorldGenRegionGuardMixin`). Measured in real worldgen first: a client run
  (`SelfCreated2RealWorldClientTest`, below) generated one place of each kind with every feature of every neighbour, then
  filled each again into an overlay over the world: what would change is what later decoration wrote into it. Pyramid
  1 083 cells (leaves and grass inside the core, on its floor and over the grounds; gravel and sand for bricks), sea form
  238 (seagrass), archive 17 (gravel in the seal), hollow 926 (543 gravel in the seal, 335 pointed dripstone hanging
  inside), floor 698 (606 birch leaves over the square). Now every cell a fill reads or sets in its chunk (`Writer`,
  worldgen in a decoration region only) is guarded until the chunk is loaded whole (`ServerChunkEvents.CHUNK_LOAD`: by
  then every neighbour is decorated); a decoration's write from another chunk into a guarded cell is refused at
  `WorldGenRegion.ensureCanWrite`, which every worldgen block write and vanilla's ore blobs ask; the place's own writes
  pass (the line's rebuild of a neighbour too). Again in the same run: 0, 0, 0, 2 (two seal cells), 0. A guard is a bit a
  cell for the level's height (12 KB a chunk), at most 4 096 chunks at once, cleared at stop; a chunk saved half made and
  loaded in a later run is unguarded (as before). Generation time of the sites unchanged within noise.
- **Cost.** `Terrains.Noise` (the kit's noise terrain) reads a chunk whole once one thread has asked about it 24 times (a
  shape filling a chunk asks every column): `NoiseVolume`, one volume of the noise's own blocks (the generator's
  `getBaseColumn` exactly: tested over 720 columns of five worlds, and the terrain read whole equals column by column over
  840 columns of 120 chunks, so a shape never depends on the order of its reads: tested for every kind). A column alone
  costs about 115 to 400 microseconds (the machine's load), a chunk whole 4 to 6 ms. The worst came from fills that read
  the ground of every column: the flat game-test world's noise is free, so the earlier measurements missed it. Per start
  (siting and shape, median / worst ms, samples) and per chunk (the fill into the overlay, median / worst ms), on the real
  noise, this Mac with other builds running, before and after:

  | kind | a start made, before | after | a candidate refused, before | after | a chunk's fill, before | after |
  | --- | --- | --- | --- | --- | --- | --- |
  | s16 | 6.4 / 8.9, 36 | 8.1 / 11.6, 43 | 8.2, 54 | 4.6, 27 | 21.6 / 112 | 6.7 / 24.3 |
  | s16d | 2.8 / 5.9, 21 | 4.5 / 5.8, 27 | 8.4, 54 | 4.4, 27 | 0.8 / 103 | 0.05 / 8.6 |
  | s17 | 18.6 / 54, 139 | 9.0 / 23.8, 62 | 13.5, 119 | 6.7, 54 | 0.16 / 298 | 0.17 / 12.1 |
  | s18 | 286 / 343, 3 169 | 303 / 345, 3 054 | 0.09, 2 | 0.10, 2 | 0.05 / 10.1 | 0.03 / 10.9 |
  | s20 | 10.3 / 11.9, 75 | 11.0 / 16.2, 75 | 5.3, 50 | 3.4, 27 | 37.8 / 79 | 6.8 / 24.6 |

  (ms median / worst and samples median; a fill into the overlay, which costs more a cell than a chunk's own sections.)
  The pyramid and the floor ask their surface samples only once the ground's spread or relief fits (the same answers).
  A refused candidate costs one sample for the hollow (its biome first), 27 for the pyramid and the floor, about 54 for
  the archive. A sample costs 115 to 400 microseconds here, so D-104's "81 samples under 1 ms" holds for none. The hollow still
  costs about 0.3 s for each start it makes (1 in about 100 of its candidates builds a shape; 18 of 1 609 surveyed): its
  shape needs the land's top on a 4-block grid out to 120 (about 3 000 exact reads; a whole chunk read costs more than its
  16), SC2-5 found an 8-block grid leaves real cliffs under 3 of rock, and the generator's column read cannot be made
  cheaper without changing its answers. Off the server thread, once a start.
- **The client run** (dev only, never in `build`): `SelfCreated2RealWorldClientTest` does nothing unless
  `-Da1016_02.sc2real=true` (or a seed) is set on the client run (a Gradle init script; `./gradlew gallery -Pgallery=none`):
  a world of the default preset, SC2-5's peaks seed, the gallery's stage and sky; for every kind a structure names, of the
  first six starts round the centres the most extreme (relief under its box, water in it), two of the hollow, and the
  line where it crosses the highest ground; their chunks generated for real; the later-worldgen read above; shots into
  `build/gallery/<kind>h/` (`far_day` and `mid` lifted over the trees, `side_1` to `side_4` from the diagonals 110 out,
  `near`, `mouth`, `aerial`, `survey_1` inside with night vision).
- **Tests** (`world.SelfCreated2HeightsTests`, own spots near -2 600 000, 2 600 000; test environment
  `world/sc2_heights`): the kinds and the line's margins; the margins and the level's own numbers (a raised floor, a level
  deeper than its noise); every kind refused near either end, never at the margin for height, and worldgen's path with
  the height moved; no start past the height in six worlds; the guard (a neighbour refused, the chunk's own decoration
  and the place's own writes pass, other cells and dimensions untouched, free once finished); the noise read in volumes
  equals the generator's columns; the terrain read whole equals column by column; every kind's shape the same whatever
  the order of its reads. When asked: the survey, one start taken apart (`-Da1016_02.sc2look=kind,seed,cx,cz,x,y,z`), the
  noise's costs. Dev helpers in the game test sources: `Sc2RealNoise`, `Sc2Audit`, `world.sc2.s17.ArchiveWays` (why a way
  does not fit).
- **Outside the owned files**: `world/sc2/s17/OtherDoor.java` (17c's dry check); `world/sc2/s20/FloorSiting.java` (the
  relief before the surface samples, the same answers); the far-patch waits of
  `core.SelfCreated2CoreTests`, `accident.Away` and `accident.SelfCreated2HuntTests` now wait in real time (at most 30 s)
  until their chunks are entity-ticking before the tick wait, as SC2-3's patches do (under machine load the chunk system
  took longer than a 200-tick test); `a1016_02.world.mixins.json`; the game tests' `fabric.mod.json` (the test class and
  the client test) and the test environment `world/sc2_heights`.
