package aside.games.fruitjump.engine;

/**
 * Does the generator actually produce water? Counting, not reading - which is how the release found that three
 * rows of water plus a pool floor never fit in the map and it silently flooded nothing.
 *
 * Not a copy of the release's probe: aside's LevelGen has no forLevel(int) factory, it takes constructor
 * arguments, so this is written against this API.
 */
public class WaterProbe {
    public static void main(String[] args) {
        int withWater = 0, total = 0;
        StringBuilder list = new StringBuilder();
        for (int level = 1; level <= 40; level++) {
            LevelMap m = new LevelGen(60, 14, 1000L + level, level).generate();
            int n = 0;
            for (int r = 0; r < m.height; r++)
                for (int c = 0; c < m.width; c++)
                    if (m.cell(r, c) == '~') n++;
            if (n > 0) { withWater++; list.append(level).append("(").append(n).append(") "); }
            total += n;
        }
        System.out.println("levels with water: " + withWater + " of 40, " + total + " cells");
        System.out.println("  " + list);
    }
}
