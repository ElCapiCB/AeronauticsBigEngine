package dev.elcapi.bigengine.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

final class NbtUtil {
    private NbtUtil() {}

    static void putPositions(CompoundTag tag, String key, List<BlockPos> list) {
        long[] arr = new long[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i).asLong();
        tag.put(key, new LongArrayTag(arr));
    }

    static List<BlockPos> getPositions(CompoundTag tag, String key) {
        List<BlockPos> out = new ArrayList<>();
        if (tag.contains(key, Tag.TAG_LONG_ARRAY))
            for (long l : tag.getLongArray(key)) out.add(BlockPos.of(l));
        return out;
    }
}
