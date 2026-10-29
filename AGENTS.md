# AGENTS.md

给在这个仓库里干活的 AI 代理（以及人类协作者）的约定。**动手前先读完本文件，提交前按第 6 节自检。**

## 1. 项目是什么

- **Spear Core Resourcepack Compat**：一个 Forge 1.20.1 小模组，让 **1.21.9+ 格式的长矛资源包**在 1.20.1 + [Spear Core](https://github.com/lyingice/Spear-Core) 上生效。
- `origin` = `github.com/wdlpiaoyi/Spear-Core-Resourcepack-Compat`。
- 它**不是**通用资源包转换器：只处理 `assets/minecraft/textures/item/*_spear(_in_hand).png` → `spearcore:textures/item/`，外加把包格式补成 15。
- 对 Spear Core 是**强依赖**（`mods.toml` 里 `mandatory`，版本 `[3.0.0,)`）。不要改成 soft dependency。

## 2. 原理（改代码前必须理解）

1. **注册阶段**：`pack/HighVersionPackFinder` 监听 `AddPackFindersEvent`（仅客户端），扫 `resourcepacks/`，只接管**含长矛贴图**的包；用 `Pack.readMetaAndCreate(...)` 手动注册一条 `<文件名> [Spear Core 贴图兼容]`。
2. **元数据伪造**：注册时传入的 `Pack.ResourcesSupplier` 返回 `CompatPackResources`，它对 `getRootResource("pack.mcmeta")` 返回**内存里补好 `pack_format: 15`** 的 JSON —— 磁盘上的包不动。
3. **读取代理**：`CompatPackResources` 在 `getResource`（先查真实 `spearcore:` 条目，没有再 remap 到 `minecraft:`）、`getNamespaces`（声明自己也有 `spearcore`）、`listResources`（用 `minecraft` 名空间枚举后以 `spearcore:` 再发一次）三处代理。
4. `listResources` 是**贴图集（TextureAtlas）唯一认的入口**，缺了它贴图不会被拼进 atlas —— 这是最容易踩的坑，别「优化」掉。
5. `FilePackResources` 在 `path` 为空时会拼出 `assets/<ns>//`，命中不了任何条目；`CompatPackResources.listResources` 因此对空 path 用 `textures` 前缀兜底。

## 3. 构建与测试（改完必须跑）

```bash
./gradlew build              # 产物 build/libs/spearcore-rpcompat-1.20.1-forge-x.y.z.jar
./gradlew runGameTestServer  # 集成测试
```

- 通过标准是日志出现 `All N required tests passed :)`（当前 4 条），失败是 `N required tests failed :(`。**以日志为准**，退出码不可靠。
- GameTest 方法**不会自动 succeed**，每个测试必须显式 `helper.succeed()`（漏了会报 `Didn't succeed or fail within 100 ticks`）。
- Gradle 需要写 `~/.gradle`，沙箱环境要放行，否则报 `Could not create parent directory for lock file`。
- 发布 jar 必须过 `reobfJar`（`jar` 已 `finalizedBy 'reobfJar'`），不要绕过。

### 开发环境的 Spear Core 依赖

`spearcore` 是强制依赖，开发环境里没有它游戏直接拒绝加载。`build.gradle` 用两条任务解决：

- `compileDevStub` + `stageSpearCoreStub`：编一个只有 `@Mod("spearcore")` 的占位 jar（`src/devstub/java` + `devstub/META-INF/mods.toml`）放进 `run/mods`；
- 如果 `run/mods/spearcore-dev.jar` 已存在（真实 Spear Core 的开发态构建），占位 jar 就跳过。

**不要把发布版（reobf/SRG 名）的 Spear Core jar 塞进 `run/mods`**：mojmap 开发环境里 mixin 目标全是 SRG 名，一启动就 `Critical injection failure`。要测真实行为，就在 Spear Core 仓库跑 `./gradlew classes processResources`，再手工 `jar` 一遍 `build/classes/java/main` + `build/resources/main` 成 `run/mods/spearcore-dev.jar`。

## 4. 代码约定

- Java 17、Forge 1.20.1、official mappings、4 空格、UTF-8、注释用中文。
- 不要升级 Forge / mappings / Gradle，不要加 MixinExtras 之类额外依赖（本模组不需要任何 mixin）。
- 所有 `PackResources` 实现必须委托到底层真实包：`close()`、`packId()` 等别自己造。
- 只处理 `textures/item/` 下的长矛 PNG。**不要**顺手去转换模型、语言文件或 `custom_model_data` —— 那是另一个量级的工程，且容易把原包改坏。

## 5. 资源与元数据约定

- 模组自身**必须**有 `src/main/resources/pack.mcmeta`（`pack_format: 15`）。缺了会在启动时弹「未能为文件 …/build/resources/main 加载有效的 ResourcePackInfo」。
- 日志里 `Missing metadata in pack file/<资源包>.zip` 是**原版**扫描高版本包失败留下的，属于预期，不要试图消除它（真正生效的是我们注册的那条）。
- 模块 id `spearrpcompat`、版本号在 `build.gradle` 与 `mods.toml` 两处，发版时同步改。

## 6. 自检清单（每次提交前）

1. `./gradlew build` 通过，产物带 `pack.mcmeta`。
2. `./gradlew runGameTestServer` 出现 `All N required tests passed :)`。
3. 改了包扫描/代理逻辑 → 有对应 GameTest（现有：meta 补丁、贴图识别、路径重映射、真实包端到端）。
4. 启动客户端没有 ResourcePackInfo 警告，资源包列表里兼容条目可启用、重载无缺失贴图报错。
5. `git status` 只含预期文件（`build/`、`run/` 已被忽略）。
6. 提交信息中文、一行摘要 + `-` 要点，说清「为什么」。

## 7. 不要做的事

- 不要修改/覆盖用户的资源包文件；一切补丁只在内存副本里。
- 不要把 `pack_format` 补成 15 以外的值。
- 不要为了「看起来兼容」把 Spear Core 改成 soft dependency 或放宽版本范围。
- 不要删除或弱化 GameTest 来让构建变绿。
- 不要提交 `build/`、`run/`、`.gradle/`、IDE 文件、本机绝对路径或密钥。
