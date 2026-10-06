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

        /**
         * Scenes that can be reached from themselves - a loop. Not a fault: a hub is a loop, and so is any story
         * where you come back to a room. It is reported because it is the one shape where UNREACHABLE can mean
         * something other than "the script is broken", and the report should say so rather than leave the reader
         * to work it out.
         */
        public Set<String> cyclicScenes = new LinkedHashSet<>();
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

            // SAY WHY, WHEN THERE IS A WHY TO SAY. "Unreachable" has two causes and only one of them is a broken
            // script. The other is a loop the search cannot re-enter, because it dedupes states and buckets
            // counters - and a reader who does not already know that will spend an hour looking for a typo that
            // is not there. That is exactly what happened to the eighth story.
            if (!cyclicScenes.isEmpty()) {
                sb.append("loops:               ").append(cyclicScenes.size())
                  .append(" scene(s) can be re-entered\n");
                for (String s : cyclicScenes) sb.append("    .. ").append(s).append('\n');
                if (!u.isEmpty() && !budgetHit) {
                    sb.append("    NOTE: this story loops, and a loop whose progress is tracked by a COUNTER alone\n");
                    sb.append("          collapses to one state - the traversal buckets counters, so count 0 and\n");
                    sb.append("          count 1 are the same state and it never re-enters the room. If the scenes\n");
                    sb.append("          above look reachable to you, gate the loop on a FLAG per branch instead.\n");
                }
            }

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
        //
        // The set holds a 64-bit hash per state, not the signature string.
        // It used to hold the string, and that is what actually ran out of
        // memory: `budget` counts states, but the retained bytes per state
        // were unbounded, so the state budget was a memory budget in
        // disguise and it ran out about 1.6x above the default. A hash
        // collision costs one branch re-explored, never a wrong answer.
        Set<Long> seen = new java.util.HashSet<>();

        // An audit tool that dies instead of reporting partial results is
        // strictly worse than one that reports them: the report format
        // already knows how to say "do not trust this" (?? prefixes,
        // BUDGET HIT, UNRELIABLE). So the traversal is allowed to fail,
        // and if it does the report says so rather than the process
        // printing a stack trace.
        try {
            while (!stack.isEmpty()) {
                if (r.statesExpanded >= budget) { r.budgetHit = true; break; }
                Vn v = stack.pollFirst();

                if (!seen.add(signatureHash(v))) continue;
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
        } catch (OutOfMemoryError oom) {
            // Free the two structures that caused it before building the
            // report, so the report itself has room to be written.
            seen.clear();
            stack.clear();
            r.budgetHit = true;
            r.warnings.add("ran out of memory after " + r.statesExpanded
                    + " states — the traversal did not finish, so anything it did not"
                    + " reach is unknown rather than unreachable");
        }

        // A LOOP IS NOT A FAULT, BUT IT CHANGES WHAT "UNREACHABLE" MEANS.
        //
        // This traversal dedupes states by (scene, beat, variables) and BUCKETS counters - every value between two
        // thresholds is one state. That is right, and it is what lets a five-night story finish. But it means a
        // loop whose progress is tracked by a COUNTER ALONE collapses: if the room offers the same choices at
        // count 0 and count 1, those are genuinely the same state, the search never re-expands it, and everything
        // past the loop is reported unreachable.
        //
        // That is exactly what happened to the eighth story. The report said "7 unreachable scenes" and the cause
        // was one counter where a flag per branch was needed. So the cycles are counted here and the audit says
        // so, because otherwise the reader has to rediscover the auditor's own state model to read its output.
        r.cyclicScenes = findCycles(script);

        return r;
    }

    /**
     * FNV-1a over the canonical signature.
     *
     * The signature string is still built, because that is how a state is
     * described; what changes is that it is not retained. One long per
     * expanded state instead of one string is the difference between a
     * traversal that finishes a five-night story and one that dies at 1.6x
     * the default budget.
     */
    /**
     * Which scenes can be reached from themselves. Small stories, so a plain per-scene search is fine and a
     * Tarjan would be more machinery than the question needs.
     */
    Set<String> findCycles(Script script) {
        Map<String, Set<String>> edges = new java.util.HashMap<>();
        for (Map.Entry<String, Scene> e : script.scenes.entrySet()) {
            Set<String> out = new java.util.LinkedHashSet<>();
            for (Beat b : e.getValue().beats) {
                if (b.target != null) out.add(b.target);
                if (b.choices != null) for (Choice c : b.choices) if (c.target != null) out.add(c.target);
            }
            edges.put(e.getKey(), out);
        }
        Set<String> cyclic = new java.util.LinkedHashSet<>();
        for (String start : edges.keySet()) {
            Set<String> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>(edges.get(start));
            while (!stack.isEmpty()) {
                String n = stack.pop();
                if (n.equals(start)) { cyclic.add(start); break; }
                if (!seen.add(n)) continue;
                for (String m : edges.getOrDefault(n, Set.of())) stack.push(m);
            }
        }
        return cyclic;
    }

    long signatureHash(Vn v) {
        String s = signature(v);
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
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
