# CrossbowSaveArrow

[![en](https://img.shields.io/badge/lang-English-blue)](README.md) [![ru](https://img.shields.io/badge/lang-Русский-green)](README.ru.md)

Per-crossbow ammo storage for Hytale. Each crossbow remembers its loaded arrows independently.

## The Problem

In vanilla Hytale, **arrows are lost when you switch away from a crossbow**. If you loaded 4 arrows and switch to a sword, those arrows vanish. This happens because ammo is stored as an entity stat (on the player), not on the item itself. When the weapon modifier is removed on switch, `clamp(ammo, 0, 0) = 0` — arrows gone forever.

## Features

- **Arrows persist on weapon switch** — each crossbow stores its own arrow count in ItemStack metadata
- **Deferred arrow consumption** — arrows are removed from inventory only AFTER ammo actually increases, preventing loss on interrupted reload
- **No arrow duplication** — vanilla SwapFrom arrow return is blocked
- **UUID tracking** — each crossbow gets a unique ID for reliable identification
- **Persistent storage** — ammo is saved when you: switch weapons, drop, store in chest, trade, relog
- **Zero performance overhead** — event-based architecture, no tick polling

## Architecture

4 Mixins injected via Hyxin:

| Mixin | Target | Method | Purpose |
|-------|--------|--------|---------|
| `StatModifiersManagerMixin` | `StatModifiersManager` | `@Inject RETURN recalculateEntityStatModifiers` | Populate entity map, assign UUID, restore ammo from metadata |
| `EntityStatMapMixin` | `EntityStatMap` | `@Overwrite addStatValue` | Deferred arrow removal after ammo increase, write metadata on any ammo change |
| `ModifyInventoryInteractionMixin` | `ModifyInventoryInteraction` | `@Redirect x2 firstRun` | Defer arrow removal to ThreadLocal, block arrow return on SwapFrom |
| `ItemStackMixin` | `ItemStack` | `@Overwrite isEquivalentType` | Ignore LoadedAmmo/CrossbowUUID in metadata comparison to prevent cancelOnItemChange |

Cross-classloader shared state via `System.getProperties()` (Mixin classloader cannot see non-mixin classes from the same JAR).

```
ItemStack metadata (source of truth)
+-- LoadedAmmo: float    -- current arrow count
+-- CrossbowUUID: string -- unique crossbow identifier
```

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

2. Download `CrossbowSaveArrow-x.x.x.jar` and place it in your **world save folder**:
   ```
   UserData/Saves/<YourWorldName>/earlyplugins
   ```
   (Create the `earlyplugins` file if it doesn't exist — it's a ZIP archive containing the jar)

3. Launch the game and load the world

> **Linux users:** The folder must be named exactly `earlyplugins` (lowercase). Linux is case-sensitive, so `EarlyPlugins` won't work.

### Dedicated Server

1. Download `Hyxin.jar` and place it in:
   ```
   <ServerRoot>/earlyplugins/
   ```

2. Download `CrossbowSaveArrow-x.x.x.jar` and place it in:
   ```
   <ServerRoot>/earlyplugins/
   ```

> **Linux users:** The folder must be named exactly `earlyplugins` (lowercase). Linux is case-sensitive.

3. Start the server

## Changelog

### v0.0.3

Universal weapon detection — no longer relies on item name.

**Changed:**
- All 4 mixins no longer check `itemId.contains("Crossbow")` to identify crossbows
- `EntityStatMapMixin` — writes `LoadedAmmo` metadata on any ammo stat change, regardless of item name
- `StatModifiersManagerMixin` — detects weapons by presence of `LoadedAmmo` metadata instead of name
- `ModifyInventoryInteractionMixin` — checks for `LoadedAmmo`/`CrossbowUUID` metadata on held item instead of name
- `ItemStackMixin` — checks for our metadata keys in BsonDocument instead of item name

**Fixed:**
- Ammo loss on swap with modded crossbows that don't have "Crossbow" in their item ID (e.g. MB crossbows with IDs like `MasBallestas_Hierro`)

### v0.0.2

Complete rewrite of the ammo storage system. 2 mixins -> 4 mixins.

**New:**
- `EntityStatMapMixin` — overwrites `addStatValue()` to consume arrows AFTER ammo increases (deferred removal). Prevents arrow loss on interrupted reload. Writes ammo to crossbow metadata on every change (reload/shot)
- `ItemStackMixin` — overwrites `isEquivalentType()` to ignore `LoadedAmmo` and `CrossbowUUID` metadata keys. Prevents `cancelOnItemChange` from breaking reload chains when metadata is updated
- UUID assigned to each crossbow for reliable tracking
- Hyxin detection check on startup with SEVERE error log if not found
- Cross-classloader state via `System.getProperties()` (ThreadLocal + WeakHashMap)

**Changed:**
- `StatModifiersManagerMixin` — removed HEAD inject and all `WeakHashMap` tracking (`lastCrossbowSlot`, `lastCrossbowAmmo`, `hasRestored`). Now only uses RETURN inject. Always restores ammo from metadata (not only when `currentAmmo == 0`)
- `ModifyInventoryInteractionMixin` — added second `@Redirect` for `removeItemStack` to defer arrow removal instead of letting vanilla remove arrows before ammo increases
- Removed all debug `LOGGER.info()` calls for production

**Fixed:**
- Arrow loss on interrupted reload (arrows were removed before ammo increased)
- Ammo not restoring when switching between two crossbows (old code required `currentAmmo == 0`)
- Ammo desync on relog (entity stat loaded from disk, not 0, restore skipped)
- Reload chain breaking when metadata was written to active hotbar slot (triggered `cancelOnItemChange`)

### v1.0.0

Initial release. 2 mixins: `StatModifiersManagerMixin` (HEAD+RETURN), `ModifyInventoryInteractionMixin` (1 redirect).

## Compatibility

- Works with vanilla crossbows and any modded crossbows that use the standard ammo stat system
- Compatible with other mods that don't modify crossbow ammo behavior

## Credits

- **Author:** Morgott
- **Mixin Framework:** Hyxin by Jenya705 — [CurseForge](https://www.curseforge.com/hytale/mods/hyxin) | [GitHub](https://github.com/Jenya705/Hyxin)
