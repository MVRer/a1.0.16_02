# Try it

One entry per merge, newest first. Run the game from the main checkout (this folder, on `main`).

## READY TO TRY: v1.3  (branch feat/v13-int; jar build/libs/a1016_02-1.3.0-26.3.jar after ./gradlew build)
```
Restart needed: full game restart. Everyone who plays needs the same jar plus Fabric API (the client sends when a menu
  is open, and draws the screenshot).
What changed, in plain words ("he knows"):
  - Echoes: minutes after you mined with a pickaxe or shovel, your own mining plays again from inside the rock below or
    beside you, the same hits and breaks with the same gaps. A door, trapdoor or gate you used plays its own open and
    close again later, from where you can't see it; the block never moves. A chest you hear opening is likely the one
    you opened last.
  - Blind moments: while a menu is open (the inventory, a chest, crafting, a furnace, a book or sign you are writing,
    the advancements, the pause menu on a LAN or server world), and only when you are alone: a footstep behind you, a
    torch of yours far off gone when you look, a door out of view that opens and closes. Never in the first 1.5 s of
    the menu, never within 30 s of a fight, never with anyone (spectators too) within 48 blocks.
  - The screenshot: once per world, a screenshot you take (F2) outdoors in the fog shows him standing at the fog
    edge, off to one side, facing you. He is only in the saved picture, never on your screen. No message, no sound.
  - Pacing: a deck with several cards that can't happen for you (the tunnel steps on the surface, a blind card for
    someone who never opens a menu) no longer loses a tenth of its moments; the first hour is as in v1.2.
See it now (Dev Client; /a1016 debug overlay on shows each player's "blind" line, the last echo or blind card that
  fired for them, and who is armed for the screenshot):
  1. echo_mining: /gamemode survival, take a pickaxe and mine 3 to 6 stone blocks one after another (each within 3 s
     of the last), then /a1016 fire echo_mining. Within a second your burst plays again from the rock 14 to 24 blocks
     below or beside you: the hits, then each break, with your gaps (a forced fire takes your newest burst whatever its
     age; the real card waits 5 to 30 minutes). /a1016 fire echo_mining fake plays a burst that is not yours.
  2. echo_door: open and close a wooden door (or trapdoor, or fence gate) by hand, walk 6 to 16 blocks away and turn
     so it is out of view (behind a wall or behind you), then /a1016 fire echo_door: its own open, then close, with
     the gap you left. Walk back: it never moved.
  3. chest_opens: open a chest, walk away until it is out of sight (within 16 blocks), /a1016 fire chest_opens: that
     chest is the one you hear.
  4. blind_step: stand with open floor behind you, /a1016 dig blind arm blind_step, then open the inventory (E) and
     wait 2 s: one or two steps 3 to 5 blocks behind you. /a1016 dig blind arm blind_step fake: your own step, late.
  5. blind_torch: place two torches about 4 blocks apart, 6 to 20 blocks from you and more than 6 from your bed (or
     your first placed blocks, if you have no bed), turn your back to them, /a1016 dig blind arm blind_torch, open the
     inventory for 2 s, close it and look: one torch is gone, its wall or floor still there. A lone torch never goes.
  6. blind_door: a closed wooden door, trapdoor or fence gate 4 to 12 blocks away and out of view,
     /a1016 dig blind arm blind_door, open the inventory: its open, then its close 1 to 2 s later. Close the menu
     before the close and it does not play.
  7. /a1016 dig blind prints your blind moment and why each blind card would or would not happen now; the arm waits
     up to 2 minutes for your next menu. /a1016 director shows your blind line (rechecks and how many fired).
  8. The screenshot: /a1016 fire the_screenshot arms you (/a1016 entity screenshot shows it: days left, ready or
     paused, fog on or off). Go outdoors in the overworld in fog: /a1016 fog night then /time add 2500, or a short
     render distance (the fog end under 160 blocks: 8 chunks or less), sky above you, first person (F5 back to
     it), no menu open, not in water. Press F2 and open the picture in run/screenshots: he stands at the fog edge, off
     the middle, head and chest in plain sight. The screen never showed him. It waits (and he does not appear) while a
     figure is out anywhere or no ground spot fits. Once per world: /a1016 entity screenshot reset to try again.
  9. The real way: a NEW world, play normally: mine, use doors, open your inventory now and then. The echoes need 5
     minutes or more to pass; the blind cards need you alone with a menu open; the screenshot comes once, from Traces.
What to look for: whether an echo reads as your own rhythm coming back, whether a step behind you in the inventory
  makes you close it and turn, whether the torch is missed, and whether the picture surprises you later.
Multiplayer: the echoes are heard by anyone in range; the blind cards and the screenshot are personal (nobody, not
  even a spectator, within 48 blocks for the blind ones). The menu signal only ever makes things happen to the player
  who opened the menu.
Existing worlds and config files are upgraded automatically: the director section steps to version 3 (it only adds
  blindHoldMinutes 15 and simBlindPerHour 20); the echo, blind and screenshot numbers are new fields with defaults.
Still to check in the Dev Client (the game tests cannot hear or see a frame):
  - the echoed mining sounds like digging inside the rock (hits then a break), not like blocks breaking next to you;
  - a blind step at 50% master volume is noticeable but not loud;
  - the screenshot's figure is tinted by the fog like the terrain around him, and is never on the window, not for one
    frame, before or after F2.
```

