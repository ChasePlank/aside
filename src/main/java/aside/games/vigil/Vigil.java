package aside.games.vigil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * vigil -- the model.
 *
 * Twelve days, five things, and less of you every day.
 *
 * The whole game is one piece of arithmetic the player does in the first
 * minute and then spends the rest of the game living with. Five things in the
 * house. Keeping one costs a unit of effort a day, and a thing that is not
 * kept loses a step and is gone after three. You start with three units and
 * you end with one. Twelve days of that is twenty-four units, and twenty-four
 * units is not five things for twelve days -- it is not even two.
 *
 * So the promise on the wall -- I'll keep it exactly as it is -- is not a
 * hard promise. It is an impossible one, and the game is what the player does
 * after finding that out. Keeping costs one. Changing costs two, and cannot
 * be undone. A room kept exactly as it was is a room with no one in it, and
 * the only way to leave a mark in this house is to stop holding on to part of
 * it.
 *
 * That is the rule that makes it a game rather than a countdown: the two
 * things the player wants -- the house intact, and themselves visible in it --
 * are bought with the same finite purse, and there is no way to buy both.
 *
 * The other thing worth saying out loud, because it is the reason the numbers
 * are what they are: the effort goes DOWN. Three units for the first four
 * days, two for the next four, one for the last four. A vigil does not fail
 * because the world takes something. It fails because the person keeping it
 * runs out.
 */
public class Vigil {

    public static final int DAYS = 12;
    public static final int FULL = 3;
    public static final int KEEP_COST = 1;
    public static final int CHANGE_COST = 2;

    /** The thing the player said before the game started. */
    public static final String PROMISE = "I'll keep it exactly as it is.";

    public enum State { AS_LEFT, CHANGED, LOST }

    // ------------------------------------------------------------- a thing

    public static class Thing {
        public final String name, where;
        public final String keptText, changedText, replacedText, lostText;

        public int condition = FULL;
        public boolean changed;      // you altered it
        public boolean replaced;     // it was lost, and you put something else there
        public boolean gone;         // it is not there now
        public boolean everLost;     // it was lost at some point, even if replaced

        Thing(String name, String where, String keptText, String changedText,
              String replacedText, String lostText) {
            this.name = name;
            this.where = where;
            this.keptText = keptText;
            this.changedText = changedText;
            this.replacedText = replacedText;
            this.lostText = lostText;
        }

        /** Is this thing yours rather than theirs? */
        public boolean mine() { return changed || replaced; }

        public State state() {
            if (gone) return State.LOST;
            if (mine()) return State.CHANGED;
            return State.AS_LEFT;
        }

        public String line() {
            return switch (state()) {
                case AS_LEFT -> keptText;
                case CHANGED -> replaced ? replacedText : changedText;
                case LOST -> lostText;
            };
        }

        public String stateWord() {
            return switch (state()) {
                case AS_LEFT -> "as they left it";
                case CHANGED -> replaced ? "yours, and not theirs" : "yours";
                case LOST -> "gone";
            };
        }

        /** What keeping it would make of it. Null when it cannot be kept. */
        public String keepPreview() {
            if (gone) return null;
            return mine() ? (replaced ? replacedText : changedText) : keptText;
        }

        /** What changing it would make of it. Null when it cannot be changed. */
        public String changePreview() {
            if (!gone && mine()) return null;
            return gone ? replacedText : changedText;
        }
    }

    // ------------------------------------------------------------- content

