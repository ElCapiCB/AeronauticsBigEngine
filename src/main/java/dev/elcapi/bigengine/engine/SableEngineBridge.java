package dev.elcapi.bigengine.engine;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.BigEngineConfig;
import dev.elcapi.bigengine.content.block.MainBearingBlock;
import dev.elcapi.bigengine.content.blockentity.EngineLinkBlockEntity;
import dev.elcapi.bigengine.registry.ModBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that touches Sable. An assembled engine is a real slider-crank mechanism made of sub-levels:
 * <ul>
 *   <li>crank group: rotary joint to the engine block, driven by a servo that follows the simulated crank angle;</li>
 *   <li>per cylinder, a connecting rod pinned (rotary joints) to the crank pin and to the piston;</li>
 *   <li>per cylinder, a piston on a prismatic joint that only lets it slide along the bore.</li>
 * </ul>
 * Only the crank is motor driven; rods and pistons are carried by the joints.
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

    private static Vector3d vec(Direction d) {
        return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static Vector3d axisVec(Direction.Axis axis) {
        return vec(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
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

    /**
     * Moves a freshly assembled sub-level to a new pose given as a rigid transform of the engine's home frame
     * (the level or the carrying ship's plot): home point v goes to {@code rotation * (v - pivot) + target}.
     */
    private static void placeBody(ServerLevel level, PhysicsPipeline pipeline, @Nullable ServerSubLevel carrier,
                                  PhysicalAssembly.Body body, Vector3d pivot, Vector3d target, Quaterniond rotation) {
        ServerSubLevel sub = find(level, body);
        if (sub == null) return;
        Pose3d pose = sub.logicalPose();
        Vector3d rp = new Vector3d(pose.rotationPoint());
        Vector3d home = rp.sub(body.plotAnchor.getX(), body.plotAnchor.getY(), body.plotAnchor.getZ())
                .add(body.homeAnchor.getX(), body.homeAnchor.getY(), body.homeAnchor.getZ());
        Vector3d moved = rotation.transform(home.sub(pivot, new Vector3d())).add(target);
        Vector3d worldPos = carrier != null ? carrier.logicalPose().transformPosition(moved, new Vector3d()) : moved;
        Quaterniond orientation = carrier != null ? new Quaterniond(carrier.logicalPose().orientation()).mul(rotation) : new Quaterniond(rotation);
        pose.position().set(worldPos);
        pose.orientation().set(orientation);
        pipeline.teleport(sub, worldPos, orientation);
        sub.updateLastPose();
    }

    // ------------------------------------------------------------------ assembly

    /** Turns the crank group, every rod and every piston into sub-levels. Returns an error, or null on success. */
    @Nullable
    public static Component assemble(ServerLevel level, BlockPos controller, EngineLayout layout, PhysicalAssembly out) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_sable");
        if (layout.rotatingBlocks.isEmpty()) return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_throws");
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        ServerSubLevel carrier = containing(level, controller);

        // ---- crank group. Main bearings between rotating parts go along for the ride (so Sable does not split
        // the crank into pieces) and are then swapped: crankshaft in the sub-level, bearing back in the world.
        int first = layout.crankLine.indexOf(layout.rotatingBlocks.get(0));
        int last = layout.crankLine.indexOf(layout.rotatingBlocks.get(layout.rotatingBlocks.size() - 1));
        List<BlockPos> crankBlocks = new ArrayList<>();
        Map<BlockPos, BlockState> bearings = new LinkedHashMap<>();
        for (int i = Math.min(first, last); i <= Math.max(first, last); i++) {
            BlockPos p = layout.crankLine.get(i);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MainBearingBlock) bearings.put(p, s);
            crankBlocks.add(p);
        }
        BlockPos crankAnchor = layout.cylinders.get(0).throwPos;
        out.crank = assembleBody(level, crankAnchor, crankBlocks);
        BlockState journal = ModBlocks.CRANKSHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, layout.crankAxis);
        for (Map.Entry<BlockPos, BlockState> e : bearings.entrySet()) {
            BlockPos plot = out.crank.toPlot(e.getKey());
            level.setBlock(plot, journal, Block.UPDATE_ALL);
            level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            out.bridges.add(plot.immutable());
        }
        out.crank.homeBlocks.removeAll(bearings.keySet());
        link(level, out.crank, controller);

        // ---- pistons and rods, each its own sub-level
        for (EngineLayout.Cylinder c : layout.cylinders) {
            PhysicalAssembly.Body piston = assembleBody(level, c.pistonCells.get(0), new ArrayList<>(c.pistonCells));
            link(level, piston, controller);
            out.pistons.add(piston);
            PhysicalAssembly.Body rod = assembleBody(level, c.rodPos, List.of(c.rodPos));
            out.rods.add(rod);
        }

        // ---- put every piston and rod where the crank (at angle 0) says it must be, so the joints start closed
        double r = EngineMath.CRANK_RADIUS, l = EngineMath.ROD_LENGTH;
        Vector3d axis = axisVec(layout.crankAxis);
        for (int i = 0; i < layout.cylinders.size(); i++) {
            EngineLayout.Cylinder c = layout.cylinders.get(i);
            double alpha = c.phaseDeg;
            double travel = EngineMath.pistonTravel(alpha);
            Vector3d d = vec(c.dir);
            Vector3d crankCentre = center(c.throwPos);

            placeBody(level, pipeline, carrier, out.pistons.get(i), new Vector3d(), new Vector3d(d).mul(travel), new Quaterniond());

            Vector3d pin = crankCentre.add(EngineMath.pinDirection(layout.crankAxis, c.dir, alpha).mul(r), new Vector3d());
            double a = Math.toRadians(alpha);
            double beta = Math.atan2(-r * Math.sin(a), l - r + travel - r * Math.cos(a));
            Vector3d bigEndHome = center(c.throwPos).sub(new Vector3d(d).mul(0.5));
            placeBody(level, pipeline, carrier, out.rods.get(i), bigEndHome, pin, new Quaterniond().rotateAxis(beta, axis.x, axis.y, axis.z));
        }
        return null;
    }

    /** Puts every block back where it was and dissolves the sub-levels. */
    public static void disassemble(ServerLevel level, PhysicalAssembly a) {
        removeHandles(a);
        for (PhysicalAssembly.Body b : a.rods) restore(level, b);
        for (PhysicalAssembly.Body b : a.pistons) restore(level, b);
        for (BlockPos bridge : a.bridges) {
            if (level.getBlockState(bridge).is(ModBlocks.CRANKSHAFT.get())) level.setBlock(bridge, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (a.crank != null) restore(level, a.crank);
        a.rods.clear();
        a.pistons.clear();
        a.bridges.clear();
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

    private static void remove(@Nullable PhysicsConstraintHandle h) {
        if (h != null && h.isValid()) h.remove();
    }

    public static void removeHandles(PhysicalAssembly a) {
        remove(a.crankHandle);
        a.crankHandle = null;
        for (PhysicalAssembly.CylinderJoints j : a.joints) {
            remove(j.slide); remove(j.rodCrank); remove(j.rodPiston); remove(j.rodCarrier); remove(j.pistonCrank);
        }
        a.joints.clear();
    }

    // ------------------------------------------------------------------ constraints

    private static boolean missing(@Nullable PhysicsConstraintHandle h) {
        return h == null || !h.isValid();
    }

    private static <T extends PhysicsConstraintHandle> T noContacts(@Nullable T h) {
        if (h != null) h.setContactsEnabled(false);
        return h;
    }

    /**
     * Makes sure every joint exists (Sable does not save joints, so they are rebuilt after a reload).
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

        Vector3d axis = axisVec(layout.crankAxis);
        if (missing(a.crankHandle)) {
            a.crankHandle = noContacts(pipeline.addConstraint(carrier, crank, new RotaryConstraintConfiguration(
                    center(a.crank.homeAnchor), center(a.crank.plotAnchor), axis, new Vector3d(axis))));
        }

        while (a.joints.size() < layout.cylinders.size()) a.joints.add(new PhysicalAssembly.CylinderJoints());
        double r = EngineMath.CRANK_RADIUS, l = EngineMath.ROD_LENGTH;

        for (int i = 0; i < layout.cylinders.size() && i < a.pistons.size() && i < a.rods.size(); i++) {
            EngineLayout.Cylinder c = layout.cylinders.get(i);
            PhysicalAssembly.Body pistonBody = a.pistons.get(i), rodBody = a.rods.get(i);
            ServerSubLevel piston = find(level, pistonBody), rod = find(level, rodBody);
            if (piston == null || rod == null) continue;
            PhysicalAssembly.CylinderJoints j = a.joints.get(i);
            Vector3d d = vec(c.dir);

            if (missing(j.slide)) {
                // Joint frame: local X = cylinder axis. Everything but sliding along X is locked.
                Quaterniond frame = new Quaterniond().rotationTo(1, 0, 0, d.x, d.y, d.z);
                j.slide = noContacts(pipeline.addConstraint(carrier, piston, new GenericConstraintConfiguration(
                        center(pistonBody.homeAnchor), center(pistonBody.plotAnchor), frame, new Quaterniond(frame),
                        EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z,
                                ConstraintJointAxis.ANGULAR_X, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z))));
                if (j.slide != null) j.slide.setLimit(ConstraintJointAxis.LINEAR_X, -0.05, 2 * r + 0.05);
            }
            if (missing(j.rodCrank)) {
                // crank pin, in the crank's own (unrotated) frame
                Vector3d pin = center(a.crank.toPlot(c.throwPos)).add(EngineMath.pinDirection(layout.crankAxis, c.dir, c.phaseDeg).mul(r));
                Vector3d bigEnd = center(rodBody.plotAnchor).sub(new Vector3d(d).mul(l - 0.5));
                j.rodCrank = noContacts(pipeline.addConstraint(crank, rod, new RotaryConstraintConfiguration(pin, bigEnd, axis, new Vector3d(axis))));
            }
            if (missing(j.rodPiston)) {
                Vector3d smallEnd = center(rodBody.plotAnchor).add(new Vector3d(d).mul(0.5));
                Vector3d pistonPin = center(pistonBody.plotAnchor).sub(new Vector3d(d).mul(0.5));
                j.rodPiston = noContacts(pipeline.addConstraint(rod, piston, new RotaryConstraintConfiguration(smallEnd, pistonPin, axis, new Vector3d(axis))));
            }
            if (missing(j.rodCarrier)) {
                j.rodCarrier = noContacts(pipeline.addConstraint(carrier, rod, new FreeConstraintConfiguration(
                        center(rodBody.homeAnchor), center(rodBody.plotAnchor), new Quaterniond())));
            }
            if (missing(j.pistonCrank)) {
                j.pistonCrank = noContacts(pipeline.addConstraint(piston, crank, new FreeConstraintConfiguration(
                        center(pistonBody.plotAnchor), center(a.crank.plotAnchor), new Quaterniond())));
            }
        }
        return true;
    }

    /**
     * Physics sub-step: point the crank servo at the engine's current crank angle (pistons and rods follow
     * through their joints; an optional assisting servo can be enabled on the pistons in the config).
     */
    public static void driveServos(ServerLevel level, EngineLayout layout, PhysicalAssembly a,
                                   double crankAngleDeg, double nextAngleDeg) {
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics == null) return;
        double t = physics.getPartialPhysicsTick();
        double angle = crankAngleDeg + (nextAngleDeg - crankAngleDeg) * t;
        PhysicsPipeline pipeline = physics.getPipeline();

        ServerSubLevel crank = find(level, a.crank);
        if (crank != null && !missing(a.crankHandle)) {
            Vector3d axis = axisVec(layout.crankAxis);
            double inertia = Math.max(10.0, crank.getMassTracker().getInertiaTensor().transform(new Vector3d(axis)).dot(axis));
            // the crank also has to swing the rods and pistons around
            for (PhysicalAssembly.Body b : a.pistons) {
                ServerSubLevel s = find(level, b);
                if (s != null) inertia += s.getMassTracker().getMass() * 0.25;
            }
            double target = Math.toRadians(((angle % 360) + 360) % 360);
            if (BigEngineConfig.INVERT_CRANK_MOTOR.get()) target = -target;
            a.crankHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, target,
                    BigEngineConfig.CRANK_STIFFNESS.get() * inertia, BigEngineConfig.CRANK_DAMPING.get() * inertia, false, 0.0);
            pipeline.wakeUp(crank);
        }

        double assist = BigEngineConfig.PISTON_STIFFNESS.get();
        for (int i = 0; i < a.pistons.size() && i < layout.cylinders.size() && i < a.joints.size(); i++) {
            ServerSubLevel piston = find(level, a.pistons.get(i));
            if (piston == null) continue;
            pipeline.wakeUp(piston);
            ServerSubLevel rod = i < a.rods.size() ? find(level, a.rods.get(i)) : null;
            if (rod != null) pipeline.wakeUp(rod);
            GenericConstraintHandle h = a.joints.get(i).slide;
            if (assist <= 0 || missing(h)) continue;
            double travel = EngineMath.pistonTravel(angle + layout.cylinders.get(i).phaseDeg);
            if (BigEngineConfig.INVERT_PISTON_MOTOR.get()) travel = -travel;
            double mass = Math.max(1.0, piston.getMassTracker().getMass());
            h.setMotor(ConstraintJointAxis.LINEAR_X, travel, assist * mass, BigEngineConfig.PISTON_DAMPING.get() * mass, false, 0.0);
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