## READY TO TRY: v1.2  (jar dist/a1016_02-1.2.0-preview.1-26.3.jar)
```
Restart needed: full game restart. Everyone who plays needs the same jar plus Fabric API.
What changed, in plain words:
  - Something happens in the first hour: a small, deniable thing in the first 10 to 25 minutes, two or three in the
    first hour, never more than five. Traces comes after about 25 to 55 minutes, Proximity after 1.75 to 2.75 hours,
    the first accident not before about 3.5 hours (2.6 in an early world, 4.4 in a very late one).
  - Fog weather, one sky for everyone: some nights are fog nights (a pale wall in the dark, not just darkness), some
    carry into a grey morning, rarely a whole day is foggy. The first night is always clear, the second or third is
    foggy. On a fog night or day he is more likely to act and can be seen in daytime fog, unless it is a quiet one.
  - Someone behind you: in a long straight tunnel you dug, footsteps behind you. First one or two steps, later steps
    that come closer and stop when you turn around, later still steps that stop far back past your last torch, and
    something stands there. Look toward it and it is gone (the one time he vanishes in view).
  - Far in the rock: underground, alone, a stone hit or a gravel slide somewhere in the rock around you. The cave
    sound no longer needs you standing still underground.
  - Friends: together you share one moment at 1.5 times one player's pace, split between you; the tunnel steps only
    happen to someone alone (nobody else within 48 blocks).
See it now (Dev Client; /a1016 debug overlay on shows the fog spell and everyone's ladder rung):
  1. Fog night: /a1016 fog night, then /time add 2500 (the spell eases in over 2500 ticks). /a1016 fog shows the
     clock, the spell (the fog it gives now, easing in, full or easing out, its ramp and fade, and "counts" once it
     gives at least half its level: only then does the director get its boost and daytime fog let him come),
     tonight's and today's roll and the history.
  2. Fog day: /a1016 fog day (grey, desaturated). /a1016 fog clear eases it out. Add "quiet" (/a1016 fog night quiet)
     for a spell that gives the director no boost.
  3. The fog is weather, outside: walk into a tunnel, or indoors where the sky's light does not reach, and the
     spell's fog clears in about a second (it follows the sky light where you stand, full from sky light 10, none at
     4 or less); step back out and it closes in again. The dusk fog is not weather and stays.
  4. The ladder: dig a straight 2-high tunnel of 20+ blocks underground (5+ blocks below the surface, dark), walk to
     its end and face away down it, no torches behind you. Then /a1016 fire steps_behind_once, then
     /a1016 fire steps_behind_coming (turn around mid-steps: they stop), then /a1016 fire steps_behind_glimpse (keep
     facing away until the steps stop, then turn slowly). /a1016 dig steps prints your rung, the last events, the used
     tunnels and whether it sees a tunnel behind you now. A forced fire skips the rung order and spacing, never the
     rules (underground, alone, the tunnel, no combat). Alone means nobody else within 48 blocks, spectators and
     dead players included (the steps would reach them): a friend spectating beside you stops it.
  5. /a1016 director shows the fog weather line (the boost while a spell that counts and is not quiet is on; "below
     half: no boost" while it eases in or out) and, per deck, any card parked because its moment never came (the
     tunnel steps on the surface, for half an hour of play).
  6. The real way: a NEW world, play normally for an hour. The tunnel rungs come one after another, at most one every
     25 minutes of your own play, the glimpse at most once an in-game day.
What to look for: whether the first hour feels like something; whether a fog night reads as fog, not darkness; whether
  the steps sound like someone in the tunnel, and whether you ever see the glimpse straight on.
Multiplayer: the fog is the same sky for everyone. Personal moments (the tunnel steps and the glimpse) need your
  friends 48+ blocks away; standing together you get shared moments instead.
Existing worlds and config files are upgraded automatically: a v1.1 world rolls its stage times again in the new
  windows (a stage reached stays reached), and config values you changed by hand are kept. A value the game cannot
  read keeps its default with one warning in the log naming it; a config file that is not JSON at all is kept as
  config/a1016_02.json.broken and a fresh one is written.
Still to check in the Dev Client (the game tests cannot see a frame):
  - a fog night reads as a pale wall in the dark (the night lift), not plain darkness;
  - a fog day reads grey and desaturated;
  - at the bottom of a ravine near bedrock it stays black (vanilla's void darkening holds over the fog's tint);
  - Darkness (a warden) and Blindness fade in and out over a fog spell without a snap, and night vision works as
    without a spell;
  - the tunnel steps are heard at 50% master volume, rung 3's from 14 to 20 blocks back;
  - the glimpse stands only where it is dark to the eye (light 0 to 3: no torch near, no daylight down a shaft).
Known rough edges: Iris shaderpacks draw their own fog and hide the mod's fog weather (not supported).
```

