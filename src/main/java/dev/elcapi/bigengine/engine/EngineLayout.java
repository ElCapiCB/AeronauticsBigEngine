package dev.elcapi.bigengine.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of scanning an inline (I) engine. All positions are in the coordinate space of the level/plot the
 * controller lives in (so engines work both on the ground and on a Sable sub-level / ship).
 */
public final class EngineLayout {
    public Direction.Axis crankAxis = Direction.Axis.X;
    /** Whole crank line, in order along the axis (bearings and the output included). */
    public final List<BlockPos> crankLine = new ArrayList<>();
    /** Blocks that rotate with the crank (crankshaft, crank throws, flywheels): they become one Sable sub-level. */
    public final List<BlockPos> rotatingBlocks = new ArrayList<>();
    public BlockPos outputPos = BlockPos.ZERO;
    public final List<Cylinder> cylinders = new ArrayList<>();

    /** Sable masses [kpg]. */
    public double rotatingMass;
    public double flywheelMass;
    /** Rotational inertia proxy used by the simulation (flywheels count much more than plain journals). */
    public double inertia;

    public double reciprocatingMass() {
        double m = 0;
        for (Cylinder c : cylinders) m += c.reciprocatingMass;
        return m;
    }

    public double totalMovingMass() {
        return rotatingMass + reciprocatingMass();
    }

    public static final class Cylinder {
        public BlockPos throwPos = BlockPos.ZERO;
        /** Direction from the crank towards the cylinder head. */
        public Direction dir = Direction.UP;
        /** Crank pin angle of this throw, degrees (0, 90, 180, 270). */
        public int phaseDeg;
        public BlockPos rodPos = BlockPos.ZERO;
        public final List<BlockPos> pistonCells = new ArrayList<>();
        public final List<BlockPos> boreCells = new ArrayList<>();
        public final List<BlockPos> headCells = new ArrayList<>();
        public final List<BlockPos> injectors = new ArrayList<>();
        public final List<BlockPos> sparkPlugs = new ArrayList<>();
        public final List<BlockPos> intakes = new ArrayList<>();
        public final List<BlockPos> exhausts = new ArrayList<>();
        /** Piston(s) + connecting rod, Sable mass [kpg]. */
        public double reciprocatingMass;

        /** All blocks that slide together as one Sable sub-level (rod first). */
        public List<BlockPos> slidingBlocks() {
            List<BlockPos> l = new ArrayList<>(pistonCells.size() + 1);
            l.add(rodPos);
            l.addAll(pistonCells);
            return l;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("Throw", throwPos.asLong());
            t.putInt("Dir", dir.get3DDataValue());
            t.putInt("Phase", phaseDeg);
            t.putLong("Rod", rodPos.asLong());
            NbtUtil.putPositions(t, "Pistons", pistonCells);
            NbtUtil.putPositions(t, "Bore", boreCells);
            NbtUtil.putPositions(t, "Heads", headCells);
            NbtUtil.putPositions(t, "Injectors", injectors);
            NbtUtil.putPositions(t, "Sparks", sparkPlugs);
            NbtUtil.putPositions(t, "Intakes", intakes);
            NbtUtil.putPositions(t, "Exhausts", exhausts);
            t.putDouble("Mass", reciprocatingMass);
            return t;
        }

        static Cylinder load(CompoundTag t) {
            Cylinder c = new Cylinder();
            c.throwPos = BlockPos.of(t.getLong("Throw"));
            c.dir = Direction.from3DDataValue(t.getInt("Dir"));
            c.phaseDeg = t.getInt("Phase");
            c.rodPos = BlockPos.of(t.getLong("Rod"));
            c.pistonCells.addAll(NbtUtil.getPositions(t, "Pistons"));
            c.boreCells.addAll(NbtUtil.getPositions(t, "Bore"));
            c.headCells.addAll(NbtUtil.getPositions(t, "Heads"));
            c.injectors.addAll(NbtUtil.getPositions(t, "Injectors"));
            c.sparkPlugs.addAll(NbtUtil.getPositions(t, "Sparks"));
            c.intakes.addAll(NbtUtil.getPositions(t, "Intakes"));
            c.exhausts.addAll(NbtUtil.getPositions(t, "Exhausts"));
            c.reciprocatingMass = t.getDouble("Mass");
            return c;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Axis", crankAxis.ordinal());
        NbtUtil.putPositions(t, "Line", crankLine);
        NbtUtil.putPositions(t, "Rotating", rotatingBlocks);
        t.putLong("Output", outputPos.asLong());
        ListTag cyl = new ListTag();
        for (Cylinder c : cylinders) cyl.add(c.save());
        t.put("Cylinders", cyl);
        t.putDouble("RotMass", rotatingMass);
        t.putDouble("FlyMass", flywheelMass);
        t.putDouble("Inertia", inertia);
        return t;
    }

    public static EngineLayout load(CompoundTag t) {
        EngineLayout l = new EngineLayout();
        l.crankAxis = Direction.Axis.values()[t.getInt("Axis")];
        l.crankLine.addAll(NbtUtil.getPositions(t, "Line"));
        l.rotatingBlocks.addAll(NbtUtil.getPositions(t, "Rotating"));
        l.outputPos = BlockPos.of(t.getLong("Output"));
        for (Tag e : t.getList("Cylinders", Tag.TAG_COMPOUND)) l.cylinders.add(Cylinder.load((CompoundTag) e));
        l.rotatingMass = t.getDouble("RotMass");
        l.flywheelMass = t.getDouble("FlyMass");
        l.inertia = t.getDouble("Inertia");
        return l;
    }
}
