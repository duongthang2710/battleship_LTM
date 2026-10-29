package com.battleship.frontend.game;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.*;
import com.battleship.frontend.graphics.*;
import com.battleship.frontend.graphics.ship.*;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.shared.game.*;
import com.jme3.app.*;
import com.jme3.font.*;
import com.jme3.input.*;
import com.jme3.input.dummy.*;
import com.jme3.input.event.*;
import com.jme3.light.*;
import com.jme3.math.*;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.system.*;
import com.jme3.texture.Image;
import com.jme3.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class DesignerRenderChecks extends SimpleApplication {
    private DesignerRenderChecks() { super(new StatsAppState()); }
    private String mode;
    private BattleScene scene;
    private GameController controller;
    private GameEngine engine;
    private BoardCoordinates coordinates;
    private Node battleBoard;
    private InjectedMouse mouse;
    private GameInput controls;
    private int frame;
    private static final int WIDTH=1600, HEIGHT=900;
    public static void main(String[] args) throws Exception {
        if(args.length==0) {
            for(String mode:List.of("placement","radiant","abyssal")) {
                var command=new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
                command.add("--enable-native-access=ALL-UNNAMED");
                if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) command.add("-XstartOnFirstThread");
                command.addAll(List.of("-cp",System.getProperty("java.class.path"),DesignerRenderChecks.class.getName(),mode));
                Process child=new ProcessBuilder(command).inheritIO().start();
                try { require(child.waitFor(120,TimeUnit.SECONDS)&&child.exitValue()==0,"Designer render child failed: "+mode); }
                finally { if(child.isAlive()) child.destroyForcibly(); }
            }
            return;
        }
        var app=new DesignerRenderChecks(); app.mode=args[0]; var settings=new AppSettings(true);
        settings.setResolution(WIDTH,HEIGHT); settings.setRenderer(AppSettings.LWJGL_OPENGL32); settings.setSamples(4);
        settings.setGammaCorrection(true); settings.setAudioRenderer(null); settings.setUseInput(false); settings.setFrameRate(30);
        app.setSettings(settings); app.setShowSettings(false); app.start(JmeContext.Type.OffscreenSurface);
    }
    @Override public void simpleInitApp() {
        setDisplayFps(false); setDisplayStatView(false); setPauseOnLostFocus(false);
        if(mode.equals("placement")) placement(); else gallery();
    }
    private void placement() {
        mouse=new InjectedMouse(); var keys=new DummyKeyInput(); mouse.initialize(); keys.initialize(); inputManager=new InputManager(mouse,keys,null,null);
        scene=new BattleScene(assetManager); rootNode.attachChild(scene.root()); var board=scene.beginLocalGameplay(); battleBoard=board; coordinates=new BoardCoordinates(board);
        var orbit=new OrbitCamera(cam); scene.installOceanReflections(viewPort); SceneEffects.install(assetManager,viewPort,scene.keyLight());
        cam.setLocation(new Vector3f(0,27,.01f)); cam.lookAt(Vector3f.ZERO,Vector3f.UNIT_Z.negate()); cam.setFrustumPerspective(45, (float)WIDTH/HEIGHT,.1f,250);
        engine=new GameEngine("Designer render"); engine.setMode(GameMode.DESIGNER);
        var materials=new Materials(assetManager); var hud=new BattleHud(assetManager,"Designer render"); var panel=new PlacementHud(assetManager);
        var minimap=new OwnBoardMinimap(assetManager); var over=new GameOverOverlay(assetManager);
        guiNode.attachChild(hud.root()); guiNode.attachChild(panel.root()); guiNode.attachChild(minimap.root()); guiNode.attachChild(over.root());
        hud.resize(WIDTH,HEIGHT); panel.resize(WIDTH,HEIGHT); minimap.resize(WIDTH,HEIGHT); over.resize(WIDTH,HEIGHT);
        controller=new GameController(engine,new BoardPicker(cam,inputManager,board),new FleetRenderer(board,materials),new PlacementFeedback(board,materials),panel,hud,minimap,new TargetingRenderer(board,materials),new AttackEffectManager(board,materials),over);
        controls=new GameInput(inputManager,controller,orbit); controller.perform(PlacementHud.Action.TOGGLE_DRAW);
    }
    private void gallery() {
        viewPort.setBackgroundColor(Materials.rgb(0x081222));
        rootNode.addLight(new AmbientLight(new ColorRGBA(.55f,.61f,.74f,1)));
        var sun=new DirectionalLight(new Vector3f(-.4f,-1,-.5f).normalizeLocal(),new ColorRGBA(1.5f,1.35f,1.10f,1)); rootNode.addLight(sun);
        cam.setLocation(new Vector3f(0,105,75)); cam.lookAt(Vector3f.ZERO,Vector3f.UNIT_Y); cam.setParallelProjection(true);
        cam.setFrustum(.1f,250,-53,53,30,-30);
        var materials=new Materials(assetManager); var factory=new DesignedShipBuilder(materials);
        var side=mode.equals("radiant")?ShipFactory.Fleet.RADIANT:ShipFactory.Fleet.ABYSSAL;
        var labels=com.battleship.frontend.ui.UiTypography.body(assetManager);
        List<Object> catalog=new ArrayList<>(); int index=0;
        for(ShipType type:ShipType.values()) {
            int variant=0;
            for(var shape:ShipShapes.all(type.length())) {
                variant++; Node model=factory.create(type,shape,side); checkModel(model,shape);
                // Every catalog footprint can also be entered using separate brush strokes.
                GameEngine editorEngine=new GameEngine("Catalog"); editorEngine.setMode(GameMode.DESIGNER); editorEngine.selectShip(type);
                var editor=new ShipDesignEditor(editorEngine);
                for(Coordinate cell:shape) { editor.beginStroke(); editor.paint(cell,false); editor.endStroke(); }
                require(editor.commit().success(),"Every catalog shape is drawable");
                float x=(index%12-5.5f)*8, z=(index/12-3.5f)*8;
                var bounds=(com.jme3.bounding.BoundingBox)model.getWorldBound(); model.setLocalTranslation(x-bounds.getCenter().x,0,z-bounds.getCenter().z);
                rootNode.attachChild(model);
                var plate=new Geometry("Gallery presentation plinth",new Box(3.4f,.035f,3.35f)); plate.setLocalTranslation(x,-.07f,z); plate.setMaterial(materials.metal(Materials.rgb(0x10253B),60)); rootNode.attachChild(plate);
                Vector3f screen=cam.getScreenCoordinates(new Vector3f(x,0,z+3.0f));
                var label=new BitmapText(labels); label.setText(type.name()+" "+String.format(Locale.ROOT,"%02d",variant)); label.setSize(12); label.setColor(Materials.rgb(0xDEB663));
                label.setLocalTranslation(screen.x-label.getLineWidth()/2,screen.y,10); guiNode.attachChild(label);
                catalog.add(Map.of("type",type.name(),"variant",variant,"cells",shape)); index++;
            }
        }
        require(index==96,"All 96 type/footprint models built for "+side);
        var title=new BitmapText(labels); title.setText(side+" / ALL 96 SHIP FOOTPRINTS"); title.setSize(24); title.setColor(Materials.rgb(0xF4EAD1)); title.setLocalTranslation(40,HEIGHT-28,10); guiNode.attachChild(title);
        try { Files.createDirectories(Path.of("build/designer-review")); Files.writeString(Path.of("build/designer-review/catalog.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(catalog)); }
        catch(Exception error) { throw new RuntimeException(error); }
        System.out.println("PASS: "+side+" 96 models, finite meshes, footprint containment and all brush drafts.");
    }
    private static void checkModel(Node model,List<Coordinate> shape) {
        model.updateGeometricState(); require(model.getQuantity()>0,"Batched model is nonempty");
        model.depthFirstTraversal(spatial -> {
            if(!(spatial instanceof Geometry geometry)) return;
            var positions=geometry.getMesh().getFloatBuffer(VertexBuffer.Type.Position);
            for(int i=0;i<positions.limit();i+=3) {
                var v=geometry.localToWorld(new Vector3f(positions.get(i),positions.get(i+1),positions.get(i+2)),null);
                require(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z),"Finite mesh vertices");
                boolean inside=shape.stream().anyMatch(c -> Math.abs(v.x-c.row())<=.525f && Math.abs(v.z+c.column())<=.525f);
                require(inside,"Model must not occupy an empty grid cell: "+v+" shape="+shape);
            }
        });
    }
    @Override public void simpleUpdate(float delta) {
        if(scene!=null) { scene.update(delta,cam); controller.update(delta,inputManager.getCursorPosition()); }
        if(++frame==12 && mode.equals("placement")) {
            Vector3f cameraBefore=cam.getLocation().clone();
            stroke(false,List.of(new Coordinate(2,2),new Coordinate(3,2)));
            require(controller.designer().cells().size()==2 && engine.state().player().board().ships().isEmpty(),"Partial stroke is a draft");
            stroke(true,List.of(new Coordinate(3,2))); require(controller.designer().cells().size()==1,"Right click erases");
            stroke(false,List.of(new Coordinate(3,2),new Coordinate(4,2),new Coordinate(4,3),new Coordinate(4,4)));
            require(engine.state().player().board().ship(ShipType.CARRIER).occupiedCells().size()==5,"Nonlinear carrier drawn through real raycast/input");
            require(cam.getLocation().equals(cameraBefore),"Drawing does not orbit camera");
            controller.perform(PlacementHud.Action.TOGGLE_DRAW);
            var empty=cam.getScreenCoordinates(coordinates.worldCenter(new Coordinate(5,5),.22f)); controller.update(new Vector2f(empty.x,empty.y));
            var preview=(Node)battleBoard.getChild("Ship placement preview");
            require(preview.getQuantity()==0,"Move mode does not preview a straight unplaced custom ship");
            controller.perform(PlacementHud.Action.TOGGLE_DRAW);
            for(var shape:List.of(List.of(new Coordinate(2,6),new Coordinate(2,7),new Coordinate(2,8),new Coordinate(3,7)),
                    List.of(new Coordinate(6,2),new Coordinate(7,2),new Coordinate(7,3)),
                    List.of(new Coordinate(6,6),new Coordinate(6,7),new Coordinate(6,8)),
                    List.of(new Coordinate(8,4),new Coordinate(9,4)))) for(Coordinate cell:shape) stroke(false,List.of(cell));
            require(engine.state().canReady() && engine.state().player().board().ships().stream().allMatch(Ship::custom),"Five custom ships ready");
            controller.perform(PlacementHud.Action.TOGGLE_DRAW);
            Ship carrier=engine.state().player().board().ship(ShipType.CARRIER);
            var origin=cam.getScreenCoordinates(coordinates.worldCenter(carrier.occupiedCells().getFirst(),.65f));
            var destination=cam.getScreenCoordinates(coordinates.worldCenter(carrier.occupiedCells().getFirst().offset(1,0),.65f));
            mouse.next=() -> {
                var press=new MouseButtonEvent(MouseInput.BUTTON_LEFT,true,Math.round(origin.x),Math.round(origin.y)); press.setTime(1);
                var move=new MouseMotionEvent(Math.round(destination.x),Math.round(destination.y),Math.round(destination.x-origin.x),Math.round(destination.y-origin.y),0,0); move.setTime(2);
                var release=new MouseButtonEvent(MouseInput.BUTTON_LEFT,false,Math.round(destination.x),Math.round(destination.y)); release.setTime(3);
                inputManager.onMouseButtonEvent(press); inputManager.onMouseMotionEvent(move); inputManager.onMouseButtonEvent(release);
            };
            inputManager.update(1/60f); rootNode.updateGeometricState();
            Ship moved=engine.state().player().board().ship(ShipType.CARRIER);
            require(moved.position().equals(carrier.position().offset(1,0)) && moved.shape().equals(carrier.shape()),"Real model raycast/drag snaps custom ship without changing shape");
            controller.perform(PlacementHud.Action.ROTATE);
            require(engine.state().player().board().ship(ShipType.CARRIER).shape().equals(ShipShapes.rotate(moved.shape())),"Placed custom ship rotates through controller");
            require(cam.getLocation().equals(cameraBefore),"Moving custom ships does not orbit camera");
            System.out.println("PASS: real 3D raycast/drawing input, erase, exclusive camera capture, full designed fleet and custom model drag/rotation.");
            controller.restartLocalMatch(); engine.setRules(new GameRules(GameMode.DESIGNER, 3, TurnRule.HIT_CONTINUES));
            stroke(false, List.of(new Coordinate(2, 2)));
            controller.perform(PlacementHud.Action.SELECT_DESTROYER);
            require(controller.designer().cells().size() == 1 && engine.state().placement().selectedType() == ShipType.CARRIER,
                    "Disabled ship hotkey cannot erase a valid draft in a 3-ship room");
            stroke(false, List.of(new Coordinate(3, 2), new Coordinate(4, 2), new Coordinate(4, 3), new Coordinate(4, 4)));
            for (var shape : List.of(List.of(new Coordinate(2, 6), new Coordinate(2, 7), new Coordinate(2, 8), new Coordinate(3, 7)),
                    List.of(new Coordinate(6, 2), new Coordinate(7, 2), new Coordinate(7, 3)))) for (Coordinate cell : shape) stroke(false, List.of(cell));
            require(engine.canReady() && engine.state().player().board().ships().size() == 3, "3D drawing stops at the configured three ships and enables Ready");
            var labels = new ArrayList<String>();
            guiNode.depthFirstTraversal(s -> { if (s instanceof BitmapText text) labels.add(text.getText()); });
            require(labels.contains("3 / 3 SHIPS PLACED") && labels.stream().noneMatch(s -> s.contains("[4] Submarine") || s.contains("[5] Destroyer")),
                    "Placement HUD uses configured count and hides disabled types");
            System.out.println("PASS: actual 3D drawing + HUD for Designer/3 ships; disabled hotkeys preserve draft and Ready follows room settings.");
        }
    }
    private void stroke(boolean erase,List<Coordinate> path) {
        int button=erase?MouseInput.BUTTON_RIGHT:MouseInput.BUTTON_LEFT;
        mouse.next=() -> {
            long time=1;
            for(int i=0;i<path.size();i++) {
                var p=cam.getScreenCoordinates(coordinates.worldCenter(path.get(i),.22f));
                if(i==0) { var press=new MouseButtonEvent(button,true,Math.round(p.x),Math.round(p.y)); press.setTime(time++); inputManager.onMouseButtonEvent(press); }
                var motion=new MouseMotionEvent(Math.round(p.x),Math.round(p.y),0,0,0,0); motion.setTime(time++); inputManager.onMouseMotionEvent(motion);
                if(i==path.size()-1) { var release=new MouseButtonEvent(button,false,Math.round(p.x),Math.round(p.y)); release.setTime(time++); inputManager.onMouseButtonEvent(release); }
            }
        };
        inputManager.update(1/60f); rootNode.updateGeometricState(); controller.update(0,inputManager.getCursorPosition());
    }
    @Override public void simpleRender(RenderManager renderer) {
        if(frame!=20) return;
        try {
            var buffer=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4); renderer.getRenderer().readFrameBufferWithFormat(null,buffer,Image.Format.BGRA8);
            var output=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer,output);
            Files.createDirectories(Path.of("build/designer-review")); ImageIO.write(output,"png",Path.of("build/designer-review",mode+".png").toFile());
            stop();
        } catch(Exception error) { throw new RuntimeException(error); }
    }
    private static final class InjectedMouse extends DummyMouseInput {
        Runnable next;
        @Override public void update() { if(next!=null) { Runnable value=next; next=null; value.run(); } }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    @Override public void handleError(String message,Throwable error) { if(error!=null) error.printStackTrace(); System.exit(1); }
    @Override public void destroy() { if(controls!=null) controls.close(); super.destroy(); }
}
