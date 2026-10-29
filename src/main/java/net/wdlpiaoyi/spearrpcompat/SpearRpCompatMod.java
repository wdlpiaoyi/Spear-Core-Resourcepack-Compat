package net.wdlpiaoyi.spearrpcompat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.wdlpiaoyi.spearrpcompat.config.CompatConfig;
import org.slf4j.Logger;

/**
 * Spear Core 资源包兼容补丁。
 *
 * <p>本模组只做两件事，都是纯客户端：</p>
 * <ol>
 *     <li>把 1.21.9+ 的资源包（{@code pack.mcmeta} 只有 {@code min_format}/{@code max_format}）
 *     补成 1.20.1 能读的 {@code pack_format: 15}，让它出现在资源包列表里；</li>
 *     <li>把包内 {@code assets/minecraft/textures/item/*_spear*.png} 映射成
 *     {@code assets/spearcore/textures/item/...}，于是一份按原版命名写的长矛贴图包也能覆盖
 *     Spear Core 的七把矛。</li>
 * </ol>
 *
 * <p>{@code spearcore} 在 mods.toml 里是 mandatory 依赖：没有 Spear Core 时本模组不该存在。</p>
 */
@Mod(SpearRpCompatMod.MODID)
public final class SpearRpCompatMod {

    public static final String MODID = "spearrpcompat";

    /** 被兼容的命名空间：Spear Core 的物品与模型都在这里。 */
    public static final String SPEAR_CORE_NAMESPACE = "spearcore";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SpearRpCompatMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CompatConfig.SPEC);
    }
}
