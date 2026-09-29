package aside.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * NOT YET RENDERED: art/sprites. Text, choices, backgrounds-by-name and the
 * branching all work; the drawings do not travel yet.
 */
public class WebExport {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: WebExport <story.aside> <out.html>");
            return;
        }
        String html = convert(Files.readString(Path.of(args[0])));
        Files.writeString(Path.of(args[1]), html);
        System.out.println("wrote " + args[1] + "  (" + html.length() / 1024 + " KB)");
    }

    static String convert(String script) {
        String title = "Untitled";
        String author = "";
        String start = null;

        Map<String, List<Map<String, Object>>> passages = new LinkedHashMap<>();
        List<Map<String, Object>> current = null;
        String currentLabel = null;

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
                current.add(item("bg", "name", line.substring(3).strip()));
                continue;
            }
            if (line.startsWith("show ")) {
                // show char expr at pos   |   show char at pos   |   show char
                String rest = line.substring(5).strip();
                String who = rest, where = "";
                int at = rest.indexOf(" at ");
                if (at >= 0) { who = rest.substring(0, at).strip(); where = rest.substring(at + 4).strip(); }
                int sp = who.indexOf(' ');
                if (sp >= 0) who = who.substring(0, sp);          // drop the expression
                current.add(item("show", "who", who, "where", where));
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

        StringBuilder json = new StringBuilder();
        json.append("{\"title\":").append(q(title))
            .append(",\"author\":").append(q(author))
            .append(",\"start\":").append(q(start))
            .append(",\"passages\":{");
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
  * { box-sizing: border-box; }
  body {
    margin: 0; background: #0d0a09; color: #e8e4dc;
    font: 17px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    -webkit-text-size-adjust: 100%;
  }
  #wrap { max-width: 720px; margin: 0 auto; min-height: 100dvh; display: flex; flex-direction: column; }
  #stage { flex: 1; padding: 22px 20px 0; }
  #place { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: #8a7f70; margin-bottom: 14px; min-height: 16px; }
  #log p { margin: 0 0 14px; }
  #log .say { color: #e8e4dc; }
  #log .who { display: block; font-size: 12px; letter-spacing: .12em; text-transform: uppercase; color: #c4622b; margin-bottom: 3px; }
  #log .narrate { color: #b3aa9c; font-style: italic; }
  #log .old { opacity: .38; }
  #choices { padding: 0 20px 8px; }
  button {
    display: block; width: 100%; margin: 0 0 10px; padding: 15px 16px;
    background: #1c1614; color: #e8e4dc; border: 1px solid #3a2f28; border-radius: 10px;
    font: inherit; text-align: left; cursor: pointer;
  }
  button:hover { border-color: #c4622b; }
  #hint { padding: 0 20px 26px; color: #6e655a; font-size: 13px; }
  #bar { height: 3px; background: #c4622b; width: 0; transition: width .25s; }
  @media (prefers-reduced-motion: reduce) { #bar { transition: none; } }
</style>
</head>
<body>
<div id="bar"></div>
<div id="wrap">
  <div id="stage">
    <div id="place"></div>
    <div id="log"></div>
  </div>
  <div id="choices"></div>
  <div id="hint">tap anywhere to continue</div>
</div>
<script>
const STORY = __STORY__;
let here = STORY.start, step = 0, lines = [];
const vars = {};   // affection and anything else set by "~ name +1"
const log = document.getElementById('log');
const choices = document.getElementById('choices');
const place = document.getElementById('place');
const hint = document.getElementById('hint');
const bar = document.getElementById('bar');

function applyFlag(it) {
  const d = parseFloat(it.delta);
  vars[it.name] = (vars[it.name] || 0) + (isNaN(d) ? 0 : d);
}

// A condition arrives as a raw expression ("listened >= 1 and aff_chica >= 2").
// It is rewritten into JavaScript and run against the flags. Without this the
// web build would take the FIRST branch every time, which does not simplify the
// story - it tells a different one, and most of the later scenes would never be
// reached at all.
function condOK(it) {
  if (!it.cond) return true;
  const js = it.cond
    .replace(/\band\b/g, '&&')
    .replace(/\bor\b/g, '||')
    .replace(/\bnot\b/g, '!')
    .replace(/[A-Za-z_][A-Za-z0-9_]*/g, m => 'vars.' + m);
  try { return !!Function('vars', 'return (' + js + ')')(vars); }
  catch (e) { return true; }   // an expression this build cannot read: do not block the story
}

function esc(s) { return s.replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }

function enter(label) {
  here = label; step = 0; lines = STORY.passages[label] || [];
  log.innerHTML = ''; choices.innerHTML = ''; place.textContent = '';
  advance();
}

function advance() {
  // consume non-visual items, then show one line
  while (step < lines.length) {
    const it = lines[step++];
    if (it.type === "bg") { place.textContent = it.name.replace(/_/g, " "); continue; }
    if (it.type === "show" || it.type === "hide") continue;
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
  } else {
    p.className = 'narrate';
    p.textContent = it.text;
  }
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
