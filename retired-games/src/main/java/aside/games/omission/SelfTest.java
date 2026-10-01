package aside.games.omission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for omission.
 *
 * The point of these is not coverage. It is that four things in this game are
 * written by the same hand and could quietly disagree with each other:
 *
 *   - the pool, which claims six things were said and twelve were not;
 *   - the lines, which are the only signal the player has and could say the
 *     opposite of what the model believes;
 *   - the deal, which promises the list is always longer than the bag;
 *   - the arithmetic, which claims that keeping what mattered is worse than
 *     keeping what nobody else knew.
 *
 * The one that matters most is the last. The whole game is a claim a reader
 * cannot check from the outside: **spending the five slots on the five heaviest
 * things is strictly worse than spending them on the five heaviest things
 * nobody else knew.** If that were false the game would still look exactly like
 * this one and would be a game about nothing. So it is not asserted here, it is
 * measured -- against every one of the 792 ways to fill the bag, on every seed.
 *
 * Run: java -cp classes aside.games.omission.SelfTest
 */
public final class SelfTest {

    static int checks = 0, failed = 0;

    static void ok(boolean cond, String what) {
        checks++;
        if (!cond) { failed++; System.out.println("FAIL  " + what); }
    }

    static void eq(Object a, Object b, String what) {
        checks++;
        boolean same = a == null ? b == null : a.equals(b);
        if (!same) { failed++; System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + ")"); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== omission self-test ===\n");

        System.out.println("--- the pool ---");
        eq(Omission.POOL.size(), 18, "there are eighteen things you know");
        Set<String> ids = new HashSet<>();
        for (Omission.Detail d : Omission.POOL) ids.add(d.id());
        eq(ids.size(), Omission.POOL.size(), "and every one of them has its own id");
        int told = 0, untold = 0;
        for (Omission.Detail d : Omission.POOL) { if (d.told()) told++; else untold++; }
        eq(told, 6, "six of them you said out loud");
        eq(untold, 12, "and twelve you never did");
        ok(Omission.LIST > Omission.SLOTS, "the list is longer than the bag");
        ok(untold > Omission.SLOTS, "and there are more silent things than slots");
        // Six said and not seven is the number that guarantees the second of
        // those on every deal, so it is load-bearing rather than decorative.
        ok(Omission.POOL.size() - told >= Omission.LIST - told + 0,
                "the pool is big enough to deal a full list");

        System.out.println("\n--- what the model believes ---");
        for (Omission.Detail d : Omission.POOL) {
            if (d.told()) {
                eq(d.assumed(), d.text(), "a thing you said is remembered as it was: " + d.id());
            } else {
                ok(!d.assumed().equals(d.text()),
                        "a thing you never said is remembered as something else: " + d.id());
                ok(d.assumed().length() > 0, "and the something else is written down: " + d.id());
            }
            ok(d.matters() >= 1 && d.matters() <= 3, "the weight of " + d.id() + " is 1..3");
            ok(d.said() != null && !d.said().isEmpty(), "the line for " + d.id() + " is written");
        }

        System.out.println("\n--- the signal, and the shortcut that must not work ---");
        // The line is the only thing telling the player whether a thing was
        // said. If a told line read like an unsaid one the game would be a lie.
        // "never" is deliberately left out of the silence markers below: two of
        // the lines about things you DID say use it ("you never once had to be
        // asked", "never got it fixed"), which is what stops the game being a
        // search for a word. The markers are the ones that can only mean
        // silence.
        for (Omission.Detail d : Omission.POOL) {
            boolean silence = d.said().contains("not ") || d.said().contains("nothing")
                    || d.said().contains("obody") || d.said().contains("no one");
            boolean never = d.said().contains("never");
            if (d.told()) ok(!silence, "a thing you said is not described as silence: " + d.id());
            else ok(silence || never, "a thing you never said is described as silence: " + d.id());
        }
        // And the shortcut has to fail: if every silent line had the word
        // "never" in it and no spoken one did, the game would be a search for a
        // word rather than a question about what you said out loud.
        int toldWithNever = 0, untoldWithoutNever = 0;
        for (Omission.Detail d : Omission.POOL) {
            boolean n = d.said().contains("never");
            if (d.told() && n) toldWithNever++;
            if (!d.told() && !n) untoldWithoutNever++;
        }
        ok(toldWithNever > 0, "\"never\" appears in a line about a thing you did say ("
                + toldWithNever + ")");
        ok(untoldWithoutNever > 0, "and a silent line does without it ("
                + untoldWithoutNever + ")");
        // The weights have to lean the way the trap needs them to: the things
        // you said are, on average, the things that mattered more. That is not
        // a rigged trap, it is what talking about what you care about does.
        double toldAvg = 0, untoldAvg = 0;
        for (Omission.Detail d : Omission.POOL) {
            if (d.told()) toldAvg += d.matters(); else untoldAvg += d.matters();
        }
        toldAvg /= told; untoldAvg /= untold;
        ok(toldAvg > untoldAvg, "the things you said weigh more on average ("
                + round2(toldAvg) + " vs " + round2(untoldAvg) + ")");
        // ...but not without exception, or keeping by weight would be worth
        // nothing and the report could not show what the strategy cost.
        boolean heavySilent = false;
        for (Omission.Detail d : Omission.POOL) if (!d.told() && d.matters() == 3) heavySilent = true;
        ok(heavySilent, "and at least one of the heaviest things is one nobody else knew");

        System.out.println("\n--- the deal ---");
        int seeds = 2000;
        int badSize = -1, dup = -1, tooFewSilent = -1, tooManySilent = -1;
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < seeds; seed++) {
            Omission o = Omission.of(seed);
            if (o.list.size() != Omission.LIST) badSize = (int) seed;
            if (new HashSet<>(o.list).size() != Omission.LIST) dup = (int) seed;
            int silent = o.silentCount();
            if (silent <= Omission.SLOTS) tooFewSilent = (int) seed;
            if (silent > Omission.LIST) tooManySilent = (int) seed;
            for (Omission.Detail d : o.list) seen.add(d.id());
        }
        eq(badSize, -1, "every seed deals a full list of twelve");
        eq(dup, -1, "and no thing appears on the list twice");
        eq(tooFewSilent, -1, "and there are always more silent things than slots");
        eq(tooManySilent, -1, "and never more silent things than there are things");
        eq(seen.size(), Omission.POOL.size(), "and every thing in the pool can turn up");
        // The list is dealt, not laid out: if the six said things came first the
        // player would be handed the answer by position.
        int sorted = 0;
        for (long seed = 0; seed < 200; seed++) {
            Omission o = Omission.of(seed);
            boolean inOrder = true;
            for (int i = 1; i < o.list.size(); i++) {
                if (o.list.get(i).told() && !o.list.get(i - 1).told()) { inOrder = false; break; }
            }
            if (inOrder) sorted++;
        }
        ok(sorted < 20, "the list is shuffled rather than laid out said-first ("
                + sorted + " of 200 came out sorted)");

