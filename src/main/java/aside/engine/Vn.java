package aside.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The runner. Holds story state and walks beats.
 *
 * Deliberately UI-free: the renderer asks for `speaker()/text()/
 * background()/shown()` and calls `advance()` or `choose(i)`. That
 * split is what lets the same engine be driven by JavaFX, by a terminal
 * loop, or by the blind-traversal bot in tests.
 */
public class Vn {
    public enum Mode { SHOWING, CHOOSING, ENDED }

    public final Script script;
    public Script getScript() { return script; }

    // ---- story state (this is what gets saved) ----
    public final Map<String, Object> vars = new LinkedHashMap<>();
    public final Set<String> visited = new LinkedHashSet<>();
    public final List<String[]> history = new ArrayList<>();  // {speaker, text}

    // ---- position ----
    public String sceneId;
    public int index;             // next beat to process
    public Mode mode = Mode.SHOWING;
    public Beat current;          // TEXT being shown, or CHOICE being offered

    // ---- presentation (also saved, so a loaded game looks right) ----
    public String background;
    public String music;
    public final Map<String, String> shown = new LinkedHashMap<>();  // char -> pose
    public final Map<String, String> stagePos = new LinkedHashMap<>();

    public int stepsTaken = 0;    // guards against runaway loops

    public Vn(Script script) {
        this.script = script;
        start();
    }

    public void start() {
        vars.clear();
        visited.clear();
        history.clear();
        shown.clear();
        stagePos.clear();
        background = null;
        music = null;
        mode = Mode.SHOWING;
        current = null;
        index = 0;
        stepsTaken = 0;
        sceneId = script.startScene;
        if (script.scene(sceneId) == null) {
            mode = Mode.ENDED;
            throw new IllegalStateException("start scene '" + sceneId + "' does not exist");
        }
        step();
    }

    // ---------------- stepping ----------------

    /** Advances until something the player can see. Returns the new mode. */
    public Mode step() {
        if (mode == Mode.CHOOSING || mode == Mode.ENDED) return mode;

        int guard = 0;
        while (true) {
            if (++guard > 100_000 || ++stepsTaken > 2_000_000) {
                mode = Mode.ENDED;
                return mode;
            }
            Scene sc = script.scene(sceneId);
            if (sc == null) { mode = Mode.ENDED; return mode; }

            if (index >= sc.beats.size()) {
                // Fell off the end of a scene with no jump — the story
                // stops here rather than guessing where to go next.
                mode = Mode.ENDED;
                return mode;
            }

            Beat b = sc.beats.get(index);

            switch (b.kind) {
                case STAGE -> { applyStage(b); index++; }
                case SET -> { Expr.apply(b.varName + " = " + b.value, vars); index++; }
                case EFFECT -> { applyEffects(b.effects); index++; }
                case GOTO -> {
                    index++;
                    if (!enter(b.target)) { mode = Mode.ENDED; return mode; }
                }
                case TEXT -> {
                    index++;
                    applyEffects(b.effects);
                    current = b;
                    record(b.speaker, b.text);
                    mode = Mode.SHOWING;
                    return mode;
                }
                case CHOICE -> {
                    current = b;
                    mode = Mode.CHOOSING;
                    return mode;
                }
            }
        }
    }

    /** Player advances past the current line. No-op while choosing. */
    public Mode advance() {
        if (mode == Mode.CHOOSING || mode == Mode.ENDED) return mode;
        return step();
    }

    /** Picks an option from the currently offered choice beat. */
    public Mode choose(int i) {
        if (mode != Mode.CHOOSING || current == null || current.kind != Beat.Kind.CHOICE) {
            return mode;
        }
        List<Choice> avail = availableChoices();
        if (i < 0 || i >= avail.size()) return mode;
        Choice c = avail.get(i);

        applyEffects(c.effects);
        record(null, "> " + c.text);

        if (!enter(c.target)) { mode = Mode.ENDED; return mode; }
        mode = Mode.SHOWING;
        return step();
    }

    /** Options the player can actually pick right now (conditions met). */
    public List<Choice> availableChoices() {
        List<Choice> out = new ArrayList<>();
        if (current == null || current.kind != Beat.Kind.CHOICE) return out;
        for (Choice c : current.choices) {
            if (Expr.test(c.condition, vars)) out.add(c);
        }
        return out;
    }

    /** Every option on the current beat, gated or not — used by the
     *  bot, which needs to see what was authored, not what's unlocked. */
    public List<Choice> allChoices() {
        return current != null && current.kind == Beat.Kind.CHOICE
                ? new ArrayList<>(current.choices) : new ArrayList<>();
    }

