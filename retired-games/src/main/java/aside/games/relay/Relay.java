package aside.games.relay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * relay, the model.
 *
 * The twelfth game, and the third one you can be good at. Attribution was the
 * first (a scored night with a confusion matrix), drift the second (a scored
 * comparison). This one is scored too, and its subject is the thing the other
 * eleven have all been standing next to: what it costs to carry a message
 * across a gap you cannot cross yourself.
 *
 * THE SITUATION. Two people are not speaking. Everything that passes between
 * them tonight passes through you. Six messages, three each way. For each one
 * you choose how to put it.
 *
 * THE RULE THE WHOLE THING RESTS ON. A message carries two things: what was
 * said, and what it was for. No way of putting it carries all of both. A
 * message is worth the SMALLER of the two -- not the total, the floor.
 *
 * WHY THAT IS THE RULE AND NOT A GIMMICK. A message that arrives with every
 * fact and none of the point is not a message, it is a transcript. The person
 * receiving it learns what happened and nothing about the person who wrote it.
 * So the floor is the honest measure, and the total is the one that lies.
 *
 * THE TRAP, AND IT IS IN EVERY MESSAGE. In all six, the option that puts the
 * most on the page -- three facts and one point, four things, the highest total
 * available -- is not the option that carries the most. The option that carries
 * the most is the one with two facts and two points: less on the page, more
 * across the gap. A player who counts what is there will lose. A player who
 * asks what survives will win. That is the whole game, and it is the same
 * lesson drift teaches from the other side (a difference is not a change; here,
 * a sentence is not a message).
 *
 * WHY IT IS FAIR. Every element of every message is declared, not inferred: the
 * facts and the points are named in the model, next to the prose, and each
 * rendering declares which of them it carries. The suite checks that every
 * declared element is carried by at least one rendering, that no rendering
 * carries an element from another message, that the keyword for every carried
 * element actually appears in the prose, and that the trap is present in all
 * six messages. See aside.games.relay.SelfTest.
 *
 * WHAT IS FIXED AND WHAT IS NOT. All of it is fixed. There is no seed: the six
 * messages are the six messages, the same every night. What varies is what you
 * do with them, and that is the only variable the game needs.
 */
public final class Relay {

    public static final String WORDMARK = "Relay";

    public static final int MESSAGES = 6;
    public static final int OPTIONS = 3;
    /** The most a single message can be worth. No rendering carries more. */
    public static final int PER_MESSAGE = 2;
    public static final int MAX = MESSAGES * PER_MESSAGE;

    // ------------------------------------------------------------- the model

    /** One thing a message carries: a fact, or a point. */
    public record Element(String id, String keyword, String label) {}

    /** One way of putting a message, and which elements survive it. */
    public record Rendering(String text, List<String> facts, List<String> points) {}

    /** One message, and the three ways you could carry it. */
    public record Message(String from, String to, String situation,
                          List<Element> facts, List<Element> points,
                          List<Rendering> options) {}

    static Element e(String id, String keyword, String label) {
        return new Element(id, keyword, label);
    }

    static Rendering r(String text, List<String> facts, List<String> points) {
        return new Rendering(text, facts, points);
    }

    static List<String> ids(String... s) { return List.of(s); }

