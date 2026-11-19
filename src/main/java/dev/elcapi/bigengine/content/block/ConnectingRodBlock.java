package dev.elcapi.bigengine.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Connecting rod (biela). FACING points from the crank throw towards the piston. */
public class ConnectingRodBlock extends EngineDirectionalBlock {
    private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape Y = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape Z = Block.box(5, 5, 0, 11, 11, 16);

    public ConnectingRodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }
}
