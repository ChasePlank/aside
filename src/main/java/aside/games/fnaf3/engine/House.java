package aside.games.fnaf3.engine;

/**
 * The building: ten camera rooms and the office.
 *
 * FNAF 3 is one animatronic in a building, so the building itself has to
 * be a real shape or the game is a coin flip with a progress bar. The
 * graph below is a tree rooted at the office, which is what makes the
 * audio lure a decision: every room has exactly one way back toward you,
 * so drawing Springtrap into a room is drawing him onto a known path, and
 * the distance you buy is a number you can count.
 *
 * <pre>
 *   OFFICE
 *     |
 *     1  Entrance
 *     |\
 *     2 7
 *    /|  |\
 *   3 6  8 9
 *   |\      |
 *   4 5     10
 * </pre>
 *
 * Room 1 is the hall outside the office. Reaching it is not reaching you
 * -- it is the last room before the office, which is where the window and
 * the vent are, and where the lure still works.
 */
public final class House {

    public static final int OFFICE = 0;
    public static final int ROOMS = 10;

    /** Room names, index 1..10. The office is 0 and has no camera. */
    public static final String[] NAME = {
        "Office",
        "Entrance", "Hall", "Main Hall", "Arcade", "Kitchen",
        "Restrooms", "Hall", "Hall", "Prize Corner", "Vent",
    };

    /** Adjacency, index 1..10. The office is not a node: room 1 is the
     *  hall outside it, and stepping out of room 1 is stepping in. */
    static final int[][] ADJ = {
        {},
        {2, 7},
        {1, 3, 6},
        {2, 4, 5},
        {3},
        {3},
        {2},
        {1, 8, 9},
        {7},
        {7, 10},
        {9},
    };

    /**
     * Distance to the office in moves. Room 1 is one move away.
     *
     * Written out rather than computed because it is the difficulty
     * curve: Springtrap's "progress" is this number going down, and the
     * audio lure's value is the difference between where he is and where
     * the lure is.
     */
    public static final int[] TO_OFFICE = {0, 1, 2, 3, 4, 4, 3, 2, 3, 3, 4};

    public static int[] neighbours(int room) {
        return ADJ[room];
    }

    /** BFS distance between two rooms, or -1 if unreachable. */
    public static int distance(int from, int to) {
        if (from == to) return 0;
        int[] dist = new int[ROOMS + 1];
        java.util.Arrays.fill(dist, -1);
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        dist[from] = 0;
        q.add(from);
        while (!q.isEmpty()) {
            int r = q.poll();
            for (int n : ADJ[r]) {
                if (dist[n] != -1) continue;
                dist[n] = dist[r] + 1;
                if (n == to) return dist[n];
                q.add(n);
            }
        }
        return -1;
    }

    /**
     * The neighbour of {@code from} that is one step closer to {@code to}.
     * Ties are broken by the caller's RNG, not here -- this returns every
     * candidate so a movement roll stays in one place.
     */
    public static int[] closerTo(int from, int to) {
        int best = Integer.MAX_VALUE;
        int n = 0;
        int[] out = new int[ADJ[from].length];
        for (int cand : ADJ[from]) {
            int d = distance(cand, to);
            if (d < 0) continue;
            if (d < best) { best = d; n = 0; }
            if (d == best) out[n++] = cand;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private House() {}
}