## READY TO TRY: v1.1 multiplayer and story clues  (jar build/libs/a1016_02-1.1.0-26.3.jar)
```
Restart needed: full game restart. Everyone who plays needs the same jar plus Fabric API.
See it now, the real way (no debug commands):
  1. Make a NEW world, then Open to LAN (or run a server). A friend joins with the same jar.
  2. Play apart for a while, then together. Each of you gets the solo pace; standing within 48 blocks you share the
     same sounds, fog and silence, and being together never doubles it (D-059, D-060).
  3. The story is one for both: anyone's reading, naming or telling counts; F06 lists both names, F23 each one's cause.
  4. Ending D's chamber now has six name plates, a blank seventh and the team's note; a group stuck on a step for a few
     in-game days finds a plainer clue where it has already been (D-066).
What to look for: anything that happens to the wrong player, or nothing happening while you're together.
  Debug: /a1016 state shows every player's pacing; /a1016 debug overlay on shows your own.
Known rough edges: tiers of two cards or fewer can still stall a pair (the real decks are much larger); a friend's
  forced quiet still holds a shared moment (D-069).
```

## READY TO TRY: v1.0  (main 1d6cb3a; jar build/libs/a1016_02-1.0.0-26.3.jar)
```
Restart needed: full game restart.
See it now, the real way (no debug commands):
  1. Make a NEW hardcore world (normal launcher or "Minecraft Client"), Render Distance 6. Play normally for hours.
     A whole first evening may contain nothing you can be sure of. That's the design.
  2. To watch the machinery while you test: /a1016 debug overlay on (dev only).
  3. To check pacing without playing: /a1016 debug playthrough 20
What to look for: whether it's scary. Report what felt cheap or too frequent; tuning lives in run/config/a1016_02.json.
Known rough edges: VERY_LATE worlds are rare by design (D-054). The F06 usernames still need a real-player check
  before release (D-013).
```

## READY TO TRY: Latin crosses everywhere, and glass memorials  (merged feat/world-crosses, commit bc6e01c)
```
Restart needed: full game restart (worldgen and Java code). Old crosses in already-generated chunks keep their
  old shape; new chunks get the new one.
See it now:
  1. Launch "Dev Client". /a1016 world place cross   (a stone or wood Latin cross, out of view; prints where)
  2. /a1016 world place glass_cross   (a glass memorial: left by people on the list, not by him)
  3. /a1016 world locate glass_cross   (the nearest generated one, if this world has any)
  4. /a1016 world signature cross_row now   (the row: one per "gone" name plus a fresh one, all Latin, never glass)
What to look for: his crosses are built from whatever was right there. The glass ones are someone's deliberate
  memorial, nearly invisible in fog at dusk, catching light at night.
Known rough edges: lore and accident still treat glass memorials as his places (one-line fix next).
```

