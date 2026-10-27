package net.camacraft.giantletters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How letter blocks join into bigger letters. Blocks showing the same character on the same surface (same
 * {@code FACE} and {@code FACING}), touching side to side along that surface and not placed {@code single}, form
 * a region; the region is cut into squares ({@link SquareCut}) and each square shows one glyph. The character is
 * the block entity's ({@link LetterBlockEntity#character()}: a renamed letter joins other letters renamed the
 * same way, not its block's own letter). Both sides use it: the client to draw, the server to dye a whole letter at
 * once. A region over {@value #CELL_BUDGET} blocks never merges.
 */
public final class LetterGroups {
    /** A connected region bigger than this is left as single letters (and the walk stops there). */
    public static final int CELL_BUDGET = 4096;

    private LetterGroups() {
    }

    /** The character shown at {@code pos} (whose state is {@code state}): the block entity's, or the block's own. */
    public static int characterAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof LetterBlockEntity be) return be.character();
        return state.getBlock() instanceof LetterBlock block ? block.glyph().ch() : 0;
    }

    /** Whether the block at {@code pos} joins a region of blocks like {@code origin} showing {@code character}: a letter of the same character, same surface, not single. */
    public static boolean joins(BlockGetter level, BlockState origin, int character, BlockPos pos, BlockState other) {
        return other.getBlock() instanceof LetterBlock
                && !other.getValue(LetterBlock.SINGLE)
                && other.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == origin.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)
                && other.getValue(HorizontalDirectionalBlock.FACING) == origin.getValue(HorizontalDirectionalBlock.FACING)
                && characterAt(level, pos, other) == character;
    }

    /** The tile of every block in the region of the letter block at {@code pos} (just {@code pos} for a single or a non-letter). */
    public static Map<BlockPos, SquareCut.Tile> tiles(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Map<BlockPos, SquareCut.Tile> out = new HashMap<>();
        if (!(state.getBlock() instanceof LetterBlock)) return out;
        if (state.getValue(LetterBlock.SINGLE)) {
            out.put(pos, SquareCut.Tile.SINGLE);
            return out;
        }
        LetterFrame frame = LetterFrame.of(state);
        Region region = walk(level, pos, state, frame);
        if (region.overBudget) {
            for (BlockPos p : region.cells.values()) out.put(p, SquareCut.Tile.SINGLE);
            return out;
        }
        int w = region.maxI - region.minI + 1, h = region.maxJ - region.minJ + 1;
        boolean[][] cells = new boolean[h][w];
        for (long key : region.cells.keySet()) cells[j(key) - region.minJ][i(key) - region.minI] = true;
        SquareCut.Tile[][] tiles = SquareCut.cut(cells, Config.maxLetterSize());
        for (Map.Entry<Long, BlockPos> e : region.cells.entrySet()) {
            out.put(e.getValue(), tiles[j(e.getKey()) - region.minJ][i(e.getKey()) - region.minI]);
        }
        return out;
    }

    /** The blocks of the one letter (square) the letter block at {@code pos} is part of; just {@code pos} when it stands alone. */
    public static List<BlockPos> square(BlockGetter level, BlockPos pos) {
        Map<BlockPos, SquareCut.Tile> tiles = tiles(level, pos);
        SquareCut.Tile mine = tiles.get(pos);
        List<BlockPos> out = new ArrayList<>();
        if (mine == null) {
            out.add(pos);
            return out;
        }
        BlockState state = level.getBlockState(pos);
        LetterFrame frame = LetterFrame.of(state);
        BlockPos corner = pos.relative(frame.right(), -mine.column()).relative(frame.up(), -mine.row());
        for (int j = 0; j < mine.size(); j++) {
            for (int i = 0; i < mine.size(); i++) {
                BlockPos p = corner.relative(frame.right(), i).relative(frame.up(), j);
                SquareCut.Tile t = tiles.get(p);
                if (t != null && t.size() == mine.size() && t.column() == i && t.row() == j) out.add(p);
            }
        }
        return out;
    }

    /** Client: works out the tiles of the whole region round {@code pos} and stores them in its block entities. */
    public static void retile(BlockGetter level, BlockPos pos) {
        for (Map.Entry<BlockPos, SquareCut.Tile> e : tiles(level, pos).entrySet()) {
            if (level.getBlockEntity(e.getKey()) instanceof LetterBlockEntity be) be.setTile(e.getValue());
        }
    }

    /**
     * Client: a letter block appeared, disappeared or changed its character at {@code pos} (with {@code state}):
     * the regions it touches along its surface, and its own, are stale.
     */
    public static void invalidateAround(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof LetterBlock)) return;
        LetterFrame frame = LetterFrame.of(state);
        markRegion(level, pos);
        for (Direction d : new Direction[]{frame.right(), frame.right().getOpposite(), frame.up(), frame.up().getOpposite()}) {
            markRegion(level, pos.relative(d));
        }
    }

    private static void markRegion(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LetterBlock)) return;
        if (state.getValue(LetterBlock.SINGLE)) {
            if (level.getBlockEntity(pos) instanceof LetterBlockEntity be) be.markTileDirty();
            return;
        }
        Region region = walk(level, pos, state, LetterFrame.of(state));
        for (BlockPos p : region.cells.values()) {
            if (level.getBlockEntity(p) instanceof LetterBlockEntity be) be.markTileDirty();
        }
    }

    // ---- the walk ------------------------------------------------------------------------------------------------

    private static final class Region {
        final Map<Long, BlockPos> cells = new HashMap<>();
        int minI, maxI, minJ, maxJ;
        boolean overBudget;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xffffffffL);
    }

    private static int i(long key) {
        return (int) (key >> 32);
    }

    private static int j(long key) {
        return (int) key;
    }

    /** Flood fill along the surface from {@code origin} over blocks that {@link #joins} its state and character. */
    private static Region walk(BlockGetter level, BlockPos origin, BlockState state, LetterFrame frame) {
        Region region = new Region();
        int character = characterAt(level, origin, state);
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        region.cells.put(key(0, 0), origin);
        queue.add(new long[]{0, 0});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long[] c = queue.poll();
            int ci = (int) c[0], cj = (int) c[1];
            for (int[] s : steps) {
                int ni = ci + s[0], nj = cj + s[1];
                long k = key(ni, nj);
                if (region.cells.containsKey(k)) continue;
                BlockPos p = origin.relative(frame.right(), ni).relative(frame.up(), nj);
                if (!joins(level, state, character, p, level.getBlockState(p))) continue;
                if (region.cells.size() >= CELL_BUDGET) {
                    region.overBudget = true;
                    return region;
                }
                region.cells.put(k, p);
                queue.add(new long[]{ni, nj});
                region.minI = Math.min(region.minI, ni);
                region.maxI = Math.max(region.maxI, ni);
                region.minJ = Math.min(region.minJ, nj);
                region.maxJ = Math.max(region.maxJ, nj);
            }
        }
        return region;
    }
}
