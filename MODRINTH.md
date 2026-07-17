# CrossAnywhere

CrossAnywhere is a server-side teleportation and waypoint mod for Fabric. It provides personal and shared waypoints, TPA requests, safe random teleportation, `/back`, configurable costs and cooldowns, and bilingual messages without requiring a client-side installation.

## Highlights

- Personal and global waypoints with descriptions and facing direction
- Clickable waypoint lists with teleport, delete, and edit actions
- TPA and TPAHere requests with accept, deny, cancellation, and timeouts
- Per-player direct-teleport allowlists
- `/back` support and optional death-location recording
- Safe random teleport through `/rtp`, `/tpr`, and `/r`
- World allowlists and cross-dimension permission controls
- Separate cooldowns for waypoints, player teleportation, `/back`, and random teleportation
- Optional experience and item costs, including Custom Model Data matching
- Unsafe-location confirmation or nearby safe-location search
- English and Simplified Chinese messages
- Compatible waypoint and allowlist data formats for migration from the Paper edition
- MCDR STP JSON import

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.154.2+26.2 or a newer compatible build
- Java 25

CrossAnywhere runs entirely on the server. Players can join with an unmodified client.

## Installation

1. Install Fabric Loader and Fabric API on the server.
2. Place the CrossAnywhere JAR in the `mods` directory.
3. Start the server to generate files under `config/crossanywherefabric/`.
4. Adjust `config.yml` and the message files as needed.

Configuration and language files can be reloaded with `/ca reload`.

## Random Teleport Performance

Random teleport is designed to avoid large synchronous chunk-generation spikes:

- Candidate chunks are generated or loaded one at a time through Minecraft's asynchronous chunk future.
- The chunk heightmap is used to find the surface without scanning the entire world height.
- Duplicate chunks and positions outside the world border are rejected before loading.
- Each player can have only one active search.
- Search attempts are capped and configurable.
- Permission and cooldown checks happen before any candidate chunk is requested.

Default settings search between 500 and 5,000 blocks from the player's current position, with at most eight candidate chunks per request.

## Commands

```text
/ca setp|setg [-f] <name> [description...]
/ca tpp|tpg <name>
/ca delp|delg <name>
/ca list|listp|listg
/ca descp|descg <name> <description...>
/ca tp|tphere <player>
/ca tpa|tpahere <player>
/ca accept|deny [player]
/ca tpaallow|tpadisallow <player>
/ca tpaallowlist
/ca back
/rtp | /tpr | /r
/ca confirm|cancelconfirm
/ca reload
/ca importstp [file] [--include-back] [--offline-uuid|--raw-uuid|--auto-uuid] [--clear]
```

`/stp` is an alias of `/ca`. Shortcut commands from the original Paper plugin are also available.

## Permissions

CrossAnywhere uses Fabric Permission API identifiers, including:

```text
crossanywhere:admin
crossanywhere:personal
crossanywhere:personal/tp
crossanywhere:global
crossanywhere:global/tp
crossanywhere:list
crossanywhere:tp
crossanywhere:tphere
crossanywhere:tpa
crossanywhere:tpahere
crossanywhere:tpa/allowlist
crossanywhere:back
crossanywhere:rtp
crossanywhere:crossworld
crossanywhere:cooldown/bypass
crossanywhere:cost/bypass
crossanywhere:safety/bypass
```

Without a permission-management mod, normal player features are enabled by default. Administrative actions, direct teleportation, cross-dimension access, and bypass permissions fall back to Minecraft's administrator permission level.

## Migrating From Paper

Copy the following files from `plugins/CrossAnywhere/` to `config/crossanywherefabric/`:

- `personal_waypoints.json`
- `global_waypoints.json`
- `tpa_allowlist.json`
- Optional `stp_uuid_map.json` and `stp_world_map.json`

Paper world names such as `world`, `world_nether`, and `world_the_end` are mapped to their vanilla Fabric dimensions automatically.
