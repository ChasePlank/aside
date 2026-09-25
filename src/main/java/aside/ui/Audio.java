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
        "chime_6am", "text_blip", "choice_move", "choice_select",
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
