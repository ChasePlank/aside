package aside.games.fnaf4.engine;

import aside.games.fnaf4.MouseMap;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The FNAF 4 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf4.engine.SelfTest
 *
 * These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that the room has a shape, that the light reaches exactly one move, that
 * a flash at nothing costs something, that Fredbear is a consequence of
 * noise rather than a coin flip, and that the week gets harder.
 *
 * The survival numbers are printed rather than asserted tightly, because
 * they are a *reading* of the difficulty, not a contract. What is asserted
 * is the shape: idle must die, pre-empting must beat reacting, sweeping
 * must be worse than both, and the week must get harder.
 */
public final class SelfTest {

    static int checks = 0;
    static int failed = 0;

    public static void main(String[] args) {
        // `--survival` runs only the sweep, which is the slow part and the
        // only part that changes when a difficulty number moves. Tuning
        // means running this a dozen times in a row.
        if (args.length > 0 && args[0].equals("--survival")) {
            survival();
            System.out.println();
            System.out.println(checks + " checks, " + failed + " failed");
            if (failed > 0) System.exit(1);
            return;
        }
        room();
        light();
        threats();
        fredbear();
        strip();
        clock();
        phone();
        survival();
        System.out.println();
        System.out.println(checks + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------- the room

    static void room() {
        section("the room");

        check("four places", Room.ALL.length == 4);
        check("the bed is the hub", Room.HUB == Room.Where.BED);

        for (Room.Where a : Room.ALL) {
            check("you are already at " + a.label,
                    Room.hops(a, a) == 0);
            for (Room.Where b : Room.ALL) {
                check("hops are symmetric: " + a.label + " / " + b.label,
                        Room.hops(a, b) == Room.hops(b, a));
            }
        }

        for (Room.Where w : Room.ALL) {
            if (w == Room.HUB) continue;
            check("the bed is one hop from " + w.label, Room.hops(Room.HUB, w) == 1);
        }
        check("a door to a door is two hops",
                Room.hops(Room.Where.LEFT, Room.Where.RIGHT) == 2);
        check("a door to the closet is two hops",
                Room.hops(Room.Where.LEFT, Room.Where.CLOSET) == 2);

        // The number the whole difficulty table is set against.
        check("the worst trip is two hops and a flash ("
                        + round(Game.worstTrip()) + "s)",
                Math.abs(Game.worstTrip() - (2 * Game.HOP_TIME + Game.FLASH_TIME)) < 1e-9);
    }

    // ------------------------------------------------------------ the light

    static void light() {
        section("the light");

        Game g = new Game(1, 42);
        g.where = Room.Where.LEFT;
        Threat bonnie = byKey(g, "bonnie");

        // Out of reach: the light does nothing, and still costs.
        bonnie.distance = 3;
        check("a flash does not reach three moves out", !g.flash());
        check("and it is counted as wasted", g.wastedFlashes == 1);
        check("and it made noise", g.noise > 0);

        // One move out: this is the pre-empt, and it is the whole skill.
        g.busy = 0;
        bonnie.distance = 1;
        check("a flash reaches one move out", g.flash());
        check("and turns it back to " + Game.PUSH_TO, bonnie.distance == Game.PUSH_TO);
        check("and it was not wasted", g.wastedFlashes == 1);

        // Standing there: the answer to a breath.
        g.busy = 0;
        bonnie.distance = 0;
        check("a flash reaches what is standing there", g.flash());
        check("and sends it all the way back", bonnie.distance == Game.DIST_MAX);

        // The asymmetry is the price of pre-empting, and it is what stops
        // "flash everything early" from being the answer to the game.
        check("catching it in the hall is worth less than catching it here",
                Game.PUSH_TO < Game.DIST_MAX);

        // The light only reaches the station you are at.
        g.busy = 0;
        Threat chica = byKey(g, "chica");
        chica.distance = 0;
        check("a flash does not reach the other door", !g.flash());
        check("and the other door is still standing there", chica.distance == 0);

        // Hands are busy.
        g.busy = 0;
        g.flash();
        check("the light cannot be used twice at once", !g.canFlash());
        check("and the second press does nothing", !g.flash());

        // Noise.
        Game n = new Game(1, 7);
        n.flash();
        double after = n.noise;
        check("a flash costs noise (" + round(after) + ")",
                Math.abs(after - Game.NOISE_PER_FLASH) < 1e-9);
        n.update(1.0);
        check("and the room goes quiet again (" + round(n.noise) + ")",
                n.noise < after);
        check("noise never goes below zero", n.noise >= 0);

        Game cap = new Game(1, 7);
        for (int i = 0; i < 40; i++) { cap.busy = 0; cap.flash(); }
        check("noise is capped at " + (int) Game.NOISE_MAX, cap.noise == Game.NOISE_MAX);
    }

    // ---------------------------------------------------------- the threats

    static void threats() {
        section("the threats");

        Game g = new Game(1, 11);
        check("four of them, one per place", g.threats.size() == 4);
        for (Room.Where w : Room.ALL) {
            int n = 0;
            for (Threat t : g.threats) if (t.home == w) n++;
            check("exactly one is coming for " + w.label, n == 1);
        }

        check("nobody starts at the door", g.threats.stream().allMatch(t -> t.distance > 0));

        // The countdown, and the breath on arrival.
        Game c = new Game(1, 3);
        Threat t = byKey(c, "bonnie");
        t.distance = 1;
        t.timer = 0;
        c.drainCues();
        // Step it just past one interval.
        c.update(c.interval(t) + 0.01);
        check("it arrives after its interval", t.distance == 0);
        check("and it breathes when it does", c.drainCues().contains("breath_bonnie"));

        // The grace is a real clock.
        Game d = new Game(1, 5);
        Threat dt = byKey(d, "foxy");
        dt.distance = 0;
        dt.hereFor = 0;
        double step = d.grace() / 2;
        d.update(step);
        check("half the grace is survivable", d.status == Game.Status.PLAYING);
        d.update(step + 0.05);
        check("and the whole of it is not", d.status == Game.Status.JUMPSCARED);
        check("and it says who", "Nightmare Foxy".equals(d.killer));

        // It keeps announcing itself while it waits.
        Game e = new Game(1, 9);
        Threat et = byKey(e, "chica");
        et.distance = 0;
        et.hereFor = 0;
        e.drainCues();
        e.update(Game.BREATH_EVERY + 0.01);
        check("a waiting threat breathes again",
                e.drainCues().contains("breath_chica"));

        // The grace is the dial, and the dial is what makes the week.
        check("night 1 can be answered from anywhere ("
                        + round(new Game(1, 1).grace()) + "s > "
                        + round(Game.worstTrip()) + "s)",
                new Game(1, 1).grace() > Game.worstTrip());
        check("night 5 barely can ("
                        + round(new Game(5, 1).grace()) + "s)",
                new Game(5, 1).grace() - Game.worstTrip() < 0.5);
        check("and the week gets harder",
                new Game(5, 1).grace() < new Game(1, 1).grace());
    }

    // ----------------------------------------------------------- Fredbear

    static void fredbear() {
        section("Fredbear");

        Game g = new Game(1, 21);
        check("he is not on night 1", g.fredbearRate() == 0.0);
        for (int i = 0; i < 400; i++) g.update(1.0 / 60.0);
        check("and does not turn up on night 1", g.fredbearAt == null);

        check("he is on night 3", new Game(3, 1).fredbearRate() > 0);
        check("and more of him on night 5",
                new Game(5, 1).fredbearRate() > new Game(3, 1).fredbearRate());

        // He is a consequence of noise, not a coin flip.
        Game quiet = new Game(5, 1);
        Game loud = new Game(5, 1);
        loud.noise = Game.NOISE_MAX;
        check("noise makes him likelier (" + round(quiet.fredbearChance() * 1000)
                        + " vs " + round(loud.fredbearChance() * 1000) + " per 1000s)",
                loud.fredbearChance() > quiet.fredbearChance() * 3);

        // He never appears where you already are.
        Game s = new Game(5, 33);
        boolean away = true;
        for (int i = 0; i < 200; i++) {
            s.fredbearAt = null;
            s.fredbearCooldown = 0;
            s.where = Room.ALL[i % Room.ALL.length];
            s.spawnFredbear();
            if (s.fredbearAt == s.where) away = false;
        }
        check("he never appears where you are standing", away);

        // The flash finds him, and finding him is what sends him away.
        Game f = new Game(5, 44);
        f.where = Room.Where.CLOSET;
        f.fredbearAt = Room.Where.CLOSET;
        f.fredbearHere = 0;
        check("the light reaches him", f.flash());
        check("and he leaves", f.fredbearAt == null);
        check("and the room is quiet for a while", f.fredbearCooldown > 0);

        // And he is a deadline, not a decoration.
        Game w = new Game(5, 55);
        w.fredbearAt = Room.Where.LEFT;
        w.fredbearHere = 0;
        w.update(Game.FREDBEAR_GRACE + 0.05);
        check("waiting him out is fatal", w.status == Game.Status.JUMPSCARED);
        check("and it is him", "Nightmare Fredbear".equals(w.killer));

        // The cooldown is real.
        Game cd = new Game(5, 66);
        cd.fredbearAt = Room.Where.RIGHT;
        cd.where = Room.Where.RIGHT;
        cd.flash();
        cd.fredbearAt = null;
        cd.update(Game.FREDBEAR_COOLDOWN / 2);
        check("he cannot come straight back", cd.fredbearAt == null);
    }

    // ------------------------------------------------------------- the strip

    static void strip() {
        section("the strip");

        // Every station is clickable, and the click lands on the one drawn.
        for (int i = 0; i < Room.ALL.length; i++) {
            double[] r = MouseMap.station(i);
            double cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
            MouseMap.Hit h = MouseMap.hit(cx, cy);
            check("clicking the middle of " + Room.ALL[i].label + " picks it",
                    h.kind() == MouseMap.Kind.STATION && h.index() == i);
        }

        // No overlap: the classic mouse bug is two regions that both claim
        // a point, and the only way to catch it is to compare every pair.
        int overlaps = 0;
        for (int i = 0; i < Room.ALL.length; i++) {
            for (int j = i + 1; j < Room.ALL.length; j++) {
                if (crosses(MouseMap.station(i), MouseMap.station(j))) overlaps++;
            }
        }
        check("no two buttons claim the same pixel", overlaps == 0);

        // And every button is inside the strip it is drawn in.
        int outside = 0;
        for (int i = 0; i < Room.ALL.length; i++) {
            double[] r = MouseMap.station(i);
            if (r[0] < MouseMap.BAR[0] || r[0] + r[2] > MouseMap.BAR[0] + MouseMap.BAR[2]
                    || r[1] < MouseMap.BAR[1] || r[1] + r[3] > MouseMap.BAR[1] + MouseMap.BAR[3]) {
                outside++;
            }
        }
        check("every button is inside the strip", outside == 0);

        // The strip wins over the scene, because it is drawn on top of it.
        double[] r = MouseMap.station(0);
        check("the strip is not also the room",
                MouseMap.hit(r[0] + 1, r[1] + 1).kind() == MouseMap.Kind.STATION);

        // Everything above the strip is the light.
        check("the room is the light",
                MouseMap.hit(MouseMap.W / 2, 300).kind() == MouseMap.Kind.SCENE);
        check("the corner of the room is the light",
                MouseMap.hit(4, 4).kind() == MouseMap.Kind.SCENE);

        // And the night rows, which share the screen with nothing.
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            double[] row = MouseMap.nightRow(n);
            check("night " + n + " is clickable",
                    MouseMap.nightAt(row[0] + row[2] / 2, row[1] + row[3] / 2) == n);
        }
        check("and there are five of them", MouseMap.NIGHTS == 5);
    }

    // -------------------------------------------------------------- the clock

    static void clock() {
        section("the clock");

        Game g = new Game(1, 77);
        double full = Game.HOUR_SECONDS * Game.NIGHT_HOURS;
        g.update(full - 0.1);
        check("the night is not over early", g.status == Game.Status.PLAYING);
        g.update(0.2);
        check("six hours ends it", g.status == Game.Status.SURVIVED);
        check("and the hour reads six", g.hour >= Game.NIGHT_HOURS);

        Game d = new Game(1, 78);
        d.update(1.0);
        d.status = Game.Status.JUMPSCARED;
        double t = d.time;
        d.update(5.0);
        check("a finished night stops moving", Math.abs(d.time - t) < 1e-9);
    }

    // ------------------------------------------------------------ survival

    /**
     * The phone build.
     *
     * <p>Two different things, and they fail for different reasons.
     *
     * <p><b>Is it current?</b> A generated file that has gone stale is worse
     * than no file: it is a second copy of the game quietly disagreeing with
     * the first. So the page is regenerated and compared rather than
     * spot-checked -- the same check every other ported game carries.
     *
     * <p><b>Is it the same game?</b> A staleness check proves the file matches
     * its generator and says nothing about whether the generator is right. The
     * failure that would otherwise be silent is a rule that lives in the page
     * as a literal and has stopped matching the engine -- so every number the
     * night is made of is asserted to be the engine's own value, and the three
     * night tables are asserted to be the engine's own tables.
     *
     * <p>What is <i>not</i> checked here is the RNG or the update loop. The
     * page reproduces {@code java.util.Random} in BigInt -- including
     * {@code nextInt}, which is a rejection sampler and not a modulo -- so the
     * same seed deals the same night in both builds, and that was verified by
     * driving both engines under the same scripted policy: night one on seed
     * 1001 produces identical traces every two seconds (the station, the
     * noise, all four distances and Fredbear's position) and both end
     * JUMPSCARED to the same killer at the same moment. It is not checkable
     * from Java without a JavaScript engine, and the alternative (a page that
     * deals its own nights) would make the two builds different games with the
     * same rules.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf4.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf4.html can be read", false);
            return;
        }
        try {
            check("web/fnaf4.html is current -- regenerate it with aside.games.fnaf4.WebRoom",
                    aside.games.fnaf4.WebRoom.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf4.html is current -- regenerate it with aside.games.fnaf4.WebRoom",
                    false);
        }

        // The rules, as numbers. A port whose hop is 1.2 rather than 1.15
        // plays differently and looks identical.
        check("the page carries the hop",
                page.contains("hopTime: " + aside.games.fnaf4.WebRoom.num(Game.HOP_TIME)));
        check("the page carries the flash",
                page.contains("flashTime: " + aside.games.fnaf4.WebRoom.num(Game.FLASH_TIME)));
        check("the page carries how long a station stays lit",
                page.contains("litTime: " + aside.games.fnaf4.WebRoom.num(Game.LIT_TIME)));
        check("the page carries where the light sends something it catches",
                page.contains("pushTo: " + Game.PUSH_TO));
        check("the page carries the length of the room",
                page.contains("distMax: " + Game.DIST_MAX));
        check("the page carries what a flash costs in noise",
                page.contains("noisePerFlash: " + aside.games.fnaf4.WebRoom.num(Game.NOISE_PER_FLASH)));
        check("the page carries how much more often Fredbear comes at full noise",
                page.contains("noiseWeight: " + aside.games.fnaf4.WebRoom.num(Game.NOISE_WEIGHT)));
        check("the page carries Fredbear's grace",
                page.contains("fredbearGrace: " + aside.games.fnaf4.WebRoom.num(Game.FREDBEAR_GRACE)));

        // The three night tables, which are the whole of the difficulty ramp.
        for (String t : new String[]{"baseInterval", "grace", "fredbearRate"}) {
            String want = aside.games.fnaf4.WebRoom.table(
                    switch (t) {
                        case "baseInterval" -> 0;
                        case "grace" -> 1;
                        default -> 2;
                    });
            check("the page carries the " + t + " table", page.contains(t + ": " + want));
        }

        // The art. The desktop draws each station in three states and the
        // difference between them is the game, so every station has to have
        // all three -- and the keys are the engine's own station names,
        // because the page looks a station up by name.
        for (String st : new String[]{"BED", "LEFT", "RIGHT", "CLOSET"}) {
            for (String state : new String[]{"dark", "lit", "here"}) {
                check("the page carries the " + state + " frame for " + st,
                        page.contains("\"" + st + ":" + state + "\":\"data:image/jpeg;base64,"));
            }
        }
        check("the page carries Fredbear",
                page.contains("\"fredbear\":\"data:image/webp;base64,"));
        for (String key : new String[]{"bonnie", "chica", "foxy", "fredbear", "freddy"}) {
            check("the page carries the scare frame for " + key,
                    page.contains("\"scare:" + key + "\":\"data:image/jpeg;base64,"));
        }

        // And the four threats, which are the four stations.
        for (String name : new String[]{"Nightmare Bonnie", "Nightmare Chica",
                "Nightmare Foxy", "Nightmare Freddy"}) {
            check("the page carries " + name, page.contains(name));
        }
    
        // The sound. This is the game where the sound *is* the countdown. The engine says it:
        // "a step when it is one move out, a breath when it is standing there." The step
        // cue names which of the four is close, and that name is what the whole pre-empt
        // half of the game runs on -- without it a player would know something was near
        // and not what, and "go and look" would be a guess rather than a decision.
        check("the page carries the shared synthesiser",
                page.contains("function voice(") && page.contains("function sfx("));
        check("the page has a voice for the chime_6am cue",
                page.contains("case \"chime_6am\""));
        check("the page has a voice for the door_close cue",
                page.contains("case \"door_close\""));
        check("the page has a voice for the footstep cue",
                page.contains("case \"footstep\""));
        check("the page has a voice for the fredbear_laugh cue",
                page.contains("case \"fredbear_laugh\""));
        check("the page has a voice for the light_click cue",
                page.contains("case \"light_click\""));
        check("the page has a voice for the scare_sprint cue",
                page.contains("case \"scare_sprint\""));
        check("the page has a voice for the breath_* cues",
                page.contains("startsWith(\"breath_\")"));
        check("the page has a voice for the step_* cues",
                page.contains("startsWith(\"step_\")"));
}

    static void survival() {
        section("survival, 60 seeds a night");

        Bot.Policy[] policies = {Bot.Policy.IDLE, Bot.Policy.REACT,
                Bot.Policy.HOLD, Bot.Policy.SWEEP};
        double[][] rate = new double[policies.length][5];
        for (int n = 1; n <= 5; n++) {
            StringBuilder line = new StringBuilder(String.format("  night %d:", n));
            for (int p = 0; p < policies.length; p++) {
                rate[p][n - 1] = Bot.survival(n, 60, policies[p]);
                line.append(String.format("   %s %3.0f%%",
                        policies[p].name().toLowerCase(), rate[p][n - 1] * 100));
            }
            System.out.println(line);
        }

        boolean idleDies = true;
        for (double v : rate[0]) if (v > 0.05) idleDies = false;
        check("a player who does nothing dies on every night", idleDies);

        // The reach is the point: pushing something back one move out has
        // to be worth the flash, or the light is only a reaction button and
        // the whole pre-empt half of the design is decoration.
        double sumReact = 0, sumHold = 0;
        for (int i = 0; i < 5; i++) { sumReact += rate[1][i]; sumHold += rate[2][i]; }
        check("pushing things back early beats waiting (avg "
                        + pct(sumReact / 5) + " -> " + pct(sumHold / 5) + ")",
                sumHold > sumReact + 0.15);

        // And the cost: a player who just holds the light down must be
        // much worse, or noise and Fredbear are both decoration.
        double sumSweep = 0;
        for (int i = 0; i < 5; i++) sumSweep += rate[3][i];
        check("flashing at everything is much worse (avg "
                        + pct(sumSweep / 5) + ")",
                sumSweep < sumHold - 0.5);

        check("a competent player survives night 1", rate[2][0] >= 0.7);
        check("the week gets harder (night 5 <= night 1)",
                rate[2][4] <= rate[2][0]);
        check("night 5 is not free", rate[2][4] <= 0.85);
    }

    // --------------------------------------------------------------- utils

    static Threat byKey(Game g, String key) {
        for (Threat t : g.threats) if (t.key.equals(key)) return t;
        throw new IllegalStateException("no threat " + key);
    }

    /** Do two rectangles share any point at all? */
    static boolean crosses(double[] a, double[] b) {
        return a[0] <= b[0] + b[2] && b[0] <= a[0] + a[2]
                && a[1] <= b[1] + b[3] && b[1] <= a[1] + a[3];
    }

    static String pct(double d) {
        return String.format("%.0f%%", d * 100);
    }

    static String round(double d) {
        return String.format("%.2f", d);
    }

    static void section(String name) {
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) failed++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
    }

    private SelfTest() {}
}
