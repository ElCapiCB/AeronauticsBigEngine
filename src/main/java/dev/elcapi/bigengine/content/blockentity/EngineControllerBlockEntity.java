package dev.elcapi.bigengine.content.blockentity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.BigEngineConfig;
import dev.elcapi.bigengine.content.block.OilFilmBlock;
import dev.elcapi.bigengine.engine.*;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import dev.elcapi.bigengine.registry.ModBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Engine Control Unit: scans the engine, runs the 4-stroke simulation, feeds the Create network through the
 * power output and drives the Sable sub-levels when the engine is assembled.
 */
public class EngineControllerBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    // ------------------------------------------------------------------ loaded registry
    private static final Set<EngineControllerBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void forEachLoadedNear(Level level, BlockPos pos, int radius, Consumer<EngineControllerBlockEntity> action) {
        for (EngineControllerBlockEntity be : new ArrayList<>(LOADED)) {
            if (be.level == level && !be.isRemoved() && be.worldPosition.distSqr(pos) <= (double) radius * radius) action.accept(be);
        }
    }

    public enum Status { NO_ENGINE, STOPPED, CRANKING, RUNNING, STALLED, OVERHEATED, NO_FUEL }

    public static final int ACTION_START = 1, ACTION_STOP = 2, ACTION_ASSEMBLE = 3, ACTION_DISASSEMBLE = 4, ACTION_AUTO_TIME = 5;

    // ------------------------------------------------------------------ persistent state
    public final EngineSettings settings = new EngineSettings();
    @Nullable private EngineLayout layout;
    @Nullable private PhysicalAssembly assembly;
    private boolean running;
    private double rpm;
    /** Unwrapped crank angle in degrees. The 4-stroke cycle position is angle mod 720. */
    private double angle;
    private double temperature = 20;
    private int timedForCylinders = -1;

    // ------------------------------------------------------------------ runtime state
    @Nullable private Component structureError;
    private boolean structureDirty = true;
    private int starterTicks;
    private double integral;
    private double fuelCmd;
    private double tickStartAngle, tickEndAngle;
    private int tickCount;
    private int quietTicks;
    private Status status = Status.NO_ENGINE;

    private double[] cylOil = new double[0];
    private double[] cylMax = new double[0];
    private boolean[] cylCanFire = new boolean[0];
    private boolean[] cylFired = new boolean[0];

    // display values (synced)
    private float capacityPerRpm;
    private float combustionTorque;
    private float frictionTorque;
    private float load;
    private float avgOil;
    private float balance = 1;
    private float fuelStored;
    private boolean knocking;
    private int cylinderCount;
    private int[] cylPhases = new int[0];
    private boolean clientAssembled;

    public EngineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE_CONTROLLER.get(), pos, state);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
        structureDirty = true;
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        LOADED.remove(this);
        if (assembly != null) SableEngineBridge.removeHandles(assembly);
        super.onChunkUnloaded();
    }

    public void markStructureDirty() {
        structureDirty = true;
    }

    /** Called when the controller block is broken: put the engine back together as normal blocks. */
    public void onControllerRemoved() {
        if (level instanceof ServerLevel sl) {
            if (assembly != null) {
                SableEngineBridge.disassemble(sl, assembly);
                assembly = null;
            }
            if (layout != null && sl.getBlockEntity(layout.outputPos) instanceof PowerOutputBlockEntity out
                    && worldPosition.equals(out.getControllerPos())) out.link(null);
        }
    }

    // ------------------------------------------------------------------ accessors for the screen

    public Status getStatus() { return status; }
    public double getRpm() { return rpm; }
    public float getCapacityPerRpm() { return capacityPerRpm; }
    public float getCombustionTorque() { return combustionTorque; }
    public float getFrictionTorque() { return frictionTorque; }
    public float getLoad() { return load; }
    public double getTemperature() { return temperature; }
    public float getAvgOil() { return avgOil; }
    public float getBalance() { return balance; }
    public float getFuelStored() { return fuelStored; }
    public boolean isKnocking() { return knocking; }
    public boolean isRunning() { return running; }
    public boolean isAssembled() { return level != null && level.isClientSide ? clientAssembled : assembly != null; }
    public int getCylinderCount() { return cylinderCount; }
    public int getCylinderPhase(int i) { return i < cylPhases.length ? cylPhases[i] : 0; }
    public double getCylinderOil(int i) { return i < cylOil.length ? cylOil[i] : 0; }
    @Nullable public Component getStructureError() { return structureError; }

    // ------------------------------------------------------------------ player input

    public void handleSetting(int key, int value) {
        settings.set(key, value, BigEngineConfig.MAX_ENGINE_RPM.get());
        setChanged();
        sync();
    }

    public void handleAction(ServerPlayer player, int action) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (action) {
            case ACTION_START -> {
                refreshStructure();
                if (layout == null) {
                    if (structureError != null) player.displayClientMessage(structureError.copy().withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (status == Status.OVERHEATED && temperature > BigEngineConfig.OVERHEAT_TEMP.get() * 0.5) {
                    player.displayClientMessage(Component.translatable("message." + BigEngine.MOD_ID + ".too_hot"), true);
                    return;
                }
                running = true;
                starterTicks = BigEngineConfig.STARTER_TICKS.get();
                integral = 0.15;
                status = Status.CRANKING;
            }
            case ACTION_STOP -> {
                running = false;
                starterTicks = 0;
            }
            case ACTION_ASSEMBLE -> {
                if (assembly != null) return;
                structureDirty = true;
                refreshStructure();
                if (layout == null) {
                    if (structureError != null) player.displayClientMessage(structureError.copy().withStyle(ChatFormatting.RED), true);
                    return;
                }
                // The sub-levels are posed for crank angle 0, so the simulation restarts its cycle there.
                angle = 0;
                tickStartAngle = tickEndAngle = 0;
                PhysicalAssembly a = new PhysicalAssembly();
                Component err;
                try {
                    err = SableEngineBridge.assemble(sl, worldPosition, layout, a);
                } catch (RuntimeException e) {
                    BigEngine.LOGGER.error("Engine assembly failed at {}", worldPosition, e);
                    err = Component.translatable("status." + BigEngine.MOD_ID + ".err.assembly_failed");
                    // put back whatever was already moved
                    SableEngineBridge.disassemble(sl, a);
                }
                if (err != null) {
                    player.displayClientMessage(err.copy().withStyle(ChatFormatting.RED), true);
                    return;
                }
                assembly = a;
                level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.6f);
            }
            case ACTION_DISASSEMBLE -> {
                if (assembly == null) return;
                SableEngineBridge.disassemble(sl, assembly);
                assembly = null;
                structureDirty = true;
                level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.7f, 0.6f);
            }
            case ACTION_AUTO_TIME -> {
                if (layout != null) settings.autoTime(layout);
            }
            default -> {}
        }
        setChanged();
        sync();
    }

    // ------------------------------------------------------------------ structure

    private void refreshStructure() {
        if (!(level instanceof ServerLevel sl)) return;
        if (assembly != null) {
            if (layout == null) {
                // should not happen, but never leave sub-levels orphaned
                SableEngineBridge.disassemble(sl, assembly);
                assembly = null;
            } else {
                Component err = validateStatic(sl, layout);
                structureError = err;
                if (err != null && running) {
                    running = false;
                    status = Status.STALLED;
                }
                structureDirty = false;
                return;
            }
        }
        structureDirty = false;
        EngineScanner.Result r = EngineScanner.scan(sl, worldPosition);
        EngineLayout old = layout;
        layout = r.layout();
        structureError = r.error();
        if (old != null && (layout == null || !old.outputPos.equals(layout.outputPos))
                && sl.getBlockEntity(old.outputPos) instanceof PowerOutputBlockEntity out && worldPosition.equals(out.getControllerPos()))
            out.link(null);
        if (layout == null) {
            running = false;
            return;
        }
        if (sl.getBlockEntity(layout.outputPos) instanceof PowerOutputBlockEntity out) out.link(worldPosition);
        if (timedForCylinders != layout.cylinders.size()) {
            settings.autoTime(layout);
            timedForCylinders = layout.cylinders.size();
        }
    }

    /** While assembled the moving parts are in sub-levels, so only the static block is re-checked. */
    @Nullable
    private Component validateStatic(ServerLevel sl, EngineLayout l) {
        int idx = 0;
        for (EngineLayout.Cylinder c : l.cylinders) {
            idx++;
            for (BlockPos b : c.boreCells) {
                BlockState s = sl.getBlockState(b);
                if (!s.isAir() && !s.is(ModBlocks.OIL_FILM.get()))
                    return Component.translatable("status." + BigEngine.MOD_ID + ".err.bore_blocked", idx);
            }
            for (BlockPos h : c.headCells)
                if (!sl.getBlockState(h).is(ModBlocks.CYLINDER_HEAD.get()))
                    return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_head", idx);
        }
        if (!(sl.getBlockEntity(l.outputPos) instanceof PowerOutputBlockEntity))
            return Component.translatable("status." + BigEngine.MOD_ID + ".err.no_output");
        return null;
    }

    /** Oil coverage, ports and fuel, refreshed often (cheap). */
    private void refreshCylinders(ServerLevel sl, EngineLayout l) {
        int n = l.cylinders.size();
        if (cylOil.length != n) {
            cylOil = new double[n];
            cylMax = new double[n];
            cylCanFire = new boolean[n];
            cylFired = new boolean[n];
        }
        double oilSum = 0;
        fuelStored = 0;
        for (int i = 0; i < n; i++) {
            EngineLayout.Cylinder c = l.cylinders.get(i);
            cylOil[i] = oilCoverage(sl, c);
            oilSum += cylOil[i];
            for (BlockPos p : c.injectors)
                if (sl.getBlockEntity(p) instanceof FuelInjectorBlockEntity inj) fuelStored += inj.getTank().getFluidAmount();
        }
        avgOil = n == 0 ? 0 : (float) (oilSum / n);
        cylinderCount = n;
        cylPhases = new int[n];
        for (int i = 0; i < n; i++) cylPhases[i] = l.cylinders.get(i).phaseDeg;
    }

    /** 0..1: how much of the bore wall area is covered with oil (weighted by film thickness). */
    private static double oilCoverage(ServerLevel sl, EngineLayout.Cylinder c) {
        int faces = 0;
        double covered = 0;
        for (BlockPos b : c.boreCells) {
            BlockState s = sl.getBlockState(b);
            boolean isOil = s.is(ModBlocks.OIL_FILM.get());
            for (Direction d : Direction.values()) {
                if (d.getAxis() == c.dir.getAxis()) continue;
                if (!sl.getBlockState(b.relative(d)).is(ModBlocks.CYLINDER_WALL.get())) continue;
                faces++;
                if (isOil && MultifaceBlock.hasFace(s, d)) covered += s.getValue(OilFilmBlock.THICKNESS) / 3.0;
            }
        }
        return faces == 0 ? 0 : covered / faces;
    }

    /** Removes one layer of oil from a random oiled face of the bore. */
    private void wearOil(ServerLevel sl, EngineLayout.Cylinder c) {
        List<BlockPos> oiled = new ArrayList<>();
        for (BlockPos b : c.boreCells) if (sl.getBlockState(b).is(ModBlocks.OIL_FILM.get())) oiled.add(b);
        if (oiled.isEmpty()) return;
        BlockPos b = oiled.get(sl.random.nextInt(oiled.size()));
        BlockState s = sl.getBlockState(b);
        int t = s.getValue(OilFilmBlock.THICKNESS);
        if (t > 1) {
            sl.setBlock(b, s.setValue(OilFilmBlock.THICKNESS, t - 1), 3);
            return;
        }
        List<Direction> faces = new ArrayList<>();
        for (Direction d : Direction.values()) if (MultifaceBlock.hasFace(s, d)) faces.add(d);
        if (faces.size() <= 1) {
            sl.removeBlock(b, false);
        } else {
            Direction d = faces.get(sl.random.nextInt(faces.size()));
            sl.setBlock(b, s.setValue(MultifaceBlock.getFaceProperty(d), false).setValue(OilFilmBlock.THICKNESS, 3), 3);
        }
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        tickCount++;
        if (structureDirty || tickCount % 40 == 0) refreshStructure();

        EngineLayout l = layout;
        if (l == null) {
            status = Status.NO_ENGINE;
            rpm = Math.max(0, rpm - 4);
            capacityPerRpm = 0;
            cylinderCount = 0;
            if (tickCount % 10 == 0) sync();
            return;
        }
        if (tickCount % 10 == 0 || cylOil.length != l.cylinders.size()) refreshCylinders(sl, l);

        if (assembly != null && !SableEngineBridge.ensureConstraints(sl, worldPosition, l, assembly)) {
            // Sable is gone from this level: give the blocks back
            SableEngineBridge.disassemble(sl, assembly);
            assembly = null;
        }

        tickStartAngle = angle;
        simulate(sl, l);
        tickEndAngle = angle;

        PowerOutputBlockEntity out = sl.getBlockEntity(l.outputPos) instanceof PowerOutputBlockEntity o ? o : null;
        if (out != null) {
            if (!worldPosition.equals(out.getControllerPos())) out.link(worldPosition);
            float speed = rpm >= 1 ? (float) Math.min(256, rpm) : 0;
            out.push(settings.reverse ? -speed : speed, speed > 0 ? capacityPerRpm : 0);
            load = out.getLoadFraction();
        } else {
            load = 0;
        }

        if (assembly == null) {
            // No physical crank: push the reaction torque straight into the carrying ship (if any).
            double net = (combustionTorque - frictionTorque) * BigEngineConfig.REACTION_TORQUE_SCALE.get() * 0.05;
            double shake = (1 - balance) * combustionTorque * BigEngineConfig.VIBRATION_SCALE.get() * 0.05;
            if (running && (Math.abs(net) > 1e-4 || shake > 1e-4) && SableEngineBridge.containing(sl, worldPosition) != null)
                SableEngineBridge.pushCarrier(sl, worldPosition, l.crankAxis, settings.reverse ? net : -net, shake);
        }

        if (tickCount % 5 == 0) {
            setChanged();
            sync();
        }
    }

    /** Called from the sub-level physics step (via the crank's link block entity). */
    public void onPhysicsTick(ServerSubLevel subLevel, BlockPos callerPlotPos, double timeStep) {
        if (assembly == null || assembly.crank == null || layout == null || !(level instanceof ServerLevel sl)) return;
        if (!callerPlotPos.equals(assembly.crank.plotAnchor)) return; // one driver per physics step
        double a0 = settings.reverse ? -tickStartAngle : tickStartAngle;
        double a1 = settings.reverse ? -tickEndAngle : tickEndAngle;
        SableEngineBridge.driveServos(sl, layout, assembly, a0, a1);
    }

    private void simulate(ServerLevel sl, EngineLayout l) {
        final double dt = 0.05;
        int n = l.cylinders.size();
        double maxRpm = Math.min(settings.maxRpm, BigEngineConfig.MAX_ENGINE_RPM.get());
        double idle = Math.min(settings.idleRpm, maxRpm - 4);

        // throttle: GUI value or analog redstone, whichever is higher
        double throttle = settings.throttle / 100.0;
        if (settings.redstoneThrottle) throttle = Math.max(throttle, sl.getBestNeighborSignal(worldPosition) / 15.0);

        boolean seized = temperature >= BigEngineConfig.OVERHEAT_TEMP.get();
        if (seized && running) {
            running = false;
            status = Status.OVERHEATED;
            sl.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 0.5f);
        }

        // ---- governor (PI) -> fuel command
        double target = running ? idle + throttle * (maxRpm - idle) : 0;
        if (running) {
            double err = target - rpm;
            integral = Mth.clamp(integral + err * 0.0025, 0, 1);
            fuelCmd = Mth.clamp(err * 0.02 + integral, 0, 1);
            if (rpm > maxRpm * 1.03) fuelCmd = 0; // rev limiter
        } else {
            fuelCmd = 0;
            integral = 0;
        }
        double charge = fuelCmd * settings.fuelLimit / 100.0;

        // ---- what every cylinder can deliver right now
        double lambda = settings.afr / 10.0 / EngineMath.STOICHIOMETRIC_AFR;
        double mixEff = EngineMath.mixtureEfficiency(lambda);
        double kTorque = BigEngineConfig.TORQUE_PER_KPG.get();
        int minFire = BigEngineConfig.MIN_FIRING_RPM.get();
        double tMax = 0;
        knocking = false;
        for (int i = 0; i < n; i++) {
            EngineLayout.Cylinder c = l.cylinders.get(i);
            boolean enabled = i >= EngineSettings.MAX_CYLINDERS || settings.cylEnabled[i];
            double advance = settings.ignitionAdvance + (i < EngineSettings.MAX_CYLINDERS ? settings.cylTrim[i] : 0);
            double energy = 0;
            for (BlockPos p : c.injectors)
                if (sl.getBlockEntity(p) instanceof FuelInjectorBlockEntity inj) energy = Math.max(energy, inj.fuelEnergy());
            int clearIntakes = 0;
            for (BlockPos p : c.intakes) {
                BlockState s = sl.getBlockState(p);
                if (!s.is(ModBlocks.AIR_INTAKE.get())) continue;
                BlockPos front = p.relative(s.getValue(dev.elcapi.bigengine.content.block.EnginePortBlock.FACING));
                if (!sl.getBlockState(front).isCollisionShapeFullBlock(sl, front)) clearIntakes++;
            }
            boolean spark = false;
            for (BlockPos p : c.sparkPlugs) spark |= sl.getBlockState(p).is(ModBlocks.SPARK_PLUG.get());
            int exhausts = 0;
            for (BlockPos p : c.exhausts) if (sl.getBlockState(p).is(ModBlocks.EXHAUST_MANIFOLD.get())) exhausts++;

            double breathing = clearIntakes == 0 ? 0 : Math.min(1.0, 0.5 + 0.5 * clearIntakes / (double) c.boreCells.size());
            double exhaustFactor = exhausts > 0 ? 1.0 : 0.35;
            boolean canFire = enabled && spark && energy > 0 && breathing > 0 && mixEff > 0 && !seized;
            cylCanFire[i] = canFire;
            cylMax[i] = canFire ? kTorque * c.reciprocatingMass * mixEff * EngineMath.timingEfficiency(advance, rpm)
                    * exhaustFactor * energy * breathing : 0;
            tMax += cylMax[i];
            if (canFire && EngineMath.knocks(advance, rpm) && rpm > minFire) knocking = true;
        }

        boolean firing = running && rpm >= minFire;
        double tComb = firing ? charge * tMax : 0;

        // ---- firing events this tick: fuel, oil wear, sound, smoke
        double dAngle = rpm * 6.0 * dt; // rpm/60 rev/s * 360 deg * dt
        double c0 = ((angle % 720) + 720) % 720;
        int fires = 0;
        boolean anyFuelMissing = false;
        for (int i = 0; i < n; i++) {
            cylFired[i] = false;
            if (!firing || !cylCanFire[i] || charge <= 0.01) continue;
            EngineLayout.Cylinder c = l.cylinders.get(i);
            double fireAt = EngineMath.tdcAngle(c.phaseDeg) + (i < EngineSettings.MAX_CYLINDERS && settings.cylSecondTurn[i] ? 360 : 0);
            double rel = ((fireAt - c0) % 720 + 720) % 720;
            if (rel >= dAngle) continue;
            // fire!
            double need = BigEngineConfig.FUEL_PER_FIRING.get() * charge * c.boreCells.size() / lambda;
            double got = 0;
            for (BlockPos p : c.injectors) {
                if (got >= need) break;
                if (sl.getBlockEntity(p) instanceof FuelInjectorBlockEntity inj) got += inj.burn(need - got);
            }
            if (got < need * 0.5) {
                anyFuelMissing = true;
                cylCanFire[i] = false;
                tComb -= charge * cylMax[i];
                continue;
            }
            cylFired[i] = true;
            fires++;
            if (sl.random.nextDouble() < BigEngineConfig.OIL_WEAR_CHANCE.get() * c.boreCells.size()) wearOil(sl, c);
            exhaustPuff(sl, c, lambda);
        }
        tComb = Math.max(0, tComb);
        if (fires > 0) {
            quietTicks = 0;
            float pitch = (float) Mth.clamp(0.5 + rpm / 200.0, 0.5, 2.0);
            sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 0.35f, pitch);
            if (knocking && sl.random.nextInt(4) == 0)
                sl.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.15f, 1.8f);
        } else {
            quietTicks++;
        }

        // ---- friction (worse without oil)
        double mass = l.totalMovingMass();
        double dryness = 1 - avgOil;
        double frictionMul = 1 + 3 * dryness * dryness;
        double tFric = (BigEngineConfig.FRICTION_PER_KPG.get() * mass + BigEngineConfig.VISCOUS_FRICTION.get() * rpm * mass) * frictionMul;
        if (rpm < 0.5) tFric = 0;

        // ---- what we offer to Create, and what Create takes back
        double available = Math.max(0, tMax * settings.fuelLimit / 100.0 - tFric);
        capacityPerRpm = firing ? (float) available : 0;
        double tLoad = Math.min(load, 1.5) * capacityPerRpm;

        // ---- integrate RPM
        double net = tComb - tFric - tLoad;
        rpm += net / l.inertia * BigEngineConfig.RPM_PER_TORQUE.get() * dt;
        if (starterTicks > 0) {
            starterTicks--;
            double starterRpm = BigEngineConfig.STARTER_RPM.get();
            if (rpm < starterRpm) rpm = Math.min(starterRpm, rpm + 3.0);
            if (rpm > idle * 0.8 && fires > 0) starterTicks = 0;
            if (tickCount % 4 == 0) sl.playSound(null, worldPosition, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.08f, 1.6f);
        }
        if (!running) rpm -= Math.max(0.5, rpm * 0.02); // engine braking while coasting down
        rpm = Mth.clamp(rpm, 0, BigEngineConfig.MAX_ENGINE_RPM.get());
        angle += rpm * 6.0 * dt;
        if (Math.abs(angle) > 7.2e6) angle %= 720; // keep precision, multiple of the cycle

        combustionTorque = (float) tComb;
        frictionTorque = (float) tFric;

        // ---- stall detection
        if (running && starterTicks == 0 && (rpm < minFire || quietTicks > 60)) {
            running = false;
            status = anyFuelMissing || fuelStored <= 0 ? Status.NO_FUEL : Status.STALLED;
        }

        // ---- temperature
        double heat = 0.004 * tComb * rpm / 60.0 * (1 + 2 * dryness) + (knocking ? 0.4 : 0) + tFric * rpm * 0.00005 * frictionMul;
        double cool = (temperature - 20) * 0.01;
        temperature = Math.max(20, temperature + heat - cool);

        // ---- smoothness: how evenly spread the firings are over the 720 deg cycle
        balance = computeBalance(l);

        if (running) status = starterTicks > 0 ? Status.CRANKING : Status.RUNNING;
        else if (status == Status.RUNNING || status == Status.CRANKING || status == Status.NO_ENGINE) status = Status.STOPPED;
    }

    private float computeBalance(EngineLayout l) {
        List<Double> fireAngles = new ArrayList<>();
        for (int i = 0; i < l.cylinders.size(); i++) {
            if (i < cylCanFire.length && !cylCanFire[i]) continue;
            double a = EngineMath.tdcAngle(l.cylinders.get(i).phaseDeg) + (i < EngineSettings.MAX_CYLINDERS && settings.cylSecondTurn[i] ? 360 : 0);
            fireAngles.add(a % 720);
        }
        int k = fireAngles.size();
        if (k <= 1) return k == 1 ? 0.5f : 0f;
        Collections.sort(fireAngles);
        double ideal = 720.0 / k, worst = 0;
        for (int i = 0; i < k; i++) {
            double gap = (i + 1 < k ? fireAngles.get(i + 1) : fireAngles.get(0) + 720) - fireAngles.get(i);
            worst = Math.max(worst, Math.abs(gap - ideal) / ideal);
        }
        return (float) Mth.clamp(1 - worst / (k - 1.0), 0, 1);
    }

    private void exhaustPuff(ServerLevel sl, EngineLayout.Cylinder c, double lambda) {
        if (sl.random.nextInt(2) != 0) return;
        for (BlockPos p : c.exhausts) {
            BlockState s = sl.getBlockState(p);
            if (!s.is(ModBlocks.EXHAUST_MANIFOLD.get())) continue;
            Direction f = s.getValue(dev.elcapi.bigengine.content.block.EnginePortBlock.FACING);
            double x = p.getX() + 0.5 + f.getStepX() * 0.6, y = p.getY() + 0.5 + f.getStepY() * 0.6, z = p.getZ() + 0.5 + f.getStepZ() * 0.6;
            boolean rich = lambda < 0.85;
            sl.sendParticles(rich ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, rich ? 3 : 2,
                    0.05, 0.05, 0.05, 0.02);
        }
    }

    // ------------------------------------------------------------------ goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        String p = "goggles." + BigEngine.MOD_ID + ".";
        tooltip.add(Component.literal("    ").append(Component.translatable(p + "title")).withStyle(ChatFormatting.GOLD));
        tooltip.add(line(Component.translatable("status." + BigEngine.MOD_ID + "." + status.name().toLowerCase(Locale.ROOT))
                .withStyle(status == Status.RUNNING ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        if (structureError != null) tooltip.add(line(structureError.copy().withStyle(ChatFormatting.RED)));
        tooltip.add(line(Component.translatable(p + "rpm", String.format(Locale.ROOT, "%.0f", rpm))));
        tooltip.add(line(Component.translatable(p + "capacity", String.format(Locale.ROOT, "%.0f", capacityPerRpm * Math.min(256, rpm)))));
        tooltip.add(line(Component.translatable(p + "temperature", String.format(Locale.ROOT, "%.0f", temperature))));
        tooltip.add(line(Component.translatable(p + "oil", String.format(Locale.ROOT, "%.0f", avgOil * 100))));
        tooltip.add(line(Component.translatable(p + "fuel", String.format(Locale.ROOT, "%.0f", fuelStored))));
        return true;
    }

    private static MutableComponent line(Component c) {
        return Component.literal("    ").append(c).withStyle(ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ save / sync

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Settings", settings.save());
        if (layout != null) tag.put("Layout", layout.save());
        if (assembly != null) tag.put("Assembly", assembly.save());
        tag.putBoolean("Running", running);
        tag.putDouble("Rpm", rpm);
        tag.putDouble("Angle", angle);
        tag.putDouble("Temp", temperature);
        tag.putInt("TimedFor", timedForCylinders);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings.load(tag.getCompound("Settings"));
        layout = tag.contains("Layout") ? EngineLayout.load(tag.getCompound("Layout")) : null;
        assembly = tag.contains("Assembly") ? PhysicalAssembly.load(tag.getCompound("Assembly")) : null;
        running = tag.getBoolean("Running");
        rpm = tag.getDouble("Rpm");
        angle = tag.getDouble("Angle");
        temperature = tag.contains("Temp") ? tag.getDouble("Temp") : 20;
        timedForCylinders = tag.contains("TimedFor") ? tag.getInt("TimedFor") : -1;
    }

    /** Lightweight data for the client screen / goggles. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.put("Settings", settings.save());
        t.putInt("Status", status.ordinal());
        t.putBoolean("Running", running);
        t.putBoolean("Assembled", assembly != null);
        t.putDouble("Rpm", rpm);
        t.putDouble("Temp", temperature);
        t.putFloat("Cap", capacityPerRpm);
        t.putFloat("Comb", combustionTorque);
        t.putFloat("Fric", frictionTorque);
        t.putFloat("Load", load);
        t.putFloat("Oil", avgOil);
        t.putFloat("Balance", balance);
        t.putFloat("Fuel", fuelStored);
        t.putBoolean("Knock", knocking);
        t.putIntArray("Phases", cylPhases.clone());
        int[] oil = new int[cylOil.length];
        for (int i = 0; i < oil.length; i++) oil[i] = (int) Math.round(cylOil[i] * 100);
        t.putIntArray("CylOil", oil);
        if (structureError != null)
            t.putString("Error", Component.Serializer.toJson(structureError, registries));
        return t;
    }

    @Override
    public void handleUpdateTag(CompoundTag t, HolderLookup.Provider registries) {
        readClient(t, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, (be, reg) -> ((EngineControllerBlockEntity) be).getUpdateTag(reg));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) readClient(pkt.getTag(), registries);
    }

    private void readClient(CompoundTag t, HolderLookup.Provider registries) {
        settings.load(t.getCompound("Settings"));
        status = Status.values()[Mth.clamp(t.getInt("Status"), 0, Status.values().length - 1)];
        running = t.getBoolean("Running");
        clientAssembled = t.getBoolean("Assembled");
        rpm = t.getDouble("Rpm");
        temperature = t.getDouble("Temp");
        capacityPerRpm = t.getFloat("Cap");
        combustionTorque = t.getFloat("Comb");
        frictionTorque = t.getFloat("Fric");
        load = t.getFloat("Load");
        avgOil = t.getFloat("Oil");
        balance = t.getFloat("Balance");
        fuelStored = t.getFloat("Fuel");
        knocking = t.getBoolean("Knock");
        cylPhases = t.getIntArray("Phases");
        cylinderCount = cylPhases.length;
        int[] oil = t.getIntArray("CylOil");
        cylOil = new double[oil.length];
        for (int i = 0; i < oil.length; i++) cylOil[i] = oil[i] / 100.0;
        structureError = t.contains("Error") ? Component.Serializer.fromJson(t.getString("Error"), registries) : null;
    }
}
