package com.battleship.frontend.game;

import com.battleship.frontend.ai.AiSetupPanel;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.*;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.shared.ai.AiDifficulty;
import com.battleship.shared.game.*;
import com.jme3.app.*;
import com.jme3.input.*;
import com.jme3.input.dummy.*;
import com.jme3.math.*;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.system.*;
import com.jme3.texture.Image;
import com.jme3.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Native render + real raycast/input routing with an injected monotonic match clock. */
public final class AiGameplayRenderChecks extends SimpleApplication {
    private BattleScene scene;
    private GameController game;
    private GameOverOverlay result;
    private AiSetupPanel setup;
    private BoardCoordinates coordinates;
    private Node board;
    private final AtomicLong clock = new AtomicLong();
    private int frame;
    private String capture;
    private final Vector2f away = new Vector2f(-1,-1);

    public static void main(String[] args) {
        var app = new AiGameplayRenderChecks(); var settings = new AppSettings(true);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32); settings.setResolution(1440,900);
        settings.setSamples(4); settings.setGammaCorrection(true); settings.setAudioRenderer(null); settings.setUseInput(false);
        app.setSettings(settings); app.setShowSettings(false); app.start(JmeContext.Type.OffscreenSurface);
    }
    @Override public void simpleInitApp() {
        setDisplayFps(false); setDisplayStatView(false); stateManager.detach(stateManager.getState(FlyCamAppState.class));
        var mouse = new DummyMouseInput(); var keyboard = new DummyKeyInput(); mouse.initialize(); keyboard.initialize();
        inputManager = new InputManager(mouse,keyboard,null,null);
        scene = new BattleScene(assetManager); rootNode.attachChild(scene.root());
        var orbit = new OrbitCamera(cam); orbit.setPlayerSide(Side.WHITE);
        scene.installOceanReflections(viewPort); SceneEffects.install(assetManager,viewPort,scene.keyLight());
        board = scene.beginLocalGameplay(); coordinates = new BoardCoordinates(board);
        var materials = new Materials(assetManager);
        var hud = new BattleHud(assetManager,"Captain"); var controls = new PlacementHud(assetManager);
        var minimap = new OwnBoardMinimap(assetManager); result = new GameOverOverlay(assetManager);
        hud.resize(1440,900); controls.resize(1440,900); minimap.resize(1440,900); result.resize(1440,900);
        guiNode.attachChild(hud.root()); guiNode.attachChild(controls.root()); guiNode.attachChild(minimap.root()); guiNode.attachChild(result.root());
        game = new GameController(new GameEngine("Captain",new Random(31)),new BoardPicker(cam,inputManager,board),
                new FleetRenderer(board,materials),new PlacementFeedback(board,materials),controls,hud,minimap,
                new TargetingRenderer(board,materials),new AttackEffectManager(board,materials),result);
        setup = new AiSetupPanel(assetManager); guiNode.attachChild(setup.root()); setup.select(AiDifficulty.HARD); setup.show();
        hud.root().setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        controls.root().setCullHint(com.jme3.scene.Spatial.CullHint.Always);
    }
    @Override public void simpleUpdate(float delta) {
        scene.update(delta,cam);
        if (++frame == 20) capture="setup-hard";
        if (frame == 25) {
            setup.hide();
            guiNode.getChild("Battle HUD").setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
            guiNode.getChild("Placement HUD").setCullHint(com.jme3.scene.Spatial.CullHint.Inherit);
            game.useAiMode(AiDifficulty.HARD,"Captain",clock::get); game.perform(PlacementHud.Action.RANDOM);
            require(game.state().player().board().ships().size()==5,"Existing random placement reused"); capture="placement-hard";
        }
        if (frame == 30) {
            game.perform(PlacementHud.Action.READY); game.update(0,away);
            require(game.inputBusy(),"Countdown blocks gameplay"); capture="countdown-hard";
        }
        if (frame == 35) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(3)); game.update(1,away); game.update(1,away);
            require(!game.inputBusy(),"Countdown releases gameplay");
            require(game.state().enemy().board().ships().isEmpty(),"No hidden models in presentation state");
            Vector2f target = screen(new Coordinate(4,4)); game.primaryClick(target);
            require(game.state().enemy().board().attackedCells().size()==1,"Raycast fires human shot");
            game.update(4,away); // Resolve visuals without advancing the match clock.
            require(!game.attackAnimating(),"First shot resolves before bot delay");
            game.perform(PlacementHud.Action.TOGGLE_BOARD); // Deliberately view enemy waters during bot's turn.
            game.primaryClick(screen(new Coordinate(4,5)));
            require(game.state().player().board().attackedCells().isEmpty() && game.state().enemy().board().attackedCells().size()==1,
                    "Camera toggle cannot impersonate bot or bypass turn ownership");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(2)); game.update(0,away);
            require(game.attackAnimating() && game.state().player().board().attackedCells().size()==1,"Bot fires through normal effects");
            game.update(1.1f,away); capture="bot-shot-hard";
        }
        if (frame == 40) {
            game.update(4,away);
            require(game.state().currentPlayerId().equals(game.state().player().id()),"Bot returns control to human");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(61)); game.update(0,away);
            require(result.visible() && game.state().winnerId().equals(game.state().enemy().id()),"Timeout displays defeat"); capture="timeout-hard";
        }
        if (frame == 45) {
            Vector2f restart = result.buttonCenter(true);
            require(result.restartAt(restart),"Play Again hit target enabled"); game.primaryClick(restart);
            require(game.aiMode() && game.state().phase()==GamePhase.PLACEMENT && game.state().enemy().name().equals("AI Hard"),"Play Again retains mode/difficulty");
            require(!result.visible() && game.state().player().board().attackedCells().isEmpty(),"Play Again clears result/knowledge");
            game.useLocalMode(); clock.addAndGet(TimeUnit.SECONDS.toNanos(70)); game.update(0,away);
            require(!game.aiMode() && game.state().phase()==GamePhase.PLACEMENT,"Leaving cancels bot lifecycle");
            System.out.println("PASS: native AI setup, placement, countdown, raycast attack, bot turn, camera ownership, effects, timeout and Play Again."); stop();
        }
    }
    private Vector2f screen(Coordinate cell) {
        rootNode.updateGeometricState(); Vector3f point = cam.getScreenCoordinates(coordinates.worldCenter(cell,.185f));
        return new Vector2f(point.x,point.y);
    }
    @Override public void simpleRender(RenderManager renderer) {
        if(capture==null) return;
        try {
            var buffer=BufferUtils.createByteBuffer(1440*900*4);
            renderer.getRenderer().readFrameBufferWithFormat(null,buffer,Image.Format.BGRA8);
            var image=new BufferedImage(1440,900,BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer,image);
            Path directory=Path.of("build/ai-review");Files.createDirectories(directory);
            ImageIO.write(image,"png",directory.resolve(capture+".png").toFile());capture=null;
        } catch(Exception error) { throw new RuntimeException(error); }
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    @Override public void handleError(String message,Throwable error) {System.err.println(message);if(error!=null)error.printStackTrace();System.exit(1);}
}
