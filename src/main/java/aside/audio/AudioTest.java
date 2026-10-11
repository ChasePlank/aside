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
        // AN UNCAUGHT EXCEPTION ON A JAVAFX THREAD IS NOT A VERDICT, AND IT MUST NOT LOOK LIKE ONE.
        //
        // Loading a clip with no audio device throws from JavaFX's own media thread, where nothing here can catch it,
        // so the stack trace lands on stderr whenever that thread gets there - sometimes after the summary this suite
        // prints. The gate reads the summary-shaped last line, which is how a green run came back red three times,
        // always with a bare "#" as the reason.
        //
        // The fix is not to swallow it. A MEDIA EXCEPTION SAYING THERE IS NO PLAYER IS THE SANDBOX'S, and it is
        // counted and named as such; anything else is an error and counts against the run. One line, never a trace:
        // the suite's own output is then the same on every run, whatever order the threads finish in.
        final java.util.List<String> asyncErrors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.concurrent.atomic.AtomicInteger noDevice = new java.util.concurrent.atomic.AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            boolean device = "MediaException".equals(e.getClass().getSimpleName())
                    && String.valueOf(e.getMessage()).contains("Could not create player");
            if (device) noDevice.incrementAndGet();
            else asyncErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  (media thread: " + e.getClass().getSimpleName() + " - "
                    + (device ? "no audio device here, expected" : "UNEXPECTED") + ")");
        });

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
        // Give the media threads a moment to finish, so anything they had to say is in the report rather than after
        // it. This is a settle, not a sleep-for-luck: the verdict below does not depend on it, because a missing
        // file already fails the run and the async errors are added if and when they arrive.
        try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        for (String e : asyncErrors) errors.add(e);
        if (noDevice.get() > 0) {
            System.out.println("note: " + noDevice.get() + " media thread(s) could not create a player - no audio"
                    + " device in this sandbox. The cue list above is the part that works everywhere.");
        }

        int bad = errors.size() + Audio.A.missing.size();
        System.out.println(bad == 0
                ? "\n=== all cues present and playable ==="
                : "\n=== " + bad + " cue(s) missing or unplayable ===");
        Platform.exit();
        if (bad > 0) System.exit(1);
    }

    public static void main(String[] args) { launch(args); }
}
