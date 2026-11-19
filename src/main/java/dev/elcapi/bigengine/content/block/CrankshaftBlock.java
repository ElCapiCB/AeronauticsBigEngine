package dev.elcapi.bigengine.content.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Plain crankshaft journal. Part of the rotating crank group. */
public class CrankshaftBlock extends RotatedPillarBlock implements IWrenchable {
    private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape Y = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape Z = Block.box(5, 5, 0, 11, 11, 16);

    public CrankshaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(state.getBlock())) EnginePart.notifyStructureChanged(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(state.getBlock())) EnginePart.notifyStructureChanged(level, pos);
    }
}