        System.out.println("\n--- the rule ---");
        // What you remember is a fact about the bag and the line, and nothing
        // else. Checked against every way of filling the bag on every seed.
        int wrongMemory = -1, wrongScore = -1, wrongBest = -1, bruteSeeds = 200;
        int weightWins = 0, silenceMisses = 0;
        double weightScore = 0, bestScore = 0;
        for (long seed = 0; seed < bruteSeeds; seed++) {
            Omission o = Omission.of(seed);
            int n = o.list.size();
            int brute = -1;
            for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++)
            for (int c = b + 1; c < n; c++) for (int d = c + 1; d < n; d++)
            for (int e = d + 1; e < n; e++) {
                Omission t = Omission.of(seed);
                t.keep(a); t.keep(b); t.keep(c); t.keep(d); t.keep(e);
                t.leave();
                for (int i = 0; i < n; i++) {
                    boolean want = t.kept.contains(i) || t.list.get(i).told();
                    if (t.right(i) != want) wrongMemory = (int) seed;
                }
                int s = t.score();
                int sum = 0;
                for (int i = 0; i < n; i++) if (t.right(i)) sum += t.list.get(i).matters();
                if (s != sum) wrongScore = (int) seed;
                if (s > brute) brute = s;
            }
            if (brute != o.best()) wrongBest = (int) seed;
            bestScore += o.best();

