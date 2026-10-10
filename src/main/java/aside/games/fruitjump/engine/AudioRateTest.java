package aside.games.fruitjump.engine;

/**
 * Landing sounds are rate-limited, and for one commit they were not.
 *
 * <p><b>FOUND BY tools/tautologies.py:</b> `AudioSystem.LAND_COOLDOWN_TIME` could be set to zero with nothing
 * noticing. The reason was not a missing assertion - it was that the cooldown did not reach the sound. `playSfx`
 * posted the cue to `pending` FIRST and checked the cooldown after, and `Sound.drain` plays everything in that
 * queue, so ten landings in one instant reached the speakers as ten sounds while the headless log said "SFX land"
 * once. A log line was making a claim about the audio that the audio was not making, which is the same fault as
 * the volume this class used to print and could not honestly report.
 *
 * <p><b>IT LIVES IN THE ENGINE PACKAGE because `Sfx` is package-private</b> - `playSfx` takes one, so no test in
 * `aside.engine` can call it at all. That is the same shape as BlastTest and PlayerModelTest, and the reason to
 * check here rather than widen the enum: the enum is not the thing under test.
 */
public class AudioRateTest {
    static int failures = 0;
    static int passes = 0;

    static void verdict(String what, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + what);
        if (ok) passes++; else failures++;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** Run the whole test and return the number of failures, so a gate can fold it in. */
    public static int runAll() {
        failures = 0;
        passes = 0;

        AudioSystem s = new AudioSystem();
        // Ten landings in the same instant: what a bounce, a slope seam or a landing on a moving platform produces.
        for (int i = 0; i < 10; i++) s.playSfx(AudioSystem.Sfx.LAND);
        int burst = s.drainPending().size();
        verdict("ten landings in one instant reach the backend as ONE sound, not ten (" + burst + ")", burst == 1);

        // And the cooldown must not be a mute: after the window, a landing gets through again.
        for (double t = 0; t < 0.2; t += GameLoop.DT) s.update(GameLoop.DT);
        s.playSfx(AudioSystem.Sfx.LAND);
        int after = s.drainPending().size();
        verdict("and a landing after the cooldown window does reach it (" + after + ")", after == 1);

        // Only landing is rate-limited: two jumps in one instant are two sounds, which is the point of a cooldown
        // that belongs to one cue rather than to the mixer.
        s.playSfx(AudioSystem.Sfx.JUMP);
        s.playSfx(AudioSystem.Sfx.JUMP);
        int jumps = s.drainPending().size();
        verdict("and other cues are not rate-limited by it (two jumps give " + jumps + ")", jumps == 2);

        System.out.println("\n=== " + passes + " passed, " + failures + " failed ===");
        return failures;
    }
}
