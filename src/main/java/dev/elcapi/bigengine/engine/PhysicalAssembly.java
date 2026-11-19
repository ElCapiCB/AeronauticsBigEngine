package dev.elcapi.bigengine.engine;

import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bookkeeping for an engine whose moving parts were turned into Sable sub-levels:
 * one sub-level for the crank group (rotary joint + servo) and one per piston/rod (prismatic joint + servo).
 */
public final class PhysicalAssembly {
    public static final class Body {
        public UUID id;
        /** Where the anchor block was before assembly (controller's level/plot coordinates). */
        public BlockPos homeAnchor = BlockPos.ZERO;
        /** Where the anchor block lives now, inside the sub-level's plot. */
        public BlockPos plotAnchor = BlockPos.ZERO;
        /** Original positions of every block moved into the sub-level. */
        public final List<BlockPos> homeBlocks = new ArrayList<>();

        public BlockPos toPlot(BlockPos home) {
            return home.subtract(homeAnchor).offset(plotAnchor);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putLong("Home", homeAnchor.asLong());
            t.putLong("Plot", plotAnchor.asLong());
            NbtUtil.putPositions(t, "Blocks", homeBlocks);
            return t;
        }

        static Body load(CompoundTag t) {
            Body b = new Body();
            b.id = t.getUUID("Id");
            b.homeAnchor = BlockPos.of(t.getLong("Home"));
            b.plotAnchor = BlockPos.of(t.getLong("Plot"));
            b.homeBlocks.addAll(NbtUtil.getPositions(t, "Blocks"));
            return b;
        }
    }

    public Body crank;
    public final List<Body> pistons = new ArrayList<>();

    // runtime only
    public transient RotaryConstraintHandle crankHandle;
    public final transient List<GenericConstraintHandle> pistonHandles = new ArrayList<>();
    public final transient List<FreeConstraintHandle> pistonCrankHandles = new ArrayList<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (crank != null) t.put("Crank", crank.save());
        ListTag l = new ListTag();
        for (Body b : pistons) l.add(b.save());
        t.put("Pistons", l);
        return t;
    }

    public static PhysicalAssembly load(CompoundTag t) {
        PhysicalAssembly a = new PhysicalAssembly();
        if (t.contains("Crank")) a.crank = Body.load(t.getCompound("Crank"));
        for (Tag e : t.getList("Pistons", Tag.TAG_COMPOUND)) a.pistons.add(Body.load((CompoundTag) e));
        return a;
    }
}
