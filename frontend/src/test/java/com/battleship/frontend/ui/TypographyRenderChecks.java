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
    private BattleScene scene;
    private int width, height, frame, index;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (int[] size : new int[][] {{1440, 900}, {1280, 800}, {960, 600}}) {
                Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                        TypographyRenderChecks.class.getName(), "" + size[0], "" + size[1]).inheritIO().start();
                try { require(child.waitFor(90, TimeUnit.SECONDS) && child.exitValue() == 0, "Typography rendering failed"); }
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
        var notice = new com.battleship.frontend.network.ConnectionNoticePanel(assetManager); notice.resize(width, height);
        notice.sync("Disconnected: Server closed the connection", false); add("connection-lost", notice.root());
        var menu = new MainMenu(assetManager); menu.resize(width, height);
        for (var action : MainMenu.Action.values()) require(menu.actionAt(menu.buttonCenter(action)).orElseThrow() == action, "Scaled menu hit target");
        add("menu", menu.root());
        var preferences = new SettingsPanel(assetManager); preferences.resize(width, height); preferences.show();
        preferences.setData(ClientPreferences.defaults(), ClientPreferences.RESOLUTIONS, true, false, "Settings saved. Tiếng Việt có dấu hiển thị rõ ràng.");
        for (var action : SettingsPanel.Action.values()) if (preferences.enabled(action)) require(preferences.actionAt(preferences.buttonCenter(action)).orElseThrow() == action, "Scaled Settings hit target");
        add("settings", preferences.root());
        var room = new PvpRoomPanel(assetManager); room.resize(width, height); room.setConnection(true, false);
        room.showChoice(); add("pvp-choice", room.root());
        room.showJoin(); for (char c : "ABC2".toCharArray()) room.inputCharacter(c); add("join-input", room.root());
        room.showRoom(new RoomSnapshot("ABC234", List.of(new RoomPlayer("a", "Nguyễn Trường Thắng", true), new RoomPlayer("b", "Đô đốc đại dương", false)), RoomPhase.WAITING), "a");
        add("waiting", room.root());
        var friends = new FriendsPanel(assetManager); friends.resize(width, height); friends.show(); friends.setLoading(FriendsAction.FRIENDS, 0);
        var entries = new ArrayList<FriendEntry>();
        for (int i = 0; i < 8; i++) entries.add(new FriendEntry(UUID.randomUUID(), "admiral_" + i, "Nguyễn Trường Thắng " + i, "FRIEND", Instant.now().toString(), true));
        friends.setData(new FriendsResult(FriendsAction.FRIENDS, 0, 8, 8, entries, "")); add("friends", friends.root());
        friends.click(friends.searchCenter()); for (char c : "admiral".toCharArray()) friends.inputCharacter(c); add("friends-input", friends.root());
        var leaderboard = new LeaderboardPanel(assetManager); leaderboard.resize(width, height); leaderboard.show();
        var ranks = new ArrayList<LeaderboardEntry>();
        for (int i = 0; i < 10; i++) ranks.add(new LeaderboardEntry(i + 1, "Đô đốc Nguyễn Trường " + i, "admiral_" + i, 20, 16, 4, 80, 3L));
        leaderboard.setPage(new LeaderboardPage(LeaderboardSort.WINS, 0, 10, 10, 10, ranks)); add("leaderboard", leaderboard.root());
        var captain = new AccountProfile(UUID.randomUUID(), "admiral", "Nguyễn Trường Thắng");
        var chat = new ChatPanel(assetManager); chat.resize(width, height); chat.setMode(ChatPanel.Mode.WAITING);
        var context = new ChatContext(UUID.randomUUID(), "ABC234", ChatScope.WAITING_ROOM, true); chat.setContext(context);
        for (int i = 0; i < 5; i++) chat.append(new ChatMessage(context.contextId(), i + 1, captain, "Tiếng Việt: Đội tàu đã sẵn sàng, chúc bạn may mắn!", Instant.now().toString()));
        add("chat", chat.root());
        chat.focus(); for (char c : "Chào đô đốc!".toCharArray()) chat.character(c); add("chat-input", chat.root()); chat.blur();
        var invite = new GameInvitePanel(assetManager); invite.resize(width, height);
        invite.show(new GameInvite(UUID.randomUUID(), "ABC234", captain, captain, GameInviteStatus.PENDING, 60_000, "Mời bạn tham gia trận đấu PvP."), true, false, 60_000);
        add("invite", invite.root());
        var engine = new GameEngine("Nguyễn Trường Thắng"); engine.randomPlacement();
        var hud = new BattleHud(assetManager, "Nguyễn Trường Thắng"); hud.resize(width, height);
        hud.setOpponentName("Đô đốc đại dương"); hud.setControlHints("Click: fire / Tab: switch board\nDrag sea: orbit / Scroll: zoom / R: reset");
        hud.setTurnClock("Nguyễn Trường Thắng", 17_000);
        var placement = new PlacementHud(assetManager); placement.resize(width, height); placement.setNetworkPlacement(true);
        placement.update(engine.state(), "Đội tàu đã sẵn sàng.", false);
        require(countText(placement.root(), "Placed") == 5, "Fleet status labels are centered without truncation");
        var gameplay = new Node("Gameplay UI preview"); gameplay.attachChild(hud.root()); gameplay.attachChild(placement.root()); add("placement", gameplay);
        engine.ready(); placement.update(engine.state(), "Đến lượt bạn bắn.", true);
        chat.setMode(ChatPanel.Mode.BATTLE); chat.toggle();
        var battle = (Node) gameplay.clone(); battle.attachChild(chat.root().clone()); add("battle", battle);
        engine.timeoutCurrentTurn();
        var over = new GameOverOverlay(assetManager); over.resize(width, height); over.show(engine.state());
        over.setRematchStatus("Waiting for opponent...", true, true); add("game-over", over.root());
        guiNode.attachChild(previews.getFirst().node());
    }
    private void add(String name, Node node) { previews.add(new Preview(name, (Node) node.clone())); }
    @Override public void simpleUpdate(float delta) { scene.update(delta, cam); }
    @Override public void simpleRender(RenderManager renderer) {
        if (++frame < 4) return;
        try {
            var buffer = BufferUtils.createByteBuffer(width * height * 4);
            renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer, image);
            var folder = Path.of("build/font-review", width + "x" + height); Files.createDirectories(folder);
            ImageIO.write(image, "png", folder.resolve(previews.get(index).name() + ".png").toFile());
            if (++index == previews.size()) { System.out.println("PASS: typography/font isolation, Vietnamese, hit targets and " + index + " rendered screens at " + width + "x" + height); stop(); return; }
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
