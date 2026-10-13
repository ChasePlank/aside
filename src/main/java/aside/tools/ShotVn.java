package aside.tools;

import aside.engine.Script;
import aside.games.overtime.VnScreen;
import aside.engine.Vn;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.nio.file.Path;

/**
 * ShotVn - capture the visual novel mid-scene, with sprites on screen.
 *
 * The sprite pipeline is mine (aside.art.SpriteProcess, 16 poses through art/raw and
 * art/sprites) and it had only ever been verified by its own quality report. Rendering is
 * the other half: a bad crop or a wrong floor line is invisible in a file listing and
 * obvious in a frame - which is how the water drawn under the sky and the jumpscare border
 * were both found. So: advance the story to a beat that actually shows someone, then draw.
 *
 * Usage:  DISPLAY=:99 java -cp out aside.tools.ShotVn [story-name]     (default: overtime)
 *
 * Run from the repo root: Assets resolves art/backgrounds and art/sprites relatively.
 *
 * VERIFIED (Sept 28): Monty drawn on the office background, keyed and cropped onto the
 * shared floor line, text box reading "Monty / Ne" - the typewriter caught mid-line by the
 * three-tick capture. So aside.art.SpriteProcess output renders correctly, which until now
 * was only known from its own quality report.
 *
 * Two things it took, both worth keeping:
 *   1. Three ticks, not ninety. One frame draws; ninety of them re-decoded the large sprite
 *      PNGs per tick and spent two minutes hitting the timeout without reaching the snapshot.
 *   2. DISPLAY=:99 on the command. Without it JavaFX dies with "Unable to open DISPLAY", and
 *      a pgrep for Xvfb does not tell you whether the display you are about to use is live.
 */