    public static List<Thing> defaultThings() {
        List<Thing> t = new ArrayList<>();
        t.add(new Thing("the fire", "in the hearth",
                "Burning. The same wood, the same shape in the grate, the same low noise it has "
                        + "always made.",
                "Banked low under its own ash. It will still be warm when they come, and it will "
                        + "not be their fire.",
                "Lit again from scratch, in a grate you cleaned out yourself. It burns well. It is "
                        + "not the fire that was here.",
                "Cold. Grey. Nothing in it but what a fire leaves behind, which is not much."));
        t.add(new Thing("the lamp", "in the window",
                "Lit in the window, where it has always been, where it can be seen from the road.",
                "On the table. It lights the room now instead of the road. It is not a signal any "
                        + "more, it is just a lamp.",
                "A new one, and it is brighter than the old one and it is in the window. It is not "
                        + "the lamp that was here.",
                "Out of oil. The wick is dry and black and there is a smear of soot on the inside "
                        + "of the glass."));
        t.add(new Thing("the plant", "on the sill",
                "Watered, upright, the same four leaves it had the morning they left.",
                "A second one beside it, in a tin, and the new one is yours. Two plants where "
                        + "there was one.",
                "Something new in the same pot. It is green and it is growing and it is not the "
                        + "plant that was here.",
                "A stick in dry soil. It went yellow, then it went thin, and then it was a stick."));
        t.add(new Thing("the clock", "on the mantel",
                "Wound. Ticking. Saying the right time, which is the only thing it has ever been "
                        + "asked to do.",
                "Stopped. You stopped it at the hour they left, and it has said that hour for days "
                        + "now.",
                "A new one, and it keeps better time than the old one ever did. The old one is in "
                        + "a drawer.",
                "Run down. The hands are wherever they were when it gave up, and they have not "
                        + "moved since."));
        t.add(new Thing("the bread", "on the board",
                "A fresh loaf, every day, on the board, the way it was the morning they left.",
                "Two loaves, and flour on the board, and the whole house smells like someone has "
                        + "been in it.",
                "Bought, not baked, and put on the board. It is bread. It is not the bread that "
                        + "was here.",
                "Mould. It went green at the edges, then it went soft, then it went."));
        return t;
    }

    /** The day's texture. One line, before the arithmetic. */
    public static String dayLine(int day) {
        return switch (day) {
            case 1 -> "The first day is the same as the day they left, minus them.";
            case 2 -> "Rain. The path outside the door goes to mud and stays there.";
            case 3 -> "You catch yourself listening for the gate. You stop yourself. It is day three.";
            case 4 -> "The house makes a noise in the night. You know what it is. You get up anyway.";
            case 5 -> "There is less of you today than there was yesterday, and it is not sleep "
                    + "that is missing.";
            case 6 -> "You do the arithmetic again, as if it will come out differently this time.";
            case 7 -> "A week. You say it out loud, to the room, and the room does not answer, "
                    + "which is correct.";
            case 8 -> "You have started talking to the things. Not to them. To the things.";
            case 9 -> "One unit of effort left in you a day. You knew this was coming. Knowing is "
                    + "different.";
            case 10 -> "You stand in the doorway for a long time before you spend anything.";
            case 11 -> "Tomorrow. You keep saying it. Tomorrow.";
            case 12 -> "They come back today.";
            default -> "";
        };
    }

    /** How much of you there is on a given day. */
    public static int unitsFor(int day) {
        if (day <= 4) return 3;
        if (day <= 8) return 2;
        return 1;
    }

    /** The whole budget, which is the number the game is actually about. */
    public static int totalUnits() {
        int n = 0;
        for (int d = 1; d <= DAYS; d++) n += unitsFor(d);
        return n;
    }

    /** What it would cost to hold every thing at full for the whole time. */
    public static int unitsToKeepEverything() { return DAYS * 5; }

    /**
     * Three. Not a balance number -- a consequence, and the one the whole game
     * is built on.
     *
     * A thing survives twelve days only if it is never left unkept for three
     * days running, because three unkept days is the whole of its condition.
     * The last stretch of the game is the days with the least of you in them,
     * and there is one unit on each of them. In that stretch a single keep
     * saves a thing only if it falls on the second or third day of the window:
     * kept on the first, it runs out on the last; kept on the last, it is
     * already gone. So the two middle days of the window save two things, and
     * the two outer days can be spent together on one more. Everything after
     * that is the player deciding which three, and whether they are still
     * theirs.
     */
    public static int maxSurvivors() {
        int min = unitsFor(DAYS);
        int window = 0;
        for (int d = DAYS; d >= 1 && unitsFor(d) == min; d--) window++;
        int units = 0;
        for (int d = DAYS - window + 1; d <= DAYS; d++) units += unitsFor(d);
        return Math.max(0, units - 1);
    }

