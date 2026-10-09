# Try it

One entry per merge, newest first. Run the game from the main checkout (this folder, on `main`).

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
