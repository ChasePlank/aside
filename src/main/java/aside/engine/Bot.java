package aside.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blind traversal for narrative.
 *
 * Walks every reachable path through the story and reports what the
 * author can't see from inside: scenes nothing leads to, jumps that
 * point at scenes that don't exist, choices whose condition is never
 * satisfiable, variables that are read but never written (usually a
 * typo), and scenes that just stop.
 *
 * It shares NOTHING with how the story was written — it only knows the
 * parsed beats. That's the point: a validator that reuses the author's
 * assumptions can't see the author's bugs.
 */
public class Bot {
    /** Wall-clock-free budget: max player-states expanded. */
    public int budget = 20_000;

    public static class Report {
        public Set<String> scenesReached = new LinkedHashSet<>();
        public Set<String> scenesDefined = new LinkedHashSet<>();
        public Set<String> choiceSitesOffered = new LinkedHashSet<>();
        public Set<String> choiceSitesAuthored = new LinkedHashSet<>();
        public List<String> missingTargets = new ArrayList<>();
        public List<String> deadEnds = new ArrayList<>();
        /** Beats sitting after a GOTO in the same scene — unreachable
         *  by construction, since the jump leaves before reaching them. */
        public List<String> unreachableBeats = new ArrayList<>();
        /** Distinct terminal scenes (paths may converge on the same one). */
        public Set<String> endings = new LinkedHashSet<>();
        /** How many explored paths ended at each terminal scene. */
        public Map<String, Integer> endingCounts = new LinkedHashMap<>();
        public Map<String, Double> varMin = new LinkedHashMap<>();
        public Map<String, Double> varMax = new LinkedHashMap<>();
        public Set<String> varsReadOnly = new LinkedHashSet<>();
        public Set<String> varsWritten = new LinkedHashSet<>();
        public int pathsExplored = 0;
        public int statesExpanded = 0;
        public boolean budgetHit = false;
        public List<String> warnings = new ArrayList<>();

        public Set<String> unreachableScenes() {
            Set<String> u = new LinkedHashSet<>(scenesDefined);
            u.removeAll(scenesReached);
            return u;
        }

        public Set<String> neverOfferedChoices() {
            Set<String> u = new LinkedHashSet<>(choiceSitesAuthored);
            u.removeAll(choiceSitesOffered);
            return u;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("paths explored:      ").append(pathsExplored).append('\n');
            sb.append("states expanded:     ").append(statesExpanded)
              .append(budgetHit ? "  (BUDGET HIT — results partial)" : "").append('\n');
            sb.append("scenes:              ").append(scenesReached.size())
              .append('/').append(scenesDefined.size()).append(" reached\n");
            sb.append("distinct endings:    ").append(endings.size()).append('\n');
            for (String e : endings) {
                sb.append("    -> ").append(e)
                  .append("   (").append(endingCounts.getOrDefault(e, 0))
                  .append(" path").append(endingCounts.getOrDefault(e, 0) == 1 ? "" : "s")
                  .append(")\n");
            }

            var u = unreachableScenes();
            sb.append("unreachable scenes:  ").append(u.size()).append('\n');
            for (String s : u) sb.append("    !! ").append(s).append('\n');

            sb.append("missing targets:     ").append(missingTargets.size()).append('\n');
            for (String s : missingTargets) sb.append("    !! ").append(s).append('\n');

            sb.append("dead ends:           ").append(deadEnds.size()).append('\n');
            for (String s : deadEnds) sb.append("    !! ").append(s).append('\n');

            sb.append("unreachable beats:   ").append(unreachableBeats.size()).append('\n');
            for (String s : unreachableBeats) sb.append("    !! ").append(s).append('\n');

            var n = neverOfferedChoices();
            sb.append("never-offered picks: ").append(n.size()).append('\n');
            for (String s : n) sb.append("    !! ").append(s).append('\n');

            sb.append("vars read but never written: ").append(varsReadOnly.size()).append('\n');
            for (String s : varsReadOnly) sb.append("    !! ").append(s).append('\n');

            if (!warnings.isEmpty()) {
                sb.append("warnings:\n");
                for (String w : warnings) sb.append("    ~ ").append(w).append('\n');
            }
            return sb.toString();
        }
    }

