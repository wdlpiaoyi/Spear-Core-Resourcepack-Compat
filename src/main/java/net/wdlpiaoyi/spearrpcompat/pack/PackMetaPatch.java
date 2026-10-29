package net.wdlpiaoyi.spearrpcompat.pack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;

/**
 * {@code pack.mcmeta} 补丁：让 1.21.9+ 的包能被 1.20.1 认出来。
 *
 * <p>1.21.9 起资源包元数据改用 {@code min_format} / {@code max_format}（且 {@code pack_format}
 * 变成可选），而 1.20.1 的 {@code PackMetadataSection} 是「{@code pack_format} 必填」的 codec，
 * 缺字段时反序列化直接失败，包不会出现在资源包列表里（表现为「MC 根本识别不出来」）。</p>
 *
 * <p>这里只改内存里的 JSON，不动原文件：把 {@code pack_format} 写成 1.20.1 的 15，其余字段
 * （{@code min_format}、{@code max_format}）原样保留——1.20.1 不认识它们，会忽略。</p>
 */
public final class PackMetaPatch {

    /** 1.20.1 客户端资源包的 pack_format。 */
    public static final int TARGET_PACK_FORMAT = 15;

    private static final Gson GSON = new Gson();

    private PackMetaPatch() {
    }

    /** 解析 {@code pack.mcmeta} 的文本；解析失败返回 {@code null}。 */
    public static JsonObject parse(byte[] metaBytes) {
        if (metaBytes == null || metaBytes.length == 0) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(metaBytes, StandardCharsets.UTF_8));
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * 是否需要补 {@code pack_format}：缺失、不是数字、或者与 1.20.1 的值不同都算需要。
     * 已经是 15 的包也会被补一遍（幂等），这样注册流程不需要分支。
     */
    public static boolean needsPatch(JsonObject root) {
        JsonElement pack = root == null ? null : root.get("pack");
        if (pack == null || !pack.isJsonObject()) {
            return true;
        }
        JsonElement format = pack.getAsJsonObject().get("pack_format");
        return format == null || !format.isJsonPrimitive()
                || !((JsonPrimitive) format).isNumber()
                || format.getAsInt() != TARGET_PACK_FORMAT;
    }

    /**
     * 生成补丁后的元数据字节。
     *
     * @param root   原始 {@code pack.mcmeta} JSON
     * @param suffix 追加到字符串型 description 末尾的后缀（用于在列表里区分兼容条目），可为空
     */
    public static byte[] patch(JsonObject root, String suffix) {
        JsonObject result = root == null ? new JsonObject() : root.deepCopy();

        JsonObject pack;
        JsonElement packElement = result.get("pack");
        if (packElement != null && packElement.isJsonObject()) {
            pack = packElement.getAsJsonObject();
        } else {
            pack = new JsonObject();
            result.add("pack", pack);
        }
        pack.addProperty("pack_format", TARGET_PACK_FORMAT);

        if (suffix != null && !suffix.isEmpty()) {
            JsonElement description = pack.get("description");
            if (description != null && description.isJsonPrimitive()
                    && description.getAsJsonPrimitive().isString()) {
                pack.addProperty("description", description.getAsString() + suffix);
            } else if (description == null) {
                pack.addProperty("description", suffix.trim());
            }
            // 非字符串 description（组件对象）保持原样：改它会破坏渐变/翻译，不值得。
        }

        return GSON.toJson(result).getBytes(StandardCharsets.UTF_8);
    }
}