public class ShotVn extends Application {
    @Override public void start(Stage stage) throws Exception {
        aside.ui.Assets.load(".");
        aside.ui.UiManager ui = new aside.ui.UiManager(".");
        // The story is an argument now, defaulting to overtime. It used to be hard-coded, and it lived in
        // the overtime game's package - which is why I forgot it existed and nearly wrote it again. A tool
        // for the engine does not belong inside one game.
        String story = getParameters().getRaw().isEmpty() ? "overtime" : getParameters().getRaw().get(0);
        // AND THE STORY'S OWN ART. Assets.load(".") reads the SHARED art/backgrounds and art/sprites, which
        // overtime uses - so this tool looked like it worked. The three stories written since have their art
        // under art/stories/<id>/, and without loadStory they rendered with NO background at all: a black frame
        // with a text box, 14 KB instead of 882 KB. A tool for verifying the sprite pipeline that verifies
        // nothing for three of four stories is worse than no tool, because it reports PASS.
        aside.ui.Assets.loadStory(".", story);
        Script script = Script.load(Path.of("stories", story + ".aside"));
        Vn vn = new Vn(script);
        // Where to stop. "sprite" waits for someone to be standing in the scene; "choice" stops the moment the
        // story offers the player a decision, which is the screen a reader spends the most time looking at and
        // the one view of this presenter I had never seen.
        String stopAt = getParameters().getRaw().size() > 1 ? getParameters().getRaw().get(1) : "sprite";
        int steps = 0;
        if ("choice".equals(stopAt)) {
            while (vn.mode != Vn.Mode.CHOOSING && steps++ < 400) vn.advance();
        } else if ("toast".equals(stopAt) || "history".equals(stopAt) || "menu".equals(stopAt)) {
            // Walk a little way into the story first, so the history has something in it and the menu is not
            // opened on an empty screen.
            for (int i = 0; i < 6 && steps++ < 400; i++) {
                if (vn.mode == Vn.Mode.CHOOSING) vn.choose(0);
                else vn.advance();
            }
        } else {
            // AND THE STOP CONDITION HAS TO BE ONE THAT CAN BE REACHED. "Advance to a beat that shows somebody"
            // is unsatisfiable for a story with no cast, and several of these are one-handers with no `show` line
            // at all - MEASURED on 2026-10-13: `the-exchange` and `the-bothy` have zero `show` directives between
            // them, so the loop ran to its 400-step cap and the DIAG line said `shown={}` every time. It stopped
            // somewhere in the middle of the story and called it a scene with a person in it.
            // Asked of the SCRIPT TEXT, because `show` is not a beat kind of its own - it is a STAGE beat, like
            // `bg` and `music`, and picking it out of those needs the parser's insides. One line of text is the
            // same question, and it is the one that was measured.
            boolean hasCast = java.nio.file.Files.readString(Path.of("stories", story + ".aside"))
                    .lines().anyMatch(l -> l.startsWith("show "));
            while ((hasCast ? vn.shown.isEmpty() : vn.mode != Vn.Mode.CHOOSING) && steps++ < 400) {
                if (vn.mode == Vn.Mode.CHOOSING) {
                    if (!hasCast) break;      // a choice is the beat this tool wanted
                    vn.choose(0);
                } else vn.advance();
            }
            if (!hasCast) System.out.println("DIAG this story shows nobody: stopping at the first choice instead");
        }
        System.out.println("DIAG reached a beat after " + steps + " steps: shown=" + vn.shown
                + " speaker=" + vn.speaker() + " mode=" + vn.mode);
        VnScreen screen = new VnScreen(ui, vn, script.title);
        ui.push(screen);
        StackPane root = new StackPane(screen.getRoot());
        Scene scene = new Scene(root, 1280, 720, Color.BLACK);
        stage.setScene(scene);
        stage.show();
        screen.enter();
        // Press a key after entering, for the views that are opened rather than navigated to. Synthesised rather
        // than clicked because the screen takes KeyEvents, and these two overlays have never been looked at.
        if ("toast".equals(stopAt) || "history".equals(stopAt) || "menu".equals(stopAt)) {
            // The toast is the one view that only exists for a couple of seconds - it is the save/load
            // confirmation, and nothing had ever looked at it. F5 saves, which raises it.
            javafx.scene.input.KeyCode code = switch (stopAt) {
                case "toast" -> javafx.scene.input.KeyCode.F5;
                case "history" -> javafx.scene.input.KeyCode.H;
                default -> javafx.scene.input.KeyCode.ESCAPE;
            };
            screen.handleKey(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    code, false, false, false, false));
            System.out.println("DIAG pressed " + code);
        }

        var t = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        t.setOnFinished(e -> {
            // TWELVE TICKS, NOT THREE AND NOT NINETY, AND THE NUMBER IS THE WHOLE POINT.
            //
            // Three was chosen to avoid the two-minute timeout ninety ticks caused, and it is not enough: JavaFX
            // decodes an Image IN THE BACKGROUND unless told otherwise, so at three ticks the background may not be
            // decoded yet and the snapshot catches a BLACK FRAME WITH A TEXT BOX ON IT. That is not a slow tool, it
            // is a tool that cannot tell "this story's art is missing" from "this story's art had not finished
            // decoding" - and it reported the first when the second was true. MEASURED on 2026-10-13: the frame for
            // `the-exchange` was byte-identical (78b77081e832) before and after the story's background became
            // resolvable, which is how it was caught.
            for (int i = 0; i < 12; i++) ui.tick(1.0 / 60);
            WritableImage img = scene.snapshot(null);
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var pr = img.getPixelReader();
                int nonBlack = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int a = pr.getArgb(x, y);
                    bi.setRGB(x, y, a);
                    if ((a & 0xFFFFFF) != 0) nonBlack++;
                }
                javax.imageio.ImageIO.write(bi, "png", new java.io.File("/root/downloads/" + story + "-vn-" + stopAt + ".png"));
                System.out.println("PASS wrote " + story + "-vn-" + stopAt + ".png non-black=" + nonBlack);
            } catch (Exception ex) {
                System.out.println("FAIL: " + ex);
            }
            Platform.exit();
            System.exit(0);
        });
        t.play();
    }
}
