package aside.tools;

import aside.game.Game;
import aside.game.Games;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Open every game in the library and check that it puts something on screen.
 *
 * <p><b>WHY THIS EXISTS.</b> Every game has a suite, and the suites test the games' *logic* headlessly - the bot
 * wins a night, the puzzle resolves, the trace is lost. **None of them opens a screen.** A game can pass every
 * check it has and still show a blank window on start, and the only thing that would have caught that is a
 * person looking. This opens all of them, one after another, and looks.
 *
 * <p>The signal is the one ShotLibrary established: count pixels that are not black. A UiScreen draws on tick,
 * so the frames are ticked first - a snapshot taken straight after {@code enter()} catches an empty stage, which
 * is what the first attempt at that tool produced and its docstring says so. A screen that renders nothing is
 * zero, and zero is not a threshold anyone has to tune.
 *
 * <p>It writes a png per game rather than only a verdict, because when this does fail the next question is
 * always "what did it look like", and a run that threw the frames away makes that a second job.
 *
 * <p>Run it through {@code tools/run-headless.sh}.
 */
public class CheckGames extends Application {

    private static final int TICKS = 12;
    private static final int FLOOR = 500;          // a blank frame is 0; nothing here is close to this
    private static final int COLOURS = 40;         // a backdrop has a handful; anything with text has many
    private static final String OUT = System.getProperty("shotdir", "/root/downloads/games");

    private final List<Game> games = Games.all();
    private int i = 0;
    private int pass = 0;
    private final java.util.List<String> failed = new java.util.ArrayList<>();

    private UiManager ui;
    private Scene scene;
    private UiScreen current;

    @Override public void start(Stage stage) throws Exception {
        // Both, in the order Main does them. The first version of this tool loaded only Assets and eleven of the
        // twenty-three games threw a NullPointerException on `Audio.A` - which was the tool being wrong, not the
        // games: the real entry point loads audio too, so A is never null in the application. Worth writing down
        // because it is exactly the failure this tool is meant to find, arriving from the other direction.
        aside.ui.Assets.load(".");
        aside.ui.Audio.load(".");
        ui = new UiManager(".");
        StackPane root = new StackPane(ui.getContainer());
        scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        new File(OUT).mkdirs();
        step();
    }

