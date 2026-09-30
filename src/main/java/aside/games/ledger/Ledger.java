package aside.games.ledger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ledger, the whole game, with nothing drawn.
 *
 * You are the night clerk of a small hotel. Six nights happen to you. You
 * keep a ledger with {@link #CAPACITY} lines in it, and the hotel does not
 * care how many things you notice -- only how many you can hold.
 *
 * The game is the erasure. Everything you are shown is true and everything
 * you are shown is worth something; you simply cannot keep it. Recording a
 * sixth thing costs you the first thing, and the first thing might be the
 * one the inspector asks about.
 *
 * The rule that makes it a game rather than a memory test: **you are never
 * told what will be asked.** The questions come from a person who was not
 * there, weeks later, and they do not ask about what looked important.
 * They ask about what was actually true. A ledger of "clues" is not a
 * record; it is a guess about the future, and guesses about the future are
 * how records get lost.
 *
 * This class is pure Java and holds no JavaFX. The window onto it lives in
 * {@link LedgerScreen}, and the standalone repo runs this file headless.
 */
public final class Ledger {

    /**
     * How many lines the ledger has.
     *
     * Five, and there are twenty-four things worth writing down. The gap is
     * the game. A ledger that held everything would only be a filing
     * cabinet, and nothing would ever have to be chosen.
     */
    public static final int CAPACITY = 5;

    // ------------------------------------------------------------- content

    /** One thing you noticed, in the words you would have used. */
    public static final class Detail {
        public final String id;
        public final String text;
        public Detail(String id, String text) { this.id = id; this.text = text; }
    }

    /** One night: a scene, and everything in it you could write down. */
    public static final class Night {
        /** The label down the side: "Night one". */
        public final String heading;
        /** What the night is, in a few words. */
        public final String title;
        public final String scene;
        public final List<Detail> details;
        Night(String heading, String title, String scene, List<Detail> details) {
            this.heading = heading;
            this.title = title;
            this.scene = scene;
            this.details = details;
        }
    }

    /** A question from somebody who was not there, and the line that answers it. */
    public static final class Reckoning {
        public final String question;
        public final String answerId;
        public final String explanation;
        Reckoning(String question, String answerId, String explanation) {
            this.question = question;
            this.answerId = answerId;
            this.explanation = explanation;
        }
    }

    static Detail d(String id, String text) { return new Detail(id, text); }

    public static final List<Night> NIGHTS = List.of(

        new Night("Night one", "The man who would not leave his coat",
            "It is raining on the street and the desk lamp is the only light on this floor. "
          + "A man comes in at half past eleven, wet through, and stands at the desk "
          + "without shaking the water off. He takes room nine.",
            List.of(
                d("n1a", "He signed the register \"R. Ainsley\"."),
                d("n1b", "He paid in exact change, and counted it twice."),
                d("n1c", "He asked for a room facing the yard, not the street."),
                d("n1d", "He would not leave his coat at the desk."))),

        new Night("Night two", "The woman in room four",
            "Room four's bell rings at two in the morning. The woman who comes down "
          + "is not dressed for the hour, and she does not look at the clock.",
            List.of(
                d("n2a", "She asked what time the kitchen opened."),
                d("n2b", "She wore a red scarf, indoors, in February."),
                d("n2c", "She was carrying a letter she did not post."),
                d("n2d", "She called you \"Mr. Pell\", and then corrected herself."))),

        new Night("Night three", "The boy who sweeps",
            "The boy who sweeps the front steps comes in out of the cold and does "
          + "not go home. He stands by the radiator until it is warm, and then a "
          + "while longer.",
            List.of(
                d("n3a", "He swept the same three steps twice."),
                d("n3b", "He asked whether the man in room nine had paid."),
                d("n3c", "He left his cap on the banister and forgot it."),
                d("n3d", "He said his sister used to work here."))),

        new Night("Night four", "The yard door",
            "At three, the draught from the back tells you the yard door is open. "
          + "The rain has stopped, and the yard is very quiet.",
            List.of(
                d("n4a", "The yard door was unlocked."),
                d("n4b", "There were wet footprints going out, not in."),
                d("n4c", "The lamp over the yard door had been unscrewed, not broken."),
                d("n4d", "A red thread was caught on the latch."))),

        new Night("Night five", "Room nine is empty",
            "You take the nine o'clock round and room nine does not answer. The key "
          + "is on the inside of the door, so you let yourself in.",
            List.of(
                d("n5a", "The bed had not been slept in."),
                d("n5b", "The window stood open, though it had rained all night."),
                d("n5c", "A letter addressed to \"Mr. Pell\" lay on the table, unopened."),
                d("n5d", "The coat was gone from the hook."))),

        new Night("Night six", "The inspector",
            "The inspector arrives before the street is light and does not take his "
          + "hat off. He has a notebook of his own, and he does not show it to you.",
            List.of(
                d("n6a", "He asked for the register."),
                d("n6b", "He asked who had been on the desk, and on which nights."),
                d("n6c", "He asked whether anything had been written down."),
                d("n6d", "He said the man in room nine was not called Ainsley."))));

    public static final List<Reckoning> RECKONINGS = List.of(

        new Reckoning(
            "The inspector asks: the man in room nine. What name did he give?",
            "n1a",
            "R. Ainsley. He signed it himself, in the register, in front of you, "
          + "and it is the only name in this hotel that he chose."),

        new Reckoning(
            "The inspector asks: was the yard door forced?",
            "n4c",
            "The lamp had been unscrewed, not broken. Somebody stood on something "
          + "and took it down on purpose, in the dark, so as not to be seen doing it. "
          + "A forced door is a stranger. That was not a stranger."),

        new Reckoning(
            "The inspector asks: the thread on the yard latch. Whose is it?",
            "n2b",
            "A red scarf, indoors, in February, on a woman who did not look at the "
          + "clock. You saw it and it was nothing. It is the whole answer."));

    /** Reckoning i happens after this night (0-based). */
    public static final int[] RECKONING_AFTER = {1, 3, 5};

    // -------------------------------------------------------------- the voice
    //
    // Every fixed line the game says lives here rather than in a screen,
    // because there are two builds now: the JavaFX screen and the phone build
    // (aside.games.ledger.WebLedger). A sentence kept in one of them is a
    // sentence the other one does not have.

    /** The note under the inspector's question. */
    public static final String INSPECTOR_NOTE =
            "He has a notebook of his own. Whatever is not in yours, you cannot give him.";

    /** The two verdict heads. */
    public static final String HAD_IT = "You had it.";
    public static final String DID_NOT_HAVE_IT = "You do not know.";

    /** What you said, when it was wrong. */
    public static final String SAID_NOTHING = "You said you did not know.";

    /** What you said, when it was wrong and it was something. */
    public static String answered(String id) {
        return "You answered: \"" + words(id) + "\"";
    }

    /** The end. */
    public static final String CLOSES = "The ledger closes.";

    public static String answeredCount(int correct) {
        return correct + " of " + RECKONINGS.size() + " questions could still be answered.";
    }

    /**
     * The line the game closes on, by how many the clerk could still answer.
     *
     * Three is not a perfect score, it is the whole of it -- there are three
     * reckonings -- and the note for it says so without congratulating anybody.
     */
    public static String endNote(int correct) {
        return switch (correct) {
            case 3 -> "You kept the right things. Nobody could have known which ones they were.";
            case 2 -> "Two answers. The third was in your hands once, and you put it down to make room.";
            case 1 -> "One answer. A record is not what you saw. It is what you were willing to carry.";
            default -> "Nothing. You wrote things down all week and kept none of the ones that were true.";
        };
    }

    /** The warning under a full ledger: what the next write costs. */
    public static String costWarning(String id) {
        return "Writing one more costs the oldest line: " + words(id);
    }

    /** What an empty ledger says it holds. */
    public static final String NOTHING = "nothing";

    /** The answer that is always on the list. */
    public static final String I_DO_NOT_KNOW = "I do not know.";

    // ---------------------------------------------------------------- state

    /** The ledger itself: detail ids, oldest first. */
    public final List<String> lines = new ArrayList<>();

    /** Which night is on the desk. 0-based. */
    public int night = 0;

    /** How many reckonings have been answered. */
    public int reck = 0;

    /** How many the player got right. */
    public int correct = 0;

    /** Every detail id the player has ever been shown. */
    public final List<String> seen = new ArrayList<>();

    public boolean finished = false;

    /** The reckoning currently being asked, or null. */
    public Reckoning reckoning() { return reck < RECKONINGS.size() ? RECKONINGS.get(reck) : null; }

    public Night currentNight() { return night < NIGHTS.size() ? NIGHTS.get(night) : null; }

    /** True when the night just ended should be followed by a question. */
    public boolean reckoningDue() {
        for (int i = 0; i < RECKONING_AFTER.length; i++) {
            if (RECKONING_AFTER[i] == night && i == reck) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- ledger

    public boolean holds(String id) { return lines.contains(id); }

    /**
     * Write a thing down.
     *
     * If the ledger is full this pushes the oldest line out. That is not a
     * side effect to be worked around -- it is the only mechanic the game
     * has. The line that goes is the one that has been in the ledger
     * longest, which is to say the one you have been carrying the longest,
     * which is exactly the one you will miss.
     */
    public void record(String id) {
        if (id == null || lines.contains(id)) return;
        if (lines.size() >= CAPACITY) lines.remove(0);
        lines.add(id);
    }

    /** Cross a line out. Recorded things can be let go on purpose, too. */
    public void erase(String id) { lines.remove(id); }

    /** Toggle a detail in or out of the ledger. */
    public void toggle(String id) {
        if (holds(id)) erase(id); else record(id);
    }

    /** The line that would be pushed out by the next write, or null. */
    public String nextOut() { return lines.size() >= CAPACITY ? lines.get(0) : null; }

    /** Look up a detail anywhere in the game. */
    public static Detail detail(String id) {
        for (Night n : NIGHTS) for (Detail x : n.details) if (x.id.equals(id)) return x;
        return null;
    }

    /** The words of a line, or the id if it is not a detail (should not happen). */
    public static String words(String id) {
        Detail x = detail(id);
        return x == null ? id : x.text;
    }

    /** Which night a detail belongs to, 0-based, or -1. */
    public static int nightOf(String id) {
        for (int i = 0; i < NIGHTS.size(); i++) {
            for (Detail x : NIGHTS.get(i).details) if (x.id.equals(id)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------- progress

    /** Note everything on this night as seen, then close the night. */
    public void closeNight() {
        Night n = currentNight();
        if (n != null) {
            for (Detail x : n.details) if (!seen.contains(x.id)) seen.add(x.id);
        }
    }

    /** Move on from a night to whatever comes next. */
    public void advanceNight() {
        night++;
        if (night >= NIGHTS.size()) finished = true;
    }

    /** Answer the current reckoning with a line, or null for "I don't know". */
    public boolean answer(String lineId) {
        Reckoning r = reckoning();
        if (r == null) return false;
        boolean ok = r.answerId.equals(lineId);
        if (ok) correct++;
        reck++;
        return ok;
    }

    /** Everything shown, grouped by whether it survived to the end. */
    public List<String> lost() {
        List<String> out = new ArrayList<>();
        for (String id : seen) if (!lines.contains(id)) out.add(id);
        return out;
    }

    // ------------------------------------------------------------- storage

    /**
     * The save file. Plain text, one fact per line, so a player can open it
     * and see exactly what their clerk is carrying -- and so the record of
     * the record is itself readable. That felt like the right joke.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ledger v1\n");
        sb.append("night\t").append(night).append('\n');
        sb.append("reck\t").append(reck).append('\n');
        sb.append("correct\t").append(correct).append('\n');
        sb.append("finished\t").append(finished ? 1 : 0).append('\n');
        for (String id : seen) sb.append("seen\t").append(id).append('\n');
        for (String id : lines) sb.append("line\t").append(id).append('\n');
        return sb.toString();
    }

    public static Ledger deserialize(String data) {
        Ledger l = new Ledger();
        for (String raw : data.split("\r?\n")) {
            if (raw.isBlank() || raw.startsWith("#")) continue;
            String[] p = raw.split("\t", -1);
            switch (p[0]) {
                case "night" -> l.night = num(p[1], 0);
                case "reck" -> l.reck = num(p[1], 0);
                case "correct" -> l.correct = num(p[1], 0);
                case "finished" -> l.finished = num(p[1], 0) == 1;
                case "seen" -> { if (detail(p[1]) != null) l.seen.add(p[1]); }
                case "line" -> { if (detail(p[1]) != null) l.lines.add(p[1]); }
                default -> { }
            }
        }
        if (l.night > NIGHTS.size()) l.night = NIGHTS.size();
        if (l.reck > RECKONINGS.size()) l.reck = RECKONINGS.size();
        return l;
    }

    static int num(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    public static Ledger load(Path file) throws IOException {
        if (!Files.exists(file)) return new Ledger();
        return deserialize(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** A fresh clerk with an empty ledger. */
    public static Ledger fresh() { return new Ledger(); }
}
