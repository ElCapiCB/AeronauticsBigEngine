package dev.elcapi.bigengine.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A film of engine oil clinging to walls, like a vine / glow lichen but made of oil.
 * Coat the inner walls of a cylinder bore so the piston slides on oil. THICKNESS (1-3) wears down as the engine runs.
 */
public class OilFilmBlock extends MultifaceBlock {
    public static final MapCodec<OilFilmBlock> CODEC = simpleCodec(OilFilmBlock::new);
    public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 1, 3);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public OilFilmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THICKNESS, 3));
    }

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(THICKNESS);
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Number of faces carrying oil. */
    public static int countFaces(BlockState state) {
        int n = 0;
        for (Direction d : Direction.values()) if (hasFace(state, d)) n++;
        return n;
    }
}
