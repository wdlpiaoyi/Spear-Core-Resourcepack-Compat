package net.wdlpiaoyi.spearrpcompat.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 只有一个开关：整体启用/停用兼容层。
 *
 * <p>关掉之后高版本长矛资源包不会再被注册（列表里看不到），已经启用的也会随资源重载消失。
 * 配置读取统一走 {@link #enabled()}，因为配置尚未加载时 {@code get()} 会抛
 * {@link IllegalStateException}，这里回退到默认值。</p>
 */
public final class CompatConfig {

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Spear Core 资源包兼容").push("compat");
        ENABLED = builder
                .comment("是否启用高版本长矛资源包兼容。",
                        "启用后：含 min_format/max_format 的资源包会被补成 pack_format 15 出现在列表里，",
                        "且包内 minecraft 命名空间的 *_spear 贴图会映射到 spearcore 命名空间。",
                        "默认 true。")
                .define("enabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private CompatConfig() {
    }

    /** 配置未加载时按默认值（启用）处理。 */
    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
