package net.wdlpiaoyi.spearrpcompat.pack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.wdlpiaoyi.spearrpcompat.SpearRpCompatMod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 一个「包装」层：底层是原版读取器（zip 用 {@link FilePackResources}、目录用
 * {@link PathPackResources}），只额外做两件事。
 *
 * <ul>
 *     <li>{@code pack.mcmeta} 换成补过 {@code pack_format} 的版本（{@link #getRootResource}）；
 *     元数据读取走继承来的 {@link AbstractPackResources#getMetadataSection}，因此会自动读到补丁版。</li>
 *     <li>把 {@code minecraft:textures/item/*_spear*.png} 借给 {@code spearcore:} 命名空间：
 *     {@link #getResource} 做按需查询，{@link #listResources} 做枚举（贴图集靠枚举收集）。</li>
 * </ul>
 */
public final class CompatPackResources extends AbstractPackResources {

    private final PackResources delegate;
    private final byte[] patchedMeta;
    private final boolean hasSpearTextures;

    public CompatPackResources(String packId, Path path, byte[] patchedMeta, boolean hasSpearTextures) {
        super(packId, false);
        this.patchedMeta = patchedMeta;
        this.hasSpearTextures = hasSpearTextures;
        this.delegate = Files.isDirectory(path)
                ? new PathPackResources(packId, path, false)
                : new FilePackResources(packId, path.toFile(), false);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && PackResources.PACK_META.equals(elements[0])) {
            return () -> new ByteArrayInputStream(patchedMeta);
        }
        return delegate.getRootResource(elements);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES
                && SpearRpCompatMod.SPEAR_CORE_NAMESPACE.equals(location.getNamespace())
                && SpearTextureDetection.isSpearRelativePath(location.getPath())) {
            // 包自己提供的 spearcore 条目优先；没有才把 minecraft 的同名贴图借过来。
            IoSupplier<InputStream> own = delegate.getResource(type, location);
            if (own != null) {
                return own;
            }
            return delegate.getResource(type, remapToMinecraft(location));
        }
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        delegate.listResources(type, namespace, path, output);

        if (type != PackType.CLIENT_RESOURCES || !SpearRpCompatMod.SPEAR_CORE_NAMESPACE.equals(namespace)) {
            return;
        }
        // 用同样的 path 去 minecraft 命名空间枚举一遍，把长矛贴图以 spearcore 身份再发一次。
        // 贴图集（TextureAtlas）只认 listResources 的结果，少了这一步贴图不会被拼进去。
        //
        // 注意 FilePackResources 的 bug：path 为空时会拼成 "assets/<ns>//"，一条都匹配不到。
        // 游戏里查贴图总是带 "textures" 这样的前缀，这里额外兜底一次空 path 的情况。
        String mirrorPath = path.isEmpty() ? "textures" : path;
        delegate.listResources(type, "minecraft", mirrorPath, (location, supplier) -> {
            if (!SpearTextureDetection.isSpearRelativePath(location.getPath())) {
                return;
            }
            ResourceLocation remapped =
                    new ResourceLocation(SpearRpCompatMod.SPEAR_CORE_NAMESPACE, location.getPath());
            if (delegate.getResource(type, remapped) == null) {
                output.accept(remapped, supplier);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new HashSet<>(delegate.getNamespaces(type));
        if (type == PackType.CLIENT_RESOURCES && hasSpearTextures) {
            namespaces.add(SpearRpCompatMod.SPEAR_CORE_NAMESPACE);
        }
        return namespaces;
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static ResourceLocation remapToMinecraft(ResourceLocation location) {
        return new ResourceLocation(ResourceLocation.DEFAULT_NAMESPACE, location.getPath());
    }
}
