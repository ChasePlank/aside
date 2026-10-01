package aside.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Export an .aside story to a SINGLE self-contained HTML file.
 *
 * Why this exists: the engine is JavaFX, and JavaFX does not run on a phone
 * without a lot of ceremony. The stories, though, are plain text with a simple
 * line-based format - so the portable thing is not the engine, it is the
 * script. One HTML file opens in any phone browser, needs no install, and can
 * be sent to someone as a single attachment, which is the whole point (Kinger,
 * Sept 29: "easier to show others that way").
 *
 * Format handled, matching the engine parser:
 *   # comment                     title: / author: / start:
 *   == label ==                   -> label
 *   * "choice" -> label           ~ var +1
 *   bg / music / sfx              show char expr at pos / hide char
 *   name: text                    name (expr): text
 *   anything else                 narration
 *
 * Unknown directives are ignored rather than fatal: a story that plays in the
 * engine should never fail to export because of a command this tool has not
 * heard of yet.
 *
 * ART NOW TRAVELS. This used to say "NOT YET RENDERED: art/sprites", and that
 * was the one thing standing between a visual novel and a phone: the words
 * went and the pictures stayed behind, so the build was a story you read
 * rather than a novel you looked at. The drawings are now inlined as data
 * URIs -- one file still, no folder to keep together -- and the staging the
 * script asks for is drawn the way VnScreen draws it: a background, up to
 * three figures on a shared floor line at the same three zones, and the
 * figures who are not speaking dimmed to 0.72.
 *
 * The images come from art/web/, which tools/vn-art.py builds by downscaling
 * the originals (14 MB of PNG is a 19 MB page; the web copies are ~1.2 MB).
 * That directory is committed, which is what lets this class stay the only
 * thing needed to regenerate a build -- aside.engine.SelfTest regenerates and
 * compares, and a check that needed PIL to run would not be a check.
 *
 * A POSE THAT WAS NEVER DRAWN IS RESOLVED HERE, NOT IN THE BROWSER. The
 * desktop falls back to the character's neutral and then to any pose at all
 * (aside.ui.Assets.sprite), and a phone build that fell back differently would
 * be a second version of the staging rather than a port of it. So the fallback
 * runs at export time, the item carries the key it resolved to, and the
 * browser is left with nothing to decide. What could not be resolved at all is
 * named in the export's "missing" list and printed when this runs as a tool --
 * a background the art library does not have should be a line of output, not a
 * black rectangle nobody can explain.
 */
public class WebExport {

