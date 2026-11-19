package dev.elcapi.bigengine.content.block;

import dev.elcapi.bigengine.content.blockentity.EngineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Helpers shared by every engine part block. */
public final class EnginePart {
    private EnginePart() {}

    /** Tells loaded controllers near {@code pos} that the structure changed so they rescan on their next tick. */
    public static void notifyStructureChanged(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        EngineControllerBlockEntity.forEachLoadedNear(level, pos, 40, EngineControllerBlockEntity::markStructureDirty);
    }

    public static Direction.Axis[] perpendicular(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
        };
    }
}