    /**
     * The night. Six messages, three each way, and for each one the three ways
     * of carrying it.
     *
     * The `facts` and `points` lists on each rendering are the answer key, and
     * they are written by hand next to the prose on purpose. SelfTest checks
     * them against the prose: the keyword of every element a rendering claims
     * has to appear in that rendering's text, so a rendering rewritten without
     * its key -- or a key edited without the prose -- fails the suite.
     */
    public static final List<Message> NIGHT = List.of(

        new Message("Ruth", "Sam", "Ruth has not written to Sam in four months.",
            List.of(
                e("sold", "sold", "the house is sold"),
                e("empty", "14th", "the buyer wants it empty by the 14th"),
                e("piano", "piano", "the piano is still in the front room")),
            List.of(
                e("notice", "not asking", "this is a notice, not a request"),
                e("elsewhere", "someone else", "she wants him to hear it from her")),
            List.of(
                r("The house is sold. The buyer wants it empty by the 14th. "
                        + "The piano is still in the front room.",
                  ids("sold", "empty", "piano"), ids()),
                r("The house is sold, and the buyer wants it empty by the 14th. "
                        + "I'm not asking you for anything \u2014 I just didn't want you "
                        + "to hear it from someone else.",
                  ids("sold", "empty"), ids("notice", "elsewhere")),
                r("The house is sold and the buyer wants it empty by the 14th. "
                        + "The piano is still in the front room. I thought you'd "
                        + "rather hear it from me than from someone else.",
                  ids("sold", "empty", "piano"), ids("elsewhere")))), 

        new Message("Sam", "Ruth", "Sam has not been back to the house since the funeral.",
            List.of(
                e("working", "working", "he is working on the 14th"),
                e("after", "weekend after", "he can come the weekend after"),
                e("nothing", "from the house", "he wants nothing out of the house")),
            List.of(
                e("spite", "not saying no", "he is not refusing out of spite"),
                e("ask", "if you need", "he will come if she asks him to")),
            List.of(
                r("I'm working on the 14th. I can come the weekend after. "
                        + "I don't want anything from the house.",
                  ids("working", "after", "nothing"), ids()),
                r("I can't do the 14th \u2014 I'm working. I can come the weekend after. "
                        + "I'm not saying no to you. If you need me there, say so "
                        + "and I'll come.",
                  ids("working", "after"), ids("spite", "ask")),
                r("I'm working on the 14th, but I can come the weekend after. "
                        + "I don't want anything from the house. I'm not saying no to you.",
                  ids("working", "after", "nothing"), ids("spite")))),

        new Message("Ruth", "Sam", "The house is being cleared on the 12th.",
            List.of(
                e("twelfth", "12th", "the piano goes to the charity shop on the 12th"),
                e("boxes", "boxes", "his boxes are in the loft"),
                e("adair", "Adair", "the neighbour asked after him")),
            List.of(
                e("last", "won't ask again", "this is the last time she will offer the piano"),
                e("tired", "the one who writes", "she is tired of being the one who writes")),
            List.of(
                r("The piano goes to the charity shop on the 12th. Your boxes are "
                        + "in the loft. Mrs Adair asked after you.",
                  ids("twelfth", "boxes", "adair"), ids()),
                r("The piano goes on the 12th. If you want it, tell me before then "
                        + "\u2014 I won't ask again. Your boxes are in the loft. I'm tired "
                        + "of being the one who writes.",
                  ids("twelfth", "boxes"), ids("last", "tired")),
                r("The piano goes to the charity shop on the 12th. Your boxes are "
                        + "in the loft. Mrs Adair asked after you. I won't ask again.",
                  ids("twelfth", "boxes", "adair"), ids("last")))),

        new Message("Sam", "Ruth", "Sam is coming up on the 15th.",
            List.of(
                e("fifteenth", "15th", "he is coming on the 15th"),
                e("van", "van", "he is bringing a van"),
                e("take", "take the piano", "he will take the piano if it is still there")),
            List.of(
                e("argue", "not coming to argue", "he is not coming to argue"),
                e("key", "key", "he is asking her to leave the key out")),
            List.of(
                r("I'll come on the 15th and take the piano if it's still there. "
                        + "I'm not coming to argue. Leave the key under the pot.",
                  ids("fifteenth", "take"), ids("argue", "key")),
                r("I'll come on the 15th. I'll take the piano if it's still there. "
                        + "I'm bringing a van.",
                  ids("fifteenth", "van", "take"), ids()),
                r("I'll come on the 15th with a van and take the piano. "
                        + "Leave the key under the pot.",
                  ids("fifteenth", "van", "take"), ids("key")))),

        new Message("Ruth", "Sam", "Ruth sold the piano in the spring.",
            List.of(
                e("soldpiano", "sold", "the piano is sold"),
                e("envelope", "envelope", "the money is in an envelope with his name on it"),
                e("march", "March", "she is moving to her sister's in March")),
            List.of(
                e("sorry", "should have waited", "she is sorry she did not wait"),
                e("keeping", "not keeping", "she is not keeping the money")),
            List.of(
                r("The piano is sold. The money's in an envelope with your name on "
                        + "it. I'm moving to Christine's in March.",
                  ids("soldpiano", "envelope", "march"), ids()),
                r("The piano is sold and the money's in an envelope with your name "
                        + "on it. I'm moving to Christine's in March. I'm sorry \u2014 "
                        + "I should have waited.",
                  ids("soldpiano", "envelope", "march"), ids("sorry")),
                r("I sold the piano. I'm sorry \u2014 I should have waited. The money's "
                        + "in an envelope with your name on it, and I'm not keeping it.",
                  ids("soldpiano", "envelope"), ids("sorry", "keeping")))),

        new Message("Sam", "Ruth", "Ruth has not answered since the 15th.",
            List.of(
                e("got", "got the envelope", "he has the envelope"),
                e("half", "half back", "he is sending half of it back"),
                e("help", "help you move", "he will come and help her move")),
            List.of(
                e("thanks", "thank you", "he is thanking her"),
                e("want", "don't want it", "he does not want the money")),
            List.of(
                r("I got the envelope \u2014 thank you. I'm sending half back. "
                        + "I'll come in March and help you move.",
                  ids("got", "half", "help"), ids("thanks")),
                r("I got the envelope. I'm sending half back \u2014 I don't want it. "
                        + "Thank you.",
                  ids("got", "half"), ids("thanks", "want")),
                r("I got the envelope. I'm sending half back. I'll come in March "
                        + "and help you move.",
                  ids("got", "half", "help"), ids()))));

