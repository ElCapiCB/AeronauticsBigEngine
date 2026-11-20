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
 * one sub-level for the crank group (rotary joint + servo), and per cylinder one for the piston (prismatic joint)
 * and one for the connecting rod (pinned to the crank pin and to the piston).
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
    /** Plot positions of the crankshaft pieces placed where main bearings are, so the crank stays one piece. */
    public final List<BlockPos> bridges = new ArrayList<>();
    public final List<Body> pistons = new ArrayList<>();
    public final List<Body> rods = new ArrayList<>();

    /** Joints of one cylinder (runtime only, rebuilt after a reload). */
    public static final class CylinderJoints {
        public GenericConstraintHandle slide;      // piston <-> engine block (prismatic)
        public RotaryConstraintHandle rodCrank;    // rod big end <-> crank pin
        public RotaryConstraintHandle rodPiston;   // rod small end <-> piston pin
        public FreeConstraintHandle rodCarrier;    // no contacts rod <-> engine block
        public FreeConstraintHandle pistonCrank;   // no contacts piston <-> crank
    }

    public transient RotaryConstraintHandle crankHandle;
    public final transient List<CylinderJoints> joints = new ArrayList<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (crank != null) t.put("Crank", crank.save());
        NbtUtil.putPositions(t, "Bridges", bridges);
        ListTag l = new ListTag();
        for (Body b : pistons) l.add(b.save());
        t.put("Pistons", l);
        ListTag r = new ListTag();
        for (Body b : rods) r.add(b.save());
        t.put("Rods", r);
        return t;
    }

    public static PhysicalAssembly load(CompoundTag t) {
        PhysicalAssembly a = new PhysicalAssembly();
        if (t.contains("Crank")) a.crank = Body.load(t.getCompound("Crank"));
        a.bridges.addAll(NbtUtil.getPositions(t, "Bridges"));
        for (Tag e : t.getList("Pistons", Tag.TAG_COMPOUND)) a.pistons.add(Body.load((CompoundTag) e));
        for (Tag e : t.getList("Rods", Tag.TAG_COMPOUND)) a.rods.add(Body.load((CompoundTag) e));
        return a;
    }
}