    boolean enter(String target) {
        if (target == null) return false;
        if (target.equals("END")) { mode = Mode.ENDED; return true; }
        Scene sc = script.scene(target);
        if (sc == null) { mode = Mode.ENDED; return false; }
        sceneId = target;
        index = 0;
        visited.add(target);
        return true;
    }

    void applyEffects(List<String> effects) {
        for (String e : effects) Expr.apply(e, vars);
    }

    void applyStage(Beat b) {
        switch (b.directive) {
            case "bg" -> background = b.arg;
            case "music" -> music = b.arg;
            case "sfx" -> {}                    // fire and forget
            case "show" -> {
                shown.put(b.arg, b.arg2);
                if (b.arg3 != null) stagePos.put(b.arg, b.arg3);
                else stagePos.putIfAbsent(b.arg, "center");
            }
            case "hide" -> { shown.remove(b.arg); stagePos.remove(b.arg); }
        }
    }

    void record(String speaker, String text) {
        history.add(new String[]{speaker, text});
        if (history.size() > 5000) history.remove(0);
    }

    // ---------------- accessors for the renderer ----------------

    public String speaker() { return current == null ? null : current.speaker; }
    public String text() { return current == null ? "" : current.text; }
    public String pose(String character) { return shown.get(character); }
    public String position(String character) { return stagePos.getOrDefault(character, "center"); }

    // ---------------- snapshot / restore ----------------

    public Vn copy() {
        Vn v = new Vn(script);
        v.vars.clear();
        v.vars.putAll(vars);
        v.visited.clear();
        v.visited.addAll(visited);
        v.history.clear();
        for (String[] h : history) v.history.add(new String[]{h[0], h[1]});
        v.shown.clear();
        v.shown.putAll(shown);
        v.stagePos.clear();
        v.stagePos.putAll(stagePos);
        v.background = background;
        v.music = music;
        v.sceneId = sceneId;
        v.index = index;
        v.mode = mode;
        v.current = current;
        v.stepsTaken = stepsTaken;
        return v;
    }

    // ---------------- save / load ----------------

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unesc(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                out.append(switch (n) { case 't' -> '\t'; case 'n' -> '\n'; default -> n; });
            } else out.append(c);
        }
        return out.toString();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Aside save v1\n");
        sb.append("scene\t").append(esc(sceneId)).append('\n');
        sb.append("index\t").append(index).append('\n');
        sb.append("background\t").append(esc(background)).append('\n');
        sb.append("music\t").append(esc(music)).append('\n');
        for (var e : vars.entrySet()) {
            sb.append("var\t").append(esc(e.getKey())).append('\t')
              .append(esc(String.valueOf(e.getValue()))).append('\n');
        }
        for (String v : visited) sb.append("visited\t").append(esc(v)).append('\n');
        for (String[] h : history) {
            sb.append("hist\t").append(esc(h[0])).append('\t').append(esc(h[1])).append('\n');
        }
        for (var e : shown.entrySet()) {
            sb.append("shown\t").append(esc(e.getKey())).append('\t')
              .append(esc(e.getValue())).append('\t')
              .append(esc(stagePos.getOrDefault(e.getKey(), "center"))).append('\n');
        }
        return sb.toString();
    }

    /** Restores into a fresh Vn built on the same script. */
    public static Vn deserialize(Script script, String data) {
        Vn v = new Vn(script);
        v.vars.clear();
        v.visited.clear();
        v.history.clear();
        v.shown.clear();
        v.stagePos.clear();
        v.index = 0;
        v.mode = Mode.SHOWING;
        v.current = null;

        for (String line : data.split("\r?\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            switch (p[0]) {
                case "scene" -> v.sceneId = unesc(p[1]);
                case "index" -> v.index = parseInt(p[1], 0);
                case "background" -> v.background = p.length > 1 ? unesc(p[1]) : null;
                case "music" -> v.music = p.length > 1 ? unesc(p[1]) : null;
                case "var" -> v.vars.put(unesc(p[1]), Expr.coerce(unesc(p[2])));
                case "visited" -> v.visited.add(unesc(p[1]));
                case "hist" -> v.history.add(new String[]{unesc(p[1]), unesc(p[2])});
                case "shown" -> {
                    v.shown.put(unesc(p[1]), p[2].isEmpty() ? null : unesc(p[2]));
                    v.stagePos.put(unesc(p[1]), p.length > 3 ? unesc(p[3]) : "center");
                }
                default -> {}
            }
        }
        return v;
    }

    static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }

    public void save(Path file) throws IOException {
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    public static Vn load(Script script, Path file) throws IOException {
        return deserialize(script, Files.readString(file, StandardCharsets.UTF_8));
    }
}
