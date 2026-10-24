# a1.0.16_02: the manual

The repository's front page is written in character. This is the plain version.

A horror mod for Minecraft Java 26.3 "Wilderness Bound" (Fabric). You are supposed to be alone in your world, and you are not. The mod never tells you what is going on. It only leaves evidence. No jumpscares, no monsters: everything you see could be the game.

**Best played in hardcore.**

## What it is
- A director decides what happens and when, and long stretches of nothing are part of the design. Two worlds never play the same.
- The land carries scars from the first minute: dead hills, bare forests, straight tunnels, small pyramids in the sea.
- A figure at the edge of the fog that always leaves first.
- 30 story fragments spread across the map. Each one is ambiguous alone.
- Accidents that look like the game's fault, each leaving one clue a careful player can find afterward.
- Four endings.

## Requirements
- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API for 26.3 (0.162.0+26.3 or newer)
- Java 25
- In multiplayer, everyone needs the same jar: not just the same version number, the same file. The server or LAN host too.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 in your `mods` folder.
3. Put the mod's jar from [Releases](https://github.com/MVRer/a1.0.16_02/releases) in your `mods` folder.
4. Start a **new** world. Hardcore if you can.

## Multiplayer
Open the world to LAN or run it on a server. Everyone shares one story: anyone's reading, naming or telling counts for the whole world. Each player feels him at their own pace, and some things only happen to a player who is alone. Friends standing together hear the same sounds and share the same fog and silence. In hardcore, a death he caused ends that player's story; the world's story ends when nobody is left in it.

## Config
`config/a1016_02.json` is created on first start with every default filled in. You never need to edit it. Each part of the mod keeps its own section with its own version; when an update changes a default, the file is upgraded on load and anything you changed is kept. A file that cannot be read is copied to `a1016_02.json.broken` and the defaults are written.

## Commands
Everything is under `/a1016` and needs op (permission level 2). They are testing tools, and most of them spoil something.

- `/a1016 state`: the shared story, then each player's own records and pacing
- `/a1016 stage <n>`: jump to a stage
- `/a1016 fire <cardId> [fake] [player]`: try to make one event happen now
- `/a1016 timewarp <days>`: move the story's clocks forward and print the director's summary
- `/a1016 profile reroll`: roll the world a new profile (its habits, density, tempo and signature)

Each part of the mod adds its own (`/a1016 director`, `entity`, `atmosphere`, `fog`, `world`, `dig`, `lore`, `accident`, `ending`). The full list is in [ARCHITECTURE.md](ARCHITECTURE.md), under "Commands".

## Build from source
Requires JDK 25.

```
./gradlew build          # builds the mod and runs the game tests; the jar is in build/libs/
./gradlew runDevClient   # dev client that opens a ready-made test world
```

## The other docs
Most of these spoil the story. Read them after you play.

- [DESIGN.md](DESIGN.md): the design document (every event, fragment and ending)
- [STORY_CLUES.md](STORY_CLUES.md): how each step of the story is pointed to in the world
- [ARCHITECTURE.md](ARCHITECTURE.md): contracts, code ownership, build and commands
- [DECISIONS.md](DECISIONS.md): design decisions made during development
- [TRY_IT.md](TRY_IT.md): how to try each feature
- [PLAN_V1_2.md](PLAN_V1_2.md), [PLAN_V1_3.md](PLAN_V1_3.md), [TASKS.md](TASKS.md): development plans and the task board

## Credits
Named after Alpha 1.0.16_02, the version in the 2010 Herobrine story. All characters are fictional. No real people, companies or forums are depicted. Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.

## License
MIT. See [LICENSE](../LICENSE).
