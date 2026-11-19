package dev.elcapi.bigengine.engine;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.block.*;
import dev.elcapi.bigengine.registry.ModBlocks;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.*;

/**
 * Finds and validates an inline (I) engine starting from the controller.
 * <pre>
 *  head  head        &lt;- cylinder heads (+ injector, spark plug, intake, exhaust on their faces)
 *  wall [bore] wall  &lt;- empty bore lined with cylinder walls, coat the walls with oil film
 *       piston       &lt;- engine piston(s), 1x1 up to 3x3
 *       rod          &lt;- connecting rod
 *  ===throw===       &lt;- crank line: crankshaft / crank throws / main bearings / flywheel / power output
 * </pre>
 */
public final class EngineScanner {
    public static final int MAX_LINE = 64;
    public static final int MAX_BORE = 9;

    public record Result(EngineLayout layout, Component error) {
        static Result fail(String key, Object... args) {
            return new Result(null, Component.translatable("status." + BigEngine.MOD_ID + ".err." + key, args));
        }
    }

    private EngineScanner() {}

    public static boolean isCrankLine(BlockState s) {
        return s.getBlock() instanceof CrankshaftBlock || s.getBlock() instanceof PowerOutputBlock;
    }

    private static Direction.Axis axisOf(BlockState s) {
        return s.getValue(BlockStateProperties.AXIS);
    }

    public static Result scan(Level level, BlockPos controllerPos) {
        // 1. find the crank line next to the controller
        BlockPos start = null;
        for (Direction d : Direction.values()) {
            BlockPos p = controllerPos.relative(d);
            if (isCrankLine(level.getBlockState(p))) {
                start = p;
                break;
            }
        }
        if (start == null) return Result.fail("no_crank");

        Direction.Axis axis = axisOf(level.getBlockState(start));
        Direction neg = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction pos = neg.getOpposite();

        BlockPos first = start;
        for (int i = 0; i < MAX_LINE; i++) {
            BlockPos n = first.relative(neg);
            BlockState s = level.getBlockState(n);
            if (!isCrankLine(s) || axisOf(s) != axis) break;
            first = n;
        }

        EngineLayout layout = new EngineLayout();
        layout.crankAxis = axis;
        BlockPos cur = first;
        int outputs = 0;
        int outputIndex = -1;
        List<BlockPos> throwsFound = new ArrayList<>();
        for (int i = 0; i < MAX_LINE; i++) {
            BlockState s = level.getBlockState(cur);
            if (!isCrankLine(s) || axisOf(s) != axis) break;
            layout.crankLine.add(cur);
            double mass = PhysicsBlockPropertyHelper.getMass(level, cur, s);
            if (s.getBlock() instanceof PowerOutputBlock) {
                outputs++;
                outputIndex = layout.crankLine.size() - 1;
                layout.outputPos = cur;
            } else if (!(s.getBlock() instanceof MainBearingBlock)) {
                layout.rotatingBlocks.add(cur);
                layout.rotatingMass += mass;
                if (s.getBlock() instanceof FlywheelBlock) {
                    layout.flywheelMass += mass;
                    layout.inertia += mass * 4.0;     // big radius
                } else {
                    layout.inertia += mass * 0.5;
                }
                if (s.getBlock() instanceof CrankThrowBlock) throwsFound.add(cur);
            }
            cur = cur.relative(pos);
        }

        if (outputs == 0) return Result.fail("no_output");
        if (outputs > 1) return Result.fail("many_outputs");
        if (outputIndex != 0 && outputIndex != layout.crankLine.size() - 1) return Result.fail("output_not_at_end");
        if (throwsFound.isEmpty()) return Result.fail("no_throws");

        // 2. a cylinder on every crank throw
        int idx = 0;
        for (BlockPos t : throwsFound) {
            idx++;
            EngineLayout.Cylinder c = new EngineLayout.Cylinder();
            c.throwPos = t;

            Direction dir = null;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == axis) continue;
                BlockState s = level.getBlockState(t.relative(d));
                if (s.is(ModBlocks.CONNECTING_ROD.get()) && s.getValue(ConnectingRodBlock.FACING) == d) {
                    if (dir != null) return Result.fail("two_rods", idx);
                    dir = d;
                }
            }
            if (dir == null) return Result.fail("no_rod", idx);
            c.dir = dir;
            // angle of the pin relative to the cylinder: 0 => this piston is at TDC when the crank angle is 0
            int pinDeg = level.getBlockState(t).getValue(CrankThrowBlock.PHASE) * 90;
            c.phaseDeg = Math.floorMod(pinDeg - EngineMath.angleTo(axis, dir), 360);
            c.rodPos = t.relative(dir);

