package aside.game;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where a phone build generator reads its page template from.
 *
 * <pre>
 *   THE RULE: the SOURCE template, not a copy of it.
 * </pre>
 *
 * <p><b>This exists because the generators used to read from the classpath
 * alone, and that is a trap.</b> {@code src/main/resources/<game>/web.html} is
 * the file a person edits; {@code classes/<game>/web.html} is a copy that only
 * {@code cp -r src/main/resources/* classes/} refreshes. So editing the template
 * and re-running the generator <b>did nothing</b> -- the page came out of the
 * old copy -- and the failure was silent until something compared the page. It
 * was walked into twice in one day, and recorded as a build note both times,
 * which is the sign that a note is not enough.
 *
 * <p>So the source file wins when it is there, and the classpath is the fallback
 * for a build that has no source tree beside it -- a jar, or a checkout that
 * only has {@code classes/}. The order is the point: the thing a person edits is
 * the thing that gets read.
 */
public final class Templates {

    private Templates() { }

    /**
     * The template at {@code src/main/resources/<name>}, or the classpath copy.
     *
     * @param name the path under the resources root, e.g. {@code "fnaf8/web.html"}
     */
    public static String read(String name) throws Exception {
        Path source = Path.of("src", "main", "resources", name);
        if (Files.isRegularFile(source)) {
            return Files.readString(source, StandardCharsets.UTF_8);
        }
        try (InputStream in = Templates.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no template at " + source
                        + " and none on the classpath at " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
