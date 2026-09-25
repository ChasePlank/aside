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
    /** Wall-clock-free budget: max player-states expanded.
     *  A five-night branching story with accumulating variables needs
     *  far more than a short one -- 20k truncated it mid-story and the
     *  partial results looked like real defects. */
    public int budget = Integer.getInteger("aside.budget", 600_000);

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
        /** Written but never read anywhere — usually a typo'd name, e.g.
         *  `~ listed +1` when every condition tests `listened`. */
        public Set<String> varsNeverRead = new LinkedHashSet<>();
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
            sb.append("unreachable scenes:  ").append(u.size())
              .append(budgetHit ? "   (UNRELIABLE: traversal did not finish)" : "")
              .append('\n');
            for (String s : u) sb.append("    ").append(budgetHit ? "?? " : "!! ").append(s).append('\n');

            sb.append("missing targets:     ").append(missingTargets.size()).append('\n');
            for (String s : missingTargets) sb.append("    !! ").append(s).append('\n');

            sb.append("dead ends:           ").append(deadEnds.size()).append('\n');
            for (String s : deadEnds) sb.append("    !! ").append(s).append('\n');

            sb.append("unreachable beats:   ").append(unreachableBeats.size()).append('\n');
            for (String s : unreachableBeats) sb.append("    !! ").append(s).append('\n');

            var n = neverOfferedChoices();
            sb.append("never-offered picks: ").append(n.size())
              .append(budgetHit ? "   (UNRELIABLE: traversal did not finish)" : "")
              .append('\n');
            for (String s : n) sb.append("    ").append(budgetHit ? "?? " : "!! ").append(s).append('\n');

            sb.append("vars read but never written: ").append(varsReadOnly.size()).append('\n');
            for (String s : varsReadOnly) sb.append("    !! ").append(s).append('\n');

            sb.append("vars written but never read: ").append(varsNeverRead.size()).append('\n');
            for (String s : varsNeverRead) sb.append("    !! ").append(s).append('\n');

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
        collectThresholds(script);

        // What the script reads vs writes — a read-only variable is
        // almost always a typo in a condition.
        for (Scene sc : script.scenes.values()) {
            for (Beat b : sc.beats) {
                if (b.kind == Beat.Kind.SET) r.varsWritten.add(b.varName);
                for (String e : b.effects) r.varsWritten.add(effectName(e));
                if (b.kind == Beat.Kind.GOTO) checkTarget(script, b.target, b.line, sc.id, r);
                if (b.kind == Beat.Kind.IFGOTO) {
                    checkTarget(script, b.target, b.line, sc.id, r);
                    if (b.condition == null || b.condition.isBlank()) {
                        r.warnings.add(sc.id + ":" + b.line
                                + " 'if' with an empty condition — use a plain '->'");
                    }
                }
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) {
                        r.choiceSitesAuthored.add(site(sc.id, c.line, c.text));
                        checkTarget(script, c.target, c.line, sc.id, r);
                        for (String e : c.effects) r.varsWritten.add(effectName(e));
                    }
                }
                // Any condition — on a choice or an `if` jump — that
                // reads a variable nothing ever writes is a typo.
                if (b.condition != null) {
                    for (String v : varsIn(b.condition)) {
                        if (!isWritten(script, v)) r.varsReadOnly.add(v);
                    }
                }
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) {
                        if (c.condition == null) continue;
                        for (String v : varsIn(c.condition)) {
                            if (!isWritten(script, v)) r.varsReadOnly.add(v);
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

        // Anything written that no condition ever reads is dead -- and
        // nearly always a misspelling of something else that IS read.
        for (String w : r.varsWritten) {
            boolean read = false;
            for (Scene sc : script.scenes.values()) {
                for (Beat b : sc.beats) {
                    if (readsVar(b, w)) { read = true; break; }
                }
                if (read) break;
            }
            if (!read) r.varsNeverRead.add(w);
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
        // FIFO, not LIFO. Depth-first descends into one branch's entire
        // subtree before touching its siblings -- with a branching
        // factor of 3 over 15 choice points that single subtree is
        // millions of states, so the budget ran out having explored one
        // corridor of the story and never returned to Night 1's other
        // options. Those then got reported as unreachable when they were
        // simply unvisited yet. Breadth-first covers the whole story
        // evenly, which is the correct order for a reachability audit.
        Deque<Vn> stack = new ArrayDeque<>();
        Vn root = new Vn(script);
        stack.addLast(root);

        // Dedupe on story state, not on path. Many different routes lead
        // to the same (scene, position, variables), and re-exploring each
        // one makes the search exponential -- a five-night story blew
        // past 20k states and returned partial, misleading results.
        // Collapsing identical states makes this a graph search, so the
        // budget goes on genuinely new territory.
        Set<String> seen = new LinkedHashSet<>();

        while (!stack.isEmpty()) {
            if (r.statesExpanded >= budget) { r.budgetHit = true; break; }
            Vn v = stack.pollFirst();

            String sig = signature(v);
            if (!seen.add(sig)) continue;
            r.statesExpanded++;

            // Run this state to a decision point or an ending
            int spin = 0;
            while (v.mode == Vn.Mode.SHOWING && spin++ < 100_000) v.advance();

            // Record reachability AFTER the advance, not before. The
            // advance loop is what walks the story forward, and every
            // scene it passes through is added to `visited` on the way.
            // Recording before it meant those scenes were never counted,
            // so finished endings were reported as unreachable while
            // simultaneously appearing in the endings list.
            r.scenesReached.addAll(v.enteredLog);
            v.enteredLog.clear();
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
                    Vn branch = v.copyForSearch();
                    // Re-find the same choice in the clone by site
                    List<Choice> av2 = branch.availableChoices();
                    int idx = -1;
                    for (int j = 0; j < av2.size(); j++) {
                        if (av2.get(j).line == avail.get(i).line) { idx = j; break; }
                    }
                    if (idx < 0) continue;
                    branch.choose(idx);
                    stack.addLast(branch);
                }
            }
        }

        return r;
    }

    /** Canonical signature of a story state: where we are, how far
     *  through, and what every variable currently holds. */
    String signature(Vn v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.sceneId).append('|').append(v.index).append('|');
        List<String> keys = new ArrayList<>(v.vars.keySet());
        java.util.Collections.sort(keys);
        for (String k : keys) sb.append(k).append('=').append(bucket(v.vars.get(k))).append(';');
        return sb.toString();
    }

    /** Every integer literal the script compares against. A variable's
     *  exact value does not matter -- only how it compares to these. */
    final java.util.TreeSet<Integer> thresholds = new java.util.TreeSet<>();

    void collectThresholds(Script script) {
        for (Scene sc : script.scenes.values()) {
            for (Beat b : sc.beats) {
                addThresholds(b.condition);
                if (b.kind == Beat.Kind.CHOICE) {
                    for (Choice c : b.choices) addThresholds(c.condition);
                }
            }
        }
    }

    void addThresholds(String condition) {
        if (condition == null) return;
        for (String t : Expr.tokenize(condition)) {
            try { thresholds.add(Integer.parseInt(t.trim())); } catch (Exception ignored) { }
        }
    }

    /**
     * Collapse a variable value to what the story can actually
     * distinguish. Two counts are equivalent if every `>=` in the
     * script answers the same for both -- so values between adjacent
     * thresholds are one state, and anything at or above the largest
     * threshold is one state.
     *
     * Without this, a five-night story with four accumulating affinity
     * counters has hundreds of thousands of distinct states that are
     * behaviourally identical, and the traversal never finishes.
     */
    Object bucket(Object value) {
        Double d = Expr.asNumber(value);
        if (d == null) return value;
        if (thresholds.isEmpty()) return value;
        int v = (int) Math.floor(d);
        Integer best = null;
        for (int t : thresholds) {
            if (t <= v) best = t;
            else break;
        }
        // Below every threshold, counts collapse to one bucket too
        return best == null ? "low" : String.valueOf(best);
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

    /** Does this beat gate on the named variable? */
    static boolean readsVar(Beat b, String name) {
        if (b.condition != null && varsIn(b.condition).contains(name)) return true;
        if (b.kind == Beat.Kind.CHOICE) {
            for (Choice c : b.choices) {
                if (c.condition != null && varsIn(c.condition).contains(name)) return true;
            }
        }
        return false;
    }
}