            Result r = scanCylinder(level, layout, c, idx);
            if (r != null) return r;
            layout.inertia += c.reciprocatingMass * 0.25;
            layout.cylinders.add(c);
        }
        layout.inertia = Math.max(1.0, layout.inertia);
        return new Result(layout, null);
    }

    /** Validates the piston / bore / walls / head of one cylinder. Returns null if fine. */
    private static Result scanCylinder(Level level, EngineLayout layout, EngineLayout.Cylinder c, int idx) {
        Direction dir = c.dir;
        BlockPos pistonStart = c.rodPos.relative(dir);
        BlockState ps = level.getBlockState(pistonStart);
        if (!ps.is(ModBlocks.ENGINE_PISTON.get()) || ps.getValue(EnginePistonBlock.FACING) != dir)
            return Result.fail("no_piston", idx);

        // flood fill the piston plane
        Direction.Axis[] plane = EnginePart.perpendicular(dir.getAxis());
        Set<BlockPos> pistons = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pistonStart);
        pistons.add(pistonStart);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction.Axis a : plane) {
                for (Direction.AxisDirection ad : Direction.AxisDirection.values()) {
                    BlockPos n = p.relative(Direction.fromAxisAndDirection(a, ad));
                    if (pistons.contains(n)) continue;
                    BlockState s = level.getBlockState(n);
                    if (s.is(ModBlocks.ENGINE_PISTON.get()) && s.getValue(EnginePistonBlock.FACING) == dir) {
                        pistons.add(n);
                        if (pistons.size() > MAX_BORE) return Result.fail("bore_too_big", idx);
                        queue.add(n);
                    }
                }
            }
        }
        // bounding box must be at most 3x3
        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
        for (BlockPos p : pistons) {
            int a = p.get(plane[0]), b = p.get(plane[1]);
            minA = Math.min(minA, a); maxA = Math.max(maxA, a);
            minB = Math.min(minB, b); maxB = Math.max(maxB, b);
        }
        if (maxA - minA > 2 || maxB - minB > 2) return Result.fail("bore_too_big", idx);
        c.pistonCells.addAll(pistons);

        double mass = PhysicsBlockPropertyHelper.getMass(level, c.rodPos, level.getBlockState(c.rodPos));
        for (BlockPos p : pistons) mass += PhysicsBlockPropertyHelper.getMass(level, p, level.getBlockState(p));
        c.reciprocatingMass = mass;

        // bore: empty (or oil film) cells right above the piston(s), surrounded by cylinder walls
        Set<BlockPos> bore = new LinkedHashSet<>();
        for (BlockPos p : pistons) bore.add(p.relative(dir));
        for (BlockPos b : bore) {
            BlockState s = level.getBlockState(b);
            if (!s.isAir() && !s.is(ModBlocks.OIL_FILM.get())) return Result.fail("bore_blocked", idx);
            for (Direction.Axis a : plane) {
                for (Direction.AxisDirection ad : Direction.AxisDirection.values()) {
                    BlockPos n = b.relative(Direction.fromAxisAndDirection(a, ad));
                    if (bore.contains(n)) continue;
                    if (!level.getBlockState(n).is(ModBlocks.CYLINDER_WALL.get())) return Result.fail("no_walls", idx);
                }
            }
        }
        c.boreCells.addAll(bore);

        // heads
        Set<BlockPos> heads = new LinkedHashSet<>();
        for (BlockPos b : bore) {
            BlockPos h = b.relative(dir);
            BlockState s = level.getBlockState(h);
            if (!s.is(ModBlocks.CYLINDER_HEAD.get())) return Result.fail("no_head", idx);
            heads.add(h);
        }
        c.headCells.addAll(heads);

        // ports bolted on the heads (FACING = away from the head)
        for (BlockPos h : heads) {
            for (Direction d : Direction.values()) {
                if (d == dir.getOpposite()) continue;
                BlockPos n = h.relative(d);
                if (heads.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (!(s.getBlock() instanceof EnginePortBlock port) || s.getValue(EnginePortBlock.FACING) != d) continue;
                switch (port.getPortType()) {
                    case FUEL_INJECTOR -> c.injectors.add(n);
                    case SPARK_PLUG -> c.sparkPlugs.add(n);
                    case AIR_INTAKE -> c.intakes.add(n);
                    case EXHAUST -> c.exhausts.add(n);
                }
            }
        }
        if (c.injectors.isEmpty()) return Result.fail("no_injector", idx);
        if (c.intakes.isEmpty()) return Result.fail("no_intake", idx);
        return null;
    }
}
