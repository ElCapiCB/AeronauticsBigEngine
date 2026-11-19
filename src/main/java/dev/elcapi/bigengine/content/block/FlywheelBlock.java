package dev.elcapi.bigengine.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Heavy disc on the crank line. Adds rotational inertia (smoother running, slower revving). */
public class FlywheelBlock extends CrankshaftBlock {
    private static final VoxelShape X = Block.box(4, 0, 0, 12, 16, 16);
    private static final VoxelShape Y = Block.box(0, 4, 0, 16, 12, 16);
    private static final VoxelShape Z = Block.box(0, 0, 4, 16, 16, 12);

    public FlywheelBlock(Properties properties) {
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
}
