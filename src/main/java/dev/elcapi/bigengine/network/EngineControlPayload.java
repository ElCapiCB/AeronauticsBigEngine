package dev.elcapi.bigengine.network;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.blockentity.EngineControllerBlockEntity;
import dev.ryanhcode.sable.Sable;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

/**
 * Client -> server: change a setting ({@code action == 0}) or press a button on the Engine Control Unit.
 */
public record EngineControlPayload(BlockPos pos, int action, int key, int value) implements CustomPacketPayload {
    public static final int SET = 0;

    public static final Type<EngineControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BigEngine.MOD_ID, "engine_control"));

    public static final StreamCodec<ByteBuf, EngineControlPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EngineControlPayload::pos,
            ByteBufCodecs.VAR_INT, EngineControlPayload::action,
            ByteBufCodecs.VAR_INT, EngineControlPayload::key,
            ByteBufCodecs.INT, EngineControlPayload::value,
            EngineControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EngineControlPayload msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!player.level().isLoaded(msg.pos)) return;
        // The controller may sit on a ship (Sable sub-level): measure the distance in world space.
        Vector3d world = Sable.HELPER.projectOutOfSubLevel(player.level(),
                new Vector3d(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5), new Vector3d());
        if (player.distanceToSqr(world.x, world.y, world.z) > 12 * 12) return;
        if (!(player.level().getBlockEntity(msg.pos) instanceof EngineControllerBlockEntity ecu)) return;
        if (msg.action == SET) ecu.handleSetting(msg.key, msg.value);
        else ecu.handleAction(player, msg.action);
    }
}
