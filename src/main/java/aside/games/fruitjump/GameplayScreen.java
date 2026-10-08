package aside.games.fruitjump;

import aside.ui.UiManager;
import aside.ui.UiScreen;

import aside.games.fruitjump.engine.*;
import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.ArrayList;

/**
 * Gameplay screen: runs the headless platformer engine inside JavaFX.
 *
 * The engine stays display-agnostic — this screen is the VIEW layer:
 * - AnimationTimer fires per display pulse; an accumulator converts
 *   that to fixed 1/60 engine steps (the engine never sees variable dt)
 * - Canvas renders the world each frame (rect debug view; sprites later)
 * - Key events set player velocity
 * - HUD overlays hearts/keys/level
 * - ESC pushes the pause overlay (gameplay frozen underneath)
 */
public class GameplayScreen extends UiScreen {
    // Tuned constants (match the validator's verified values)
    private static final double RUN_SPEED = 200;
    private static final double JUMP_V = -420;
    // High-res: window and canvas are 2x the engine's logical 800x600.
    // The engine (physics, world coords) is untouched — only the VIEW
    // scales. Nearest-neighbor smoothing keeps pixel art crisp at 2x.
    private static final double SCALE = 2.0;

    /**
     * How many levels the climb takes to reach the deepest sky.
     *
     * <p><b>THE RUN IS ONE EVENING, AND THIS IS THE ONLY THING THAT SAYS SO.</b> The game's own sentence is "a
     * climber, a sunset, and a way home" - and until this existed every generated level looked identical, so a
     * forty-level run had no sense of going anywhere and the sunset read as a wallpaper rather than as a time of
     * day. The sun now starts high and sinks as the run goes on, and the sky deepens with it.
     *
     * <p>It is deliberately MODEST and deliberately ONE NUMBER. The world is a black silhouette against this sky,
     * so a sky that darkened too far would swallow the thing the player is looking at - and the premise is a
     * sunset, not a night. Forty levels is far enough that the change reads as weather over a run rather than as a
     * palette swap between adjacent levels.
     *
     * <p>The tutorial does not use it at all: its levels are hand-built and the sunset is the game's identity, so
     * they keep the canonical sky. This is progression for the generated run, which is the part that has none.
     */
    public static final int LEVELS_TO_DUSK = LevelGen.FINAL_LEVEL;
    private static final int VIEW_W = 800, VIEW_H = 600;
    private static final int CANVAS_W = (int) (VIEW_W * SCALE), CANVAS_H = (int) (VIEW_H * SCALE);

    // Level grid. 20 rows is not arbitrary: at 2x a cell is 64 physical
    // px, so 20 rows = 1280px, just over the 1200px canvas, and the
    // level fills the window. (At 14 rows the level was 896px tall
    // inside a 1200px canvas, so most of the vertical space was empty --
    // that is what made everything read as "sitting at the top".)
    // PUBLIC LIKE THE ENGINE'S OWN REPORTS, because a check has to be able to read them. They are the game's
    // shape, not an internal: 20 rows at 64 physical pixels is 1280px against a 1200px canvas, and the mutation
    // sweep found both numbers unprotected - 14 rows, the value the comment says made everything "read as sitting
    // at the top", left the gate green at 455 passed, 0 failed.
    public static final int LEVEL_W = 60, LEVEL_H = 20;

    // Engine state
    private final World world;
    private final Combat combat;
    private final Physics.Body player;
    private final PlayerInventory inventory;
    private final LevelMap map;
    private final Camera camera;
    private final int levelNum;

    /** Ridge profiles, as fractions of one period across and of the layer's amplitude up. Jagged rather than
     *  smooth on purpose: a smooth hill on a horizon reads as a smudge, and the four other screens that draw this
     *  horizon (Skyline) use a polygon ridge for the same reason. */
    private static final double[] RIDGE_X = {0.00, 0.13, 0.26, 0.44, 0.57, 0.71, 0.88, 1.00};
    private static final double[] RIDGE_H = {0.58, 0.18, 0.46, 0.04, 0.34, 0.12, 0.42, 0.58};
    /** One ridge period, logical px. Wide enough that the repeat is not obvious at 800 logical px of viewport. */
    private static final double RIDGE_PERIOD = 640;

    /** The two background ridges. far() scrolls at 30% of the camera and near() at 60%, which is what makes the
     *  sky read as depth rather than as a flat panel - the whole point of the class, and it had no consumer. */
    private final ParallaxLayer ridgeFar;
    private final ParallaxLayer ridgeNear;

    // The climber's current look (hair length grid + hair/pack palette).
    private final Sprites.Look look;
    /** Floating tutorial words, or null on a generated level. */
    private final AudioSystem audio;
    /** The last track this screen asked the UI player for, so it asks only when the engine changes its mind. */
    private String musicPlaying;
    private final Sound sound;
    private final java.util.List<Tutorial.Sign> signs;
    /**
     * True while playing the tutorial: hand-built levels, no autosave, and it ends at {@link Tutorial#LAST}.
     *
     * <p>This used to name a number, and the number was wrong - it had been wrong long enough that a check on the
     * README's tutorial count is what made me look at the one next to it. It names the constant now, so it cannot
     * drift, and {@code readme-counts.sh} fails any comment anywhere under src/ that states a tutorial end which
     * disagrees with {@code Tutorial.LAST}. (It does not quote the old number on purpose: the check greps for that
     * pattern, and an explanation of a fault trips the check for the fault.)
     */
    private final boolean tutorial;

    // View
    // Loop
    private AnimationTimer timer;
    private double accumulator = 0;
    private long lastPulse = -1;

    // Input state (held keys)
    private boolean left, right;
    // Held while the key is down. UP/W is jump on land and the swim-up stroke in
    // water; DOWN/S does nothing on land and dives. The water system reads one
    // of these every frame, so a stroke that is not held is not a stroke.
    private boolean up, down;

    // Facing direction (1=right, -1=left). Persists after keys release —
    // weapons fire where you're looking (playtest suggestion).
    private int facing = 1;

    /** Damage flash: the player goes red for a moment when hit. Most players
     *  watch the character, not the HUD, so the cue belongs on the body. */
    /** Below this much stun left, the climber is on one knee instead of flat. */
    private static final double KNEEL_UNTIL = 0.38;
    private double hitFlash = 0;
    private double lastHP = -1;

    // Weapons
    private final Hookshot hookshot;

    // Save/continue
    private static final String SAVE_FILE = System.getProperty("user.home")
            + "/.tropical-punch-autosave.txt";
    private double playTime = 0;

    public GameplayScreen(UiManager ui, int levelNum) {
        this(ui, levelNum, null, false);
    }

    /**
     * Tutorial mode. A SEPARATE mode rather than the first eleven levels of every
     * run - a returning player should not have to sit through the explanations
     * again (Kinger, Sept 29).
     */
    public GameplayScreen(UiManager ui, int levelNum, boolean tutorial) {
        this(ui, levelNum, null, tutorial);
    }

    /**
     * Full constructor. `resume` = a loaded GameState to restore into the
     * freshly generated level (autosave continuation), or null for a
     * fresh run.
     */
    public GameplayScreen(UiManager ui, int levelNum, SaveSystem.GameState resume) {
        this(ui, levelNum, resume, false);
    }

