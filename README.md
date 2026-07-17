# CrossAnywhere Fabric

[Chinese](README_zh.md)

CrossAnywhere is a server-side teleportation mod for Minecraft 26.2. It ports the original Paper plugin to Fabric while preserving its waypoint data format and command behavior.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.154.2+26.2 or newer compatible build
- Java 25

Players do not need to install the mod on their clients.

## Features

- Personal and global waypoints with position, rotation, and descriptions
- TPA and TPAHere requests with accept, deny, cancellation, timeout, and duplicate policies
- Direct-teleport allowlists
- `/back` support, including optional death-location recording
- World allowlists and cross-dimension permission checks
- Separate cooldowns for waypoints, player teleportation, `/back`, and random teleportation
- Experience and item costs with Custom Model Data support
- Unsafe-location confirmation and nearby safe-location search
- Random teleportation through `/rtp`, `/tpr`, or `/r`
- Sequential asynchronous chunk loading for low-impact random teleport searches
- English and Simplified Chinese messages with clickable waypoint and TPA actions
- MCDR STP JSON import
- `/ca`, `/stp`, and the shortcut commands provided by the original Paper plugin

## Building

```powershell
.\gradlew.bat build
```

The compiled JAR is written to `build/libs/`.

## Installation

1. Install Fabric Loader and Fabric API on a Minecraft 26.2 server.
2. Place the CrossAnywhere JAR in the server's `mods/` directory.
3. Start the server once to generate the configuration files.
4. Edit files under `config/crossanywherefabric/` as needed, then run `/ca reload` or restart the server.

## Data Directory

CrossAnywhere creates the following files:

```text
config/crossanywherefabric/
  config.yml
  messages_en_US.yml
  messages_zh_CN.yml
  personal_waypoints.json
  global_waypoints.json
  tpa_allowlist.json
```

The waypoint and TPA allowlist JSON formats remain compatible with the Paper edition. To migrate an existing server, copy these files from `plugins/CrossAnywhere/` to `config/crossanywherefabric/`:

- `personal_waypoints.json`
- `global_waypoints.json`
- `tpa_allowlist.json`
- `stp_uuid_map.json` and `stp_world_map.json` when using the STP importer

The default configuration recognizes the Paper world names `world`, `world_nether`, and `world_the_end` and maps them to their corresponding vanilla Fabric dimension IDs.

## Random Teleport

Random teleport uses the player's current position as its center. It samples candidates uniformly by area between the configured minimum and maximum radii.

```yaml
cooldown:
  rtp: 60

random_teleport:
  enabled: true
  min_radius: 500
  max_radius: 5000
  max_attempts: 8
```

Candidate chunks are loaded one at a time through Minecraft's asynchronous chunk future. CrossAnywhere uses the chunk heightmap instead of scanning the full world height, rejects duplicate and out-of-border candidates before loading them, and prevents each player from starting overlapping searches.

## Permissions

The Fabric edition uses Fabric Permission API identifiers. A Paper permission such as `crossanywhere.personal.tp` becomes `crossanywhere:personal/tp`:

```text
crossanywhere.admin                 -> crossanywhere:admin
crossanywhere.personal.tp           -> crossanywhere:personal/tp
crossanywhere.tpa.allowlist         -> crossanywhere:tpa/allowlist
crossanywhere.cooldown.bypass       -> crossanywhere:cooldown/bypass
crossanywhere.rtp                   -> crossanywhere:rtp
```

Without a permission-management mod, personal waypoints, waypoint listing, TPA, TPA allowlists, `/back`, and random teleport are available to players by default. Global waypoint management, direct `/tp`, cross-dimension teleportation, and bypass permissions require an administrator permission level by default.

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
