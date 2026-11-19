package dev.elcapi.bigengine.content.block;

import dev.elcapi.bigengine.content.blockentity.EngineLinkBlockEntity;
import dev.elcapi.bigengine.registry.ModBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Crank throw: a crankshaft section with an offset crank pin. One connecting rod / cylinder sits on each throw.
 * PHASE (0-3) is the crank pin angle in 90 degree steps; use a Create wrench to change it.
 */
public class CrankThrowBlock extends CrankshaftBlock implements IBE<EngineLinkBlockEntity>, IWrenchable {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);

    public CrankThrowBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PHASE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockState next = state.setValue(PHASE, (state.getValue(PHASE) + 1) % 4);
        level.setBlock(context.getClickedPos(), next, 3);
        IWrenchable.playRotateSound(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (player != null && !level.isClientSide)
            player.displayClientMessage(Component.translatable("message.aeronauticsbigengine.crank_phase", next.getValue(PHASE) * 90), true);
        EnginePart.notifyStructureChanged(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<EngineLinkBlockEntity> getBlockEntityClass() {
        return EngineLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends EngineLinkBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ENGINE_LINK.get();
    }
}
