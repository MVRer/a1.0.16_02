# v1.3 plan: he knows

Oct 16, 2026 · after v1.2 (Mariano: "what can make it feel more omniscient?")

**Status: shipped.** C-2, wave 1, INT-2, the R4 to R6 reviews and their fixes, VERIFY-3 (READY) and its two lows
(FIX-FINAL2) are done; 661 tests green 3/3 (`docs/TASKS.md`). No tag or release until Mariano says.

Three things that make him feel like he was watching: he copies what you just did, he acts in the moments you are
not looking (a menu open), and once per world a screenshot you take shows him in the fog. Every worker reads
`docs/ARCHITECTURE.md`, this file's section for their task, and the decisions below. Same process as v1.2
(`docs/PLAN_V1_2.md`, "Waves"): worktrees, one branch per task, at most 4 workers at once, `./gradlew build` green
before reporting, reports of at most 40 lines, core asks in the report.

## Decisions

### D-076 Echoes: he copies you
Minutes after the player does something, the same sounds happen again where they can't see, with their own rhythm.
Only sounds; nothing in the world changes. Deniable: a mob, gravel, a friend, the game.
- What is recorded (core, per player, session only, a ring of the last 64): mining (each block broken by hand, with
  its state, position and tick, grouped into bursts of 2 to 6 breaks less than 3 s apart), doors, trapdoors and fence
  gates used (position, state, tick), containers opened (position, tick).
- `echo_mining` (AMBIENT, Alone, SOUND, fake): one of the player's own mining bursts from 5 to 30 minutes of their play
  ago, replayed with the same number of hits and breaks, the same gaps and the block's own sounds, from inside solid
  rock 14 to 24 blocks from the player (below or beside them), where nobody can see the spot. Fake: a burst with a
  different rhythm and block.
- `echo_door` (MINOR, Traces, SOUND): a door, trapdoor or gate the player used 5 to 60 minutes ago, now 6 to 16 blocks
  away and out of their view, plays its own open then close sound (its `BlockSetType`), the gap the player left
  between them. The block never moves.
- `chest_opens` (existing, MINOR, Traces since v1.2) prefers the container the player opened most recently, if it fits
  the card's rules (out of sight, within its radius); otherwise as before.
- Sounds through `SoundCues.playAt`, so anyone in range hears them (D-060). Never within 30 s of combat.

### D-077 Blind moments
While a menu is open (inventory, a container, crafting, a furnace, trading, a book or sign being edited, the
advancements, the pause menu on a LAN or server world), the player is not watching the world. That is when it happens.
- Core: the client tells the server when such a screen opens and closes (chat, the death screen and the debug screen
  do not count); `PlayerWatch.blindTicks(player)` (0 when not blind). The signal only ever makes things happen to the
  player who sends it.
- Director: `Director.recheck(server, player)`: core calls it 30 ticks after a blind moment starts and every 20 ticks
  while it lasts (at most 10 times). It fires that player's held or parked card if its moment fits now and every gate
  passes; it never rolls or draws. `CardTag.BLIND` cards keep the normal 60-minute hold instead of the 5-minute unfit
  give-back (a menu comes often), and are rechecked first.
- `blind_step` (AMBIENT, Alone, SOUND, PERSONAL, BLIND, fake): one or two footsteps 3 to 5 blocks behind the player
  (against their facing), the floor block's own step sound (the ladder's recipe), while they have been blind for at
  least 1.5 s. Fake: their own step, late.
- `blind_torch` (MINOR, Traces, LIGHT, PERSONAL, BLIND): a torch, wall torch, lantern or soul torch the player placed,
  6 to 20 blocks away and out of everyone's view, is removed through `TraceService.remove` while they are blind (cause
  `dig:blind_torch`). Never the last light within 6 blocks of their bed, never one that keeps a spawn-proof room lit
  if it is the only light in it (cheap rule: at least one other light source within 8 blocks of it). Its support
  stays.
- `blind_door` (MINOR, Traces, SOUND, PERSONAL, BLIND): a door, trapdoor or gate 4 to 12 blocks away and out of view
  plays its own open then close sound while they are blind. Nothing moves.