    // ------------------------------------------------------------- the state

    /** Which rendering was carried for each message, or -1 for not yet. */
    public final int[] choice = new int[MESSAGES];
    public boolean reported;

    public Relay() { Arrays.fill(choice, -1); }

    public boolean done() {
        for (int c : choice) if (c < 0) return false;
        return true;
    }

    public int next() {
        for (int i = 0; i < MESSAGES; i++) if (choice[i] < 0) return i;
        return -1;
    }

    public int carriedFacts(int m, int o) { return NIGHT.get(m).options().get(o).facts().size(); }
    public int carriedPoints(int m, int o) { return NIGHT.get(m).options().get(o).points().size(); }

    /** Everything on the page, which is the number that lies. */
    public int total(int m, int o) { return carriedFacts(m, o) + carriedPoints(m, o); }

    /** What the message is worth: the smaller of the two, not the total. */
    public int worth(int m, int o) { return Math.min(carriedFacts(m, o), carriedPoints(m, o)); }

    /** The best any rendering of this message can be worth. */
    public int best(int m) {
        int b = 0;
        for (int o = 0; o < OPTIONS; o++) b = Math.max(b, worth(m, o));
        return b;
    }

    /** The rendering with the highest total. Used by the suite, not the game. */
    public int mostOnThePage(int m) {
        int b = 0;
        for (int o = 1; o < OPTIONS; o++) if (total(m, o) > total(m, b)) b = o;
        return b;
    }

    public int score() {
        int s = 0;
        for (int m = 0; m < MESSAGES; m++) if (choice[m] >= 0) s += worth(m, choice[m]);
        return s;
    }

    /** How many messages arrived as something else entirely. */
    public int breaks() {
        int n = 0;
        for (int m = 0; m < MESSAGES; m++) if (choice[m] >= 0 && worth(m, choice[m]) == 0) n++;
        return n;
    }

    /** The labels of everything this rendering carried. */
    public List<String> kept(int m, int o) {
        List<String> out = new ArrayList<>();
        for (Element el : NIGHT.get(m).facts()) if (NIGHT.get(m).options().get(o).facts().contains(el.id())) out.add(el.label());
        for (Element el : NIGHT.get(m).points()) if (NIGHT.get(m).options().get(o).points().contains(el.id())) out.add(el.label());
        return out;
    }

    /** The labels of everything it left behind. */
    public List<String> lost(int m, int o) {
        List<String> out = new ArrayList<>();
        for (Element el : NIGHT.get(m).facts()) if (!NIGHT.get(m).options().get(o).facts().contains(el.id())) out.add(el.label());
        for (Element el : NIGHT.get(m).points()) if (!NIGHT.get(m).options().get(o).points().contains(el.id())) out.add(el.label());
        return out;
    }

    // -------------------------------------------------------------- the save

