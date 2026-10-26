package net.camacraft.giantletters;

/**
 * Cuts a region of one letter into the squares that each show one glyph: from the bottom-left corner, row by row,
 * every uncovered cell takes the largest square anchored at it that lies inside the region, up to {@code maxSize}.
 * A filled 3x3 is one square; two 3x3 side by side (the two L's of HELLO at that size) are two; a line is singles;
 * a 3x3 with a hole is nine singles. Plain Java, no Minecraft types, so it has unit tests.
 */
public final class SquareCut {
    /** A cell's place: the square's size in cells, and the cell's column and row in it from the bottom-left. */
    public record Tile(int size, int column, int row) {
        public static final Tile SINGLE = new Tile(1, 0, 0);
    }

    private SquareCut() {
    }

    /**
     * @param cells   {@code cells[row][column]}, row 0 the bottom row; true where the region has a letter block
     * @param maxSize the biggest square allowed
     * @return the tile of every cell ({@code null} where {@code cells} is false)
     */
    public static Tile[][] cut(boolean[][] cells, int maxSize) {
        int h = cells.length;
        int w = h == 0 ? 0 : cells[0].length;
        Tile[][] out = new Tile[h][w];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (!cells[j][i] || out[j][i] != null) continue;
                int k = 1;
                while (k < maxSize && fits(cells, out, i, j, k + 1)) k++;
                for (int dj = 0; dj < k; dj++) {
                    for (int di = 0; di < k; di++) {
                        out[j + dj][i + di] = new Tile(k, di, dj);
                    }
                }
            }
        }
        return out;
    }

    /** Whether the square of size {@code k} anchored at ({@code i}, {@code j}) is all region and uncovered: only its new outer row and column need looking at. */
    private static boolean fits(boolean[][] cells, Tile[][] out, int i, int j, int k) {
        int h = cells.length, w = cells[0].length;
        if (i + k > w || j + k > h) return false;
        int top = j + k - 1, rightmost = i + k - 1;
        for (int x = i; x <= rightmost; x++) {
            if (!cells[top][x] || out[top][x] != null) return false;
        }
        for (int y = j; y <= top; y++) {
            if (!cells[y][rightmost] || out[y][rightmost] != null) return false;
        }
        return true;
    }
}
