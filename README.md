# Spear Core Resourcepack Compat

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

> 简体中文（默认） ｜ [English](README.en.md)

让 **1.21.9+ 的高版本长矛资源包** 能在 1.20.1 的 **Spear Core** 上用起来的小补丁模组。

## 它解决什么问题

三件事叠在一起，导致高版本资源包在 1.20.1 + Spear Core 里「完全没反应」：

1. **包格式**：1.21.9 起 `pack.mcmeta` 用 `min_format` / `max_format`，不再写 `pack_format`。1.20.1 读不出 `PackMetadataSection`，这个包在资源包列表里**根本不出现**。
2. **命名空间**：1.21.11 原版长矛的贴图在 `assets/minecraft/textures/item/{wooden,stone,copper,iron,golden,diamond,netherite}_spear.png`，而 Spear Core 从 `assets/spearcore/textures/item/` 读 —— 就算包能加载，也找不到贴图。
3. **格式细节**：1.21.9+ 的包还常带 `_in_hand` 模型、`custom_model_data` 等 1.20.1 不认识的资源，于是就算包能加载，模型/贴图也不会回退成能用的样子。

本模组做的事：扫描 `resourcepacks/`，找出**含原版命名空间长矛贴图**的包，给它的 `pack.mcmeta` 补一份 `pack_format: 15` 的副本注册进列表，再把 `minecraft:textures/item/*_spear*.png` 借给 `spearcore:` 命名空间。资源包本身**不改动**。

## 依赖

- Minecraft 1.20.1 + Forge 47.x
- **Spear Core 3.0.0+（强制依赖）**：本模组就是给 Spear Core 打辅助的，没装它没有意义，`mods.toml` 里声明为 `mandatory`。

## 安装

1. 把 `spearcore-rpcompat-1.20.1-forge-x.y.z.jar` 丢进 `mods/`。
2. 把高版本长矛资源包（zip 或文件夹）丢进 `resourcepacks/`。
3. 进游戏 → 选项 → 资源包，列表里会出现一个带 **`[Spear Core 贴图兼容]`** 后缀的条目。
4. 启用它并确认。

## 能修 / 不能修

**能：**

- 1.21.9+ 格式的包在 1.20.1 里出现在资源包列表；
- 包内 `assets/minecraft/textures/item/*_spear*.png`（含 `_in_hand`）被 Spear Core 正确读取；
- 包自带 `spearcore:` 命名空间贴图时优先用包自己的。

**不能：**

- 不转换 3D/`in_hand` 模型、`custom_model_data`、物品名/语言文件、音效等其它资源；只重映射 `textures/item/` 下的长矛 PNG。
- 不会把 `pack_format` 补成 15 之外的版本：1.20.1 只认 15 与 15 上下的少量兼容值，补成 15 是唯一能让它进列表的做法。
- 不保证包里 1.21 专属的资源（例如新方块/新物品模型）在 1.20.1 生效。

## 配置

`config/spearrpcompat-common.toml`

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `compat.enabled` | `true` | 关掉后不再扫描/注册任何兼容条目（改完需重启游戏）。 |

## 从源码构建

```bash
./gradlew build              # 产物在 build/libs/，已过 reobfJar
./gradlew runGameTestServer  # 集成测试，看日志 "All N required tests passed :)"
```

- Java 17、ForgeGradle `[6.0.16,6.2)`、official mappings、Forge `1.20.1-47.3.0`。
- Gradle 需要写 `~/.gradle`；沙箱环境请放行，否则报 `Could not create parent directory for lock file`。

## 许可

MIT，见 [LICENSE](LICENSE)。