## READY TO TRY: Death-marker crosses are proper Latin crosses  (merged feat/accident-cross-shape, commit 7909b67)
```
Restart needed: full game restart (Java code)
See it now:
  1. Launch "Dev Client". /a1016 accident mark fell   (a preview cross goes up behind you; nothing is recorded)
What to look for: 5 to 6 tall, 1 block above the arms, a long foot below, built only from the ground right there
  (dirt, stone, whatever was around), never glass.
Known rough edges: the world's crosses (worldgen, the cross row, glass memorials) land in the next merge.
```

## READY TO TRY: Final integration (everything wired together)  (merged feat/integration-final, commit 8130486)
```
Restart needed: full game restart (Java code across workstreams)
See it now:
  1. Ending D's last minute is now meant to be WATCHED: in a real completion, the stair blocks climb back one at a time
     and the leaves come back in one red and orange wave in front of you (D-048). The debug preview
     (/a1016 ending d lastminute) is now cosmetic only: music, fog, no world changes.
  2. After Ending C or D, fog surges and drift never happen again; the house copy stops for good after D and pauses
     during C.
  3. /a1016 debug playthrough 20   now runs with all 70 real cards. Every hard limit holds for every tempo.
What to look for: nothing new to fire. This is the glue: endings, director, entity, lore and accident now talk
  through proper hooks instead of workarounds.
Known rough edges: the very-late tempo runs rare (about 0.5 minors/h overall, a major every about 6 h; D-054). Your
  playtests decide.
```

## READY TO TRY: Dusk fog that creeps in  (merged feat/atmosphere-dusk-ease, commit 6ecfc75)
```
Restart needed: full game restart (client fog code). Your config needs no changes.
See it now:
  1. Launch "Dev Client". /a1016 atmosphere fog dusk 0.45
  2. /time set 9000, then /gamerule doDaylightCycle true, and just play or watch for a few minutes.
  3. Level change: /a1016 atmosphere fog dusk 0.7, then back to 0.45: it eases over about 90 s instead of snapping.
What to look for: you shouldn't be able to say when it started. About 1% thicker after 30 s, 8% after a minute,
  full at deep dusk (13000), easing to the night level by 16000, gone by sunrise. Sky haze and clouds blend in too.
Known rough edges: on first joining a world, the current level applies at once (no fade from clear).
```

## READY TO TRY: Ending D, "No longer with us" (the true ending)  (merged feat/ending-d, commit df57a98)
```
Restart needed: full game restart (Java code, worldgen mixins)
See it now (USE A THROWAWAY WORLD: the afterward undoes removals and new chunks stop having caves, for good):
  1. /a1016 ending d status   (the current step and what's still missing)
  2. Walk the chain or jump it: /a1016 ending d step <1-7>. The map "where he didnt" (F28) leads to the untouched
     grove; F30 "i did, but" is on the oldest poplar; take your first block back out of the cairn (F13); dig through
     the seed pyramid's floor (F07) down the team's stair; place exactly six torches; under F30's twin write
     "he is no longer with us"; build his cross on your first block, topped with planks from the grove.
  3. /a1016 ending d lastminute   plays the last minute where you stand: silence, the stair returning block by block,
     a lost torch back on the wall, dawn with the fog gone, him on the far shore walking into the grove, the leaves
     coming back, then music.
  4. Afterward: /a1016 ending d undo status. Your house's blocks come back, the copy falls apart, the network under
     your base fills in. The crosses and the dead stay.
What to look for: nothing in it is kind to him. It's a burial, not forgiveness.
Known rough edges: the stair's return and the leaf wave currently happen only out of view (D-048 makes them
  visible: final integration). If Ending B had started finishing the house copy, it keeps going after D (fix next).
```

