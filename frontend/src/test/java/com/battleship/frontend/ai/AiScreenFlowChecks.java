package com.battleship.frontend.ai;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.game.PlacementHud;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.settings.SettingsPanel;
import com.battleship.shared.ai.AiDifficulty;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.game.*;
import com.battleship.shared.protocol.*;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.input.*;
import com.jme3.input.dummy.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual screen/input lifetimes and real TCP framing; authentication server is scripted. */
public final class AiScreenFlowChecks {
    public static void main(String[] args) throws Exception {
        try(var listener=new ServerSocket(0);var worker=Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(30_000);
            System.setProperty("battleship.host","127.0.0.1"); System.setProperty("battleship.port",Integer.toString(listener.getLocalPort()));
            Future<?> server=worker.submit(() -> {
                try(var socket=listener.accept()) {
                    socket.setSoTimeout(15_000);
                    var input=new DataInputStream(socket.getInputStream());var output=new DataOutputStream(socket.getOutputStream());
                    Message request;
                    while((request=MessageCodec.read(input))!=null) switch(request.type()) {
                        case CONNECT -> MessageCodec.write(output,Message.reply(MessageType.CONNECT_OK,request.requestId(),new ConnectResponse(new PlayerIdentity("ai-check","Captain"))));
                        case LOGIN -> MessageCodec.write(output,Message.reply(MessageType.AUTH_SUCCESS,request.requestId(),new AccountProfile(UUID.randomUUID(),"ai_check","Captain")));
                        case PING -> MessageCodec.write(output,Message.reply(MessageType.PONG,request.requestId(),null));
                        case DISCONNECT -> {return;}
                        default -> throw new AssertionError("PvAI must not send room/match commands: "+request.type());
                    }
                } catch(IOException error) {throw new UncheckedIOException(error);}
            });
            var assets=new DesktopAssetManager(true);var mouse=new InjectedMouse();var keys=new DummyKeyInput();mouse.initialize();keys.initialize();
            var input=new InputManager(mouse,keys,null,null);var camera=new Camera(1280,800);var gui=new Node("UI");
            var board = new Node("Board");
            var surface = new Geometry("Crystalline playing surface",new com.jme3.scene.shape.Quad(10,10));
            surface.rotate(-com.jme3.math.FastMath.HALF_PI,0,0);surface.setLocalTranslation(-5,.185f,5);
            board.attachChild(surface);board.updateGeometricState();
            try(var screens=new ScreenController(assets,gui,input,camera,new OrbitCamera(camera),board,"Captain",()->{})) {
                screens.resize(1280,800);
                require(screens.accountPanel().visible()&&!screens.aiSetupPanel().visible(),"PvAI remains behind login");
                await(screens,()->screens.pvp().connected());screens.pvp().login("ai_check","TestPassword!");
                await(screens,()->screens.mainMenu().visible());
                click(screens,input,mouse,screens.mainMenu().buttonCenter(MainMenu.Action.AI));
                require(screens.aiSetupPanel().visible()&&!screens.mainMenu().visible(),"AI Battle opens setup");
                click(screens,input,mouse,screens.aiSetupPanel().buttonCenter(AiSetupPanel.Action.HARD));
                require(screens.aiSetupPanel().difficulty()==AiDifficulty.HARD,"Real pointer selects Hard");
                click(screens,input,mouse,screens.aiSetupPanel().buttonCenter(AiSetupPanel.Action.START));
                require(screens.game().aiMode()&&!screens.aiSetupPanel().visible()&&screens.game().state().enemy().name().equals("AI Hard"),"Start enters AI placement");
                key(screens,input,mouse,KeyInput.KEY_F,'f');require(screens.game().state().player().board().ships().size()==5,"Placement shortcuts attached");
                screens.game().perform(PlacementHud.Action.SETTINGS);tick(screens);
                require(screens.settingsPanel().visible(),"Settings opens from AI placement");
                click(screens,input,mouse,screens.settingsPanel().buttonCenter(SettingsPanel.Action.BACK));
                require(screens.game().aiMode()&&!screens.settingsPanel().visible(),"Settings returns to AI rather than PvP");
                key(screens,input,mouse,KeyInput.KEY_RETURN,'\n');require(screens.game().state().phase()==GamePhase.BATTLE,"Gameplay listener restored after Settings");
                screens.game().perform(PlacementHud.Action.SETTINGS);tick(screens);
                click(screens,input,mouse,screens.settingsPanel().buttonCenter(SettingsPanel.Action.BACK));
                require(screens.game().state().phase()==GamePhase.BATTLE,"Battle preserved through Settings");
                key(screens,input,mouse,KeyInput.KEY_ESCAPE,'\0');
                require(gui.getChild("Leave confirmation").getLocalCullHint()==Spatial.CullHint.Never,"Esc asks to leave AI");
                key(screens,input,mouse,KeyInput.KEY_ESCAPE,'\0');
                require(gui.getChild("Leave confirmation").getLocalCullHint()==Spatial.CullHint.Always&&screens.game().aiMode(),"Cancel preserves match");
                key(screens,input,mouse,KeyInput.KEY_ESCAPE,'\0');click(screens,input,mouse,new Vector2f(537,342));
                require(screens.mainMenu().visible()&&!screens.game().aiMode(),"Confirmed leave returns to menu and discards bot");
                key(screens,input,mouse,KeyInput.KEY_F,'f');require(screens.game().state().player().board().ships().isEmpty(),"Gameplay keys detached in menu");
            } finally { listener.close(); }
            server.get(5,TimeUnit.SECONDS);
            System.out.println("PASS: authenticated AI Battle navigation, difficulty/start clicks, placement keys, Settings return, leave/cancel and no room protocol commands.");
        }
    }
    private static void await(ScreenController screens,BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()&&System.nanoTime()<end){tick(screens);Thread.sleep(10);}
        require(condition.getAsBoolean(),"Screen/TCP transition completed");
    }
    private static void tick(ScreenController screens){screens.update(0,new Vector2f(-1,-1));screens.update(0,new Vector2f(-1,-1));}
    private static void click(ScreenController screens,InputManager input,InjectedMouse mouse,Vector2f point) {
        mouse.next=()->{input.onMouseButtonEvent(new MouseButtonEvent(0,true,(int)point.x,(int)point.y));input.onMouseButtonEvent(new MouseButtonEvent(0,false,(int)point.x,(int)point.y));};
        input.update(1/60f);tick(screens);
    }
    private static void key(ScreenController screens,InputManager input,InjectedMouse mouse,int code,char value) {
        mouse.next=()->{input.onKeyEvent(new KeyInputEvent(code,value,true,false));input.onKeyEvent(new KeyInputEvent(code,value,false,false));};input.update(1/60f);tick(screens);
    }
    private static final class InjectedMouse extends DummyMouseInput {
        private Runnable next;
        @Override public void update(){if(next!=null){Runnable events=next;next=null;events.run();}}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
