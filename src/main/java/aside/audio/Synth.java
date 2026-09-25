package aside.audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Generates the sounds that are filtering and noise rather than
 * performance -- a fan, static, and mechanical transients. Anything
 * that needs a musician's ear (the music box) is not attempted here.
 *
 * Writes 16-bit mono PCM WAV by hand so there is no javax.sound or
 * external dependency.
 *
 * Usage: java aside.audio.Synth <outputDir>
 */
public class Synth {
    static final int RATE = 44100;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "audio");
        out.mkdirs();

        write(new File(out, "fan_hum.wav"), fanHum(6.0));
        write(new File(out, "static.wav"), staticNoise(4.0));
        write(new File(out, "door_close.wav"), thud(0.55, 70, 0.35));
        write(new File(out, "door_open.wav"), thud(0.40, 95, 0.22));
        write(new File(out, "light_click.wav"), click(0.05));
        write(new File(out, "footstep.wav"), thud(0.22, 120, 0.18));
        write(new File(out, "pot_clank.wav"), clank());
        write(new File(out, "power_down.wav"), sweep(1.6, 420, 40));
        write(new File(out, "power_up.wav"), sweep(1.1, 60, 380));
        write(new File(out, "chime_6am.wav"), chime());
    }

    // ---------------- sound builders ----------------

    /** A room fan: low rumble, filtered noise, and a slow rotation
     *  wobble so it breathes instead of hissing flatly. */
    static double[] fanHum(double seconds) {
        int n = (int) (seconds * RATE);
        double[] s = new double[n];
        Random r = new Random(7);
        double lp = 0, rumble = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double white = r.nextDouble() * 2 - 1;
            lp += 0.06 * (white - lp);                 // low-passed air
            rumble += 0.004 * (white - rumble);        // very low motor
            double wobble = 1.0 + 0.18 * Math.sin(2 * Math.PI * 2.1 * t);
            double blade = 0.05 * Math.sin(2 * Math.PI * 41 * t);
            s[i] = (lp * 0.5 + rumble * 3.0 + blade) * wobble * 0.55;
        }
        loopEnds(s);
        return s;
    }

    /** Camera static: broadband hiss with occasional crackle. */
    static double[] staticNoise(double seconds) {
        int n = (int) (seconds * RATE);
        double[] s = new double[n];
        Random r = new Random(11);
        double hp = 0, prev = 0;
        for (int i = 0; i < n; i++) {
            double w = r.nextDouble() * 2 - 1;
            hp = w - prev + 0.92 * hp;                 // gentle high-pass
            prev = w;
            double crackle = r.nextDouble() < 0.00035 ? (r.nextDouble() - 0.5) * 1.6 : 0;
            s[i] = hp * 0.16 + crackle;
        }
        loopEnds(s);
        return s;
    }

    /** Low mechanical thump: a fast attack and a soft body. */
    static double[] thud(double seconds, double freq, double body) {
        int n = (int) (seconds * RATE);
        double[] s = new double[n];
        Random r = new Random(3);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * (9.0 / seconds));
            double tone = Math.sin(2 * Math.PI * freq * t) * body;
            double thump = Math.sin(2 * Math.PI * (freq * 0.4) * t) * (1 - body);
            double noise = (r.nextDouble() * 2 - 1) * 0.25 * Math.exp(-t * 55);
            s[i] = (tone + thump + noise) * env * 0.8;
        }
        return s;
    }

    /** A small hard click for the light switch. */
    static double[] click(double seconds) {
        int n = (int) (seconds * RATE);
        double[] s = new double[n];
        Random r = new Random(5);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * 400);
            s[i] = (r.nextDouble() * 2 - 1) * env * 0.5;
        }
        return s;
    }

    /** Metal in a sink: two inharmonic partials beating against each
     *  other, which is what makes it read as struck metal. */
    static double[] clank() {
        int n = (int) (1.1 * RATE);
        double[] s = new double[n];
        double[] partials = {1180, 1730, 2420, 3310};
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double v = 0;
            for (int p = 0; p < partials.length; p++) {
                double decay = 3.2 + p * 1.7;
                v += Math.sin(2 * Math.PI * partials[p] * t)
                        * Math.exp(-t * decay) / (p + 1.5);
            }
            s[i] = v * 0.45;
        }
        return s;
    }

    /** Power failing / returning: a pitch gliding under a dying level. */
    static double[] sweep(double seconds, double from, double to) {
        int n = (int) (seconds * RATE);
        double[] s = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double k = t / seconds;
            double f = from + (to - from) * k;
            phase += 2 * Math.PI * f / RATE;
            double env = from > to ? Math.exp(-t * 1.2) : Math.min(1, t * 1.6);
            s[i] = (Math.sin(phase) * 0.6 + Math.sin(phase * 0.5) * 0.25) * env * 0.5;
        }
        return s;
    }

    /** The 6AM bell: a struck triad with a long tail. */
    static double[] chime() {
        int n = (int) (2.6 * RATE);
        double[] s = new double[n];
        double[] notes = {523.25, 659.25, 783.99};     // C E G
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double v = 0;
            for (int k = 0; k < notes.length; k++) {
                double delay = k * 0.16;
                if (t < delay) continue;
                double tt = t - delay;
                v += (Math.sin(2 * Math.PI * notes[k] * tt)
                        + 0.3 * Math.sin(4 * Math.PI * notes[k] * tt))
                        * Math.exp(-tt * 1.6);
            }
            s[i] = v * 0.22;
        }
        return s;
    }

    /**
     * Cross-fade the tail into the head so a loop has no click at the
     * seam. Without this a looping fan thumps once per cycle, which is
     * far more noticeable than the seam it was meant to hide.
     */
    static void loopEnds(double[] s) {
        int fade = Math.min(RATE / 4, s.length / 8);
        for (int i = 0; i < fade; i++) {
            double k = i / (double) fade;
            s[i] = s[i] * k + s[s.length - fade + i] * (1 - k);
        }
        for (int i = 0; i < fade; i++) {
            s[s.length - fade + i] = s[i];
        }
    }

    // ---------------- wav output ----------------

    static void write(File f, double[] samples) throws Exception {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = (int) Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write("WAVE".getBytes("ASCII"));
        body.write("fmt ".getBytes("ASCII"));
        body.write(le(16, 4));
        body.write(le(1, 2));            // PCM
        body.write(le(1, 2));            // mono
        body.write(le(RATE, 4));
        body.write(le(RATE * 2, 4));     // byte rate
        body.write(le(2, 2));            // block align
        body.write(le(16, 2));           // bits
        body.write("data".getBytes("ASCII"));
        body.write(le(pcm.length, 4));
        body.write(pcm);

        byte[] data = body.toByteArray();
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write("RIFF".getBytes("ASCII"));
            os.write(le(4 + data.length, 4));
            os.write(data);
        }
        System.out.printf("  %-18s %5.2fs  %6d KB%n",
                f.getName(), samples.length / (double) RATE, f.length() / 1024);
    }

    static byte[] le(int v, int bytes) {
        byte[] b = new byte[bytes];
        for (int i = 0; i < bytes; i++) b[i] = (byte) ((v >> (8 * i)) & 0xFF);
        return b;
    }
}
