package dev.elcapi.bigengine.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Something bolted onto a cylinder head face: spark plug, air intake, exhaust or fuel injector. */
public class EnginePortBlock extends EngineDirectionalBlock {
    public enum PortType { SPARK_PLUG, AIR_INTAKE, EXHAUST, FUEL_INJECTOR }

    private final PortType type;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public EnginePortBlock(Properties properties, PortType type) {
        super(properties);
        this.type = type;
        double w = switch (type) {
            case SPARK_PLUG -> 4;
            case FUEL_INJECTOR -> 5;
            default -> 6;
        };
        double len = type == PortType.SPARK_PLUG ? 8 : 12;
        for (Direction d : Direction.values()) shapes.put(d, shape(d, 8 - w, 8 + w, len));
    }

    /** Box attached to the face opposite of {@code facing}, sticking out {@code len} pixels. */
    private static VoxelShape shape(Direction facing, double lo, double hi, double len) {
        return switch (facing) {
            case UP -> Block.box(lo, 0, lo, hi, len, hi);
            case DOWN -> Block.box(lo, 16 - len, lo, hi, 16, hi);
            case NORTH -> Block.box(lo, lo, 16 - len, hi, hi, 16);
            case SOUTH -> Block.box(lo, lo, 0, hi, hi, len);
            case WEST -> Block.box(16 - len, lo, lo, 16, hi, hi);
            case EAST -> Block.box(0, lo, lo, len, hi, hi);
        };
    }

    public PortType getPortType() {
        return type;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }
}
