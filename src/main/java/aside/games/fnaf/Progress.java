package aside.games.fnaf;

import aside.game.Games;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.*;
import java.nio.file.*;

/**
 * Persistent progress: which nights are beaten, which characters are
 * unlocked (by dying to them), and a custom-night preset.
 *
 * Saved next to the jar as `fnaf-progress.txt` — plain text so it can
 * be hand-edited or deleted. Reset from the info screen.
 */
public class Progress {
    static final int NIGHTS = 5;

    /** Highest night unlocked (1..5). Beating night N unlocks N+1. */
    public int unlocked = 1;
    /** Which characters are unlocked in the info screen. */
    public boolean[] met = new boolean[4];   // monty, roxanne, chica, freddy
    /** Beaten nights (persist even if you replay). */
    public boolean[] beaten = new boolean[NIGHTS + 1];

    /** Custom Night unlocked after beating night 5. */
    public boolean customUnlocked() { return beaten[NIGHTS]; }

    private static Path file() {
        return Games.saveDir("fnaf").resolve("progress.txt");
    }

    public static Progress load() {
        Progress p = new Progress();
        Path f = file();
        if (!Files.exists(f)) return p;
        try {
            for (String line : Files.readAllLines(f)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String k = kv[0].trim(), v = kv[1].trim();
                switch (k) {
                    case "unlocked" -> p.unlocked = Math.max(1, Math.min(NIGHTS, Integer.parseInt(v)));
                    case "met" -> {
                        // comma-separated indices
                        for (String s : v.split(",")) {
                            if (s.isBlank()) continue;
                            int i = Integer.parseInt(s.trim());
                            if (i >= 0 && i < 4) p.met[i] = true;
                        }
                    }
                    case "beaten" -> {
                        for (String s : v.split(",")) {
                            if (s.isBlank()) continue;
                            int i = Integer.parseInt(s.trim());
                            if (i >= 1 && i <= NIGHTS) p.beaten[i] = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Corrupt save -> start fresh rather than crash
            System.err.println("Progress load failed, starting fresh: " + e);
        }
        return p;
    }

    public void save() {
        StringBuilder metIdx = new StringBuilder();
        for (int i = 0; i < 4; i++) if (met[i]) metIdx.append(i).append(",");
        StringBuilder beatenIdx = new StringBuilder();
        for (int i = 1; i <= NIGHTS; i++) if (beaten[i]) beatenIdx.append(i).append(",");

        String text = "# FNAF Glamrock progress — delete this file to reset\n"
                + "unlocked=" + unlocked + "\n"
                + "met=" + metIdx + "\n"
                + "beaten=" + beatenIdx + "\n";
        try {
            Files.writeString(file(), text);
        } catch (IOException e) {
            System.err.println("Progress save failed: " + e);
        }
    }

    /** Record a night win — unlock the next night, mark beaten. */
    public void beat(int night) {
        if (night >= 1 && night <= NIGHTS) beaten[night] = true;
        if (night < NIGHTS) unlocked = Math.max(unlocked, night + 1);
        save();
    }

    /** Record meeting (dying to) an animatronic. */
    public void meet(String name) {
        int i = switch (name) {
            case "Monty" -> 0;
            case "Roxanne" -> 1;
            case "Chica" -> 2;
            case "Freddy" -> 3;
            default -> -1;
        };
        if (i >= 0 && !met[i]) { met[i] = true; save(); }
    }

    public void reset() {
        unlocked = 1;
        met = new boolean[4];
        beaten = new boolean[NIGHTS + 1];
        save();
    }
}
