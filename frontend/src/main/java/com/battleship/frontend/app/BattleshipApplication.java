package com.battleship.frontend.app;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.settings.SettingsStore;
import com.battleship.frontend.settings.DisplaySettings;
import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import java.util.logging.Logger;

public final class BattleshipApplication extends SimpleApplication {
    private static final Logger LOG = Logger.getLogger(BattleshipApplication.class.getName());
    private BattleScene battleScene;
    private OrbitCamera orbit;
    private ScreenController screens;
    private com.battleship.frontend.input.DisplayModeInput displayModeInput;
    private com.battleship.frontend.ui.FullscreenCursor fullscreenCursor;
    private int hudWidth;
    private int hudHeight;

    public static void main(String[] args) {
        BattleshipApplication application = new BattleshipApplication();
        application.setPauseOnLostFocus(false);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Battleship | Naval Strategy");
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        var saved = new SettingsStore().load();
        settings.setResolution(saved.preferences().width(), saved.preferences().height());
        settings.setMinResolution(960, 600);
        settings.setResizable(true);
        settings.setFullscreen(false);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(true);
        settings.setAudioRenderer(null);
        application.setSettings(settings);
        application.setShowSettings(false);
        application.start();
    }

    @Override
    public void simpleInitApp() {
        setPauseOnLostFocus(false);
        setDisplayFps(false);
        setDisplayStatView(false);
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        if (inputManager.hasMapping(INPUT_MAPPING_EXIT)) inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        displayModeInput = new com.battleship.frontend.input.DisplayModeInput(inputManager);
        viewPort.setBackgroundColor(Materials.rgb(0x07101C));
        battleScene = new BattleScene(assetManager);
        rootNode.attachChild(battleScene.root());
        orbit = new OrbitCamera(cam);
        battleScene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, battleScene.keyLight());
        String player = System.getProperty("battleship.player", "Captain");
        var board = battleScene.beginLocalGameplay();
        screens = new ScreenController(assetManager, guiNode, inputManager, cam, orbit, board, player, this::stop, new DisplaySettings(this));
        fullscreenCursor = new com.battleship.frontend.ui.FullscreenCursor(assetManager, guiNode, inputManager, context, guiViewPort.getCamera());
        resizeHud();
        LOG.info("Account screen ready. Sign in to enter the PvP menu.");
    }

    @Override
    public void simpleUpdate(float delta) {
        var guiCamera = guiViewPort.getCamera();
        if (guiCamera.getWidth() > 0 && guiCamera.getHeight() > 0
                && (hudWidth != guiCamera.getWidth() || hudHeight != guiCamera.getHeight())) resizeHud();
        battleScene.update(delta, cam);
        screens.update(delta, inputManager.getCursorPosition());
        if (displayModeInput.pollToggle()) screens.toggleFullscreen();
        fullscreenCursor.update();
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        // GLFW input already uses framebuffer pixels. Resize hit areas in the same callback,
        // before input dispatch, rather than waiting until the end of simpleUpdate.
        if (screens != null && width > 0 && height > 0) {
            resizeHud();
            orbit.update();
        }
    }

    private void resizeHud() {
        var guiCamera = guiViewPort.getCamera();
        hudWidth = guiCamera.getWidth();
        hudHeight = guiCamera.getHeight();
        screens.resize(hudWidth, hudHeight);
    }

    @Override
    public void gainFocus() {
        super.gainFocus();
        enqueue(() -> {
            if (inputManager == null || context == null || !context.isCreated()) return;
            inputManager.reset();
            inputManager.setCursorVisible(true);
            // InputManager caches visibility; reapply to native input even when its flag is already true.
            context.getMouseInput().setCursorVisible(true);
            inputManager.setMouseCursor(null);
            if (fullscreenCursor != null) fullscreenCursor.refresh();
        });
    }

    @Override
    public void loseFocus() {
        super.loseFocus();
        if (displayModeInput != null) displayModeInput.reset();
    }

    @Override
    public void handleError(String message, Throwable error) {
        if (battleScene == null && message != null && (message.startsWith("WGL:")
                || message.contains("Failed to create display") || message.contains("Unable to initialize GLFW"))) {
            LOG.log(java.util.logging.Level.SEVERE,
                    "Graphics startup failed. Battleship requires OpenGL 3.2 with a working graphics driver. "
                    + "Integrated GPUs are supported; a discrete GPU is not required. "
                    + "Check the active display driver if Windows uses Microsoft Basic Display Driver. "
                    + "On macOS, use the generated launcher or :frontend:run.",
                    error);
            System.exit(2); // The Windows launcher can retry with the integrated GPU preference.
        }
        super.handleError(message, error);
    }

    @Override
    public void destroy() {
        if (fullscreenCursor != null) fullscreenCursor.close();
        if (displayModeInput != null) displayModeInput.close();
        if (screens != null) screens.close();
        super.destroy();
    }
}
