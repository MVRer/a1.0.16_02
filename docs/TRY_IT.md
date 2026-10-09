# Try it

One entry per merge, newest first. Run the game from the main checkout (this folder, on `main`).

## READY TO TRY: The dread layer (fog, silence, music off, sounds, mobs acting wrong)  (merged feat/atmosphere-dread, commit 2dd4fb8)
```
Restart needed: full game restart (client mixins for fog, music and compass)
See it now:
  1. Launch "Dev Client", then /a1016 stage 2
  2. Fog: /a1016 atmosphere fog surge 0.85 8   then   /time set 12900 and /a1016 atmosphere fog dusk 0.6
  3. Sound: /a1016 atmosphere silence 10 (everything cuts, then creeps back), /a1016 atmosphere music off|on
  4. Mobs: /summon cow ^ ^ ^5, then /a1016 atmosphere tamper freeze | face | release
  5. Cards: /a1016 fire fog_drift | animals_face_fog | distant_cave_sound | silence | mining_in_the_dark (at night,
     stand still 20 s) | chest_opens | door_left_open | one_block_missing | footstep_late | compass_drift (hold a
     compass) | villagers_inside_at_noon | dog_wont_go | cat_hisses_corner | patient_skeleton | empty_water |
     bat_in_sealed_room | cow_where_nothing_spawns (needs a dead mountain nearby)
  6. Relog: music off and dusk fog come back after leaving and rejoining.
What to look for: no music after your first night. Fog that thickens for a few seconds and lets go. Animals all
  staring the same way. Every sound is vanilla, with no visible cause.
Known rough edges: footstep_late plays at your next stop after it's fired. The fake "only a cow" and
  "zombie at dusk" sightings now work, since MobTamper is real.
```

## READY TO TRY: The world is wrong (old scars and new scars)  (merged feat/world-scars, commit 11ce61a)
```
Restart needed: full game restart (new worldgen feature and Java code)
See it now:
  1. Launch "Dev Client". Old scars only generate in NEW chunks at least 300 blocks from spawn. In a1016_dev,
     travel out past 300 blocks, or delete run/saves/a1016_dev for a fresh world.
  2. /a1016 world locate <scar>   (dead_mountain, bare_forest, cut, stair, abandoned_build, panic_tower,
     emptied_house, cross, lone_light, ocean_pyramid), then /tp to it. /a1016 world sites lists what's recorded.
  3. /a1016 world place <scar>   builds one behind you (out of view) so you can see it without travelling.
  4. New scar: walk through an area, leave it, /a1016 timewarp 2, then /a1016 world newscar now (it reports where).
  5. Cards: /a1016 fire light_on_mountain (at night, look for one far torch on a hilltop) | lone_light_near_base |
     emptied_house | new_scar
What to look for: everything should first read as odd worldgen: a hill gone grey-brown with a hard edge, a
  forest of bare trunks, a tunnel that's too straight. The horror is on the second look.
Known rough edges: no fragments in the builds yet (lore, v0.5). "Debug place" builds pyramids and crosses
  directly instead of from moved material. Dead mountains aren't silent and animal-free yet.
```

## READY TO TRY: Diggers and "Under you"  (merged feat/dig-diggers, commit dc44d5c)
```
Restart needed: full game restart (new Java code)
See it now:
  1. Launch "Dev Client", then /a1016 stage 2
  2. Under you: place a bed near spawn, /time set night, sleep in it. Then /a1016 dig network grow 8,
     /a1016 dig network info and /a1016 dig network reveal. Dig down at the x/z it prints, or break the
     block under your bed to find the shaft that stops one block below it.
  3. Tunnels: /a1016 dig tunnel plain, /a1016 dig tunnel tunnel_that_grows (needs a hillside or cave wall
     40-72 blocks from your base), /a1016 dig tunnel tunnel_into_mine (dig a 2-high tunnel first, walk 32+
     blocks away and face away).
  4. Cards: /a1016 fire torches_gone | torches_behind_you | mining_that_moves | trees_stripped |
     under_you_sound | under_you_footstep | under_you_stack
What to look for: clean 2x2 cuts through stone that nobody dug. Nothing changes while you look. The network
  stays at least 3 blocks from anything you dug, so you only break into it by digging close.
Known rough edges: the dark chest under your base only appears once a far abandoned build or hut exists to
  take it from (world, v0.3). Until then, taken stacks wait in the ledger. tunnel_that_grows returns
  "no spot" on flat ground.
```

