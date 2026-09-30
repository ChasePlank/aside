package aside.games.relay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * relay's own checks.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.relay.SelfTest
 *
 * The point of these is not that the code runs. It is that the CONTENT is what
 * it claims to be: that every declared element is reachable, that the prose and
 * the answer key still agree, that no rendering carries everything, and that the
 * trap the game is built on is actually present in all six messages rather than
 * in the two the author happened to be thinking about.
 */
public class SelfTest {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== relay self-test ===\n");

        System.out.println("--- the shape ---");
        check("six messages", Relay.NIGHT.size() == Relay.MESSAGES);
        for (int m = 0; m < Relay.MESSAGES; m++) {
            Relay.Message msg = Relay.NIGHT.get(m);
            String who = msg.from() + "->" + msg.to();
            check(who + ": three facts", msg.facts().size() == 3);
            check(who + ": two points", msg.points().size() == 2);
            check(who + ": three ways to carry it", msg.options().size() == Relay.OPTIONS);
            check(who + ": the two of them are different people", !msg.from().equals(msg.to()));
        }

        System.out.println("\n--- the answer key agrees with the prose ---");
        for (int m = 0; m < Relay.MESSAGES; m++) {
            Relay.Message msg = Relay.NIGHT.get(m);
            Set<String> factIds = new HashSet<>(), pointIds = new HashSet<>();
            for (Relay.Element el : msg.facts()) factIds.add(el.id());
            for (Relay.Element el : msg.points()) pointIds.add(el.id());
            check("message " + (m + 1) + ": element ids are unique",
                    factIds.size() == 3 && pointIds.size() == 2);

            for (int o = 0; o < Relay.OPTIONS; o++) {
                Relay.Rendering r = msg.options().get(o);
                String tag = "message " + (m + 1) + " option " + (o + 1);
                check(tag + ": no fact borrowed from another message",
                        factIds.containsAll(r.facts()));
                check(tag + ": no point borrowed from another message",
                        pointIds.containsAll(r.points()));
                check(tag + ": every fact it claims is in the prose",
                        keywordsPresent(r.text(), msg.facts(), r.facts()));
                check(tag + ": every point it claims is in the prose",
                        keywordsPresent(r.text(), msg.points(), r.points()));
                check(tag + ": carries no more than a message can be worth",
                        new Relay().worth(m, o) <= Relay.PER_MESSAGE);
                check(tag + ": does not carry the whole message",
                        r.facts().size() < 3 || r.points().size() < 2);
            }
        }

        System.out.println("\n--- nothing is stranded ---");
        for (int m = 0; m < Relay.MESSAGES; m++) {
            Relay.Message msg = Relay.NIGHT.get(m);
            for (Relay.Element el : msg.facts()) {
                boolean carried = false;
                for (Relay.Rendering r : msg.options()) if (r.facts().contains(el.id())) carried = true;
                check("message " + (m + 1) + ": a rendering carries the fact \"" + el.label() + "\"", carried);
            }
            for (Relay.Element el : msg.points()) {
                boolean carried = false;
                for (Relay.Rendering r : msg.options()) if (r.points().contains(el.id())) carried = true;
                check("message " + (m + 1) + ": a rendering carries the point \"" + el.label() + "\"", carried);
            }
        }