## READY TO TRY: Endings A, B and C  (merged feat/ending-abc, commit 34b526a)
```
Restart needed: full game restart (Java code and mixins)
See it now (use a throwaway copy of a1016_dev or a fresh world: endings change the world for good):
  1. /a1016 ending status   (which path you're on, and what each path is still waiting for)
  2. Ending A, the false peace: /a1016 ending path A, then /a1016 ending step repeatedly. He's seen once, walking
     away. Then days of real quiet, then one ordinary accident on your own mine route. The "Stop." sign ends up in
     front of your cross.
  3. Ending B, removed: /a1016 ending path B, then step. Your house is emptied, the copy elsewhere is finished, mobs
     wait in your doorway, and the final trap is inside the copy. F10 gains "* removed [your name]".
  4. Ending C, for the record: burn every fragment you ever held in lava, take your house apart, stay away. The world
     goes quiet forever. Type his name once and it all starts again.
  5. A third marked death always means Ending B. In hardcore, one marked death ends the story.
What to look for: every ending is earned by what you did, never announced. Nothing says "Ending A".
Known rough edges: Ending D lands next (v0.8 is tagged after it). Naming him during C also resets which fragments
  count as "held".
```

## READY TO TRY: Integration cleanup  (merged feat/integration-cleanup, commit fb2b57e)
```
Restart needed: full game restart (Java code; old config keys are dropped quietly)
See it now:
  1. /a1016 state   numbers now print with dots ("12.5"), never commas.
  2. /a1016 world signature still_burning now   in worlds with F21, the camp is now 1800-2200 blocks out
     ("nobody for 2000 blocks"). This replaces the 900-1450 note in the world signatures entry below.
  3. /a1016 director sim 20 telling 8   a dry run where you name him at hour 8: sightings almost vanish after
     (3 against 61 in the same worlds without telling).
What to look for: behaviour otherwise unchanged. Every gameplay timing is now in config, so tuning and devFastMode reach it.
Known rough edges: none new.
```

## READY TO TRY: The zombie has your sword, bridges over the void and lava, gravel and dripstone  (merged feat/accident-sword, commit b0b283e)
```
Restart needed: full game restart (Java code)
See it now:
  1. Launch "Dev Client". /a1016 stage 2.
  2. The zombie has your sword: put a renamed or enchanted sword in a chest at your base, walk away,
     /a1016 fire under_you_stack (or chest_opens) until it's taken, then /a1016 accident stolen (lists what could come
     back). At night: /a1016 accident arm zombie_has_your_sword. A zombie 24-48 blocks out is now holding YOUR item.
     Kill it and it drops exactly what was taken.
  3. Gravel ceiling: dig up under gravel, then /a1016 accident arm gravel_ceiling. Dripstone: in a dripstone cave,
     /a1016 accident arm falling_dripstone and walk under a stalactite.
  4. End: build a 1-wide bridge over the void, walk it a bit, /a1016 accident arm void_bridge (one block ahead goes
     missing out of view) or enderman_on_bridge. Nether: the same over lava with lava_bridge.
  5. Dark corner now always leaves its clue: one torch a block off, or, if every off spot is blocked, one torch missing.
What to look for: nothing ever spawns or gets stronger. The zombie is ordinary; it just has your gear.
Known rough edges: the enderman clue (no enderman lives within teleport reach of that bridge) is subtle by design.
```

## READY TO TRY: Director tuning (rarer, but not starved) and the ending hooks  (merged feat/director-tuning, commit ae61642)
```
Restart needed: full game restart (Java code). Your run/config was updated to the new pacing defaults.
See it now:
  1. /a1016 debug playthrough 20   compare run/logs/a1016_playthrough_*.log with before: Proximity about 0.7-0.8
     minors/h overall (about 1.3/h while active), a major every 2.3 / 3.8 / 5.4 h for early / slow burn / very late,
     about 0.8 ambient/h in Traces, 40-46% of Proximity quiet or empty.
  2. /a1016 debug overlay on   while you play: you'll see quiet periods and empty sessions doing their job.
What to look for: a real bug is fixed. Waiting out quiet periods used to push every later major back, so long
  worlds starved. It's still rare on purpose; your playtests decide from here.
Known rough edges: the endings' director hooks (silence, pace) do nothing visible until the endings land (v0.8).
```

