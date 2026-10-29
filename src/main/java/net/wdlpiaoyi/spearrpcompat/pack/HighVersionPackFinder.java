package net.wdlpiaoyi.spearrpcompat.pack;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.api.distmarker.Dist;
import net.wdlpiaoyi.spearrpcompat.SpearRpCompatMod;
import net.wdlpiaoyi.spearrpcompat.config.CompatConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 客户端资源包列表的「多出来一个来源」。
 *
 * <p>原版只会从 {@code resourcepacks/} 里读 {@code pack.mcmeta} 合格的包；1.21.9+ 的包缺
 * {@code pack_format}，在 1.20.1 里读不出 {@code PackMetadataSection}，于是列表里根本不出现。
 * 这里扫描同一个目录，自己读出条目名与 {@code pack.mcmeta}，筛出「包含原版命名空间长矛贴图」
 * 的包，再用 {@link PackMetaPatch} 补一份元数据、用 {@link CompatPackResources} 包一层注册进去。</p>
 *
 * <p>只处理 {@link PackType#CLIENT_RESOURCES}；专用服务器不加载客户端资源，不会走到这里。</p>
 */
@Mod.EventBusSubscriber(modid = SpearRpCompatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HighVersionPackFinder {

    /** 列表里用来和其他来源的资源包区分。 */
    public static final String TITLE_SUFFIX = " [Spear Core 贴图兼容]";

    private static final String PACK_META = "pack.mcmeta";

    private HighVersionPackFinder() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !CompatConfig.enabled()) {
            return;
        }
        event.addRepositorySource(HighVersionPackFinder::loadPacks);
    }

    private static void loadPacks(Consumer<Pack> consumer) {
        Path root = FMLPaths.GAMEDIR.get().resolve("resourcepacks");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(HighVersionPackFinder::looksLikePack)
                    .sorted()
                    .forEach(path -> tryRegister(path, consumer));
        } catch (IOException e) {
            SpearRpCompatMod.LOGGER.warn("扫描资源包目录失败：{}", root, e);
        }
    }

    private static boolean looksLikePack(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith(".") || name.endsWith(".disabled")) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static void tryRegister(Path path, Consumer<Pack> consumer) {
        Candidate candidate = readCandidate(path);
        if (candidate == null) {
            return;
        }
        String fileName = path.getFileName().toString();
        byte[] patchedMeta = PackMetaPatch.patch(candidate.meta(), TITLE_SUFFIX);
        String id = "spearrpcompat/" + fileName;

        Pack pack = Pack.readMetaAndCreate(
                id,
                Component.literal(fileName + TITLE_SUFFIX),
                false,
                packId -> new CompatPackResources(packId, path, patchedMeta, true),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.DEFAULT);

        if (pack == null) {
            SpearRpCompatMod.LOGGER.warn("资源包元数据补丁后仍无法注册，跳过：{}", fileName);
            return;
        }
        SpearRpCompatMod.LOGGER.info("已把高版本长矛资源包注册为兼容条目：{}（pack_format → {}）",
                fileName, PackMetaPatch.TARGET_PACK_FORMAT);
        consumer.accept(pack);
    }

    /** 读出条目名与 pack.mcmeta；不合格（不是资源包 / 没有长矛贴图）返回 null。 */
    private static Candidate readCandidate(Path path) {
        try {
            List<String> entries = new ArrayList<>();
            byte[] meta;
            if (Files.isDirectory(path)) {
                Path base = path;
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(Files::isRegularFile).forEach(file ->
                            entries.add(base.relativize(file).toString().replace('\\', '/')));
                }
                Path metaPath = path.resolve(PACK_META);
                meta = Files.isRegularFile(metaPath) ? Files.readAllBytes(metaPath) : null;
            } else {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    Enumeration<? extends ZipEntry> enumeration = zip.entries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry entry = enumeration.nextElement();
                        if (!entry.isDirectory()) {
                            entries.add(entry.getName().replace('\\', '/'));
                        }
                    }
                    ZipEntry metaEntry = zip.getEntry(PACK_META);
                    meta = metaEntry == null ? null : readAll(zip.getInputStream(metaEntry));
                }
            }

            if (meta == null || !SpearTextureDetection.isSpearPack(entries)) {
                return null;
            }
            JsonObject json = PackMetaPatch.parse(meta);
            if (json == null) {
                return null;
            }
            return new Candidate(json, entries.size());
        } catch (IOException e) {
            SpearRpCompatMod.LOGGER.debug("读取资源包失败，跳过：{}", path, e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            return stream.readAllBytes();
        }
    }

    private record Candidate(JsonObject meta, int entryCount) {
    }
}
