# Self-created 2, round 3: found, and too big to have been built

Oct 26, 2026 · lead design for round 3 of the second wave · branch `sc3/plan`, from `self-created` (`97f4aee`, the
1.5.0-self-created.1 build Mariano played)

**Spoilers. Mariano plays this blind. He must not read this file, the `sc3/*` branches' commits, the gallery PNGs or
the test names.** This file changes `docs/SELF_CREATED_2.md` where it says so and nowhere else: its principles, fair
danger (D-097), multiplayer (D-098), the gallery (D-099), the spoiler rules (D-103), the cost rules (D-104), the
SC2-H height and terrain rules, shape versioning (R12-2) and every "Fixed in review" note hold. Every worker reads
`docs/ARCHITECTURE.md`, `docs/SELF_CREATED.md` ("Spoiler rules", "Guardrails"), `docs/SELF_CREATED_2.md` ("Principles",
"Spoilers", "Guardrails", "Fair danger", "Multiplayer", "The gallery", "Remains: common rules", the feature sections
their task touches, "As built (SC2-H)", "FIX-SC2-WORLD"), then this file's "Diagnosis", "Decisions", "Findability",
"The error signs" and their task.

## Status

Plan only. Nothing built. Tasks, ownership and waves at the end. Addendum after wave 2 began: 33, the depths under
16's pit (D-120 to D-124), task R3-D.

## What Mariano said

He played 1.5.0-self-created.1 for some hours, found an archive (17) and wrote (Spanish, translated; his words are
the brief):
1. The books in the library have only names. They should have some text inside.
2. The library would look more liminal if it were much taller, so tall that no person could have built it.
3. The entrances need a little light, or something distinctive that says you can go in there.
4. The places do generate, but after hours of play they were almost impossible to find, even flying in creative.
5. Make it more liminal, more like the old eras of the game. It should scare the shit out of you.
6. He likes that the entrances look like an error in the world. They are just far, far too hidden.
7. A breadcrumb mechanic that leads you to these places without your meaning to go: something that makes you
   curious. Redstone torches making a path you hardly notice, very far apart, guiding you where you should not go.
   It could pass for normal, but it is a bit suspicious.
8. The crumbs can be things made from the original blocks, in that style.

## Diagnosis (the code at `97f4aee`)

Blunt, so the round fixes the right things.

1. **The land of the first hours stays empty forever.** `world.sc2.kit.Snapshot.refusal` refuses every start while
   the world's stage is under the kind's `fromStage` (1, Traces, for every kind; `s19.LineFeature` the same). A chunk's
   start is decided once, when the chunk first generates, so every chunk generated in Alone (0.49 to 0.81 h measured,
   FIX-SC2-PACE) and every chunk generated for a player's view before Traces holds no place, ever. What Mariano flew
   over was mostly land decided before his world reached Traces.
2. **Far from where players live.** `minOriginDistance` 560 (16), 500 (17), 700 (18, 20) from the origin and the world
   spawn; the line from 1 024. The first place a new world can hold is past 500 to 700, in land first generated in
   Traces.
3. **Three of the five cannot be seen.** 17 is a 2x2 notch in a slope (it shows at about 40 since R11-12), 18 is "a
   mountain like any other" with one dark square on a cliff, 19 is a black wall that vanishes at night. At night nothing
   shows but 16's apex light (0.6 of land pyramids; faint at 140 in vanilla's haze, SC2-V V-4) and 20's two house
   torches. Density matters less than visibility: a flyer 30 over the land sees a 2x2 notch from 40 at best.
4. **`/locate` answers none.** 26.3's `ChunkGenerator.findNearestMapStructure` handles only
   `ConcentricRingsStructurePlacement` and `RandomSpreadStructurePlacement` (checked in the 26.3 bytecode), so the rays
   (`world/sc2r`, 16 and 16d) are never searched; and the random-spread kinds are refused by siting in a world under
   Traces, and are absent from land generated before Traces, so `/locate` says none for them too.
5. **Nothing leads to them.** R10-6 was taken in part (16's apex light, 17's lintel glowstone); 22 and 23 point only at
   places already generated, which were rare.
6. **The library is human-sized, and mute.** The hall is 63 or 95 long, 25 wide, 17 high (`ArchiveShape` v2): players
   build bigger. Its records are blank by design (D-100), the shelves at reach empty since R11-6.
7. **Creative visitors spend the places.** `kit.Visits` skips spectators only. A creative flyer counts as inside: the
   answers' firsts (R10-7) and per-place caps (16b 3, 18b 2, 17c 2) go to someone who cannot be hurt.
8. **Weak answers** where R10 and R11 said so and nothing changed: 20b (the cows face the house: the base mod's
   "animals facing the fog", R10-16), 16d (nothing to enter, R11-18), the hollow's dark (no lit focal point, R11-8 in
   part).

