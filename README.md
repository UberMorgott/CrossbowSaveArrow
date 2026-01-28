# CrossbowSaveArrow

Per-crossbow ammo storage for Hytale. Each crossbow remembers its loaded arrows independently.

## The Problem

In vanilla Hytale, **arrows are lost when you switch away from a crossbow**. If you loaded 4 arrows and switch to a sword, those arrows vanish. This mod fixes that.

## Features

- **Arrows persist on weapon switch** — the main feature! No more losing loaded ammo when switching weapons
- **Individual ammo tracking** — each crossbow stores its own arrow count in item metadata
- **Persistent storage** — ammo is saved when you:
  - Switch to another weapon
  - Drop the crossbow
  - Store it in a chest
  - Trade with other players
- **No arrow duplication** — arrows that would normally return to inventory on weapon switch are blocked
- **Multiplayer compatible** — works correctly when other players pick up your crossbows

## Technical Details

This mod uses an **event-based architecture** with Mixin injection rather than tick-based systems. This means:

- **Zero performance overhead** during normal gameplay
- Code only executes when weapon stats are recalculated (on weapon switch)
- No continuous polling or per-tick checks

## Requirements

- **Hyxin** — Mixin loader for Hytale
  - [CurseForge](https://www.curseforge.com/hytale/mods/hyxin)
  - [GitHub](https://github.com/Jenya705/Hyxin)

## Installation

### Single Player / Client-Hosted Server

1. Download `Hyxin.jar` and place it in:
   ```
   UserData/EarlyPlugins/
   ```

2. Download `CrossbowSaveArrow-1.0.0.jar` and place it in your **world save folder**:
   ```
   UserData/Saves/<YourWorldName>/earlyplugins
   ```
   (Create the `earlyplugins` file if it doesn't exist — it's a ZIP archive containing the jar)

3. Launch the game and load the world

> **Linux users:** The folder must be named exactly `earlyplugins` (lowercase). Linux is case-sensitive, so `EarlyPlugins` won't work.

### Dedicated Server

1. Download `Hyxin.jar` and place it in:
   ```
   <ServerRoot>/EarlyPlugins/
   ```

2. Download `CrossbowSaveArrow-1.0.0.jar` and place it in:
   ```
   <ServerRoot>/EarlyPlugins/
   ```

3. Start the server

## How It Works

When you switch away from a crossbow, the current ammo count is saved to the crossbow's item metadata. When you switch back (or pick up a crossbow from the ground), the ammo is restored from metadata.

This allows you to have multiple crossbows with different ammo counts — perfect for combat loadouts where you want pre-loaded crossbows ready to fire.

## Compatibility

- Works with vanilla crossbows and any modded crossbows that use the standard ammo stat system
- Compatible with other mods that don't modify crossbow ammo behavior

## Source Code

This mod is open source. Feel free to learn from or contribute to the codebase.

## Credits

- **Author:** Morgott
- **Mixin Framework:** Hyxin by Jenya705 — [CurseForge](https://www.curseforge.com/hytale/mods/hyxin) | [GitHub](https://github.com/Jenya705/Hyxin)
