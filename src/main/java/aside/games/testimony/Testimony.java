package aside.games.testimony;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Testimony -- the rules, with nothing drawn.
 *
 * Three things happen here and they are the game:
 *
 *   1. You are shown an evening once. Then it is taken away.
 *   2. You are asked about it. Every answer you give is written into your
 *      memory whether or not it was right, and the memory keeps no record
 *      of which ones were right. There is no field for that, on purpose.
 *      A memory that could tell you it was wrong would not be a memory.
 *   3. Your account is read back to you as settled fact, and then the
 *      evening is placed next to it.
 *
 * This is the third verb in the engine's small collection. Residue is
 * about leaving, Ledger is about recording, and this one is about
 * recalling -- the only one of the three that changes the thing it is
 * about. Nothing in Residue or Ledger can alter what already happened.
 * Here, the act of answering is the damage.
 *
 * Two mechanics carry that:
 *
 *   PROPAGATION. A later question is phrased using your earlier answer.
 *   If you said the coat was blue, you are later asked about the woman in
 *   the blue coat, and the question will feel more familiar than the
 *   truth would have. Confidence is manufactured by consistency, and
 *   consistency is manufactured by you.
 *
 *   THE LEADING QUESTION. One question offers two things that never
 *   happened and one that did: that she said nothing. It is the only
 *   question where the honest answer is on the list, and it is placed
 *   last, after seven questions have taught you that an answer is always
 *   available.
 */
public final class Testimony {

    // -------------------------------------------------------------- content

    public record Option(String id, String text, String phrase) {}

    /**
     * One question, its true answer, and what it contributes to the
     * account. {@code prompt} and {@code accountLine} may reference another
     * question's answer as %id%, which is how propagation happens --
     * the phrasing follows the player's memory, not the truth.
     */
    public record Question(String id, String prompt, String truth,
                           List<Option> options, String note, String accountLine) {

        public Option option(String optionId) {
            for (Option o : options) if (o.id().equals(optionId)) return o;
            return null;
        }

        public String phrase(String optionId) {
            Option o = option(optionId);
            return o == null ? "?" : o.phrase();
        }

        public String text(String optionId) {
            Option o = option(optionId);
            return o == null ? "\u2014" : o.text();
        }
    }

    public static final String SCENE_TITLE = "THE BUS STOP, 6:40 PM";

    public static final String SCENE = """
            It had rained. The pavement was dark, the bench was wet, and nobody was
            sitting on it.

            The streetlight above the shelter was out. The one across the road was
            working, and it was the only reason you could see any of this.

            A woman in a green coat stood under the shelter with a folded umbrella,
            not using it. A man read a newspaper standing outside the shelter, in the
            wet, apparently not noticing. A small brown dog was tied to the end of the
            bench. It watched the road.

            The bus was the 14, and it was late. A boy sat on the wall eating chips out
            of a paper bag. Somewhere behind you a shop was playing something you could
            not name. A red car went past twice, the second time slower.

            The timetable on the post had been torn at one corner. Somebody had
            scratched two initials into the bench. There was a paper cup on the ground,
            on its side, and it had been there long enough to fill.""";

    /** Shown under the scene, before it is taken away. */
    public static final String SCENE_NOTE =
            "This is everything you saw. Nothing else happened that you noticed.";

