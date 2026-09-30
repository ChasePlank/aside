package aside.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Aside — entry point.
 *
 * Window fits the screen's work area and is resizable; the drawing
 * surface stays a fixed 1280x720 and is scaled to fit. Region
 * backgrounds are avoided entirely (they don't paint on this GPU), so
 * every screen draws itself onto a Canvas.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        String root = resolveRoot();
        Assets.load(root);
        Audio.load(root);

        for (String note : Assets.A.notes) System.out.println("[assets] " + note);
        for (String note : Audio.A.notes) System.out.println("[audio] " + note);

        UiManager ui = new UiManager(root);

        StackPane host = new StackPane(ui.getContainer());
        host.setStyle("-fx-background-color: #000000;");

        javafx.geometry.Rectangle2D area =
                javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.min(1280, area.getWidth());
        double h = Math.min(720, area.getHeight());

        Scene scene = new Scene(host, w, h, Color.BLACK);
        scene.setOnKeyPressed(ui::handleKey);
        scene.setOnKeyReleased(ui::handleKeyReleased);
        // Mouse, for the screens that want it. Scene coordinates; the manager
        // converts them into whatever canvas the top screen is using.
        scene.setOnMousePressed(e -> ui.handleMouse(e.getSceneX(), e.getSceneY(), true));
        scene.setOnMouseMoved(e -> ui.handleMouse(e.getSceneX(), e.getSceneY(), false));

        // Screenshot mode: render N frames, write the frame to a file,
        // exit. Screen-grabbing the desktop proved unreliable (the
        // window can composite to white on this GPU), but asking
        // JavaFX for its own frame always tells the truth.
        String shotPath = System.getProperty("aside.snapshot");
        String shotKeys = System.getProperty("aside.keys");
        String shotMouse = System.getProperty("aside.mouse");
        String shotClick = System.getProperty("aside.click");

        AnimationTimer loop = new AnimationTimer() {
            long last = -1;
            long frames = 0;
            @Override public void handle(long now) {
                double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
                last = now;
                ui.tick(dt);
                frames++;

                // Feed synthetic input, then snapshot. Keys first, then the
                // mouse, so a click can land on the screen a key just opened
                // -- which is the only way to render a click on anything
                // that is not the first screen.
                if (frames == 30) {
                    if (shotKeys != null) {
                        for (String k : shotKeys.split(",")) {
                            javafx.scene.input.KeyCode code;
                            try {
                                code = javafx.scene.input.KeyCode.valueOf(k.trim().toUpperCase());
                            } catch (Exception ex) { continue; }
                            ui.handleKey(new javafx.scene.input.KeyEvent(
                                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                                    "", "", code, false, false, false, false));
                        }
                    }
                    applyMouse(ui, shotMouse, false);
                    applyMouse(ui, shotClick, true);
                }

                if (shotPath != null && frames == 90) {
                    try {
                        var img = host.snapshot(null, null);
                        java.awt.image.BufferedImage bi =
                                javafx.embed.swing.SwingFXUtils.fromFXImage(img, null);
                        javax.imageio.ImageIO.write(bi, "png", new java.io.File(shotPath));
                        System.out.println("[snapshot] wrote " + shotPath
                                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
                    } catch (Exception ex) {
                        System.out.println("[snapshot] failed: " + ex);
                    }
                    javafx.application.Platform.exit();
                }
            }
        };
        loop.start();

        stage.setTitle("Aside — visual novel engine");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        ui.push(new LibraryScreen(ui));
    }

    public static void main(String[] args) { launch(args); }

    /**
     * One synthetic mouse event, from -Daside.mouse=x,y (hover) or
     * -Daside.click=x,y (press).
     *
     * Coordinates are WINDOW coordinates, which is what a real scene
     * delivers; UiManager converts them into the top screen's canvas space,
     * so a screen that draws at its own resolution still gets the right
     * numbers. In a render the window is 1280x720 and so is the engine
     * canvas, so there they are also canvas coordinates.
     *
     * A mouse feature that cannot be rendered is a mouse feature that rots:
     * a click that did nothing and a click that was never made look exactly
     * alike in a screenshot.
     */
    static void applyMouse(UiManager ui, String spec, boolean pressed) {
        if (spec == null) return;
        String[] parts = spec.split(",");
        if (parts.length != 2) return;
        try {
            ui.handleMouse(Double.parseDouble(parts[0].trim()),
                           Double.parseDouble(parts[1].trim()), pressed);
        } catch (NumberFormatException ignored) {
            // a malformed hook is not worth failing a render over
        }
    }

    /**
     * Where the art, audio and stories live.
     *
     * This used to be nothing but `System.getProperty("aside.root", ".")`, so
     * the app showed NO ART AT ALL unless it happened to be launched from the
     * right working directory with the right flag. A tester on a machine that
     * had never run it got an empty-looking game and no explanation.
     *
     * Now it works out where it is. In order:
     *   1. -Daside.root, if it actually points at some assets
     *   2. next to the jar or executable we are running from (the packaged
     *      app image puts art/ beside Aside.jar, so this is the normal case)
     *   3. a few directories up, for running from a build folder
     *   4. the working directory and its parent, for `java -cp classes ...`
     * Falling through to "." is fine: Assets also looks on the classpath.
     */
    static String resolveRoot() {
        String prop = System.getProperty("aside.root");
        if (prop != null && hasAssets(prop)) return prop;

        try {
            java.nio.file.Path self = java.nio.file.Path.of(
                    Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            java.nio.file.Path dir = java.nio.file.Files.isDirectory(self)
                    ? self : self.getParent();
            for (int up = 0; up < 4 && dir != null; up++, dir = dir.getParent()) {
                if (hasAssets(dir.toString())) return dir.toString();
            }
        } catch (Exception ignored) {
            // no code source (unusual packaging): fall through to the cwd
        }

        for (String candidate : new String[] { ".", "..", ".." }) {
            if (hasAssets(candidate)) return candidate;
        }
        return ".";
    }

    private static boolean hasAssets(String root) {
        java.io.File art = new java.io.File(root, "art");
        return art.isDirectory() && art.list() != null && art.list().length > 0;
    }
}
