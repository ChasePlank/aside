package aside.ui;

/**
 * Fat-jar entry point.
 *
 * `java -jar` refuses to start a class that extends Application when JavaFX is
 * on the classpath rather than the module path - it reports "JavaFX runtime
 * components are missing". Launching it from a class that is NOT an Application
 * subclass sidesteps that check, which is what lets a single self-contained jar
 * work with nothing but Java installed.
 */
public class Launcher {
    public static void main(String[] args) {
        javafx.application.Application.launch(Main.class, args);
    }
}
