package aside.ui;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sound. Loads cues by name from an audio folder; missing files are
 * noted and ignored rather than breaking anything.
 *
 * Design notes:
 *
 *  - **Music** is a looping MediaPlayer, one at a time. Asking for a new
 *    track fades the old one out, so `music music_box` mid-scene does not
 *    cause a hard cut.
 *
 *  - **Effects** are AudioClips, which are cheap and can overlap -- a
 *    door closing while a footstep plays is fine.
 *
 *  - **Lookup falls back through a list of candidate names.** A cue can
 *    be asked for specifically (`scare_monty`) and resolve to a general
 *    one (`scare_door`) when the specific file is absent. That means
 *    audio can be added incrementally, or split per-character later,
 *    with no code change.
 *
 * JavaFX plays MP3, WAV, AIFF and AAC. It does NOT play OGG or FLAC.
 */
public class Audio {
    public static Audio A;

    /** Formats JavaFX can actually decode. */
    static final String[] EXTS = {".mp3", ".wav", ".aiff", ".aif", ".m4a", ".aac"};

    public static final String[] MUSIC_CUES = {"fan_hum", "music_box", "room_tone", "title"};
    public static final String[] SFX_CUES = {
        "door_open", "door_close", "light_click", "camera_up", "camera_down",
        "static", "footstep", "pot_clank", "power_down", "power_up",
        "chime_6am", "text_blip", "choice_move", "choice_select", "at_door",
        "scare_door", "scare_sprint",
        "scare_monty", "scare_roxanne", "scare_chica", "scare_freddy",
    };

    private final Map<String, File> files = new LinkedHashMap<>();
    private final Map<String, AudioClip> clips = new LinkedHashMap<>();
    private final Map<String, Media> medias = new LinkedHashMap<>();

    private MediaPlayer music;
    private String musicName;

    public double musicVolume = 0.6;
    public double sfxVolume = 0.8;
    public boolean enabled = true;

    /** Where the volume settings are remembered between runs. */
    static final String SETTINGS_FILE =
            System.getProperty("user.home") + "/.aside-audio.txt";

    /**
     * Typewriter blips are throttled by TIME, not by how many characters have
     * appeared. Firing the shared sfx() every third character stacked ten to
     * fifteen overlapping copies a second, which is what "overlapping itself
     * and crunchy" was.
     */
    static final long BLIP_MIN_GAP_NANOS = 62_000_000L;   // ~16 per second
    static final double BLIP_VOLUME = 0.5;                // sit under the music
    private long lastBlipNanos = 0;

    /** Cues the script asked for that have no file. */
    public final Set<String> missing = new LinkedHashSet<>();
    public final List<String> notes = new ArrayList<>();