## READY TO TRY: He goes under, follows you to the Nether and End, and rushes past when chased  (merged feat/entity-goes-under, main fe17516)
```
Restart needed: full game restart (Java code)
See it now:
  1. Launch "Dev Client". Render Distance 6. /a1016 stage 2, /time set 13000, /a1016 atmosphere fog dusk 0.45
  2. Goes under: /a1016 fire sighting_walks_away, find him, then /a1016 entity goesunder. He digs straight down,
     sinks, and the hole closes over him. Walk over: a too-clean patch of bare dirt where there was grass.
     (It also happens on its own in about 1 in 4 sightings, on natural ground with dirt below.)
  3. Nether: /a1016 fire sighting_among_piglins (stand near zombified piglins, Nether fog). End: /a1016 fire
     sighting_among_endermen (white eyes among the purple ones).
  4. Rush: /a1016 fire sighting_walks_away, then fly or ride at him fast. Within about 10 blocks he turns, runs past
     you about 2 blocks to the side, and is gone the moment he's behind you.
  5. Tune: /a1016 entity tune  (goUnderChance, rushTriggerDistance, pass offset, dig speed, depths)
What to look for: the hole never closes while you're looking at it; it waits until you look away.
Known rough edges: the dig-down only happens where there's dirt in the shaft (that's the clue).
```

## READY TO TRY: Playtest tooling (dev overlay and the 20 h playthrough)  (merged feat/debug-playtest, commit 5dc34df)
```
Restart needed: full game restart (new HUD and Java code)
See it now:
  1. Launch "Dev Client". /a1016 debug overlay on   (a small panel, top-left: stage, attention, tension, quiet, pity,
     tempo, held card and why it's waiting, last card fired (real or fake), next minor or major allowed, armed trap,
     sighting phase). /a1016 debug overlay off hides it. Dev environment only, never in a real play jar.
  2. /a1016 debug playthrough 20   (simulates 20 h of play for each tempo as a dry run; nothing happens in your world)
     Logs: run/logs/a1016_playthrough_early.log, _slow_burn.log, _very_late.log
What to look for: hard limits pass for every tempo. Rates (seed 1016): EARLY on target; SLOW_BURN minors run low
  (0.42/h overall); VERY_LATE had only 1 major in 12 h of Proximity. A director tuning pass is next.
Known rough edges: in devFastMode the overlay's "first day" and "quiet" countdowns show unscaled times.
```

## READY TO TRY: World signatures (still burning, your house elsewhere, the cross row, redstone torches)  (merged feat/world-signatures, commit af92e15)
```
Restart needed: full game restart (Java code)
See it now:
  1. Launch "Dev Client". /a1016 world signature status   (which signature this world rolled, and what has happened)
  2. /a1016 world signature still_burning now   (prints coordinates; /tp there: an abandoned camp, the furnace still lit)
  3. /a1016 world signature cross_row now   (a row of crosses on a hilltop, one per "gone" name, plus a fresh one with
     dug-up ground around it)
  4. Your house, elsewhere: build a small first shelter, then /a1016 world signature house_elsewhere now, walk away,
     /a1016 world housecopy step 5 a few times, /a1016 world housecopy status. Your walls lose a few blocks at a time
     (never the roof, never opening the house); the copy far away grows from them.
  5. /a1016 fire lone_redstone_torch   (one redstone torch deep in a cave you explored and left)
What to look for: each one happens at most once per world. Everything is moved or "left by others", never created.
Known rough edges: in worlds with F21, the camp sits about 900-1450 blocks out instead of about 2000 until a lore
  fix lands (in progress). Dead mountains are now recorded from their first chunk, so worldgen animals are blocked too.
```

## READY TO TRY: Dead mountains are silent and empty  (merged feat/atmosphere-followups, commit 9ada3a8)
```
Restart needed: full game restart (client sound filter and a spawn mixin)
See it now:
  1. Launch "Dev Client". /a1016 world locate dead_mountain, then /tp there (or travel 300+ blocks out in new chunks).
  2. Walk onto the dead ground: ambient sound and music fade out over about 4 s. Step back onto living grass and
     they return over about 10 s. Footsteps and block sounds stay. /a1016 atmosphere status shows it.
  3. Animals never spawn on the dead ground. The rare "cow where nothing spawns" card is the exception, by design.
What to look for: it is quieter than anywhere else, with a hard edge exactly where the grass dies.
Known rough edges: animals generated with the world before the mountain is recorded can still be there (world's
  part of D-042 is coming in the world signatures task).
```

