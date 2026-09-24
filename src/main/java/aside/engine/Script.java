package aside.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Aside script format into scenes.
 *
 * The format is line-based and meant to be written by hand:
 *
 *   # comments
 *   title: Night Shift
 *
 *   == start ==
 *   bg office_night
 *   music fan_hum
 *   The chair is still warm.
 *   monty: You're late.
 *   monty (gritted): Again.
 *   ~ aff_monty -1
 *   * "I'm here, aren't I?" -> defiant
 *   * Say nothing. -> quiet
 *   * "...Sorry." -> apologetic [if aff_monty > 0]
 *       ~ aff_monty +1
 *   -> hallway
 *
 * Rules that matter:
 *  - A `speaker: text` line is dialogue only when the speaker is a
 *    single bare identifier, so prose containing a colon ("It was 5 AM:
 *    the shift ended.") stays narration.
 *  - `~ effect` attaches to the beat ABOVE it, or to the choice above
 *    it if the previous line was a `*` option.
 *  - Effects on a TEXT beat fire when the line is shown.
 */
public class Script {
    public String title = "Untitled";
    public String author = "";
    public String startScene = "start";
    public final Map<String, Scene> scenes = new LinkedHashMap<>();

    /** Non-fatal problems found while parsing (reported, never thrown). */
    public final List<String> warnings = new ArrayList<>();

    static final Pattern SCENE_HDR = Pattern.compile("^==\\s*(\\S+)\\s*==\\s*$");
    static final Pattern DIALOGUE = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(([^)]*)\\))?\\s*:\\s+(.*)$");
    static final Pattern CHOICE_P = Pattern.compile(
            "^\\*\\s+(.*?)\\s*->\\s*(\\S+)\\s*(?:\\[\\s*if\\s+(.+?)\\s*\\])?\\s*$");
    static final Pattern GOTO_P = Pattern.compile("^->\\s*(\\S+)\\s*$");
    static final Pattern IF_P = Pattern.compile("^if\\s+(.+?)\\s*->\\s*(\\S+)\\s*$");
    static final Pattern SET_P = Pattern.compile("^set\\s+([A-Za-z_]\\w*)\\s*=\\s*(.+)$");
    static final Pattern EFFECT_P = Pattern.compile("^~\\s*(.+)$");
    static final Pattern STAGE_P = Pattern.compile("^(bg|music|sfx|show|hide)\\s+(.*)$");
    static final Pattern META_P = Pattern.compile("^([A-Za-z_]+)\\s*:\\s*(.*)$");

    public static Script parse(String source, String name) {
        Script s = new Script();
        Scene current = null;
        Beat lastBeat = null;      // beat that a `~` would attach to
        Choice lastChoice = null;  // choice that a `~` would attach to

        String[] lines = source.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String t = raw.trim();
            int lineNo = i + 1;

            if (t.isEmpty() || t.startsWith("#")) continue;

            Matcher m;

            // --- scene header ---
            if ((m = SCENE_HDR.matcher(t)).matches()) {
                String id = m.group(1);
                if (s.scenes.containsKey(id)) {
                    s.warnings.add(name + ":" + lineNo + " duplicate scene id '" + id + "'");
                }
                current = new Scene();
                current.id = id;
                current.line = lineNo;
                s.scenes.put(id, current);
                lastBeat = null;
                lastChoice = null;
                continue;
            }

            // --- metadata (only before the first scene) ---
            if (current == null) {
                if ((m = META_P.matcher(t)).matches()) {
                    switch (m.group(1).toLowerCase()) {
                        case "title" -> s.title = m.group(2);
                        case "author" -> s.author = m.group(2);
                        case "start" -> s.startScene = m.group(2).trim();
                        default -> s.warnings.add(name + ":" + lineNo
                                + " unknown header '" + m.group(1) + "'");
                    }
                    continue;
                }
                s.warnings.add(name + ":" + lineNo + " content before any '== scene =='");
                continue;
            }

            // --- effect on the previous beat / choice ---
            if ((m = EFFECT_P.matcher(t)).matches()) {
                String eff = m.group(1).trim();
                if (lastChoice != null) {
                    lastChoice.effects.add(eff);
                } else if (lastBeat != null) {
                    lastBeat.effects.add(eff);
                } else {
                    // A `~` before any beat in the scene runs on entry.
                    // (It used to be dropped with a warning, which
                    // silently lost the author's intent.)
                    Beat eb = Beat.effect(eff, lineNo);
                    current.beats.add(eb);
                    lastBeat = eb;
                }
                continue;
            }

            // --- choice option ---
            if (t.startsWith("*")) {
                if ((m = CHOICE_P.matcher(t)).matches()) {
                    // Consecutive `*` lines belong to the same CHOICE
                    // beat; any other line type resets lastBeat, which
                    // is what closes the run.
                    Beat cb;
                    if (lastBeat != null && lastBeat.kind == Beat.Kind.CHOICE) {
                        cb = lastBeat;
                    } else {
                        cb = Beat.choice(lineNo);
                        current.beats.add(cb);
                    }
                    Choice c = new Choice(m.group(1).trim(), m.group(2));
                    if (m.group(3) != null) c.condition = m.group(3).trim();
                    c.line = lineNo;
                    cb.choices.add(c);
                    lastBeat = cb;
                    lastChoice = c;
                    continue;
                }
                s.warnings.add(name + ":" + lineNo + " malformed choice: " + t);
                continue;
            }

            lastChoice = null;

            // --- conditional jump (must be tested before narration) ---
            if ((m = IF_P.matcher(t)).matches()) {
                Beat b = Beat.ifGoTo(m.group(1).trim(), m.group(2), lineNo);
                current.beats.add(b);
                lastBeat = b;
                continue;
            }

            // --- goto ---
            if ((m = GOTO_P.matcher(t)).matches()) {
                Beat b = Beat.goTo(m.group(1), lineNo);
                current.beats.add(b);
                lastBeat = b;
                continue;
            }

            // --- set ---
            if ((m = SET_P.matcher(t)).matches()) {
                Beat b = Beat.set(m.group(1), m.group(2).trim(), lineNo);
                current.beats.add(b);
                lastBeat = b;
                continue;
            }

            // --- staging ---
            if ((m = STAGE_P.matcher(t)).matches()) {
                String dir = m.group(1);
                String[] parts = m.group(2).trim().split("\\s+");
                String a1 = parts.length > 0 ? parts[0] : null;
                String a2 = null, a3 = null;
                for (int p = 1; p < parts.length; p++) {
                    if (parts[p].equals("at") && p + 1 < parts.length) {
                        a3 = parts[p + 1];
                        p++;
                    } else if (a2 == null) {
                        a2 = parts[p];
                    }
                }
                Beat b = Beat.stage(dir, a1, a2, a3, lineNo);
                current.beats.add(b);
                lastBeat = b;
                continue;
            }

            // --- dialogue or narration ---
            Beat b;
            if ((m = DIALOGUE.matcher(t)).matches()) {
                b = Beat.text(m.group(1), m.group(2), m.group(3).trim(), lineNo);
            } else {
                b = Beat.text(null, null, t, lineNo);
            }
            current.beats.add(b);
            lastBeat = b;
        }

        return s;
    }

    public static Script load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8),
                file.getFileName().toString());
    }

    public static Script loadResource(String path) throws IOException {
        try (InputStream in = Script.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing resource: " + path);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), path);
        }
    }

    public Scene scene(String id) { return scenes.get(id); }
}