            // The strategy the game is about: the five heaviest things.
            Omission w = Omission.of(seed);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) order.add(i);
            order.sort((x, y) -> w.list.get(y).matters() - w.list.get(x).matters());
            for (int i = 0; i < Omission.SLOTS; i++) w.keep(order.get(i));
            w.leave();
            weightScore += w.score();
            if (w.score() >= o.best()) weightWins++;

            // And the strategy the rule implies: the five heaviest things
            // nobody else knew.
            Omission s2 = Omission.of(seed);
            List<Integer> silent = new ArrayList<>();
            for (int i = 0; i < n; i++) if (!s2.list.get(i).told()) silent.add(i);
            silent.sort((x, y) -> s2.list.get(y).matters() - s2.list.get(x).matters());
            for (int i = 0; i < Omission.SLOTS; i++) s2.keep(silent.get(i));
            s2.leave();
            if (s2.score() != o.best()) silenceMisses++;
        }
        eq(wrongMemory, -1, "what you remember is a fact about the bag and the line");
        eq(wrongScore, -1, "and the score is the weight of what you got right");
        eq(wrongBest, -1, "and best() is the true maximum over all 792 bags");
        eq(weightWins, 0, "keeping the five heaviest is never optimal");
        eq(silenceMisses, 0, "keeping the five heaviest silent ones always is");
        System.out.println("       keeping by weight:  " + round2(weightScore / bruteSeeds)
                + " of " + round2(bestScore / bruteSeeds) + " on average");
        ok(bestScore - weightScore >= 2 * bruteSeeds,
                "and the loss is not marginal: " + round2((bestScore - weightScore) / bruteSeeds)
                        + " points a night on average");

        System.out.println("\n--- the report ---");
        // A bag that spent a slot on something already said, and a bag that did
        // not, have to be told apart by the verdict -- otherwise the report is
        // not telling the player what they did.
        Omission o = Omission.of(7);
        Omission.Detail wasted = o.heaviestWasted();
        eq(wasted, null, "an empty bag has wasted nothing");
        for (int i = 0; i < o.list.size(); i++) {
            if (o.list.get(i).told()) { o.keep(i); break; }
        }
        ok(o.heaviestWasted() != null, "a slot spent on something said is named");
        ok(o.wastedSlots() == 1, "and counted");
        ok(!Omission.verdict(10, 12, o.heaviestWasted(), o.heaviestLost())
                .equals(Omission.verdict(12, 12, null, null)),
                "the verdict for a wasted slot is not the verdict for a perfect bag");
        ok(Omission.verdict(12, 12, null, null).contains("nothing better"),
                "a perfect bag says there was nothing better to do");
        ok(!Omission.closing(12, 12, 7, 0).equals(Omission.closing(10, 12, 7, 2)),
                "and a perfect night closes differently from a wasteful one");
        ok(Omission.closing(10, 12, 7, 2).contains("safe"),
                "the wasteful closing says what was kept was already safe");
        ok(Omission.scoreLine(9, 12, 19, 24).contains("nine of twelve"),
                "the score line says the count in words");
        ok(Omission.bestLine(22).contains("22"), "the best line names the number");
        for (int m = 1; m <= 3; m++) ok(!Omission.worth(m).isEmpty(), "weight " + m + " has a word");
        eq(Omission.word(5), "five", "five is a word");
        eq(Omission.word(12), "twelve", "twelve is a word");

        System.out.println("\n--- the file ---");
        Path tmp = Files.createTempFile("omission", ".state");
        Omission saved = Omission.of(4242);
        saved.keep(1); saved.keep(4); saved.keep(9);
        saved.save(tmp);
        Omission back = Omission.load(tmp);
        eq(back.seed, saved.seed, "the seed survives the round trip");
        eq(back.kept, saved.kept, "and what went in the bag survives");
        eq(back.list, saved.list, "and the list is the same list");
        eq(back.left, false, "and the night is not over");
        saved.leave();
        saved.save(tmp);
        eq(Omission.load(tmp).left, true, "and leaving survives too");
        Files.deleteIfExists(tmp);

        System.out.println("\n--- the phone build ---");
        Path out = Path.of("web", "omission.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
        } else {
            ok(WebOmission.html().equals(Files.readString(out)),
                    "web/omission.html is current -- regenerate it with aside.games.omission.WebOmission");
        }

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static String round2(double d) { return String.valueOf(Math.round(d * 100) / 100.0); }

    private SelfTest() { }
}