        System.out.println("\n--- the rule bites ---");
        Relay probe = new Relay();
        Set<Integer> bestAt = new HashSet<>();
        for (int m = 0; m < Relay.MESSAGES; m++) {
            int best = probe.best(m);
            check("message " + (m + 1) + ": is worth something", best > 0);
            check("message " + (m + 1) + ": is worth no more than " + Relay.PER_MESSAGE,
                    best <= Relay.PER_MESSAGE);

            int bestCount = 0, bestIndex = -1;
            for (int o = 0; o < Relay.OPTIONS; o++) {
                if (probe.worth(m, o) == best) { bestCount++; bestIndex = o; }
            }
            check("message " + (m + 1) + ": one way of carrying it is the best", bestCount == 1);
            bestAt.add(bestIndex);

            // The trap, stated as a property rather than as a hope: there is a
            // rendering with at least as much on the page as the best one and
            // less across the gap. If this ever fails, the game has stopped
            // teaching what it is about.
            boolean trap = false;
            for (int o = 0; o < Relay.OPTIONS; o++) {
                if (probe.total(m, o) >= probe.total(m, bestIndex) && probe.worth(m, o) < best) trap = true;
            }
            check("message " + (m + 1) + ": the fullest option is not the best one", trap);

            // And the option that reads as complete: every fact, no point.
            boolean transcript = false;
            for (int o = 0; o < Relay.OPTIONS; o++) {
                if (probe.carriedFacts(m, o) == 3 && probe.carriedPoints(m, o) == 0) transcript = true;
            }
            check("message " + (m + 1) + ": there is a transcript to be tempted by", transcript);
        }
        check("the best choice is not always in the same place", bestAt.size() >= 3);

        System.out.println("\n--- what the two strategies score ---");
        Relay reading = new Relay(), counting = new Relay();
        for (int m = 0; m < Relay.MESSAGES; m++) {
            int best = -1;
            for (int o = 0; o < Relay.OPTIONS; o++) if (reading.worth(m, o) == reading.best(m)) best = o;
            reading.choice[m] = best;
            counting.choice[m] = counting.mostOnThePage(m);
        }
        check("reading for the floor gets all " + Relay.MAX, reading.score() == Relay.MAX);
        check("counting what is on the page does not", counting.score() < Relay.MAX);
        System.out.println("       reading the floor: " + reading.score() + "/" + Relay.MAX
                + "   counting the page: " + counting.score() + "/" + Relay.MAX);

        Relay none = new Relay();
        for (int m = 0; m < Relay.MESSAGES; m++) none.choice[m] = transcriptOf(none, m);
        check("carrying every fact and no point scores nothing", none.score() == 0);
        check("and every one of them counts as a break", none.breaks() == Relay.MESSAGES);

        System.out.println("\n--- the words ---");
        check("a perfect night says so", Relay.closing(Relay.MAX).contains("All six arrived"));
        check("an empty night says something else",
                !Relay.closing(0).equals(Relay.closing(Relay.MAX)));
        check("the score line names the ceiling", Relay.worthLine(9).endsWith("of " + Relay.MAX));
        check("no breaks reads as good news", Relay.breaksLine(0).startsWith("Nothing"));
        check("worth is worded for a person",
                Relay.worthWord(2).equals("both") && Relay.worthWord(1).equals("half")
                        && Relay.worthWord(0).equals("broke"));

        System.out.println("\n--- the save ---");
        Path tmp = Files.createTempFile("relay", ".state");
        Relay saved = new Relay();
        saved.choice[0] = 2; saved.choice[1] = 0; saved.choice[2] = 1;
        saved.reported = true;
        saved.save(tmp);
        Relay back = Relay.load(tmp);
        boolean same = true;
        for (int i = 0; i < Relay.MESSAGES; i++) if (back.choice[i] != saved.choice[i]) same = false;
        check("the choices survive a round trip", same);
        check("so does whether the night is over", back.reported);
        check("an unplayed night loads as unplayed",
                Relay.load(tmp.resolveSibling("nothing-here.state")).next() == 0);
        Files.deleteIfExists(tmp);

        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    /** The rendering that carries every fact and no point, or -1. */
    static int transcriptOf(Relay r, int m) {
        for (int o = 0; o < Relay.OPTIONS; o++) {
            if (r.carriedFacts(m, o) == 3 && r.carriedPoints(m, o) == 0) return o;
        }
        return -1;
    }

    /** Every element in `claimed` has its keyword somewhere in the prose. */
    static boolean keywordsPresent(String text, List<Relay.Element> all, List<String> claimed) {
        String lower = text.toLowerCase();
        for (Relay.Element el : all) {
            if (!claimed.contains(el.id())) continue;
            if (!lower.contains(el.keyword().toLowerCase())) return false;
        }
        return true;
    }
}