    public void save(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("relay 1\n");
        sb.append("choices");
        for (int c : choice) sb.append(' ').append(c < 0 ? "-" : String.valueOf(c));
        sb.append('\n');
        sb.append("reported ").append(reported ? 1 : 0).append('\n');
        Files.writeString(p, sb.toString());
    }

    public static Relay load(Path p) throws IOException {
        Relay r = new Relay();
        if (!Files.exists(p)) return r;
        for (String line : Files.readAllLines(p)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) continue;
            if (parts[0].equals("choices")) {
                for (int i = 0; i < MESSAGES && i + 1 < parts.length; i++) {
                    r.choice[i] = parts[i + 1].equals("-") ? -1 : Integer.parseInt(parts[i + 1]);
                }
            } else if (parts[0].equals("reported") && parts.length > 1) {
                r.reported = parts[1].equals("1");
            }
        }
        return r;
    }

    // ------------------------------------------------------------- the words

    public static final String OPEN_TITLE = "Relay";
    public static final String OPEN_SUB = "Six messages cross tonight. You are the one carrying them.";

    public static final String[] OPEN_SITUATION = {
        "The house is sold. Ruth and Sam are not speaking, and they are not going "
                + "to start. Everything that passes between them tonight passes "
                + "through you.",
        "Ruth writes first, then Sam, then Ruth. Six messages. For each one you "
                + "choose how to carry it, and once it is carried it is carried.",
    };

    public static final String RULES_HEADING = "the rule";
    public static final String[][] RULES = {
        { "two things", "A message carries what was said, and what it was for. "
                + "No way of putting it carries all of both." },
        { "the floor", "A message is worth the smaller of the two. Not the total "
                + "\u2014 the smaller. A message that arrives with every fact and "
                + "none of the point is not a message, it is a transcript." },
    };

    public static final String START_LINE = "ENTER to take the first message.";

    public static final String PROGRESS = "message %d of %d";
    public static final String FROM_TO = "%s \u2192 %s";
    public static final String ARROW = "\u2192";
    public static final String CHOOSE = "ENTER to carry it";
    public static final String CARRIED = "carried";
    /** The phone has buttons, not keys, so it gets its own line. */
    public static final String START_BUTTON = "take the first message";
    public static final String CARRY_NOTE = "Once it is carried it is carried.";

    public static final String REPORT_HEAD = "what arrived";
    public static final String AGAIN = "carry them again";
    public static final String KEPT_HEAD = "kept";
    public static final String LOST_HEAD = "lost";
    public static final String NOTHING_LOST = "nothing \u2014 all of it arrived";

    public static final String HINT_READ = "UP/DOWN choose    ENTER carry it    M mute    [ quieter    ] louder";
    public static final String HINT_REPORT = "R carry them again    ENTER for the library    M mute";

    /** "both" / "half" / "broke" -- what one message was worth, in a word. */
    public static String worthWord(int w) {
        return switch (w) {
            case 2 -> "both";
            case 1 -> "half";
            default -> "broke";
        };
    }

    public static String worthLine(int score) {
        return "worth " + score + " of " + MAX;
    }

    public static String breaksLine(int breaks) {
        if (breaks == 0) return "Nothing arrived as something else.";
        if (breaks == 1) return "One of the six arrived as something else.";
        return breaks + " of the six arrived as something else.";
    }

    public static String closing(int score) {
        if (score == MAX) {
            return "All six arrived. Both of them end the night knowing the same "
                    + "thing, and neither was told anything the other did not say. "
                    + "That is the whole job, and it is rarer than it sounds.";
        }
        if (score >= 9) {
            return "Most of it arrived. Where it did not, it was the floor that "
                    + "went: a message that kept every fact and lost the reason for "
                    + "it. They will be working from the same facts and two "
                    + "different nights.";
        }
        if (score >= 5) {
            return "Half of it arrived. They will compare notes eventually and find "
                    + "they were told two different evenings. Nothing was invented. "
                    + "A great deal was dropped.";
        }
        if (score >= 1) {
            return "Very little arrived. The facts mostly got through; almost none "
                    + "of the reasons did. They will both be accurate about the "
                    + "house and wrong about each other.";
        }
        return "Nothing arrived. Every message came through as a transcript: "
                + "correct, complete, and not what anyone was saying. They will "
                + "read it and learn nothing about the person who wrote it.";
    }

}