    public static final List<Question> QUESTIONS = List.of(

        new Question("coat",
            "The woman under the shelter. What colour was her coat?",
            "green",
            List.of(new Option("green", "Green", "green"),
                    new Option("blue",  "Blue",  "blue"),
                    new Option("grey",  "Grey",  "grey")),
            "Green. It was the first thing you noticed about her, and the only green in the scene.",
            "The woman under the shelter wore a %this% coat."),

        new Question("umbrella",
            "Her umbrella.",
            "folded",
            List.of(new Option("folded", "Folded, not in use",   "folded, not in use"),
                    new Option("open",   "Open, over her head",  "open, over her head"),
                    new Option("none",   "She had no umbrella",  "not there at all")),
            "Folded. She stood under a shelter holding a closed umbrella, which is a particular kind of waiting.",
            "Her umbrella was %this%."),

        new Question("dog",
            "The dog.",
            "tied",
            List.of(new Option("tied", "Tied to the end of the bench",       "tied to the end of the bench"),
                    new Option("held", "Held by the man with the newspaper", "held by the man with the newspaper"),
                    new Option("loose", "Loose, on the road",                "loose, on the road")),
            "Tied to the bench. It watched the road, and nobody was holding it.",
            "The dog was %this%."),

        new Question("light",
            "The streetlight above the shelter.",
            "off",
            List.of(new Option("off",     "It was out",       "was out"),
                    new Option("on",      "It was on",        "was on"),
                    new Option("flicker", "It was flickering", "was flickering")),
            "Out. The only working light was the one across the road, which is why the scene had a direction.",
            "The streetlight above the shelter %this%."),

        new Question("bus",
            "Which bus was late?",
            "n14",
            List.of(new Option("n14", "The 14", "the 14"),
                    new Option("n41", "The 41", "the 41"),
                    new Option("n4",  "The 4",  "the 4")),
            "The 14. You read it off the front of a bus that had not arrived, which is a strange thing to be able to read.",
            "The bus was %this%, and it was late."),

        new Question("car",
            "The red car.",
            "twice",
            List.of(new Option("once",   "It went past once",        "once"),
                    new Option("twice",  "It went past twice",       "twice"),
                    new Option("thrice", "It went past three times", "three times")),
            "Twice, and slower the second time. You noticed the second time because of the first.",
            "A red car went past %this%."),

        new Question("timetable",
            "The timetable on the post.",
            "torn",
            List.of(new Option("torn",    "Torn at one corner", "torn at one corner"),
                    new Option("missing", "It was missing",     "missing entirely"),
                    new Option("glass",   "It was behind glass","behind glass")),
            "Torn at one corner. Somebody had started to take it and stopped.",
            "The timetable on the post was %this%."),

        // The leading question, and the propagation. Both in one line.
        new Question("speech",
            "The woman in the %coat% coat. What did she say?",
            "silent",
            List.of(new Option("complained", "She complained about the bus",     "complained about the bus"),
                    new Option("time",       "She asked the man for the time",   "asked the man for the time"),
                    new Option("silent",     "She did not speak",                "did not speak")),
            "She did not speak. You saw everything that happened at that stop, and nothing was said. "
                + "The question offered you two sentences she never said -- and a colour she may not have been wearing.",
            "The woman %this%."));

    // ----------------------------------------------------------------- state

    /** question id -> chosen option id. This is the whole of the memory. */
    public final Map<String, String> memory = new LinkedHashMap<>();

    /** question id -> whether the player claimed to be sure. */
    public final Map<String, Boolean> sure = new LinkedHashMap<>();

    /** question ids, in the order they were answered. */
    public final List<String> order = new ArrayList<>();

    public boolean finished = false;

    public static Question byId(String id) {
        for (Question q : QUESTIONS) if (q.id().equals(id)) return q;
        return null;
    }

    public int index() { return order.size(); }

    public boolean done() { return order.size() >= QUESTIONS.size(); }

    /** The question waiting to be answered, or null when there is none. */
    public Question current() { return done() ? null : QUESTIONS.get(order.size()); }

    /**
     * Answer the current question.
     *
     * The answer is written into memory whether or not it is true, and
     * nothing anywhere records which. An unknown option id is refused
     * rather than silently stored, because a memory of a thing that was
     * never offered is how the real thing breaks.
     */
    public boolean answer(String optionId, boolean confident) {
        Question q = current();
        if (q == null) return false;
        if (q.option(optionId) == null) return false;
        order.add(q.id());
        memory.put(q.id(), optionId);
        sure.put(q.id(), confident);
        if (done()) finished = true;
        return true;
    }

    /** The prompt for a question with the player's own answers filled in. */
    public String promptFor(Question q) {
        String s = q.prompt();
        for (Question other : QUESTIONS) {
            // The fallback only fires if a cross-referenced question has not
            // been asked yet, which the fixed order makes impossible in play.
            String chosen = memory.getOrDefault(other.id(), other.truth());
            s = s.replace("%" + other.id() + "%", other.phrase(chosen));
        }
        return s;
    }

    // ------------------------------------------------------------- scoring

    public boolean correct(String qid) {
        Question q = byId(qid);
        return q != null && q.truth().equals(memory.get(qid));
    }

    public int correctCount() {
        int n = 0;
        for (String qid : order) if (correct(qid)) n++;
        return n;
    }

