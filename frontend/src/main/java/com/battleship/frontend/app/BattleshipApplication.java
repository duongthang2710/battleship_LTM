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
        viewPort.setBackgroundColor(Materials.rgb(0x07101C));
        battleScene = new BattleScene(assetManager);
        rootNode.attachChild(battleScene.root());
        orbit = new OrbitCamera(cam);
        battleScene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, battleScene.keyLight());
        String player = System.getProperty("battleship.player", "Captain");
        var board = battleScene.beginLocalGameplay();
        screens = new ScreenController(assetManager, guiNode, inputManager, cam, orbit, board, player, this::stop, new DisplaySettings(this));
        resizeHud();
        LOG.info("Account screen ready. Sign in to enter the PvP menu.");
    }

    @Override
    public void simpleUpdate(float delta) {
        battleScene.update(delta, cam);
        screens.update(delta, inputManager.getCursorPosition());
        if (cam.getWidth() > 0 && cam.getHeight() > 0
                && (hudWidth != cam.getWidth() || hudHeight != cam.getHeight())) {
            resizeHud();
            orbit.update();
        }
    }

    private void resizeHud() {
        hudWidth = cam.getWidth();
        hudHeight = cam.getHeight();
        screens.resize(hudWidth, hudHeight);
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
        if (screens != null) screens.close();
        super.destroy();
    }
}
