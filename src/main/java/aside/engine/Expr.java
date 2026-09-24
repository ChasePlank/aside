package aside.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The condition and effect mini-language.
 *
 * Conditions (used on choices and jumps):
 *   aff_roxy >= 2        aff_roxy < 3      met_monty
 *   !met_monty           a and b           a or b
 *
 * Effects (used with `~` and `set`):
 *   aff_roxy +1     aff_roxy -1     aff_roxy += 2     aff_roxy = 5
 *   flag = true     name = "string"
 *
 * Deliberately small. Anything more expressive belongs in the host
 * language, not in the script file.
 */
public final class Expr {

    // ---------- conditions ----------

    public static boolean test(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) return true;
        List<String> toks = tokenize(expr);
        try {
            Parser p = new Parser(toks, vars);
            boolean r = p.orExpr();
            return r;
        } catch (RuntimeException e) {
            // A broken condition is treated as false and surfaced by
            // the bot's script audit rather than crashing a playthrough.
            return false;
        }
    }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '(' || c == ')' || c == '!') { out.add(String.valueOf(c)); i++; continue; }
            if (c == '>' || c == '<' || c == '=') {
                StringBuilder op = new StringBuilder();
                while (i < n && (s.charAt(i) == '>' || s.charAt(i) == '<'
                        || s.charAt(i) == '=' || s.charAt(i) == '!')) {
                    op.append(s.charAt(i++));
                }
                out.add(op.toString());
                continue;
            }
            if (c == '"') {
                int j = s.indexOf('"', i + 1);
                if (j < 0) j = n - 1;
                out.add(s.substring(i, Math.min(j + 1, n)));
                i = j + 1;
                continue;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i))
                    && "()!<>=,".indexOf(s.charAt(i)) < 0) i++;
            out.add(s.substring(start, i));
        }
        return out;
    }

    static class Parser {
        final List<String> t;
        int i;
        final Map<String, Object> vars;

        Parser(List<String> t, Map<String, Object> vars) { this.t = t; this.vars = vars; }

        String peek() { return i < t.size() ? t.get(i) : null; }
        String next() { return i < t.size() ? t.get(i++) : null; }

        boolean orExpr() {
            boolean left = andExpr();
            while ("or".equals(peek())) { next(); boolean r = andExpr(); left = left || r; }
            return left;
        }

        boolean andExpr() {
            boolean left = term();
            while ("and".equals(peek())) { next(); boolean r = term(); left = left && r; }
            return left;
        }

        boolean term() {
            String tk = peek();
            if (tk == null) return false;
            if (tk.equals("!")) { next(); return !term(); }
            if (tk.equals("not")) { next(); return !term(); }
            if (tk.equals("(")) {
                next();
                boolean v = orExpr();
                if ("".equals(peek()) || ")".equals(peek())) next();
                return v;
            }
            return comparison();
        }

        boolean comparison() {
            String name = next();
            if (name == null) return false;
            Object lv = vars.get(name);
            String op = peek();
            if (op != null && (op.equals(">=") || op.equals("<=") || op.equals(">")
                    || op.equals("<") || op.equals("==") || op.equals("!=")
                    || op.equals("="))) {
                next();
                String rhsRaw = next();
                return compare(lv, op, rhsRaw);
            }
            return truthy(lv);
        }

        boolean compare(Object lv, String op, String rhsRaw) {
            if (rhsRaw == null) return false;
            Double ln = asNumber(lv);
            Double rn = asNumber(rhsRaw);
            if (ln != null && rn != null) {
                int c = Double.compare(ln, rn);
                return switch (op) {
                    case ">=" -> c >= 0;
                    case "<=" -> c <= 0;
                    case ">" -> c > 0;
                    case "<" -> c < 0;
                    case "==", "=" -> c == 0;
                    case "!=" -> c != 0;
                    default -> false;
                };
            }
            String ls = lv == null ? "" : unquote(String.valueOf(lv));
            String rs = unquote(rhsRaw);
            return switch (op) {
                case "==", "=" -> ls.equals(rs);
                case "!=" -> !ls.equals(rs);
                default -> false;
            };
        }
    }

    // ---------- effects ----------

    /** Applies one effect string to the variable map. */
    public static void apply(String effect, Map<String, Object> vars) {
        String s = effect.trim();
        if (s.isEmpty()) return;

        // name OP value
        int k = 0;
        while (k < s.length() && (Character.isLetterOrDigit(s.charAt(k)) || s.charAt(k) == '_')) k++;
        if (k == 0) return;
        String name = s.substring(0, k);
        String rest = s.substring(k).trim();
        if (rest.isEmpty()) { vars.put(name, Boolean.TRUE); return; }

        String op;
        String val;
        if (rest.startsWith("+="))      { op = "+"; val = rest.substring(2).trim(); }
        else if (rest.startsWith("-=")) { op = "-"; val = rest.substring(2).trim(); }
        else if (rest.startsWith("="))  { op = "="; val = rest.substring(1).trim(); }
        else if (rest.startsWith("+"))  { op = "+"; val = rest.substring(1).trim(); }
        else if (rest.startsWith("-"))  { op = "-"; val = rest.substring(1).trim(); }
        else                            { op = "="; val = rest; }

        Double cn = asNumber(vars.get(name));
        Double vn = asNumber(val);

        if (op.equals("=")) {
            vars.put(name, coerce(val));
        } else {
            double base = cn == null ? 0 : cn;
            double delta = vn == null ? 0 : vn;
            vars.put(name, op.equals("+") ? base + delta : base - delta);
        }
    }

    public static Object coerce(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        Double d = asNumber(s);
        return d != null ? d : s;
    }

    public static Double asNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof Boolean b) return b ? 1.0 : 0.0;
        try { return Double.parseDouble(String.valueOf(o).trim()); }
        catch (NumberFormatException e) { return null; }
    }

    static boolean truthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        Double d = asNumber(o);
        if (d != null) return d != 0;
        return !String.valueOf(o).isBlank();
    }

    static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private Expr() {}
}
