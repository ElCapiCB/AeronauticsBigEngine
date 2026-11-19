package dev.elcapi.bigengine.content.blockentity;

import dev.elcapi.bigengine.registry.ModBlockEntities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lives on crank throws and pistons. Once the engine is assembled these blocks sit inside their own Sable
 * sub-level; every physics sub-step this block entity asks the controller to update the servo targets.
 */
public class EngineLinkBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    @Nullable private BlockPos controllerPos;

    public EngineLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE_LINK.get(), pos, state);
    }

    public void setController(@Nullable BlockPos pos) {
        this.controllerPos = pos;
        setChanged();
    }

    @Nullable
    private EngineControllerBlockEntity controller() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof EngineControllerBlockEntity ecu ? ecu : null;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        EngineControllerBlockEntity ecu = controller();
        if (ecu != null) ecu.onPhysicsTick(subLevel, worldPosition, timeStep);
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (controllerPos == null || level == null) return null;
        // Keep the engine's moving parts loaded together with whatever carries the controller (e.g. a ship).
        SubLevel carrier = Sable.HELPER.getContaining(level, controllerPos);
        return carrier == null ? null : List.of(carrier);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