    public GameplayScreen(UiManager ui, int levelNum, SaveSystem.GameState resume, boolean tutorial) {
        super(ui);
        this.levelNum = levelNum;

        this.tutorial = tutorial;
        this.look = Sprites.buildLook(CharacterConfig.load());

        // Generate + build level (deterministic seed: same levelNum
        // always makes the same level — saves reference the level number)
        // Tutorial mode: hand-built levels, not generated - they have to
        // teach one thing each and be identical every run.
        if (tutorial) {
            map = Tutorial.map(levelNum);
            signs = Tutorial.signs(levelNum);
        } else {
            LevelGen gen = new LevelGen(LEVEL_W, LEVEL_H, 1000L + levelNum, levelNum);
            map = gen.generate();
            signs = null;
        }
        world = new World();
        map.buildWorld(world);
        combat = new Combat();
        // The engine posts cue names; Sound plays them. Kept here rather than inside World because the
        // engine must stay JavaFX-free - it runs headless in the gate and on machines with no sound device.
        audio = new AudioSystem();
        // The engine asks; the screen plays. `AudioSystem` records which track is current and knows nothing about
        // JavaFX, and `Sound` is the platformer's own audio and deliberately does not import aside.ui.* - so the
        // bridge is here, in a wiring file, exactly like the global volume two lines below.
        audio.playMusic(AudioSystem.Music.LEVEL);
        world.setAudio(audio);
        sound = Sound.load(ui.root());
        inventory = new PlayerInventory();

        // Player
        player = new Physics.Body(map.spawnX, map.spawnY, 24, 44);
        player.oneway = true;
        world.addBody(player);
        world.playerBody = player;

        // Enemies from map
        for (double[] e : map.enemies) {
            Enemy en = new Enemy(e[0], e[1], 24, 24, (int) e[2]);
            en.stationary = (en.kind == Enemy.KIND_SNAKE);   // coiled until it sees you
            world.addEnemy(en);
        }

        // Camera: room = full level (60*32 x 14*32). Viewport is the
        // PHYSICAL canvas size — worldToScreen returns physical pixels,
        // so all draw calls (sprites at 2x, tiles at 2x) land 1:1 on
        // screen with no resampling.
        // Viewport is PHYSICAL; room bounds are LOGICAL, matching the
        // bodies the camera follows. setScale is what worldToScreen was
        // always assumed to be doing.
        camera = new Camera(CANVAS_W, CANVAS_H);
        camera.setScale(SCALE);
        camera.setRoom(LEVEL_W * 32, LEVEL_H * 32);

        // The ridges behind the level. Vertical positions are in PHYSICAL px and stay put while the camera moves
        // vertically, which is what ParallaxLayer documents ("fixed for now, could be parallax too") - and it is
        // the behaviour that suits a horizon: the ground rises and falls in front of it, the hills do not.
        // THE OFFSET IS NOW A LIFT ABOVE THE TERRAIN, not a fraction of the canvas - see horizonScreenY for what
        // that fixed fraction did wrong. Far sits higher because it is further away.
        ridgeFar = ParallaxLayer.far(64);
        ridgeNear = ParallaxLayer.near(26);

        // Weapons
        hookshot = new Hookshot(player);

        // Restore from autosave if provided (level number must match —
        // the seed determines the level; a save from a different level
        // can't be applied to this one)
        if (resume != null && resume.levelNum == levelNum) {
            new SaveSystem().applyState(resume, player, combat, inventory, world);
            // -1 = checkpoint spawn marker: restore stats but spawn at
            // the level's start position, not a mid-level coordinate.
            if (resume.playerX < 0) {
                player.x = map.spawnX;
                player.y = map.spawnY;
                player.vx = 0;
                player.vy = 0;
            }
        }

        // Fit the fixed-size canvas into the (now resizable) window:
        // uniform scale, centered, letterboxed. Without this the
        // 1600x1200 canvas overflows a smaller window and clips the
        // HUD (playtest: "stuck unable to see certain stats").
    }



    /** Snapshot current state and write the autosave file. */
    private void autosave() {
        // A tutorial run must never overwrite the save of a real run.
        if (tutorial) return;
        SaveSystem.GameState state = SaveSystem.GameState.snapshot(
            player, combat, inventory, world, "level" + levelNum, playTime);
        state.levelNum = levelNum;
        try {
            new SaveSystem().save(state, SAVE_FILE);
        } catch (java.io.IOException ex) {
            System.err.println("autosave failed: " + ex.getMessage());
        }
    }

    @Override
    public void enter() {
        accumulator = 0;   // paused time is never simulated
    }

    @Override
    protected int canvasWidth() { return CANVAS_W; }


    @Override
    protected int canvasHeight() { return CANVAS_H; }


    @Override

    public void tick(double dt) {
        // The host ticks only the top screen, so a pushed pause overlay
        // freezes this one automatically -- no timer to stop and start.
        accumulator += Math.min(dt, 0.25);
        while (accumulator >= GameLoop.DT) {
            engineUpdate(GameLoop.DT);
            accumulator -= GameLoop.DT;
        }
        render();
    }


    @Override
    public void pause() { }

    @Override
    public void resume() {
        accumulator = 0;
    }

    @Override
    public void exit() {
        if (timer != null) timer.stop();
    }

