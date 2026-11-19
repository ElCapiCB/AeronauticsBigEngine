package dev.elcapi.bigengine.content.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.elcapi.bigengine.content.blockentity.PowerOutputBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Power take-off: the end of the crank line where engine power enters a Create kinetic network.
 * Shafts connect on both ends of its axis.
 */
public class PowerOutputBlock extends RotatedPillarKineticBlock implements IBE<PowerOutputBlockEntity> {
    public PowerOutputBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(state.getBlock())) EnginePart.notifyStructureChanged(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean changed = !newState.is(state.getBlock());
        super.onRemove(state, level, pos, newState, moved);
        if (changed) EnginePart.notifyStructureChanged(level, pos);
    }

    @Override
    public Class<PowerOutputBlockEntity> getBlockEntityClass() {
        return PowerOutputBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PowerOutputBlockEntity> getBlockEntityType() {
        return ModBlockEntities.POWER_OUTPUT.get();
    }
}
