package net.camacraft.giantletters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SquareCutTest {
    /** Rows written top to bottom as a picture; {@code #} is a letter block. */
    private static boolean[][] grid(String... rowsTopDown) {
        int h = rowsTopDown.length, w = rowsTopDown[0].length();
        boolean[][] cells = new boolean[h][w];
        for (int j = 0; j < h; j++) {
            String row = rowsTopDown[h - 1 - j];
            for (int i = 0; i < w; i++) cells[j][i] = row.charAt(i) == '#';
        }
        return cells;
    }

    private static String sizes(SquareCut.Tile[][] tiles) {
        StringBuilder b = new StringBuilder();
        for (int j = tiles.length - 1; j >= 0; j--) {
            for (SquareCut.Tile t : tiles[j]) b.append(t == null ? '.' : (char) ('0' + t.size()));
            b.append('/');
        }
        return b.toString();
    }

    @Test
    void aFullThreeByThreeIsOneLetter() {
        SquareCut.Tile[][] t = SquareCut.cut(grid("###", "###", "###"), 32);
        assertEquals("333/333/333/", sizes(t));
        assertEquals(new SquareCut.Tile(3, 0, 0), t[0][0]);
        assertEquals(new SquareCut.Tile(3, 2, 2), t[2][2]);
        assertEquals(new SquareCut.Tile(3, 1, 0), t[0][1]);
    }

    @Test
    void twoThreeByThreesSideBySideAreTwoLetters() {
        SquareCut.Tile[][] t = SquareCut.cut(grid("######", "######", "######"), 32);
        assertEquals("333333/333333/333333/", sizes(t));
        assertEquals(new SquareCut.Tile(3, 0, 0), t[0][3]);
        assertEquals(new SquareCut.Tile(3, 2, 2), t[2][5]);
    }

    @Test
    void aLineIsSingles() {
        assertEquals("#####/".replace('#', '1'), sizes(SquareCut.cut(grid("#####"), 32)));
        assertEquals("1/1/1/", sizes(SquareCut.cut(grid("#", "#", "#"), 32)));
    }

    @Test
    void aHoleBreaksTheSquare() {
        SquareCut.Tile[][] t = SquareCut.cut(grid("###", "#.#", "###"), 32);
        assertEquals("111/1.1/111/", sizes(t));
        assertNull(t[1][1]);
    }

    @Test
    void maxSizeCapsTheSquare() {
        assertEquals("2222/2222/2222/2222/", sizes(SquareCut.cut(grid("####", "####", "####", "####"), 2)));
    }

    @Test
    void anOddShapeIsCutFromTheBottomLeft() {
        // 5 wide, 3 tall: a 3x3 at the left, a 2x2 beside it at the bottom, two singles above that
        assertEquals("33311/33322/33322/", sizes(SquareCut.cut(grid("#####", "#####", "#####"), 32)));
    }

    @Test
    void anEmptyRegionIsEmpty() {
        assertEquals(0, SquareCut.cut(new boolean[0][0], 32).length);
    }
}