    public int wrongCount() { return order.size() - correctCount(); }

    public int correctSure() {
        int n = 0;
        for (String qid : order) if (correct(qid) && Boolean.TRUE.equals(sure.get(qid))) n++;
        return n;
    }

    public int correctUnsure() {
        int n = 0;
        for (String qid : order) if (correct(qid) && !Boolean.TRUE.equals(sure.get(qid))) n++;
        return n;
    }

    public int wrongSure() {
        int n = 0;
        for (String qid : order) if (!correct(qid) && Boolean.TRUE.equals(sure.get(qid))) n++;
        return n;
    }

    public int wrongUnsure() {
        int n = 0;
        for (String qid : order) if (!correct(qid) && !Boolean.TRUE.equals(sure.get(qid))) n++;
        return n;
    }

    public String closing() {
        int wrong = wrongCount();
        if (order.isEmpty()) {
            return "You answered nothing. An account that says nothing cannot be wrong, "
                    + "and it cannot be any use either.";
        }
        if (wrong == 0) {
            return "You saw it, you kept it, and you did not add to it. Almost nobody does.";
        }
        if (wrongSure() == 0) {
            return "You were wrong " + wrong + " time" + (wrong == 1 ? "" : "s")
                    + ", and every time you said you were not sure. That is not the same as being right -- "
                    + "but it is the honest half of it, and it is the half that can be worked with.";
        }
        return "You were wrong " + wrong + " time" + (wrong == 1 ? "" : "s")
                + ". You were sure " + wrongSure() + " of those. The second number is the one that matters: "
                + "the wrong things you doubted will be checked, and the wrong things you were certain of are "
                + "already in the account.";
    }

    // ------------------------------------------------------------- account

    /** The account as the player would give it: assembled from memory. */
    public List<String> account() { return accountFrom(memory); }

    /**
     * The account as it stands right now -- only the questions already
     * answered. This is what the player watches being written during the
     * interview, and it is the reason the panel is not a quiz scoreboard:
     * it reads as prose, and prose does not hedge.
     */
    public List<String> accountSoFar() {
        List<String> out = new ArrayList<>();
        for (String qid : order) {
            Question q = byId(qid);
            if (q == null) continue;
            String s = q.accountLine().replace("%this%", q.phrase(memory.get(qid)));
            for (Question other : QUESTIONS) {
                String chosen = memory.get(other.id());
                if (chosen != null) s = s.replace("%" + other.id() + "%", other.phrase(chosen));
            }
            out.add(s);
        }
        return out;
    }

    /** The same account, assembled from what actually happened. */
    public List<String> truthAccount() { return accountFrom(truthMemory()); }

    public static Map<String, String> truthMemory() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Question q : QUESTIONS) m.put(q.id(), q.truth());
        return m;
    }

    static List<String> accountFrom(Map<String, String> m) {
        List<String> out = new ArrayList<>();
        for (Question q : QUESTIONS) {
            String s = q.accountLine();
            s = s.replace("%this%", q.phrase(m.get(q.id())));
            for (Question other : QUESTIONS) {
                s = s.replace("%" + other.id() + "%", other.phrase(m.get(other.id())));
            }
            out.add(s);
        }
        return out;
    }

    // -------------------------------------------------------------- saving

    public void save(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String qid : order) {
            sb.append(qid).append('|').append(memory.get(qid)).append('|')
              .append(Boolean.TRUE.equals(sure.get(qid)) ? "1" : "0").append('\n');
        }
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, sb.toString());
    }

    public static Testimony load(Path p) {
        Testimony t = new Testimony();
        if (p == null || !Files.exists(p)) return t;
        try {
            for (String line : Files.readAllLines(p)) {
                if (line.isBlank()) continue;
                String[] f = line.split("\\|");
                if (f.length < 3) continue;
                Question q = byId(f[0]);
                if (q == null || q.option(f[1]) == null) continue;
                if (t.memory.containsKey(f[0])) continue;   // a duplicate line is not a second memory
                t.order.add(f[0]);
                t.memory.put(f[0], f[1]);
                t.sure.put(f[0], "1".equals(f[2]));
            }
        } catch (IOException ignored) { }
        t.finished = t.done();
        return t;
    }

    public Testimony() {}
}
