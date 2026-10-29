package net.wdlpiaoyi.spearrpcompat.pack;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 长矛贴图识别与路径换算。全部是纯函数，GameTest 直接测这些。
 *
 * <p>原版 1.21.11 把长矛贴图放在 {@code assets/minecraft/textures/item/<材质>_spear.png}
 * 与 {@code ..._spear_in_hand.png}；Spear Core 的物品在 {@code spearcore:} 命名空间，
 * 模型引用 {@code spearcore:item/<材质>_spear}，于是贴图落在
 * {@code assets/spearcore/textures/item/...}。把前者映射成后者，资源包就能生效。</p>
 */
public final class SpearTextureDetection {

    /** 原版物品贴图目录（相对资源包根）。 */
    public static final String MINECRAFT_ITEM_TEXTURE_DIR = "assets/minecraft/textures/item/";

    /** 相对 {@code assets/<ns>/} 的贴图目录，用于判定 remap 目标是不是长矛贴图。 */
    public static final String ITEM_TEXTURE_DIR = "textures/item/";

    private static final Pattern SPEAR_FILE_NAME =
            Pattern.compile("^[a-z0-9_]+_spear(?:_in_hand)?\\.png$");

    private SpearTextureDetection() {
    }

    /** 资源包条目是否为「原版命名空间下的长矛贴图」。 */
    public static boolean isSpearEntry(String entryPath) {
        if (entryPath == null || !entryPath.startsWith(MINECRAFT_ITEM_TEXTURE_DIR)) {
            return false;
        }
        return SPEAR_FILE_NAME.matcher(entryPath.substring(MINECRAFT_ITEM_TEXTURE_DIR.length()))
                .matches();
    }

    /** 这个资源包是否值得接管：条目里至少有一张原版命名空间的长矛贴图。 */
    public static boolean isSpearPack(Collection<String> entryPaths) {
        if (entryPaths == null || entryPaths.isEmpty()) {
            return false;
        }
        for (String entry : entryPaths) {
            if (isSpearEntry(entry)) {
                return true;
            }
        }
        return false;
    }

    /** {@code assets/minecraft/textures/item/iron_spear.png} → {@code textures/item/iron_spear.png}。 */
    public static String toSpearCorePath(String minecraftEntryPath) {
        String prefix = MINECRAFT_ITEM_TEXTURE_DIR.substring(0, MINECRAFT_ITEM_TEXTURE_DIR.length() - ITEM_TEXTURE_DIR.length());
        return minecraftEntryPath.substring(prefix.length());
    }

    /** 相对资源路径（{@code textures/item/x.png}）是否长矛贴图。 */
    public static boolean isSpearRelativePath(String relativePath) {
        return relativePath != null && relativePath.startsWith(ITEM_TEXTURE_DIR)
                && SPEAR_FILE_NAME.matcher(relativePath.substring(ITEM_TEXTURE_DIR.length())).matches();
    }

    /** 展示用：{@code textures/item/iron_spear.png} → {@code iron_spear.png}。 */
    public static String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }
}
