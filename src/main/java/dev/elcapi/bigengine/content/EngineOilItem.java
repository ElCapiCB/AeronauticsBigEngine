package dev.elcapi.bigengine.content;

import dev.elcapi.bigengine.content.block.OilFilmBlock;
import dev.elcapi.bigengine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Oil can. Use it on a wall face to paint a film of oil on it (stacks up to 3 layers).
 * Using it on an existing film thickens it.
 */
public class EngineOilItem extends Item {
    public EngineOilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        OilFilmBlock oil = ModBlocks.OIL_FILM.get();

        BlockPos target;
        Direction attachTo;
        if (clickedState.is(oil)) {
            // Thicken the existing film.
            if (clickedState.getValue(OilFilmBlock.THICKNESS) >= 3) return InteractionResult.FAIL;
            if (!level.isClientSide) {
                level.setBlock(clicked, clickedState.setValue(OilFilmBlock.THICKNESS, clickedState.getValue(OilFilmBlock.THICKNESS) + 1), 3);
                consume(ctx, level, clicked);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        target = clicked.relative(ctx.getClickedFace());
        attachTo = ctx.getClickedFace().getOpposite();
        if (!clickedState.isFaceSturdy(level, clicked, ctx.getClickedFace())) return InteractionResult.FAIL;

        BlockState existing = level.getBlockState(target);
        BlockState newState;
        if (existing.is(oil)) {
            if (MultifaceBlock.hasFace(existing, attachTo)) {
                int t = existing.getValue(OilFilmBlock.THICKNESS);
                if (t >= 3) return InteractionResult.FAIL;
                newState = existing.setValue(OilFilmBlock.THICKNESS, t + 1);
            } else {
                newState = existing.setValue(MultifaceBlock.getFaceProperty(attachTo), true);
            }
        } else if (existing.canBeReplaced()) {
            newState = oil.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(attachTo), true).setValue(OilFilmBlock.THICKNESS, 1);
        } else {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.setBlock(target, newState, 3);
            consume(ctx, level, target);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void consume(UseOnContext ctx, Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
        Player player = ctx.getPlayer();
        if (player != null && !player.getAbilities().instabuild)
            ctx.getItemInHand().hurtAndBreak(1, player, ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}