    // --------------------------------------------------------------- state

    public List<Thing> things = new ArrayList<>();
    public int day = 1;
    public int effort;
    public boolean[] keptToday = new boolean[0];
    public boolean finished;
    public int spentKeeping;
    public int spentChanging;

    public static Vigil of() {
        Vigil v = new Vigil();
        v.things = defaultThings();
        v.day = 1;
        v.effort = unitsFor(1);
        v.keptToday = new boolean[v.things.size()];
        v.finished = false;
        v.spentKeeping = 0;
        v.spentChanging = 0;
        return v;
    }

    public Thing thing(int i) {
        return (i < 0 || i >= things.size()) ? null : things.get(i);
    }

    /** Spend one unit holding a thing at what it was. */
    public boolean keep(int i) {
        if (finished) return false;
        Thing t = thing(i);
        if (t == null || t.gone) return false;
        if (effort < KEEP_COST) return false;
        effort -= KEEP_COST;
        spentKeeping += KEEP_COST;
        t.condition = FULL;
        keptToday[i] = true;
        return true;
    }

    /**
     * Spend two units making a thing something else. Irreversible: a thing you
     * have already made yours cannot be made yours again, and there is no way
     * back to what it was. A thing that is gone can be replaced -- what goes in
     * its place is yours, and the thing that was there is still gone.
     */
    public boolean change(int i) {
        if (finished) return false;
        Thing t = thing(i);
        if (t == null) return false;
        if (!t.gone && t.mine()) return false;
        if (effort < CHANGE_COST) return false;
        effort -= CHANGE_COST;
        spentChanging += CHANGE_COST;
        if (t.gone) {
            t.gone = false;
            t.replaced = true;
        } else {
            t.changed = true;
        }
        t.condition = FULL;
        keptToday[i] = true;
        return true;
    }

    /**
     * A day passes. Everything not kept today loses a step, and a thing at
     * nothing is gone. Then tomorrow, which has less of you in it.
     */
    public void endDay() {
        if (finished) return;
        for (int i = 0; i < things.size(); i++) {
            Thing t = things.get(i);
            if (t.gone || keptToday[i]) continue;
            t.condition -= 1;
            if (t.condition <= 0) {
                t.condition = 0;
                t.gone = true;
                t.everLost = true;
            }
        }
        if (day >= DAYS) { finished = true; return; }
        day += 1;
        effort = unitsFor(day);
        keptToday = new boolean[things.size()];
    }

    /** End the day with effort still in hand. Legal, and usually a mistake. */
    public int unspent() { return effort; }

    // -------------------------------------------------------------- counts

    public int intact() {
        int n = 0;
        for (Thing t : things) if (t.state() == State.AS_LEFT) n++;
        return n;
    }

    public int mine() {
        int n = 0;
        for (Thing t : things) if (t.state() == State.CHANGED) n++;
        return n;
    }

    public int empty() {
        int n = 0;
        for (Thing t : things) if (t.state() == State.LOST) n++;
        return n;
    }

    /** How many things were lost at any point, including ones replaced since. */
    public int everLost() {
        int n = 0;
        for (Thing t : things) if (t.everLost) n++;
        return n;
    }

    public int replaced() {
        int n = 0;
        for (Thing t : things) if (t.replaced) n++;
        return n;
    }