    private void engineUpdate(double dt) {
        // Hookshot first: if it's pulling, it OWNS player velocity —
        // don't zero it with input, and let the physics step consume it.
        // (The old order — input zeroing, then world.update, then
        // hookshot.update — meant the pull velocity set at the end of
        // frame N was wiped before frame N+1's physics. The line drew
        // but the player never moved.)
        // Knocked flat by a bat: no input, no steering, no hookshot pull.
        // World owns the stun tick and keeps gravity running, so an
        // airborne climber still falls - they just cannot adjust the arc.
        boolean stunned = player.stunTimer > 0;
        // A piranha bit this frame. The engine says what happened; the screen decides what it costs, because
        // Combat (hearts, i-frames, the hurt sound) lives here and not in World.
        if (world.piranhaBit) combat.hurtPlayer(player, world.piranhaBitFromX);

        boolean pulling = !stunned && hookshot.update(dt, world);
        
        // Player input → velocity (skipped while hookshot pulls)
        if (stunned) {
            player.vx = 0;
            world.water.setVerticalInput(0);   // a climber flat on the ground does not stroke
        } else if (!pulling) {
            double desired = 0;
            if (left) desired -= RUN_SPEED;
            if (right) desired += RUN_SPEED;
            // Vertical: one of these every frame, and the water system applies it
            // only while the body is actually in the pool.
            world.water.setVerticalInput(up ? -1 : (down ? 1 : 0));
            // Horizontal. On land this is the assignment it always was. In water it
            // goes through the water system: swimming STEERS, so a body keeps its
            // momentum in and out of the pool instead of snapping to a new speed,
            // and wading is a multiplier on the run. Assigning vx directly still
            // works on land, but it gives water no horizontal effect at all - the
            // pool would slow nothing and nothing would carry through it.
            player.vx = world.water.swimming(player)
                ? world.water.steerVx(player, desired, dt)
                : desired * world.water.speedMultiplier(player);
            // Facing persists after keys release — weapons fire where
            // you're LOOKING, not where you're holding (playtest:
            // "bombs default right, shift that to where youre facing")
            if (left && !right) facing = -1;
            else if (right && !left) facing = 1;
        }

        // Engine step
        world.update(dt);
        // THE GLOBAL MUTE AND VOLUME KEYS REACH THIS GAME HERE, and they did not before. Those keys live on
        // aside.ui.Audio (M, and [ / ] - see UiManager), and this game's cues go through Sound, which knew
        // nothing about them: pressing M in the platformer said "SOUND OFF" and the cues kept playing. The
        // bridge is in this screen rather than inside Sound because Sound deliberately does NOT import
        // aside.ui.* - that is what lets it travel with the release sync - and this screen is a wiring file
        // that the sync skips. Read every frame rather than pushed on change, because two field reads are
        // cheaper than a listener and cannot get out of step.
        // AND THE MUSIC, the other half of the same bridge. Asked once per change rather than every frame, because
        // `Audio.music` starts a player and a per-frame call would restart the track continuously.
        String wantMusic = audio.currentMusicName();
        if (wantMusic != null && !wantMusic.equals(musicPlaying) && aside.ui.Audio.A != null) {
            musicPlaying = wantMusic;
            aside.ui.Audio.A.music(wantMusic);
        }
        if (aside.ui.Audio.A != null) {
            sound.setEnabled(aside.ui.Audio.A.enabled);
            // sfxVolume and not musicVolume, because every cue this backend plays IS an effect: it loads
            // AudioSystem.sfxNames() and nothing else, and the platformer has no music track of its own.
            sound.setVolume(aside.ui.Audio.A.sfxVolume);
        }
        sound.drain(audio);
        combat.update(dt);

        // Drowning: air ran out a beat ago. The ticks arrive pre-metered (~1/s) and
        // draining them is what spends them, so the damage stays on the engine's
        // clock rather than this loop's - a frame that runs long does not hurt more.
        for (int i = 0; i < world.water.drainDrownTicks(player); i++) {
            combat.hurtPlayer(player, player.x + 1);
        }

        // Damage cue. Watching the HP itself rather than hooking each damage
        // source means the flash fires for spikes, enemies, and anything
        // added later, without every source having to remember to ask.
        // A bomb is not selective - if the climber is inside its own blast it
        // takes the hit like anything else. World decides (it owns the blast
        // radius); the view applies the HP loss.
        if (world.playerBlastPending) {
            world.playerBlastPending = false;
            combat.hurtPlayer(player, player.x + 1);
        }
        if (lastHP >= 0 && combat.playerHP < lastHP) hitFlash = 0.35;
        lastHP = combat.playerHP;
        if (hitFlash > 0) hitFlash -= dt;

        // Enemy contact (stomp or hurt — Combat decides)
        for (Enemy e : new ArrayList<>(world.enemies)) {
            combat.processContact(player, e);
        }

        // AND THE BOSS, which is not an Enemy and so is not in that loop. Contact damage only while it is
        // dangerous - its telegraph - and Combat's own i-frames are what stop a charge from draining the player
        // in one pass. The same division as everywhere else here: the engine says what is happening, the screen
        // decides what it costs.
        if (world.boss != null && !world.boss.isDead() && world.boss.isDangerous()
                && player.aabb().overlaps(world.boss.aabb())) {
            combat.hurtPlayer(player, world.boss.aabb().x0);
        }

        // Pickups (tryCollect applies HP/keys + audio itself)
        for (Pickup p : new ArrayList<>(world.pickups)) {
            p.tryCollect(player, combat, inventory);
        }

        // Doors (try to unlock if player has key and touches door)
        for (Door d : world.doors) {
            if (d.tryUnlock(player, inventory, combat)) {
                world.unlockDoor(d);
            }
        }

        // Spikes
        for (Physics.AABB sp : world.spikes) {
            if (sp.overlaps(player.aabb())) {
                combat.hurtBySpike(player, player.x + 1);
            }
        }

        // Death or fell out of world: game over
        if (combat.playerDead() || player.y > map.heightCells() * 32 + 64) {
            ui.replace(new GameOverScreen(ui, levelNum, playTime));
            return;
        }

        // Exit reached: autosave (next level's checkpoint), then next
        // level (replace — no way back). The save stores the NEW level's
        // spawn state (fresh position, carried HP/keys) — Continue
        // resumes at the next level's start, which is the checkpoint.
        if (Math.abs(player.x - map.exitX) < 24 && Math.abs(player.y - map.exitY) < 40) {
            if (tutorial && Tutorial.endsTheTutorial(levelNum)) {
                // The tutorial is done - back to the menu, not on to level 9.
                ui.replace(new MainMenu(ui));
                return;
            }
            // AND THIS IS THE WAY HOME. The run has a length (LevelGen.FINAL_LEVEL) and its end is the dusk - the
            // sun the whole climb has been sinking towards. It offers to keep climbing, so the endless run is still
            // there; what changes is that the game now has somewhere to be going.
            if (!tutorial && levelNum >= LevelGen.FINAL_LEVEL) {
                ui.replace(new VictoryScreen(ui, levelNum, playTime, inventory.coins));
                return;
            }
            int nextLevel = levelNum + 1;
            SaveSystem.GameState checkpoint =
                    checkpointFor(nextLevel, combat, inventory, playTime);
            try {
                new SaveSystem().save(checkpoint, SAVE_FILE);
            } catch (java.io.IOException ex) {
                System.err.println("checkpoint save failed: " + ex.getMessage());
            }
            // Carry HP/keys/playTime into the next level. The checkpoint
            // (playerX=-1) restores stats but spawns at the level start.
            // Previously the next level got a FRESH Combat — full HP
            // every level (playtest: "lives still reset each level").
            ui.replace(new GameplayScreen(ui, nextLevel, checkpoint, tutorial));
            return;
        }

        playTime += dt;

        // Camera follows (with look-ahead)
        camera.update(dt, player.x, player.y, player.vx);
    }

