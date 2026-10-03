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
                var pr = img.getPixelReader();
                int lit = 0;
                java.util.HashSet<Integer> colours = new java.util.HashSet<>();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int argb = pr.getArgb(x, y);
                    bi.setRGB(x, y, argb);
                    if ((argb & 0x00FFFFFF) != 0) lit++;
                    colours.add(argb);
                }
                javax.imageio.ImageIO.write(bi, "png", new File(OUT, g.id() + ".png"));
                // TWO SIGNALS, because the first one alone was too weak. "Non-black pixels" is satisfied by the
                // BACKGROUND: every screen here fills the frame with a dark grey, so a screen that drew its
                // backdrop and nothing else scored 921,600 - the whole frame - and passed. A screen that draws
                // TEXT or a UI has anti-aliasing and many distinct colours; a backdrop has a handful.
                int distinct = colours.size();
                if (lit >= FLOOR && distinct >= COLOURS) {
                    System.out.printf("  %-12s ok     %d lit, %d distinct colours%n", g.id(), lit, distinct);
                    pass++;
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

    private void finish() {
        System.out.println();
        System.out.printf("=== %d game(s) opened and drew something, %d did not ===%n", pass, failed.size());
        if (!failed.isEmpty()) System.out.println("did not draw: " + String.join(" ", failed));
        System.out.println("frames in " + OUT);
        Platform.exit();
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
