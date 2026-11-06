# Rift Watcher

*Reality has fractures. Something on the other side has noticed you.*

A survival-horror mod for **Minecraft 1.21.11 (Fabric, Mojang mappings)**. No new blocks, no new items, nothing to
craft. It just changes what the dark means.

## How it works

### Dread
Every player has a hidden **Dread** meter (0-1000). There is no bar. You feel it.

| It rises when you are... | It falls when you are... |
|---|---|
| in the dark (night, caves; read from the light level at your feet) | standing in bright light (light 12+) |
| deep underground | near another player |
| at 3 hearts or less | |
| being stared at by the Watcher | |

| Stage | Dread | What happens |
|---|---|---|
| 1 Unease | 150+ | The world makes sounds it shouldn't: footsteps that stop when you turn around, a door creaking that nobody opened, a creeper hiss with no creeper, whispers, a music box playing slowly out of tune somewhere in the dark |
| 2 Dread | 350+ | Breathing right behind you. Your vision dips to black for a moment. A column of smoke at the edge of your sight, gone when you look. Rifts rip open in the distance. **The Watcher appears**, 26-40 blocks away, in the dark, somewhere you are facing. A low drone starts under everything |
| 3 Terror | 650+ | **The Watcher hunts you.** Your torches start going out |

### The Watcher
A tall figure that stands in the dark. At night or in a cave you won't see its body at all, only **two violet eyes** staring at you.
Its rules are strict, and they are what make it frightening:

1. **It only hunts you.** It is bound to one player. Other players can see it, but it ignores them.
2. **It never moves while you look at it.** The moment you look away, it blinks closer through a tear in the world. You can always *hear* the tear, even when you can't see it.
3. **Looking at it costs you.** Eye contact feeds your Dread. After about 5 seconds your eyes give out (blindness + darkness) and it closes the distance while you can't see.
4. **It cannot exist in light.** It dissolves anywhere the light level is 8+, and it can't step into torchlight. A ring of torches is a shelter, with the thing standing right at the edge of it, watching. Place a torch next to it and it screams and vanishes. It comes back, faster.
5. **The strike.** When it gets right behind you it winds up for one second (you hear breathing). **Turn around and look at it in that second and it recoils.** Don't, and it hits you for 4 hearts, blinds you, and leaves. It remembers.
6. **It can't be killed.** Hitting it only makes it scream and stop waiting.

At terror it also snuffs out the torches around you, one at a time. They drop as items, so you can put them back, if you dare.

Dying wakes you up: your Dread resets to 0. Peaceful difficulty disables the Watcher itself.

## Building it

You need **JDK 21** and an internet connection (for Gradle to fetch Minecraft and Fabric).

```
./gradlew build          # Windows: gradlew.bat build
```

The mod jar is `build/libs/riftwatcher-1.0.0.jar`. Put it in your `mods` folder next to **Fabric API**.
To try it straight from the project: `./gradlew runClient`.

### No JDK? Let GitHub build the jar for you
The project includes a workflow (`.github/workflows/build.yml`). Pushing to the `riftwatcher` branch (or running it from the
**Actions** tab) builds the mod and publishes the finished `.jar` as a **Release** on the repo's page, with a direct download link.

### If the build complains
This project was written without being able to compile it against Minecraft, so the first build is where any small
mismatches will show up. The likely ones, in order:

1. **Version numbers** (`gradle.properties`): if Gradle can't resolve `loader_version`, `loom_version` or
   `fabric_api_version`, open <https://fabricmc.net/develop>, pick 1.21.11, and copy the numbers it shows.
2. **`Identifier` not found**: your mappings still call it `ResourceLocation`. Change the import and the type in just two
   files: `Ids.java` and `client/RiftWatcherRenderer.java`.
3. **Package or signature differences** in `EnderMan` / `EndermanRenderer`, `hurtServer`, `EntityType.create(level, reason)`
   or the `ClientboundSoundPacket` constructor. Each is used in exactly one or two places, so each fix is a line or two.
   (`Sfx.java` is the only file that builds sound packets; `Haunting.summon` is the only place a Watcher is created.)

If it turns into a fight, generate a blank 1.21.11 Fabric project at fabricmc.net/develop and copy this project's `src/` folder
over its `src/`. Only the source files matter.

## Trying it without waiting for the dark

In-game commands (on by default, switch off in the config on public servers). Use **survival mode**, since creative is ignored unless you enable `affectCreative`.

| Command | |
|---|---|
| `/riftwatcher status` | Your Dread and stage |
| `/riftwatcher dread 400` | Set Dread. 350 = it watches you, 650 = it hunts you |
| `/riftwatcher summon` | A rift opens close by; the Watcher hunts you. Ignores light, so it works in daylight |
| `/riftwatcher summon watch` | Same, but it only stands and stares |
| `/riftwatcher banish` | Removes every Watcher |

## Config (`config/riftwatcher.json`, created on first launch)

| Option | Default | |
|---|---|---|
| `enabled` | `true` | Master switch |
| `watcherEnabled` | `true` | `false` = ambient scares only, no Watcher |
| `affectCreative` | `false` | Haunt creative players too |
| `snuffTorches` | `true` | The Watcher puts out torches near you at terror |
| `debugCommands` | `true` | The `/riftwatcher` commands |
| `strikeDamage` | `8.0` | Damage from the strike (2 per heart) |
| `dreadGainMultiplier` | `1.0` | `0.5` = gentler, `2.0` = much worse |
| `stage1Dread` / `stage2Dread` / `stage3Dread` | 150 / 350 / 650 | Thresholds |

## What's in the box

- `RiftWatcherEntity`: the Watcher. It borrows the Enderman body, but all vanilla AI is removed and replaced by the rules above.
- `Haunting`: the director (Dread, ambient scares, spawning the Watcher, rift scars, torch snuffing).
- `assets/.../sounds`: eight sounds synthesized from scratch for this mod (heartbeat, drone, rift tear, stare, scream, whisper, breath, music box). Generated by script, so no third-party audio and no licensing issues.
- `assets/.../textures/entity/rift_watcher.png`: shattered-glass skin with glowing violet seams, on the vanilla Enderman UV layout.

Dread is kept in memory, not saved to disk: quitting the world resets it to calm.

## License
MIT