What works and stays: the pyramid's silhouette and pit (R10, R11), 17b's lights going out (17n, rated A), 16b, 16c,
17c, 17d, 17e, 18b, 31c among the trunks (A), the line's ribs and holes, the floor's first house (R11-5). Rarity is not
the scare: meeting it is (D-107's lesson, now for the places).

## Decisions

| ID | Decision | Why |
| --- | --- | --- |
| D-108 | **Dormant from creation.** The places generate in every chunk from the world's creation on, beyond 384 of the world spawn and of the origin (and never before the world's spawn is known); the stage gate on generation is gone (`fromStage` 0). Everything they do stays asleep until the world reaches Traces (`Remains.awake`): answers, the player's records, sightings at them, the crumbs' runtime rules, 01+, trespass, the visit events. Principle 6 now reads: "nothing happens to you in Alone or in your first hour; the world holds his places from its creation, as the old scars". Supersedes D-093's "from the kind's `fromStage`" | Mariano explored for hours and found nothing: the land of the first hours was decided empty. A place that is "always there" is the base mod's old-scar rule, and it is what "he comes with the world" means |
| D-109 | **Densities, distances and find targets** (the tables under "Findability"), measured by worldgen's own decisions over the real noise; the round is not done until T1 to T6 hold | Findability is a number, not a feeling |
| D-110 | **The error sign.** Every place shows itself from far as a fault in the world, never as a welcome: chunk-aligned cuts and missing pieces by day, a lighting fault by night (a chunk-aligned square of the world lit as at noon with no source, vanilla's `minecraft:light`), glowstone only where a block is meant. The way in is marked by the fault; the inside stays dark. Light read as part of the error. Each place's sign is specified at 64, 128 and 200, day and night | Mariano likes that the ways in look like an error and asks that they be seen. Old versions' lighting and chunk errors are period-true and read as "that's not right" from far. "He darkens a place before he appears" holds: the fault is outside; what he darkens is inside (17b, 17f, 18c, 20b v2) |
| D-111 | **The crumbs (feature 32).** Worldgen lines of small old-era constructions in the original blocks, laid from each place outward, sparse and deniable far off, closer, straighter and wronger near it; varied per place. They are put in, as the remains (D-094: the developer's way markers to his test builds, from before there were coordinates). At runtime he only removes (32b) and moves (32c) crumbs: guardrail 7 held. Distinct from 11 (t0rch's torches, a person's, in caves, once a world) | Mariano's breadcrumbs: curiosity that walks you in. Worldgen is "always there", cheap and chunk-safe |
| D-112 | **`/locate` finds every kind.** The random-spread kinds by vanilla once D-108 lifts the stage gate; the rays and the line through a locate mixin over the kit's placements and a marker structure for the line. Ids stay opaque (D-103 amended: `/locate` shows `a1016_02:world/sNN` only) | Mariano could not find them even looking for them |
| D-113 | **The archive v3: the stacks.** The hall three times taller with its light left hanging where the old ceiling was; a square well beside it from above the land to the world's bottom, galleries round a void, rails everywhere, water at the bottom; the well's top standing out of the land, cut open, as its sign. Scale down as much as up | "So tall that no person could have built it" |
| D-114 | **The archive's books are written, in his voice.** The records, the day books and the editions (catalogue below) carry text in the voice of the layer's front page (terse, lowercase, wrong, calm), composed from what the mod knows about the world and its players. DESIGN guardrail 2 ("he never writes") is bent for the archive's books only, as D-096 bent guardrail 1 for the hunts: the self-created layer is the part he made himself (its front page says so) and the archive is its record. Held everywhere else: no sign, no chat line, no other book, his name never written, nothing explained. Supersedes D-100's and D-083's "no new text" for the archive's books only | Mariano: the books should have text. A record of you, kept by something that was there, is the record theme at its worst |
| D-115 | **The liminal pass.** Per place: scale no person builds, old-era faults and shapes, one focal point in the dark (16 v4 with the 191, 16d's way in at the waterline, 18 v3's cut summit and its piece hanging inside, 19 v3 nearer and longer with its lit foot, 20 v3's fullbright test floor up to 192). What R11 rated A is kept | "More liminal, like the old eras of the game" |
| D-116 | **Answers that make going in frightening.** Creative and spectator players are never inside for answers (a creative visit spends nothing). New: 17f (the well goes dark from above), 17g (your record is rewritten while you are inside), 18c (the hanging piece goes dark, then the trunks), 20b v2 (R10-16: the floor and the house go dark, the animals turn to the doorway, a glimpse in it), 16d's way in with 16b. Kept as they are: 16b, 16c, 17b to 17e, 18b | Each place's answer should make entering a risk; the weak ones are rebuilt, the strong ones left alone |
| D-117 | **Versions.** Every shape change is a new version of its kind (16 v4, 17 v3, 18 v3, 19 v3, 20 v3); a start keeps the version it was decided with, so a place half generated in 1.5.0 is finished as it was begun (R12-2). Each crumb line fixes its layout and version the first time any chunk builds it (as the line's `Plans`). A siting decision, once made, is kept in the world (`Decided`) | Old worlds keep working; nothing half-built changes shape |
| D-118 | **Cost.** D-104's amended class (8 ms median, 25 ms worst per chunk, on the serial worldgen executor) for every place fill at the new scales; crumbs 0.5 ms median a chunk; a candidate's siting paid once per world, whoever asks first (its own start, a crumb, `/locate`); saves bounded (below) | Scale and density raise worldgen's work; it stays within what a structure may cost |
| D-119 | **The pacing is re-measured** (R3-P) with the places' new presence, their awake gate, 01+ only while awake, the crumbs' runtime rules and the new answers: D-070's first hour, DESIGN 4b's rates, D-107's numbers and the hunts' clock must hold, and "a place met by session N" joins the tables | Places from creation and denser change what the director's dry runs assumed (`sc01 p` 0.06, 22's, 31's and 31c's reach) |
| D-120 | **The depths (33), added to the round.** The pit's tunnel no longer ends blind: at its end an oak door into the first house (20's), lit, under the land; behind it the same house again, down, in levels: the house, the rooms, the warren, the end and his tunnel. Version 5 of 16's shape, under the pit and inside the pyramid's footprint and box | Mariano: the tunnel at the very bottom should lead to something dangerous that makes you curious while you know that the deeper you go the worse off you are. A known thing where it cannot be is the wrongness, repetition the liminal, a light round a door the curiosity |
| D-121 | **Every level deeper is worse, and shows it**: light (two torches, one a house, none, his tunnel), doors (wood, many, stuffed, iron with nothing to open it), sound (none, far doors, steps beside you, silence), the way back (longer, darker, shut). Each level is a stair down, so the player always knows how deep they are | Dread is knowing; the escalation is the place's structure, not a timer |
| D-122 | **Its answers, 33b to 33e**: a door heard past the tunnel (the lure); doors and steps in other rooms coming nearer; the house's light carried down to the bottom house; the iron door opening for you, shutting behind you, the dark, his glimpse, then silence. Through `Answers`; DESIGN 7 read as 17c's: a door swung is a door moved, a torch carried down a light moved | Mariano's doors that open and close, steps in other rooms and his presence; he darkens a place before he appears |
| D-123 | **Fair danger below** (D-097 for the depths): the danger is vanilla (the dark levels spawn; wooden doors hold them, a zombie breaks one on Hard); no answer opens a door near a monster, takes a player's light or acts in view; every step 1 high; the iron door shuts only on players carrying a pickaxe at 10 health with nobody near its other side; the way down never needs breaking anything | Hardcore: told, a way out, nothing unmarked |
| D-124 | **One seam, one task.** 16's shape calls `world.sc2.s16.deep.Depths` (R3-2 leaves the seam, "The hook"); R3-D owns `deep/**` and raises 16's version to 5; the kit's one piece per start holds; the depths shrink to fit (L2 dropped first, then all) and never cost a pyramid its start; never in the deep dark; v1 to v4 starts keep their dead end (R12-2). Extends D-117 (16 v5) | R3-2 is mid-flight on 16; no kit change; old worlds unchanged |

---

## Findability

### Generation from creation, asleep until Traces (D-108)
- **Generation.** `Snapshot.refusal` drops the stage test; every kind's `fromStage` defaults to 0 (`keepOrNew` from 1, so
  a changed value is kept and still honoured); `LineFeature` the same. The snapshot carries `spawnKnown`: false from
  `SERVER_STARTING` until the overworld's spawn is set (published again at `SERVER_STARTED`); no start and no crumb is
  decided while it is false (the spawn-preparation chunks get none: R0 covers them anyway). Every kind refuses a start
  within `R0` 384 of the world spawn or of the origin (each section's `b`, `keepOrNew`); the crumbs keep 256 clear of
  the spawn.
- **Asleep.** `core.Remains.awake(MinecraftServer)`: the switch on, the story's world gates (`Answers.held`), and the
  world's stage at Traces or later. While asleep: no answer (`Answers.refusal` "asleep"), no 17a record of a player (the
  six are worldgen and stay), no 17g, no 32b or 32c, `Visits` keeps `visited` and `trespassedAt` but fires no
  `REMAINS_ENTERED`, `REMAINS_LEFT` or `ENTERED_TUNNEL` (so entity arms nothing, the director remembers nothing),
  `trespassedAt` is not written, 01+ gives no factor (R3-P), 22, 23 and 31 already need Traces or later.
- **Creative.** `Visits` treats a creative player as a spectator for answers and caps: they are seen as present (view
  rules, "nobody at the mouth") but never inside, never spend a first or a cap, never get a record (D-116).
- **Old worlds.** Chunks generated before this version keep what they have. A 1.5.0 world gets places and crumbs in
  new land at the new densities, whatever its stage.

### Densities and distances (D-109)

| kind | placement, now → round 3 | distance from spawn and origin | other |
| --- | --- | --- | --- |
| 16, 16d | rays 4, first 640, step 640 → **rays 5, first 448, step 512** (jitter 8 degrees, 48 blocks; villages 4 chunks off, kept) | 560 → **384** | sizes {63, 95, 127, 191} (R3-2); apex light 0.6 → 0.75 on land, 1.0 at sea |
| 17 | random spread 20 / 8 → **16 / 6** (trial chambers 4 chunks off, kept; ancient cities 4 chunks off, new: the well reaches the deep dark's depths) | 500 → **384** | |
| 18 | random spread 24 / 10 → **18 / 7** | 700 → **448** | `c` minRise 28 kept (biome first: a refused candidate costs one sample) |
| 19 | four half-axis segments from 1 024 (+0 to 256) → **from 512 (+0 to 256)**, lengths 256 to 768 → **512 to 1 024** | | a marker structure for `/locate` |
| 20 | random spread 36 / 16 → **28 / 12** (villages 6 chunks off, kept) | 700 → **448** | squares 128 first, then 96, then 64; 192 where the land allows (R3-3) |
| 32 crumbs | from every place: up to 3 lines (below) | none within 256 of the spawn | |

R3-0 may move a number inside these bounds if a target misses and says so: 17 spacing 14 to 20, 18 14 to 24, 20 22 to
36, 16 rays 4 to 6, first 384 to 512, step 448 to 640, R0 320 to 448. Rough sums at 1 000 from the spawn (siting's
measured success rates of SC2-H and FIX-SC2-WORLD): about one place per 430 x 430 blocks of land, against one per
about 600 x 600 before (and none at all in the land of the first hours).

### Targets (measured)
`world.SelfCreated3FindTests` when asked (`-Da1016_02.sc3find=true`): worldgen's own decisions (`Decided` over
`Sc2RealNoise`, SC2-H's path), 20 default worlds (bound), 2 large-biome and 2 amplified worlds (reported). A "sign" is a
place's sign point (below), a "crumb" a crumb the line plans and its footing allows.
- **T1 near home.** The nearest place of any kind within 640 of the world spawn in at least 90 % of the worlds,
  median at most 500.
- **T2 per kind.** 16 on land within 800 in at least 85 %; 17 within 640 in 85 %; 20 within 900 in 75 %; 18 within 1 200
  in 60 % of the worlds with a mountain biome within 1 200; 19 from 512 to 768 out on each half-axis (by construction;
  since FIX-R3-E past a place that stands at its near end).
- **T3 flight.** 400 straight flights a world, 1 500 long (2.3 minutes of creative flight), from a random point 384 to
  3 000 from the spawn on a random heading: at least 70 % pass within 128 of a sign; at least 90 % within 128 of a sign
  or within 48 of a crumb.
- **T4 the explorer.** A model player: sessions of 60 minutes from home (the spawn), walking half the time at 4.3
  blocks a second, heading turned by a normal of 25 degrees every 20 s with a 20 % pull outward, home at each session's
  end: within 128 of a sign or 48 of a crumb by the end of session 1, 2, 3 in at least 50, 75, 90 % of 2 000 runs a
  world; within 128 of a sign by session 3 in at least 70 %.
- **T5 lead-in.** Following any crumb of any line toward its place reaches that place's sign, with no gap longer than
  twice the tier's spacing plus 16 (footing failures included): every line of the 20 worlds.
- **T6 not too many.** No two places' boxes within 96 of each other; between 900 and 3 000 from the spawn, 3 to 6
  places per 1 000 x 1 000 of land on average (each stays a journey).

R3-0 reports every number before and after; R3-P folds T4 into "what a player meets".

### The error signs (D-110)
**The grammar** (the kit's `Fault`, R3-0; every place task uses it):
- **The lighting fault.** A chunk-aligned square (x and z on chunk borders, 16 or a multiple) lit as at noon with
  nothing lighting it: `minecraft:light` blocks at level 12 on a 3-block grid one over the surface, inset one from the
  square's edge, waterlogged where the surface is water; measured edge: light at least 10 on 95 % of the square's top
  cells and at most 6 three blocks outside it (R3-0 tunes level and grid to hold it). By day it does not show; by night
  it is the sign. It melts snow and ice (vanilla, over 11): a cold-biome square is also a square without snow by day.
  It stops monsters spawning on itself, never inside. The cells are guarded (SC2-H's `Guard`) and listed with the place,
  so an answer can take them (ledgered).
- **The cut.** Chunk-aligned vertical faces, flat tops at a multiple of 16 in height (a section line), a piece of the
  world missing or standing where it should not: never a face over 3 a player can walk off without a lip or kerb
  (D-097.7, `world.Edges`).
- **Glowstone** only where a block is meant (the archive's lights, the apexes, the crumbs' near tier).

**Each place** (the sign point is where the gallery's `d64` ... `n200` cameras aim; views at 64, 128 and 200 from it on
the side toward the origin, eye 1.7 over the land, noon and 18000):

| place | day 200 | day 128 | day 64 | night 200 | night 128 | night 64 | inside |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 16 land, open | the silhouette (10 / 14 / 20 % of the frame's height for 95 / 127 / 191), the dead ring | the 2-wide cut as a dark slot in the red face; the bare trunks | the cut, and a chunk-aligned 16-wide strip of the face round it set back one block (two chunks that disagree) | the apex light (3 x 3) and a lit square at the foot | the strip lit as at noon, the cut black in it | the cut black, the core black behind it | dark, as now |
| 16 sealed | as open | the set-back strip, no cut | the strip | as open | the lit strip on unbroken brick: "here" | | (dig: `DUG_INTO_PYRAMID`) |
| 16d | the stepped top over the sea | the stepped top, the waterline way in (v2) | the way in, 2 x 2, at the waterline | the apex light (always at sea) and a lit square of sea | the lit water, the black mouth | | dark |
| 17 | the well's top: a 32-wide block of cobblestone 20 to 31 over the land, cut flat at a multiple of 16, chunk-aligned, its face toward the origin missing | that face as a section: gallery floors and rails as lines, the dark under the land line | shelves, rails, the top galleries' glowstone, the ground gallery's sill at the land | a square grid of points standing over the land (the top galleries' glowstone through the open face), a glow over the open top | the lit galleries in section, black under the land line | the ground gallery lit, the void black under it | lit at the top (the record room's light, D-094); dark from the land line down; one lit gallery far down; black water at the bottom |
| 17 notch (kept) | | | R11-12's notch and dead ground | the lintel glowstone, a point at about 100 (R10-6) | | | the annex as now |
| 18 | the summit cut flat: a square shoulder in the skyline (the peak's top 8 to 14 removed in the chunk-aligned 16 x 16 holding the highest land) | the cut's square edges; the window's dark square on its cliff | the way in at the foot inside a chunk-aligned 16 x 16 of dead slope (coarse dirt and gravel, plants and snow removed) | a lit square at the mountain's foot | the black 2 x 2 in the lit square | the 2 x 2 black, nothing lit past it | dark; overhead, the piece lit from under, the one lit thing |
| 19 | the wall (ribs to the top, panels to the horizon band) | the 2 x 2 holes every 48 | a hole | a dotted line of lit squares running straight to the horizon: at every hole, 8 x 4 lit on each side of the wall | lit pairs, the black wall between them hiding the stars | the hole black between two lit patches | (none) |
| 20 | the flat square and its chunk-aligned cliffs (from a rise; from the low side its kerb) | the house on the flat | the doorway toward the origin | the whole floor lit as at noon: a pale perfect square in dark hills (the flat test world, lit for nobody) | the lit floor, the animals on it, the house dark but for its two torches | the windows' light, the doorway | two torches (20b v2 takes them) |

Bar for each (the art director's): at 200 by day the sign alone says "that is not right" in a second without the
camera being told where to look (the scale figure in frame); at 200 by night the lighting fault is the brightest thing
in the frame that is not the moon; at 64 the way in is unmistakable and still reads as a mistake in the world, never
as a door made for players; under Iris and Complementary (`-Pgallery.iris`) the night shots still show the fault (block
light, D-102).

### `/locate` (D-112)
- 17, 18, 20: vanilla's random-spread search, once D-108 lifts the stage gate (its `StructureCheck` asks our siting
  for chunks not yet generated and reads the saved starts of chunks that are). Tested in a fresh world under Traces.
- 16, 16d: `world.mixin.ChunkGeneratorLocateMixin` at the return of `findNearestMapStructure`: for each requested
  structure whose placement is the kit's (`RaysPlacement`, `AxesPlacement`), the slots nearest first within the asked
  radius (chunks), each checked with vanilla's private `getStructureGeneratingAt` (an `@Invoker`), the nearer of
  vanilla's answer and ours returned; the `skipKnown` flag honoured.
- 19: a marker structure `world/s19` (structure registry; the feature keeps its id in its own registry) on a new
  placement `world/sc2a` (`AxesPlacement`: one placement chunk at each planned segment's middle, from `LinePlan`); its
  start writes nothing (an empty piece, a 1-block box at the ground) and records nothing; the mixin finds it.
- Cost: a check runs a candidate's siting on the server thread once (then `Decided`); `/locate a1016_02:world/s18` may
  take a few seconds in mountains (the hollow's 0.3 s a made start, SC2-H). A command, not the game.
- Spoilers: the ids are the opaque `sNN`; the command prints coordinates, as vanilla's.

### `Decided` (the kit)
One record per (kind, candidate chunk) per world: refused, or made (anchor, entrance and facing, sign point, box,
variant, version). Asked by the start itself (`Sc2Structure.findGenerationPoint`), by the crumbs (any worldgen thread)
and by `/locate`; computed once by the kind's siting, kept in a concurrent map, drained into `Sc2Data` (key `d`,
lenient, at most 16 bytes a refused candidate and 64 a made one) every tick and at stop (as `Recorder` and `Plans`).
A decision made with one config is kept when the config changes. `Remains.predicted(level, pos, radius, kinds)` reads
it (places decided and not generated yet), for crumbs, for `/locate` and for 22 and 23 if R3-P asks.

---

## 32 The crumbs (D-111; feature `world/s32`; `sections.sc32`)

- **Experience.** Out past your first hills, a bookshelf standing alone in a field. Odd; maybe someone's. A few
  minutes on, another, and you notice it is in line with the first. Then a pillar of cobblestone three high with a
  bookshelf on top, exactly in line, and another thirty-two blocks on, and the line does not bend for a hill. At night
  the pillars past the next rise end in glowstone, a dotted line of light no village makes. Then the ground has 2 x 2
  holes in it, each lit at the bottom, every sixteen blocks, and in front of you a square block of cobblestone stands out
  of the land, its near face missing, shelves inside it going down. Elsewhere it is bricks: a brick alone on the grass,
  then a brick step the size of a toy pyramid, then a pyramid you could climb, then the pyramid.
- **Why scary.** You did not decide to go there; you were walked there. The crumbs are from the first versions of the
  game (a dirt pillar, a bookshelf, a brick, a little plank house), things a 2010 player built, standing in today's
  world. Each one is deniable; the line is not.
- **Deniable.** Far off, yes: a player's marker, an odd build (the base mod's lone lights and abandoned builds look
  alike). Near, no: nobody builds a dead-straight line of glowstone across 400 blocks.
- **Guardrails.** Put in at worldgen (D-094: the developer's way markers to his test builds, made from each build's
  own blocks, from before F3 had coordinates; nobody says so). At runtime he only removes (32b) and moves (32c).
  Never a wall torch on a right-hand wall and never a line that ends at a hole (that grammar is t0rch's, 11 and 11+).
  Never the shrine (a redstone torch on mossy cobblestone).

### Lines
- From every place (16, 16d, 17, 18, 20; 19 below) whose start `Decided` made: up to `a` 3 lines from its sign point.
  Line A toward the world spawn (pinned in the decision), stopping `b` 256 short of the spawn or at `c` 640 long; lines B
  and C at plus and minus 100 to 140 degrees from A (a hash of the place's seed), `c` 640 long at most; a line stops at
  another place's box (that place's lines take over) and is dropped if under 128 long.
- 19: from each segment's gap, one line each side of the wall toward the spawn's side and away, 512 long.
- **Tiers**, by the distance `d` from the sign along the line:

  | tier | `d` | spacing | across the line | how wrong |
  | --- | --- | --- | --- | --- |
  | far | 384 to 640 (`h` to `c`) | `f` 56 ± 8 | up to ± 6 | one old block where it should not be; at night a redstone torch on every other one (a dim red point at about 64) |
  | mid | 192 to 384 (`g` to `h`) | `e` 32 exactly | none: dead straight | a small built thing; every third crowned with glowstone (seen at night from 150) |
  | near | 64 to 192 (`g`) | `d` 16 exactly | none | his marks: lit, cut, dead ground round each (seen by day from 128, at night from 200) |
  | sign | under 64 | | | the place's error sign (above) |

- **What they are** (each place's own blocks; R3-C builds them in `world.sc2.s32`, not in the places' packages):

  | place | far | mid | near |
  | --- | --- | --- | --- |
  | 16 | one brick block on the ground | a brick step 3 x 3 with one brick on it (a pyramid two high) | a brick pyramid 5 x 5, three high, glowstone at its top, a 7 x 7 of dead ground round it |
  | 16d | a 1 x 1 cobblestone pillar from the sea floor to the surface, one brick over the water | the pillar with a 3 x 3 brick step at the surface | the pillar's top glowstone, a 5 x 5 lit square of sea round it |
  | 17 | a bookshelf alone on the ground | a 1 x 1 cobblestone pillar three high with a bookshelf on top | a 2 x 2 hole two deep in a 4 x 4 of cobblestone flush with the ground, its floor glowstone (his 2 x 2, lit) |
  | 18 | a bare trunk of the biome's wood, 3 to 5 logs, no leaves | a 1 x 1 pillar of the land's own stone five high, cut square | a 3 x 3 x 3 cube of the land's stone set on the ground, glowstone in its top's middle (a piece taken out of the mountain) |
  | 19 | an obsidian block on the ground | an obsidian post two high | an obsidian post three high, glowstone on top |
  | 20 | a dirt pillar three high with a torch on top (the first night's pillar) | a 3 x 3 of grass raised one block and dead flat, a cobblestone block with a torch in its middle (a test floor in miniature) | a 3 x 3 oak plank hut two high, one glass block, an open doorway toward the place, a torch inside (the first house in miniature) |

  At night the far tier's redstone torch stands on every other far crumb (on top, or beside it on the ground for a
  trunk or a pillar); 20's far and mid use plain torches (the house's light), 19's far has none.
- **Footing and fairness.** A crumb's point moves up to 4 along the line to find footing (live blocks in the
  decoration region): solid ground under it; no drop over 3 within 2 of it; no lava within 8; no water deeper than 1
  except the sea form's pillars (and 16d's and any line's crumb over open water uses the pillar form, mid and near tiers
  only; far ones over water are skipped); not on leaves (in a forest it stands on the ground and the leaves over it,
  a 3 x 3 column to the sky, are removed: a lit hole in the canopy at night); never inside another structure's box
  (villages, trial chambers, the base mod's scars), never within 256 of the spawn, never in a chunk generated before
  this version. A point with no footing is skipped (T5 bounds the gaps). A line never leads to an unmarked death: it is
  points, never a path across a drop, and the near tier stands on the place's own approach.
- **Worldgen.** A placed feature at `top_layer_modification` (after every other decoration, as the line), biome
  modification over the overworld; in each chunk: the lines that cross it (from `Decided` places within 640 + 64,
  through a per-region index), their crumbs in this chunk, each written only inside the chunk (a crumb straddling a
  border is moved to fit), its cells guarded (SC2-H's `Guard`). A line's layout (place, bearing, length, tier table,
  version) is fixed the first time any chunk builds it (`Sc2Data` key `c`, lenient, as `Plans`), so a later version of
  the crumbs never mixes into a half-built line.
- **The switch off.** No new crumbs; generated ones stay (D-093); 32b and 32c stop and give back what is loaded.

### 32b It was right here (runtime, multiplayer)
A participant "sees" a crumb when it is within 32, in their view cone and line of sight (sampled every 20 ticks
against the crumbs of lines within 48 of them, a chunk-grid index: O(1)). When a participant who has not seen a crumb
comes within `j` 64 of it, while everyone who has seen it is at least `i` 96 away and it is out of everyone's view:
the crumb is removed (`TraceService` batch, `world:s32/gone`, not dropped). Once per line, at most one a day per world,
asleep never. "It was a bookshelf on a cobble pillar, right here." The rest of the line still leads in. The switch
and Ending D give it back.

### 32c It came closer (runtime, a move)
A participant who saw 2 or more crumbs of one line in one in-game day and then did not follow it (the next 10 minutes
of their play took them 160 or more farther from its place than the nearest crumb they saw, or they went home): that
night, the line's farthest remaining far crumb is moved (`TraceService.move`, every block of it, `world:s32/pull`) to a
spot `k` 96 to `l` 160 from that player's base, on the straight line from the base toward the nearest crumb they saw,
out of everyone's view, on clear footing, never within 8 of a block a player placed, never within `n` 64 of any base.
The next such night the next far crumb goes 64 farther out on the same line, so a spur grows from near home toward the
line. At most one a day per player, `m` 4 per line; material conserved (moved, never made). The switch and Ending D give
each back. Asleep never.

### Multiplayer
World objects: both friends see and follow the same crumbs. 32b turns one friend's crumb into nothing for the other
(the D-098 doubt: "it was here"; the screenshot shows it). 32c is the player's: the spur points from their base (a
shared base: both see it).

### Config `sc32` (v1; keys `a` to `n`)
`a` linesPerPlace 3, `b` spawnClear 256, `c` lineMax 640, `d` nearSpacing 16, `e` midSpacing 32, `f` farSpacing 56, `g`
nearTo 192, `h` midTo 384, `i` goneSeerAway 96, `j` goneNewcomerNear 64, `k` pullMin 96, `l` pullMax 160, `m` pullPerLine
4, `n` pullBaseClear 64.

### Build, tests, gallery
- **Build.** `world.sc2.s32`: `CrumbLines` (from `Decided`; the per-region index), `CrumbShapes` (the table above, by
  kind and tier), `CrumbFeature`, `Sightings` (the seen-crumbs sampler), `Gone` (32b), `Pull` (32c), `Sc32Config`,
  `/a1016 sc 32` (the nearest line: its place's kind number, distance, crumbs seen; `sc 32 a` runs 32b and 32c's checks
  now, rules kept); data `worldgen/feature/world/s32.json`, `placed_feature/world/s32.json`; exhibits.
- **Tests** (`world.SelfCreated3CrumbTests`). Lines per place (count, bearings, lengths, spawn clearance, stopping at
  another place); tier spacing and straightness; each kind's crumb blocks per tier; footing (no drop over 3 within 2, no
  lava within 8, no deep water but the pillar form, the canopy hole); never in another structure's box, near the spawn
  or in an old chunk; a line crossing six chunks built by worldgen equals the same line built live; a chunk's decoration
  writes only in that chunk; the line's plan fixed once (a later version does not change a begun line); T5 on the real
  noise (when asked, with R3-0's find run); 32b with two mock players (seer and newcomer: removed; with the seer near:
  not; given back by the switch); 32c (moved, block count conserved, never near a base or a placed block, the spur's
  second step, the caps); asleep refuses both; nothing at INFO or above names a position or a word of the crumbs.
- **Gallery.** Exhibits `32-16`, `32-16d`, `32-17`, `32-18`, `32-19`, `32-20` (one per place kind; never `32b` or `32c`,
  which name the runtime rules), each a 700-long line on `GalleryProps` terrain (a hill, a forest strip, a stream) ending at a
  stub sign: `d48`, `d96`, `d160` and `n48`, `n96`, `n160` from beside the far, mid and near tiers looking along the
  line; `along_day`, `along_night` (from a far crumb: the next ones in line); `aerial` (the tiers from 160 up); `canopy`
  (the forest crumb from above at night). Must show: the far crumb deniable alone, the line legible from the mid tier,
  the near tier unmistakable, every crumb a 2010 thing.

---

## The archive v3: the stacks (17; D-113, D-114)

### Shape (version 3; R3-1)
- **The hall, three times taller.** Interior 25 wide, 63 long (95 at `c` 0.25), **41 high** (17 before). The stacks
  rise 27 (rows 8 deep, 2 thick, every 6, aisles 4, a nave 9 wide, as v2) with walkways at 9, 18 and 27 (planks, oak
  fence on every open edge, continuous; ladders at the row ends as v2). The wall shelves go floor to ceiling, 41. **The
  light stays where the old ceiling was:** single glowstone blocks on the v2 grid (4 x 4, 6 a row) floating at 17 over
  the nave and aisles, held by nothing, and nothing lit above them: the shelves rise past the light into the dark (light
  from the grid reaches about 30 up; the top 10 of the walls and the ceiling are never lit). The theft stays as R11-6
  made it at reach and on the first deck; above 27, the stacks are full (books nobody could reach).
- **The well.** Beside the hall's entry wall, chunk-aligned: outer 32 x 32 (two chunks by two), interior 24 x 24, a
  cobblestone shell 2 thick (4 at the land line). Galleries every 6 (floor, then 5 clear) round all four walls, 3 wide,
  oak plank floors, oak fence continuous on the void side; the void 18 x 18 in the middle. The outer walls bookshelves
  floor to gallery ceiling, 2 chiseled shelves at reach on each wall of each gallery (8 a gallery, 48 slots: the day
  books). The descent: one flight of oak stairs per gallery along the wall, each step 1 high, the flights alternating
  sides so the way down turns round the void. From the bottom: water 3 deep over the whole void and the lowest
  gallery's floor 1 over it, a ladder down into it, its surface at the world's lowest worldgen block + 10 (Y -54 in the
  default world; `Heights.lowest` + 10 in any world, the shell kept 6 over it), to the top: the first multiple of 16 at
  least 20 over the highest land in the well's footprint, cut flat, open to the sky. The hall's floor is one gallery's
  floor; a doorway 3 wide, 4 high joins them through the entry wall beside the annex's door.
- **The well's top (its sign).** The face of the shell toward the origin is missing from the top down to the ground
  gallery (the gallery nearest the land on that side): the galleries above the land stand open in section, their floors
  ending at the cut, no rail there (unreachable from the ground gallery: no flight goes up past it; climbing there is
  the player's choice, R13-8). The land in front of the ground gallery is cut or filled flat for 7 at its floor (a kerb
  where it falls away, a lip where it rises, D-097.7). Everything else of the shell above the land stays.
- **Light in the well.** Glowstone in the gallery ceilings every 6 along the ring on the 6 galleries from the top down;
  every other gallery from there to the land line; none under the land line but **one gallery two thirds of the way
  down, fully lit** (the lure: a lit ring in the black far below); the water black. Everything lit to 8 at generation
  stays as v2 for the hall and the annex; the well under the land line is dark by design (it fills with what the dark
  fills with; the rails keep everyone on the galleries).
- **Fair danger.** No fall a player can walk into: every void edge railed, every step 1 high; a fall past a rail (a
  broken fence, a jump from a pillar) lands in water 3 deep (D-097.7: "without water at the bottom", kept); the pool has a
  ladder out. Darkness from 17b and 17f never falls on the player's own row or gallery or the next one toward the way
  out. Mobs fallen into the pool drown into drowned (vanilla); that is the well.
- **Siting.** As v2 (land biomes, the depth rules, SC2-H's dry ways), plus: the well's column from the pool to the cut
  must be clear of the deep dark (its biome tested at the pool, the land line and halfway) and of an ancient city (4
  chunks); the hall's floor between Y -24 and 40 as before, its ceiling (41 up) at least 8 under the land at every
  corner; the whole box within the kit's `MAX_REACH` (8 chunks of the start; if not, the annex shrinks to 5 x 3 with the
  long hall, else no start); the well's top at most `Heights.highest` - 6.
- **Kept from v2.** The annex (cells, loops, details, infested walls), the dais and the records wall, his bay, the
  notch way in with its dead ground and lintel glowstone, the two sealed ways (17c), 11+'s breach.

### Answers (17)
- **17b** (lights out) as v2 over the floating grid: a row of 6 floating glowstone at a time, behind the player toward
  them; the A shots (`17n`'s `in_hall`, `in_dark`) must be at least as strong (the art director compares v2 and v3).
- **17f The well goes dark from above (new).** A participant at least `t` 3 galleries under the ground gallery for `u`
  60 s: out of everyone's view, the well's glowstone is removed gallery by gallery from the top down, one every `v` 160
  ticks, stopping 2 galleries above the highest participant in the well (never their gallery or the one above them);
  at most 64 glowstone a visit, once per in-game day per archive (`world:s17/well`, ledgered, not dropped). Looking up,
  the lit shaft goes black from the top: the way out is up, in the dark. After `w` 3 galleries dark: `Figure.glimpse`
  on a dark gallery across the void, 1 to 3 levels up, 20 to 30 off (D-105). The dark galleries then spawn. Every
  answer rule holds (out of view, firsts, quiets, the health floor 10, awake, never to creative).
- **17g Your record, rewritten (new; a rule like 17a).** During a visit, once a player has stood within 6 of the records
  wall and then gone 24 or more from it with the wall out of everyone's view: their record on the wall is replaced by a
  new edition whose last line is about this visit (lines `r`), and the replaced one is moved up into the stacks (an
  edition, below). If they took their record with them, the new one is left in its place (17a's rule). Once per visit,
  awake only, never in their first hour, never to creative. Left by others' category as 17a (`world:left/s17/record`,
  never undone); the moved edition `world:left/s17/edition`.
- 17a, 17c, 17d, 17e, 11+, 31b: as built.

### The books (D-114)
**What exists, where, how many.**

| books | where | how many | text |
| --- | --- | --- | --- |
| **Records of the six** (the list's names) | the dais wall's middle rows (v2) | 6 | worldgen: the six fixed texts (`u`), one of three variants each by the world's seed |
| **Records of the players** | the dais wall, after the six | one per known player per archive (17a), rewritten each visit (17g) | composed from what the mod knows about that player (below); "by" the player's own name, generation original |
| **Editions** | the stacks above the first deck, nearest the dais first | one per (player, visit), at most `x` 16 per player per archive (the oldest moves out of view to the far end's highest shelf when full) | the record as it stood; generation tattered (vanilla prints "Tattered" under the title) |
| **Day books** | the well's galleries: the top gallery days 1 to 48, the next 49 to 96, on down | one per in-game day of the world, 48 a gallery (24 galleries hold 1 152 days) | past days: the world's day log; today: unfinished (its last line breaks off); days to come: titled, blank, down into the dark |
| Everything else | | | empty chiseled slots (the theft) and plain bookshelves |

The day books are left at runtime, never in worldgen: when a participant is in the well, the shelves of the galleries
within 2 of theirs (and the top gallery) get their volumes out of everyone's view (`TraceService.leaveStack`,
`world:left/s17/day`, never undone), idempotent by title; a past day's volume written before its day ended is replaced
by the full one at the next visit. Nothing is placed while asleep.

**What the mod knows** (R3-B's `Facts`, an immutable bag built on the server thread when a book is written; only
in-game data, never a real time, a real date, a file name, an operating-system name or anything outside the world):
- names: the known players (`HerobrineState`), lowercase as written in the books;
- days: the world's day, the player's first day, the days they played (`PlayerWatch` visits), days away;
- deaths: marked deaths (`DeathMarker`, the list's cause words) and vanilla deaths (the damage type, mapped to a short
  word by the texts file);
- vanilla statistics (`ServerStatsCounter`, online players; a small snapshot kept at logout for offline ones): blocks
  mined by block, distance walked, jumps, mob kills, times slept, time since last death;
- firsts: the first block, crafting table and chest with their places (`HerobrineState.FirstKind`);
- home: `PlayerWatch.base`, its coordinates;
- recent actions: `PlayerWatch.recentActions` (mining bursts, doors used, containers opened, with places and ticks): a
  door or chest at a friend's base is a fact about two players;
- his traces: what the ledger took from that player (`TRACED` with `placedByPlayer`: torches, blocks, stacks; counts per
  day);
- his places: `Remains.visited` per kind, the day of the first visit;
- the day log (new, R3-B's `DayLog`, its own saved data `a1016_02_s2d`, lenient): per in-game day the weather (clear,
  rain, thunder, snow), the moon's phase, a fog night (if atmosphere's state says so), who was online (indices into the
  known players), deaths per player (vanilla and marked), his traces that day (counts by kind), places entered (kinds),
  the stage at the day's end; at most 48 bytes a day, the last 1 024 days; days before the log existed read as "not
  kept".

**How a player's record is made** (R3-B's `Composer`): the name; an opening (`a`); their first day (`b`); 6 to 12
entries, oldest first, each "day N." and a line from the kind its fact calls for (`c` to `n`), the most telling facts
first (a death over a door, a friend over a stat), never two of one kind in a row; exactly one wrong line (`p`) among
them in an edition in two (`y` 0.5): something calmly untrue (they "did not come" on a day they played, they "stood at
the window all night"), at most one in a book naming a friend, never a death; then a closing (`q`) or, in 17g's edition,
the visit line (`r`). Pages: at most 13 lines of 19 characters, wrapped at spaces, at most 16 pages; title the name (32
characters at most); author the subject's own name ("by" them: every text in the world was written by a human, the
book claims); a book is never longer than vanilla allows. A day book: "day N" as title, author empty (vanilla prints
"by" alone), lines `s` by what the log holds; a day nobody came is one line. Today's breaks off mid-line (`t`).

**Variation.** Every pick is a hash of (world seed, subject's UUID or the day, book kind, edition, slot), so the same
facts read differently in two worlds and two editions differ; no line twice in one book; an opening or closing not
reused in a player's next 6 editions; slot words (blocks, items, biomes) from registry ids, spaces for underscores.

**Multiplayer: whose books.** Every known player's record is on the same wall, and anyone may read anyone's. A friend's
record names you where the facts do ("day 12. lalo was at mariano's door. mariano was down in the mine."): true facts
that read as an accusation; the one wrong line may name a friend once a book. A record taken away keeps its text; the
wall's next edition differs from the one in your chest (D-098: records that disagree). Day books name everyone who
played that day. A friend who joins late gets their record at their first visit (17a) and their days from then.

**Spoilers.** The texts are in-game content and are read in game: fine. Nothing of them reaches a log at any level, a
command's output (`sc 17 r` prints counts and lengths, never text), the config or a file name. The texts file is
`data/a1016_02/world/sc2/b17.json` (opaque path, exempt from `VerifyScSpoilerTests`' word test by that path only, as
in-game text; the writer still never names a place by what it is).

**The voice** (R3-W writes it; R3-B's validator checks the mechanical rules). The layer's front page, in game: terse,
lowercase, wrong, calm. Short lines, plain words, periods; no question marks, exclamation marks, capitals, emoji, jokes,
memes, swearing, gore or threats. Third person by name; "i" at most once in a book, never explaining itself; "you"
never. He never names himself, never writes "herobrine" or any name of his, never mentions a brother, the team's
reasons, the endings, the fragments' secrets (`docs/DESIGN.md`, "What the player can learn, and what they never
will"; `docs/STORY_CLUES.md`), the mod, the jar, a computer, a real place or time. He records; he does not explain, warn
or plead. Facts are stated flat and a little wrong ("fell. got up somewhere else."). His places are named by their
look, never by a word on the spoiler list ("the brick one", "the room with the books under the hill", "the mountain
with nothing in it", "the black line", "the flat place with the house"). The six's records keep to what DESIGN's
fragments already say about them (kirbyfan_92: the fog, the tree line, singleplayer; stoneslab: the hill, the diary's
days; dyl4n_m: the house; oreo_tm: white eyes, a cave; pk_alpha and t0rch: gone; t0rch's torches on the right-hand wall),
never a new fact about how they died. A tone sample (not to be reused verbatim): "mariano." · "came on day 1. it was
raining." · "day 4. dug 312 stone. none of it was needed." · "day 9. lalo opened mariano's chest. took nothing." ·
"day 11. fell. got up somewhere else." · "day 12. did not come." · "kept." · "mariano is here now." · "day 30. nobody
came."

**The texts file** (schema; R3-W fills it, R3-B reads it):
```json
{
  "v": 1,
  "l": {
    "a": [ { "t": "{name}.", "w": 1 }, ... ],
    "c": [ { "t": "day {day}. fell. got up somewhere else.", "if": ["cause:fall"] }, ... ],
    ...
  },
  "u": { "kirbyfan_92": [ ["page", "page"], ["variant two page"], ["variant three page"] ], ... },
  "map": { "cause": { "fall": "fell", "drown": "the water", ... }, "place": { "16": ["the brick one", ...], ... },
           "weather": { ... }, "moon": { ... } }
}
```
`t` a line (slots `{name}`, `{friend}`, `{day}`, `{n}`, `{block}`, `{item}`, `{biome}`, `{x}`, `{z}`, `{cause}`,
`{place}`, `{w}`, `{moon}`), `w` a weight (default 1), `if` conditions the fact must meet (`cause:<word>`,
`friend`, `n>=<k>`, `night`, `first`, `rain`, `alone`, `away>=<days>`), `u` the six (three variants each, 2 to 4 pages).
Minimum pools (lines): `a` openings 30, `b` first day 15, `c` a death 30, `d` a marked death 12, `e` mining 20, `f`
building and firsts 20, `g` home 20, `h` a night out 15, `i` doors and chests 20, `j` a friend 30, `k` what he took 20,
`l` a place visited 20 (4 a kind), `m` statistics 25, `n` days away 15, `p` wrong lines 30, `q` closings 30, `r` the
visit 20, `s` day books 80 (weather and moon 20, who came 15, deaths 15, his traces 10, places 10, nobody came 10), `t`
today 15, `x` edition notes 10; about 500 lines, enough that a world's records do not repeat a line in a hundred days
of two players.

---

## The liminal pass (D-115)

### 16 The pyramid (version 4; R3-2)
- **191.** A fourth size, base 191, 96 high (the shell, core, pit and dead end as the others; the pit 88 deep at most,
  else the 127), weights `d` {0.05, 0.35, 0.35, 0.25} for 63, 95, 127, 191, the largest the land allows (its 7 x 7 ground
  samples, SC2-H's foundation rule); its grounds out to B/2 + 32 (within `MAX_REACH`). From 200 it is a wall to the sky.
- **The sign.** The set-back strip and the lighting fault (table above) round the way in, on sealed ones too; the apex
  light `f` 0.6 to 0.75 (31 keeps to unlit apexes).
- Kept: the dead ring, the bare trunks, the cut, the pit and its dead end, 16b, 16c (R10 and R11's strongest).

### 16d The sea pyramid (version 4 of 16's shape; R3-2; R11-18)
- **A way in at the waterline.** On the face toward the origin, a 2 x 2 with its sill one over the sea's surface, into a
  dry dark core (the sea form's core as v3); inside, a 2-wide flight down the inner face to the core's floor on the sea
  bed, every step 1 high (fair: no drop, no water in). 16b applies (the way in closes; a pickaxe, 10 health). The apex
  light always; the lit square of sea at the way in.

### 18 The hollow (version 3; R3-3)
- **The summit cut.** In the chunk-aligned 16 x 16 square holding the highest land over the hollow, every column above
  `top - m` (`m` 8 to 14, the shell's top kept at least `d` 7 over the cavity under it) is removed: the peak cut off
  flat, a square shoulder in the skyline. Where the land round the square rises more than 3 over the cut's floor the cut
  stops (no walls to fall from; D-097.7 by `world.Edges`). Snow layers and powder snow left on the flat as weather does.
- **The piece, hanging.** The 16 x 16 x `m` cut from the summit, its own layers (snow, grass, stone), set inside the
  cavity upside down, floating, unsupported, its grass face down, under the summit 16 to 24 under the cavity's ceiling
  (or the cavity's middle height where the ceiling is lower), with the lighting fault under it (light blocks on its
  underside's cells, level 12): from the floor, a lit square of grass overhead in the black, 40 to 80 up. The one lit
  thing (R11-8's focal point). No way onto it.
- **The foot.** The way in inside a chunk-aligned 16 x 16 of dead slope and its lighting fault.
- **18c The piece goes dark (new answer).** A participant inside within 48 of the grove for `n` 60 s: out of everyone's
  view, the piece's light blocks are removed in four quarters 5 s apart (`world:s18/dark`, ledgered); once per in-game
  day per hollow; then 31c may come (its card, its own rules; the trunks are in the dark already). Every answer rule.
- Kept: the grove, the pillars, the window and its landing (R11-8, R12-1), 18b, 31c (A).

### 19 The line (version 3; R3-2)
- Nearer and longer (`b` 512, `d` 512, `e` 1 024; the table). **The lit foot:** at every hole, the lighting fault 8 x 4
  on each side of the wall at the ground (water: waterlogged), so at night a dotted line of lit squares runs straight
  to the horizon. The marker structure for `/locate` (R3-0 builds it; R3-2 keeps it in step with `LinePlan`).
- Kept: ribs to the top, panels to the horizon band, holes every 48, the 9-wide gap, plain obsidian (R10-17).
- Clear of the places (FIX-R3-E): a segment ends short of the first place in its way (`LinePlan.CLEARANCE` 32 from its
  box), laid out again over what is left; below.

### 20 The floor (version 3; R3-3)
- **Fullbright.** The lighting fault over the whole square: at night a pale perfect square of lit grass in dark hills;
  the cows on it (spawn overrides as v2); no monster spawns on it while lit.
- **Bigger.** Squares 128 first, then 96, then 64; **192** where the land's relief and the cost allow (12 x 12 chunks, a
  64-high cut at most; every edge kerbed or lipped as v2).
- **20b v2 (R10-16).** At night, a participant within `d` 32 of the house: out of everyone's view, the house's two
  torches are gone (`world:s20/dark`, ledgered), then the floor's light goes out chunk by chunk from the far edges toward
  the house, one chunk every 4 s, never the chunk the player stands in or the one between them and the nearest walkable
  edge, at most 64 light blocks a visit; the animals turn to the dark doorway for `e` 20 s (`MobTamper.face`, silent);
  after 4 chunks dark: `Figure.glimpse` in the doorway, 20 to 40 off (D-105). Then the floor spawns what dark grass
  spawns. Once per player per in-game day, firsts and quiets as every answer, health 10, never to creative.
- Kept: the first house (R11-5's proportions and windows), the cut cliffs and the course of bedrock.

### 17
The stacks, above.

## Answers inside: the review (D-116)

| place | the answers | verdict | round 3 |
| --- | --- | --- | --- |
| 16 | 16b way in closes; 16c torches go, the glimpse in the pit | strong (R10) | kept |
| 16d | none | weak: nothing to enter | the waterline way in, 16b |
| 17 | 17a records, 17b lights out (A), 17c the other door, 17d the marker moves, 17e your book, 31b | strong | + 17f the well goes dark from above, + 17g your record rewritten |
| 18 | 18b way in closes; 31c among the trunks (A) | good, but the dark has nothing to take | + 18c the piece goes dark before the trunks |
| 19 | none | a world object; fine | the lit foot only |
| 20 | 20b the cows face the house | weak (R10-16) | 20b v2: the dark comes in, a glimpse in the doorway |
| all | spent by creative visitors | wrong | creative never inside (D-116) |

---

## 33 The depths (addendum; D-120 to D-124; 16's shape version 5; `sections.sc33`; R3-D)

Added after wave 2 began. Mariano, on 16's pit (translated): the tunnel at the very bottom should lead to something:
something dangerous, a room, a labyrinth, something that makes you curious while you know that the deeper you go the
worse off you are, like someone who finds a cult and keeps investigating out of fear; doors that open and close, steps
in other rooms, something you know is terribly wrong, maybe his presence.

### Experience
At the bottom of the pit the dead end is not a dead end any more: at the end of the 2 x 2 tunnel, in your torchlight, a
plank wall and an oak door, and round the door a little light. Behind it, the first house, the one on the test floor
(20), the one the first versions put you in: whole, its two torches burning, its glass windows looking onto stone, eighty
blocks under the land. Its back wall has a door the floor's house never had. Steps go down from it to the same house
again, and through its door another, and another: a grid of them, each lit by one torch, every door shut, windows
looking into rooms looking into rooms. Further down, the same houses with no light at all, more doors than you can keep
track of, doorways stuffed with cobblestone and dirt from the inside. Behind one of them, broken through from outside,
someone's crafting table, a cold furnace, a strip of white wool on the floor, a chest with a worn stone pickaxe, bread, a
compass, and no torch. At the warren's far corner the last steps end at an iron door with nothing to open it. Later,
somewhere in the dark rooms, an iron door is heard opening. Beyond it, the house once more, lit exactly as the first one
was; the first one, when you go back up, is dark. The bottom house's back wall is gone; from the gap one of his 2 x 2 tunnels runs on
through the rock, back under the rooms toward where the world began, and near its blind end a single cobblestone stands
in the middle of the floor, like the one at the bottom of the pit, with nothing on it.

### Why scary
- **Curiosity first.** A door and a light where there was a wall. Then the wrongness of a known thing where it cannot
  be (the house, its windows onto stone), repetition (the house again and again, too many, too regular), and someone
  else's fear set down in blocks: the stuffed doorways, the shelter broken into, no light left. Nothing is explained.
- **The deeper, the worse, and you know it** (D-121). Every level is a stair down, and every level has less light, more
  doors, nearer sounds. The way back is everything you passed, now darker and shut.
- **His presence before his face.** The doors and the steps come nearer room by room; the light leaves the top and turns
  up at the bottom (he darkens a place before he appears); once the iron door shuts behind you, the depths go silent.

### Deniable
Not the place (D-092). The answers, the first time: doors bang near zombies; "an echo"; "my friend"; "I left that door
open"; the bottom house was always lit, you misremember. Not the second time.

### Guardrails
Put in (D-094): the houses are the developer's test house copied below his test pyramid, the iron door his (never wired
to anything); the camp and the stuffed doorways a person's (DESIGN 7: builds and lights were left by others, as t0rch's
torches). His pass, all removals: the end house's back wall, his tunnel, the torch gone from the last cobblestone, the
doors gone from some doorways. At runtime he only removes and moves (D-122): a door swung is a door moved (17c's other
door is the precedent), the house's two torches carried down, the end house's taken. Nothing spawned, no text anywhere
(DESIGN 2; D-114's bend stays the archive's), no redstone, no shrine; vanilla blocks, sounds and mobs only.

### The shape (16 v5; land pyramids with a pit, open or sealed)
The frame is the tunnel's: its end (`PyramidShape.tunnelEnd()`, the hook below), the facing (toward the way in's side,
which faces the origin), the right. Level 1's feet are the tunnel's floor (`pitBottomY()`); each next level's feet 8 lower.
Stairs between levels: 8 steps each exactly 1 high, 1 wide and 2 high (3 high at a turn's landing), raw stone sides,
unlit, routed by the plan (they turn where they must).
- **The door.** The tunnel's end face (the 2 x 2 at `f = TUNNEL_LENGTH + 2`) becomes the house's front wall: an oak door,
  closed, in the side-0 cell, its lower half at the tunnel's feet; the side-1 cell and the cells over both are the
  house's wall (cobblestone course at the foot, planks over). The tunnel otherwise exactly as v4.
- **L1, the house.** 20's first house exactly (R11-5's proportions: 7 x 7 outside, a cobblestone course, two plank
  courses, the flat plank roof, a glass pane in the middle of each side wall, the mossy cobblestone floor, two wall
  torches on the back wall), its doorway the door above, the house running on along the facing; its panes give onto
  bare stone (the seal's material here). One difference from the floor's house: an oak door, closed, in the middle of
  its back wall, the stair down behind it. The light reaches the tunnel through the door (doors pass light): about 6 to
  8 at the door's tunnel side, falling to 0 within the tunnel (measured; the lure must read from the pit's floor; if
  26.3's door stops the light, the side-1 cell's upper block is a glass pane instead of planks: the test decides).
- **L2, the rooms.** Centred under the pit's floor, the house repeated in a grid sharing walls (6 apart, aligned with
  the facing): 3 x 3 for the 63 and 95, 3 x 5 for the 127 and 191 (5 along the facing). Every house keeps one of its two
  torches (left or right by its cell's hash); every wall two houses share has an oak door (closed) in its middle and a
  pane either side at eye height, so a lit room is seen through a window, and another past it. The stair from L1 comes
  into the house nearest L1; the stair down leaves the house farthest from it.
- **L3, the warren.** The house repeated again, unlit, no panes: 5 x 5 (63), 5 x 7 (95), 7 x 7 (127), 9 x 9 (191). The
  doorways are a maze over the grid (a spanning tree from the stair's house, plus one extra doorway for every 6 houses):
  3 in 4 hold an oak door (closed), the rest are empty doorways (his pass). The stair up comes in at a house on the
  facing side; the stair down leaves the house in the opposite corner, and the way between them never needs breaking
  anything. **The camp**, a house 2 to 4 doorways off that way: a crafting table, a furnace (cold), white wool 1 x 2 on
  the floor, an oak chest (a stone pickaxe at a fifth of its durability, two bread, a compass, nine cobblestone: no torch,
  coal or anything that lights); each of its doorways stuffed from the inside with cobblestone and dirt mixed (both cells
  of the doorway), one of them broken through from outside (both cells empty, the frame's planks round it gone too, one
  cobblestone standing on the floor outside it). One or two other doorways off the way stuffed the same, a dark empty
  house behind each.
- **L4, the end.** The stair down from the warren's far house ends at an iron door, closed, in a cobblestone wall, with
  no button, lever, plate, tripwire or redstone anywhere in the depths; 1 step past it, the house a last time, the same
  as L1 and facing the same way, its two torch places bare (dark at worldgen). Its back wall is gone (the roof over it
  kept), and from the gap his tunnel: 2 wide, 2 high, raw stone, level, straight along the facing back under the warren,
  24 (63), 32 (95, 127) or 40 (191) long, unlit, ending blind; 8 before its end, a single cobblestone in the middle of
  its floor, nothing on it.
- **Fit.** Every level, stair and his tunnel inside the pyramid's footprint less 3 (the pyramid stands over all of it) and
  inside the shape's box; at least 3 of rock between any two of them, the pit's lining, the tunnel and the core, except
  where they join; at least 12 under the noise ground of every column it spans; every cell (its seal included) over the
  kit's height margin (`Heights.BOTTOM` over the lowest block worldgen fills). A level that does not fit, or whose 9
  biome samples (corners, sides' middles, middle, at its feet; `Terrain.biome`) hold `minecraft:deep_dark`, is dropped:
  L2 first (the stair from the house then goes down 16 to the warren), then the whole depths (the tunnel ends as in v4).
  The depths never make the kit refuse a start: every start v4 accepts, v5 accepts (tested over SC2-H's six worlds).
  Target over the default world's starts, the 191 included: all four levels in at least 70 %, three or four in 95 %.
- **Built as every shape.** Decided in the constructor from the start's seed and the noise (levels kept, the grids, the
  maze, the camp, the stuffed doorways, the torch each L2 house keeps), never per chunk; seal first (`Seal.REACH` round
  every depths cell, never into the pyramid's interior), then cut, then blocks; writes only in its clip; worldgen equals
  `Live.place`. Doors and wall torches set whole (both halves, the facing); the chest's contents through
  `Writer.contents`. Spawns as the pyramid's box (no creatures; monsters as vanilla: L3, L4 and his tunnel fill in the
  dark; L1 and L2 do not while lit).
- **Inside.** Every level, stair and his tunnel is inside the pyramid's place (`PyramidShape.interior()` through the
  hook), so `Visits`, `Remains.containing`, 16b (the way in may close while you are below, on its own rules) and 01+
  see it. 16c stays the pit's (it asks `pit`). A player's level is the level whose box holds their feet; a stair counts
  as the level it leaves going down.
- **None** under the sea form, and under every start tied to v1 to v4 (R12-2): a pyramid decided before this version
  keeps its dead end; the depths are under pyramids in land decided from this version on.

### Answers (D-122)
Every one through `Answers` (asleep; the switch and the story; creative and spectators never inside; never in a player's
quiet but a first; never in their first hour), out of everyone's view, ledgered under opaque causes (`world:s16/33c`,
`world:s16/33d`, `world:s16/33e`, under 16's `giveBackWhenOff("sc16", "world:s16")`), `REMAINS_ANSWERED` and tension as
every answer. Sounds through `SoundCues.playAt` (everyone in range hears them), vanilla only, at volumes that are
clear at 10. Out of view in the depths also means: no player in a house whose pane or open doorway gives onto the cell's
house (the plan's room graph: a pane is not a wall).
- **33b The door below (the lure).** A participant on the pit's 3 x 3 floor or in the tunnel for `a` 20 s, nobody in
  the depths, the house's front door out of everyone's view: its open sound, then 20 to 40 ticks later its close (oak),
  at the door, `j` 1.6; a door standing open closes then opens (it ends as it stands; echo_door's rule); nothing moves.
  Once per player per in-game day.
- **33c Other rooms.** One answer a visit (once per player per in-game day), its events its parts:
  - *Doors.* In L2 every `b` 60 to 120 s, in L3 every `c` 25 to 50 s: a door 1 to 3 houses from the player's, never their
    own house's or one in view, plays its open and close 20 to 40 ticks apart at `j` 1.6. In L3 a share `d` 0.5 of them
    really change state (a shut one left open, an open one shut: both halves through `TraceService`), and each next door
    is a house nearer the player than the last until one is in a wall of their own house. Never opens a door with a
    monster within 16 of it on either side; never shuts one with a player within 3 of it; never the front door while
    anyone is in the tunnel, never the iron door. At most `e` 12 a visit, `f` 6 of them moved.
  - *Steps.* In L3, while a player walks along a row of houses and the house beside theirs (sharing a wall, abreast on
    their heading) is empty and out of view: each of their steps is answered `h` 6 ticks late by a step on that house's
    floor, abreast of them (`SoundCues.stepType`, `SoundSource.PLAYERS`, `k` 1.6), for `g` 6 to 12 steps; when they stop,
    one more, then nothing. At most `i` 2 walks a visit, 90 s apart, never within 30 s of a door.
  - Two players apart in the warren: the steps may walk the empty house between them (both hear them; neither is it).
  - Nothing within 30 s of any player's combat; nothing more in the visit once 33e's door has shut (the silence).
- **33d The light goes down.** A participant in L3 for `m` 45 s, or anyone in L4: out of everyone's view and never
  within 6 of a player, L2's torches are taken house by house from the stair up toward the player, one every `n` 3 to 6
  s; then L1's two torches are moved (`TraceService` move, the same wall facing) to the end house's two bare places,
  which must be out of view too. So the bottom house is lit as the first one was and the first one is dark. Once per
  place until given back (`world:s16/33d`); only the depths' own torches, never one a player set.
- **33e The iron door.**
  - *It opens.* Once 33d has lit the end house: a participant in L3 for `o` 60 s, at least `p` 16 from the iron door,
    the door and the house behind it out of everyone's view: the door opens (both halves, `world:s16/33e`), its iron
    open sound at `l` 2.0 (heard to 32). Once per place per in-game day; it stays open until it shuts.
  - *It shuts behind you.* A participant past it (the end house or his tunnel) for `q` 20 s and at least `r` 8 from it,
    the door out of everyone's view, nobody on the near side within 16 of it, every player past it carrying a pickaxe
    and at 10 health or more (16b's rules, R12-24, R12-25): it shuts, its iron close sound at `l` 2.0. `s` 3 per place
    ever, once per player per in-game day.
  - *The dark.* Then the end house's torches (the ones 33d brought) are taken one by one 4 to 8 s apart, never within 6
    of a player (one standing by them keeps them).
  - *His glimpse.* Then, with the end house dark and the player in his tunnel: `Services.figure().glimpse(player, feet,
    yaw)` on the tunnel's floor `t` 14 to 24 from them, toward the house first (between them and the way out), else
    toward the blind end (D-073, D-105; `canGlimpse` holds every glimpse rule, so it often does not come). Once per player
    per place, ever.
  - *The silence.* From the shutting to the visit's end, no 33b and no 33c.

### Fair danger (D-123)
- **The danger is the game's.** The unlit levels spawn monsters as any dark does; wooden doors hold them (a zombie
  breaks one on Hard, as vanilla). No answer strikes: none opens a door with a monster within 16 of it, none acts in
  view, none takes a light a player set (their torches are their way back through a maze); the darkening never falls
  within 6 of a player (D-097.2).
- **Told.** Every change is heard before it is found (each door its own sound, the iron door at 32), the light goes
  where the player can see it has gone, and the levels say how deep you are.
- **A way out, always.** The way from the stair up to the stair down never needs breaking anything; every step is 1 high
  (D-097.7: nothing drops more than 1); no fluid (the seal); wooden doors open by hand; the iron door shuts only on
  players who carry a pickaxe at 10 health with nobody on its other side, and is mined in seconds (vanilla drops it);
  the warren's walls are planks and the rock round everything is stone. A player who breaks the iron door has ended 33e
  for that place (no answer acts on a door that is not there).
- **Hardcore.** Nothing here kills that the game would not; what kills here is the dark, the monsters and the time.

### Multiplayer
| case | what happens |
| --- | --- |
| Two inside together | Every sound is heard by both (`playAt`); a change waits until it is out of both views; 33e shuts on both (both carry a pickaxe, both at 10) |
| Two inside, apart | The steps may walk the empty house between them; 33c may shut a door between them (never a lock: wooden); 33d waits while either is within 6 of a torch or sees it; a friend on the near side within 16 of the iron door keeps it open |
| One inside, one outside | The outside one hears nothing of the depths (range); a friend within 16 of the way in keeps 16b from closing; one waiting on the pit's floor hears the real doors of the friend below, and 33b does not play while anyone is in the depths |
| A newcomer, a leaver | Answers are per visit by UUID; a newcomer inherits nothing (R12-24); a leaver's parts stop, what was done stays ledgered |

### Switch, asleep, Ending D
Asleep (D-108): the depths are there from creation, lit house and all; nothing answers. Switch off: no answer; the doors
and torches given back through 16's registration (loaded and out of view, retried while off); what generated stays
(D-093). Ending D gives every edit back; nothing during `ending:last_minute`, after `ending:ended` or `d_complete`.

### Config `sc33` (v1; keys `a` to `t`)
`a` lureSeconds 20, `b` roomsDoorEvery {60, 120}, `c` warrenDoorEvery {25, 50}, `d` doorMovedShare 0.5, `e` doorsPerVisit
12, `f` doorMovesPerVisit 6, `g` stepsPerWalk {6, 12}, `h` stepLateTicks 6, `i` walksPerVisit 2, `j` doorVolume 1.6, `k`
stepVolume 1.6, `l` ironVolume 2.0, `m` darkAfterSeconds 45, `n` darkEverySeconds {3, 6}, `o` ironOpenAfterSeconds 60, `p`
ironOpenMinDistance 16, `q` ironShutAfterSeconds 20, `r` ironShutMinDistance 8, `s` ironShutPerPlaceMax 3, `t`
glimpseDistance {14, 24}. The shape's numbers are constants of the version, never config (a start's shape never moves).

### The hook (R3-2 leaves it; D-124)
On `sc3/pyramid`, before its merge, and nothing else of R3-2's changes for it:
1. **A seam class** `world/sc2/s16/deep/Depths.java` (`com.forzacode.a1016_02.world.sc2.s16.deep.Depths`), a stub
   R3-D fills: `public static final int FROM = 5;` `public static final Depths NONE`; `public static Depths of(PyramidShape
   shape, Terrain terrain)` returning `NONE`; `public Optional<BoundingBox> box()` (empty); `public void fill(Writer w,
   BoundingBox clip)` (nothing); `public boolean inside(int x, int y, int z)` (false); `public static void init()` (nothing).
2. **In `PyramidShape`:** a field `private final Depths depths`, assigned as the constructor's last statement:
   `this.depths = version >= Depths.FROM && hasPit() ? Depths.of(this, terrain) : Depths.NONE;` · `box()` returns the
   pyramid's box encapsulating `depths.box()` when present · `fill(w, clip)` calls `depths.fill(w, clip)` as its last
   statement (after the pit, its torch, the tunnel and every seal) · `interior()` is `core || pit || tunnel ||
   depths.inside(x, y, z)` · public `depths()`.
3. **Public accessors** the depths read: `Optional<BlockPos> tunnelEnd()` (the tunnel's last feet cell on side 0,
   `local(0, pitBottomY() - y0, TUNNEL_LENGTH + 1)`; empty without a tunnel), `right()`, `seed()`, `size()`, `footprint()`, beside the ones there
   (`local`, `facing`, `pitBottomY`, `pitDepth`, `halfWidth`, `baseY`, `centerX`, `centerZ`, `version`, `hasPit`, `core`,
   `pit`, `tunnel`, `lining`). The tunnel stays where v3 put it (`f` 2 to `TUNNEL_LENGTH + 1`, sides 0 and 1, feet at
   `pitBottomY()`), its end face (`f = TUNNEL_LENGTH + 2`) sealed as now.
4. **In `S16.init()`:** `Depths.init();` as its last line.
5. **`VERSION` stays 4**, and R3-2's tests of v4's geometry build a `Start` tied to 4 (`start.withVersion(4)`), never
   `LATEST`, so R3-D's bump to 5 leaves them green.

### Build
`world.sc2.s16.deep` (R3-D): `Depths` (the seam, filled), `DepthsPlan` (levels kept, grids, maze, camp, stuffed
doorways, kept torches, stairs; per start, from the seed), `RoomGraph` (houses, doorways, panes, doors' cells: view, the
nearer rule, the house between two players), `DepthsAnswers` (33b to 33e, ticked every 10 ticks per participant inside),
`Sc33Config`, `Sc33Commands` (`/a1016 sc 33`: the nearest pyramid's depths, levels built, your level and house, the doors'
states, the answers spent; `sc 33 arm` runs the timers now, rules kept; `sc 33 here`), `Sc33Gallery` (dev only).
`PyramidShape.VERSION` becomes `Depths.FROM`.

### Cost
16's fill stays in D-118's class (≤ 8 ms median, 25 ms worst a chunk): the depths add at most about 20 000 cells a level
over at most 16 chunks (≤ 0.5 ms median a chunk measured into the overlay); siting adds the levels' biome samples (≤ 36,
≤ 0.5 ms a start; nothing for v4 and older); runtime under 5 µs a tick idle (the room graph built once a place a run,
cached with the shape); saves: per-place answer counts only (`Sc2Data`, ≤ 64 bytes a place).

### Tests (`world.SelfCreated3DepthsTests`; test environment `world/s33`)
The shape: v5 builds the depths, v4 and v3 starts build exactly v4 and v3 (R12-2; the kit's pin stays green); every
step 1 high, walked from the pit's floor to the blind end of his tunnel (`world.Edges`); no fluid or cave air within 2 of
any depths cell; 3 of rock between levels, stairs, the pit's lining and the tunnel; inside the footprint less 3, the box,
`MAX_REACH` and the height in SC2-H's six worlds and a short one (the levels dropped in order, never a start refused for
them); deep dark refused per level; worldgen equals `Live.place` for a 63 with four levels; `Remains.containing` in every
level and his tunnel; the door at the tunnel's end and the light it lets through (the tunnel's last cell lit, the pit's
floor not); L1 equals 20's house but for its back door; L2's one torch a house and its panes; L3's maze connected from the
stair up to the stair down without breaking anything; the camp's blocks and chest; the stuffed doorways off the way only;
the iron door's world has no redstone source; the end house bare, his tunnel and its cobblestone.
The answers: 33b (the pit's floor and the tunnel only, nobody below, out of view, once a day, nothing moves); 33c (the
rhythm per level, the nearer rule, the share moved, ledgered and given back, never in view through a pane, never onto a
monster, never shut on a player, the caps; the steps abreast and late, the one after stopping, the house between two
players); 33d (L2's from the stair up, L1's two moved to the end house's places, never within 6, never a player's
torch, out of view at both ends, once); 33e (opens only after 33d and out of view, the clank's range; shuts only with
the pickaxe, the health, nobody near on the other side, the caps, R12-24, R12-25; the dark; the glimpse after the dark,
once per player per place; the silence); a broken iron door ends it; asleep, creative and the switch refuse all; the
switch's give-back; two players together and apart, one outside, a leave midway.
Cost: the fill into the overlay over the real noise (`VerifySc2CostTests`' method); the find run's share of starts with
four and with three levels (the target above); SC2-H's survey with no new finding; the real-world client run for `s16`.

### Gallery (exhibits `33` a 127 with four levels, `33s` a 63 with L2 dropped, `33h` a 191)
`in_1` (from the pit's floor by its torch into the tunnel: the door and its light; true light), `in_2` (inside the house:
the panes onto stone; true light), `in_3` (L2 through a pane into the next room and the next; true light), `in_4` (L3,
night vision: doors and a stuffed doorway), `in_5` (the camp through its broken doorway), `in_6` (the iron door from the
stair, a torch in hand), `in_7` (33d and 33e forced: the door open, the lit end house beyond), `in_8` (his tunnel from
the gap, the cobblestone), `in_9` (L1 after 33d from the tunnel: dark), `section` (cut along the facing: the pyramid,
its pit, the tunnel and the levels below at once: the scale), `plan_1` to `plan_4` (each level from above, cut at its
roof). Must show: the lure at the tunnel's end from the pit's floor; L1 read as 20's house where no house can be; L2's
too many; L3's dark and its doors; the end house lit as the first was; `section` a descent that no person dug.

### Considered and left out
- A mineshaft: Beta's, vanilla and everywhere already; nothing wrong with one.
- A maze of bare stone corridors: no rooms, so no "other rooms", no doors; the houses are what make the steps and the
  doors mean something.
- A ladder shaft down: an open drop when you let go (D-097.7); stairs only.
- A button, lever or plate for the iron door: a mechanism found is a mechanism explained.
- Spawners, or mobs set in the rooms: a creature factory (DESIGN 7: darkness spawns for him).
- A sign, a note or a book from the one who camped: DESIGN 2 (text is the archive's alone, D-114).
- Opening a door onto a monster, or taking the player's own torches below: a strike, and a maze with no way back.
- Depths under existing (v3, v4) pyramids: R12-2 and D-093 (nothing placed at runtime; old places keep their shape).
- A second piece or structure for the depths: the kit makes one piece per start; a second would change the kit's save.

---

## Cost (D-118)

| what | before | round 3 (budget) | why it moves | as built (measured; the task) |
| --- | --- | --- | --- | --- |
| 16 a chunk's fill (land) | 6.7 / 24.3 ms | ≤ 8 / 25 ms (amended class) | 191: up to 96 of core air and 88 of pit a column; about 15 x 15 chunks a start instead of 11 x 11; the strip and fault add under 0.1 ms | 7.0 ms median (6.7 before), outliers about 200 ms on a loaded machine (R3-2) |
| 16 a start's siting | 8.1 / 11.6 ms, 43 samples | ≤ 15 ms, ≤ 70 samples | the 191's 7 x 7 ground samples, asked only once the 127 fits | 19 / 112 ms made, 9 / 52 refused: over the 15 (up to 128 reads a slot, once a world; R3-2) |
| 17 a chunk's fill | 0.17 / 12.1 ms | ≤ 8 / 25 ms | the hall 41 high (about 2.5 x the cells), the well 4 chunks x about 150 high (up to 38 000 cells a chunk); the cut reads the land under 1 024 columns once | R3-1's (not merged into this branch) |
| 18 a start | 303 / 345 ms (once a start) | ≤ 400 ms once a start | the summit's 256 columns read exactly; the piece and fault: 1 to 4 chunks more writes | the shape 225 ms median (636 at version 2), a made siting 269 ms, a refused 0.3 ms (R3-3) |
| 19 a chunk | as SC2-8 | unchanged per chunk | more chunks (longer), 32 light blocks a hole | not measured again (R3-2) |
| 20 a chunk's fill | 6.8 / 24.6 ms | ≤ 8 / 25 ms | 192: 144 chunks a start, the same per chunk; the fault adds about 30 light blocks a chunk | siting 18.7 / 47 ms made, 12.0 / 144 refused (R3-0); the fill 2.92 / 9.12 ms in CPU, the noise read in it, 1.28 worst warm (FIX-R3-E, the survey's 73 starts) |
| 32 a chunk | | ≤ 0.5 ms median, ≤ 25 ms worst (excluding a candidate's first siting) | a few crumbs a chunk, under 40 blocks | 0.001 ms median, 0.15 p95, 3.6 worst; a place's lines planned once 422 ms median, 1.5 s worst under load (R3-C) |
| 16 v5 the depths (33) | | + ≤ 0.5 ms median a chunk (16's fill still ≤ 8 / 25 ms); + ≤ 0.5 ms a start's siting; ≤ 5 µs a tick idle | up to about 20 000 cells a level over at most 16 chunks; ≤ 36 biome samples a start | a start 1.9 / 5.4 ms (9 ground reads), a chunk's fill 0.60 / 18 ms (R3-D) |
| siting once per candidate | per start | the same total, paid by whoever asks first (`Decided`) | crumbs and `/locate` ask earlier; nothing is sited twice | 16 made 10.4 / 177 ms, 17 15.4 / 144, 18 447 / 725 before R3-3, the `avoid` check 2 µs (R3-0) |
| density | | about 1.6 x the places per area | more chunks carry a piece (about 1.5 to 2.5 % of land chunks); a chunk's own budget unchanged | 3.32 places a km² from 900 to 3 000 (T6: 3 to 6), no two boxes within 96 (R3-0) |
| runtime idle | under 20 µs a tick | under 25 µs a tick | 32's sampler every 20 ticks per participant (chunk-grid index), the day log's rollover | 32's pass 39 µs every 20 ticks for two participants (about 2 µs a tick, R3-C); 33 a look every 10 ticks |
| saves | `sc2` ≤ 2 KB a place | + `d` ≤ 16 bytes a refused candidate, 64 a made one; + `c` ≤ 256 bytes a crumb line; day log ≤ 48 bytes a day (1 024 days); editions ≤ 16 per player per archive (about 1 KB each in their chunk) | | not measured again |

Measured by each task in its own tests (own test environment, the real noise, `VerifySc2CostTests`' method), gathered
by R3-P into one table.

## Pacing (D-119, R3-P)

Re-measured with `SelfCreated2PacingTests`' runs (192 seeds x 20 h per tempo) after the wave 2 merges:
- 01+ gives its place factor only while awake; D-070's first hour (first event by 30 / 45 min in 80 / 94 %, two or
  more in 90 %) unchanged in every tempo.
- The dry runs' inputs re-derived from R3-0's presence (T1 to T4): `sc01 p` (the share of play at his places, 0.06), 22's
  reach (0.08 a look), 31's (0.05), 31c's (0.03), the called nights after a visit, visits per hour; versioned with
  `keepOrNew`.
- Must hold: Traces' and Proximity's tier rates and the majors within 15 % of the deck without the layer (the four
  cases); the second wave from Proximity 0.40 to 0.55 real a played hour; never more than two of its moments in an hour;
  the first beat by Proximity + 1.5 h in at least 80 %; the hunts' own clock (never before 2.5 h x tempo); the traps on
  the accident clock within a quarter of the plain deck; two players together 0.95 to 1.05 of one player's minors.
- New rules counted: answers (17f, 18c, 20b v2) raise tension and buy quiet as every answer; 32b and 32c are rules
  with day caps, out of view, and count as one of the wave's moments for the 30-minute gap (`t`) when they fire.
- New rows in "what a player meets": a place's sign seen by session 1 / 3 / 5 (T4's model folded into the sessions);
  a place entered awake by session 3 / 5; an answer felt by session 3 / 5. Targets: a sign by session 3 for at least
  80 % of players; an answer felt by session 5 for at least 50 %.
- 33's answers (33b to 33e, R3-D) raise tension and buy quiet as every answer; one visit's answers below the pit count
  as one of the wave's moments for the 30-minute gap. If they break a bound, `sc33`'s rhythm (`b`, `c`, `e`, `i`) moves
  first.
- If a bound breaks, R3-P moves `sc01` (`p`, `s`) first, then asks; never the places' densities (R3-0's).
- **As measured (R3-P; "As built (R3-P)" under Tasks).** `sc01` 6: `p` 0.10, `s` {0.3, 0.4}. From Proximity 0.49 / 0.52 /
  0.52 real moments of the wave a played hour (early / slow burn / very late), never more than two in an hour from the
  director (33's, which the player chooses, can make an hour of three or four); D-070's first hour unchanged; 4b within
  15 %; two players together 0.98 to 1.01 of one player's minors; a sign by session 3 for every player, an answer felt by
  session 5 for 93 %; in a world three times as dense, answers 1.1 an awake hour, their caps held.

## Spoilers for round 3 (D-103 extended)
- New ids: feature and placed feature `a1016_02:world/s32`; structure `world/s19` and structure set `world/s19` (the
  marker); placement type `world/sc2a`; saved data `a1016_02_s2d`; `Sc2Data` keys `d` and `c`; config `sections.sc32`
  (keys `a` to `n`); exhibits `32-16` to `32-20`, `16h`, `17w`, `17t`, `17r` (the books, client screens), `18p`, `20n`; gallery
  views `d64`, `d128`, `d200`, `n64`, `n128`, `n200`, `d48`, `d96`, `d160`, `n48`, `n96`, `n160` (numbered camera
  words). 33 (R3-D): config `sections.sc33` (keys `a` to `t`); ledger causes `world:s16/33c`, `world:s16/33d`,
  `world:s16/33e`; exhibits `33`, `33s`, `33h`; commands `/a1016 sc 33` (`arm`, `here`).
- `VerifyScSpoilerTests` gains: crumb, breadcrumb, fault, glitch, edition, tattered, summit, tabletop, hanging, stacks,
  dormant, asleep, awake, and for 33 depths, warren, barricade (watched by hand if an existing line trips one: well,
  piece, day, light, lit, and for 33 rooms, iron, camp).
- Logs: no line at INFO or above names a position, a book's text or title, a crumb or a place's kind word; debug
  `ScLog` as before. `/locate` prints what vanilla prints.

## Config sections (round 3)
| section | version | change | owner |
| --- | --- | --- | --- |
| sc16 | 3 | `a` 1 → 0, `b` 560 → 384 (R3-0); `d` four weights, `f` 0.75, keys `o` seaLight 1.0, `p` stripDepth 1 (R3-2) | R3-0, then R3-2 |
| sc17 | 2 | `a` 0, `b` 384 (R3-0); keys `s` wellLitGalleries 6, `t` wellDarkDepth 3, `u` wellDarkAfterSeconds 60, `v` wellDarkTicks 160, `w` wellGlimpseAfter 3, `x` editionsMax 16, `y` wrongLineChance 0.5, `z` dayBooksAhead 2 (R3-1) | R3-0, then R3-1 |
| sc18 | 3 | `a` 0, `b` 448 (R3-0); `m` summitCut {8, 14}, `n` pieceDarkAfterSeconds 60 (R3-3) | R3-0, then R3-3 |
| sc19 | 2 | `a` 0, `b` 512, `d` 512, `e` 1 024 (R3-0) | R3-0 (R3-2 may add) |
| sc20 | 4 | `a` 0, `b` 448 (R3-0); `f` sideMax 192 (`c` side kept: the size tried after it), 20b v2's `g` darkChunkSeconds 4, `h` darkPerVisit 64, `i` glimpseAfterChunks 4 (R3-3); v4: `g` darkRingSeconds 7, `h` 6 000, `i` glimpseAfterRings 2 (FIX-R3-B) | R3-0, then R3-3, FIX-R3-B |
| sc32 | 1 | new | R3-C |
| sc33 | 1 | new (keys `a` to `t`, under "33") | R3-D |
| sc01 | 6 | `p` 0.06 → 0.10, `s` {0.3, 0.75} → {0.3, 0.4} (R3-P; the reaches are `SimFits`' code) | R3-P |

A version step another task already raised joins it (FIX-SC2-PACE's precedent): each `upgrade()` runs its steps in
order with `keepOrNew`.

---

## Tasks

Branches from `self-created` (local; the detailed history of round 2 is on `sc2-history`), merged back into
`self-created` only; `self-created` → `main` only on Mariano's word. Same process as round 2: git worktrees, one branch
per task, at most 4 workers at once, a worker edits only its task's owned paths (a path not listed is someone else's:
ask in the report), `./gradlew build` green on the branch (every game test), commit, a report of at most 40 lines
(branch, commit, test count, what was built, the final gallery PNG paths and one line per gallery round for visual
tasks, asks, anything open). Game test classes are `SelfCreated3<Name>Tests`; test environments a task adds are its
own new files. Every visual task runs the gallery at least three rounds (SELF_CREATED_2 "How tasks use it") and writes
`build/gallery/<id>/NOTES.txt`. Every task that changes a shape (R3-1, R3-2, R3-3) or writes worldgen blocks (R3-C)
keeps SC2-H whole: its new version goes through `Sc2Structure.site`'s height rule, the seal and the `Guard`; it re-runs
`SelfCreated2HeightsTests` (the six worlds, both ends, a short world) and SC2-H's survey (`-Da1016_02.sc2survey=true`)
with no new audit finding, and the real-world client run (`-Da1016_02.sc2real=true`) for its kinds, and says so; and it
adds the R12-2 test that a start tied to the previous version still builds exactly that version.

| ID | Branch | Owned packages and files | Depends on | Size | Implements |
| --- | --- | --- | --- | --- | --- |
| R3-S | `sc3/seams` | `src/gametest/resources/fabric.mod.json` (the new classes); empty `world/SelfCreated3KitTests`, `SelfCreated3FindTests`, `SelfCreated3BooksTests`, `SelfCreated3ArchiveTests`, `SelfCreated3PyramidTests`, `SelfCreated3HollowFloorTests`, `SelfCreated3CrumbTests`, `director/SelfCreated3PacingTests`; empty `world/sc2/s32/S32.java` and its line in `world/sc2/WorldSc2`; empty `world/sc2/s17/books/Books.java`; `core/VerifyScSpoilerTests` (the words) | - | S | the seams |
| R3-0 | `sc3/kit` | `world/sc2/kit/**` (`Snapshot`, `Siting`, `Sc2Structure`, `Visits`, `Answers`, `RaysPlacement`, new `AxesPlacement`, `Decided`, `Fault`, `Sc2Data`); `core/Remains.java` (`awake`, `predicted`, `sign`); `core/Gallery.java` (sign views); new `world/mixin/ChunkGeneratorLocateMixin.java` and its line in `a1016_02.world.mixins.json`; the five configs' `a` and `b` and the line's distances only (`s16/Sc16Config`, `s17/Sc17Config`, `s18/Sc18Config`, `s19/Sc19Config`, `s20/Sc20Config`); new `world/sc2/s19/LineMarker.java` and its registration line in `s19/S19.java`; data `worldgen/structure_set/world/*.json` (all), new `worldgen/structure/world/s19.json`, `worldgen/structure_set/world/s19.json`; tests `world/SelfCreated3KitTests`, `world/SelfCreated3FindTests`; the updates its numbers force in `SelfCreated2*Tests` (named in the report) | R3-S | L | D-108, D-109, D-110's kit, D-112, `Decided`, creative never inside |
| R3-B | `sc3/books` | `world/sc2/s17/books/**` (`Books`, `Facts`, `DayLog`, `Voice`, `Composer`, `Pages`); test fixture `src/gametest/resources/data/a1016_02/world/sc2/b17_test.json`; `world/SelfCreated3BooksTests` | R3-S | M | the book system's code (no placement) |
| R3-W | `sc3/words` | `src/main/resources/data/a1016_02/world/sc2/b17.json` only | - (the schema above) | M | the texts |
| R3-1 | `sc3/archive` | `world/sc2/s17/**` except `books/**` (it may fix a bug there and says so); data `worldgen/structure/world/s17.json`, `tags/worldgen/biome/world/s17*`; test env `world/s17`; `world/SelfCreated3ArchiveTests` | R3-0, R3-B, R3-W | L | 17 v3, 17b on the floating grid, 17f, 17g, the books' placement, the sign; exhibits `17`, `17l`, `17n`, `17w`, `17t`, `17r` |
| R3-2 | `sc3/pyramid` | `world/sc2/s16/**`, `world/sc2/s19/**`; data `worldgen/structure/world/s16*.json`, `tags/worldgen/biome/world/s16*`, `worldgen/feature/world/s19.json`, `placed_feature/world/s19.json`; `world/SelfCreated3PyramidTests` | R3-0 | L | 16 v4 (191, the sign), 16d's way in, 19 v3 (the lit foot, nearer and longer) |
| R3-3 | `sc3/hollow` | `world/sc2/s18/**`, `world/sc2/s20/**`; data `worldgen/structure/world/s18.json`, `s20.json`, `tags/worldgen/biome/world/s18*`, `s20*`; `world/SelfCreated3HollowFloorTests` | R3-0 | L | 18 v3 (the summit cut, the piece, the foot), 18c, 20 v3 (fullbright, 192), 20b v2 |
| R3-C | `sc3/crumbs` | `world/sc2/s32/**`; data `worldgen/feature/world/s32.json`, `placed_feature/world/s32.json`; `world/SelfCreated3CrumbTests` | R3-0 | L | 32, 32b, 32c |
| R3-D | `sc3/depths` | `world/sc2/s16/deep/**` (from R3-2's seam `Depths`, which becomes R3-D's); in `world/sc2/s16/PyramidShape.java` the `VERSION` line only (4 becomes `Depths.FROM`, 5) and one sentence for version 5 in its class Javadoc; new test environment `world/s33`; new `world/SelfCreated3DepthsTests` and its line in `src/gametest/resources/fabric.mod.json`; `core/VerifyScSpoilerTests` (33's words only); the updates the bump forces in `SelfCreated3PyramidTests`, `SelfCreated2PyramidTests`, `SelfCreated2HeightsTests` (none if R3-2 pinned v4; each named in the report) | R3-2 merged (the hook) | L | 33 (D-120 to D-124): 16 v5, 33b to 33e; exhibits `33`, `33s`, `33h` |
| R3-P | `sc3/pace` | `director/**` (`sc/**`, `DirectorSim`, `SimFits`), `debug/PlaythroughCheck` (limits); `director/SelfCreated3PacingTests`; `docs/SELF_CREATED_2_R3.md` (the "As built" notes from every report, the pacing and cost tables) | wave 2 merged (R3-D included) | M | D-119; the docs |
| R3-A | (review) | none: `scratchpad/reviews/R14.md` | wave 2 merged, `gallery -Pgallery=all` | M | the art director's review of the round |
| FIX-R3 | `fix/sc3-*` | by workstream, as the findings fall | R3-A, R3-P | M | the review's fixes |
| R3-V | `verify/sc3` | none (report) | FIX-R3 | M | the verifier |

### R3-S `sc3/seams` (wave 0)
- **Implements.** The empty test classes listed, registered in the game tests' `fabric.mod.json`; `S32` (empty `init`)
  called from `WorldSc2`; an empty `Books` class; the spoiler words. **Done.** Build green; the merge lands before wave 1
  branches.

### R3-0 `sc3/kit` (wave 1)
- **Implements.** D-108 (no stage gate, `spawnKnown`, R0, `Remains.awake` in `Answers.refusal`, `Visits`' events and
  trespass, 17a's runtime call guarded through `Answers.held`'s new reason); creative never inside (`Visits`,
  `Answers`); the densities and distances (the table; structure sets; configs `a` and `b` with `keepOrNew` and a version
  step); `Decided` (the cache, saved, read by `Sc2Structure` first, thread-safe; `Remains.predicted`); `Fault` (the
  lighting fault with its measured edge, the cut, section and chunk alignment helpers, the kerb and lip from
  `world.Edges`' rule; `Remains.sign(place)`, each kind's sign point registered through `Sc2Kind`, default the
  entrance); `/locate` (the mixin with an `@Invoker` for `getStructureGeneratingAt`, `AxesPlacement`, `LineMarker`);
  `Gallery.signViews(sign, facing)` (`d64` ... `n200`).
- **Tests** (`SelfCreated3KitTests`). Siting decides with the stage at Alone; nothing within 384 of the spawn or origin;
  nothing before the spawn is known; awake and asleep (each answer refused asleep, events silent, trespass unrecorded);
  creative: present for view and mouth rules, never inside, spends nothing; `Decided` (once per candidate across threads,
  saved and loaded, a config change keeps the decision, worldgen's start equals the decision); the lighting fault's edge
  measured in a test world (light at least 10 on 95 % of the top cells, at most 6 three outside, waterlogged in water, no
  monster spawns on it, guarded, removable and given back); `/locate structure` for s16, s16d, s17, s18, s19, s20 in a
  fresh world under Traces (the nearest, the same as `Decided`'s nearest made); version: every kind's start tied to its
  current version still builds exactly as before (a fill hash on a test seed). `SelfCreated3FindTests` (when asked): T1 to
  T4 and T6 with before and after numbers (T5 is R3-C's).
- **Gallery.** None of its own; `signViews` used by every place task.
- **Done.** Build green; T1 to T4 and T6 met (or the bounds reached and reported); `/locate` finds every kind; the
  round 2 tests updated only where a number moved (listed).

#### As built (R3-0, `world.SelfCreated3KitTests` 7, `world.SelfCreated3FindTests` 2 when asked)
- **D-108, from creation, asleep.** Every kind's `a` (fromStage) is 0; the stage test stays in `Snapshot.refusal`, so a
  value raised by hand is honoured. `Snapshot` gains `spawnKnown` (false for the snapshot taken at `SERVER_STARTING`,
  true once the server runs; a test's 6-argument snapshot is true): no start and no crumb while false. `core.Remains`
  gains `asleep(server)` (default null, the stub "no places"), the static `Remains.awake(server)`, `sign(place)` and
  `predicted(level, pos, radius, kinds)`. `Answers.story(server)` is the live story: the story's world gates, then
  `Answers.ASLEEP` ("asleep") under Traces; so `Answers.held` (17a's and 17c's guard) and `Answers.refusal` refuse
  while asleep, and `RemainsImpl.asleep` is `Answers.LIVE.held`. `Visits` takes an awake predicate (LIVE:
  `Remains::awake`; the 4-argument constructor is always awake): asleep, a visit is followed and `visited` kept, no
  event fires, no trespass time is written; a leave fires only for an enter that fired. 01+ is R3-P's.
- **D-116, creative.** `Visits.neverInside(player)`: a spectator or a creative player is never inside (no enter, no
  visited, no trespass, never in `inside`, so no answer counts them); both stay present for the view rules.
- **D-109, numbers** (config versions sc16 3, sc17 2, sc18 3, sc19 2, sc20 3, each step `keepOrNew` from the old `a`
  and `b`). After trials within the bounds (`SelfCreated3FindTests`, `-Da1016_02.sc3try`): rays 6, first 416, step 448
  (16, 16d); archive 16 / 6 (trial chambers 4 chunks off kept; ancient cities 4 chunks off through the structure
  JSON's new `avoid` list, in `s17.json`, R3-1's file: one field); hollow 14 / 5; floor 24 / 10 (villages 6 off kept);
  R0 352 (16, 16d), 384 (17), 416 (18, 20); the line from 512 (+0 to 256), 512 to 1 024 long.
- **The places' spacing rule (new, for T6).** Without it, 73 pairs of boxes within 96 a world before and 189 after
  (archive with archive mostly). `Decided.yields`: a made siting yields to any place of a kit set (its own included)
  whose start goes in another candidate chunk, whose box comes within `Decided.NEAR` (96) of its own, whose precedence
  is higher (`Decided.priority`: the rarer kind first, pyramid, hollow, floor, archive; then the larger box; then a hash
  of the seed, kind and chunk) and which is made in the end itself. It equals keeping, in order of precedence, every place no kept one is near, whatever the chunk order. The
  neighbours are sited once per world (`Decided.sited`, this run's map of sitings before the rule).
- **`Decided`** (`world.sc2.kit.Decided`). One record per (kind, candidate chunk) of the live world, refused or made
  (start with its version, box, anchor, entrance, facing, sign point, the biome probe's height); every asker waits on
  the first one's siting (futures). A decision includes the live world's exclusion zones (vanilla's `/locate` never
  asks them), the JSON's `avoid` list, the kind's siting, the kit's reach and height, vanilla's biome check at the stub
  and the spacing rule. Kept only for the live overworld's generator and seed while the snapshot is the live one
  (`Snapshot.install` decides without keeping; `Snapshot.installLive` keeps, for tests). Saved under `Sc2Data` key `d`
  (8 bytes a refused candidate, 72 a made one), loaded, drained and cleared with `Plans`. The kit's gates come first and
  are never kept. `Sc2Structure.findGenerationPoint` reads it first and builds the kept start (its shape made at its
  first chunk, `Sc2Piece(kind, start, box)`).
- **`/locate`.** `world.mixin.ChunkGeneratorLocateMixin` (a `@Shadow` of the private `getStructureGeneratingAt`, in
  place of an `@Invoker`: one file) adds the `KitPlacement`s (`RaysPlacement`, `AxesPlacement`). The line: structure
  `world/s19` (type `world/sc2`, `"marker": true`: one empty `Sc2Piece` at the ground of each planned segment's middle
  chunk, nothing recorded) on structure set `world/s19` with placement type `world/sc2a` (`AxesPlacement`, registered by
  `s19.LineMarker.init`, one line in `S19.init`). The game tests' flat world generates no structures (vanilla and the
  mixin stop before asking), so `SelfCreated3KitTests` checks what `/locate` asks per candidate (vanilla's structure
  check sees the line's marks and nowhere beside, a kept made decision as there and a kept refusal as not); the whole
  command is run in the real world's client run (`-Da1016_02.sc2real`, below).
- **`Fault`** (`world.sc2.kit.Fault`), the sign's grammar, worldgen-safe through a `Writer`. The lighting fault at
  **15** (the plan said 12: at 12 the gallery's `00f` read as a dim orange patch at 64 and nothing at 128 by night; at 15
  every top cell keeps 13 or more on level land, it melts snow and ice and keeps new snow off, as the plan's cold-biome
  square wants), grid 3, inset 1, waterlogged in water. Measured (`SelfCreated3KitTests`): 10 or more on 95 % of the
  square's top cells, block light on every one (no monster spawn), and on level land
  outside 11 three out, at most 6 eight out, 0 fifteen out. **The plan's "at most 6 three blocks outside" cannot hold**
  with 10 at the edge (light falls by one a block); the edge is soft and the halo stops spawns about 14 round it.
- **Gallery.** `Gallery.signViews(level, sign, facing[, lookUp])` (`d64` ... `n200`). The kit's dev exhibit `00f`
  (`FaultGallery`, registered with the demo kind; `FaultDemo` lays lit squares and one cut on any land): the fault on a
  hill's flank, a slope, a hilltop cut at a section line and a shallow sea. Read at night: at 64 the lit land reads as
  "lit at noon for nobody"; at 128 the brightest thing in the frame; at 200 a small warm patch (a 24-high hill). A lit
  square on **level** land seen from 1.7 over it at 128 or 200 is edge-on and barely shows: each place's sign needs
  the fault on faces toward the viewer (a cut's faces, a slope, the well's top, the wall's foot, a floor seen from a
  rise). `SelfCreated2RealWorldClientTest` now also shoots each real site's sign views (`build/gallery/<kind>h/d64` ...
  `n200`) and runs `/locate` for every kind from the world spawn (its log line `real locate:`). On SC2-H's seed: s16 at
  1 343 from the spawn, s16d 385, s17 522, s18 2 026 (1.3 s), s19 960 (a mark), s20 1 058, each the kit's kept decision.
  Round 2's pyramid at 200 by night is a bare silhouette there (the "before" of R3-2's sign). Its site picker sites
  without the avoid list and the spacing rule, so a picked site may get no start (it says so: 17 and 20 on that seed).
- **Find (T1 to T4, T6),** 20 default worlds, worldgen's own decisions over the real noise (before is 97f4aee's
  numbers with every chunk taken as decided after Traces, a bound in its favour): T1 within 640 95 % → 100 %, median
  498 → 355 (structures only 356). T2: 16 on land within 800 40 → 65 % (target 85), 17 within 640 90 → 85 % (85), 20
  within 900 25 → 50 % (75), 18 within 1 200 0 → 27 % of the 15 worlds with a mountain biome within 1 200 (60), 19 from
  512 to 768 by construction. T3 77 → 83 % of flights pass within 128 of a sign (70); 98 → 99 % with the modelled
  crumbs (90). T4 (2 000 runs a world): 100 / 100 / 100 % by session 1 / 2 / 3, a sign by session 3 100 % (the model is
  generous: 7 700 blocks a session). T6: 73 → 0 pairs within 96 a world; 3.37 → 3.32 places a km2 from 900 to 3 000 (3
  to 6). Large biomes (2): T1 median 390, T3 85 %. Amplified (2): T1 median 567, T3 56 % (91 % with crumbs).
  **T2 misses for 16, 18 and 20 are their sitings' rates, not the placements** (16 about 20 % of slots made, 20 about
  11 % of candidates, 18 under 1 %, biome first; every bound of D-109 tried): R3-2 and R3-3 are the ones who can move
  them (and the 191 and 192 sizes will make them rarer).
- **Cost.** Per chunk (SC2-H's method, real noise, this Mac with other builds running): the lighting fault's fill
  0.14 ms median, 0.48 ms worst for a lit 16 x 16 into an overlay (warm columns; a chunk's own sections are cheaper);
  the marker placement's check 98 ns a chunk (asked for every chunk); the `avoid` check 2 µs a candidate; a kept
  decision's read 15 ns. Sitings measured during the find run (ms median / worst): 16 made 10.4 / 177, refused 7.3 /
  172; 16d 6.8 / 20; 17 made 15.4 / 144, refused 11.9 / 144; 18 made 447 / 725 (over its 400 budget: R3-3), refused 0.12;
  20 made 18.7 / 47, refused 12.0 / 144. The spacing rule sites a made place's neighbours once (the sitings their own
  starts would have paid).
- **The API for wave 2.**
  - Places (R3-1, R3-2, R3-3): override `Shape.sign()` (default the entrance) with the sign point; draw the sign with
    `Fault.light(w, square, fromY)` (pass the place's own top as `fromY`), `Fault.cut(w, square, topY)`,
    `Fault.lip(w, square, fromY, land, material)`, `Fault.square(x, z, side, minY, maxY)`, `Fault.sectionFloor/Ceil`,
    `Fault.chunkAligned`; an answer that takes lights finds them with `Fault.cells(level, square)` and removes them
    through the `TraceService` (`Fault.lightOf(level, wet)` to put one back by hand). Shoot `Gallery.signViews(level,
    sign, facing)` in each exhibit. A kind gets `Remains.sign(place)` for free (from `Decided`, else the entrance).
    Shape changes still go through R12-2: `SelfCreated3KitTests.everyKindsCurrentVersionStillBuildsAsBefore` pins one
    start of each kind at its version now (s16 v3, s16d v3, s17 v2, s18 v2, s20 v2) by its fill's hash; a kind's new
    version must leave it green. An answer of a place asks `Answers` as before: asleep and creative are already refused.
  - Crumbs (R3-C): `Decided.around(level, x, z, radius)` (every kit place made round a point, decided now if nobody
    asked, kept per 512 square; any worldgen thread), each `Decided.Decision` with `kind`, `chunk`, `start`, `box`,
    `sign`, `facing`, `place()`; `Remains.predicted(level, pos, radius, kinds)` for what is decided so far;
    `Plans.fixLine(key, tag)` / `Plans.line(key)` fix a line's layout once (saved under `Sc2Data` key `c`);
    `Snapshot.spawnKnown()`, `Remains.awake(server)` for 32b and 32c; `Fault` for the near tier's dead ground and light;
    the line's segments from `LineFeature.segments` (19's lines from each gap). `SelfCreated3FindTests` models the lines
    without footing (T3, T4); its `crumbs(...)` is the place to swap in the real planner for T5.
  - Pacing (R3-P): `Remains.awake(server)` for 01+; the find numbers above for `sc01 p` and the reaches.
- **Outside the owned paths** (asked): `src/main/resources/data/a1016_02/worldgen/structure/world/s17.json` (the
  `avoid` field); `world.SelfCreated2RealWorldClientTest` (the sign views and the `/locate` line); `world.Sc2RealNoise`
  (skips markers); round 2's tests where a number moved: `SelfCreated2KitTests` (a start in Alone; a raised stage),
  `SelfCreated2LineTests` (the line in Alone), `SelfCreated2PyramidTests` (rays 6 / 416 / 448, slots per ray, Alone),
  `SelfCreated2HollowTests` (14 / 5, Alone); and `SelfCreated2LineTests`' 20b animals test now waits for its patch in
  real time (`core.FarPatches.until`): on an idle machine it failed the same at the round's base. Two builds green at
  the merge with `self-created` (1 129 game tests). The kit's own exhibit `00f` lives in the kit (the plan said none of its
  own: it is the grammar's, for judging the light).

### R3-B `sc3/books` (wave 1)
- **Implements.** "The books" above, except placement: `Facts`, `DayLog` (saved, rollover, events, lenient, capped),
  `Voice` (loads `b17.json` with a built-in minimal fallback, as `TorchTrail`'s text file; picks by hash; the
  validator), `Composer` (records, editions, day books, today's, the future's blank, the six), `Pages` (wrap, limits,
  vanilla's `WrittenBookContent`), `Books` (the API R3-1 calls: `record`, `edition`, `day`, `blankDay`, `six`,
  `visitLine`).
- **Tests** (`SelfCreated3BooksTests`). Records from fixture facts deterministic per seed and different across seeds
  (60 % of lines at least); page and title limits; no slot unfilled, no line twice in a book; the wrong line at most one,
  never a death, a friend at most once; the author and generation; the day log (rollover, saved and loaded, capped,
  "not kept" days); offline players' snapshot; two and three players (friend lines from a door or chest at another's
  base); the validator over the real `b17.json` once present (parses; every pool at its minimum; lowercase; no `?`, `!`,
  capitals or forbidden words; every slot known; every line fits a page); nothing logged (`LogCapture`).
- **Done.** Build green; R3-1 can call `Books` with no change.

#### As built (R3-B)
- **Package** `world/sc2/s17/books`: `Books` (the API), `Voice` (the texts, the validator), `DayLog` (saved data
  `a1016_02:s2d`), `LogEvents` (what feeds it), `Facts`, `Composer`, `Pages`. Nothing places a block or a stack; nothing
  logs a text (the only lines: `[a1016] s2: b17 is missing`, `... could not be read`, `... has {} entries it could not
  use`, WARN through `ScLog`).
- **The API R3-1 calls** (days are the books' days, 1 the world's first, `Books.today(server)` = `GameClock.day` + 1;
  every method returns a new `ItemStack` and changes nothing; server thread, but `six`):
  - `Books.init()`: registers the day log's events, once (later calls do nothing). **Call it from `S17.init()`**; until
    then the log keeps nothing and records read from the state alone.
  - `Books.record(level, uuid, edition[, wrongChance])`: the dais wall's record of a known player, edition 0 the first
    (one more each 17g rewrite; R3-1 keeps the count per player per archive). An original, title and author the
    player's name lowercase. Pass `sc17.y` as `wrongChance` (default `Composer.WRONG_CHANCE` 0.5).
  - `Books.visitLine(level, uuid, edition[, wrongChance])`: 17g's new record, its last line this visit's (`r`).
  - `Books.edition(level, stackAsItStood)`: the replaced (or carried) record moved to the stacks: same title, author and
    pages, tattered, an edition note (`x`) after its text where a page has room. `Books.edition(level, uuid, edition)`
    composes one when there is no stack.
  - `Books.day(level, day)`: a past day's book from the log; today's ends on a `t` line that breaks off; a day to come
    blank; a day before the log (or a day the log skipped, a time warp) reads `not kept.` (built in: the texts have no
    pool for it). Title `day N`, author empty, original. `Books.blankDay(day)` a day to come.
  - `Books.stale(level, stack)`: true for a day book to replace now (a past day whose text is not the full one, today's
    left blank); `Books.dayOf(stack)` a day book's day (title `day N`, no author), for "idempotent by title".
  - `Books.six(level, name)` / `Books.six(server, name, seed)` (worldgen): the six's records (`Books.SIX`), one of three
    variants by the seed; blank for another name.
  - `Books.isRecordOf(stack, name)`: the title test, any case (round 2's records kept the name's case);
    `Books.title(name)`.
- **The log** (`DayLog`, 44 bytes a day, the last 1 024 days, 255 words, 64 players' snapshots and place days; lenient
  load): fed only while the switch is on, once a second (rollover with the stage at the day's end, weather by worst,
  snow from a participant's biome, the moon and fog at night, online and out at night for the first 32 known players,
  doors and chests used within 8 of another known player's home: watch base when online, else their first block),
  deaths (vanilla's damage-type path and the marks, merged within 40 ticks), what he took (`TRACED` with
  `placedByPlayer`, to the nearest participant within 64), places entered (`REMAINS_ENTERED`), statistics at logout and
  rollover. Ending D and the switch stop it; nothing it keeps is in the world.
- **Composition.** The writer's conventions: entry, `p` and `r` lines carry `day {day}. ` themselves; a day-book line's
  kind is read from its slots; `m`'s `{n}` is any count; "i" at most once a book. A record: opening and closing on a
  per-(world, player) permutation stepped one an edition (none again within the pool's size); 6 to 12 entries ranked
  (marked death, death, friend, door at a friend's, his take, place, days away, night out, home, firsts, mining,
  statistics) with per-edition jitter, one fact of a kind a day, at most 3 a kind, in their days' order, never two of a
  kind together; the wrong line (`p`, never a death) in its day's place on a day they played; dropped to fit 16 pages
  from the least telling. Every pick a hash of (seed, subject or day, kind, edition, slot).
- **Tests** (`SelfCreated3BooksTests`, 15): the plan's list, plus the live wiring (`Books.init` on the test server).
  Fixture `src/gametest/resources/data/a1016_02/world/sc2/b17_test.json` (neutral pools in the writer's conventions).

### R3-W `sc3/words` (wave 1)
- **Implements.** `b17.json` per the schema and minimum pools, in the voice, plus the six's records (three variants
  each). Reads first: this file's "The books", `README.md` on this branch (the voice), `docs/DESIGN.md` (canon and
  guardrails, the fragments table, "What the player can learn"), `docs/STORY_CLUES.md`. Writes nothing else.
- **Checks** (in the report): the counts per pool; a self-check script's result (lowercase, no `?` or `!`, every slot
  in the schema's list, no line over 19 x 13 characters); three sample records and three day books composed by hand from
  invented facts to show the voice.
- **Done.** The file parses and meets every minimum; R3-B's validator passes it at R3-1's merge.

### R3-1 `sc3/archive` (wave 2)
- **Implements.** 17 v3 (the hall 41 high with the floating grid, the well, its top and sign, siting), 17b on the grid,
  17f, 17g, the books' placement (the six at worldgen, records and editions, day books at runtime), `sc 17` debug (`r`:
  counts only), every v2 rule kept.
- **Tests** (`SelfCreated3ArchiveTests`). The shape: the hall's height and decks, every void edge railed (walked by
  `world.Edges`), every step 1 high, the pool 3 deep under the whole void, the floating grid, nothing lit above 30 in the
  hall, the well's light by depth (the lit gallery far down), the top cut at a multiple of 16 and chunk-aligned, the
  missing face from the top to the ground gallery, the kerb and lip in front; worldgen equals `Live.place` for the short
  hall; v2 starts still build v2; siting (deep dark, ancient city, `MAX_REACH`, a 0 to 255 world, a raised floor);
  17b's rows on the grid; 17f (from the top down, never the player's gallery or the one above, caps, glimpse after 3,
  out of view, undone); 17g (after the wall, a new edition with the visit line, the old one in the stacks, the taken
  record's case, once a visit); day books (near galleries only, past written, today unfinished, future blank,
  idempotent); two players (two records, editions per player, the friend line, 17f with both in the well); asleep and
  creative refuse all.
- **Gallery.** `17`, `17l`, `17n` (must stay A), `17w`: `in_1` (from the ground gallery down the void), `in_2` (from
  the pool up), `in_3` (the lit gallery far down, true light), `in_4` (17f forced: the shaft black from the top),
  `survey_1`, `section`; `17t`: `d64` ... `n200`, `aerial`; `17r` (client screens of a record's first page, an edition, a
  day book, a future day: the book GUI, checked for layout).
- **Done.** Build green; the gallery's three rounds; the sign meets the bar at 200; the hall reads "no person built
  this" (the scale figure on the floor, the shelves into the dark); 17n at least as strong.

#### As built (R3-1, `world.SelfCreated3ArchiveTests` 14: 5 of the shape, 8 of the rules, 1 diagnostic when asked)
- **Version 3** (`ArchiveShape.VERSION` 3; R12-2: a start tied to 2 builds 2, the kit's s17 v2 pin unchanged; round 2's
  archive tests now build version 2 explicitly). Variant bits: long hall (0), infested share (1 to 7), the small annex
  (8: the long hall with the short hall's cells when the whole leaves the reach, tried once), the galleries lit from
  the top (9 to 12, `sc17.s`), the world's lowest block + 4096 (13 on). Kept with the place: the tunnels' six numbers,
  the well's cut, front land and lowest block, and which way is open (10 ints).
- **The ways** (kept from v2, with one change for acceptance): the hall is 15 deeper, so its ways rise up to 190 cells
  (150 before) and the first way's exit (dry, a step from the land) failed most; the open way is now the first of the
  three in the columns' order turned 0, 1 or 2 whose exit works (`ArchivePlan.rotation`), and if none does all three are
  sealed and the well's crack is the way in (`sealed`; `entrance()` is then the crack's sill; 17c finds no single way
  open and waits). The open way chosen needs a dry notch (no water in or beside its first cells, its two columns' land
  within 4); a closed way whose notch is not dry keeps no outer part (its exit is its mouth). A lining block facing
  natural air or water (a crevasse beside a way) is stone.
- **The hall** (`ArchivePlan` v3 predicates): 41 high, ceiling layer 42, the stacks three tiers (shelves 1 to 26,
  decks 9, 18, 27 over each side's stacks and aisles; the aisles' upper tiers are pockets with a ladder at each aisle's
  wall end from the floor to the top deck; a rail on every walkable deck cell over a fall, so a fence line at 10, 19
  and 28 across every aisle's mouth and along the whole top deck). The light: single glowstone floating at 17 on the v2
  grid (4 a row apart) over the nave, and full rows of 6 over the open floors (the entry, before and over the dais);
  nothing over the stacks (it would touch a deck). Nothing lit in the top 9 (tested). The theft to a hand over the
  first deck (u 13), full above; the editions' shelves (empty chiseled) in each row's front at 12 from the second row,
  the far end's highest (row 0, 26) for the oldest. 17b works on it unchanged (rows of the floating grid).
- **The well** (`Well`, pure): chunk-aligned 32 x 32 on the side away from the bay, its near edge the first chunk
  border 4 cols + 3 out (clear of the cells), joined by a corridor 3 x 4 from a doorway in the hall's long wall at
  `f` -4..-6 (the plan's "through the entry wall" is where the cells stand). Galleries every 6, the hall's floor one of
  them, 3 wide (fence on the void side), bookshelf faces; one flight of 5 stairs a gallery along a wall (a hole of 3 over
  it), sides turning (never into the hall's doorway, never across the way in); no flight above the ground gallery. The
  pool 3 deep under the whole void, the lowest gallery 1 over it, at least 13 over the world's lowest block (the jacket
  under it keeps the kit's 6), a ladder out. Lights by depth: the top `s` 6 galleries, every other one down to the land
  line, none under it but the lure two thirds down (never the hall's gallery), lit every 3; over the land the galleries
  are also lit as at noon by invisible light blocks (the record room's light). Day books (chiseled, two a wall) from the
  first gallery wholly under the land (`kDays`, days 1 to 48) down.
- **The sign (changed from the plan).** The plan's "32-wide block of cobblestone 20 to 31 over the land, its face toward
  the origin missing" was built and shot first (at 20, then 48 over the land): on the gallery's flat land a wooden
  dollhouse, on the real terrain a high-rise coming out of the ground, which is Mariano's complaint about 1.5.0. The
  open top also lit the shaft by day down to the pool. As built: the well's top is a **2 x 2 chunk piece of the land
  lifted 24 to 40 over its surroundings** (cut on the first section line at least 24 over the highest land), its own
  soil and surface block on top (sandstone under sand), its sides the world's stone with dirt and gravel pockets and
  coal and iron, closed on every side but **one crack 2 wide up the middle of the face toward the origin**, from the
  ground gallery's floor to the soil: the way in, the lit galleries glimpsed through it. The soil roof keeps the sky out
  of the shaft (the depths dark by day). At night the lighting fault stands one out from all four faces on a grid of 3
  (light 15): the piece's stone lit as at noon, the crack's lit slices in it. The apron before the crack (12 wide, 7
  deep): filled flat to the ground gallery's sill (set at or over the highest land and water of 15 samples, so never cut
  beside a pond), dead ground, stone kerbs and lips; a front falling more than 18 under the sill refuses the start.
  The surface block on top from the land's biome (sand, red sand, else grass). `Shape.sign()` is the face's middle.
- **Never a wall in sight** (Mariano's report on 1.5.0, through the coordinator): the body (hall, cells, corridor, the
  well under its cut) is jacketed 4 round every inside, air filled even over the land (crevasse, cliff face, cave), so
  every shell block has the world's ground beyond it; siting reads the land every 8 over the hall, the cells and the
  corridor's strip (each part's top 8 under the lowest of it) and in a ring 6 round them (the jacket's 3) (`floor3`,
  about 150 to 250 samples a made start; a valley, a river bed or a cliff wider than the grid pushes the hall down or
  refuses it);
  the well's outer layer is the world's own (no cobblestone outside); a way's lining block facing natural air (a
  crevasse beside a way) is stone. `noWallOfItInSight`: four real-noise worlds (three default, one amplified), 17
  starts including the 3 SC2-H's survey flagged: no built block (cobblestone, shelves, planks, logs) faces air or water
  the plan did not open; SC2-H's audit no worse than version 2's at the same start (the one leak left, at a way's mouth
  in the amplified world, is version 2's own); the control: the same starts as version 2 show 8 657 such blocks.
- **The books**: `Books.init()` from `S17.init()`; the six's records written at worldgen (v3 only) through
  `Books.six(server, name, seed)` (`Writer.server()`, the kit's one change). The rules (17a with the books, 17g with its
  editions, the day books, 17f, `sc 17 r`) were built by a worker of this task on `sc3/archive-rules` and merged:
  per-player edition numbers in the books' custom data (no new saved data); 17f's cap is 128 glowstone (a lit gallery
  over the land holds about 34; at 64 the glimpse after 3 galleries could never come); at most 64 day books composed a
  look.
- **Config** `sc17` (version 2): `s` 6, `t` 3, `u` 60, `v` 160, `w` 3, `x` 16, `y` 0.5, `z` 2.
- **Numbers.** Find (`SelfCreated3FindTests`, 20 default worlds): 17 within 640 of the spawn 85 % at R3-0's merge
  (version 2's shape), 35 % with version 3's first sitings, 70 % after this task's acceptance work (made 70 then 268 of
  630 candidates of three worlds: the open way chosen, the ways sealed when none opens, the apron a landing, the ring
  covering the jacket only, rivers, beaches, shores and peaks in the biome tag, a mirrored second try), **95 % with the
  structure set's spacing 14 / 5** (was 16 / 6; the plan's bound 14 to 20; R3-0's file, moved here). T3 89 % within 128
  of a sign; T6 no near pairs, 3.92 places a km2 from 900 to 3 000. Refusals left (of 630): the kit's biome 94, the
  apron 72, the reach 50, the deep dark at the well 47, the floor 43, the ways 28. Cost (SC2-H's method: a warm second
  fill into an overlay over the real noise, 19 starts, chunk by chunk, other builds running on this Mac): a chunk's fill
  median 0.74 ms, 90 % 14.9 ms, worst 49.5 ms; version 2 at the same starts 0.33 / 10.5 / 126 under the same load. The
  median within D-118's 8; the worst over its 25 in the well's chunks and the jacketed hall's (a well chunk writes up to
  38 000 cells and seals 4 round its inside; the seal's lookups are memoized). A made start's siting reads about 150
  to 250 land samples for the cover, 40 for the well, and the ways'; a refused one may try once more mirrored. In a
  world from 0 to 255 an archive needs the land 75 over the whole of it (SC2-H's survey found none in its worlds of that
  height); the deep dark's check is three biome reads at the well.
- **Gallery** (`17`, `17l`, `17n`, `17w`, `17t`, `17n2` = version 2's 17n to compare; the real terrain's `s17h`):
  eight rounds and five real-terrain runs: R1 the plan's open face 20 over the land (a dollhouse; invisible by night),
  R2 at 48 (a tower; the open top skylit the shaft to the pool), R3 the soil roof (the depths dark, the lure's ring far
  down), R4 the full set (the hall's floating lines, 17n against v2's 17n2), R5 the lifted piece with a 4 x 4 cut (a
  door), R6 the crack and the lit faces, R7 and R8 the full set and the cameras. At night 200 out the mod's own night
  fog at Traces (about 130) hides every place, this one too; at 128 and 64 the lit faces are the brightest thing in the
  frame. The real terrain (`s17h`, REAL_SEED, before the spacing change): a grey grass-topped block standing in a river
  by a desert, no cobblestone in sight from the aerial or the four sides; later runs' picked s17 sites yielded to
  another place in real worldgen (the picker sites without the kit's spacing rule, R3-0's note). Not done: `17r` (the book GUI's client screens: the gallery has no
  screen capture).
- **Outside the owned paths**: `world/sc2/kit/Writer.java` (`server()`, for a shape's contents);
  `worldgen/structure_set/world/s17.json` (spacing 14 / 5, above). In the gametest s17 package: round 2's
  `ArchiveTestKit` builds version 2 explicitly; `ArchiveRulesTestKit`'s friend line is asked only on a day the two
  players had alone (the books' day log is shared by every game test). Two builds green at the merge with
  `self-created` at `0ac6f1c` (1 189 game tests; a third build in between saw R3-C's `itCameCloserMovesTheFarthestFarPostTowardTheBaseAndTheSpurGrows` fail once on "the spur's second step: no footing", green before and after).

#### Fixed in review (FIX-R3-17: R3A-1, R3A-8, R3A-11, R3A-12; the kit's cost)
- **R3A-1, the well's top (archive version 4,** `ArchiveShape.VERSION` 4; R12-2: version 3 builds as before, pinned in
  `SelfCreated3KitTests.PINS_R3` with every kind's round 3 version). The top is a piece of the land shoved up, not a
  lid: each column's surface stands its own land plus the lift (16 at least over the lowest of the footprint's 25
  samples, more only where a gallery over the ground gallery needs it, at most 24 over a column's land; the land's own
  bumps, 0 to 2 in patches, on flat land too), so on a hill its top is the hill's relief. Grass (sand, red sand) over 3 to
  5 of soil in patches right to the faces; under it and over the land the outer three layers are a section of the world:
  stone with blobs 5 to 8 across (andesite, diorite, granite, gravel, near the top dirt) four cells in five, coal and
  iron, and one or two ragged cave mouths (6 to 10 wide, 2 deep, their backs stone) in the back or side faces, never the
  crack's (a hole there read as a lit window). No cobblestone over the land. Short grass and a few flowers on top; what
  a tree of the land left over it is cleared. The crack and the night's lighting fault on all four faces are kept (the
  lights now up to each column's surface). Kept numbers: version 3's ten, the lift and the crest. The plan's "cut on a
  section line" is dropped for version 4: a lifted relief has no single cut. In the gallery's flat land it stands 19 to
  21 over the land (`17t`; version 3's sign is `17t3`).
- **The apron is a talus** (R3A-1's point 4): in front of the crack and one a side the land is brought to the sill, then
  slopes one a block out and to the sides within 24, ragged (past the crack's own step a column now and then one lower):
  scree (gravel, stone, andesite, a little dirt and granite) where the land is lower, a notch where it is higher (its cut
  stops at water and beside it), the land untouched where it already lies on that slope. No coarse dirt, nothing raised
  on land at the sill's height, nothing framing the crack. The fair edge (D-097.7): a stone kerb on the higher column
  wherever the talus and a neighbour stand more than 3 apart. Siting: version 3's apron rule plus 10 samples on the
  talus's rim (no more than 3 under its foot); the ground gallery is always at or over the front land and its water
  (version 4's first cut let a capped lift push it under a pond: the burial test's audit caught 2 leaks).
- **The apron kerb tests (R3-1's open item):** `theApronsKerbsGuardAFallingFront` (version 3) and
  `theTalusGuardsAFallingFront` (version 4) build over a front falling 12 two out and all along one side, and walk it
  with `world.Edges` from the crack: no unguarded drop of more than 3. Also `shapeOfTheLiftedWell` (version 3's checks
  plus the piece 16 to 26 over its land, the land's top at every face column, no built block in a face or a cave's back,
  two of andesite, diorite and granite in the faces, a cave, the faces' lights) and `worldgenEqualsLiveForTheLiftedWell`.
  The burial test (19 real-noise starts) is green at version 4: 0 blocks in sight.
- **R3A-11:** version 4 empties every shelf a hand could reach (to the first deck's reach), a board here and there low
  down; the colour only above (`17/in_hall.png`). Tested in `shapeOfTheLiftedWell` (no full shelf in reach, over 1 000
  above).
- **R3A-12:** `sill_night` 1.7 over the sill's level 8 out, the scale figure beside the crack (version 4).
- **R3A-8, the books' screens (`17r`):** `world.sc2.s17.ArchiveBooksClientTest` (a fabric client game test, run by
  `./gradlew gallery` when `-Pgallery` lists `17r` or `all`): a flat world on day 6 with five days in the day log, vanilla's
  book screen opened on a player's record, an edition, a past day, today and a day to come, shot page by page into
  `build/gallery/17r`, every page split with the client's own font at the screen's 114: no page past the 14 lines it
  shows, no word wider than a line, 1 to 16 pages, today's last line unfinished, the edition tattered; the tooltips in
  `17r/books.txt`. Seen: the texts wrap cleanly in vanilla's pages and read in his voice; the record reads "by <name>",
  the edition "Tattered"; **a day book shows no author line at all** (vanilla hides a blank author: R3A-8's "by alone"
  is not what the game shows); today's book breaks off ("today player0 went into the").
- **The kit's cost** (the orchestrator's 25 ms; `SelfCreated2HeightsTests`' survey, `-Da1016_02.sc2survey`, 8 a centre,
  the same eight worlds before and after, now in the survey thread's CPU time, which the machine's other load (three
  workers, load 9 to 30) does not change, after each kind's first start (the code warm); before is self-created at
  07d6426 with the same survey):

  | kind | before: median / 99.9 % / worst ms | after | wall time after (median / worst, under load) |
  | --- | --- | --- | --- |
  | 16 | 7.83 / 22.3 / 35.8 | 2.44 / 16.0 / 24.8 | 2.47 / 41.1 |
  | 16d | 5.47 / 17.7 / 19.9 | 1.83 / 4.9 / 11.0 | 1.83 / 11.3 |
  | 17 | 0.81 / 57.5 / 77.9 (v3) | 0.14 / 9.5 / 19.4 (v4) | 0.16 / 31.2 |
  | 18 | 0.05 / 5.7 / 13.8 | 0.04 / 6.2 / 12.8 | 0.03 / 20.6 |
  | 20 | 9.88 / 44.2 / 57.6 | 3.58 / 16.6 / 34.1 | 3.63 / 34.6 |

  After is this branch merged with FIX-R3-B (its 18, 19 and 20 included). The archive's own burial test: a chunk's fill
  median 0.08 ms, 90 % 0.47, worst 10.2 (wall, 19 real-noise starts; R3-1's version 3: 0.74 / 14.9 / 49.5).
  The profile (a flight recording of the survey) put 87 % of the fills' time in the noise's heights: a column read as the
  generator reads it about 0.2 ms, the per-thread whole chunks thrown away after 4, every chunk's fill reading its
  neighbours' again. Now (`Terrains.Noise`): a land's grounds and surfaces are kept per chunk for every thread and start
  (4 096 chunks a land); in each 4 x 4 of a chunk the first 6 columns are read one at a time (a strip along an edge, what
  a neighbour's fill asks, stays that way), then the 4 x 4 at once, the chunk whole after 3 of those or when a writer
  fills half of it; each read (`NoiseVolume.heights`) goes from the noise's top down to each column's ground only (a
  chunk's heights 4.3 ms of CPU against 7.6 for its blocks whole, a 4 x 4's 0.5). The same numbers as the generator's own
  columns (`theNoiseTerrainReadWholeAnswersAsColumnByColumn`, 7 080 columns in five worlds). In worldgen the `Writer`
  reads and writes the chunk itself (no lookup and write-zone check a block; a block entity or a point of interest still
  through the region). The archive's `natural()` no longer reads a noise column for its top block (the noise's ground is
  its default block). **Open:** 20's worst chunk (34 ms of CPU) is
  the floor's own work (5 ms of it the noise), FIX-R3-B's; 16's (24.8) reads two other chunks' heights and 65 more
  columns from one chunk's fill (`PyramidShape` round its canopies and floors), so it sits at the budget's edge.
- **The real terrain:** `SelfCreated2RealWorldClientTest` takes `-Da1016_02.sc2real.only=<kinds>` (one window for one
  kind's fix, no crumbs), shoots the archive's /locate site (`s17h_loc`) and two picked archive sites (`s17h`, `s17h2`;
  on both seeds tried the picked sites got no start: the picker sites without the spacing rule). SC2-H's seed, /locate's
  archive at [2, 35] on a hill by a river: its top follows the hill's slope; at 64 and 128 it reads as a piece of the
  hill shoved up, its faces beside the land's own stone faces. Seed 1, /locate's archive at [29, 29] on a hilltop: a
  block of the land about 20 over it, bumps and grass on top, granite and diorite in its faces, the crack and its scree.
  0 cells changed after either was built. Before FIX-R3-B's merge the line (19) ran through the archive at [2, 35] (its
  obsidian filled 5 124 of the archive's cells, the hall and the well among them, and stood over it to the sky);
  FIX-R3-B's line no longer crosses it there, but the line still avoids no place.
- **Builds:** two green at the merge with `self-created` at 2fb22c9 (1 210 game tests). Gallery: `17`, `17w`, `17t`,
  `17t3`, `17r`, `s17h_loc` (two seeds).

### R3-2 `sc3/pyramid` (wave 2)
- **Implements.** 16 v4 (191 and its siting, the strip, the fault, the apex light), 16d's way in and 16b there, 19 v3
  (distances, lengths, the lit foot), keeping `LineMarker` with `LinePlan`.
- **Tests** (`SelfCreated3PyramidTests`). The 191's shape and steps, the size fallback, within `MAX_REACH` and the
  height; the strip and fault on open and sealed; v3 starts still build v3; 16d: the sill over the water, the flight's
  steps, no water in, 16b at the waterline; 19: the new distances, the fault at every hole (both sides, water), the
  marker found by `/locate`.
- **Gallery.** `16`, `16x`, `16h` (191), `16s`, `16l`, `16d`: the standard views plus `d64` ... `n200`; `16d`'s
  `mouth` and `in_1`; `19`: `n200` along the line, `n64` at a hole.
- **Leaves the hook for R3-D** ("33", "The hook"): the seam `deep/Depths` (a stub), its four calls in `PyramidShape`
  (constructor's last statement, `box()`, `fill`'s last statement, `interior()`), the accessors, `Depths.init()` last in
  `S16.init()`; `VERSION` 4; its v4 tests on `start.withVersion(4)`. A test that `Depths.NONE` leaves v4 unchanged.
- **Done.** Build green; the gallery's three rounds; the night sign brightest in its frame at 200; the hook in place.

#### As built (R3-2, `world.SelfCreated3PyramidTests` 11, a siting survey of its own when asked)
- **16 v4** (`PyramidShape.VERSION` 4; versions 1 to 3 build as before: the kit's pins green). The 191 (`BASES` gains it;
  96 high, its pit 88 deep, its grounds `GROUNDS_HALF_191` 32 past it so its box stays within `MAX_REACH` on a chunk's
  middle). The sign: on the way in's face the chunk column holding the mouth (`inStrip`, from 2 out of the centre so the
  top level stays whole) stands `p` 1 back (variant bits 5 and 6: a start keeps its depth) and is laid fresh (no wear,
  damp or missing brick); the lighting fault (`litCells`) over that strip and the chunk at its foot (`litBand`), none
  within `MOUTH_DARK` 3 of the mouth (it stays black); sealed ones the same, no mouth. The apex light `f` 0.75. On land the
  foundation and the plinth reach down to the land as deep as the box allows (`foundationDepth`; versions 1 to 3 keep
  24), so neither hangs over land that falls away.
- **The trail** (after Mariano's playtest: inside a pyramid "it seemed to have no purpose"): a torch on every 5th tread
  of the pit's far face on the way in's line, from 3 under the rim to the last ring over the floor, and the floor's own
  torch (`trail()`): the first thing seen inside, someone's lights going down to where the dead end runs on (R3-D's way).
  On the near face it was hidden under the rim (gallery round 3). Sealed ones have it too.
- **16d v4.** The way in at the waterline (`wayLayer`: the sill one over the water, the land's two rows of three that many
  courses up), into the dry core; from the inner mouth a 2-wide flight to the sand floor, each step one down and one in,
  solid under, kerbed both sides one higher (`flightMass`); the apex light always (`o` 1.0); the water at the strip's foot
  lit (waterlogged). 16b works there unchanged; `S16.dug` asks the shape (a v4 sea form is not closed). The sea form is
  never sealed from v4; a sea too shallow or too deep for the way in keeps it closed (`wayLayer` 2 to r - 11).
- **Answers after the playtest.** 16e (`world:s16/trail`): on a place's first visit, after `r` 6 s inside or a step down,
  the trail's lights go one every `s` 4 s or more, the farthest first, each out of view, never within 8 of the player nor
  3 of anyone; counted once a place ever (2 a player a day), the rest its parts. 16g (`PyramidOpening`, `world:s16/open`;
  not a kit answer: nobody is inside yet): a participant `t` 20 s within 20 of where a sealed one's way in would be finds
  it open once they look away; once a place ever, only awake, ledgered. 16b at `g` 40 s and `h` 12 (were 90, 20); 16c from
  `j` 6 steps, a light every `k` 5 to `l` 8 s, the glimpse after `n` 3 (were 12, 8 to 12, 4). Kit, minimal, listed:
  `Visits.CREATIVE_INSIDE` (a creative player is inside a pyramid as a survival one; D-116 kept for every other kind) and
  `Answers`' first-hour rule skips a creative player. D-108 holds: under Traces nothing answers.
- **Siting** (T2): per size the centre at the slot chunk's middle, else 16 or 32 off it (within reach); the spread `c` 16
  for the 63 and 95, `q` 24 for the 127, 40 for the 191 (its 7 x 7 keeps the foundation rule); per spot the kind's biome
  tag (`world/s16` gains dark forest, old growth birch, cherry grove, the windswept ones, wooded and eroded badlands, ice
  spikes, grove, snowy slopes; jungle, river and swamp stay out), the land 10 and 24 out round the foot never under where
  the plinth ends, a 7 x 7 under every size; the other form's middle refused at once; samples read once (`Samples`), at
  most 40 a size and 128 a slot. Config `sc16` v4 (`d` four weights, `f`, new `o` to `t`, the lowered 16b and 16c
  numbers, `keepOrNew` from v3).
- **The depths hook** (R3-D's spec): `world/sc2/s16/deep/Depths` (stub, `FROM` 5, `NONE`); `PyramidShape.depths` set
  last in the constructor, `box()` encloses it, `fill()` calls it last, `interior()` holds it; `tunnelEnd()`, `right()`,
  `seed()`, `size()`, `footprint()`; `S16.init` ends with `Depths.init()`; the v4 tests build on version 4 explicitly.
- **19 v3** (`LineShape.VERSION` 3; versions 1 and 2 as before, a v2 copy cell for cell the same but the lights): at every
  opening the lighting fault 8 along by 4 out on each side of the wall (waterlogged on water) and three lights up each face
  of the wall's foot beside it; the box 4 out (`LIT_REACH`), the wall and its dead ground 2. The distances are R3-0's;
  `LineMarker` reads `LineFeature.segments`, so it stays in step (tested).
- **Measured.** SC2-H's survey (`-Da1016_02.sc2survey`), base → now: s16 61 made, 38 clean → 112 made, 96 clean; s16d 92
  made, 78 clean → 108 made, 88 clean (floating foundations over sea trenches, as before); every height world green.
  Siting (survey) made median 19 ms, worst 112; refused 9 / 52 (the plan's 15 ms is over: up to 128 reads a slot, once a
  world a slot); a chunk's fill median 7.0 ms (6.7 before; worst outliers about 200 ms on a loaded machine, as before).
  R3-2's own siting survey (`-Da1016_02.sc3pyr`): a land 16 within 800 in 13 of 20 worlds before, 18 of 20 now. The
  find test (`-Da1016_02.sc3find`, 20 default worlds): T2 16 on land within 800 65 % (R3-0) → 90 % (target 85); T1 100 %,
  median 346; T3 84 % / 99 %; T6 0 near pairs, 3.35 places a km2; 17, 18, 20 as R3-0 left them (not this task's); large
  biomes 100 %, amplified 0 → 50 %. 19 from 512 to 768 on each half-axis by construction. Real world (`-Da1016_02.sc2real`): `/locate` finds s16 439 from the spawn (1 343 before).
- **Gallery** (3 rounds for 16, 16h, 16d, 19; 2 for 16x, 16s, 16l; notes in each `NOTES.txt`): sign views on every
  exhibit, `16h` (the 191), `16d`'s `water`, `water_night`, `in_1`, `survey_2`, `in_0` and `in_0_night` (the trail),
  `air_night`, 19's `hole_64` / `hole_n64`, `along_night`, `air_night`. By day the 191 is the sign (a wall to the sky);
  the strip reads at 64 only. By night the mod's own haze (Traces, fog end about 180) swallows 200 for every place: the lit
  band reads at 64 and at 128 (on the real seed the brightest thing in the frame), never at 200 in vanilla rendering (a
  shaderpack's fog differs, D-102): **the plan's "brightest at 200 by night" cannot hold under the mod's haze.** 19's lit
  squares on level land are edge-on from a walker's eye and lit obsidian stays dark: its lit feet read from near and from
  the air only. The 191's hall is so deep that vanilla's fog glows faintly on its far walls.
- **Outside the owned paths** (asked): `world/sc2/kit/Visits.java` and `kit/Answers.java` (the creative rule, two lines
  each, after the playtest), `SelfCreated2PyramidTests` (the siting's moved spots, the sea form's ocean and way in,
  version 3 rigs with round 2's numbers). Merged with `self-created` (its R3-D addendum); two builds green in a row
  (1 140 game tests); one earlier run failed `dig`'s `BlindGameTests` once (`NO_TORCH`, not this branch's code).

### R3-3 `sc3/hollow` (wave 2)
- **Implements.** 18 v3 (the summit cut, the piece, the foot), 18c; 20 v3 (fullbright, 192), 20b v2.
- **Tests** (`SelfCreated3HollowFloorTests`). The cut's square and its no-wall rule (`world.Edges`); the piece's blocks
  match the cut, floating, its fault; the foot; v2 starts still build v2; 18c (quarters, out of view, once a day, undone,
  31c's fit unchanged); the floor's fault (edge measured), the 192's edges, 20b v2 (torches, chunks from the far edges,
  never the player's or the one toward the nearest edge, the face, the glimpse after 4, caps, undone); two players; asleep
  and creative.
- **Gallery.** `18`, `18w`, `18p` (`in_1`: the piece overhead from the grove, true light; `in_2`: 18c forced), the sign
  views; `20`, `20n` (`n200`, `n128`, `near` at midnight; frames `20b` with the dark coming in), the sign views.
- **Done.** Build green; the gallery's three rounds; 31c's trunks shot unchanged (A).

#### As built (R3-3, `world.SelfCreated3HollowFloorTests` 10, of them 3 when asked)
- **18 v3** (`HollowShape.VERSION` 3; version 2 builds as before, pinned by the kit's hash test and
  `startsOfVersion2StillBuildVersion2`).
  - **The land read whole** (`s18.HollowLand`): the grid's tops from one strided volume of the noise's final density a
    tile (16 x 16 points), the first block over 0 from the top, the generator's own answer (equal on 3 024 points of
    three real worlds, `theHollowsLandReadAVolumeAtATime...`), clamped under the floor less 8 or the sea (under the sea
    the aquifer's barriers stand solid where the density is not). Kit: `Terrains.NoiseLand`, `Terrains.noiseLand`.
  - **The summit cut**, not on the highest chunk as planned: from the side the way in looks out of, the peak is 250 to
    330 off at `d200`, past the fog at render distance 16 (gallery round 1: nothing seen). The chunk on that side with
    the most land rising behind it (up to 16), then the highest: its cut is a square bite out of the slope facing the
    viewer, the missing chunk. Depth: config `m` {8, 14} into the variant (bits 20 to 23), then down to the section line
    under it where that is within 20 of the top (D-110's flat tops on a section line). The grid is lowered to the cut
    and the ceilings made again, so the shell under it is `d`. The ring round it gets a lip a block over the land coming
    down to it (one block was level with a slope rising away: `world.Edges` found a 20-block drop).
  - **The piece**: what was cut, upside down, its surface face down (plants, snow layers and anything that falls left
    out, leaves made persistent), 16 to 24 under the inside's lowest top over the square or across its middle, its
    lowest at least 10 over the floor; where the inside is low, the cut's top 6 to 16 layers only
    (`Summit.pieceSkip`). Only over a chunk wholly over the inside (up to 48 tried); else the cut alone (10 of 86 in the
    probe). No columns through it, no opening near it. Its lights on the fault's grid under its lowest blocks, 15.
  - **The foot**: two chunks by two (the plan's one chunk read as a dim spark at `n128`), round the chunk corner nearest
    the mouth; dead slope (60 % gravel where something solid is under it, coarse dirt; plants, snow, logs and leaves
    gone) under `Fault.light`. The sign point is the mouth (the default).
  - **The way in**: the square's line first, then the other lanes in the order the grid's land says is cheapest, the
    first the terrain holds (round 2 read all thirteen and took the cheapest).
- **18 siting** (`HollowSiting`): the biome tag holds the land's hills (taigas, forests, plains, savannas, badlands,
  cherry grove, jungles, snowy plains, with the peaks and slopes): the rise decides what is a mountain. With the peaks'
  tag alone the probe made 9 hollows in 20 worlds' reach (2 657 of 2 723 candidates refused by biome); with the hills'
  85. So the find's "worlds with mountains" is now every world. Five samples first (the middle and the edges' middles
  within 12: flat); the way in may look out of the second side toward the origin.
- **18c** (`HollowAnswers`): a participant within 48 of the square for `n` (60 s): out of everyone's view, the piece's
  lights go in four even runs round the square's middle, 5 s apart (`world:s18/dark`; the first run the counted answer,
  the rest its parts), once an in-game day a hollow; lit again on a later day with nobody inside (its entries undone
  out of view). 31's `trunks` shot as before (the piece is far over the nave).
- **20 v3** (`FloorShape.VERSION` 3): sides 192 (7 x 7 samples, relief 24 to 64), 128, `c`, 64 in turn (config `f` 192;
  the dry middle first, each column read once); `Fault.light` over the whole floor and **its faces**: a lit square on
  level land is edge-on from the plain, so the cut cliffs get lights in the edge columns up to the land's top outside
  and the built sides in front of them (`n128`: a straight lit wall of earth with its bedrock course at midnight). The
  biome tag widened to the grassy hills.
- **The real run** (`-Da1016_02.sc2real`, SC2-H's seed): `/locate` finds s18 at 595 from the spawn (R3-0: 2 026), s20
  as before; the picked hollow sites get no start (the spacing rule), so the run now also builds and shoots what
  `/locate` finds (`s18h_loc`, `s20h_loc`). Its "cells changed if filled again" are the re-fill's, not damage: a
  built hollow cannot make its piece again (its summit is cut: the piece's 966 cells and 23 lights), a built floor's
  lips and kerb rise again (round 2's edges are not idempotent), and a cold floor's snow (worldgen's freeze runs after
  the structure, before the light: the snow melts under the fault's light in the first minutes).
- **20b v2** (`FloorAnswers.askDark`; round 2's `ask` stays for its tests): at night, out of view, the torches go
  (`world:s20/dark`), the free animals turn silent to the dark doorway for `e`; a chunk of lights every `g` (4 s) from
  the far edges, never a player's chunk nor the one toward their nearest edge; after `i` (4) chunks the glimpse in the
  doorway for a player 20 to 40 off; once a player a day, health 10; lit again on a later day with nobody on it; given
  back when switched off (`sc20d`). **`h` is 400, not 64**: the fault's grid puts about 28 lights in a chunk, so 64 is
  two chunks, under the four the glimpse waits for.
- **Find** (R3-0's run on the merged branch, 20 default worlds; before is R3-0's after): 18 within 1 200 **27 % of 15
  -> 65 % of 20** (every world now counts as one with mountains: the tag holds the hills); 20 within 900 **50 ->
  75 %**; 17 stays 90 %, 16 70 %; T1 100 %, median 343; T3 84 % (100 % with crumbs); T6 0 pairs; 3.38 places a km2.
  Large biomes: 18 50 %, 20 0 % of 2; amplified: 18 100 %, 20 0 % of 2.
- **Cost**: a hollow's shape one at a time (`-Da1016_02.sc3cost`, the same starts, one run): version 2 636 ms median,
  version 3 225 ms (the land 197, the way in 49, the summit 6); a whole made siting 269 ms; refused 0.3 ms median
  (biome and the five samples first). The find's own timings are its parallel streams on a machine shared with three
  other workers' builds: 18 made 998 ms median there, but 17 made 42 ms against R3-0's 15.4 in the same table (2.7
  times slower), so they say little; 20 made 75 ms, refused 0.5 ms there.
- **SC2-H**: `SelfCreated2HeightsTests` green in both builds; its survey (`-Da1016_02.sc2survey`) finds nothing in 20 and, in
  18, round 2's kinds only (12 starts with a few floating cells of the walking floor at its edge, over a dip, and 1 leak by
  the steps; `-Da1016_02.sc3audit` builds versions 2 and 3 of 574 starts side by side: 256 and 269 floating cells, none in
  the cut, the piece or the foot). Open for SC2-H's owner, not changed here.
- **Outside the owned paths** (asked): `kit/Terrains.java` (`NoiseLand`); `SelfCreated2HollowTests` (its start helper
  and fewer-points case tied to version 2; the refused biome a desert); `SelfCreated2LineTests` (the floor's round-2
  tests tied to version 2; its siting test at `sideMax` 96); `SelfCreated2RealWorldClientTest` (shoots the hollow and
  the floor `/locate` finds as `s18h_loc`, `s20h_loc`: the picked sites may get no start). Two builds green in a row at the merge with
  `self-created` (R3-2 in it; 1 150 game tests). Gallery: five rounds (18, 18w, 18p, 20, 20n, 31; the real run twice).

### R3-C `sc3/crumbs` (wave 2)
- **Implements.** 32, 32b, 32c as specified. **Tests, gallery** under "32". T5 on the real noise with R3-0's find run.
- **Done.** Build green; T5 holds; the gallery's three rounds; each kind's far crumb deniable alone, its near tier
  unmistakable.

#### As built (R3-C, `world.SelfCreated3CrumbTests` 11, 1 of them when asked)
- **Package `world.sc2.s32`.** `CrumbLines` (the lines and their spots, pure geometry), `CrumbShapes` (the things),
  `CrumbFooting` (footing over the live blocks and over the noise), `CrumbIndex` (the per-256 index and each place's
  lines), `CrumbFeature` (the placed feature), `CrumbData` (saved data `a1016_02:s32`, `data/a1016_02/s32.dat`),
  `CrumbRules` (sightings, 32b, 32c), `CrumbCommands` (`/a1016 sc 32`, `sc 32 a`), `CrumbGallery`, `Sc32Config`
  (`sections.sc32` v1, `a` to `n` as planned), `S32`. Data `worldgen/feature/world/s32.json`,
  `placed_feature/world/s32.json` (`top_layer_modification`, every overworld biome).
- **Lines.** From every made `Decided` place round a chunk (`Decided.around`, a 256 square's lines found once a run:
  the places within 896 of it, each place's obstacles within 752 of its sign) and from each 19 segment's cut (two, square
  to the wall, 512). A from the sign toward the world spawn, B and C at plus and minus 100 to 140 degrees (a hash of the
  start's seed). A line stops 16 short of another place's box (19's planes included), of a big surface structure's
  likely box (`CrumbIndex.villages`: each village, ocean monument, mansion and pillager outpost set's candidate chunks
  whose biome takes one, 72, 40, 48 and 32 round the candidate; the real world's run lost five mid crumbs of a line in a
  village's box and four at sea in a monument's) and where it first comes within
  `b` of the spawn (any line, not only A), and is **cut where it could no longer be followed** over the noise: walking out
  from the sign, at the first crumb that finds footing more than its tier's longest gap (twice the spacing plus 16) past
  the last one that did, it ends at the last one; under 128 it is dropped. Crumbs inside their own place's box (plus 4)
  are left out (B and C often cross their own place: the place stands there). Near every `d` from 64 to `g`, mid every `e`
  to `h` (both dead straight), far from `h` every `f` give or take 8, up to 6 aside. Every other far one lit, every third
  mid crowned (the first included). A line is fixed in `Plans.fixLine` (key `s32/<seed36>/<salt36>/<kind>@<cx>,<cz>/<A-C>`,
  about 110 bytes) the first time any chunk builds one of its crumbs; a place whose three lines are fixed is not planned
  again.
- **The things** (the plan's table; the one-cell-a-position rule makes a crown replace its block). Additions: the near
  tier's dead ground gets a fault light (`Fault.lightOf(15)`) over each corner of its ring (lit as at noon for nobody;
  without it the near tier was a row of faint points at night); 20's ring keeps its grass (a test floor), plants and
  snow off; 18's ring is gravel and dirt; 17's 4 x 4 lies within 2 of the crumb's ground, filled with cobblestone under a
  lower column and the land over a higher one cut (on a slope it floated like a slab on a post), and its hole keeps solid
  walls and floor (cobblestone where the land is not). Over
  water deeper than 1 (at most 40) the mid and near tiers and every 16d crumb stand on a cobblestone pillar from the floor;
  a far one in water is skipped. No mossy cobblestone, no wall torch anywhere (t0rch's and the shrine's grammar).
- **Footing.** The point moves up to 4 along its line, and, to fit its chunk, up to its reach across it (at least one
  block in from the edge, so a canopy hole is cut whole); the first shift that passes every check is built: no step over
  3 along the four rays out of it within 2 of its reach (live and over the noise alike, so a line's cut holds live; a
  square check failed terraced hillsides the model passed), no lava within 8 (sampled every 2 near the surface), fill
  under a lower footprint column at most 3, every own cell of the land (plants, water plants, leaves, snow; never a log,
  a block entity, a made block), out of every structure start's box the chunk references (plus 2: villages, trial
  chambers, his places) and every old scar's footprint touching it. The noise model reads the footprint at its corners.
  Built by its own chunk only (writes only in it), its cells guarded by the kit's `Writer`; the leaves over it (a 3 x 3
  to the sky) taken off. `CrumbFeature.why` tells why a spot is not built (dev runs).
- **Runtime.** `CrumbData` keeps every crumb built (12 bytes: its foot, its number, tier, height, torch): a post (one
  column and its torch, the far and the 1 x 1 mid things) is what 32b may take and 32c may move. Sightings every 20 ticks
  (within 32, a 70 degree cone and line of sight, the crumbs of the chunks within 48). 32b and 32c as planned, through
  `TraceService` batches (`world:s32/gone`, `world:s32/pull`, given back by the switch through
  `Answers.giveBackWhenOff("sc32", "world:s32")` and by Ending D's ledger), only for survival and adventure participants,
  only awake, never a crumb with a block a player placed. 32c's watch: two sightings of a line in a day start ten minutes
  of their play; 160 farther from the place than the nearest crumb seen, or home (24 from their base), owes a move that
  night (13 000 to 23 000).
- **Find (R3-0's run with the real lines, `SelfCreated3FindTests.crumbs` now calls `SelfCreated3CrumbTests.real`; the
  modelled ones with `-Da1016_02.sc3modelled`), 20 default worlds:** T3 83 % of flights within 128 of a sign, **98 %
  within 128 of a sign or 48 of a crumb** (90; 96 % with R3-0's modelled crumbs); T4 100 / 100 / 100 %, a sign by
  session 3 100 % (the model is generous); **T5 15 785 lines, 100 % followable to their place** over the noise (by
  construction: the cut), 1 715 lines dropped short (9.8 %: a near tier on a cliff or water, a structure at the place's
  door), 13.5 crumbs a line. Large biomes (2): T3 97 %, T5 1 738 lines, 100 %. Amplified (2): T3 86 %, T5 643 lines,
  100 %, 126 dropped. **T5 live** (the real world's run, every line of each kind's first real site built by worldgen,
  `SelfCreated3CrumbRealShots`, SC2-H's seed, sites of 16, 16d, 17 and 20): **10 of 10 lines followable**, 128 of 138
  crumbs built (before the four rays, the next shift and the structures as obstacles: 8 of 9). The noise does not see
  trees, lakes or the structures' real boxes: live, a crumb can still miss (a trunk beside every shift, a structure's
  box the candidates did not foresee).
- **Cost** (`-Da1016_02.s32cost`, `VerifySc2CostTests`' method, this Mac): a chunk 0.001 ms median, 0.15 ms p95, 3.6 ms
  worst over 1 096 chunks of six lines' corridors (114 with a crumb: 0.15 ms median, 3.6 ms worst); the rules' pass for
  two participants 39 µs every 20 ticks (about 2 µs a tick). Once a place a run (off the chunk's budget, as a siting):
  planning its lines over the noise 422 ms median, 1.5 s worst under the find run's parallel load (13 601 places; a place
  whose lines are all fixed is not planned again); the index's `Decided.around` sites the places within 896 of a new
  256 square. Saves: about 110 bytes a line fixed (`Sc2Data` `c`), 12 bytes a crumb (`s32.dat`).
- **Gallery** (`./gradlew gallery -Pgallery=32_16,32_16d,32_17,32_18,32_19,32_20 -Pgallery.rd=24`; gallery ids take
  `_`, not `-`), five rounds, then the real world's (`build/gallery/32_<kind>r`). The far crumb alone reads as an odd
  thing in a field (a bookshelf, a brick, an obsidian block, a dirt pillar and its torch, a bare trunk); the mid tier as a
  dead straight row that does not bend for a hill (18's stone pillars five high are the strongest); the near tier as a
  dotted line of lit squares at night converging on the place (`aerial_night`). Real land hides crumbs from a ground eye
  at 50 or more: the real shots stand 14 from each tier's outermost crumb.
- **Outside the owned paths** (asked): `SelfCreated3FindTests.crumbs` and its report (two lines);
  `SelfCreated2RealWorldClientTest` (one call to the new `SelfCreated3CrumbRealShots`); test environment
  `world/s32.json`. No kit change. Two builds green in a row at the merge with `self-created` (`89cad67`, R3-2 in;
  1 151 game tests). One earlier build ran out of heap saving the test world after every test had passed; the crumb
  tests now clear narrower patches of it and it did not come back.
- **Open.** R3-P counts 32b and 32c as the wave's moments (no event is fired for them yet); the noise model cannot see
  trees, lakes or structure boxes, so T5 over the noise is by construction (the live run is the check); planning a
  place's lines is a once-a-run cost of a few hundred ms on a worldgen thread.

### R3-D `sc3/depths` (wave 2, from R3-2's merge)
- **Implements.** "33 The depths": 16 v5 through the seam (the shape, its fit and fallback, the room graph), 33b to
  33e, `sc33`, `/a1016 sc 33`, the exhibits. Branches from `self-created` once R3-2 is merged; starts in the slot R3-2
  frees, so wave 2 never runs more than 4.
- **Tests, gallery** under "33". Every shape rule of the round holds (SC2-H's tests and survey, the real-world client
  run for `s16`, R12-2: v4 and v3 starts build exactly as before).
- **Done.** Build green; the gallery's three rounds; the lure reads from the pit's floor in true light (`in_1`); L1 reads
  as 20's house (`in_2` beside 20's `in_house`); four levels in at least 70 % of land starts, three or four in 95 %; no
  start lost to the depths.

#### As built (R3-D, `world.SelfCreated3DepthsTests` 14, of them 1 when asked)
- **16 v5** (`PyramidShape.VERSION` is `Depths.FROM`, 5; v3 and v4 starts and the sea form keep their dead end, the kit's
  pins green). `deep/DepthsPlan` decides everything once from the frame (`s` right, `f` toward the way in, `P` the pit
  floor's feet) and the seed; `deep/Depths` (the seam) decides the fit, writes, answers `inside` and `levelOf`.
- **The levels as built.** L1 is 20's first house cell for cell (its torches on the side walls a block in from the doorway,
  as 20 has them, not the back wall), an oak door shut in its front doorway (the tunnel's end face) and in its back wall;
  the cell behind each window is stone. L2: 3 x 3 (63, 95) or 3 x 5 (127, 191) on a pitch of 6 under the pit, an oak door
  and a glass pane either side of it (at +-1, so the torch hangs on the plank at +-2) in every shared wall, one of 20's two
  torches a house (by hash). L3: 5 x 5, 5 x 7, 7 x 7, 9 x 9, unlit, no glass; its maze a depth-first spanning tree from
  the stair's house plus n / 6 doorways, 3 in 4 an oak door, the rest empty; the camp 2 to 4 doorways off the way (table,
  cold furnace and chest against the wall facing its broken doorway, wool by the hole; in the chest a stone pickaxe at
  26/131, two bread, a compass, nine cobblestone), all its doorways stuffed (cobblestone and dirt), the one toward the way
  broken through with the planks round it gone and a cobblestone outside; one or two leaf dead ends off the way stuffed.
  L4: a 3 x 2 landing at the foot of the last stair, the iron door shut in a cobblestone wall 3 wide, a step, the end
  house (L1 with its own oak front door, its torch places bare, its back wall gone under its roof), his tunnel 2 x 2 from
  the gap along the facing under the warren (24, 32, 32, 40), a cobblestone 8 before its blind end.
- **The stairs** are 1 wide and **3 high** everywhere (a 1-high step is walked down and up only with three; the plan's 2
  could not be walked), every step 1 down. Fixed routes per size: A from L1's back door out to `f` 20, down 8 sideways
  (to the seed's side `m`), back along a corridor into the rooms' near corner house; B from the rooms' other near corner
  out along the facing over the warren (3 of rock over its roof), down 8 sideways beyond the warren's face, in at its
  facing side; C from the warren's far corner out past its back, down 8 sideways, to the landing. Each descent stands 3
  of rock outside every level; without the rooms A goes down 16 (two flights) to the warren. The rooms' stair down leaves
  from the other corner of their near row (not the farthest house): the warren is entered from its facing side, and no
  route round the 63's footprint would hold the far one.
- **Fit.** In order: the footprint less 3 and the kit's height margin (no read); the deep dark (9 biome samples a level);
  the land, 9 ground samples over the whole of the depths held to 12 over the first house's roof (the highest; one set for
  every level since a ground read computes a noise column); one block under the end where worldgen fills (two more if a
  cave). The rooms alone dropped keeps the rest, anything else drops all. Over the real noise (`-Da1016_02.sc33find`,
  `build/sc33find.txt`): 199 land starts over eight default worlds, **97 % with four levels**, the rest none (the deep dark
  never reached the rooms without the warren: three never came); SC2-H's six worlds: no start lost to its depths (the short
  ones keep a third).
- **Light.** The house's light reaches the tunnel's end through the shut door: 9 there (the pit's own torch alone gives
  4), 11 at the tunnel's mouth either way; no pane needed.
- **Answers** (`DepthsAnswers`, kit ids 33b, 33c, 33d, 33e the shutting, 33f the opening; causes `world:s16/33c`, `33d`,
  `33e`, given back with 16's). A visit is the kit's pyramid visit; a look every 10 ticks, the cues and the walks every
  tick. The room graph (`RoomGraph`) sees straight on through panes and open doorways or doors. 33c's door in the warren is
  the farthest allowed under the last (3, 2, 1, then one in a wall of the player's own house, out of the kit's view only);
  the walk's steps are the player's position mirrored across the wall into the empty house beside (or on the middle line
  of the house between two players), a step every 1/0.6 blocks walked, `h` late, one more when they stop. 33e's glimpse is
  remembered per player in the place's counts (`33g:` + the player). A creative player is inside a pyramid (R3-2's
  `Visits.CREATIVE_INSIDE`), so the answers come to one as to a survival one (the plan's "creative refuses" is older).
- **Cost** (the survey): `Depths.of` a start median 1.9 ms, worst 5.4 (9 ground reads; the plan's 0.5 counted the
  biome samples only); the depths' fill a chunk into an overlay median 0.60 ms, worst 18 (the first chunk works out the
  seal's zones for the shape).
- **Gallery** (`33` a 127, `33s` a 63 without the rooms, `33h` a 191 on land raised 20: the deep flat is too shallow for
  its depths): `in_1` to `in_6`, `in_8`, `section` and `section_deep` (a copy cut along the facing, the end a step deeper);
  frames (shoot `f01`, after the staging): `in_7` and `in_7_end` (33d and 33e forced), `in_9` (the first house dark),
  `plan_1` to `plan_4` (cut from above). Three rounds.
- **Outside the owned paths:** none (the hook was R3-2's; `PyramidShape` only its `VERSION` line and one Javadoc sentence).
  Merged with `self-created` (sc3/crumbs); two builds green in a row (1 175 game tests). Two builds before them failed
  once each on R3-C's `itCameCloserMovesTheFarthestFarPostTowardTheBaseAndTheSpurGrows` ("no footing"): its second move
  lands 160 to 224 from the base by a random player id, and `CrumbRules.spot` skips unloaded chunks, which the test's
  flattened boxes do not always cover (chance, not 33's; R3-C's to make the area loaded).
- **Not run:** the real-world client run for `s16` (`-Da1016_02.sc2real`) and SC2-H's full survey; the kit's pins, SC2-H's
  height tests and the pyramid tests are green with version 5.

### R3-P `sc3/pace` (wave 3)
- **Implements.** "Pacing (D-119)"; folds every task's report into "As built" notes in this file; the cost table.
- **Tests** (`SelfCreated3PacingTests`). The bounds above in the four cases and the three tempos; 01+ asleep; the new
  rows. **Done.** Build green; the tables in this file.

#### As built (R3-P, `director.SelfCreated3PacingTests` 7, of them 1 a sweep when asked; `debug.SelfCreatedDebugTests` +1, `debug.DebugGameTests` +1)
Measured with the dry runs (`SelfCreated2PacingTests`' and `SelfCreated3PacingTests`' runs: the registered deck, 192
seeds x 20 h per tempo, every card at its own dice with `sc01`'s shares); early / slow burn / very late. "Before" is
the self-created tip `0ac6f1c` with round 2's dry-run inputs (its numbers equal FIX-SC2-PACE's table).
- **01+ asleep.** `DirectorImpl.placeFactor` gives 01+ only while the places are awake (`remainsFactorHere`:
  `Remains.asleep` null, then `atRemains`); `/a1016 sc 01` the same. The brain already gave no place factor before
  Traces, so the dry runs only needed their visits, answers and moments from Traces on (they had them).
- **The wave's moments that are no card (the hook R3-C's 32b and 32c needed).** `core.Director` gains
  `waveMomentMay(server, player)` (past the player's own first hour and outside their wave gap `t`; always for a player
  the director keeps no pacing for) and `waveMoment(server, player)` (starts their gap, as a group card's fire does).
  `world.sc2.s32.CrumbRules`: 32b waits while the newcomer or a seer may not have it and starts the gap for all of them;
  32c waits for the owed player (the night goes on, or the next) and starts theirs (three lines in `gone`, three in
  `pull`). 33: `DirectorImpl.onAnswered` now takes the answer's id; every answer below the pit (`33b` to `33f`) starts the
  player's gap again, so one visit's answers there are one moment and the gap runs from the last. 33 is not held back by
  the gap (the player goes down when they choose).
- **The dry runs (`DirectorSim`, `SimFits`).** New event kinds `VISIT`, `ANSWER`, `MOMENT`; `Result.met()`. A visit
  (awake) brings answers: `ANSWERS_PER_VISIT` 1.0 of at most 4, `ANSWER_TENSION` 4 each (the kit's), never in the first
  hour, never in a quiet but the first visit's, at most `ANSWERS_PER_DAY` 6 an in-game day; `DEPTHS_SHARE` 0.25 of
  visits go below the pit (`DEPTHS_ANSWERS` 1.0 more, one `MOMENT 33`). The crumbs: `CRUMBS_PER_HOUR` 0.15 (32c),
  `CRUMBS_SHARED_PER_HOUR` 0.1 with a friend apart (32b, to both), once an in-game day a player, past the first hour,
  outside the gap. Players together share a visit (each rolls its share, so each visits as often as alone). Inputs
  re-derived from R3-0's presence (T1's median 498 → 355, about twice the places near home; signs seen from far, T3 77 →
  83 %): `sc01 p` 0.06 → **0.10**, `VISIT_MINUTES` 8 → 12 (the stacks' well, the depths' levels), 22's reach 0.08 → 0.14,
  31's 0.05 → 0.08, 31c's 0.03 → 0.04 (18 stays rare); `CALLED_NIGHT_OUT` 0.6 kept (R0 384 keeps a place a walk from
  home). Visits 0.45 → 0.50 an awake hour.
- **The tuning.** With round 2's shares the wave from Proximity came to 0.56 / 0.59 / 0.60 real moments a played hour
  (bound 0.55), the crumbs and the depths taking about 0.2 an hour of it. `sc01` version 6 (`keepOrNew`): `p` 0.06 →
  0.10, `s` {0.3, 0.75} → **{0.3, 0.4}** (the minor share; swept 0.4 to 0.75 and 0.2 to 0.3 for the ambient,
  `theSweepOfTheWavesShareAndThePlacesShareWhenAsked`, `-Da1016_02.sc3pace=true`).
- **Numbers** (before → after):

| | Early | Slow burn | Very late |
| --- | --- | --- | --- |
| First hour: first event by 30 / 45 min; two or more | 80 / 94 %, 90 % → the same | 81 / 95 %, 92 % → the same | 81 / 95 %, 95 % → the same |
| The wave from Proximity a played hour (real) | 0.47 (0.40) → 0.52 (0.49) | 0.48 (0.40) → 0.57 (0.52) | 0.49 (0.42) → 0.56 (0.52) |
| The wave in Traces a played hour | 0.36 → 0.53 | 0.32 → 0.51 | 0.33 → 0.55 |
| Most in an hour: the director's (cards, crumbs); with the depths | 2 → 2; 4 (18 of 192 runs had an hour of 3+) | 2 → 2; 3 (29) | 2 → 2; 4 (28) |
| The first real one by Proximity + 1.5 h | 86 → 90 % | 83 → 85 % | 82 → 86 % |
| Five sessions: features met, a hunt; the first hunt's median | 2.90, 48 %, 10.0 h → 3.80, 49 %, 9.9 h | 2.94, 55 %, 8.2 h → 3.99, 57 %, 8.2 h | 3.09, 64 %, 7.5 h → 4.02, 57 %, 8.5 h |
| Traces minors (plain deck) | 0.93 → 0.97 (0.86) | 0.82 → 0.85 (0.82) | 0.85 → 0.84 (0.78) |
| Proximity minors, a major every (plain deck) | 1.08, 1.79 h → 1.05, 1.84 h (1.01, 1.79) | 1.09, 2.14 h → 1.09, 2.16 h (1.03, 2.24) | 1.13, 2.47 h → 1.11, 2.54 h (1.07, 2.71) |
| The traps' first on the accident clock, median (plain) | 4.73 → 4.60 h (4.18) | 5.95 → 5.83 h (5.12) | 7.80 → 7.78 h (6.47) |
| His places: visits an awake hour; answers a visit, an awake hour | 0.50; 0.80, 0.40 | 0.50; 0.81, 0.40 | 0.50; 0.83, 0.41 |
| Answers: most in an in-game day, in a visit; in a quiet past the first visit | 6, 5, none | 6, 5, none | 5, 5, none |
| A sign seen by session 1 / 3 / 5 | 93 / 100 / 100 % | 93 / 100 / 100 % | 93 / 100 / 100 % |
| A place entered awake by session 1 / 3 / 5 | 52 / 90 / 98 % | 45 / 91 / 98 % | 40 / 90 / 98 % |
| An answer felt by session 1 / 3 / 5 | 29 / 80 / 93 % | 31 / 82 / 93 % | 33 / 83 / 94 % |
| Five sessions: 32 met, 33 met (new rows) | 64 %, 51 % | 65 %, 52 % | 65 %, 54 % |

  Two players together (`DirectorMultiplayerTests`): 0.98 / 0.99 / 1.01 of one player's minors (bound 0.95; 0.89 in
  the early tempo before the shared visits). DESIGN 4b's rates and the majors within 15 % of the plain deck in the four
  cases (the closest: the early tempo's Traces minors with the dread share, +13 %); the traps on the accident clock within
  a quarter (+10 / +14 / +20 %).
- **A dense world** (`aDenseWorldKeepsItsAnswersToTheirCaps`, `sc01 p` 0.3: visits 1.52 an awake hour, three times the
  dry runs'): answers 0.72 / 0.74 / 0.77 a visit and 1.09 / 1.12 / 1.16 an awake hour (bound 1.5), at most 6 an in-game
  day (the cap) and 6 a visit, none in a quiet past the first visit's; the director's two an hour holds; with the depths
  at most 5 / 4 / 4 in an hour; the wave from Proximity 0.60 / 0.62 / 0.66 real (above the dry runs' bound, as a world
  that dense would be). The live kit (`aDenseWorldsAnswersKeepThePerPlayerAndPerPlaceDayCaps`): six places 40 apart, one
  player through all of them in a day: 33b, 33c and the common rule once each; a second player in the first place has
  none (its day cap), in the next one his; the next day again; in a quiet the first only.
- **Checks.** `DirectorSim.check` starts the gap on a `MOMENT` and flags a crumb one inside it; `PlaythroughCheck.Limits`
  carries the group's cards and gap, a hard check "at least 30 min between the second wave's moments" (33 only starts
  it); `DebugGameTests` expects 8 hard checks when the layer paces. `SelfCreatedDebugTests` holds the index and the
  overlay's layer rows to `SC3` and `SC3_GENERIC` as whole words ("fault" in "default", "hanging" in "changing", "lit" in
  "split" pass; `day` is watched by hand, 04's line uses it); the plan asks for no index line or overlay row of 32 or 33, so none was added.
- **Builds.** Two green in a row at `af95b06` (1 184 game tests), merged with `self-created` at `0ac6f1c`: R3-1
  (`sc3/archive`) was not in it yet; it merges with this branch without a conflict, and its answers (17a, 17f, 17g, the
  day books) go through `REMAINS_ANSWERED` as every answer the dry runs count.
- **Outside the owned paths:** `core.Director` (the two defaults), `world.sc2.s32.CrumbRules` (the hook's six lines),
  `debug.PlaythroughLog` (the new kinds' lines), `debug.DebugGameTests` (the limits' constructor, the count, one test).
- **Open.** 33 is the one moment of the wave no gap holds: a player who goes below a pit twice in an hour, or right
  after two of the wave's moments, has three or four in that hour (10 to 15 % of 20-hour runs have one such hour). The
  hook is ready: 33b's and 33c's start asking `Director.waveMomentMay` would hold them like the crumbs (R3-D's code;
  FIX-R3's call). The dry runs' answer and crumb chances are reasoned, not measured in play. The crumbs' rules had no
  first-hour rule of their own; the hook now gives them the player's (a player the director keeps no pacing for is not
  held). The Traces rate of the wave rose with the places' moments (they wake at Traces, D-108).

### R3-A review (wave 3), FIX-R3 (wave 4), R3-V (wave 5)
- **R3-A**, the art director (as R11), over `./gradlew gallery -Pgallery=all` on the merged branch and the `-Pgallery.iris`
  shots of the signs: ratings A to D for every exhibit of the round, the signs at 64, 128 and 200 by day and night, the
  crumbs' tiers, the books' screens, 33's descent (`33`, `33s`, `33h`); what R11 rated A must still be A. A horror critic's pass (as R10) is optional, the
  orchestrator's call.
- **FIX-R3**: the findings by workstream (kit, archive, pyramid and line, hollow and floor, crumbs, pacing).
- **R3-V**, the verifier: three green builds; the find targets (T1 to T6) re-run; `/locate` for every kind in a dev
  client; the pacing tables against the tests; the spoiler surface (config, commands, `/locate` output, logs, the jar's
  resources); a 1.5.0 world opened in this build (old chunks unchanged, new land with places and crumbs, half-generated
  places finished in their version, its pyramids keeping their dead end); the LAN pair's steps for the books (two
  records, editions, the friend line), 32b and 33 (steps in the house between two players, the iron door kept open by a
  friend on its near side); a checklist for Mariano's real install without spoilers.

#### Fixed in review (FIX-R3-C: the pyramid, the depths, the crumbs)
- **16 v6** (`PyramidShape.VERSION` 6; versions 1 to 5 build as before, a v5 sea start cell for cell as v4: tested).
- **R3A-5, the first house's window** (`Depths.WINDOW_FROM` 6): in L1's front wall beside the shut door, the tunnel's
  side-1 cell at eye height is a glass pane (`Depths.window()`), and the back wall's glass on the same line is 20's plank
  (through both, the stone behind the back glass showed black: the window read dark). From the pit's floor the tunnel
  now ends in a shut door with a lit window beside it; after 33d it is black (`33/in_1`, `33/in_9/f01`). No answer, no
  ledger.
- **R3A-9, the stuffed doorways** (COULD, version 6): dirt low, cobblestone on it, the plank over the doorway torn out
  and dirt packed in its place (no opening: `inside` and the leak test unchanged; `33/in_4`).
- **R3A-6, the sea pyramid** (version 6 at sea, `PyramidShape.seaTrail()`): a torch on the flight's right kerb every 5th
  step from the 3rd, then the floor's torch on a cobblestone on the core's sand floor, 5 left of the flight's line under
  the middle (`floorLight()`); 16e takes them as on land (farthest first, out of view); 16c counts steps down the flight
  (`stepsDown`, `hasDescent`, `descentSteps`) and its glimpse after `n` is on the floor two from that light
  (`PyramidAnswers.floorSpots`); the lighting fault over the top 8 courses (`capBand`). Gallery `16d/in_1_night`,
  `16d/in_floor`. The lit top reads at 64 from a side (`16d/side_n64`, as the strip does from the front); at 128 by night the haze still
  takes the apex from every side (`16d/n128`, `16d/side_n128`), as R3-2 found for every place under the mod's haze.
- **R3A-7, the crumbs** (`CrumbLines.VERSION` 2; version 1 lines build as before): among the shifts that hold, the
  highest footing (`CrumbFooting.RISES_FROM`), a far crumb up to `FAR_SHIFT` 8 along; every second mid crumb crowned;
  `CrumbFeature.spot` builds the chosen shift's cells (it kept the last accepted); version 2's canopy hole from the
  crumb's foot up. The real run's sight check (`SelfCreated3CrumbTests.nextInView`, validated on open land): the next crumb in view
  by day from beside a crumb, over 12 lines of the real seed's first 16, 16d, 17 and 20 sites: near 70 % (31 of 44), mid
  63 % (34 of 54; the review asks 70), far 27 % (8 of 30; asks 50); one `from_crumb` shot a kind (`32_*r`). No version 1
  baseline (a run builds one version). Live T5 12 of 12 followable. The find run (`-Da1016_02.sc3find`, 20 default
  worlds): T1 100 %, median 355; T3 sign 90 %, sign or crumb 100 % (98 before); T5 20 311 lines, 100 % followable, 1 941
  dropped short (8.7 %), 11.9 crumbs a line; a place's lines planned in 158 ms median, 1.24 s worst.
- **The flaky crumb test** (`itCameCloser...`): a fixed player id and a flattened, loaded corridor along the whole line
  from the base (the second move lands up to 224 + 4 out).
- **Cost** (`SelfCreated3PyramidTests.theFillCostPerChunkWhenAsked`, `-Da1016_02.sc3fill=<worlds>`, as FIX-R3-17
  measures: each chunk's first fill into an overlay in the thread's CPU time, each form's first start left out; 8 default
  worlds, FIX-R3-17's kit merged): a chunk's fill, 16 (with its depths) 3.88 ms median, 99 % 7.05, worst 16.9 (FIX-R3-17's
  survey had 24.8); 16d 1.53 / 4.78 / 9.6; 33 alone 0.06 / 0.26 / 0.39. 32 on real worldgen (the real run,
  `-Da1016_02.s32cost`, 4 218 chunks with lines) 0.002 ms median, 99 % 0.54, worst 8.6. The margin for 16 is the slot's:
  version 2's cut in front of the way in worked out its length from the land of every column 24 out, and read the land
  of its floor and wall columns, in every chunk's fill (FIX-R3-17's "two other chunks' heights and 65 more columns").
  Now `slotEnd` is read once in the constructor and a fill skips the slot's columns outside its clip (nothing of them
  was written there). Blocks unchanged: `theSlotFillsAsBeforeWithoutReadingTheChunksInFront` (fill hashes of real
  starts of versions 3, 5 and 6 with a slot, the old path behind `PyramidShape.slotAsBefore`; no chunk the slot crosses
  reads a height outside itself on its account). The survey lists who reads outside a chunk over 12 ms
  (`OutsideReads`): none of 16's slow chunks now does. Before FIX-R3-17 (wall time, warm second fills): 16 6.7 ms median,
  worst 28.5 in one pass.
- **Not done:** R3A-13 (the sea form's foundation over trenches: a deeper box or a refusal changes the siting and the
  height rules; SC2-H's survey's call), R3A-15 (the last descent 16: a new route and fit for every size), R3A-10 (a 1:1
  stepped face lowered one block reads as set back one block: the "seams stepping" cannot show), R3A-17 (no action, as
  the review says).
- **Builds:** two green in a row at the merge with `self-created` 75cb5f7 (FIX-R3-B in; 1 210 game tests); one run
  before them failed `atmosphere.SelfCreated2InstrumentTests` "their own animal ... not back home at dawn" once (not
  this branch's code); see the last line of this note for the builds with FIX-R3-17's kit.
- **Builds with FIX-R3-17's kit** (merged at `self-created` eb1be12, the slot's change in): two green in a row, 1 216
  game tests. Between them and the earlier pair, two runs failed once each on tests outside this branch's change under
  load (load average 12): `VerifySc2CostTests`' wave idle at 21.9 us (budget 20; the kit's share 20.5) and this branch's
  `builtLiveItLetsItsLightOutAndMatchesWorldgen` reading the light before the engine had worked out the dark copy (now
  read every tick until it has).

#### Fixed in review (FIX-R3-B: 18, 19, 20)
- **R3A-2, the hollow's sign (18 v4,** `HollowShape.VERSION` 4; versions 2 and 3 build as before, v3 pinned by
  `version3OfTheHollowAndTheFloorStillBuildsAsBefore`). The bite is two chunks wide where the chunk beside the summit's
  square across the way in's line stands 6 or more over the cut (`neighbour`; both lowered before the inside is made
  again, so the shell keeps `d` under both; else one chunk as v3; `Summit.bite()`, `wide()`); the ring's lips follow
  it. The bite is lit (`biteLights`): `Fault.light` on its floor and lights up its faces in its edge columns against the
  land outside (from 3 over the floor to under the noise's land there, the fault's grid). The foot is grey: gravel (on
  something solid) or the land's own stone, never coarse dirt (`landStone`), with lights 3 and 5 over the land where
  the land up to 3 back stands over them, none within 3 of the mouth (`footFaces`). The piece is as v3 (the square's
  own chunk). Tests: `theBiteIsTwoChunksWideLitOnItsFloorAndUpItsFacesAndFair`,
  `theFootIsGreyAndItsLightsClimbTheSlopeBesideTheMouth`; the v3 tests build v3. **(d)** The real-terrain run's sign
  cameras for s18, s19 and s20 are raised (up to 48) or slid toward the sign, 8 at a time, to the first spot whose ray
  to the sign point meets no block, leaves and logs included (`LineGallery.clearEye`; the offset in each view's note);
  `-Da1016_02.sc2real.only=s18,s20` (FIX-R3-17's filter, the same code) runs those kinds alone.
- **R3A-3, 20b's dark (FloorAnswers).** The floor goes a ring of chunks at a time from its edge in (`rings`): each step
  (config `g` darkRingSeconds 7) the dark reaches one ring further and takes every lit chunk of the rings reached that
  nobody sees now in one ledgered edit (a chunk in view waits; `FloorAnswers.Env.view`, live
  `TraceService.isOutOfView`), never a player's chunk nor the one toward their nearest edge; the glimpse once it has
  reached `i` glimpseAfterRings 2 rings; `h` 6 000 (every light but the kept chunks). A 192 goes in about a minute, a 128
  in 40 s. Config sc20 v4 (`keepOrNew`). Gallery `20n`: `20b` at the game's pace, 8 frames 8 s apart from the doorway
  along the floor, and `20b_back` from 40 out looking back (both staged without the view rule: what a player sees each
  time they look). Test: `theFloorGoesDarkFromItsFarEdgesNeverWhereTheyStand` (rings, a chunk in view waiting).
- **R3A-4, the line's night sign (19 v4,** `LineShape.VERSION` 4; fixed segments keep their version; v3 pinned). Each
  opening is the light: a ring of the land's stone round it in the wall's plane (the 4 x 4 round its 2 x 2 where the
  wall stood, `ring`; the orchestrator's decision after the first gallery: lit obsidian stays black, so the ring is what
  reads), light blocks in its four cells (waterlogged in water) and on its sill cells either side (`litHoles`; no
  obstacle); the lit foot's grass and dead ground pale (gravel or stone, `paleFoot`). Changed in place (round 3 has not
  shipped). Gallery 19:
  `hole45e_n64`, `hole45e_n128`, `hole45w_n64`, `hole45w_n128` (45 degrees to either face). Test:
  `version4LightsEveryOpeningAndPalesItsFoot`.
- **19b, the wall mends (new; the orchestrator's decision, Mariano: every place answers).** `LineAnswers`, through the
  kit's `Answers` and `Visits`: a participant standing in one of the line's openings or its cut; out of everyone's view
  the opening next to theirs along the wall on each side becomes wall (obsidian converted into its four cells and its
  ring of stone, the lights round it removed; `world:s19/close`, ledgered). One standing in the wall sees nothing along it; it is found by
  whoever walks on to the next hole, and by night the two lit holes nearest theirs are dark. Fair: never an opening
  anyone stands in or within 16 of (their own, the cut and every other opening stay: nobody is shut in or out), health
  10, the kit's caps (a line a day, a player a day), firsts, quiets, first hour, asleep, creative. Mended openings open
  again on a later in-game day out of view (the ledger's days, so after a restart too); given back when switched off
  (`sc19`). Gallery 19: `19b` frames (16 off the face, the next opening, then wall). Test:
  `theOpeningsBesideTheOneYouStandInMendOutOfView`.
- **R3A-16, a cold floor.** `aColdFloorsSnowMeltsUnderItsLightAndStaysOff`: every open cell over a floor stands at block
  light 12 or more (snow melts over 11, falls only under 10) but the square's own corner on a cut side (11: two and
  two from the nearest light), so worldgen's snow on the floor melts at its next random tick except there. The real
  run shoots `s20h_loc_late` (`near_2min`, `aerial_2min`) two in-game minutes after the floor is made.
- **Cost** (`theFillOfAChunkCostsWhenAsked`, `-Da1016_02.sc3fill=true`: every chunk of the made 18 and 20 starts of four
  of the find's worlds and each world's four line segments, filled into an overlay over the real noise with the
  chunk's and its neighbours' noise made first, the second pass): 18 median 0.03 ms, worst 18.8 ms (3 468 chunks); 19
  median 0.11, worst 23.0 (1 454, a line chunk writes its plane from the bottom to the top); 20 median 8.0, worst 24.6
  (1 352; a floor's chunk clears and guards every cell over it to its box's top, so later worldgen grows nothing on
  it). Every worst is under the 25 ms budget on this branch, before FIX-R3-17's cheaper kit fills (not in
  `self-created` yet: re-measure once they land); 20's margin is thin.
- **Find** (`-Da1016_02.sc3find`, 20 default worlds, after): T1 100 % median 355; T2 16 90 %, 17 70 %, 20 within 900
  75 %, 18 within 1 200 65 % (as R3-3); T3 90 % / 100 % with crumbs; T6 0 near pairs, 3.96 places a km2; large and
  amplified as R3-3's. The real seed's `/locate` finds s18 986 out (R3-3: 595). Not the hollow's v4: R3-3's hollow
  (chunk 30, -22) is still made by its siting, as version 4 (a wide bite) and as version 3
  (`whyTheRealSeedsNearestHollowMovedWhenAsked`), so it is the kit's decision (the spacing rule's yield, or an exclusion)
  that drops it now, after the rounds merged since R3-3.
- **Gallery** (the scratchpad's `gallery-fixb`; the flat exhibits 18, 18w, 18p, 19, 20n; the real seed's s18h_loc, s19h, s20h_loc,
  s20h_loc_late, and `s18h_loc_raised`, the last real run):
  - 18: at `n128` the foot's grey lit square is the brightest thing in the frame (it was a brown smear); `summit_night`
    (from the air) the bite is a lit chunk-shaped notch; at `d128` and `d200` a square shoulder in the snow cap. From the
    ground at night the bite lies about 200 off, past the mod's haze: the night sign at 128 is the foot.
  - s18h_loc: the located hollow stands behind hills with a village by its way in; along its bearing no spot within 48
    up and to 24 from the sign has a clear sight (leaves, logs, plants and the village's houses count), so its cameras
    stand 48 over the land: at `n128` the lit foot is the brightest patch; by day from the ground (the first run's
    `d128`) the bite is a square gap in the crest with the sky through it.
  - 19 (`gallery-fixb-ring`): light blocks do not render and lit obsidian stays black, so the first v4 read as nothing;
    with the ring every opening is a pale square frame lit in the black wall: at 64 (`hole_n64`, `hole45w_n64`) a clear
    frame with the night through its middle, from either side and square on; at 128 a small pale square, about as
    bright as a birch trunk, the brightest things in those frames with the trunks. s19h's sign views look along the
    wall (its sign point is the opening nearest the origin), where the frames are edge-on; its night sign there stays
    the ribs against the stars. By day the pale foot is grey squares at the wall's foot. `19b`: from 16 off the face,
    the next opening a bright gap at the wall's foot on the hill, then wall.
  - 20n: `20b` at the game's pace: the dark's edge walks in from every side ring by ring (`f02` the far band, `f07`
    only the kept chunks lit round the doorway); `20b_back` the rings behind the house.
  - s20h_loc_late: two in-game minutes on, the floor is a green square in a white world (`aerial_2min`).
- **Builds:** two green in a row on `fix/r3-b` with `self-created` at 07d6426 merged (nothing new there yet), 1 205
  game tests; after the ring (with self-created at 75cb5f7, nothing else new), two more green, 1 206.
- **Outside the owned paths:** `SelfCreated3PyramidTests` (its 19 v3 lit-foot test builds version 3 explicitly; one
  line); `SelfCreated2RealWorldClientTest` (the s18, s19, s20 sign cameras, the `only` filter, `s20h_loc_late`).

#### Fixed in review (FIX-R3-E: 20's cost, the line clear of the places)
- **20's cost was the test overlay's, not the floor's.** Profiled with a new run of SC2-H's survey for 20 alone
  (`SelfCreated3HollowFloorTests.theFloorsFillCostAndHashWhenAsked`, `-Da1016_02.sc3floor=true`: its eight worlds, its
  centres, 8 candidates a centre, 73 made starts, 6 916 chunks; each chunk filled as the survey fills it, in the
  thread's CPU time, cold and warm, and again into an overlay of its own; a flight recording of the run). 80 % of a
  floor's fill was the test `Overlay`'s one hash map for the whole start (`set` 35 %, `get` 18 %, its growth 9 %): a
  floor sets every cell over its square up to its box's top, 2.4 to 3.5 million a 192, and a resize of that map (4
  million slots) fell inside one chunk's time; 36 % was the noise's heights (the cold pass); `FloorShape`'s own code
  under 5 %. A chunk in an overlay of its own cost at most 1.1 ms warm. Worldgen writes each chunk into that chunk, so
  the overlay now keeps a map a chunk column (kit `Overlay`; `cells()` merges them when asked). The same run, before →
  after: cold (the survey's measure, the noise read in it) median 3.42 → 2.92 ms, 99.9 % 13.85 → 7.48, **worst 28.39 →
  9.12** (the worst before is the survey's 34.05 chunk: seed 1, start [-179, -203], chunk -174 -207); warm worst 26.26
  → 1.28. **The fill hash is equal** before and after (every cell the 73 starts set, with its block: 76 412 036 cells,
  `2ebd4e453e4bd90a`; the cells whose block differs from the noise's: 10 545 391, `5d9dbc3b9a88f06c`), and
  `FloorShape` is not touched, so 20's blocks and the cells it guards in worldgen are as they were (no pin moves).
  `sc3fill` (wall, this machine) before → after: 18 worst 33.09 → 9.78, 19 7.66 → 0.32, 20 13.80 → 4.94; `sc3cost`
  unchanged (the fault 0.23 → 0.11 ms a chunk, the rest as before).
- **The line clear of the places (19).** `LinePlan.clear`: a planned segment whose footprint (its plane and lit foot, 4
  either side) comes within `CLEARANCE` 32 of another place's box is laid out again (`LinePlan.cut`, its own seed: ribs
  alone at its ends, the cut in the middle half, the openings on the same rule) over one stretch of its own between
  the places: the nearest the origin at least `preferred` long (half the config's `d`, 256), so it keeps its near end
  and ends short of the first place in its way when that leaves enough of it; else the longest stretch of at least
  eight bays; else that half-axis has none. It never bends: a straight wall on zero that stops where a place stands, as
  it stops at its far end. `LineFeature.segments` (every caller: the feature, the marker for `/locate`, the crumbs'
  `CrumbIndex`) lays each unfixed segment out so, asking the kit's new `Decided.within` (the candidates of a rectangle
  of chunks, each decided through its start's own path and kept, without deciding whole regions) for the places whose
  start chunk lies within `PLACE_REACH` 192 and the clearance of the footprint, on the live world only
  (`Snapshot.liveLevel`, new); a test's own snapshot gets the plan. Kept for the run per world; fixed in `Plans` on its
  first decoration as before, so a segment fixed earlier stays as fixed. The feature asks only for the half-axes near
  the chunk it decorates (`segmentsNear`). The line, like every place, now builds nothing before the world spawn is
  known (`refusal`: D-108's gate; the places are not decided before it). `/a1016 sc 19` shows the cleared ends.
- **Measured** (`SelfCreated2LineTests.theLineKeepsClearOfEveryPlaceOverTheRealNoise`, 3 worlds in every build,
  `-Da1016_02.sc3line=12`: every place worldgen decides in a band 256 wider than the line asks about): over 12
  worlds, 28 of 48 segments met a place as planned (58 %; SC2-H's seed's south arm, 624 to 1 216, crossed its archive's
  box, 461 to 671: now 704 to 1 216); 11 end short of it, 17 begin past it, none lost a half-axis; after, no segment
  within 32 of any place; the farthest box 128 from its start's chunk (`PLACE_REACH` 192); the live line sites about
  105 candidates a world, once (about 60 ms a siting under the test's parallel load), on worldgen's threads, the
  decisions kept as the crumbs' are. `theLineEndsShortOfAPlaceInItsWayAndKeepsClearOfIt`: the rule on every arm of four
  seeds (a place beside, on the axis, at the near end, a short stretch, two places, places all along; a fixed segment
  stays).
- **19's crumbs (T5)** follow the kept-clear segment (its cut moves with it; `CrumbIndex` reads `segments`). R3-C's find
  run with the real lines (`-Da1016_02.sc3find=20`, the line kept clear of each era's places in T3, T4 and T5): T5
  normal 19 736 lines, **100 % followable to their place**, 1 698 dropped short, 11.9 crumbs a line; large 1 966, 100 %;
  amplified 1 402, 100 %. The rest as FIX-R3-B's: T1 100 % median 356; T2 16 90 %, 17 70 %, 20 75 %, 18 65 %; T3 89 % /
  99 % with crumbs; T4 100 / 100 / 100 %; T6 0 near pairs, 3.85 places a km2.
- **Builds:** two green in a row on `fix/r3-e` with `self-created` at 66c6c25 (FIX-R3-C) merged, 1 219 game tests (the
  build's own 3 worlds after the merge: 8 of 12 segments met a place, none within 32 after, the farthest box 127).
- **Outside the owned paths:** the kit (`Overlay`, `Decided.within`, `Snapshot.liveLevel`); `SelfCreated3FindTests`
  (`placesIn`, the line kept clear in its T3/T4 sign points); `SelfCreated3CrumbTests.real` (one line: 19 as worldgen
  lays it out, kept clear, for T5).
- **Open.** On a new world the first crumbs or line chunk (or the `/locate` marker) sites the places along the four
  half-axes at once: a few seconds of one worldgen thread, once a world. The cost tests time the fill into an overlay,
  not a proto-chunk's own writes (heightmaps, light sources). T2's 19 "512 to 768 out" now holds only where no place
  stands at the near end (17 of 48 segments begin farther out).

## Waves

| wave | tasks (at most 4 at once) | starts when |
| --- | --- | --- |
| 0 | R3-S | now |
| 1 | R3-0, R3-B, R3-W | R3-S merged |
| 2 | R3-1, R3-2, R3-3, R3-C; then R3-D | R3-0, R3-B and R3-W merged; R3-D once R3-2 is merged (its slot) |
| 3 | R3-P, R3-A (and the optional critic) | wave 2 merged, R3-D included; the full gallery run |
| 4 | FIX-R3 (one to three fixers by workstream) | R3-A and R3-P reported |
| 5 | R3-V | FIX-R3 merged |

Merge order inside a wave: the kit first (wave 1: R3-0, then R3-B, then R3-W); wave 2 in any order (their files do not
meet; `SelfCreated2*Tests` updates were R3-0's), but R3-2 before R3-D, whose one line in `PyramidShape` lands on R3-2's.

## Considered and left out
- Lit doorways, torches at the ways in, signs or arrows: a welcome, built for players; the fault is the mark (Mariano:
  the error is what he likes).
- Runtime crumbs placed out of nothing near the player: guardrail 7 (he only removes and moves); 32c moves existing ones.
- Crumbs that are only torches: Mariano asked for the original blocks; one block kind would read as one player's habit.
- A beacon, an end rod, sea lanterns or shroomlights as signs: modern blocks, a mod light (R11-1's warning).
- A shaft or open drop in the well or the hall: the rails and the water hold D-097.7.
- Books written at worldgen for the days: the future is not known, and 1 000 books a place would weigh on every chunk
  save; day books are left as one goes down.
- Text in his voice anywhere but the archive's books (signs, chat, other books): DESIGN 2 holds outside the archive.
- Names invented for thousands of other records: unverifiable against real people (DESIGN: "check the F06 usernames
  against real players"); the empty shelves say it.
- The ways in facing the world spawn instead of the origin (R10-20): kept on the origin (D-095); the crumbs' first line
  runs toward the spawn, which is what gets a player there.
- A sighting card in the floor's doorway: a new card and the entity's files; 20b v2's glimpse (D-105) does it.
- Changing the hunts or the instruments: not this round's brief.
