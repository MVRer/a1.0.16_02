# a1.0.16_02: the manual (self-created branch)

This branch's front page is written in character. This is the plain version. Nothing on this page says what the self-created layer contains.

A horror mod for Minecraft Java 26.3 "Wilderness Bound" (Fabric). You are supposed to be alone in your world, and you are not. The mod never tells you what is going on. It only leaves evidence. No jumpscares, no monsters: everything you see could be the game.

**Best played in hardcore.**

## What it is
- A director decides what happens and when, and long stretches of nothing are part of the design. Two worlds never play the same.
- The land carries scars from the first minute: dead hills, bare forests, straight tunnels, small pyramids in the sea.
- A figure at the edge of the fog that always leaves first.
- 30 story fragments spread across the map. Each one is ambiguous alone.
- Accidents that look like the game's fault, each leaving one clue a careful player can find afterward.
- Four endings.

## This branch: the self-created layer
`self-created` is the whole mod above plus one more layer. What the layer is, is written down only in the spoiler docs (see "The other docs"). It is meant to be played blind.

- It ships in the same jar as the rest of the mod. There is nothing extra to install.
- Everyone in the world needs the jar built from this branch. A player with a jar from `main` or from Releases does not have the same mod.
- The layer is on by default. You can turn it off; see "The switch" below.
- The commit messages on this branch spoil the layer. Don't read the log if you want to play it blind.

## Requirements
- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API for 26.3 (0.162.0+26.3 or newer)
- Java 25
- In multiplayer, everyone needs the same jar: not just the same version number, the same file. The server or LAN host too.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 in your `mods` folder.
3. Build the jar from this branch (see "Build from source") and put it in your `mods` folder. Give the same file to everyone who plays.
4. Start a **new** world. Hardcore if you can.

## Multiplayer
Open the world to LAN or run it on a server. Everyone shares one story: anyone's reading, naming or telling counts for the whole world. Each player feels him at their own pace, and some things only happen to a player who is alone. Friends standing together hear the same sounds and share the same fog and silence. In hardcore, a death he caused ends that player's story; the world's story ends when nobody is left in it.

## The switch
The self-created layer has one switch. It is on by default.

- In game (op): `/a1016 sc off` and `/a1016 sc on`. It takes effect at once and is written to the config, so it stays that way after a restart.
- In the config: set `enabled` to `false` in the `sc` section, under `sections` in `config/a1016_02.json`.
- Off stops the whole layer and puts back what it changed where it can. The rest of the mod goes on as usual.
- In singleplayer there is one config file. On a server, the server's file decides for the world and each player's own file decides for their own game, so set it the same everywhere.

## Config
`config/a1016_02.json` is created on first start with every default filled in. You never need to edit it. Each part of the mod keeps its own section with its own version; when an update changes a default, the file is upgraded on load and anything you changed is kept. A file that cannot be read is copied to `a1016_02.json.broken` and the defaults are written.

The self-created layer's own sections use letters for their keys, on purpose, so reading the file spoils nothing. Leave them alone; the switch above is the only one meant for you.

## Commands
Everything is under `/a1016` and needs op (permission level 2). They are testing tools, and most of them spoil something.

- `/a1016 state`: the shared story, then each player's own records and pacing
- `/a1016 stage <n>`: jump to a stage
- `/a1016 fire <cardId> [fake] [player]`: try to make one event happen now
- `/a1016 timewarp <days>`: move the story's clocks forward and print the director's summary
- `/a1016 profile reroll`: roll the world a new profile (its habits, density, tempo and signature)
- `/a1016 director`: the director's state, one block per player taking part
- `/a1016 fog`: the fog weather now; `/a1016 fog night`, `morning` or `day` (add `quiet` for a quiet one) starts a spell, `/a1016 fog clear` ends it
- `/a1016 sc on` / `/a1016 sc off`: the self-created layer's switch

A command that acts on a player takes the one you name, else you, else (from the server console) the first player taking part.

Each part of the mod also adds its own under its name (`/a1016 entity`, `atmosphere`, `world`, `dig`, `lore`, `accident`, `ending`). Those spoil the part they belong to, so they are not listed here, and neither are the layer's other commands under `/a1016 sc`.

## Build from source
Requires JDK 25. Check out this branch first (`git checkout self-created`).

```
./gradlew build          # builds the mod and runs the game tests; the jar is in build/libs/
./gradlew runDevClient   # dev client that opens a ready-made test world
```

## The other docs
Most of these spoil the story. Read them after you play.

- [DESIGN.md](DESIGN.md): the design document (every event, fragment and ending)
- [STORY_CLUES.md](STORY_CLUES.md): how each step of the story is pointed to in the world
- [ARCHITECTURE.md](ARCHITECTURE.md): contracts, code ownership, build and commands. On this branch it also has a "Self-created layer" section; it is written in neutral words, but it still hints.
- [DECISIONS.md](DECISIONS.md): design decisions made during development
- [TRY_IT.md](TRY_IT.md): how to try each feature
- [PLAN_V1_2.md](PLAN_V1_2.md), [PLAN_V1_3.md](PLAN_V1_3.md), [TASKS.md](TASKS.md): development plans and the task board
- `SELF_CREATED.md` and `SELF_CREATED_2.md`: what the self-created layer is. Full spoilers. Not linked, on purpose.

## Credits
Named after Alpha 1.0.16_02, the version in the 2010 Herobrine story. All characters are fictional. No real people, companies or forums are depicted. Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.

## License
MIT. See [LICENSE](../LICENSE).