    /**
     * theirs / yours / gone, for the header.
     *
     * Not "kept": on the first day nothing has been kept and all five are
     * still as they were, so a column headed kept would read "kept 5" to a
     * player who has done nothing. The column is about whose the things are.
     */
    static final String[] WORDS = {"none", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"};

    /**
     * Small counts, spelled. The prose was starting clauses with a digit --
     * "3 things come through twelve days in this house", "1 of them is exactly
     * what it was" -- which reads like a readout rather than a sentence. The
     * tally and the arithmetic panel keep their digits, because those two are
     * a readout.
     */
    public static String num(int n) {
        if (n < 0) return String.valueOf(n);
        if (n < 20) return WORDS[n];
        if (n == 20) return "twenty";
        if (n < 30) return "twenty-" + WORDS[n - 20];
        return String.valueOf(n);
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public String tally() {
        return "theirs " + intact() + "   yours " + mine() + "   gone " + empty();
    }

    // ------------------------------------------------------------- endings

    public String headline() {
        int m = mine(), i = intact();
        if (m == 0 && i == 0) return "Nothing left.";
        if (m == 0 && spentChanging > 0) return "Nothing of yours made it.";
        if (m == 0) return "As it was, what is left of it.";
        if (i == 0) return "Nothing of theirs.";
        return "Theirs, and yours.";
    }

    public String verdict() {
        int m = mine(), i = intact(), e = empty(), through = i + m;
        StringBuilder b = new StringBuilder();
        b.append("Twelve days, ").append(num(totalUnits())).append(" units of effort in you ")
         .append("altogether. ").append(cap(num(spentKeeping)))
         .append(" went on holding on to what was here");
        if (spentChanging > 0) {
            b.append(", and ").append(num(spentChanging)).append(" went on making it something else");
        }
        b.append(".\n\n");

        if (spentChanging == 0) {
            b.append("You did not spend a single unit on yourself. That was the promise, and you "
                    + "kept it exactly.\n\n");
        } else if (m == 0) {
            b.append("You spent ").append(num(spentChanging)).append(" units on yourself, and none ")
             .append("of it is here. What you changed went the way of everything else.\n\n");
        }

        b.append(cap(num(maxSurvivors()))).append(" things come through twelve days in this house, ")
         .append("whatever you do with them. You brought through ").append(num(through))
         .append(".\n\n");

        if (through == 0) {
            b.append("None of them is here. Every one of the five went, and the room they walk "
                    + "into has nothing in it that was in it before.");
        } else if (i == 0) {
            b.append("Not one of them is theirs. The things still standing in this room are things "
                    + "you put there, or things you made out of what was there.");
        } else if (m == 0) {
            b.append(i == 1
                    ? "It is exactly what it was. Nothing in this room is yours."
                    : "All of them are exactly what they were. Nothing in this room is yours.");
        } else {
            b.append(cap(num(i))).append(i == 1 ? " of them is" : " of them are")
             .append(" exactly what ").append(i == 1 ? "it was" : "they were")
             .append(", and ").append(num(m)).append(m == 1 ? " is yours." : " are yours.");
        }
        if (e > 0) {
            b.append(" ").append(cap(num(e)))
             .append(e == 1 ? " of the five is gone." : " of the five are gone.");
        }
        return b.toString();
    }

    public String arrival() {
        int m = mine(), i = intact(), e = empty();
        if (m == 0 && i == 0) {
            return "Nothing is where they left it, because nothing is there. They stand in the "
                 + "doorway for a long time.\n\nThen they come in, because there is nothing to come "
                 + "in to, and it turns out that is the same thing.";
        }
        if (m == 0) {
            return "They walk through it slowly. They stop at the places where something used to "
                 + "be, and they do not say anything about it.\n\nAt the end of it they stand in "
                 + "the middle of the room and look at you and say: you kept it.\n\nAnd you did. "
                 + "That is exactly what you did, and there is nothing in this house that says "
                 + "otherwise, because there is nothing in this house that says anything about you "
                 + "at all.";
        }
        if (i == 0) {
            return "They walk through a house that is entirely yours. They are careful about it. "
                 + "They touch nothing and they ask about nothing, and they are polite to you in a "
                 + "way they have never been polite to you before.";
        }
        return "They stop at the first thing that is not how they left it. They look at it for a "
             + "while. They do not touch it.\n\nThen they look at you, and it is not anger. It is "
             + "the look you give a house you have come back to and found lived in.";
    }

    public String closing() {
        int m = mine(), i = intact();
        if (m == 0 && i == 0) {
            return "You kept the promise and lost the house, and nobody could have done both.";
        }
        if (m == 0 && spentChanging > 0) {
            return "You broke the promise and it bought you nothing, because the thing you changed "
                 + "was going to go anyway.";
        }
        if (m == 0) {
            return "A room kept exactly as it was is a room with no one in it.";
        }
        if (i == 0) {
            return "A room that is entirely yours is not the room they left, and they will know it "
                 + "before they are through the door.";
        }
        return "You could not keep it as it was -- nobody could have. What you did instead was "
             + "leave something of yourself in it, and they will see it the moment they walk in. "
             + "That is the whole of what twelve days bought you, and it is not nothing.";
    }

    // ------------------------------------------------------------- storage

    public void save(Path p) throws Exception {
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        StringBuilder b = new StringBuilder();
        b.append("v1 ").append(day).append(' ').append(effort).append(' ')
         .append(spentKeeping).append(' ').append(spentChanging).append(' ')
         .append(finished ? 1 : 0).append('\n');
        for (int i = 0; i < things.size(); i++) {
            Thing t = things.get(i);
            b.append(t.condition).append(' ')
             .append(t.changed ? 1 : 0).append(' ')
             .append(t.replaced ? 1 : 0).append(' ')
             .append(t.gone ? 1 : 0).append(' ')
             .append(t.everLost ? 1 : 0).append(' ')
             .append(i < keptToday.length && keptToday[i] ? 1 : 0).append('\n');
        }
        Files.writeString(p, b.toString());
    }

    /**
     * A save that cannot be read is a fresh game, not an error. The player
     * loses twelve days of clicking, which is annoying; the alternative is a
     * game that will not open, which is worse.
     */
    public static Vigil load(Path p) {
        Vigil v = of();
        try {
            if (!Files.exists(p)) return v;
            List<String> lines = Files.readAllLines(p);
            if (lines.isEmpty()) return v;
            String[] h = lines.get(0).strip().split("\\s+");
            if (h.length < 6 || !h[0].equals("v1")) return v;
            int day = Integer.parseInt(h[1]);
            int effort = Integer.parseInt(h[2]);
            int sk = Integer.parseInt(h[3]);
            int sc = Integer.parseInt(h[4]);
            boolean fin = h[5].equals("1");
            if (day < 1 || day > DAYS) return v;
            if (effort < 0 || effort > unitsFor(day)) return v;
            if (sk < 0 || sc < 0) return v;

            boolean[] kept = new boolean[v.things.size()];
            for (int i = 0; i < v.things.size(); i++) {
                if (1 + i >= lines.size()) break;
                String[] f = lines.get(1 + i).strip().split("\\s+");
                if (f.length < 6) break;
                Thing t = v.things.get(i);
                int cond = Integer.parseInt(f[0]);
                if (cond < 0 || cond > FULL) break;
                t.condition = cond;
                t.changed = f[1].equals("1");
                t.replaced = f[2].equals("1");
                t.gone = f[3].equals("1");
                t.everLost = f[4].equals("1");
                kept[i] = f[5].equals("1");
                // A thing cannot be both gone and at full condition, and it
                // cannot be gone and kept today. A save that says otherwise is
                // a save that was hand-edited, so it is thrown out.
                if (t.gone && (t.condition > 0 || kept[i])) return of();
            }
            v.day = day;
            v.effort = effort;
            v.spentKeeping = sk;
            v.spentChanging = sc;
            v.finished = fin;
            v.keptToday = kept;
            return v;
        } catch (Exception e) {
            return of();
        }
    }
}
