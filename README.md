# Framebreaker

Framebreaker is a ZenithProxy plugin for targeting and breaking map item frames around spawn.

It supports:
- priority-based targeting
- pathfinding and stuck recovery
- tracking broken and unreachable frames
- filtering by `map_id`
- filtering by renamed map item text such as Discord URLs or shop names

## Main Commands

These are the commands most people will actually use:

- `frames`
- `framestats`
- `framebreaker filter show`
- `framebreaker filter name list`
- `framebreaker filter name add "discord.gg"`
- `framebreaker filter name remove "discord.gg"`
- `framebreaker filter name toggle on`
- `framebreaker target info`

## Full Command List

### Stats and Tracking

- `frames`
- `framestats`
- `framebreaker`
- `fb`
- `framebreaker stats`
- `framebreaker list`
- `framebreaker list all`
- `framebreaker clear confirm`

### Filter Overview

- `framebreaker filter`
- `framebreaker filter list`
- `framebreaker filter show`
- `framebreaker filter reload`
- `framebreaker filter reset`

### Name Filter Commands

Use these for renamed maps such as Discord ads, store names, and similar text.

- `framebreaker filter name list`
- `framebreaker filter name add "discord.gg"`
- `framebreaker filter name remove "discord.gg"`
- `framebreaker filter name clear`
- `framebreaker filter name toggle on`
- `framebreaker filter name toggle off`
- `framebreaker filter name mode off`
- `framebreaker filter name mode allowlist`
- `framebreaker filter name mode blocklist`
- `framebreaker filter name case on`
- `framebreaker filter name case off`
- `framebreaker filter name partial on`
- `framebreaker filter name partial off`

### Map ID Filter Commands

Use these if you know exact map IDs you want to allow or block.

- `framebreaker filter mapid list`
- `framebreaker filter mapid add 1234`
- `framebreaker filter mapid remove 1234`
- `framebreaker filter mapid clear`
- `framebreaker filter mapid toggle on`
- `framebreaker filter mapid toggle off`
- `framebreaker filter mapid mode off`
- `framebreaker filter mapid mode allowlist`
- `framebreaker filter mapid mode blocklist`

### Target Information

- `framebreaker target info`

This shows:
- current target location
- distance
- detected `map_id`
- detected renamed map text
- whether the current filters matched

### Help

- `framebreaker help`

## Recommended Setup

If your main goal is to target renamed advertising maps, use the name filter:

```text
framebreaker filter name add "discord.gg"
framebreaker filter name add "discord.com"
framebreaker filter name add "kit shop"
framebreaker filter name toggle on
framebreaker filter name mode allowlist
```

If you want to block known map IDs instead:

```text
framebreaker filter mapid add 1234
framebreaker filter mapid toggle on
framebreaker filter mapid mode blocklist
```

## Installation

1. Download the latest release JAR from GitHub Releases.
2. Put the JAR into your ZenithProxy `plugins/` folder.
3. Restart ZenithProxy.

## Build

```powershell
.\gradlew.bat clean build
```

The built plugin JAR will be in:

```text
build/libs/Framebreaker-1.0.0.jar
```

## Automated Releases

This repository includes GitHub Actions release automation.

To publish a release:

1. Update `plugin_version` in `gradle.properties`
2. Commit and push
3. Create and push a version tag, for example:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

That will build the plugin and upload the JAR to GitHub Releases automatically.
