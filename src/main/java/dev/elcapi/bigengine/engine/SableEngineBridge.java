package dev.elcapi.bigengine.engine;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.BigEngineConfig;
import dev.elcapi.bigengine.content.blockentity.EngineLinkBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Everything that touches Sable: turning the crank group and every piston into sub-levels, joining them with
 * physics constraints, driving them with servo motors, and pushing reaction torque into the carrying ship.
 */
public final class SableEngineBridge {
    private SableEngineBridge() {}

    // ------------------------------------------------------------------ helpers

    @Nullable
    public static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
        SubLevel s = Sable.HELPER.getContaining(level, pos);
        return s instanceof ServerSubLevel ss ? ss : null;
    }

    @Nullable
    private static ServerSubLevel find(ServerLevel level, @Nullable PhysicalAssembly.Body body) {
        if (body == null || body.id == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel s = container.getSubLevel(body.id);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() ? ss : null;
    }

    private static Vector3d center(BlockPos p) {
        return new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private static Vector3d axisVec(Direction.Axis axis) {
        Direction d = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static BoundingBox3i bounds(List<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static PhysicalAssembly.Body assembleBody(ServerLevel level, BlockPos anchor, List<BlockPos> blocks) {
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds(blocks));
        PhysicalAssembly.Body body = new PhysicalAssembly.Body();
        body.id = sub.getUniqueId();
        body.homeAnchor = anchor.immutable();
        body.plotAnchor = sub.getPlot().getCenterBlock().immutable();
        for (BlockPos b : blocks) body.homeBlocks.add(b.immutable());
        return body;
    }

    private static void link(ServerLevel level, PhysicalAssembly.Body body, BlockPos controller) {
        for (BlockPos home : body.homeBlocks) {
            BlockEntity be = level.getBlockEntity(body.toPlot(home));
            if (be instanceof EngineLinkBlockEntity link) link.setController(controller);
        }
    }

    // ------------------------------------------------------------------ assembly

    /** Turns the crank group and each piston into sub-levels. Returns an error, or null on success. */
    @Nullable
    public static Component assemble(ServerLevel level, BlockPos controller, EngineLayout layout, PhysicalAssembly out) {
        if (SubLevelContainer.getContainer(level) == null)
            return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_sable");
        if (layout.rotatingBlocks.isEmpty()) return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_throws");

        BlockPos crankAnchor = layout.cylinders.get(0).throwPos;
        out.crank = assembleBody(level, crankAnchor, new ArrayList<>(layout.rotatingBlocks));
        link(level, out.crank, controller);

        for (EngineLayout.Cylinder c : layout.cylinders) {
            PhysicalAssembly.Body body = assembleBody(level, c.rodPos, c.slidingBlocks());
            link(level, body, controller);
            out.pistons.add(body);
        }
        return null;
    }

    /** Puts every block back where it was and dissolves the sub-levels. */
    public static void disassemble(ServerLevel level, PhysicalAssembly a) {
        removeHandles(a);
        for (PhysicalAssembly.Body b : a.pistons) restore(level, b);
        if (a.crank != null) restore(level, a.crank);
        a.pistons.clear();
        a.crank = null;
    }

    private static void restore(ServerLevel level, PhysicalAssembly.Body body) {
        ServerSubLevel sub = find(level, body);
        if (sub == null) return;
        SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(body.plotAnchor, body.homeAnchor, 0, Rotation.NONE, level);
        List<BlockPos> plotBlocks = new ArrayList<>();
        for (BlockPos home : body.homeBlocks) {
            BlockPos p = body.toPlot(home);
            if (!level.getBlockState(p).isAir()) plotBlocks.add(p);
        }
        for (BlockPos p : plotBlocks) {
            if (level.getBlockEntity(p) instanceof EngineLinkBlockEntity link) link.setController(null);
        }
        if (!plotBlocks.isEmpty()) SubLevelAssemblyHelper.moveBlocks(level, transform, plotBlocks);
    }

    public static void removeHandles(PhysicalAssembly a) {
        if (a.crankHandle != null && a.crankHandle.isValid()) a.crankHandle.remove();
        a.crankHandle = null;
        for (GenericConstraintHandle h : a.pistonHandles) if (h != null && h.isValid()) h.remove();
        for (FreeConstraintHandle h : a.pistonCrankHandles) if (h != null && h.isValid()) h.remove();
        a.pistonHandles.clear();
        a.pistonCrankHandles.clear();
    }

    // ------------------------------------------------------------------ constraints

    /**
     * Makes sure every joint exists (they are not saved by Sable, so they are rebuilt after a reload).
     *
     * @return false if Sable is unavailable in this level
     */
    public static boolean ensureConstraints(ServerLevel level, BlockPos controller, EngineLayout layout, PhysicalAssembly a) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        ServerSubLevel carrier = containing(level, controller);

        ServerSubLevel crank = find(level, a.crank);
        if (crank == null) return true; // not loaded (yet) - try again next tick

        if (a.crankHandle == null || !a.crankHandle.isValid()) {
            Vector3d axis = axisVec(layout.crankAxis);
            RotaryConstraintConfiguration cfg = new RotaryConstraintConfiguration(
                    center(a.crank.homeAnchor), center(a.crank.plotAnchor), axis, new Vector3d(axis));
            a.crankHandle = pipeline.addConstraint(carrier, crank, cfg);
            if (a.crankHandle != null) a.crankHandle.setContactsEnabled(false);
        }

        while (a.pistonHandles.size() < a.pistons.size()) a.pistonHandles.add(null);
        while (a.pistonCrankHandles.size() < a.pistons.size()) a.pistonCrankHandles.add(null);

        for (int i = 0; i < a.pistons.size() && i < layout.cylinders.size(); i++) {
            PhysicalAssembly.Body body = a.pistons.get(i);
            ServerSubLevel piston = find(level, body);
            if (piston == null) continue;
            GenericConstraintHandle h = a.pistonHandles.get(i);
            if (h == null || !h.isValid()) {
                Direction dir = layout.cylinders.get(i).dir;
                // Joint frame: local X axis = cylinder axis. Everything but sliding along X is locked.
                Quaterniond frame = new Quaterniond().rotationTo(1, 0, 0, dir.getStepX(), dir.getStepY(), dir.getStepZ());
                GenericConstraintConfiguration cfg = new GenericConstraintConfiguration(
                        center(body.homeAnchor), center(body.plotAnchor), frame, new Quaterniond(frame),
                        EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z,
                                ConstraintJointAxis.ANGULAR_X, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z));
                h = pipeline.addConstraint(carrier, piston, cfg);
                if (h != null) {
                    h.setContactsEnabled(false);
                    h.setLimit(ConstraintJointAxis.LINEAR_X, -0.05, 1.05);
                }
                a.pistonHandles.set(i, h);
            }
            FreeConstraintHandle fh = a.pistonCrankHandles.get(i);
            if (fh == null || !fh.isValid()) {
                // A free joint that only exists to stop the rod from colliding with the crank throw.
                FreeConstraintConfiguration cfg = new FreeConstraintConfiguration(
                        center(body.plotAnchor), center(a.crank.plotAnchor), new Quaterniond());
                fh = pipeline.addConstraint(piston, crank, cfg);
                if (fh != null) fh.setContactsEnabled(false);
                a.pistonCrankHandles.set(i, fh);
            }
        }
        return true;
    }

    /**
     * Physics sub-step: point every servo at the engine's current crank angle.
     *
     * @param crankAngleDeg   crank angle at the start of the game tick (unwrapped)
     * @param nextAngleDeg    crank angle at the end of the game tick (unwrapped)
     */
    public static void driveServos(ServerLevel level, EngineLayout layout, PhysicalAssembly a,
                                   double crankAngleDeg, double nextAngleDeg) {
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics == null) return;
        double t = physics.getPartialPhysicsTick();
        double angle = crankAngleDeg + (nextAngleDeg - crankAngleDeg) * t;
        PhysicsPipeline pipeline = physics.getPipeline();

        ServerSubLevel crank = find(level, a.crank);
        if (crank != null && a.crankHandle != null && a.crankHandle.isValid()) {
            Vector3d axis = axisVec(layout.crankAxis);
            double inertia = Math.max(10.0, crank.getMassTracker().getInertiaTensor().transform(new Vector3d(axis)).dot(axis));
            double target = Math.toRadians(((angle % 360) + 360) % 360);
            if (BigEngineConfig.INVERT_CRANK_MOTOR.get()) target = -target;
            a.crankHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, target,
                    BigEngineConfig.CRANK_STIFFNESS.get() * inertia, BigEngineConfig.CRANK_DAMPING.get() * inertia, false, 0.0);
            a.crankHandle.setContactsEnabled(false);
            pipeline.wakeUp(crank);
        }

        for (int i = 0; i < a.pistons.size() && i < layout.cylinders.size() && i < a.pistonHandles.size(); i++) {
            GenericConstraintHandle h = a.pistonHandles.get(i);
            if (h == null || !h.isValid()) continue;
            ServerSubLevel piston = find(level, a.pistons.get(i));
            if (piston == null) continue;
            EngineLayout.Cylinder c = layout.cylinders.get(i);
            double travel = EngineMath.pistonTravel(angle + c.phaseDeg);
            if (BigEngineConfig.INVERT_PISTON_MOTOR.get()) travel = -travel;
            double mass = Math.max(1.0, piston.getMassTracker().getMass());
            h.setMotor(ConstraintJointAxis.LINEAR_X, travel,
                    BigEngineConfig.PISTON_STIFFNESS.get() * mass, BigEngineConfig.PISTON_DAMPING.get() * mass, false, 0.0);
            pipeline.wakeUp(piston);
        }
    }

    // ------------------------------------------------------------------ reaction on the carrying ship

    /** Applies a torque impulse (about the crank axis) and an optional random shake to the ship carrying the engine. */
    public static void pushCarrier(ServerLevel level, BlockPos controller, Direction.Axis axis, double torqueImpulse, double shake) {
        ServerSubLevel carrier = containing(level, controller);
        if (carrier == null) return;
        RigidBodyHandle handle = RigidBodyHandle.of(carrier);
        if (handle == null || !handle.isValid()) return;
        Vector3d t = axisVec(axis).mul(torqueImpulse);
        if (shake > 0) {
            t.add((level.random.nextDouble() - 0.5) * shake, (level.random.nextDouble() - 0.5) * shake, (level.random.nextDouble() - 0.5) * shake);
        }
        if (t.lengthSquared() > 1.0e-12) handle.applyAngularImpulse(t);
    }
}
