package aside.games.changeover;

import aside.engine.Script;
import aside.game.Game;
import aside.games.overtime.VnScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.io.File;

/**
 * Changeover, as a game module.
 *
 * <p>Two projectionists who ran a cinema together, and the year it stopped. The carbon arc is the part nobody warns
 * you about.
 *
 * <p>THE TENTH REGISTER AND THE FIRST WITH TWO NARRATORS. Every story before this has one voice. This one
 * alternates: every scene is a letter, and the letters go back and forth between two people who are no longer in
 * the same building. <b>The reader sees both sides; neither of them does.</b>
 *
 * <p>That is the whole formal idea. The choices are what each of them WRITES, and because a letter cannot be
 * unsent, a choice made in one scene is a fact the other person has to answer in the next. Nothing here is hidden
 * from the reader and everything is hidden from the characters, which is the opposite of how the other nine work.
 *
 * <p>AND IT SHOWS THAT THE PATH COUNT IS NOT A SIGNATURE OF THE FORM. A440 has one choice and three paths;
 * this has fifteen choices and three paths, because these branches REJOIN - a letter answered is a letter
 * answered. The count says how much the story forks, not what shape it is.
 *
 * <p>ONE BACKGROUND and no sprites. The whole story is in the booth, and the other person is only ever a letter.
 */
public class ChangeoverGame implements Game {

    static final String STORY = "stories/changeover.aside";

    @Override public String id() { return "changeover"; }

    @Override public String title() { return "Changeover"; }

    @Override public String blurb() {
        return "Two projectionists, one booth, and the year the cinema stopped.";
    }

    @Override
    public UiScreen create(UiManager ui) {
        try {
            Script s = Script.load(new File(STORY).toPath());
            aside.ui.Assets.loadStory(".", "changeover");
            return new VnScreen(ui, s, s.title);
        } catch (Exception e) {
            throw new IllegalStateException("could not load " + STORY + ": " + e.getMessage(), e);
        }
    }
}