    /** Where the web-sized art lives. Built by tools/vn-art.py; see its header. */
    static final Path ART = Path.of("art", "web");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: WebExport <story.aside> <out.html>");
            return;
        }
        String html = convert(Files.readString(Path.of(args[0])));
        Files.writeString(Path.of(args[1]), html);
        System.out.println("wrote " + args[1] + "  (" + html.length() / 1024 + " KB)");
        for (String m : missingIn(html)) System.out.println("  no art for: " + m);
    }

    /** The staging this export could not draw, read back out of a built page. */
    static List<String> missingIn(String html) {
        List<String> out = new ArrayList<>();
        int at = html.indexOf("\"missing\":[");
        if (at < 0) return out;
        int end = html.indexOf(']', at);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(html.substring(at + 11, end));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static String convert(String script) {
        return convert(script, ART);
    }

    static String convert(String script, Path artRoot) {
        String title = "Untitled";
        String author = "";
        String start = null;

        Map<String, List<Map<String, Object>>> passages = new LinkedHashMap<>();
        List<Map<String, Object>> current = null;
        String currentLabel = null;

        // What the script asks to see, in the order it asks. Collected while
        // parsing so the export inlines the art this story uses and not the
        // whole library -- the difference between a 1.2 MB page and a 4 MB one.
        Set<String> wantBg = new LinkedHashSet<>();
        Set<String> wantSprite = new LinkedHashSet<>();

        for (String raw : script.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;

            if (line.startsWith("title:"))  { title = line.substring(6).strip(); continue; }
            if (line.startsWith("author:")) { author = line.substring(7).strip(); continue; }
            if (line.startsWith("start:"))  { start = line.substring(6).strip(); continue; }

            if (line.startsWith("== ") && line.endsWith(" ==")) {
                currentLabel = line.substring(3, line.length() - 3).strip();
                if (currentLabel.equals("END")) break;
                current = new ArrayList<>();
                passages.put(currentLabel, current);
                continue;
            }
            if (current == null) continue;

            // choice
            if (line.startsWith("*")) {
                String rest = line.substring(1).strip();
                int arrow = rest.lastIndexOf("->");
                if (arrow < 0) continue;
                String label = cleanTarget(rest.substring(arrow + 2));
                String text = rest.substring(0, arrow).strip();
                if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
                    text = text.substring(1, text.length() - 1);
                }
                Map<String, Object> ch = item("choice", "text", text, "to", label);
                String cc = trailingCond(line);
                if (cc != null) ch.put("cond", cc);
                current.add(ch);
                continue;
            }

            // "if <expr> -> label" - the condition-PREFIX form, and the one the
            // stories actually use most. The expression can be an and/or of
            // comparisons and bare flags, so it is carried as a raw string and
            // evaluated by the player rather than being taken apart here.
            if (line.startsWith("if ") && line.contains("->")) {
                int arrow = line.lastIndexOf("->");
                String expr = line.substring(3, arrow).strip();
                String to = cleanTarget(line.substring(arrow + 2));
                if (!to.equals("END") && !expr.isEmpty()) {
                    Map<String, Object> it = item("jump", "to", to);
                    it.put("cond", expr);
                    current.add(it);
                }
                continue;
            }

            // jump. A condition may also ride along: "-> label [if aff_x >= 2]".
            if (line.startsWith("->")) {
                String to = cleanTarget(line.substring(2));
                if (to.equals("END")) continue;      // an ending: nothing follows
                Map<String, Object> it = item("jump", "to", to);
                String c = trailingCond(line);
                if (c != null) it.put("cond", c);
                current.add(it);
                continue;
            }

            // scene directive
            if (line.startsWith("bg ")) {
                String name = line.substring(3).strip();
                wantBg.add(name);
                current.add(item("bg", "name", name));
                continue;
            }
            if (line.startsWith("show ")) {
                // show char expr at pos   |   show char at pos   |   show char
                String rest = line.substring(5).strip();
                String who = rest, where = "", pose = "";
                int at = rest.indexOf(" at ");
                if (at >= 0) { who = rest.substring(0, at).strip(); where = rest.substring(at + 4).strip(); }
                int sp = who.indexOf(' ');
                if (sp >= 0) { pose = who.substring(sp + 1).strip(); who = who.substring(0, sp); }
                if (!pose.isEmpty()) wantSprite.add(who + "-" + pose);
                else wantSprite.add(who + "-neutral");
                current.add(item("show", "who", who, "pose", pose, "where", where));
                continue;
            }
            if (line.startsWith("hide ")) {
                current.add(item("hide", "who", line.substring(5).strip()));
                continue;
            }
            if (line.startsWith("music ") || line.startsWith("sfx ")) continue;   // no audio in the web build yet
            if (line.startsWith("~")) {
                // "~ aff_monty +1" -> name + delta, so conditions can be evaluated
                String rest = line.substring(1).strip();
                String[] parts = rest.split("\\s+");
                if (parts.length >= 2) {
                    current.add(item("flag", "name", parts[0], "delta", parts[1]));
                }
                continue;
            }

            // dialogue: "name: text" or "name (expr): text"
            int colon = line.indexOf(':');
            if (colon > 0 && colon < 24) {
                String who = line.substring(0, colon).strip();
                String text = line.substring(colon + 1).strip();
                if (!who.contains(" ") || who.matches("[A-Za-z0-9_'()\\- ]+")) {
                    current.add(item("say", "who", who, "text", text));
                    continue;
                }
            }

            current.add(item("narrate", "text", line));
        }

        if (start == null || !passages.containsKey(start)) {
            start = passages.isEmpty() ? "" : passages.keySet().iterator().next();
        }

        // ---- resolve the staging against the art that exists -----------------
        Set<String> bgFiles = stems(artRoot.resolve("backgrounds"), ".jpg", ".png");
        Set<String> spFiles = stems(artRoot.resolve("sprites"), ".webp", ".png");
        Map<String, String> assets = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        Path bgDir = artRoot.resolve("backgrounds");
        Path spDir = artRoot.resolve("sprites");

        for (String name : wantBg) {
            Path file = bgFiles.contains(name) ? fileFor(bgDir, name, ".jpg", ".png") : null;
            if (file != null) assets.put("bg:" + name, dataUri(file));
            else missing.add("bg " + name);
        }

        // The fallback runs here, once, and the item carries the answer: the
        // browser must not be able to disagree with the desktop about which
        // picture a missing pose falls back to.
        Map<String, String> poseFor = new LinkedHashMap<>();
        for (String want : wantSprite) {
            String key = resolveSprite(want, spFiles);
            if (key == null) { missing.add("show " + want); continue; }
            poseFor.put(want, key);
            if (!key.equals(want)) missing.add("show " + want + " (drawn as " + key + ")");
            assets.put("sp:" + key, dataUri(fileFor(spDir, key, ".webp", ".png")));
        }

        // Rewrite every show item to carry the key it resolved to, so the page
        // is a lookup and not a decision.
        for (List<Map<String, Object>> items : passages.values()) {
            for (Map<String, Object> it : items) {
                if (!"show".equals(it.get("type"))) continue;
                String who = String.valueOf(it.get("who"));
                String pose = String.valueOf(it.get("pose"));
                String want = pose.isEmpty() ? who + "-neutral" : who + "-" + pose;
                it.put("key", poseFor.getOrDefault(want, ""));
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"title\":").append(q(title))
            .append(",\"author\":").append(q(author))
            .append(",\"start\":").append(q(start))
            .append(",\"assets\":{");
        boolean firstA = true;
        for (Map.Entry<String, String> e : assets.entrySet()) {
            if (!firstA) json.append(',');
            firstA = false;
            json.append(q(e.getKey())).append(':').append(q(e.getValue()));
        }
        json.append("},\"missing\":[");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) json.append(',');
            json.append(q(missing.get(i)));
        }
        json.append("],\"passages\":{");
        boolean firstP = true;
        for (Map.Entry<String, List<Map<String, Object>>> e : passages.entrySet()) {
            if (!firstP) json.append(',');
            firstP = false;
            json.append(q(e.getKey())).append(":[");
            boolean firstI = true;
            for (Map<String, Object> it : e.getValue()) {
                if (!firstI) json.append(',');
                firstI = false;
                json.append('{');
                boolean firstK = true;
                for (Map.Entry<String, Object> kv : it.entrySet()) {
                    if (!firstK) json.append(',');
                    firstK = false;
                    json.append(q(kv.getKey())).append(':').append(q(String.valueOf(kv.getValue())));
                }
                json.append('}');
            }
            json.append(']');
        }
        json.append("}}");

        return HTML_TEMPLATE.replace("__TITLE__", title.replace("<", "&lt;"))
                            .replace("__STORY__", json.toString());
    }

    /** File stems in a directory, for the extensions a web build may hold. */
    static Set<String> stems(Path dir, String... exts) {
        Set<String> out = new TreeSet<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.map(p -> p.getFileName().toString()).forEach(n -> {
                for (String e : exts) {
                    if (n.endsWith(e)) { out.add(n.substring(0, n.length() - e.length())); return; }
                }
            });
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    /** The file a stem actually lives in, so a .png background still exports. */
    static Path fileFor(Path dir, String stem, String... exts) {
        for (String e : exts) {
            Path p = dir.resolve(stem + e);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    /**
     * The pose a character's art actually has, mirroring aside.ui.Assets.sprite:
     * the pose asked for, then this character's neutral, then any pose at all.
     * Null when the character has no art whatsoever.
     */
    static String resolveSprite(String want, Set<String> available) {
        if (available.contains(want)) return want;
        int dash = want.lastIndexOf('-');
        if (dash < 0) return null;
        String ch = want.substring(0, dash);
        if (available.contains(ch + "-neutral")) return ch + "-neutral";
        for (String k : available) if (k.startsWith(ch + "-")) return k;
        return null;
    }

    static String dataUri(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".webp") ? "image/webp"
                        : name.endsWith(".png") ? "image/png"
                        : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * One story item: a TYPE plus key/value pairs.
     *
     * This used to treat ALL its arguments as alternating key/value, so
     * `item("bg", "name", "office_night")` produced {"bg":"name"} - every item
     * in the export was garbage and every passage came out empty. The type is
     * its own leading argument, and the JS switches on it.
     */
    /** "label [if aff_x >= 2]" -> "label". */
    private static String cleanTarget(String s) {
        s = s.strip();
        int br = s.indexOf('[');
        return (br >= 0 ? s.substring(0, br) : s).strip();
    }

    /**
     * The expression inside a trailing "[if ...]", or null.
     *
     * Returned as a raw STRING, not taken apart into name/op/value: the stories
     * use "A and B" and "A or B", so a single comparison is not enough. It is
     * carried to the player and evaluated there against the live flags.
     */
    private static String trailingCond(String line) {
        int br = line.indexOf('[');
        if (br < 0) return null;
        int end = line.indexOf(']', br);
        String inner = line.substring(br + 1, end < 0 ? line.length() : end).strip();
        if (inner.startsWith("if")) inner = inner.substring(2).strip();
        return inner.isEmpty() ? null : inner;
    }

    private static Map<String, Object> item(String type, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static final String HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0d0a09">
<title>__TITLE__</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; background: #0d0a09; color: #e8e4dc;
    font: 17px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    -webkit-text-size-adjust: 100%;
  }
  #bar { position: fixed; top: 0; left: 0; height: 3px; background: #c4622b; width: 0;
         transition: width .25s; z-index: 5; }
  /* A phone VN is a fixed frame with a scrolling script under it, not a
     document with a picture in it. So the page itself does not scroll: the
     stage holds the top, the log is the only thing that moves, and the choices
     sit at the bottom where a thumb already is. The stage is the desktop's
     1280x720 frame at 16:9 -- the same frame, so the same three zones and the
     same floor line, and a room that is framed the way the room was drawn
     rather than cropped to a phone's idea of a picture. */
  html, body { height: 100%; }
  body { margin: 0; display: flex; flex-direction: column; height: 100dvh; overflow: hidden; }
  #stage, #place, #log, #choices, #hint { width: 100%; max-width: 760px; margin: 0 auto; }
  #stage { flex: 0 0 auto; position: relative; overflow: hidden; background: #101018;
           aspect-ratio: 16 / 9; max-height: 46dvh; border-bottom: 1px solid #241d19; }
  /* contain, not cover, and anchored to the bottom. On a phone the stage is
     exactly 16:9 so the two are the same thing; on a laptop the height cap
     makes the stage wider than the room, and cover would answer that by
     cropping the top and bottom off the room. A letterbox keeps the whole
     frame and keeps the figures standing on the image's floor line. */
  #bg { position: absolute; inset: 0; background-size: contain;
        background-position: center bottom; background-repeat: no-repeat; }
  #cast { position: absolute; inset: 0; }
  #cast img { position: absolute; bottom: 1.9%; height: 91.7%; width: auto;
              transform: translateX(-50%); transition: opacity .18s; }
  #place { flex: 0 0 auto; padding: 11px 20px 0; font-size: 12px; letter-spacing: .14em;
           text-transform: uppercase; color: #8a7f70; min-height: 15px; }
  #log { flex: 1 1 auto; overflow-y: auto; overscroll-behavior: contain;
         padding: 8px 20px 0; -webkit-overflow-scrolling: touch; }
  #log p { margin: 0 0 14px; }
  #log .say { color: #e8e4dc; }
  #log .who { display: block; font-size: 12px; letter-spacing: .12em; text-transform: uppercase;
              color: #c4622b; margin-bottom: 3px; }
  #log .narrate { color: #b3aa9c; font-style: italic; }
  #log .old { opacity: .38; }
  #choices { flex: 0 0 auto; padding: 0 20px 6px; max-height: 42dvh; overflow-y: auto; }
  button {
    display: block; width: 100%; margin: 0 0 10px; padding: 15px 16px;
    background: #1c1614; color: #e8e4dc; border: 1px solid #3a2f28; border-radius: 10px;
    font: inherit; text-align: left; cursor: pointer;
  }
  button:hover { border-color: #c4622b; }
  #hint { flex: 0 0 auto; padding: 0 20px 16px; color: #6e655a; font-size: 13px; }
  @media (prefers-reduced-motion: reduce) { #bar, #cast img { transition: none; } }
</style>
</head>
<body>
<div id="bar"></div>
<div id="stage"><div id="bg"></div><div id="cast"></div></div>
<div id="place"></div>
<div id="log"></div>
<div id="choices"></div>
<div id="hint">tap anywhere to continue</div>
<script>
const STORY = __STORY__;
const ASSETS = STORY.assets || {};
let here = STORY.start, step = 0, lines = [], lastSpeaker = null;
const vars = {};   // affection and anything else set by "~ name +1"
const log = document.getElementById('log');
const choices = document.getElementById('choices');
const place = document.getElementById('place');
const hint = document.getElementById('hint');
const bar = document.getElementById('bar');
const bgEl = document.getElementById('bg');
const castEl = document.getElementById('cast');
const castEls = {};   // character -> <img>, so a pose change does not flash

function applyFlag(it) {
  const d = parseFloat(it.delta);
  vars[it.name] = (vars[it.name] || 0) + (isNaN(d) ? 0 : d);
}

// A condition arrives as a raw expression ("listened >= 1 and aff_chica >= 2").
// It is rewritten into JavaScript and run against the flags. Without this the
// web build would take the FIRST branch every time, which does not simplify the
// story - it tells a different one, and most of the later scenes would never be
// reached at all.
//
// The word boundaries below are written with a DOUBLE backslash, and that is
// not cosmetic: this template is a Java text block, where a single backslash-b
// is the backspace character. Spelled with one backslash it compiled, exported,
// and shipped a regex that matched nothing, so "and" survived into the
// expression, the Function() threw, and condOK's catch returned true -- every
// compound condition silently taking its first branch. No story here uses
// and/or yet, which is the only reason it was never seen. SelfTest now asserts
// the boundary is a boundary, and that the page holds no control characters.
function condOK(it) {
  if (!it.cond) return true;
  const js = it.cond
    .replace(/\\band\\b/g, '&&')
    .replace(/\\bor\\b/g, '||')
    .replace(/\\bnot\\b/g, '!')
    .replace(/[A-Za-z_][A-Za-z0-9_]*/g, m => 'vars.' + m);
  try { return !!Function('vars', 'return (' + js + ')')(vars); }
  catch (e) { return true; }   // an expression this build cannot read: do not block the story
}

function esc(s) { return s.replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }

// ---- staging, drawn the way VnScreen draws it ----
// Zones are 292 / 640 / 988 of 1280, the floor is 706 of 720 and a figure is
// 660 of 720 tall, so the phone frame is the desktop frame in percentages and
// the two cannot drift apart by a pixel count.
function zoneLeft(where) {
  return where === 'left' ? '22.8%' : where === 'right' ? '77.2%' : '50%';
}
function showChar(it) {
  const src = ASSETS['sp:' + it.key];
  let el = castEls[it.who];
  if (!el) { el = document.createElement('img'); el.alt = it.who; castEls[it.who] = el; castEl.appendChild(el); }
  if (src) { if (el.getAttribute('src') !== src) el.setAttribute('src', src); el.style.display = ''; }
  else el.style.display = 'none';
  el.style.left = zoneLeft(it.where);
}
function hideChar(it) {
  const el = castEls[it.who];
  if (el) { el.remove(); delete castEls[it.who]; }
}
// Whoever is not talking is dimmed, exactly as the desktop dims them; during
// narration nobody is talking and everybody is lit.
function dimCast() {
  for (const ch in castEls) {
    castEls[ch].style.opacity = (lastSpeaker === null || lastSpeaker === ch) ? '1' : '0.72';
  }
}

// A jump changes the scene and NOTHING ELSE. The room and whoever is standing
// in it are state, not scenery: Vn.enter() moves sceneId and index and leaves
// background/shown/stagePos alone, so a scene that opens without a "bg" line
// carries on in the room the last one set up. Clearing them here looked
// harmless and was not -- the first jump out of the opening scene wiped the
// office off the screen, and every scene after it that did not restate its
// background played against black. Only a restart clears the stage.
function enter(label) {
  here = label; step = 0; lines = STORY.passages[label] || [];
  log.innerHTML = ''; choices.innerHTML = '';
  lastSpeaker = null;
  advance();
}

function advance() {
  // consume non-visual items, then show one line
  while (step < lines.length) {
    const it = lines[step++];
    if (it.type === "bg") {
      place.textContent = it.name.replace(/_/g, " ");
      const src = ASSETS['bg:' + it.name];
      bgEl.style.backgroundImage = src ? 'url("' + src + '")' : '';
      continue;
    }
    if (it.type === "show") { showChar(it); dimCast(); continue; }
    if (it.type === "hide") { hideChar(it); dimCast(); continue; }
    if (it.type === "flag") { applyFlag(it); continue; }
    if (it.type === "jump") { if (condOK(it)) { enter(it.to); return; } continue; }
    if (it.type === "choice") { step--; offer(); return; }
    render(it);
    progress();
    return;
  }
  // passage ran out with no jump: nothing more to say
  hint.textContent = 'the end';
}

function render(it) {
  for (const p of log.children) p.classList.add('old');
  const p = document.createElement('p');
  if (it.type === "say") {
    p.className = 'say';
    p.innerHTML = '<span class="who">' + esc(it.who) + '</span>' + esc(it.text);
    lastSpeaker = it.who;
  } else {
    p.className = 'narrate';
    p.textContent = it.text;
    lastSpeaker = null;
  }
  dimCast();
  log.appendChild(p);
  p.scrollIntoView({ block: 'nearest' });
  hint.textContent = 'tap anywhere to continue';
}

function offer() {
  const opts = [];
  while (step < lines.length && lines[step].type === "choice") {
    const c = lines[step++];
    if (condOK(c)) opts.push(c);
  }
  for (const o of opts) {
    const b = document.createElement('button');
    b.textContent = o.text;
    b.onclick = ev => { ev.stopPropagation(); enter(o.to); };
    choices.appendChild(b);
  }
  hint.textContent = 'choose';
  progress();
}

function progress() {
  const pct = lines.length ? Math.min(100, Math.round(100 * step / lines.length)) : 0;
  bar.style.width = pct + '%';
}

document.body.addEventListener('click', () => { if (!choices.children.length) advance(); });
document.addEventListener('keydown', e => {
  if ((e.key === ' ' || e.key === 'Enter') && !choices.children.length) advance();
});
enter(STORY.start);
</script>
</body>
</html>
""";
}
