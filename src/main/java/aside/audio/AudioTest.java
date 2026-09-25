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
 * Usage: java aside.audio.AudioTest [root]
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
        Platform.exit();
    }

    public static void main(String[] args) { launch(args); }
}
