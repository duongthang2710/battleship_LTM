package com.battleship.frontend.game;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.shared.game.GamePhase;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.dummy.DummyKeyInput;
import com.jme3.input.dummy.DummyMouseInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** A short native check of the real screen controller and menu hit targets. */
public final class MainMenuFlowChecks extends SimpleApplication {
    private ScreenController screens;
    private BattleScene scene;
    private InjectedMouse mouse;
    private boolean exitCalled;
    private int frame;

    public static void main(String[] args) {
        MainMenuFlowChecks app = new MainMenuFlowChecks();
        AppSettings settings = new AppSettings(true);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setResolution(1440, 900);
        settings.setSamples(4);
        settings.setGammaCorrection(true);
        settings.setAudioRenderer(null);
        settings.setUseInput(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start(JmeContext.Type.OffscreenSurface);
    }

    @Override public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        stateManager.detach(stateManager.getState(FlyCamAppState.class));
        mouse = new InjectedMouse();
        DummyKeyInput keyboard = new DummyKeyInput();
        mouse.initialize();
        keyboard.initialize();
        inputManager = new InputManager(mouse, keyboard, null, null);
        scene = new BattleScene(assetManager);
        rootNode.attachChild(scene.root());
        OrbitCamera orbit = new OrbitCamera(cam);
        scene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, scene.keyLight());
        screens = new ScreenController(assetManager, guiNode, inputManager, cam, orbit,
                scene.beginLocalGameplay(), "Captain", () -> { exitCalled = true; stop(); });
        screens.resize(1440, 900);
    }

    @Override public void simpleUpdate(float delta) {
        scene.update(delta, cam);
        screens.update(delta, inputManager.getCursorPosition());
        frame++;
        if (frame == 20) {
            require(screens.mainMenu().visible(), "App starts at Main Menu");
            require(guiNode.getChild("Placement HUD").getLocalCullHint() == CullHint.Always,
                    "Placement HUD stays hidden on menu");
            var initialState = screens.game().state();
            for (MainMenu.Action action : MainMenu.Action.values()) {
                if (action == MainMenu.Action.LOCAL_GAME || action == MainMenu.Action.EXIT
                        || action == MainMenu.Action.PVP || action == MainMenu.Action.CREATE_ROOM
                        || action == MainMenu.Action.JOIN_ROOM || action == MainMenu.Action.PROFILE) continue;
                click(action);
                require(screens.mainMenu().visible() && screens.game().state() == initialState,
                        "Planned button is inert: " + action);
            }
            screens.resize(960, 600);
            for (MainMenu.Action action : MainMenu.Action.values())
                require(screens.mainMenu().actionAt(screens.mainMenu().buttonCenter(action)).orElseThrow() == action,
                        "Menu hit target scales: " + action);
            screens.resize(1440, 900);
            key(KeyInput.KEY_F, 'f');
            key(KeyInput.KEY_RETURN, '\n');
            require(screens.game().state() == initialState, "Gameplay shortcuts are inactive on menu");
        } else if (frame == 25) {
            click(MainMenu.Action.LOCAL_GAME);
            require(!screens.mainMenu().visible() && screens.game().state().phase() == GamePhase.PLACEMENT,
                    "PLAY opens existing placement");
            require(guiNode.getChild("Placement HUD").getLocalCullHint() == CullHint.Inherit,
                    "Placement HUD visible after PLAY");
            require(screens.game().state().player().board().ships().isEmpty(), "PLAY release does not place a ship");
            key(KeyInput.KEY_F, 'f');
            key(KeyInput.KEY_RETURN, '\n');
            require(screens.game().state().phase() == GamePhase.BATTLE, "Ready still enters battle");
            screens.requestMainMenu();
            screens.update(0, inputManager.getCursorPosition());
            require(screens.mainMenu().visible() && screens.game().state().player().board().ships().isEmpty(),
                    "Return to menu clears local match");
            click(MainMenu.Action.LOCAL_GAME);
            require(!screens.mainMenu().visible() && screens.game().state().phase() == GamePhase.PLACEMENT,
                    "PLAY again starts a fresh local match");
            screens.requestMainMenu();
            screens.update(0, inputManager.getCursorPosition());
        } else if (frame == 30) {
            click(MainMenu.Action.EXIT);
            require(exitCalled, "EXIT invokes application stop");
            System.out.println("PASS: Main Menu, all planned buttons inert, scaled hit targets, PLAY/Ready, return/replay, EXIT.");
        }
    }

    @Override public void simpleRender(RenderManager renderer) {
        if (frame != 20) return;
        try {
            var buffer = BufferUtils.createByteBuffer(1440 * 900 * 4);
            renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            BufferedImage image = new BufferedImage(1440, 900, BufferedImage.TYPE_4BYTE_ABGR);
            Screenshots.convertScreenShot(buffer, image);
            Path directory = Path.of(System.getProperty("battleship.previewDir", "docs"));
            Files.createDirectories(directory);
            ImageIO.write(image, "png", directory.resolve("main-menu.png").toFile());
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private void click(MainMenu.Action action) {
        Vector2f position = screens.mainMenu().buttonCenter(action);
        require(screens.mainMenu().actionAt(position).orElseThrow() == action, "Menu button: " + action);
        mouse.next = () -> {
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) position.x, (int) position.y));
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) position.x, (int) position.y));
        };
        inputManager.update(1 / 60f);
        screens.update(0, position);
    }

    private void key(int code, char value) {
        mouse.next = () -> {
            inputManager.onKeyEvent(new KeyInputEvent(code, value, true, false));
            inputManager.onKeyEvent(new KeyInputEvent(code, value, false, false));
        };
        inputManager.update(1 / 60f);
        screens.update(0, inputManager.getCursorPosition());
    }

    private static final class InjectedMouse extends DummyMouseInput {
        private Runnable next;
        @Override public void update() {
            if (next == null) return;
            Runnable events = next;
            next = null;
            events.run();
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    @Override public void handleError(String message, Throwable error) {
        System.err.println(message);
        if (error != null) error.printStackTrace();
        System.exit(1);
    }

    @Override public void destroy() {
        if (screens != null) screens.close();
        super.destroy();
    }
}
