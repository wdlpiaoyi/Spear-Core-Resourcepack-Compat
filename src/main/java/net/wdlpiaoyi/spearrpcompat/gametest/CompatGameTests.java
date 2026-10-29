package net.wdlpiaoyi.spearrpcompat.gametest;

import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.wdlpiaoyi.spearrpcompat.SpearRpCompatMod;
import net.wdlpiaoyi.spearrpcompat.pack.CompatPackResources;
import net.wdlpiaoyi.spearrpcompat.pack.PackMetaPatch;
import net.wdlpiaoyi.spearrpcompat.pack.SpearTextureDetection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 纯逻辑单测（借助 GameTest 跑在服务端）：元数据补丁、贴图识别、路径换算，以及用真实高版本包
 * 跑一遍包装层。
 *
 * <p>判定以日志的 "All N required tests passed :)" / "N required tests failed :(" 为准。</p>
 */
@Mod.EventBusSubscriber(modid = SpearRpCompatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatGameTests {

    private static final String TEMPLATE = "empty5x5x5";

    private CompatGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(CompatGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearRpCompatMod.MODID, template = TEMPLATE, batch = "spearrpcompat")
    public static void packMetaIsPatched(GameTestHelper helper) {
        JsonObject vanilla = PackMetaPatch.parse(
                ("{\"pack\":{\"description\":\"Spears Overhaul\",\"min_format\":79,\"max_format\":100}}")
                        .getBytes(StandardCharsets.UTF_8));
        helper.assertTrue(vanilla != null, "1.21.9+ 的 pack.mcmeta 应该能解析");
        helper.assertTrue(PackMetaPatch.needsPatch(vanilla), "缺少 pack_format 时应判定为需要补丁");

        JsonObject patched = PackMetaPatch.parse(PackMetaPatch.patch(vanilla, " [兼容]"));
        helper.assertTrue(patched != null, "补丁后的元数据应该能解析");
        JsonObject pack = patched.getAsJsonObject("pack");
        helper.assertTrue(pack.get("pack_format").getAsInt() == PackMetaPatch.TARGET_PACK_FORMAT,
                "pack_format 应被补成 15，实际为 " + pack.get("pack_format"));
        helper.assertTrue(pack.get("description").getAsString().endsWith("[兼容]"),
                "字符串 description 应追加后缀");
        helper.assertTrue(pack.has("min_format") && pack.has("max_format"),
                "min_format / max_format 应原样保留");

        JsonObject wrongFormat = PackMetaPatch.parse(
                "{\"pack\":{\"description\":\"old\",\"pack_format\":20}}".getBytes(StandardCharsets.UTF_8));
        helper.assertTrue(wrongFormat != null, "旧格式元数据应该能解析");
        helper.assertTrue(PackMetaPatch.needsPatch(wrongFormat), "pack_format 20 也应被统一成 15");
        JsonObject normalized = PackMetaPatch.parse(PackMetaPatch.patch(wrongFormat, ""));
        helper.assertTrue(normalized.getAsJsonObject("pack").get("pack_format").getAsInt() == 15,
                "pack_format 20 应被改成 15");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearRpCompatMod.MODID, template = TEMPLATE, batch = "spearrpcompat")
    public static void spearTextureDetection(GameTestHelper helper) {
        helper.assertTrue(SpearTextureDetection.isSpearEntry(
                "assets/minecraft/textures/item/iron_spear.png"), "iron_spear.png 应被识别");
        helper.assertTrue(SpearTextureDetection.isSpearEntry(
                "assets/minecraft/textures/item/wooden_spear_in_hand.png"), "wooden_spear_in_hand.png 应被识别");
        helper.assertTrue(!SpearTextureDetection.isSpearEntry(
                "assets/minecraft/textures/item/apple.png"), "普通物品贴图不应被识别");
        helper.assertTrue(!SpearTextureDetection.isSpearEntry(
                "assets/minecraft/sounds/item/spear.ogg"), "非 textures/item 路径不应被识别");
        helper.assertTrue(!SpearTextureDetection.isSpearEntry(
                "assets/spearcore/textures/item/iron_spear.png"), "已经属于 spearcore 的条目不应重复接管");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearRpCompatMod.MODID, template = TEMPLATE, batch = "spearrpcompat")
    public static void spearTexturePathRemap(GameTestHelper helper) {
        String minecraftPath = "assets/minecraft/textures/item/golden_spear_in_hand.png";
        String expected = "textures/item/golden_spear_in_hand.png";
        helper.assertTrue(expected.equals(SpearTextureDetection.toSpearCorePath(minecraftPath)),
                "remap 结果应为 " + expected + "，实际为 " + SpearTextureDetection.toSpearCorePath(minecraftPath));
        helper.assertTrue(SpearTextureDetection.isSpearRelativePath(expected),
                "相对路径应被识别为长矛贴图");
        helper.assertTrue("golden_spear_in_hand.png".equals(SpearTextureDetection.fileName(expected)),
                "文件名提取应为 golden_spear_in_hand.png");
        helper.succeed();
    }

    /**
     * 拿 run/resourcepacks 里的真实高版本包跑一遍包装层：能读到补过格式的元数据、
     * 能把 minecraft 命名空间的长矛贴图借给 spearcore。
     *
     * <p>目录里没有合适的包时直接跳过（保证在没有测试资源的环境里也能跑绿）。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearRpCompatMod.MODID, template = TEMPLATE, batch = "spearrpcompat")
    public static void realPackIsRemapped(GameTestHelper helper) throws IOException {
        Path pack = findSpearPackInResourcepacks();
        if (pack == null) {
            SpearRpCompatMod.LOGGER.info("run/resourcepacks 里没有高版本长矛包，跳过 realPackIsRemapped");
            helper.succeed();
            return;
        }

        JsonObject json = PackMetaPatch.parse(readPackMeta(pack));
        helper.assertTrue(json != null, "测试用资源包的 pack.mcmeta 应该能解析：" + pack.getFileName());
        byte[] patched = PackMetaPatch.patch(json, " [兼容]");

        CompatPackResources resources = new CompatPackResources(
                "spearrpcompat/gametest", pack, patched, true);
        try {
            helper.assertTrue(resources.getRootResource("pack.mcmeta") != null,
                    "包装层应返回补丁后的 pack.mcmeta");
            ResourceLocation ironIcon =
                    new ResourceLocation(SpearRpCompatMod.SPEAR_CORE_NAMESPACE, "textures/item/iron_spear.png");
            helper.assertTrue(resources.getResource(PackType.CLIENT_RESOURCES, ironIcon) != null,
                    "minecraft:iron_spear.png 应被借到 spearcore: 命名空间");
            helper.assertTrue(resources.getNamespaces(PackType.CLIENT_RESOURCES)
                            .contains(SpearRpCompatMod.SPEAR_CORE_NAMESPACE),
                    "包装层应对外声明 spearcore 命名空间");

            List<ResourceLocation> listed = new ArrayList<>();
            resources.listResources(PackType.CLIENT_RESOURCES, SpearRpCompatMod.SPEAR_CORE_NAMESPACE, "textures",
                    (location, supplier) -> listed.add(location));
            helper.assertTrue(listed.stream().anyMatch(location ->
                            SpearTextureDetection.isSpearRelativePath(location.getPath())),
                    "listResources(path=textures) 应枚列出 spearcore 命名空间下的长矛贴图，实际："
                            + listed.size() + " 条");

            List<ResourceLocation> listedAll = new ArrayList<>();
            resources.listResources(PackType.CLIENT_RESOURCES, SpearRpCompatMod.SPEAR_CORE_NAMESPACE, "",
                    (location, supplier) -> listedAll.add(location));
            helper.assertTrue(listedAll.stream().anyMatch(location ->
                            SpearTextureDetection.isSpearRelativePath(location.getPath())),
                    "listResources(path 为空) 也应兜底枚列出长矛贴图，实际：" + listedAll.size() + " 条");
        } finally {
            resources.close();
        }
        helper.succeed();
    }

    private static Path findSpearPackInResourcepacks() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve("resourcepacks");
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .filter(CompatGameTests::containsSpearTexture)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean containsSpearTexture(Path zip) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return file.stream().map(ZipEntry::getName).anyMatch(SpearTextureDetection::isSpearEntry);
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] readPackMeta(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry entry = file.getEntry("pack.mcmeta");
            if (entry == null) {
                throw new IOException("缺少 pack.mcmeta：" + zip);
            }
            try (InputStream in = file.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }
}