    private void render() {
        // All rendering is in PHYSICAL pixels (canvas 1600x1200).
        // worldToScreen returns physical coords; sprites are pre-
        // scaled 2x and drawn at 2x logical size — 1:1, no resampling.
        final double S = SCALE;

        // Sky
        // Sunset: warm sky, a low sun, and the world as a black
        // silhouette against it. Ground and underground are both BLACK,
        // which is the whole point of the look -- and which is also why
        // chambers get their own slightly-lifted dark below (two blacks
        // in a row would make a carved room invisible).
        // How far into the evening this level is. 0 for every tutorial level, so the hand-built ones keep the
        // canonical sunset; otherwise it climbs to 1 by LEVELS_TO_DUSK.
        double dusk = tutorial ? 0.0
                : Math.max(0.0, Math.min(1.0, (levelNum - 1) / (double) LEVELS_TO_DUSK));

        gc.setFill(Color.web("#E8763A").interpolate(Color.web("#B4482C"), dusk));
        gc.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // The sun: backdrop, screen-anchored, no parallax. It is meant
        // to read as far away, so it should NOT track the camera.
        //
        // It SINKS with the run, and gets a little larger as it goes - which is what a sun near the horizon does,
        // and it is what makes the deepening sky read as time passing rather than as a tint. By the last levels its
        // centre is below the far ridge's baseline, so it is drawn behind the hills: the ridges are painted after
        // this, so the setting is done by the existing draw order rather than by clipping anything.
        {
            double sunR = CANVAS_H * (0.30 + 0.06 * dusk);
            double sunY = CANVAS_H * (0.38 + 0.14 * dusk);
            gc.setFill(Color.web("#F7C847"));
            gc.fillOval(CANVAS_W / 2.0 - sunR, sunY - sunR, sunR * 2, sunR * 2);
            gc.setFill(Color.web("#FBDD7E"));
            gc.fillOval(CANVAS_W / 2.0 - sunR * 0.62, sunY - sunR * 0.72,
                        sunR * 1.24, sunR * 1.24);
        }

        // TWO RIDGES, at 30% and 60% of the camera's speed. Between the sun and the terrain there was nothing but
        // flat orange - the sun is deliberately screen-anchored because it is meant to read as far away, and the
        // terrain is nearest, so with no layer in between the sky had no depth at all and the parallax machinery
        // sat unused ("ParallaxLayer: nothing names it at all", the wiring report).
        //
        // The colours are a ramp rather than two choices: sky #E8763A, then farther #9C4E2C, then nearer #4A2417,
        // then the terrain's own near-black #0D0A09. Each step darkens toward the player, which is what makes the
        // distance read - the ground is not "behind" the hills because it was drawn later, it is in front because
        // it is darker.
        double horizon = horizonScreenY();
        drawRidge(ridgeFar, Color.web("#9C4E2C"), 0.10, horizon);
        drawRidge(ridgeNear, Color.web("#4A2417"), 0.07, horizon);

        // Underground background: for each column, everything from the
        // topmost ground cell down is INSIDE the terrain, not open sky.
        // The terrain is solid rock with rooms carved out of it, so
        // without this a carved chamber renders as a sky-coloured box
        // sitting in the ground -- which is how the vault read as a
        // heart trapped in a hole rather than a room behind a wall.
        {
            // Chambers and open underground are a dim WARM tone, not
            // black: the terrain is black now, so a carved room in the
            // same black would be invisible. It reads as a lit alcove,
            // which is what it is once there is a snack sitting in it.
            gc.setFill(Color.web("#3B2419"));
            double cell = 32 * S;
            for (int c = 0; c < map.widthCells(); c++) {
                int top = -1;
                for (int r = 0; r < map.heightCells(); r++) {
                    char ch = map.cell(r, c);
                    // Ground-ish cells only. A door, key, heart or enemy
                    // floating above the surface is not terrain and must
                    // not define where the ground starts.
                    //
                    // Water counts, and it is the reason this pass is right:
                    // a flooded gap is a pit in the terrain, so the column's
                    // "inside the rock" begins at the pool's SURFACE, not at
                    // the floor under it. Left out, the pool was drawn over
                    // open sky and came out the colour of sky and water mixed
                    // -- a flat grey panel hanging in the air rather than a
                    // pool in a pit. The pit is the thing that makes it read
                    // as water.
                    if (ch == '#' || ch == 'C' || ch == '^' || ch == '/' || ch == '\\' || ch == '~') {
                        top = r;
                        break;
                    }
                }
                if (top < 0) continue;
                double sx = camera.worldToScreenX(c * 32);
                double sy = camera.worldToScreenY(top * 32);
                if (sx > CANVAS_W || sx + cell < 0) continue;
                gc.fillRect(sx, sy, cell, (map.heightCells() - top) * 32 * S);
            }
        }

        // Bedrock below the level. The terrain is solid all the way down
        // now, so the strip under the grid is the bottom of the world,
        // not more sky -- without this the level's own rock ends and the
        // area beneath it reads as a hole in the ground.
        {
            double bedrockY = camera.worldToScreenY(map.heightCells() * 32);
            if (bedrockY < CANVAS_H) {
                gc.setFill(Color.web("#0D0A09"));
                gc.fillRect(0, bedrockY, CANVAS_W, CANVAS_H - bedrockY);
            }
        }

        // Water: after every background (sky, sun, the underground fill, the
        // bedrock) and BEFORE the terrain, so a pool reads as water sitting in
        // a pit with the tiles as its walls rather than as a panel drawn over
        // them. Drawn before the sky it would be painted over, which is how
        // the release's first water frame came back sky-coloured where the
        // pool should have been.
        //
        // This is the piece the upstream port left behind. Water arrived here
        // with its generation, its physics, its breath meter and its probe -
        // the field existed, a body could swim in it, and not one pixel of it
        // had ever reached a screen. The one part of the system no unit test
        // can cover is the part that needs a screen.
        //
        // Widths are scaled by S. worldToScreen returns PHYSICAL pixels, so a
        // rect measured in world units is S times too small if it is not. The
        // release draws its water at 1x while its tiles are at 2x, which is
        // why its pools come out at half the width of the gap they sit in.
        //
        // Rects are merged water runs, not one per tile, so a wide pool is a
        // few fillRects instead of one per cell.
        Water waterField = world.water.water();
        if (waterField != null && !waterField.isEmpty()) {
            for (double[] r : waterField.rects) {
                double sx = camera.worldToScreenX(r[0]), sy = camera.worldToScreenY(r[1]);
                double w = (r[2] - r[0]) * S, h = (r[3] - r[1]) * S;
                if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) continue;
                gc.setFill(Color.web("#2E86C1", 0.55));
                gc.fillRect(sx, sy, w, h);
                gc.setFill(Color.web("#7FD4F0", 0.85));   // surface line
                gc.fillRect(sx, sy, w, 3);
            }
        }

        // Facing sprite: mirrored variant when facing left
        // (playtest: "able to look both directions")
        // Getting up is a SEQUENCE, not one frozen pose: flat on the ground,
        // then up onto one knee, then standing. A single pose held for the whole
        // stun read as a glitch rather than as being knocked over.
        boolean prone = player.stunTimer > 0;
        boolean kneeling = prone && player.stunTimer <= KNEEL_UNTIL;
        javafx.scene.image.Image playerSprite = !prone
                ? (facing < 0 ? look.left2x : look.right2x)
                : kneeling
                        ? (facing < 0 ? look.kneelLeft2x : look.kneelRight2x)
                        : (facing < 0 ? look.downLeft2x : look.downRight2x);

