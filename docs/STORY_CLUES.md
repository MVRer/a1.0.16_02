# Story clues: a proposal

Oct 14, 2026 · for @Mariano · status: proposal, nothing in the game has changed

The goal: keep the mystery of why (who he was, how he died, what he wants) and remove the frustration of what to do. A group that pays attention can always work out the next step. A careless one still misses it.

Everything below was checked against the code on `main` (7326b2e), not only against DESIGN.md. Where the code is stricter than any clue could make fair, the proposal loosens the code.

## 1. The clue map

Ratings: **clear** (a careful group gets it from what's in the world), **guessable** (needs a leap, or works by habit or accident), **near impossible** (nothing in the world points there).

"Rolled" means the fragment exists in only some worlds (D-003: about 12 of the 20 optional ones). F08, F15, F16, F21, F24 and F29 are all rolled.

### Stages and endings A, B, C

| Action | What the code checks | What points there now | Rating |
| --- | --- | --- | --- |
| **Stage 3 starts**: name him once | `NameMatcher` finds "herobrine" in a sign, a book or a chat line, ignoring case, spaces, punctuation and 3/1/0 swaps. Only naming starts Stage 3; writing near his traces counts as telling but doesn't (D-041) | F15 "1. dont put his name in commit messages. 2. dont put it in chat." · F05 "dont post it. i mean it." · Outside the game, everyone knows the name | **Clear.** The warnings are the invitation |
| **"Stop." (F03) appears**: write a sign that names him while in Stage 3 | The first sign naming him written once the stage is already Telling becomes the "Stop." sign. Chat and books never do. The sign that started Stage 3 doesn't count | F02 "my thread got deleted. like 5 min. im posting again." · F05 "dont post it" | **Guessable.** A group that only names him in chat never gets "Stop.", so Ending A can't happen. Fine for a trap; no change proposed |
| **Stage 4 starts** | No action of its own. It begins when an ending commits (D-006): A, B or C by their rules, D at the last plank | n/a | n/a |
| **Ending A**: obey "Stop." | `aWhy`: "Stop." seen; nobody named him since; the director's `obeyed_after_stop` (3 in-game days with no telling); no fragment read and no visit within 1 chunk of a tunnel end, pyramid, cut, bare grove or dead mountain for 3 days | F03 "Stop." · F24 "i did what the / sign said. quiet / for a week / i thought" (rolled) · F21 "day 14 said sorry to him. got quiet" / "day 19 its been really quiet" (rolled) · F15 "4. dont talk about it after." (rolled) | **Clear.** "Stop." is an order, and doing nothing is what a scared group does. Exploring quietly resets it, which is right for a trap |
| **Ending B**: keep telling | Stage 3+ and 12 tellings, or 3 tellings after "Stop.", or attention 95. Or a player's third marked death (D-063) | F05 "people who post stop being on the list." · F06 "pk\_alpha . gone" / "t0rch . gone" · F02 "im posting again" | **Clear.** It's where loud play goes |
| **Ending C**: burn every fragment, take your house down, stay quiet | `cWhy`: Stage 3+; at least 1 fragment item thrown into lava or fire by a player; every fragment item anyone ever held burned that way and none held anywhere (inventory, nested containers, ender chest, containers within 24 of the base); the player broke at least 75% of the most blocks they ever had around home (at least 12) and at most 25% remain; then 3 days with no naming, no scar visit and no fog staring (20 s still outdoors, looking level, dusk to dawn or rain) | F15 "3. if a build has tunnels nobody wrote, roll it back." (rolled) · F16 "wed. took his pass out completely." (rolled) · F19 "took the profile down after." (rolled). Nothing says burn the books or take your own house down. The ending is named after F20 "for the record", which only appears on the Ending B path | **Near impossible.** Also silently blocked forever if any fragment someone held was lost another way: despawned, blown up, or left in a friend's chest |

### Ending D

| Step | What the code checks | What points there now | Rating |
| --- | --- | --- | --- |
| **0. Reach the start**: read F23 | F23 needs 6 fragments read. F28 is only placed after F23 is read | Panic towers are a visible scar, and climbing them is natural | **Guessable** |
| **1. The map** | F28 read (held in a hand once) and any player within 48 blocks of the untouched grove's center. F28 goes into the **buried** secret chest of the nearest Abandoned Camp to the base whose secret chest is still unopened (within 768 blocks). In the 26.3 camp pieces that chest is an oxidized copper chest one block under a barrel, a campfire, gravel or a log | F16 "sat. its not random. it goes where people are." (rolled, and it doesn't point at camps). Once found: the item name "where he didnt" and the map's red X | **Near impossible** to find on purpose; **clear** once found. Nothing says a map exists, let alone in a buried chest |
| **2. The untouched grove** | F30 read (within 4.5 blocks, looked at for 1 s) and at least 1 poplar log cut that grew inside the grove. Logs carried in don't count; logs cut earlier do | The map's red X · F30 "i did, but" on the oldest poplar · the only grove still red and orange | **Guessable.** Reading F30 is clear. Cutting is habit, but nothing asks for it, and a group that has read F26 "planted them / all back / thought hed / like it" may leave this grove alone |
| **3. Take it back** | F13 must exist. It is placed only in **Stage 3**, and only if the first crafting table, else the first chest, else the first block, still stands where it was placed. Break the cairn's center with no item thrown within 16 blocks on that visit; then, **while the chain is on step 3**, a player carries the marked drop 32+ blocks from the cairn | The table missing at home · finding it inside the nearest pyramid (F13 itself) · F15 "6. dont go back for things." points the other way. DESIGN's entry (F29 "everybody else got one") doesn't point at the cairn | **Guessable.** Finding your own table in his pyramid makes you take it. But: if nobody ever named him, there is no cairn at all. If the group took it home before step 3, the step waits until someone happens to pick it up again. One dropped item on the visit when it was taken, and it never counts |
| **4. Under the seed** | A player inside the 7x7 chamber around F30's twin on bedrock. The team's stair is built once F28 is read; its top sits just under the first solid block below the F07 sign, sealed under the pyramid's sand and the sea floor | F07 "4788685 / 74082066 / 804" (says nothing about digging) · F25 "this is the only place he cant cut" (usually at a different stair) · F30 "i did, but" (nothing says it has a twin) | **Near impossible.** Nothing says there is anything under the seed pyramid |
| **5. Count your torches** | Exactly 6 torches (any torch, standing or on a wall, not redstone) in the chamber at one check (every 10 ticks). A wrong count does nothing | F25 "count your torches" · F06 and F23 list six names besides yours · F23 gives causes for only four; "stoneslab ." and "kirbyfan\_92 ." are blank, and DESIGN reads blanks as "the not-yet" · the cross row has four old crosses | **Near impossible** by reasoning: every count in the world says four. It passes by accident if someone keeps lighting the room (the count goes through 6). It fails for a group that lights the room with two torches, or that lit it with seven before step 5 began |
| **6. Finish the sentence** | A sign (not the twin) within 2 blocks across and 2 up or down of the twin, whose letters on one side are exactly "heisnolongerwithus" (F08's lines). His name or "sorry" on any chamber sign arms the fall | F08 "he is no / longer / with us" (rolled: missing in about 4 worlds in 10) · the twin "i did, but" · F29 "you have to say it to make one" (rolled, and only after F21 is read: most runs never see it) | **Guessable** with F08 or the canon line, **near impossible** without. The fan's answer, "i did, but he is no longer with us", fails the exact match. So does "hes no longer with us" |
| **7. His cross** | The marked first block placed directly on bedrock in the chamber, at least 2 solid blocks stacked on it, the top one **poplar planks** crafted from marked grove logs, and at some middle level a solid block on both sides (D-057: any recognisable cross) | None. DESIGN's cell is empty. F29 "we never gave him one / everybody else got one" (rolled, rare) says a cross, not what it's made of | **Near impossible.** Nothing says the foot is the first block or the top is grove wood. A grove log on top instead of planks fails |

### Silent blockers found while reading the code

These stop a step with no sign at all. No clue can fix them; the code has to.

1. **D needs someone to name him.** F13 (the cairn) is Stage 3 only, and only naming starts Stage 3. The careful group, the one that obeys F05, F15 and F22, never gets a cairn, so step 3 can never be done.
2. **F13 needs the first table, chest or block still standing.** If all three were broken or moved, F13 is never placed. Many players pick up their first crafting table.
3. **Step 3 is strict about timing.** The block must be carried while the chain is on step 3. One offering on the taking visit sets `first_taken` to false for good, unless someone puts the block back in the exact center and takes it again.
4. **The true sentence counts as telling.** Once the stair has lost a block (`ending:d/stair`, ledgered as his edit), the chamber is within 32 blocks of "his traces". The step-6 sign then raises attention and adds a telling, and can tip Ending B (3 tellings after "Stop.").
5. **Ending C is blocked forever** by any fragment that was held and then lost any other way than lava or fire.
6. **Only the subject's actions count in ending.d.** `Chain.check`, `Cairn.onBreak`, `Cairn.onItemAdded` and `Grove.onBreak` all check `subject` or `isSubject`. With friends (D-064), a correct action by the wrong player silently does nothing.

## 2. Principles

1. **Three clues per conclusion.** Every action a player must take gets at least three independent pointers, because players miss some. At least one is concrete: a place, an object, a count or the exact words.
2. **Clear what, dark why.** Clues say what to do and where. They never say who he was, how he died, what he wants, or what the cross will do to him.
3. **People leave clues, never him.** He cannot create and cannot write. Every new text is by the team, the developer or the dead on the list. Late clues "turn up"; who surfaced them is never said.
4. **In-world only.** Books, signs, places, objects, what's missing. No UI, no objective text, no tutorial voice.
5. **Voice.** Lowercase 2010 forum and dev-note style, short, no explanations. Signs are 4 lines within the 90 px sign width (about 15 characters), split with " / ". No real names: only "the developer", "the studio", "the team". Real quotes stay unattributed.
6. **Escalate.** First the oblique clue (an existing text, or a reworded one), then the concrete one (a place or object), and last the late clue, almost plain but still in a dead person's voice.
7. **Critical means always on.** A clue the true ending depends on is fixed, like the D-003 chain. A clue that sometimes doesn't exist is not a clue.
8. **The check accepts what the clue says.** If the world says "he is no longer with us", the sign check takes the whole canon line too. If the world says "wood from where he didnt", logs and planks both count.
9. **Clues sit where the danger is.** Each new clue is at a place that is already hunted (the camp at night, the grove canopy, the cairn's route, the flooding stair). Being helped never feels safe, and he never reads as helpful.

## 3. The proposal

Cost tags: **(text)** reword an existing fragment · **(new fragment)** a book or sign on an existing site or placer type · **(place)** an environmental clue built by others · **(mechanic)** needs new code.

Every (text) change also needs its DESIGN.md table row updated, because `LoreTextTests` compares the fragment data with DESIGN.md word for word.

### 3.0 One-time plumbing (mechanic, small)

**M0. Switch on clue fragments.** `WorldProfile.allFragmentIds()` stops at F30, `isEnabled` reads the saved profile, and `LoreTextTests` requires the data ids to equal that list. Add a `CLUE_FRAGMENTS` set (F31 to F44) that is always enabled, also in old saves. Extend the id list and the test. Add every Ending D clue to `PlaceNotFound.NEVER`, so F04 can't erase one.

### Gap 1. Ending D can't start for a quiet group

**M3 (mechanic, small).** F13 is also placed once F28 has been read, even in Proximity. Stage 3 placement stays as it is for every other path. One new placement condition in `FragmentEngine.eligible`, for example `"stage_or_read": "F28"` in f13.json.

**M11 (mechanic, small).** If the first table, chest and block are all gone, F13 uses that player's oldest player-placed block still standing at their base, and stores the choice (D-061).

### Gap 2. Step 1: nothing says a map exists, or where

**T1. F23 gains a second page (text).** Page 1 is untouched, because `LiveBooks` writes the cause onto the "[PLAYER NAME] ." line.

- Old (one page): "pk\_alpha . fell" / "t0rch . lava" / "dyl4n\_m . drowned (in his house)" / "oreo\_tm . fell" / "stoneslab ." / "kirbyfan\_92 ." / "\[PLAYER NAME\] ."
- New, page 1: the same, unchanged.
- New, page 2: "someone found a map at one of the camps. it was buried. it shows where he didnt. they went to look"

It lands at the right moment: F28 is placed when F23 is read. "they went to look" reads as one more dead name.

**F31 (new fragment).** A sign at the camp itself (see the fragment table below).

**F38 (late clue).** A light at the camp (see Late clues).

### Gap 3. Step 2: nothing asks the group to cut wood here

**F33 (new fragment).** One poplar in the untouched grove has been cut down to a stump by people, with a sign on it. This also seeds step 7's top block. Because the stump grew in the grove, cutting the stump itself counts as a grove log.

**F39 (late clue).** A plainer sign by the stump.

### Gap 4. Step 3: the cairn is found, but the step is strict

**F34 (new fragment, JSON only after M0).** A dead player's sign in the emptied core of another ocean pyramid. It uses the existing `pyramid_core` rule.

**M4 (mechanic, small).**
- Once the first block is out of the cairn cleanly, it counts wherever it is, carried or placed (a tracked `first_block`), 32+ blocks from the cairn, as soon as the chain reaches step 3.
- An offering spoils only the visit it was left on. If the block left on a spoiled visit, the step waits until someone brings it back within 16 blocks, leaves nothing, and walks it 32 blocks away again. Going back is the danger (the hollow route), so the lure keeps its teeth.

**F40 (late clue).** A sign on the shore nearest the cairn.

### Gap 5. Step 4: nothing says there's anything under the seed

**F32 (new fragment).** A team sign beside F30 in the grove, pointing at the twin.

**P1 (place, small).** When the team's stair is finished, the column of sand and gravel between the F07 sign and the stair's top becomes cobblestone. It is ledgered `lore:left/`, so it is never undone. Whoever opens the seed pocket sees the sign standing on cobblestone inside a sand pyramid, and digs. Digging opens the way for the sea, so the flood still works exactly as now.

**F41 (late clue).** A torch on a lone block on the seed pyramid, with a sign.

### Gap 6. Step 5: every count in the world says four

**F35 (new fragment, small placer).** The team's six plates in the chamber, one per name, each above an empty cobblestone ledge. This settles that stoneslab and kirbyfan\_92 are dead without giving a cause, and makes the count something you can see. The same build carries step 6's blank plate.

**F42 (late clue).** A book in the chamber chest.

**M6 (mechanic, optional, multiplayer).** See section 4.

### Gap 7. Step 6: the words are missing in 4 worlds in 10, and the check rejects the canon line

**M2 (mechanic, one line).** Add F08 to `WorldProfile.FIXED_FRAGMENTS` (and to `PlaceNotFound.NEVER`). M0 makes it reach old saves too. F08's lines are where the code reads the sentence (`Chamber.SENTENCE_FRAGMENT`), so its text stays exactly as it is.

**M1 (mechanic, small).** In `Chamber.Written.says`, accept one side whose letters are exactly one of:

- "heisnolongerwithus"
- "hesnolongerwithus"
- "ididbutheisnolongerwithus"
- "ididbuthesnolongerwithus"

A sign that names him or says "sorry" never counts.

**F35's blank plate (place).** A blank, unwaxed standing sign on bedrock within 2 blocks of the twin. The empty sign beside "i did, but" is the spot, and a sign is the object. Writing on it passes the current radius check.

**M9 (mechanic, small).** Signs inside the Ending D chamber are not "writing near his traces". Naming him there still counts as naming.

**F43 (late clue).** A book with the full line.

### Gap 8. Step 7: no pointer at all

**F36 (new fragment, rides on F35's placer).** The team's note in a chest in the chamber. It names the cross and both materials, without saying why or what it does.

**M5 (mechanic, small).** In `Chamber.cross`:
- The top block counts if it is tracked as grove wood, whatever its shape: planks, log, wood or stripped log. Drop the `POPLAR_PLANKS` check.
- The marked first block may be anywhere in the post, as long as the post's lowest block stands on bedrock.

The arms rule stays.

**F44 (late clue).** The plain version.

### Gap 9. Ending C: no pointer, and a silent blocker

**T2. F15 gains a second page (text).** Page 1 is untouched: rule 6 is step 3's danger line, and page 1 already uses 10 of its 14 lines.

- Old (one page): "1. dont put his name in commit messages. 2. dont put it in chat. 3. if a build has tunnels nobody wrote, roll it back. 4. dont talk about it after. 5. this is not a joke so stop making it one. 6. dont go back for things."
- New, page 1: the same, unchanged.
- New, page 2: "7. burn the notes. all of them, this one too. 8. if he got into something you built, take it down yourself before he does."

**F37 (new fragment, small placer).** A sign at a bare foundation: someone who did C. It is fixed, because F15 is rolled.

**M8 (mechanic, small).** A fragment item destroyed any way (lava, fire, cactus, explosion, void, despawn) counts as gone. Only fragments that still exist count against C. "At least one thrown into lava or fire" stays.

### New fragments (DESIGN.md table columns)

All of these are fixed (always on through M0) and appear only when their step is reached, so they don't crowd the early game. Every sign line below was measured with `BookLayout` and fits in 90 px; every page fits in 14 lines.

| ID | Form | Where / when | Text | Implies |
| --- | --- | --- | --- | --- |
| F31 map is back | Sign | On dry ground at the edge of F28's Abandoned Camp. Placed with F28 | "map is back in" / "the green chest" / "under the camp." / "not at night" | Someone found the map, put it back for the next one, and knew the camp was dangerous after dark (step 1's creepers) |
| F32 the other one | Sign | Standing, 2 to 4 blocks from F30 in the untouched grove. Placed with F30 | "the other one" / "is under the" / "numbers. all" / "the way down" | F30 has a twin. It's under the seed sign (F07), at bedrock |
| F33 for the top | Sign on a stump | One poplar in the untouched grove, 6 to 16 blocks from F30, cut down to its bottom log by people (`lore:left/`; its leaves gone as if they decayed). Placed with F30 | "took one from" / "here. for the" / "top of his." / "never finished" | People cut wood here once, meant for the top of something of his. You can cut here too |
| F34 took mine back | Sign | The emptied core of the nearest ocean pyramid that isn't F07's or F13's (existing `pyramid_core` rule, `not_with` F07). Stage 3+, after F13 is placed | "found my table" / "in here. took" / "it back. left" / "him nothing" | Pyramids hold what he took from people. You can take yours back; leave him nothing. Whoever wrote it is dead anyway |
| F35 the plates | Seven signs | In the bedrock chamber when the team's stair is finished. Six waxed wall signs, a name on line 2 of each, every one above a single cobblestone ledge on the floor with nothing on it. A seventh standing sign, blank and not waxed, on bedrock within 2 blocks of the twin | "pk\_alpha" · "t0rch" · "dyl4n\_m" · "oreo\_tm" · "stoneslab" · "kirbyfan\_92" · (blank) | The team counted six dead, one light each. The seventh plate is blank: nobody could write his. It stands beside "i did, but" |
| F36 nothing he took | Book, no title | A chest in a chamber corner (F25's, if F25 was offered there). Placed with F35 | "nothing he took can go in his cross. it has to stand on something you got back from him. wood from where he didnt at the top. we got this far. none of us could say it" | His cross: the foot is what you took back from the cairn, the top is grove wood. The team stopped at the line (F29) |
| F37 took it down | Sign | A bare house floor, walls and roof gone, 150 to 1000 blocks from the base (people's build, `lore:left/`). Stage 3+ | "took it down" / "myself. burnt" / "the books. its" / "quiet here" | Someone took their own house apart and burned the books, and it went quiet there. It doesn't say they lived |

F35 and F36 need one small placer, `chamber_plates`, run when the stair finishes. F31, F32, F39 and F40 share one new generic placer, `sign_near`: an anchor, a radius and "dry ground". F33 needs `grove_stump`, and F37 needs `bare_foundation` (a `Builders.planHouse` floor only).

**One note on F25.** F36 and the late chamber books are complete texts on bedrock too. F25 stays the only complete **warning**. DESIGN's "the only complete one in the world" should read "the only complete warning".

### Late clues (mechanic, medium)

**M7.** If the group has been stuck on one step for a number of in-game days, a plainer clue turns up.

**The trigger.**
- Ending D publishes `ending:d_step=<n>` and `ending:d_step_day=<day>` as shared flags on every advance.
- For step 1, the clock starts on the day F23 is first read.
- Fragment data gets a new requirement, `"stuck": {"step": n, "days": d}`, read by `FragmentEngine.eligible`.
- Days are in-game days on the world clock, which only runs while a participant is online (D-007, D-059).
- Defaults: steps 1, 3 and 4 after 5 days; step 2 after 3 days; steps 5 to 7 after 2 days. Configurable.

**Where they appear.** Late clues are placed out of view like everything else, and only where the group has been or was already pointed: the camp, the grove, the cairn's shore, the seed pyramid, the chamber chest. They never appear near the base and never name a player, so they read as "it was always there, we missed it".

| ID | Form | Trigger | Where | Text | Implies |
| --- | --- | --- | --- | --- | --- |
| F38 the light at the camp | Structure: one glowstone block on a fence post (a lone light) | F23 read, F28 not read, 5 days | Beside F31 at the F28 camp | (none; F31 is beside it) | Someone marked the camp. It shows from far away only at night, the hour F31 says not to come. The late clue is also the lure |
| F39 cut one | Sign | Step 2, 3 days | Beside the F33 stump | "cut one down." / "this wood is" / "the only thing" / "he never took" | Plain: cut here |
| F40 the nearest one | Sign | Step 3, 5 days | On dry ground at the shore nearest the cairn | "nearest one has" / "your first" / "thing in it." / "take it home" | Plain: the nearest pyramid holds your first block. Take it away |
| F41 the light on the seed | Structure: a torch on a lone cobblestone block rising from the seed pyramid to just above the water, with a sign on the block | Step 4, 5 days | The seed (F07) pyramid | "under the sign" / "inside. all" / "the way down" / (blank) | Plain: dig down from the sign inside. The torch is DESIGN's "torch on a lone block in the ocean at night" |
| F42 one each | Book | Step 5, 2 days | The chamber chest | "six of them. one light each. nothing extra." | Plain count |
| F43 one line came back | Book | Step 6, 2 days | The chamber chest | "asked if there was a brother. one line came back. i did, but he is no longer with us. my thread was gone in five minutes" | The whole line, unattributed. The writer posted, so the writer is gone |
| F44 his cross | Book | Step 7, 2 days | The chamber chest | "his cross goes here, on the bedrock. the first thing you ever put in at the bottom. poplar from where he didnt at the top. arms on both sides. like everybody else got" | Plain recipe. Still never says why, or what it does |

### After the changes: pointers per Ending D step

New items are in bold. **(c)** marks the concrete one.

| Step | Pointers, oblique to plain |
| --- | --- |
| 1. The map | F16 "it goes where people are" (rolled) · **T1 F23 page 2** "a map at one of the camps. it was buried" · **F31** "the green chest / under the camp." **(c)** · **F38** a light at the camp (late) |
| 2. The grove | F28's red X **(c)** · F30 "i did, but" · **F33** a cut poplar and "took one from / here" **(c)** · **F39** "cut one down." (late) |
| 3. Take it back | Your table missing at home · F13: your own table inside the nearest pyramid **(c)** · **F34** "found my table / in here. took / it back. left / him nothing" · **F36** "something you got back from him" · **F40** "nearest one has / your first / thing in it." (late) |
| 4. Under the seed | F25 "the only place he cant cut" · F07 the numbers · **F32** "the other one / is under the / numbers. all / the way down" · **P1** the sign standing on cobblestone in a sand pyramid **(c)** · **F41** a torch on the seed pyramid (late) |
| 5. Torches | F25 "count your torches" · F06 and F23: six other names · **F35** six plates over six empty ledges **(c)** · **F42** "six of them. one light each." (late) |
| 6. The sentence | The twin "i did, but" · **F08, now always there** "he is no / longer / with us" **(c)** · **F35** the blank plate beside the twin **(c)** · F29 "you have to say it to make one" (rolled) · **F43** the whole line (late) |
| 7. His cross | F29 "we never gave him one" (rolled) · **F35** six plates and one blank: he never got one · **F33** "for the / top of his." · **F36** "nothing he took can go in his cross..." **(c)** · **F44** the plain recipe (late) |

**Left alone on purpose.** Stage 3, "Stop.", and Endings A and B are already clear. More clues there would weaken the traps. F07, F25, F29 and F30 keep their text.

## 4. Multiplayer notes

1. **One reader unlocks the chain for everyone** (D-058). When one friend reads F23, F28 appears, and the others don't know why. So chain-critical words go in books, which can be handed over (T1, F36, F42 to F44). Signs are kept for places the group has to visit anyway.
2. **Comparing notes is a feature.** The three-clue rule gets easier in a group, so spread the clues so that different people tend to find different ones: one finds the F23 page, another finds the camp sign, another remembers F16. Nothing in the proposal depends on one player's private memory.
3. **"Your" first block is one player's** (D-061: fixed rule, stored). The owner notices the table missing at home and tells the group. F36 says "something you got back from him", which is true for whoever pulled it out of the cairn. F40's "your first thing" reads true for the owner and as "someone's" for the others. That's fine.
4. **Each reader sees their own name on F06 and F23** (D-061). "Mine has my name." "Mine has mine." That chill comes free. No proposed text depends on whose name is on the list, and the plates never name a player.
5. **A dead friend changes step 5's count.** In hardcore, a player whose line was finished by a marked death is now a dead list member, and a group may light one for them, which makes 7. **M6 (mechanic, optional):** torches required = 6 + participants whose line was finished by a marked death. If M6 is adopted, F42 becomes "six of them, and one for each of yours he took. one light each. nothing extra."
6. **Dangers hit whoever acts.** One friend writing "sorry" on a chamber sign arms the fall. An item thrown by anyone spoils the cairn visit. F34 "left / him nothing" speaks to the group.
7. **Ending A and C are asked of the group** (D-064). One chatty friend breaks A, and F15 "2. dont put it in chat." now reads as a group rule. For C, F15's "all of them, this one too" reminds the group to gather every copy. A fragment in an offline friend's inventory must not block C forever (M8 plus D-064).
8. **The late-clue clock is the world's**, not one player's. A friend idling alone moves it, and that's fine: the group is still stuck.
9. **M10 (mechanic, blocker).** ending.d must count any participant, not only the subject. That covers `Chain.check` (looping over participants), `Cairn.onBreak` and `onItemAdded`, `Grove.onBreak`, and `Marks.carries`. If the multiplayer worktree hasn't covered ending.d yet, it has to. Otherwise every clue above can be followed perfectly and still do nothing.

## 5. What stays a mystery

No proposed line answers anything in the "Never learns" column. The ones that come close:

| Never learns | Lines that come close | Verdict |
| --- | --- | --- |
| His name in his own words | F35's seventh plate | Stays blank. Nobody writes his name; he writes nothing. Safe |
| How he died | F43 quotes the whole canon line | Says he is dead, as F08 already does. Never how. Safe |
| What he wants | The late clues (F38 to F44) | **The real risk.** If help appears exactly when you're stuck, a sharp player may think he is helping, so he wants the cross. Mitigations: late clues only turn up where the group has been and could have missed them; they are human and old; F38 glows only at the hour F31 warns against, and F40 stands on the cairn's hollow route. If that isn't enough, drop F42 to F44: the chamber already has F35 and F36 |
| What he wants | F36 and F44, "his cross" | The team thought he should have one. Never "he wants", never "hed like". Safe |
| Who sent "Stop." | none | Untouched |
| Whether he is still "someone" | F36 and F44 call it "his cross" | A burial for a person who died. Whether the thing in the fog is still that person stays open. Close; keep the wording exactly as written |
| Whether the developer regretted it | F33 "never finished" | Could read as the developer's regret if a player assumes he wrote it. F36's "we got this far" makes the team the speaker. For zero risk: "nobody finished" |
| How to make him stop for good | F36, F44, F37 | No line says the cross ends, frees, rests or stops anything. F36 stops at "none of us could say it". F37 says "quiet **here**", which is local and temporary, never "gone". Safe |

Guardrail checks:
- **Guardrail 2.** Every new text is by the team, the developer's side or the dead.
- **Guardrail 6.** No real names. F43 doesn't say who answered.
- **Guardrail 8.** F44 is plain about what to build, never about the theme.
- **Guardrail 10.** The brother never speaks.
- **D-013.** The plates repeat the F06 usernames, so the open check against real players covers them too.

## 6. The short list

Ranked by how much frustration each removes per unit of cost. Items 4, 5, 7, 9, 10 and 11 need M0 once (new fragment ids switched on, old saves included).

| # | Change | Cost | What it fixes |
| --- | --- | --- | --- |
| 1 | **M2** F08 always exists | mechanic, one line | Step 6's words are in every world |
| 2 | **M1** the step-6 sign takes "i did, but he is no longer with us" and "hes" | mechanic, a few lines | The fan's correct answer stops failing |
| 3 | **M3** the cairn also appears once the map is read | mechanic, small | A group that never names him can still finish D |
| 4 | **F35** six name plates over six empty ledges, plus a blank seventh beside the twin | new fragment + small placer | Step 5's count is visible; step 6's spot is obvious; step 7's "he never got one" |
| 5 | **F36** "nothing he took can go in his cross. it has to stand on something you got back from him. wood from where he didnt at the top..." | new fragment (F35's placer) | Step 7 gets its first pointer |
| 6 | **M5** grove logs count on top; the first block may be anywhere in the post | mechanic, small | Step 7 stops failing on shape details |
| 7 | **F32** "the other one / is under the / numbers. all / the way down" + **P1** cobblestone plug under the seed sign | new fragment + place, small | Step 4 becomes findable |
| 8 | **M4** step 3 counts once the block is out and away, carried or placed; an offering spoils one visit, not forever | mechanic, small | Step 3 can't silently lock |
| 9 | **T1** F23 page 2 ("a map at one of the camps. it was buried") + **F31** "map is back in / the green chest / under the camp. / not at night" | text + new fragment | Step 1 has a trail |
| 10 | **F33** a poplar stump with "took one from / here. for the / top of his. / never finished" | new fragment + small placer | Step 2's cut, and step 7's top |
| 11 | **T2** F15 rules 7 and 8 + **F37** bare foundation sign + **M8** lost fragments stop blocking | text + new fragment + mechanic | Ending C becomes discoverable and finishable |
| 12 | **M7** late clues F38 to F44 | mechanic, medium | Nobody stays stuck forever |

Also needed regardless of this list, because these are blockers rather than clues: **M10** (ending.d counts any participant), **M9** (the true sentence isn't telling), **M11** (the cairn survives a broken first table).