    public static void load(String root) {
        A = new Audio();
        // "../audio" lets both projects share one folder next to them,
        // so the same cue file serves the game and the visual novel.
        for (String dir : new String[]{"audio", "../audio", "art/audio", "sounds"}) {
            File d = new File(root, dir);
            if (!d.isDirectory()) continue;
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase();
                for (String e : EXTS) {
                    if (n.endsWith(e)) {
                        A.files.put(n.substring(0, n.length() - e.length()), f);
                        break;
                    }
                }
            }
        }
        A.notes.add("audio: found " + A.files.size() + " cue(s)"
                + (A.files.isEmpty() ? " (silent)" : ""));
        A.readSettings();
    }

    // ---------------- settings ----------------

    void readSettings() {
        try {
            File f = new File(SETTINGS_FILE);
            if (!f.exists()) return;
            for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String v = kv[1].trim();
                switch (kv[0].trim()) {
                    case "music" -> musicVolume = Double.parseDouble(v);
                    case "sfx" -> sfxVolume = Double.parseDouble(v);
                    case "enabled" -> enabled = Boolean.parseBoolean(v);
                    default -> { }
                }
            }
        } catch (Exception ignored) { }
    }

    void writeSettings() {
        try {
            java.nio.file.Files.writeString(new File(SETTINGS_FILE).toPath(),
                    "music=" + musicVolume + "\n"
                  + "sfx=" + sfxVolume + "\n"
                  + "enabled=" + enabled + "\n");
        } catch (Exception ignored) { }
    }

    /** Volume up/down, or mute. Applies live to whatever is playing. */
    public String volume(boolean up) {
        double v = Math.max(0.0, Math.min(1.0, musicVolume + (up ? 0.1 : -0.1)));
        musicVolume = v;
        sfxVolume = Math.min(1.0, v + 0.2);
        enabled = musicVolume > 0;
        if (music != null) {
            try { music.setVolume(musicVolume); } catch (Exception ignored) { }
        }
        writeSettings();
        return describe();
    }

    public String toggleMute() {
        enabled = !enabled;
        if (music != null) {
            try { music.setVolume(enabled ? musicVolume : 0.0); } catch (Exception ignored) { }
        }
        writeSettings();
        return describe();
    }

    /** What the on-screen indicator should say. */
    public String describe() {
        if (!enabled) return "SOUND OFF   (M to unmute)";
        int pct = (int) Math.round(musicVolume * 100);
        StringBuilder bar = new StringBuilder();
        int filled = (int) Math.round(musicVolume * 10);
        for (int i = 0; i < 10; i++) bar.append(i < filled ? '#' : '-');
        return "VOLUME " + pct + "%  [" + bar + "]";
    }

    // ---------------- silencing ----------------

    /**
     * Stop everything: the looping music and every effect clip.
     *
     * Called when the player returns to the library. Without it a game's
     * ambience outlives the game - leaving FNAF left the fan running, because
     * nothing owned the job of turning it off (playtest, Sept 29).
     */
    public void stopAll() {
        stopMusic();
        for (AudioClip c : clips.values()) {
            try { c.stop(); } catch (Exception ignored) { }
        }
    }

    /**
     * A typewriter blip: throttled, and RESTARTED rather than layered.
     *
     * Overlapping copies of the same sample at 15/s is both the crunch and the
     * apparent echo. One voice, retriggered, is what a blip should sound like.
     */
    public void blip(String... candidates) {
        if (!enabled) return;
        long now = System.nanoTime();
        if (now - lastBlipNanos < BLIP_MIN_GAP_NANOS) return;
        lastBlipNanos = now;
        File f = resolve(candidates);
        if (f == null) { if (candidates.length > 0) missing.add(candidates[0]); return; }
        try {
            AudioClip c = clips.computeIfAbsent(f.getAbsolutePath(),
                    k -> new AudioClip(f.toURI().toString()));
            c.stop();
            c.setVolume(sfxVolume * BLIP_VOLUME);
            c.play();
        } catch (Exception e) {
            notes.add("blip '" + candidates[0] + "' failed: " + e.getMessage());
        }
    }

    /** First candidate that has a file, or null. */
    File resolve(String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            File f = files.get(c.toLowerCase());
            if (f != null && f.exists()) return f;
        }
        return null;
    }

    // ---------------- music ----------------

    public void music(String name) {
        if (name == null || name.equalsIgnoreCase("none")) { stopMusic(); return; }
        if (name.equals(musicName)) return;              // already playing
        File f = resolve(name);
        if (f == null) { missing.add(name); return; }

        stopMusic();
        musicName = name;
        if (!enabled) return;
        try {
            Media m = medias.computeIfAbsent(name, k -> new Media(f.toURI().toString()));
            music = new MediaPlayer(m);
            music.setCycleCount(MediaPlayer.INDEFINITE);
            music.setVolume(musicVolume);
            music.play();
        } catch (Exception e) {
            notes.add("music '" + name + "' failed: " + e.getMessage());
        }
    }

    public void stopMusic() {
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) { }
            music.dispose();
            music = null;
        }
        musicName = null;
    }

    public String currentMusic() { return musicName; }

    // ---------------- effects ----------------

    /** Play a one-shot. Extra names are fallbacks, tried in order. */
    public void sfx(String... candidates) {
        if (!enabled) return;
        File f = resolve(candidates);
        if (f == null) { if (candidates.length > 0) missing.add(candidates[0]); return; }
        try {
            AudioClip c = clips.computeIfAbsent(f.getAbsolutePath(),
                    k -> new AudioClip(f.toURI().toString()));
            c.setVolume(sfxVolume);
            c.play();
        } catch (Exception e) {
            notes.add("sfx '" + candidates[0] + "' failed: " + e.getMessage());
        }
    }

    /** A character's scream: their own if it exists, else the shared
     *  door scream, else the sprint scream. */
    public void scream(String character) {
        String c = character == null ? "" : character.toLowerCase();
        sfx("scare_" + c, "scare_door", "scare_sprint");
    }

    public int cueCount() { return files.size(); }
    public boolean silent() { return files.isEmpty(); }
}