## READY TO TRY: Accidents (every trap, the death marker)  (merged feat/accident-traps, commit 4c40376)
```
Restart needed: full game restart (new Java code and a spawn-observer mixin)
See it now (walk, don't fly; survival is best for the real thing, creative for setup):
  1. Launch "Dev Client". /a1016 stage 2. /a1016 accident status and /a1016 accident candidates (trap spots near you).
  2. Safe preview of a death: /a1016 accident mark fell   builds a cross behind you and prints the list line it WOULD
     add. It records nothing. Only "/a1016 accident mark fell record" counts toward Ending B.
  3. Missing rung: build a ladder 8+ high, then /a1016 accident arm missing_rung and climb.
  4. Dark corner: sleep in a bed, place 6+ torches around, /a1016 accident arm dark_corner, /time set 12000, then
     wait through the night. One corner goes dark, and in the morning one torch is back a block off.
  5. Others: arm lava_floor | lava_in_the_wall | house_fire | short_bridge | flooded_tunnel | moved_mob | no_bed |
     powder_snow | bare_wool (each needs its setting nearby; "candidates" tells you what's possible).
What to look for: nothing changes while you look. Every death should first feel like your fault, and every trap
  leaves exactly one clue (a too-clean 1x1 gap, a torch a block off, a ladder gone with no item).
Known rough edges: gravel ceiling and falling dripstone are switched off until the next accident task turns on
  the new core call. A missing rung rarely kills when ladders below catch you. The zombie with your sword and
  the void and lava bridges come in the next accident task.
```

## READY TO TRY: Telling (naming him, "Stop.", blank signs, place not found)  (merged feat/lore-telling + feat/director-fixes, commit 65a96dd)
```
Restart needed: full game restart (new mixins for signs, books and chat)
See it now:
  1. Launch "Dev Client". Write "her0brine" (or any spelling of his name) on a sign. /a1016 state shows Stage 3
     (Telling). /a1016 lore telling shows the count and which signs it remembers.
  2. "Stop.": walk away so the sign is out of view, then /a1016 fire stop_sign. Go back: line 2 reads "Stop.".
  3. Write another sign, walk away, /a1016 fire blank_sign. It comes back empty.
  4. /a1016 lore place F01, visit it, leave, then /a1016 fire place_not_found (only after Stop.): flat dirt and one
     blank sign where it was.
  5. Writing a sign near one of his tunnels or pyramids raises attention but does NOT start Stage 3. Only his name does.
  6. Previews: /a1016 lore listcause lava (what F23 would say), /a1016 lore ending finishf10 | placef20 | stoptocross
What to look for: he never writes. "Stop." is the team's word, moved onto your sign. The more you tell, the faster
  things go. Chat counts too.
Known rough edges: endings (v0.8) are what call finishF10, placeF20 and the Stop.-to-cross move.
```

## READY TO TRY: Your missing stacks are under you  (merged feat/dig-followups, commit 4ab44bf)
```
Restart needed: full game restart (Java code)
See it now:
  1. Launch "Dev Client", /a1016 stage 2. Place a bed, sleep, then /a1016 dig network grow 8.
  2. Put some items in a chest at your base, walk away, then /a1016 fire under_you_stack (a stack goes missing).
  3. /a1016 dig network grow 2, then /a1016 dig network chest   (prints the dark chest's position and contents).
  4. /a1016 dig network reveal, dig down, break into the tunnel.
What to look for: the stack that vanished from your chest is sitting in a chest in the dark under your base.
  The tunnel that grows toward you keeps its end current, so a fragment placed there stays at the very end.
Known rough edges: the dark chest only arrives once a far abandoned build or hut exists to take it from.
```