- Alone (the ladder's rule): no other player, spectators included, within 48 blocks.
- As built: see `docs/DECISIONS.md` D-077 (W-BLIND, W-DIR2, FIX-SMALL). A held BLIND card goes back, parked, after
  `blindHoldMinutes` (15), not the 60-minute hold, so a player who never opens a menu never freezes a tier.

### D-078 The screenshot
Canon: "only one screenshot came out. just fog." Once per world, a screenshot a player takes (F2) shows him at the
fog edge, though he was never on their screen.
- `the_screenshot` (MINOR, Traces, SIGHTING, once per world): fires by arming that player's client for 2 in-game days
  or until they leave. It only fits in the overworld.
- On the client, the next screenshot while armed, with the conditions met (overworld, sky light at the eyes 10 or
  more, the fog end inside 160 blocks or a dusk fog or fog spell on, no screen open, not under water, not in third
  person), renders him into the saved image only: never on the screen, not for one frame (render the frame with him,
  grab it, and keep it off the window; the next frame is normal). If the conditions fail, the arm waits for the next
  screenshot.
- Where: standing on the terrain at 0.6 to 0.75 of the fog end, inside the frame but off the middle (15 to 35% of the
  width from either edge), facing the camera, the default Steve skin, eyes as the configured style, no name, tinted by
  the fog like the terrain. If no ground spot fits, no figure and the arm waits.
- The client tells the server it happened; the server records it once per world (a flag), counts it as that player's
  sighting for the day, and disarms everyone. Nothing else knows: no message, no sound.
- He is drawn by the client only, as a render-only copy of the figure; nothing is spawned in the world.
- As built: see `docs/DECISIONS.md` D-078 (W-SHOT, FIX-SHOT). Only weather fog counts (a dusk fog or fog spell; the
  fog end inside 160 blocks alone never does), and the arm waits with Blindness, Darkness, a surge or a shaderpack; he
  stands 12 to 192 blocks off and at least 24 pixels tall; one arm in the world at a time, ended with the story.

### D-079 Core for v1.3
- `PlayerWatch.recentActions(player)` (D-076's ring, newest first), `PlayerWatch.blindTicks(player)` (D-077), the
  blind payload and its client sender, the recheck calls, `CardTag.BLIND`, `Director.recheck(server, player)` (a
  default no-op the director implements).

## Waves

| Wave | Task | Workstream | Depends on |
| --- | --- | --- | --- |
| 0 | C-2 core batch and docs | core, docs | - |
| 1 | W-ECHO echoes | atmosphere | C-2 |
| 1 | W-BLIND blind moments | dig | C-2 |
| 1 | W-SHOT the screenshot | entity | C-2 |
| 1 | W-DIR2 recheck and BLIND holds | director | C-2 |
| 2 | Merge W-DIR2, W-ECHO, W-BLIND, W-SHOT; INT-2 (pacing re-check with every new card registered, overlay, docs, TRY_IT); preview jar | orchestrator, one worker | wave 1 |
| 3 | R4 echoes and blind moments, R5 the screenshot and client code, R6 core/director/multiplayer/cost | reviewers | wave 2 |
| 3 | FIX-x | workers | the reviews |
| 4 | VERIFY | one verifier | wave 3 |

### C-2 core batch
D-079, plus: version `1.3.0-26.3`; DECISIONS D-076 to D-079; ARCHITECTURE for the new core APIs; DESIGN event catalog
rows (echo mining, echo door, chest opens prefers yours, blind step, blind torch, blind door, the screenshot) and a
line under Triggers or Sightings for the screenshot; TASKS v1.3 rows. Tests: the action ring (bursts grouped, ring
capped, a broken player-placed block counts as mining too, session-only), the blind payload and `blindTicks`
(screens that count and those that don't are the client's job; the server side: open, close, disconnect resets),
the recheck schedule (30 ticks after blind, every 20, at most 10), the stub `recheck` is a no-op.

### W-DIR2 director
`recheck`: for that player only, fire a held or parked card whose `contextFits` is true now, through every gate the
normal decision uses (silence, grace, quiet, empty session, gaps, caps, sharers, PERSONAL), never a new roll or draw;
BLIND cards first. BLIND cards are exempt from the 5-minute unfit give-back (60-minute hold as before). Sims: a BLIND
card's moment is rare in the dice (simFitChance applies only on rechecks: model rechecks as a per-tick chance of a
blind moment, `simBlindPerHour` 20). Tests: recheck fires a held BLIND card while blind and nothing otherwise; it
respects every gate; it never draws; PERSONAL with a friend near; solo sims unchanged when no BLIND card exists.

### W-ECHO atmosphere
D-076: `echo_mining`, `echo_door`, `chest_opens` prefers the player's last container. Config in AtmosphereConfig
(version step only if a default changes). Tests: the burst replays with the recorded gaps and the block's own sounds
from inside rock out of view, at 14 to 24 blocks; the fake differs; the door echo plays the door's own set type at
the recorded gap and never changes the block; out of view and the distance band; combat blocks it; chest_opens picks
the last container when it fits and falls back otherwise; a friend in range hears the same.

### W-BLIND dig
D-077: `blind_step`, `blind_torch`, `blind_door` (tags as above; registered with the director). Reuse the ladder's
step recipe and alone rule. Tests: each fires only while blind 1.5 s or more, alone; the step lands 3 to 5 behind
against the facing; the torch rules (player-placed, out of everyone's view, 6 to 20 away, another light within 8,
never near the bed's last light, support kept, ledgered with its cause); the door plays its own sound and never
changes; a spectator within 48 means no fire.

### W-SHOT entity
D-078: the card, the arm and disarm payloads (entity's own, registered like `FogEndPayload`), the client render into
the saved image only (never on screen), the spot rules, the report back, once per world. Config in EntityConfig
(version step only if a default changes). Tests: what game tests can reach (card gates, arm, once per world, the
report handling, disarm on leave, the pure spot math: distance band, off-centre band, ground found or none); and a
written check list for the dev client (the verifier runs it if it can).

### INT-2 (wave 2)
After the merges: run the director's pacing tests with every new card registered and retune within D-070's targets if
anything moved (first hour, caps, group bounds); overlay lines (blind now, last echo); docs for what was built;
TRY_IT "READY TO TRY: v1.3" with commands to try each (`/a1016 fire echo_mining` after mining a few blocks, open the
inventory for blind moments, `/a1016 fire the_screenshot` then F2 outdoors in fog); TASKS.

## Out of scope
- Pushing, tagging or a GitHub release: only when Mariano says.
