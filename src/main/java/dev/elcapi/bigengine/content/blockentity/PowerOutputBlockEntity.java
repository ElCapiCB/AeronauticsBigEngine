package dev.elcapi.bigengine.content.blockentity;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Create kinetic source driven by the engine. Speed = engine RPM, stress capacity per RPM = available torque.
 * Create multiplies capacity by speed, so total SU = torque x RPM, i.e. mechanical power.
 */
public class PowerOutputBlockEntity extends GeneratingKineticBlockEntity {
    @Nullable private BlockPos controllerPos;
    private float targetSpeed;
    private float targetCapacity;
    private int updateCooldown;

    public PowerOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public PowerOutputBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.POWER_OUTPUT.get(), pos, state);
    }

    public void link(@Nullable BlockPos controller) {
        if (!java.util.Objects.equals(controller, controllerPos)) {
            controllerPos = controller;
            if (controller == null) push(0, 0);
            setChanged();
        }
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /** Called by the controller every tick. Rate-limited so the Create network is not re-propagated constantly. */
    public void push(float speed, float capacityPerRpm) {
        float oldSpeed = targetSpeed, oldCap = targetCapacity;
        targetSpeed = speed;
        targetCapacity = capacityPerRpm;
        if (updateCooldown > 0) return;
        boolean speedChanged = Math.round(oldSpeed) != Math.round(speed) || Math.round(getGeneratedSpeed()) != Math.round(speed);
        boolean capChanged = Math.abs(capacityPerRpm - lastCapacityProvided) > Math.max(0.5f, lastCapacityProvided * 0.04f);
        if (speedChanged || capChanged || (oldCap == 0) != (capacityPerRpm == 0)) {
            updateGeneratedRotation();
            updateCooldown = 4;
        }
    }

    /** Fraction of the network's capacity currently used (0..1+, above 1 = overstressed). */
    public float getLoadFraction() {
        if (capacity <= 0) return isOverStressed() ? 1.5f : 0f;
        return stress / capacity;
    }

    @Override
    public void tick() {
        super.tick();
        if (updateCooldown > 0) updateCooldown--;
        if (level != null && !level.isClientSide && controllerPos != null && level.getGameTime() % 20 == 0) {
            if (!(level.getBlockEntity(controllerPos) instanceof EngineControllerBlockEntity)) link(null);
        }
    }

    @Override
    public float getGeneratedSpeed() {
        if (controllerPos == null) return 0;
        return Math.round(targetSpeed);
    }

    @Override
    public float calculateAddedStressCapacity() {
        float cap = Math.max(0, targetCapacity);
        this.lastCapacityProvided = cap;
        return cap;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (controllerPos != null) compound.putLong("Controller", controllerPos.asLong());
        compound.putFloat("TargetSpeed", targetSpeed);
        compound.putFloat("TargetCapacity", targetCapacity);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        controllerPos = compound.contains("Controller") ? BlockPos.of(compound.getLong("Controller")) : null;
        targetSpeed = compound.getFloat("TargetSpeed");
        targetCapacity = compound.getFloat("TargetCapacity");
    }
}
