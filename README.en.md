# Spear Core Resourcepack Compat

> English ｜ [简体中文](README.md)

A small patch mod that makes **resource packs built for Minecraft 1.21.9+ spears** usable with **Spear Core** on 1.20.1.

## What it fixes

Three things stack up so that a modern spear resource pack does "nothing at all" on 1.20.1 + Spear Core:

1. **Pack format** — since 1.21.9, `pack.mcmeta` uses `min_format` / `max_format` and no longer writes `pack_format`. 1.20.1 cannot read a `PackMetadataSection` out of that, so the pack **never even shows up** in the resource pack list.
2. **Namespace** — vanilla 1.21.11 spear textures live at `assets/minecraft/textures/item/{wooden,stone,copper,iron,golden,diamond,netherite}_spear.png`, while Spear Core reads `assets/spearcore/textures/item/`. Even a loadable pack would have no texture to contribute.
3. **Format details** — 1.21.9+ packs usually also ship `_in_hand` models, `custom_model_data`, etc., which 1.20.1 does not understand, so "loaded but looks wrong" is the best case.

What this mod does: scan `resourcepacks/`, pick out packs that contain **vanilla-namespace spear textures**, register a copy of the pack whose `pack.mcmeta` gains `pack_format: 15`, and then lend `minecraft:textures/item/*_spear*.png` to the `spearcore:` namespace. The pack on disk is **never modified**.

## Requirements

- Minecraft 1.20.1 + Forge 47.x
- **Spear Core 3.0.0+ (hard dependency)** — this mod exists only to assist Spear Core; `mods.toml` declares it as `mandatory`.

## Installation

1. Drop `spearcore-rpcompat-1.20.1-forge-x.y.z.jar` into `mods/`.
2. Drop the modern spear resource pack (zip or folder) into `resourcepacks/`.
3. In game → Options → Resource Packs, an entry suffixed with **`[Spear Core 贴图兼容]`** appears in the list.
4. Enable it and confirm.

## What it can / cannot do

**Can:**

- make 1.21.9+ format packs appear in the 1.20.1 resource pack list;
- feed `assets/minecraft/textures/item/*_spear*.png` (including `_in_hand`) to Spear Core;
- prefer the pack's own `spearcore:` textures when it ships them.

**Cannot:**

- convert 3D/`in_hand` models, `custom_model_data`, item names / language files, sounds, or any other resources — only spear PNGs under `textures/item/` are remapped;
- patch `pack_format` to anything but 15: 1.20.1 only accepts 15 (plus a narrow compatible window), so 15 is the only value that gets the pack listed;
- guarantee that other 1.21-only resources in the pack (new blocks, item models, …) work on 1.20.1.

## Configuration

`config/spearrpcompat-common.toml`

| Key | Default | Description |
| --- | --- | --- |
| `compat.enabled` | `true` | When disabled, no packs are scanned or registered (restart required). |

## Building from source

```bash
./gradlew build              # output in build/libs/, already reobf'ed
./gradlew runGameTestServer  # integration tests, look for "All N required tests passed :)"
```

- Java 17, ForgeGradle `[6.0.16,6.2)`, official mappings, Forge `1.20.1-47.3.0`.
- Gradle needs to write `~/.gradle`; in a sandboxed environment allow it, otherwise you get `Could not create parent directory for lock file`.

## License

MIT, see [LICENSE](LICENSE).

---

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)