## READY TO TRY: He uses your real fog, and you can't outrun him  (merged feat/entity-fogdistance, commit ae5c20f)
```
Restart needed: full game restart (new client-to-server fog report and Java code)
See it now:
  1. Launch "Dev Client". Render Distance 6. /a1016 stage 2, /time set 13000, /a1016 atmosphere fog dusk 0.6
  2. /a1016 fire sighting_close   then   /a1016 entity info   (shows "reported fog end" from your client and the
     distance he spawned at; in testing: fog end 55, close spawn 24)
  3. /a1016 fire sighting_walks_away, then sprint after him: he keeps about 10% ahead of you, up to 9 blocks/s.
  4. Tune live: /a1016 entity tune  (normalFractionMin/Max 0.55-0.75, closeFractionMin/Max 0.35-0.50,
     closeMinDistance/closeMaxDistance 16-28, minDistance 12, outrunFactor 1.1, maxRunSpeed 9)
What to look for: he should now always be inside what you can see, never lost in the fog, at a distance that
  feels the same whatever the fog level.
Known rough edges: flying after him in creative still catches him (the close-chase rush, D-037, is next).
  If your fog ends closer than 12 blocks, he doesn't spawn at all.
```

## READY TO TRY: Core update (contract batch)  (merged feat/core-contracts, commit 3e434ee)
```
Restart needed: full game restart (core Java code)
See it now:
  1. /a1016 timewarp 2   now prints the director's summary of the skipped days.
  2. Fog drift now ships at 0.45 to 0.7 by default (D-022; your config already had it).
What to look for: mostly invisible plumbing. It unlocks the next features: the dig-down exit, the zombie with
  your sword, the gravel ceiling and dripstone traps, blank signs and "Stop.", and new scars that never touch the
  untouched grove.
Known rough edges: none visible. The workstreams adopt the new calls in their next tasks.
```

## READY TO TRY: Bright eyes and sightings you can actually see  (merged feat/entity-eyes, commit 3f33a8e)
```
Restart needed: full game restart (new renderer, shader and Java code)
See it now:
  1. Launch "Dev Client". Render Distance 6. /a1016 stage 2, /time set 13000, /a1016 atmosphere fog dusk 0.45
  2. /a1016 fire sighting_walks_away   (or sighting_cow, sighting_ridge, sighting_close). Turn slowly; he's behind you.
  3. Eyes, live: /a1016 entity eyes bright 0.5  |  /a1016 entity eyes bright 0.8 (pierces more fog)  |
     /a1016 entity eyes flat  |  /a1016 entity eyes glow.  Bare /a1016 entity eyes prints the current style.
  4. Tuning, live: /a1016 entity tune shows every value. For example /a1016 entity tune approachBlocks 14,
     /a1016 entity tune stareSeconds 4, /a1016 entity tune spawnDistanceFractionMax 0.6,
     /a1016 entity tune closeMaxDistance 30
What to look for: a hazy but clear shape at about half the fog distance. At night his body fades into the fog
  but two pale points stay. Walking a few steps no longer scares him off: he leaves after 10 blocks of real
  approach, within 18 blocks, or after 3 s of staring, then stares back 2 s before turning.
Known rough edges: the fog distance uses atmosphere's curve directly until the shared core helper lands.
  The "goes under" exit (D-030) is next.
```

## READY TO TRY: Fragments (all 30, placed by stage)  (merged feat/lore-fragments, commit 3ea390a)
```
Restart needed: full game restart (new Java code and mixins). Later fragment text edits only need /reload.
See it now:
  1. Launch "Dev Client". /a1016 lore list shows every fragment: rolled or not, placed (where), read.
  2. /a1016 stage 1, then /a1016 lore place F01 (it builds or fills the site out of view and prints coordinates;
     /tp there and open the chest).
  3. /a1016 lore give F06   (the list, with your name already written in at the end). Open it, then /a1016 state.
  4. More: /a1016 lore place F07 (the seed sign in the largest ocean pyramid's core), /a1016 lore place F14
     (10 blocks under world spawn), /a1016 lore place F15 (the sealed test room), /a1016 lore read F23 then
     /a1016 lore place F28 (the map in an Abandoned Camp).
What to look for: lowercase 2010 forum voice, word for word from the design. Each one is ambiguous alone.
  Books have no author. The F30 signs can't be broken (try a pickaxe, TNT or a piston).
Known rough edges: telling (Stop., blank signs, place not found, the list gaining causes) is next (v0.6).
  Fragment builds were tested in a flat test world, so check how they sit in real terrain.
```

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
