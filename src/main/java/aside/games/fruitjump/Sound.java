package aside.games.fruitjump;

import aside.games.fruitjump.engine.AudioSystem;
import javafx.scene.media.AudioClip;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The platformer's sound backend: turns posted cue names into sound.
 *
 * <p><b>Why this is a separate class.</b> {@code AudioSystem} posts events and knows nothing about JavaFX -
 * that split is deliberate and worth keeping, because the engine runs headless in the gate and on a machine
 * with no sound device. This is the other half: it reads the names and plays the files.
 *
 * <p><b>Why it lives in {@code games/fruitjump/} and not next to the screens.</b> The screens are "wiring"
 * files that the release sync skips, so a backend written there would have to be written twice and would drift.
 * A file here travels with the sync, and it can, because it does not import {@code aside.ui.*} - it talks to
 * JavaFX directly. That is the same reason {@code CharacterConfig} and {@code WaterSuite} live here.
 *
 * <p>Nothing about this is load-bearing for the game: with no audio folder every cue is silently skipped, and
 * the platformer plays exactly as it did before it had sound.
 */
public class Sound {

    private final Map<String, AudioClip> clips = new LinkedHashMap<>();
    private final Map<String, String> missing = new LinkedHashMap<>();
    private boolean enabled = true;
    private double volume = 1.0;

    /**
     * How a cue is actually sounded, separated from the DECISION to sound it.
     *
     * <p><b>Why this seam exists.</b> The volume path had a defect that nothing could see: the global mute and
     * volume keys live on {@code aside.ui.Audio} and this backend ignored them, so pressing M in the platformer
     * said "SOUND OFF" and the game kept playing. It was invisible to every check here because the audible
     * result cannot be observed in a sandbox with no sound device - {@code play()} returns early when the clip is
     * missing, which is the same thing it does when it is working. Splitting "should this sound, and how loud"
     * from "make the sound" makes the first half an ordinary function with an ordinary test, and leaves only the
     * second half unverifiable. {@code SelfTest} drives this with a recorder.
     */
    public interface Sink {
        void play(String cue, double volume);
    }

    private Sink sink = this::playClip;

    /**
     * Load every cue the engine can post, from {@code root/audio} or from the jar.
     *
     * <p>Two places, and the second is not a fallback for tidiness. The README's instruction is
     * {@code java -jar tropical-punch.jar}, and a jar someone downloaded on its own sits next to no audio
     * folder at all - so a folder-only loader means the published jar is silent while the checkout is not, and
     * nothing would say which. The cues are 216KB together, which is nothing beside the JavaFX runtime the jar
     * already carries. Folder first, so a checkout can still override a cue by dropping a file in.
     */
    public static Sound load(String root) {
        Sound s = new Sound();
        File dir = new File(root, "audio");
        for (String cue : AudioSystem.sfxNames()) {
            String source = null;
            for (String ext : new String[]{".wav", ".mp3"}) {
                File f = new File(dir, cue + ext);
                if (f.isFile()) { source = f.toURI().toString(); break; }
            }
            if (source == null) {
                var res = Sound.class.getResource("/audio/" + cue + ".wav");
                if (res == null) res = Sound.class.getResource("/audio/" + cue + ".mp3");
                if (res != null) source = res.toExternalForm();
            }
            if (source == null) { s.missing.put(cue, "no file"); continue; }
            try {
                s.clips.put(cue, new AudioClip(source));
            } catch (Exception ex) {
                // A file that exists is not a file that plays - the same distinction AudioTest exists for.
                s.missing.put(cue, String.valueOf(ex.getMessage()));
            }
        }
        return s;
    }

    /**
     * Is the audio where the game can find it?
     *
     * <pre>
     *   java -cp out aside.games.fruitjump.Sound
     *   java -jar tropical-punch.jar   # then it is reported on the first frame anyway
     * </pre>
     *
     * <p>A diagnostic rather than a test: it needs a JavaFX toolkit and a display, so it cannot live in the
     * gate, and the question it answers - "is this build's audio present" - is exactly the one that is silent
     * when the answer is no.
     */
    public static void main(String[] args) {
        javafx.application.Platform.startup(() -> { });
        Sound s = Sound.load(args.length > 0 ? args[0] : ".");
        System.out.println("cues loaded: " + s.loaded() + " of " + AudioSystem.sfxNames().length);
        if (!s.missing().isEmpty()) System.out.println("missing: " + s.missing());
        javafx.application.Platform.exit();
    }

    /** Play everything posted since the last call. Call this once a frame. */
    public void drain(AudioSystem audio) {
        if (audio == null) return;
        for (String cue : audio.drainPending()) play(cue);
    }

    /** The real sounding: the volume is applied to the clip, then it plays. */
    private void playClip(String cue, double volume) {
        AudioClip c = clips.get(cue);
        if (c == null) return;          // no file for this cue, or no sound device. Either way, nothing to do.
        try {
            c.setVolume(volume);
            c.play();
        } catch (Exception ignored) {
            // A clip that will not start must never take the game with it.
        }
    }

    /**
     * Sound a cue, if sound is on.
     *
     * <p>Two decisions, both here and both checkable: whether to sound at all, and how loud. What happens next is
     * the sink's business.
     */
    public void play(String cue) {
        if (!enabled) return;
        sink.play(cue, volume);
    }

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    /** Volume for this backend, 0..1. Clamped, so a caller cannot hand the sink a nonsense level. */
    public void setVolume(double v) { volume = Math.max(0.0, Math.min(1.0, v)); }
    public double volume() { return volume; }

    /** For tests: sound cues through something other than JavaFX. Null restores the real clips. */
    public void setSink(Sink s) { sink = (s == null ? this::playClip : s); }
    public int loaded() { return clips.size(); }
    public Map<String, String> missing() { return missing; }
}
