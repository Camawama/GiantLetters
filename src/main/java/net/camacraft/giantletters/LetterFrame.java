package net.camacraft.giantletters;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which way a letter reads: {@code right} and {@code up} along its face, {@code normal} toward whoever reads it
 * (out of the wall, up from a floor, down from a ceiling). {@code right x up = normal}, so a quad drawn
 * bottom-left, bottom-right, top-right, top-left faces the reader.
 * <p>
 * On a wall the letter stands upright facing out ({@code FACING}). On a floor or ceiling {@code FACING} points at
 * the player who placed it, as on a button: a floor letter's top points away from them (they read it looking
 * down), a ceiling letter's top points toward them (they read it with their head back).
 */
public record LetterFrame(Direction right, Direction up, Direction normal) {
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

    static {
        PLATES.put(Direction.NORTH, Shapes.box(0, 0, 15 / 16.0, 1, 1, 1));
        PLATES.put(Direction.SOUTH, Shapes.box(0, 0, 0, 1, 1, 1 / 16.0));
        PLATES.put(Direction.EAST, Shapes.box(0, 0, 0, 1 / 16.0, 1, 1));
        PLATES.put(Direction.WEST, Shapes.box(15 / 16.0, 0, 0, 1, 1, 1));
        PLATES.put(Direction.UP, Shapes.box(0, 0, 0, 1, 1 / 16.0, 1));
        PLATES.put(Direction.DOWN, Shapes.box(0, 15 / 16.0, 0, 1, 1, 1));
    }

    public static LetterFrame of(BlockState state) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        return switch (face) {
            case WALL -> new LetterFrame(facing.getCounterClockWise(), Direction.UP, facing);
            case FLOOR -> new LetterFrame(facing.getOpposite().getClockWise(), facing.getOpposite(), Direction.UP);
            case CEILING -> new LetterFrame(facing.getOpposite().getClockWise(), facing, Direction.DOWN);
        };
    }

    /** The one-pixel plate against the face the letter hangs on (its outline, nothing collides with it). */
    public VoxelShape plate() {
        return PLATES.get(normal);
    }

    /**
     * A point of the letter's face in the block's own space: {@code x} right and {@code y} up along the face, in
     * blocks from its bottom-left corner, {@code depth} blocks off the wall.
     */
    public Vector3f point(float x, float y, float depth) {
        Vector3f p = new Vector3f();
        set(p, right, x);
        set(p, up, y);
        set(p, normal, depth);
        return p;
    }

    /** How far along the face a point of the block's own space lies in the letter's right direction, 0 to 1. */
    public double across(Vec3 local) {
        return along(local, right);
    }

    /** How far up the face a point of the block's own space lies in the letter's up direction, 0 to 1. */
    public double upward(Vec3 local) {
        return along(local, up);
    }

    private static double along(Vec3 local, Direction axis) {
        double v = switch (axis.getAxis()) {
            case X -> local.x;
            case Y -> local.y;
            case Z -> local.z;
        };
        return axis.getAxisDirection() == Direction.AxisDirection.POSITIVE ? v : 1.0 - v;
    }

    private static void set(Vector3f p, Direction axis, float along) {
        float v = axis.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1.0F - along;
        switch (axis.getAxis()) {
            case X -> p.x = v;
            case Y -> p.y = v;
            case Z -> p.z = v;
        }
    }
}