    private void step() {
        if (i >= games.size()) { finish(); return; }
        Game g = games.get(i);
        var wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
        wait.setOnFinished(e -> {
            try {
                current = g.create(ui);
                ui.push(current);
                current.enter();
                for (int t = 0; t < TICKS; t++) current.tick(1.0 / 60);
                var img = scene.snapshot(null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                // BULK, not per pixel. This was a double loop over 1280x720 with
                // a getArgb and a setRGB each -- 921,600 of each per game, and
                // 24 games, which was most of what made this tool take nine
                // minutes. One getPixels into an int[] and one setRGB of the
                // whole array is the same pixels by a shorter road.
                int[] px = new int[w * h];
                img.getPixelReader().getPixels(0, 0, w, h,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, w);
                bi.setRGB(0, 0, w, h, px, 0, w);
                int lit = 0;
                java.util.HashSet<Integer> colours = new java.util.HashSet<>();
                for (int argb : px) {
                    if ((argb & 0x00FFFFFF) != 0) lit++;
                    colours.add(argb);
                }
                javax.imageio.ImageIO.write(bi, "png", new File(OUT, g.id() + ".png"));
                // TWO SIGNALS, because the first one alone was too weak. "Non-black pixels" is satisfied by the
                // BACKGROUND: every screen here fills the frame with a dark grey, so a screen that drew its
                // backdrop and nothing else scored 921,600 - the whole frame - and passed. A screen that draws
                // TEXT or a UI has anti-aliasing and many distinct colours; a backdrop has a handful.
                int distinct = colours.size();

                // AND THEN PRESS SOMETHING.
                //
                // This tool opened every game and pressed nothing, so a game whose key handler throws on the
                // first DOWN passed - it had drawn its opening screen, which is all that was asked of it. The
                // frame is hashed before and after; a key that changes nothing is a NOTE, because some screens
                // legitimately ignore DOWN, but a key that throws is a FAILURE, because that is a player
                // pressing a button and getting an exception.
                long before = frameHash(img);
                String inputError = null;
                try {
                    for (javafx.scene.input.KeyCode code : new javafx.scene.input.KeyCode[]{
                            javafx.scene.input.KeyCode.DOWN, javafx.scene.input.KeyCode.DOWN,
                            javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCode.SPACE}) {
                        ui.handleKey(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                                "", "", code, false, false, false, false));
                        for (int t = 0; t < 4; t++) ui.tick(1.0 / 60);
                    }
                } catch (Throwable t) {
                    inputError = String.valueOf(t);
                }
                long after = frameHash(scene.snapshot(null));
                boolean moved = before != after;

                if (lit >= FLOOR && distinct >= COLOURS && inputError == null) {
                    System.out.printf("  %-12s ok     %d lit, %d distinct colours, keys %s%n",
                            g.id(), lit, distinct, moved ? "change the screen" : "do nothing (noted, not failed)");
                    pass++;
                } else if (inputError != null) {
                    System.out.printf("  %-12s FAIL   pressing a key threw: %s%n", g.id(), inputError);
                    failed.add(g.id());
                } else {
                    System.out.printf("  %-12s FAIL   %d lit, %d distinct colours - it drew a backdrop, not a screen%n",
                            g.id(), lit, distinct);
                    failed.add(g.id());
                }
            } catch (Throwable t) {
                System.out.printf("  %-12s FAIL   %s%n", g.id(), t);
                failed.add(g.id());
            }
            i++;
            step();
        });
        wait.play();
    }

    /** A hash of the frame, for asking whether a key press changed anything. */
    private static long frameHash(javafx.scene.image.Image img) {
        var pr = img.getPixelReader();
        long h = 1469598103934665603L;
        for (int y = 0; y < (int) img.getHeight(); y += 3) {
            for (int x = 0; x < (int) img.getWidth(); x += 3) {
                h ^= pr.getArgb(x, y);
                h *= 1099511628211L;
            }
        }
        return h;
    }

    /** One image with every game on it, so the whole library can be looked at at once. */
    private void writeContactSheet() throws Exception {
        int cols = 5, cell = 320, label = 22;
        int rows = (games.size() + cols - 1) / cols;
        var sheet = new java.awt.image.BufferedImage(cols * cell, rows * (cell + label),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14));
        for (int n = 0; n < games.size(); n++) {
            var img = javax.imageio.ImageIO.read(new File(OUT, games.get(n).id() + ".png"));
            if (img == null) continue;
            int x = (n % cols) * cell, y = (n / cols) * (cell + label);
            g.drawImage(img.getScaledInstance(cell, cell, java.awt.Image.SCALE_SMOOTH), x, y, null);
            g.setColor(java.awt.Color.LIGHT_GRAY);
            g.drawString(games.get(n).id() + "   " + games.get(n).title(), x + 6, y + cell + 16);
        }
        g.dispose();
        javax.imageio.ImageIO.write(sheet, "png", new File(OUT, "contact-sheet.png"));
        System.out.println("contact sheet: " + OUT + "/contact-sheet.png  ("
                + sheet.getWidth() + "x" + sheet.getHeight() + ", " + games.size() + " games)");
    }

    private void finish() {
        try { writeContactSheet(); } catch (Exception e) { System.out.println("contact sheet failed: " + e); }
        System.out.println();
        System.out.printf("=== %d game(s) opened, drew a screen and took a key, %d did not ===%n", pass, failed.size());
        // "did not draw" was wrong the moment input was added: a game whose key handler throws drew its screen
        // perfectly and still failed. The summary should say what was actually asked of it.
        if (!failed.isEmpty()) System.out.println("failed: " + String.join(" ", failed));
        System.out.println("frames in " + OUT);
        Platform.exit();
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