        // Solid tiles: grass-topped dirt (rect base + grass strip).
        //
        // Two things happen here that are not obvious:
        //
        // 1. Door AABBs live in world.tiles because PHYSICS needs them
        //    solid, but they are not terrain. Drawing them painted the
        //    door's invisible anti-jump wall as a floating dirt block
        //    hovering a tile above every door.
        //
        // 2. The grass test ("is anything solid directly above me") used
        //    to be a nested loop over world.tiles -- O(tiles^2). With the
        //    terrain now solid mass instead of a one-cell strip, that is
        //    roughly a million comparisons per frame on a Celeron. One
        //    hash set per frame makes it O(tiles).
        java.util.HashSet<Long> solidCells = new java.util.HashSet<>();
        for (Physics.AABB t : world.tiles) {
            if (!isDoorBox(t)) solidCells.add(cellKey(t.x0, t.y0));
        }
        for (Physics.AABB t : world.tiles) {
            if (isDoorBox(t)) continue;
            drawGroundTile(t, solidCells);
        }
        // Cracked tiles: crack overlay on top of ground
        for (Physics.AABB t : world.cracked) drawCrackedTile(t);
        // One-ways: wooden platform
        for (Physics.AABB t : world.oneways) {
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            double w = (t.x1 - t.x0) * S, h = (t.y1 - t.y0) * S;
            if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) continue;
            gc.setFill(Color.web("#8B5A2B"));
            gc.fillRect(sx, sy, w, h);
            gc.setFill(Color.web("#DAA520"));
            gc.fillRect(sx, sy, w, 8);
        }
        // Moving platforms. Drawn, because an engine feature the game builds and does not draw is the exact bug
        // this project has fixed three times (bats, particles, splash) - and a platform you cannot see is worse
        // than one that does not exist, since the player is carried by something they cannot locate.
        //
        // A DELIBERATELY DIFFERENT PALETTE from the one-way platform immediately above (#8B5A2B body, #DAA520
        // rim). Those are static and these move, and the two must not read as the same object before the player
        // has seen one move - which is the one moment the distinction has to land.
        for (MovingPlatform m : world.movers) {
            Physics.AABB a = m.aabb();
            double sx = camera.worldToScreenX(a.x0), sy = camera.worldToScreenY(a.y0);
            double w = (a.x1 - a.x0) * S, h = (a.y1 - a.y0) * S;
            if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) continue;
            gc.setFill(Color.web("#2E3A46"));
            gc.fillRect(sx, sy, w, h);
            gc.setFill(Color.web("#7FD4E8"));
            gc.fillRect(sx, sy, w, 8);
        }
        // The boss, if the level has one. Drawn from its box rather than as a sprite, for now: the body, a rim
        // that says whether it is dangerous, and a bright weak point while one is open. The weak point is the
        // whole fight - it is the only time damage counts - so it has to be visible, and a shape that changes
        // colour is more legible for that than a sprite frame would be.
        if (world.boss != null) {
            Physics.AABB bb = world.boss.aabb();
            double bx = camera.worldToScreenX(bb.x0), by = camera.worldToScreenY(bb.y0);
            double bw = (bb.x1 - bb.x0) * S, bh = (bb.y1 - bb.y0) * S;
            if (!(bx > CANVAS_W || by > CANVAS_H || bx + bw < 0 || by + bh < 0)) {
                // AND IT FADES WHILE IT DIES. The engine has always given the boss a one-second DYING window
                // before it is dead - a beat for the death to be seen - and this asked only `isDead()`, which is
                // false for that whole second, so the beat showed a boss in perfect health. Boss.deathFade() is
                // the question this could not previously ask.
                final double bossFade = world.boss.deathFade();
                gc.setGlobalAlpha(bossFade);
                // THE SPRITE FILLS ITS BOX EXACTLY. 64 grid pixels at SCALE lands one to one on the 128 physical
                // pixels the box is, so nothing here is resampled - which is the whole reason the grid is 64.
                gc.drawImage(Sprites.boss2x, bx, by, bw, bh);
                gc.setFill(world.boss.isDead() ? Color.web("#4A5560")
                        : world.boss.isDangerous() ? Color.web("#C4402A") : Color.web("#7FD4E8"));
                gc.fillRect(bx, by, bw, 12);
                if (world.boss.weakPointOpen()) {
                    Physics.AABB wp = world.boss.weakPointBox();
                    gc.setFill(Color.web("#FBDD7E"));
                    gc.fillOval(camera.worldToScreenX(wp.x0), camera.worldToScreenY(wp.y0),
                                (wp.x1 - wp.x0) * S, (wp.y1 - wp.y0) * S);
                }
                gc.setGlobalAlpha(1.0);
                // The same white wash the enemies get, over the whole box: a boss dying should be the loudest
                // thing on the screen, and for a second it is.
                double bossWash = Math.max(0.0, 1.0 - (1.0 - bossFade) * 2.0);
                if (bossWash > 0) {
                    gc.setFill(Color.web("#FFFFFF", bossWash * 0.6));
                    gc.fillRect(bx, by, bw, bh);
                }
            }
        }
        // Spikes: sprite (32 logical → 64 physical)
        for (Physics.AABB t : world.spikes) {
            double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
            if (sx > CANVAS_W || sx + 64 < 0) continue;
            gc.drawImage(Sprites.spike2x, sx, sy, 32 * S, 32 * S);
        }
        // Doors: visible sprite is 2 tiles (64px) sitting on the floor;
        // the collision wall above it is intentionally invisible.
        for (Door d : world.doors) {
            if (d.isSolid()) {
                double sx = camera.worldToScreenX(d.aabb().x0);
                double sy = camera.worldToScreenY(d.aabb().y1 - d.visibleH);
                if (sx > CANVAS_W || sx + 64 < 0) continue;
                gc.drawImage(Sprites.door2x, sx, sy, 32 * S, d.visibleH * S);
            }
        }

        // Pickups: sprites at 2x
        for (Pickup p : world.pickups) {
            if (!p.active) continue;
            double sx = camera.worldToScreenX(p.x - 8), sy = camera.worldToScreenY(p.y - 8);
            if (p.type == Pickup.Type.HEART) gc.drawImage(Sprites.snack2x, sx, sy, 16 * S, 16 * S);
            else if (p.type == Pickup.Type.JAR) gc.drawImage(Sprites.jar2x, sx, sy, 16 * S, 16 * S);
            else gc.drawImage(Sprites.key2x, sx, sy, 16 * S, 16 * S);
        }

        // Enemies: sprite at 2x
        for (Enemy e : world.enemies) {
            // A DEAD ENEMY IS DRAWN UNTIL ITS FADE RUNS OUT. This skipped dead ones outright, so every kill was a
            // disappearance on the frame it happened - and it left Enemy.deadTimer counting a fade nothing drew.
            // The counter was the whole mechanism; this is the half that was missing. See Enemy.deathFade().
            final double fade = e.deathFade();
            if (fade <= 0) continue;
            // Drawn at the SPRITE's own size and bottom-aligned on the body's
            // feet, rather than stretched into the collision box. A snake is
            // longer than it is tall, and forcing it into a square box would
            // squash it.
            boolean isSnake = e.kind == Enemy.KIND_SNAKE;
            // The snake RESTS COILED and only uncoils into its long pursuit
            // pose once it is actually coming for you.
            boolean coil = isSnake && !e.isChasing();
            javafx.scene.image.Image img;
            if (isSnake) {
                img = coil
                        ? (e.dir < 0 ? Sprites.snakeCoilL2x : Sprites.snakeCoil2x)
                        : (e.dir < 0 ? Sprites.snakeL2x : Sprites.snake2x);
            } else {
                img = e.dir < 0 ? Sprites.spiderL2x : Sprites.spider2x;
            }
            double w = (isSnake
                    ? (coil ? Sprite.SNAKE_COIL[0].length() : Sprite.SNAKE[0].length())
                    : Sprite.SPIDER[0].length()) * S;
            double h = (isSnake
                    ? (coil ? Sprite.SNAKE_COIL.length : Sprite.SNAKE.length)
                    : Sprite.SPIDER.length) * S;
            double sx = camera.worldToScreenX(e.body.x) - w / 2;
            double sy = camera.worldToScreenY(e.body.y + e.body.hh) - h;
            if (sx > CANVAS_W || sx + w < 0) continue;
            gc.setGlobalAlpha(fade);
            gc.drawImage(img, sx, sy, w, h);
            gc.setGlobalAlpha(1.0);
            // A white wash over the first third of the fade: the hit reads as a hit, and then the thing goes.
            double wash = Math.max(0.0, 1.0 - (1.0 - fade) * 3.0);
            if (wash > 0) {
                gc.setFill(Color.web("#FFFFFF", wash * 0.75));
                gc.fillRect(sx, sy, w, h);
            }
        }

        // Bats: flying pursuers, centred on the body.
        // Piranhas. Drawn rather than sprited - there is no piranha art yet, and a fish that exists in the
        // engine and is not drawn is the exact bug this project has fixed three times (bats, particles, splash).
        // A body, a tail, an eye and a tooth line reads as a fish at this size and is honest about being a stand-in.
        // Piranhas, drawn from a pixel grid like every other creature here.
        //
        // This was canvas ovals - a body, a tail, an eye and a tooth line - because there was no piranha art.
        // That read as a fish and was honest about being a stand-in, but it was the only creature in the game
        // not drawn as a sprite, and at a glance the difference showed. Sprite.PIRANHA is a hand-drawn grid in
        // the same style as the bat and the banana.
        for (Piranha f : world.piranhas) {
            double pw = Sprite.PIRANHA[0].length() * S, ph = Sprite.PIRANHA.length * S;
            double px = camera.worldToScreenX(f.body.x) - pw / 2;
            double py = camera.worldToScreenY(f.body.y) - ph / 2;
            if (px > CANVAS_W || px + pw < 0) continue;
            gc.drawImage(f.state == Piranha.State.BITE ? Sprites.piranha2x : Sprites.piranha, px, py, pw, ph);
        }


        for (Bat b : world.bats) {
            double bw = Sprite.BAT[0].length() * S, bh = Sprite.BAT.length * S;
            double sx = camera.worldToScreenX(b.body.x) - bw / 2;
            double sy = camera.worldToScreenY(b.body.y) - bh / 2;
            if (sx > CANVAS_W || sx + bw < 0) continue;
            gc.drawImage(Sprites.bat2x, sx, sy, bw, bh);
        }

        // Exit portal (20x30 logical -> 40x60 physical), bottom-aligned
        double exitW = Sprite.EXIT[0].length(), exitH = Sprite.EXIT.length;
        // Bottom-aligned on the platform under the 'E' cell: exitY is
        // the cell centre, so the floor the portal stands on is one
        // half-cell below it. Previously the anchor was hardcoded to the
        // old 16x24 flag and drifted the moment the sprite changed.
        double ex = camera.worldToScreenX(map.exitX - exitW / 2);
        double ey = camera.worldToScreenY(map.exitY + 16 - exitH);
        if (ex > -exitW * S && ex < CANVAS_W) {
            gc.drawImage(Sprites.exit2x, ex, ey, exitW * S, exitH * S);
        }

        // Player: radioactive banana sprite (facing-aware). Drawn at its
        // OWN aspect, centered on the body — the 14x22 grid stretched
        // into the 24x44 body box read as a pencil (playtest).
        {
            // Bottom-aligned on the body's feet, at whatever size the pose
            // actually is. The prone pose is LONG, not a squashed standing one,
            // so the two draws cannot share a single set of dimensions.
            double pw = (prone ? (kneeling ? look.kneelGridW : look.downGridW) : look.gridW) * S;
            double ph = (prone ? (kneeling ? look.kneelGridH : look.downGridH) : look.gridH) * S;
            double px = camera.worldToScreenX(player.x) - pw / 2;
            double py = camera.worldToScreenY(player.y + player.hh) - ph;
            gc.drawImage(playerSprite, px, py, pw, ph);
            // The flash itself: a translucent red wash over the body. JavaFX's
            // Canvas cannot tint an image, and one rectangle over the sprite's
            // own bounds reads as the character flashing red.
            if (hitFlash > 0) {
                gc.setFill(Color.web("#ff2b2b", 0.55));
                gc.fillRect(px, py, pw, ph);
            }
        }

        // Projectiles
        for (Projectile p : world.projectiles) {
            if (!p.active) continue;
            if (p.type == Projectile.Type.ARROW) {
                gc.drawImage(Sprites.arrow2x, camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 2), 12 * S, 4 * S);
            } else {
                // The blast radius, drawn while the fuse burns.
                //
                // Before this the only way to learn how far was safe was to be
                // hit by it, which is not something a player can be expected to
                // learn from. It brightens as the fuse runs down, so the circle
                // doubles as the countdown. The radius drawn is the DAMAGE
                // radius - the one that matters - and the bomb breaks tiles a
                // little further out than it hurts.
                double r = Projectile.BLAST_DAMAGE_RANGE * S;
                double bx = camera.worldToScreenX(p.x);
                double by = camera.worldToScreenY(p.y);
                double urgency = 1.0 - Math.max(0, Math.min(1, p.timer / Projectile.FUSE_TIME));
                if (bx + r > 0 && bx - r < CANVAS_W) {
                    gc.setStroke(Color.web("#ffffff", 0.16 + 0.32 * urgency));
                    gc.setLineWidth((1.5 + 1.5 * urgency) * S);
                    gc.strokeOval(bx - r, by - r, r * 2, r * 2);
                }
                // Bomb: red flash as fuse burns
                gc.drawImage(p.timer < 0.4 ? Sprites.bombFlash2x : Sprites.bomb2x,
                        camera.worldToScreenX(p.x - 6), camera.worldToScreenY(p.y - 6), 12 * S, 12 * S);
            }
        }

        // Hookshot line (player → hook tip while active). Pulling draws
        // to the anchor; retracting (missed shot) draws to the hook tip
        // — the old code always drew to anchorX/anchorY, which is stale
        // on a miss, so the line pointed at nothing off-screen.
        if (hookshot.isActive()) {
            double tipX = hookshot.isPulling() ? hookshot.anchorX : hookshot.hookX;
            double tipY = hookshot.isPulling() ? hookshot.anchorY : hookshot.hookY;
            gc.setStroke(Color.web("#C0C0C0"));
            gc.setLineWidth(3 * S);
            gc.strokeLine(camera.worldToScreenX(player.x), camera.worldToScreenY(player.y),
                          camera.worldToScreenX(tipX), camera.worldToScreenY(tipY));
            // Hook claw at the tip
            gc.setFill(Color.web("#C0C0C0"));
            double ax = camera.worldToScreenX(tipX), ay = camera.worldToScreenY(tipY);
            gc.fillOval(ax - 4 * S, ay - 4 * S, 8 * S, 8 * S);
        }

        // Tutorial words, floating where they belong in the world rather than
        // parked at the top of the screen.
        if (signs != null) {
            gc.setFont(Font.font("Arial", 22));
            for (Tutorial.Sign s : signs) {
                double sx = camera.worldToScreenX(s.x());
                double sy = camera.worldToScreenY(s.y());
                 java.util.List<String> lines = wrapSign(s.text());
                double widest = 0;
                for (String line : lines) widest = Math.max(widest, line.length());
                if (sx > CANVAS_W || sx + widest * 14 < 0) continue;
                // AND CLAMPED, because wrapping alone was not enough. Tutorial 9's PIRANHA sign is anchored so
                // far right that even a 34-character line ran off - "they come in groups. get out an". A sign
                // that is wrapped but still past the edge is a sign half-read.
                //
                // Clamping keeps it NEAR the thing it points at, which is the best a screen edge allows. 12px a
                // character is a shade over the 22pt Arial this is drawn in, so the last character lands inside.
                double realW = 0;
                for (String line : lines) {
                    javafx.scene.text.Text t = new javafx.scene.text.Text(line);
                    t.setFont(gc.getFont());
                    realW = Math.max(realW, t.getLayoutBounds().getWidth());
                }
                sx = Math.min(sx, CANVAS_W - realW - 8);
                for (int i = 0; i < lines.size(); i++) {
                    double ly = sy + i * 26;
                    gc.setFill(Color.web("#1a1a2e"));
                    gc.fillText(lines.get(i), sx + 2, ly + 2);
                    gc.setFill(Color.WHITE);
                    gc.fillText(lines.get(i), sx, ly);
                }
            }
        }

        // Particles, on top of everything. The pool has existed since the engine came
        // over and NOTHING has ever drawn it: the only emitter in the codebase is the
        // splash, so every droplet the engine has simulated since the port was
        // simulated invisibly. Drawn last, because a droplet belongs in front of the
        // wall it landed beside - behind it, it is a colour nobody sees.
        //
        // Squares, not circles: at 2-5 physical pixels a filled oval and a filled rect
        // are the same handful of pixels, and the rect is one call.
        for (Particle p : world.particles().getAll()) {
            if (!p.isActive()) continue;
            double px = camera.worldToScreenX(p.px()) - p.psize() * S / 2;
            double py = camera.worldToScreenY(p.py()) - p.psize() * S / 2;
            double size = p.psize() * S;
            if (px > CANVAS_W || py > CANVAS_H || px + size < 0 || py + size < 0) continue;
            gc.setFill(new Color(p.pr(), p.pg(), p.pb(), p.palpha()));
            gc.fillRect(px, py, size, size);
        }

        renderHUD();
    }

    /** Grid-cell key for the solid-cell set. Cell-aligned coords only. */
    private static long cellKey(double x, double y) {
        return ((long) (x / 32) << 20) ^ (long) (y / 32);
    }

    /** Is this AABB a door's collision box rather than terrain? */
    private boolean isDoorBox(Physics.AABB t) {
        for (Door d : world.doors) {
            if (d.aabb() == t) return true;
        }
        return false;
    }

    /**
     * One ridge, tiled across the viewport and scrolled at its layer's rate.
     *
     * <p>Deliberately a procedural polygon and not a sprite. The README's own note on this item was that the
     * layers "need something to draw, so this one comes with art or with a procedural shape standing in for it",
     * and a polygon has an advantage a sprite would not: it tiles, so the depth reads at any camera position
     * instead of running out at the end of a finite strip. The other four screens that draw this game's horizon
     * already do it this way.
     *
     * <p>Units, because they are not the same and the difference is easy to get wrong: the horizontal shift
     * comes from the layer in LOGICAL px (camera space) and is scaled to physical px here; the vertical position
     * is the layer's own offsetY, already in physical px.
     */
    /**
     * The screen y of the terrain's top edge, so the ridges can be hung above it.
     *
     * <p><b>WHY THIS IS COMPUTED AND NOT A CONSTANT.</b> These baselines were `CANVAS_H * 0.72` and `0.80` - 864
     * and 960 on a 1200-tall canvas - and the generated walk does not land at a fixed screen height: MEASURED, its
     * top edge sits at y 896 on one level and y 832 on another, a 64px swing that is more than the band between
     * those baselines. So on the levels whose ground sat higher, the terrain buried the parallax layers
     * completely: level 1 showed 18,713 far-ridge pixels and levels 20 and 40 showed about 700, which is a sliver
     * at the edge. The layers were drawn, and the feature was invisible - the same shape as a boss that exists
     * only in the tutorial.
     *
     * <p>The rule is the one the underground fill below already uses - a column's terrain starts at its topmost
     * ground-ish cell - and the MEDIAN across columns is taken rather than the highest or the lowest, because one
     * raised structure would move a maximum and one pit would move a minimum. The two passes agreeing matters:
     * the fill is what covers the ridges, so the horizon has to be measured the way the fill sees the world.
     */
    private double horizonScreenY() {
        double[] tops = new double[map.widthCells()];
        int n = 0;
        for (int c = 0; c < map.widthCells(); c++) {
            for (int r = 0; r < map.heightCells(); r++) {
                char ch = map.cell(r, c);
                if (ch == '#' || ch == 'C' || ch == '^' || ch == '/' || ch == '\\' || ch == '~') {
                    tops[n++] = camera.worldToScreenY(r * 32.0);
                    break;
                }
            }
        }
        if (n == 0) return CANVAS_H * 0.72;   // no terrain at all: fall back to where it used to be
        java.util.Arrays.sort(tops, 0, n);
        return tops[n / 2];
    }

    private void drawRidge(ParallaxLayer layer, Color colour, double amplitudeFraction, double horizon) {
        final double S = SCALE;
        double period = RIDGE_PERIOD * S;
        double shift = -layer.getOffsetX(camera) * S;
        double baseline = horizon - layer.getOffsetY();   // the layer's offsetY is now a lift above the horizon
        double amplitude = amplitudeFraction * CANVAS_H;

        // ONE POLYGON ACROSS THE WHOLE SPAN, not one per period. Tiling it and fixing the seam by overlapping
        // adjacent tiles by a pixel did NOT work - the shared edge is a slanted line meeting a vertical one, and
        // the seam survived at (174,87,47) against the ridge's (156,78,44), a 22% blend with the sky. Measured
        // twice: once to see it, once to find the overlap had not shifted it. A single polygon has no internal
        // edges to blend, which is the only version of this that cannot have the fault.
        int n = RIDGE_X.length;
        int firstTile = (int) Math.floor(-shift / period) - 1;
        int lastTile = (int) Math.ceil((CANVAS_W - shift) / period) + 1;
        int points = n + (lastTile - firstTile) * (n - 1) + 2;
        double[] px = new double[points];
        double[] py = new double[points];
        int k = 0;
        for (int t = firstTile; t <= lastTile; t++) {
            double x0 = shift + t * period;
            // Skip the tile's first point after the first tile: consecutive periods share it, and two identical
            // consecutive vertices are the kind of thing that draws a hairline nobody can explain later.
            for (int i = (t == firstTile ? 0 : 1); i < n; i++) {
                px[k] = x0 + RIDGE_X[i] * period;
                py[k] = baseline - RIDGE_H[i] * amplitude;
                k++;
            }
        }
        // Down the right edge, along the bottom, and the closing edge runs back up the left one.
        px[k] = px[k - 1]; py[k] = CANVAS_H; k++;
        px[k] = px[0];     py[k] = CANVAS_H; k++;
        gc.setFill(colour);
        gc.fillPolygon(px, py, k);
    }

    private void drawGroundTile(Physics.AABB t, java.util.HashSet<Long> solidCells) {
        final double S = SCALE;
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        double w = (t.x1 - t.x0) * S, h = (t.y1 - t.y0) * S;
        if (sx > CANVAS_W || sy > CANVAS_H || sx + w < 0 || sy + h < 0) return;
        // Black silhouette base.
        gc.setFill(Color.web("#0D0A09"));
        gc.fillRect(sx, sy, w, h);
        // Grass top ONLY if nothing solid directly above (a stacked
        // column of tiles shouldn't have grass bands mid-pillar —
        // visible in the first screenshot as stripes on every segment)
        // Warm rim light on the top edge only -- the sun is behind
        // everything, so exposed ground catches it. This replaces the
        // green grass strip.
        gc.setFill(Color.web("#C4622B"));
        boolean above = solidCells.contains(cellKey(t.x0, t.y0 - 32));
        if (!above) gc.fillRect(sx, sy, w, Math.min(16, h));
    }

    private void drawCrackedTile(Physics.AABB t) {
        final double S = SCALE;
        double sx = camera.worldToScreenX(t.x0), sy = camera.worldToScreenY(t.y0);
        if (sx > CANVAS_W || sx + 64 < 0) return;
        // Stone base first. A cracked tile used to be the dirt ground
        // tile with a cyan overlay on top, which is why bombable blocks
        // read as "the ground with cracks in it" rather than as a wall
        // block you are meant to blow up. Stone grey separates the two
        // at a glance.
        gc.setFill(Color.web("#5A5F63"));
        gc.fillRect(sx, sy, 32 * S, 32 * S);
        gc.setFill(Color.web("#9BA3A8"));
        gc.fillRect(sx, sy, 32 * S, 6);
        gc.drawImage(Sprites.crack2x, sx, sy, 32 * S, 32 * S);
    }

    private void renderHUD() {
        gc.setFont(Font.font("Arial", 20));
        // Reset transform for HUD: text should render at native res,
        // not scaled (scaled text is blurry and mispositioned).
        gc.setTransform(1, 0, 0, 1, 0, 0);
        // Hearts
        // One heart and a count ("<3 3x"), not a row of hearts. The count is
        // the number of lives, it starts at 3 every run, and the heart in a
        // jar raises the ceiling - a single number reads further and grows
        // without the HUD needing to be re-laid-out.
        gc.setFill(Color.RED);
        gc.fillText("<3", 40, 64);
        gc.setFill(Color.WHITE);
        gc.fillText(((int) combat.playerHP) + "x", 84, 64);
        if (hitFlash > 0) {
            gc.setFill(Color.web("#e94560"));
            gc.fillText("<3", 40, 64);
        }
        // Keys
        gc.setFill(Color.GOLD);
        gc.fillText("Key x" + inventory.keys, 40, 120);
        // COINS, which the game has been counting in secret. Pickup increments them, the save file persists them,
        // and the field's own comment says "currency (top-down prototype; shop later)" - and the HUD has never shown
        // them, so a player collects one, hears the cue, and has no way to know it counted. WHAT COINS ARE FOR is
        // still the open question that comment records; that they are LEGIBLE is not a question. Below the keys
        // line and clear of the air bar, which sits at y 138.
        gc.setFill(Color.web("#F7C847"));
        gc.fillText("Coins " + inventory.coins, 40, 176);
        // Air: drawn only while it is actually draining, so a climber who is not
        // swimming never sees a bar they do not need, and the bar's arrival is
        // itself the warning that they are under.
        double air = world.water.airFraction(player);
        if (air < 1.0) {
            gc.setFill(Color.web("#0B3D5C"));
            gc.fillRect(40, 138, 160, 16);
            gc.setFill(air > 0.35 ? Color.web("#7FD4F0") : Color.web("#E74C3C"));
            gc.fillRect(40, 138, 160 * air, 16);
        }
        // Level
        gc.setFill(Color.WHITE);
        gc.fillText(tutorial ? "Tutorial " + levelNum + " / " + Tutorial.LAST : "Level " + levelNum,
                     CANVAS_W - 260, 64);
    }

    @Override
    public void handleKey(KeyEvent e) {
        // While flat on the ground the climber cannot act at all - no jump,
        // no weapons, no hookshot. Movement keys are still tracked so a
        // release is never lost; movement itself is zeroed while stunned.
        if (player.stunTimer > 0) {
            switch (e.getCode()) {
                case SPACE, UP, W, X, F, G -> { e.consume(); return; }
                default -> { }
            }
        }
        switch (e.getCode()) {
            case LEFT, A -> { left = true; e.consume(); }
            case RIGHT, D -> { right = true; e.consume(); }
            case SPACE, UP, W -> {
                // Held as well as pressed: on land this is only a jump, but in
                // water the hold is the swim-up stroke and the press is the
                // impulse. jumpV answers all three cases - a breach hop at the
                // surface (which is how a pool is escaped), a weaker paddle
                // when fully under, and the plain jump when dry.
                up = true;
                if (player.grounded || world.water.swimming(player)) {
                    player.vy = world.water.jumpV(player, JUMP_V);
                }
                e.consume();
            }
            case DOWN, S -> { down = true; e.consume(); }
            case X -> {
                // Hookshot: X while pulling = cancel (player agency —
                // a pull must never hold the player hostage).
                if (hookshot.isPulling()) {
                    hookshot.release();
                } else {
                    double dx = facing;
                    double dy = 0;
                    if (e.isShiftDown()) dy = -1;  // Shift+X = fire upward
                    hookshot.fire(dx, dy, world);
                }
                e.consume();
            }
            case F -> {
                // Arrow: fast projectile in facing direction
                // From the middle of the body, not above it. player.y is the CENTRE of the physics box, so -10
                // fired the arrow from the chest upward and it sailed over anything level with you. Reported from
                // play: "the arrows shoot from above the player so if you're level with the enemy it'll fly over."
                world.addProjectile(Projectile.arrow(player.x, player.y, facing));
                e.consume();
            }
            case G -> {
                // Bomb: thrown arc in facing direction
                world.addProjectile(Projectile.bomb(player.x + facing * 10, player.y + player.hh - 6, facing));
                e.consume();
            }
            case ESCAPE -> {
                ui.push(new PauseScreen(ui, this));
                e.consume();
            }
        }
    }

    /** Key release — Main routes KEY_RELEASED here (held-key movement). */
    public void handleKeyReleased(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, A -> left = false;
            case RIGHT, D -> right = false;
            case SPACE, UP, W -> up = false;
            case DOWN, S -> down = false;
        }
    }
    /**
     * A SIGN THAT RUNS OFF THE EDGE IS WRAPPED, NOT MOVED.
     *
     * <p>Found by looking at tutorial 9: its two long signs are anchored near the right-hand end of the level, and
     * the text simply ran past the edge of the screen - "unlike a bat, this one takes a H" and "they come in
     * groups. get out an". The longest signs in the tutorial are 46 to 48 characters, which is around 550 pixels,
     * so any sign anchored past about x=730 lost its end.
     *
     * <p>A sign points at something in the world, so moving it would move it away from the thing it is talking
     * about. Making it taller keeps it where it belongs.
     *
     * <p>Thirty-four characters a line, broken on a space where there is one and mid-word only when a single word
     * is longer than the line - which none of these are.
     */
    static java.util.List<String> wrapSign(String text) {
        final int WIDTH = 34;
         java.util.List<String> out = new java.util.ArrayList<>();
        if (text.length() <= WIDTH) { out.add(text); return out; }
        String rest = text;
        while (rest.length() > WIDTH) {
            int cut = rest.lastIndexOf(' ', WIDTH);
            if (cut <= 0) cut = WIDTH;
            out.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    /**
     * The checkpoint that carries a run into the next level: HP, keys and time, spawning at the level start.
     *
     * <p>EXTRACTED SO A TEST CAN CALL IT, and that is the whole reason it is a method. It was six inline
     * statements, and the mutation sweep found the consequence: changing `playerHP = combat.playerHP` to a flat
     * `3` - the exact bug a player reported as "lives still reset each level" - left the gate green at 449
     * passed, 0 failed. A screen needs a UiManager and a display, so nothing headless could reach the line.
     *
     * <p>Same shape as Wake's manual reset: behaviour written inline in a screen is behaviour no test can call,
     * and behaviour belongs in a named function.
     */
    public static SaveSystem.GameState checkpointFor(int nextLevel, aside.games.fruitjump.engine.Combat combat,
                                              aside.games.fruitjump.engine.PlayerInventory inv, double playTime) {
        SaveSystem.GameState c = new SaveSystem.GameState();
        c.levelNum = nextLevel;
        c.playerX = -1; c.playerY = -1;      // -1 = spawn at the level start, but restore the stats
        c.playerHP = (int) combat.playerHP;
        c.maxHP = (int) combat.maxHP;
        c.keys = inv.keys;
        c.playTime = playTime;
        return c;
    }

}