    public Report run(Script script) {
        Report r = new Report();
        for (String id : script.scenes.keySet()) r.scenesDefined.add(id);
        for (String w : script.warnings) r.warnings.add(w);

        // What the script reads vs writes — a read-only variable is
        // almost always a typo in a condition.
        for (Scene sc : script.scenes.values()) {
            for (Beat b : sc.beats) {
                if (b.kind == Beat.Kind.SET) r.varsWritten.add(b.varName);
                for (String e : b.effects) r.varsWritten.add(effectName(e));
                if (b.kind == Beat.Kind.GOTO) checkTarget(script, b.target, b.line, sc.id, r);
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) {
                        r.choiceSitesAuthored.add(site(sc.id, c.line, c.text));
                        checkTarget(script, c.target, c.line, sc.id, r);
                        for (String e : c.effects) r.varsWritten.add(effectName(e));
                        if (c.condition != null) {
                            for (String v : varsIn(c.condition)) {
                                if (!isWritten(script, v)) r.varsReadOnly.add(v);
                            }
                        }
                    }
                }
            }
        }

        // Scenes that fall off the end with nothing to continue into
        for (Scene sc : script.scenes.values()) {
            if (sc.endsOpen() && sc.beats.size() > 0) {
                r.deadEnds.add(sc.id + " (line " + sc.line + ")");
            }
        }

        // Beats authored after a GOTO never run — the jump leaves first
        for (Scene sc : script.scenes.values()) {
            boolean afterJump = false;
            for (Beat b : sc.beats) {
                if (afterJump) {
                    r.unreachableBeats.add(sc.id + ": line " + b.line
                            + " (" + b.kind + ") sits after a '-> ' jump");
                }
                if (b.kind == Beat.Kind.GOTO) afterJump = true;
            }
        }

        // --- traversal ---
        Deque<Vn> stack = new ArrayDeque<>();
        Vn root = new Vn(script);
        stack.push(root);

        while (!stack.isEmpty()) {
            if (r.statesExpanded >= budget) { r.budgetHit = true; break; }
            Vn v = stack.pop();
            r.statesExpanded++;

            // Run this state to a decision point or an ending
            int spin = 0;
            while (v.mode == Vn.Mode.SHOWING && spin++ < 100_000) v.advance();

            r.scenesReached.addAll(v.visited);
            if (v.sceneId != null) r.scenesReached.add(v.sceneId);
            noteVars(r, v.vars);

            if (v.mode == Vn.Mode.ENDED) {
                r.pathsExplored++;
                String key = v.sceneId == null ? "(no scene)" : v.sceneId;
                r.endings.add(key);
                r.endingCounts.merge(key, 1, Integer::sum);
                continue;
            }

            if (v.mode == Vn.Mode.CHOOSING) {
                List<Choice> avail = v.availableChoices();
                for (Choice c : avail) r.choiceSitesOffered.add(site(v.sceneId, c.line, c.text));
                if (avail.isEmpty()) {
                    r.deadEnds.add(v.sceneId + " — choice at line "
                            + (v.current != null ? v.current.line : -1)
                            + " has NO satisfiable options");
                    r.pathsExplored++;
                    continue;
                }
                for (int i = 0; i < avail.size(); i++) {
                    Vn branch = v.copy();
                    // Re-find the same choice in the clone by site
                    List<Choice> av2 = branch.availableChoices();
                    int idx = -1;
                    for (int j = 0; j < av2.size(); j++) {
                        if (av2.get(j).line == avail.get(i).line) { idx = j; break; }
                    }
                    if (idx < 0) continue;
                    branch.choose(idx);
                    stack.push(branch);
                }
            }
        }

        return r;
    }

    static String site(String scene, int line, String text) {
        return scene + ":" + line + " \"" + text + "\"";
    }

    static void noteVars(Report r, Map<String, Object> vars) {
        for (var e : vars.entrySet()) {
            Double d = Expr.asNumber(e.getValue());
            if (d == null) continue;
            r.varMin.merge(e.getKey(), d, Math::min);
            r.varMax.merge(e.getKey(), d, Math::max);
        }
    }

    static void checkTarget(Script s, String target, int line, String from, Report r) {
        if (target == null) return;
        if (target.equals("END")) return;
        if (s.scene(target) == null) {
            r.missingTargets.add(from + " -> '" + target + "' (line " + line + ")");
        }
    }

    static String effectName(String effect) {
        int i = 0;
        while (i < effect.length()
                && (Character.isLetterOrDigit(effect.charAt(i)) || effect.charAt(i) == '_')) i++;
        return effect.substring(0, i);
    }

    static boolean isWritten(Script s, String name) {
        for (Scene sc : s.scenes.values()) {
            for (Beat b : sc.beats) {
                if (b.kind == Beat.Kind.SET && b.varName.equals(name)) return true;
                for (String e : b.effects) if (effectName(e).equals(name)) return true;
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) {
                        for (String e : c.effects) if (effectName(e).equals(name)) return true;
                    }
                }
            }
        }
        return false;
    }

    static List<String> varsIn(String condition) {
        List<String> out = new ArrayList<>();
        for (String t : Expr.tokenize(condition)) {
            if (t.isEmpty()) continue;
            char c0 = t.charAt(0);
            if (Character.isLetter(c0) || c0 == '_') {
                if (t.equals("and") || t.equals("or") || t.equals("not")) continue;
                out.add(t);
            }
        }
        return out;
    }
}