## READY TO TRY: The figure (all sighting variants)  (merged feat/entity-figure, commit 54e5a45)
```
Restart needed: full game restart (new entity, model and renderer)
See it now:
  1. Launch "Dev Client". Options > Video: Render Distance 6 (the small canon render distance).
  2. /time set 13000, then /a1016 stage 2
  3. /a1016 fire sighting_cow   (chat prints where he is; he spawns behind you, so turn slowly)
  4. Variants: sighting_walks_away, sighting_ridge (face away from the peaks ~100 blocks off),
     sighting_between_trunks (stay in the spawn forest), sighting_across_water (stand on the ocean shore,
     back to the sea), sighting_close. /a1016 entity info shows his phase and every gate.
What to look for: default Steve, blank white eyes with no glow, no name tag, no sound, at the edge of the fog.
  He's never there when you first look; turning reveals him. Stare about 2 s or walk toward him and he
  turns and leaves into the fog, then he's gone once you look away.
Known rough edges: the skin is the placeholder. sighting_in_the_light needs world's lone lights (v0.3).
  The fake "only a cow" needs atmosphere's MobTamper (v0.4). If your render distance is above your
  simulation distance and you walk away, he may stand still while you watch, until you look away.
```

## READY TO TRY: The director (decks, pacing, quiet, stages by time)  (merged feat/director-core, commit c41bff5)
```
Restart needed: full game restart (new Java code and a jukebox mixin)
See it now:
  1. Launch "Dev Client". /a1016 state now ends with live director lines.
  2. /a1016 director            (deck, held card, next allowed times, quiet, pity, tension, stage timers)
  3. /a1016 director sim 20 synthetic   (a 20 h dry run; the full log is in run/logs/a1016_director_sim.log)
  4. /a1016 director timewarp 3, then /a1016 state   (3 in-game days pass as a dry run; watch the stage timers move)
What to look for: the sim summary shows rare, uneven events, long quiet stretches, whole empty sessions, and stages
  arriving inside their windows. In your world, the only card so far is debug_ping. After the 5-minute grace it may
  fire once on its own ("[a1016] debug_ping fired"), which proves the live loop runs.
Known rough edges: no real cards yet (they arrive with each workstream). Proximity runs at about 0.6 minors/h overall
  (D-020); say if that feels too sparse once real events exist. /a1016 timewarp still prints no summary; use
  /a1016 director timewarp.
```

## READY TO TRY: Skeleton (mod loads, world state, debug commands)  (merged feat/core-scaffold, commit 408ef8e)
```
Restart needed: full game restart (new Java code, mixins and a new run config)
See it now:
  1. In VS Code Run and Debug, pick "Dev Client" (or run ./gradlew runDevClient).
     It creates and opens the world "a1016_dev": creative, cheats on, spawn in a Dappled Forest.
  2. /a1016 state
  3. /a1016 fire debug_ping   then   /a1016 fire debug_ping fake
  4. /a1016 stage 2, /a1016 timewarp 3, /a1016 profile reroll, then /a1016 state again
What to look for: /a1016 state prints the stage, attention, tension, clock and this world's rolled profile
  (habits, density, tempo, signature, which fragments exist). Nothing happens in the world on its own. That's correct.
Known rough edges: no director yet, so nothing fires by itself. The old figure prototype is still registered
  (/summon a1016_02:him) and its eyes still glow; the entity worker replaces it in v0.2.
```
