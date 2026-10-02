package aside.game;

/**
 * The phone builds' sound, as one piece of JavaScript.
 *
 * <p><b>Why this exists.</b> FNAF 6's and FNAF 9's phone builds synthesise
 * their own audio -- that is what keeps a build one file with no audio in it --
 * and the other six shipped without any. That was a real loss rather than a
 * polish item, because in this franchise the sound is not decoration: FNAF 3's
 * only verb is a noise and its whole night is spent listening for a footstep on
 * a graph; FNAF 4's countdown is audible, and the cue that says <i>which</i> of
 * the four is one move out is what the entire pre-empt half of that game runs
 * on; FNAF 5's Ballora follows sound and the cue is the only channel that says
 * something is next door; and FNAF 2's arrival cue is the fix for Chase's own
 * playtest note, quoted in the engine -- <i>"I checked lights, went to wind the
 * music box, and got jumpscared. If the timer between entering and killing is 5
 * seconds, then they arent visible."</i> A silent port of any of those is a
 * port of a different, easier game.
 *
 * <p><b>Why it is one file and not six.</b> Six copies of a synthesiser is six
 * copies that drift, which is the same argument the generators are built on.
 * So the palette lives here, each template carries a {@code /*__AUDIO__*}{@code /}
 * marker, and each generator splices this in. A game's own cue names are mapped
 * to the palette in its template, because that mapping is the game's business:
 * FNAF 8 deliberately gives both halls the same footfall, and FNAF 9 has no
 * directional channel at all.
 *
 * <p><b>What it is not.</b> It is not the desktop's audio. The desktop plays
 * WAV and MP3 files that are megabytes; this is a handful of oscillators and
 * filtered noise, and it is meant to carry the same <i>information</i> -- a
 * footstep, a knock, a breath, a clank, static, a scream -- rather than the same
 * recording. The information is the part the game is made of.
 */
public final class WebAudio {

    /** The marker every phone template carries, and the generator replaces. */
    public static final String MARKER = "/*__AUDIO__*/";

    /** The synthesiser, as JavaScript. */
    public static String js() {
        return """
// --------------------------------------------------------------- the sound
//
// Synthesised, so the page stays one file with no audio in it. The palette is
// shared by every phone build -- see aside.game.WebAudio -- and what each game
// does with it is its own business: the cue names below are mapped to these
// voices in the page, because the mapping is the game.
//
// A browser will not start audio until the player has touched the page, so the
// context is created on the first cue and resumed on every one after that. The
// first tap of a night is what turns the sound on, which is the same gesture
// that starts the night.

let AC = null;

function ac() {
  if (!AC) {
    const K = window.AudioContext || window.webkitAudioContext;
    if (!K) return null;
    try { AC = new K(); } catch (e) { return null; }
  }
  if (AC.state === 'suspended') AC.resume();
  return AC;
}

function noiseBuf(a, dur) {
  const n = Math.max(1, Math.floor(a.sampleRate * dur));
  const b = a.createBuffer(1, n, a.sampleRate);
  const d = b.getChannelData(0);
  for (let i = 0; i < n; i++) d[i] = Math.random() * 2 - 1;
  return b;
}

function tone(a, t, f0, f1, dur, type, gain) {
  const o = a.createOscillator();
  o.type = type;
  o.frequency.setValueAtTime(f0, t);
  if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(Math.max(1, f1), t + dur);
  const g = a.createGain();
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(gain, t + 0.012);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g).connect(a.destination);
  o.start(t); o.stop(t + dur + 0.02);
}

function hiss(a, t, dur, type, freq, q, gain, bendTo) {
  const s = a.createBufferSource();
  s.buffer = noiseBuf(a, dur);
  const f = a.createBiquadFilter();
  f.type = type;
  f.frequency.setValueAtTime(freq, t);
  if (bendTo) f.frequency.linearRampToValueAtTime(bendTo, t + dur);
  f.Q.value = q;
  const g = a.createGain();
  g.gain.setValueAtTime(0.0001, t);
  g.gain.linearRampToValueAtTime(gain, t + Math.min(0.04, dur / 3));
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(f).connect(g).connect(a.destination);
  s.start(t); s.stop(t + dur + 0.02);
}

/**
 * One voice. Every game's cues land on one of these, and the palette is
 * deliberately small: a footstep, a knock, a breath, a clank, static, a click,
 * a chime, a scream, and a system going down or coming back.
 */
function voice(name) {
  const a = ac();
  if (!a) return;
  const t = a.currentTime;
  switch (name) {
    case 'footstep':
      // A footfall somewhere in the building. Low, and not close.
      hiss(a, t, 0.30, 'lowpass', 1500, 1, 0.34);
      tone(a, t, 92, 74, 0.26, 'sine', 0.30);
      break;
    case 'step':
      // The same footfall, one room away. Louder and drier, so the distance
      // is audible rather than only readable.
      hiss(a, t, 0.24, 'lowpass', 2100, 1, 0.52);
      tone(a, t, 108, 84, 0.22, 'sine', 0.44);
      break;
    case 'knock':
      // Something arriving at the door. Heavier than a step and shorter.
      hiss(a, t, 0.42, 'bandpass', 2600, 1, 0.72, 900);
      tone(a, t, 118, 82, 0.36, 'sine', 0.56);
      break;
    case 'breath':
      // Something standing there. A soft swell, not a hit -- it has to be
      // recognisable while it is repeating every second and a half.
      hiss(a, t, 0.55, 'bandpass', 700, 0.8, 0.30, 480);
      break;
    case 'clank':
      // The lure: a metal thing struck in an empty room.
      hiss(a, t, 0.10, 'highpass', 4200, 1, 0.46);
      tone(a, t, 620, 300, 0.30, 'square', 0.20);
      tone(a, t + 0.02, 940, 520, 0.22, 'sine', 0.16);
      break;
    case 'static':
      // A feed, or a phantom. Short and bright.
      hiss(a, t, 0.34, 'highpass', 2400, 1, 0.40);
      break;
    case 'click':
      // A switch, a light, a panel.
      hiss(a, t, 0.05, 'highpass', 5200, 1, 0.44);
      tone(a, t + 0.005, 1150, 1150, 0.10, 'sine', 0.16);
      break;
    case 'chime':
      // Six AM.
      tone(a, t, 880, 880, 0.55, 'sine', 0.22);
      tone(a, t + 0.16, 1320, 1320, 0.60, 'sine', 0.18);
      break;
    case 'down':
      // A system failing. It falls.
      tone(a, t, 320, 52, 0.85, 'sawtooth', 0.30);
      hiss(a, t, 0.70, 'lowpass', 900, 1, 0.30);
      break;
    case 'up':
      // And coming back.
      tone(a, t, 180, 620, 0.42, 'sine', 0.24);
      break;
    case 'laugh':
      // Something laughing in the dark. Three short falling tones, which is
      // as close as a palette this small gets to a voice.
      tone(a, t, 520, 380, 0.16, 'triangle', 0.20);
      tone(a, t + 0.18, 470, 330, 0.16, 'triangle', 0.18);
      tone(a, t + 0.36, 430, 290, 0.20, 'triangle', 0.16);
      break;
    case 'scream':
      // The one cue nobody chose.
      tone(a, t, 2200, 90, 1.10, 'sawtooth', 0.34);
      hiss(a, t, 1.10, 'highpass', 900, 1, 0.30);
      break;
    default:
      break;
  }
}
""";
    }

    private WebAudio() {}
}
