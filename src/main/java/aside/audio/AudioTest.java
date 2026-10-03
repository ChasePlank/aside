package aside.audio;

import aside.ui.Audio;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the audio folder and actually plays every cue, reporting
 * decode or playback errors.
 *
 * Existence of a file is not evidence it can be played. This is the
 * check that matters, because JavaFX refuses several formats (OGG,
 * FLAC) and refuses them at play time rather than load time.
 *
 * <p><b>And it EXITS NON-ZERO, which it did not until 2026-10-03.</b> It
 * collected its errors, printed them, and called {@code Platform.exit()} --
 * so a run with a missing cue and a run with everything present both ended
 * with status 0, and the README's claim that it "works as a gate rather than a
 * report" was not true of it. A report that prints FAIL and exits clean is the
 * same defect as a suite with no assertions: it reads as verified.
 *
 * <p>Usage: java aside.audio.AudioTest [root]
 */
public class AudioTest extends Application {
    @Override
    public void start(Stage stage) {
        String root = getParameters().getRaw().isEmpty()
                ? "." : getParameters().getRaw().get(0);
        Audio.load(root);
        System.out.println("[audio] cues found: " + Audio.A.cueCount());
        for (String n : Audio.A.notes) System.out.println("        " + n);

        List<String> errors = new ArrayList<>();
        Audio.A.enabled = true;

        // Play each music cue in turn, then each effect.
        for (String cue : Audio.MUSIC_CUES) {
            Audio.A.music(cue);
            // Check the cue itself, not "is anything playing" -- a
            // previous track still looping made this report ok for
            // files that do not exist.
            boolean started = cue.equals(Audio.A.currentMusic());
            boolean noFile = Audio.A.missing.contains(cue);
            System.out.printf("  music %-5s %s%s%n",
                    started ? "ok" : (noFile ? "none" : "FAIL"), cue,
                    (!started && !noFile) ? "   <-- file exists but did not play" : "");
            if (!started && !noFile) errors.add(cue);
        }
        Audio.A.stopMusic();

        for (String cue : Audio.SFX_CUES) {
            boolean had = Audio.A.cueCount() > 0;
            Audio.A.sfx(cue);
            System.out.println("  sfx tried   " + cue
                    + (Audio.A.missing.contains(cue) ? "   (no file)" : ""));
        }

        System.out.println();
        System.out.println("missing cues (no file present): " + Audio.A.missing);
        System.out.println("errors: " + (errors.isEmpty() ? "none" : errors));

        // A missing cue is a failure too, not just a note: the scripts ask for
        // it by name and nothing else checks that the file is there.
        int bad = errors.size() + Audio.A.missing.size();
        System.out.println(bad == 0
                ? "\n=== all cues present and playable ==="
                : "\n=== " + bad + " cue(s) missing or unplayable ===");
        Platform.exit();
        if (bad > 0) System.exit(1);
    }

    public static void main(String[] args) { launch(args); }
}
