# a1.0.16_02

A horror mod for Minecraft Java 26.3 "Wilderness Bound" (Fabric). You are supposed to be alone in a singleplayer world, and you are not. The mod never tells you what is going on. It only leaves evidence.

**Best played in hardcore.**

## What it is
- A director decides what happens and when, and long stretches of nothing are part of the design. Two worlds never play the same.
- The land carries scars from the first minute: dead hills, bare forests, straight tunnels, small pyramids in the sea.
- A figure at the edge of the fog that always leaves first.
- 30 story fragments spread across the map. Each one is ambiguous alone.
- Accidents that look like the game's fault, each leaving one clue a careful player can find afterward.
- Four endings.

## Build and run
Requires JDK 25.

```
./gradlew build          # builds the mod and runs the game tests
./gradlew runDevClient   # dev client that opens a ready-made test world
```

Debug commands (op): `/a1016 state`, `/a1016 stage <n>`, `/a1016 fire <cardId>`, `/a1016 timewarp <days>`.

## Docs
- `docs/DESIGN.md`: the design document
- `docs/ARCHITECTURE.md`: contracts and code ownership
- `docs/DECISIONS.md`: design decisions made during development
- `docs/TRY_IT.md`: how to try each feature

All characters are fictional. No real people, companies or forums are depicted.

## License
MIT. See `LICENSE`.
