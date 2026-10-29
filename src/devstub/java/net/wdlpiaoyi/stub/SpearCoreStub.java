package net.wdlpiaoyi.stub;

import net.minecraftforge.fml.common.Mod;

/**
 * 只用于开发/GameTest 的占位模组：让 FML 认为 {@code spearcore} 已安装，
 * 从而满足本模组在 mods.toml 里声明的强制依赖。绝不会打进发布 jar。
 */
@Mod(SpearCoreStub.MODID)
public final class SpearCoreStub {

    public static final String MODID = "spearcore";

    public SpearCoreStub() {
    }
}
