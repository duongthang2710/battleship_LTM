package com.battleship.frontend.ui;

import com.battleship.frontend.auth.AccountPanel;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.chat.ChatPanel;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.friends.FriendsPanel;
import com.battleship.frontend.game.*;
import com.battleship.frontend.leaderboard.LeaderboardPanel;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.pvp.*;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.settings.*;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.chat.*;
import com.battleship.shared.friends.*;
import com.battleship.shared.game.GameEngine;
import com.battleship.shared.leaderboard.*;
import com.battleship.shared.room.*;
import com.jme3.app.SimpleApplication;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Node;
import com.jme3.system.*;
import com.jme3.texture.*;
import com.jme3.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Render-only sample data stays in tests; no server, account or database is modified. */
public final class TypographyRenderChecks extends SimpleApplication {
    private TypographyRenderChecks() { super(new com.jme3.app.StatsAppState()); }
    private record Preview(String name, Node node) { }
    private final List<Preview> previews = new ArrayList<>();
    private final List<String> layoutFindings=new ArrayList<>();
    private BattleScene scene;
    private int width, height, frame, index;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (int[] size : new int[][] {{1280,800}, {960,600}, {1280,720}, {1920,1080}}) {
                Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                        TypographyRenderChecks.class.getName(), "" + size[0], "" + size[1]).inheritIO().start();
                try { require(child.waitFor(150, TimeUnit.SECONDS) && child.exitValue() == 0, "Typography rendering failed"); }
                finally { if (child.isAlive()) child.destroyForcibly(); }
            }
            return;
        }
        var app = new TypographyRenderChecks(); app.width = Integer.parseInt(args[0]); app.height = Integer.parseInt(args[1]);
        var settings = new AppSettings(true); settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setResolution(app.width, app.height); settings.setSamples(4); settings.setGammaCorrection(true);
        settings.setAudioRenderer(null); settings.setUseInput(false); settings.setFrameRate(30);
        app.setSettings(settings); app.setShowSettings(false); app.start(JmeContext.Type.OffscreenSurface);
    }
    @Override public void simpleInitApp() {
        setDisplayFps(false); setDisplayStatView(false); setPauseOnLostFocus(false);
        var original = assetManager.loadFont("fonts/CormorantGaramond.fnt");
        var originalTexture = original.getPage(0).getTextureParam("ColorMap").getTextureValue();
        var filter = originalTexture.getMinFilter();
        var ui = UiTypography.display(assetManager);
        require(ui.getPage(0) != original.getPage(0) && originalTexture.getMinFilter() == filter, "Board font materials remain unchanged");
        require(ui.getPage(0).getTextureParam("ColorMap").getTextureValue().getMinFilter() == Texture.MinFilter.Trilinear, "UI font uses mipmaps");
        var body = UiTypography.body(assetManager);
        UiTypography.normalize("Tiếng Việt: Nguyễn, Trường, chiến thắng, Đội tàu").codePoints()
                .forEach(cp -> require(body.getCharSet().getCharacter(cp) != null, "Missing Vietnamese glyph: " + cp));
        require(UiTypography.normalize("Tie\u0302\u0301ng Vie\u0323\u0302t").equals("Tiếng Việt"), "Decomposed input normalizes correctly");
        scene = new BattleScene(assetManager); rootNode.attachChild(scene.root()); new OrbitCamera(cam);
        scene.installOceanReflections(viewPort); SceneEffects.install(assetManager, viewPort, scene.keyLight());
        var account = new AccountPanel(assetManager); account.resize(width, height); account.show();
        account.sync(null, "Đăng nhập để tham gia trận đấu.", false, true); add("login", account.root());
        for (char c : "captain".toCharArray()) account.inputCharacter(c);
        account.nextField(); for (char c : "Password123!".toCharArray()) account.inputCharacter(c); add("login-filled", account.root());
        account.switchMode(); add("register", (Node) account.root().clone()); account.switchMode();
        account.switchMode(); account.nextField(); for(char c:"Password123!".toCharArray()) account.inputCharacter(c);
        account.nextField(); for(char c:"Password123!".toCharArray()) account.inputCharacter(c);add("register-filled",account.root());account.switchMode();
        account.toggleServer();add("server-settings",account.root());account.toggleServer();
        account.sync(null,"The username or password is incorrect. Please check your account details.",false,true);add("login-error",account.root());
        account.sync(new AccountProfile(UUID.randomUUID(),"duongthang2710","Nguyễn Trường Thắng"),"Connected",false,true);add("profile",account.root());
        var notice = new com.battleship.frontend.network.ConnectionNoticePanel(assetManager); notice.resize(width, height);
        notice.sync("Disconnected: Server closed the connection", false); add("connection-lost", notice.root());
        notice.sync("Disconnected: Server closed the connection",true);add("connection-reconnecting",notice.root());
        var menu = new MainMenu(assetManager); menu.resize(width, height);
        for (var action : MainMenu.Action.values()) require(menu.actionAt(menu.buttonCenter(action)).orElseThrow() == action, "Scaled menu hit target");
        add("menu", menu.root());
        var preferences = new SettingsPanel(assetManager); preferences.resize(width, height); preferences.show();
        preferences.setData(ClientPreferences.defaults(), ClientPreferences.RESOLUTIONS, true, false, "Settings saved. Tiếng Việt có dấu hiển thị rõ ràng.");
        for (var action : SettingsPanel.Action.values()) if (preferences.enabled(action)) require(preferences.actionAt(preferences.buttonCenter(action)).orElseThrow() == action, "Scaled Settings hit target");
        add("settings", preferences.root());
        preferences.setData(new ClientPreferences(1920,1080,true,1,1,.75f,false),ClientPreferences.RESOLUTIONS,true,false,"Settings saved.");add("settings-fullscreen",preferences.root());
        var room = new PvpRoomPanel(assetManager); room.resize(width, height); room.setConnection(true, false);
        room.showChoice(); add("pvp-choice", room.root());
        room.showCreate(); add("room-settings-basic", room.root());
        room.toggleMode(); room.changeShipCount(-1); room.changeShipCount(-1); room.toggleTurnRule();
        room.selectOption(PvpRoomPanel.Action.DEPLOY_SHUFFLED);room.selectOption(PvpRoomPanel.Action.REPORT_HIDE);
        add("room-settings-designer-3", room.root());
        room.showJoin(); for (char c : "ABC2".toCharArray()) room.inputCharacter(c); add("join-input", room.root());
        room.showRoom(new RoomSnapshot("ABC234", List.of(new RoomPlayer("a", "Nguyễn Trường Thắng", true), new RoomPlayer("b", "Đô đốc đại dương", false)), RoomPhase.WAITING), "a");
        require(countText(room.root(), "COPY")==0, "Copy uses a compact icon instead of a large text button");
        add("waiting", room.root());checkWaitingSpacing(room);
        room.showRoom(new RoomSnapshot("7UA8TZ",List.of(new RoomPlayer("a","duongthang2710",false)),RoomPhase.WAITING),"a");
        room.setStatus("Waiting for another admiral.");checkWaitingSpacing(room);add("waiting-one-player",room.root());
        room.showRoom(new RoomSnapshot("ABC234",List.of(new RoomPlayer("a","Nguyễn Trường Thắng",true),new RoomPlayer("b","Đô đốc đại dương",true)),RoomPhase.WAITING,
            new com.battleship.shared.game.GameRules(com.battleship.shared.game.GameMode.DESIGNER,3,com.battleship.shared.game.TurnRule.HIT_CONTINUES),new RoomOptions(true,true)),"a");
        room.setStatus("The room is full. Both players must confirm before placement begins.");checkWaitingSpacing(room);add("waiting-ready-long-status",room.root());
        var friends = new FriendsPanel(assetManager); friends.resize(width, height); friends.show(); friends.setLoading(FriendsAction.FRIENDS, 0);add("friends-loading",friends.root());
        var entries = new ArrayList<FriendEntry>();
        for (int i = 0; i < 8; i++) entries.add(new FriendEntry(UUID.randomUUID(), "admiral_" + i, "Nguyễn Trường Thắng " + i, "FRIEND", Instant.now().toString(), true));
        friends.setData(new FriendsResult(FriendsAction.FRIENDS, 0, 8, 8, entries, "")); add("friends", friends.root());
        friends.click(friends.searchCenter()); for (char c : "admiral".toCharArray()) friends.inputCharacter(c); add("friends-input", friends.root());
        friends.confirm(entries.getFirst().userId());add("friends-remove-confirm",friends.root());friends.cancelConfirmation();
        friends.setLoading(FriendsAction.RECEIVED,0);friends.setData(new FriendsResult(FriendsAction.RECEIVED,0,8,1,List.of(new FriendEntry(UUID.randomUUID(),"rival_admiral","Admiral Dusk","INCOMING",Instant.now().toString(),true)),""));add("friends-received",friends.root());
        friends.setLoading(FriendsAction.SENT,0);friends.setData(new FriendsResult(FriendsAction.SENT,0,8,1,List.of(new FriendEntry(UUID.randomUUID(),"rival_admiral","Admiral Dusk","OUTGOING",Instant.now().toString(),false)),""));add("friends-sent",friends.root());
        friends.setLoading(FriendsAction.SEARCH,0);friends.setData(new FriendsResult(FriendsAction.SEARCH,0,8,1,List.of(new FriendEntry(UUID.randomUUID(),"new_admiral","New Admiral","NONE",null,true)),""));add("friends-search-result",friends.root());
        friends.setLoading(FriendsAction.FRIENDS,0);friends.setData(new FriendsResult(FriendsAction.FRIENDS,0,8,0,List.of(),""));add("friends-empty",friends.root());
        friends.setError("Unable to load your friends. Please try again.");add("friends-error",friends.root());
        var leaderboard = new LeaderboardPanel(assetManager); leaderboard.resize(width, height); leaderboard.show();
        var ranks = new ArrayList<LeaderboardEntry>();
        for (int i = 0; i < 10; i++) ranks.add(new LeaderboardEntry(i + 1, "Đô đốc Nguyễn Trường " + i, "admiral_" + i, 20, 16, 4, 80, 3L));
        leaderboard.setPage(new LeaderboardPage(LeaderboardSort.WINS, 0, 10, 10, 10, ranks)); add("leaderboard", leaderboard.root());
        leaderboard.setPage(new LeaderboardPage(LeaderboardSort.WIN_RATE,0,10,0,10,List.of()));add("leaderboard-empty",leaderboard.root());
        leaderboard.setError("Unable to load rankings. Please try again.");add("leaderboard-error",leaderboard.root());
        var captain = new AccountProfile(UUID.randomUUID(), "admiral", "Nguyễn Trường Thắng");
        var chat = new ChatPanel(assetManager); chat.resize(width, height); chat.setMode(ChatPanel.Mode.WAITING);
        var context = new ChatContext(UUID.randomUUID(), "ABC234", ChatScope.WAITING_ROOM, true); chat.setContext(context);
        for (int i = 0; i < 5; i++) chat.append(new ChatMessage(context.contextId(), i + 1, captain, "Tiếng Việt: Đội tàu đã sẵn sàng, chúc bạn may mắn!", Instant.now().toString()));
        add("chat", chat.root());
        chat.focus(); for (char c : "Chào đô đốc!".toCharArray()) chat.character(c); add("chat-input", chat.root()); chat.blur();
        var invite = new GameInvitePanel(assetManager); invite.resize(width, height);
        invite.show(new GameInvite(UUID.randomUUID(), "ABC234", captain, captain, GameInviteStatus.PENDING, 60_000, "Mời bạn tham gia trận đấu PvP."), true, false, 60_000);
        add("invite", invite.root());
        invite.show(new GameInvite(UUID.randomUUID(), "ABC234", captain, captain, GameInviteStatus.PENDING, 60_000, "Waiting for your friend to accept the invitation."), false, false, 60_000);
        add("invite-sent", invite.root());
        invite.error("The room is no longer available. Reopen Friends and send a new invitation."); add("invite-error", invite.root());
        invite.show(new GameInvite(UUID.randomUUID(),"ABC234",captain,captain,GameInviteStatus.EXPIRED,0,"This invitation has expired."),true,false,0);add("invite-expired",invite.root());
        var engine = new GameEngine("Nguyễn Trường Thắng"); engine.randomPlacement();
        var hud = new BattleHud(assetManager, "Nguyễn Trường Thắng"); hud.resize(width, height);
        hud.setOpponentName("Đô đốc đại dương");
        hud.setTurnClock("Nguyễn Trường Thắng", 17_000);
        var placement = new PlacementHud(assetManager); placement.resize(width, height); placement.setNetworkPlacement(true);
        hud.setOpponentReady(true);
        placement.update(engine.state(), "Đội tàu đã sẵn sàng.", false);
        require(countText(placement.root(), "5 / 5 READY") == 1, "Compact fleet tray shows complete fleet count");
        var ownMap = new OwnBoardMinimap(assetManager); ownMap.resize(width,height); ownMap.update(engine.state().player().board()); ownMap.setBattleVisible(false);
        var gameplay = new Node("Gameplay UI preview"); gameplay.attachChild(hud.root()); gameplay.attachChild(placement.root()); gameplay.attachChild(ownMap.root()); add("placement", gameplay);
        placement.toggleMore();add("placement-more",gameplay);placement.toggleMore();
        engine.ready(); hud.setBattleState(engine.state(), com.battleship.shared.game.GameRules.DEFAULT); hud.setGameStatus("Your turn", "Choose a target"); placement.update(engine.state(), "Đến lượt bạn bắn.", true);
        ownMap.setBattleVisible(true);
        chat.setMode(ChatPanel.Mode.BATTLE);
        chat.setLocalAccountId(captain.id());
        var rival=new AccountProfile(UUID.randomUUID(),"opponent","Admiral Dusk");
        chat.append(new ChatMessage(context.contextId(),6,rival,"Your move, admiral. Good luck!",Instant.now().toString()));
        var chatRoles=new java.util.HashSet<String>();
        chat.root().depthFirstTraversal(n->{ String role=n.getUserData("chatRole"); if(role!=null) chatRoles.add(role); });
        require(chatRoles.containsAll(java.util.Set.of("self","opponent","content")),"Chat renders own name, opponent name and content as distinct roles");
        require(!chat.collapsed() && chat.inputCenter().y>0,"Battle chat opens at bottom-left with Enter input");
        require(!ownMap.contains(chat.inputCenter()),"Chat input stays clear of minimap");
        hud.root().updateLogicalState(0); hud.root().updateGeometricState();
        ownMap.root().updateLogicalState(0); ownMap.root().updateGeometricState();
        float avatarCenter=((com.jme3.bounding.BoundingBox)hud.root().getChild("Own commander portrait").getWorldBound()).getCenter().x;
        float mapCenter=((com.jme3.bounding.BoundingBox)ownMap.root().getChild("Crystalline minimap grid").getWorldBound()).getCenter().x;
        require(Math.abs(avatarCenter-mapCenter)<.01f,"Avatar and minimap remain centered above corner chat");
        var battle = (Node) gameplay.clone(); battle.attachChild(chat.root().clone()); add("battle", battle);
        hud.setFleetReportVisible(false);add("battle-hidden-report",gameplay);hud.setFleetReportVisible(true);
        for(int seconds=3;seconds>=1;seconds--) {
            hud.setStartCountdown(seconds*1000L,false); add("countdown-"+seconds,gameplay);
        }
        hud.setStartCountdown(0,true); add("countdown-start",gameplay); hud.setStartCountdown(0,false);
        hud.update(4);
        require(((Node)hud.root().getChild("Battle turn notice")).getQuantity()==0,"Turn notice expires instead of staying permanently");
        engine.timeoutCurrentTurn();
        var over = new GameOverOverlay(assetManager); over.resize(width, height); over.show(engine.state());
        over.setRematchStatus("Waiting for opponent...", true, true); add("game-over", over.root());
        over.setEndReason(com.battleship.shared.match.MatchEndReason.TURN_TIMEOUT);over.setRematchStatus("Opponent declined the rematch.",false,false);add("game-over-declined",over.root());
        var ended=engine.state();var victory=new com.battleship.shared.game.GameState(ended.phase(),ended.player(),ended.enemy(),ended.currentPlayerId(),ended.player().id(),ended.lastAttack(),ended.placement(),ended.ready(),ended.turnNumber());
        over.show(victory);over.setRematchStatus("",true,false);over.setEndReason(com.battleship.shared.match.MatchEndReason.FLEET_DESTROYED);add("game-over-victory",over.root());
        var confirm=new LeaveConfirmationPanel(assetManager);confirm.resize(width,height);confirm.show(()->{});add("leave-confirmation",confirm.root());
        guiNode.attachChild(previews.getFirst().node());
    }
    private void add(String name, Node node) {
        Node copy=(Node)node.clone();copy.updateLogicalState(0);copy.updateGeometricState();
        auditText(name,copy);previews.add(new Preview(name,copy));
    }
    private void checkWaitingSpacing(PvpRoomPanel room) {
        room.root().updateLogicalState(0);room.root().updateGeometricState();
        float scale=UiTypography.scale(width,height),buttonTop=room.buttonCenter(PvpRoomPanel.Action.TOGGLE_READY).y+24*scale;
        room.root().depthFirstTraversal(n->{if(n instanceof com.jme3.font.BitmapText t && (t.getText().contains("Empty board /")||t.getText().contains("Shuffled fleet /"))) {
            var b=(com.jme3.bounding.BoundingBox)t.getWorldBound();
            require(b.getCenter().y-b.getYExtent()>buttonTop+6*scale,"Waiting details must clear the Ready button");
        }});
    }
    private void auditText(String screen,Node node) {
        var texts=new ArrayList<com.jme3.font.BitmapText>();
        node.depthFirstTraversal(n->{if(n instanceof com.jme3.font.BitmapText t && !t.getText().isBlank()) {
            for(com.jme3.scene.Spatial p=t;p!=null;p=p.getParent()) if(p.getLocalCullHint()==com.jme3.scene.Spatial.CullHint.Always) return;
            texts.add(t);
        }});
        for(int i=0;i<texts.size();i++) for(int j=i+1;j<texts.size();j++) {
            var a=(com.jme3.bounding.BoundingBox)texts.get(i).getWorldBound();var b=(com.jme3.bounding.BoundingBox)texts.get(j).getWorldBound();
            float dx=Math.min(a.getCenter().x+a.getXExtent(),b.getCenter().x+b.getXExtent())-Math.max(a.getCenter().x-a.getXExtent(),b.getCenter().x-b.getXExtent());
            float dy=Math.min(a.getCenter().y+a.getYExtent(),b.getCenter().y+b.getYExtent())-Math.max(a.getCenter().y-a.getYExtent(),b.getCenter().y-b.getYExtent());
            if(dx>2&&dy>2) layoutFindings.add(screen+"\t"+texts.get(i).getText().replace('\n',' ')+"\t"+texts.get(j).getText().replace('\n',' ')+"\t"+dx+"\t"+dy);
        }
    }
    @Override public void simpleUpdate(float delta) { scene.update(delta, cam); }
    @Override public void simpleRender(RenderManager renderer) {
        if (++frame < 4) return;
        try {
            var buffer = BufferUtils.createByteBuffer(width * height * 4);
            renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer, image);
            var folder = Path.of("build/font-review", width + "x" + height); Files.createDirectories(folder);
            ImageIO.write(image, "png", folder.resolve(previews.get(index).name() + ".png").toFile());
            if (++index == previews.size()) { Files.write(folder.resolve("layout-findings.tsv"),layoutFindings); require(layoutFindings.isEmpty(),"Overlapping UI text: "+String.join("; ",layoutFindings)); System.out.println("PASS: typography/font isolation, Vietnamese, hit targets and " + index + " rendered screens at " + width + "x" + height); stop(); return; }
            guiNode.detachAllChildren(); guiNode.attachChild(previews.get(index).node()); frame = 0;
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static long countText(Node node, String value) {
        return node.getChildren().stream().mapToLong(child -> child instanceof com.jme3.font.BitmapText text
                ? (text.getText().equals(value) ? 1 : 0) : child instanceof Node nested ? countText(nested, value) : 0).sum();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void handleError(String message, Throwable error) { if (error != null) error.printStackTrace(); System.exit(1); }
}
