package com.battleship.frontend.ui;

import com.battleship.frontend.auth.*;
import com.battleship.frontend.input.NavigationInput;
import com.battleship.frontend.network.*;
import com.battleship.frontend.pvp.PvpRoomPanel;
import com.battleship.frontend.pvp.RoomCodeInput;
import com.battleship.frontend.friends.*;
import com.battleship.frontend.chat.*;
import com.battleship.shared.chat.*;
import com.battleship.shared.room.*;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.input.*;
import com.jme3.input.dummy.*;
import com.jme3.input.event.*;
import java.awt.datatransfer.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiFeedbackChecks {
    public static void main(String[] args) throws Exception {
        var assets = new DesktopAssetManager(true);
        var mouse = new DummyMouseInput(); var keyboard = new DummyKeyInput(); mouse.initialize(); keyboard.initialize();
        var input = new InputManager(mouse, keyboard, null, null);
        var account = new AccountPanel(assets); account.show(); account.sync(null, "", false, true);
        var actions = new ArrayList<AccountPanel.Action>();
        try (var keys = new AccountInput(input, account, actions::add)) {
            for (char c : "captain123".toCharArray()) keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_UNKNOWN, c, true, false));
            account.update(.6f); require(!account.caretVisible(), "Caret blinks off");
            account.update(.5f); require(account.caretVisible(), "Caret blinks back on");
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, false));
            for (int i = 0; i < 5; i++) keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, true));
            require(account.username().equals("capt"), "GLFW repeated Backspace deletes continuously");
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, false));
            require(account.username().equals("capt"), "Key release does not delete");
            require(account.caretVisible(), "Typing resets blink");
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', true, false));
            require(!actions.contains(AccountPanel.Action.EXIT), "Esc at login never exits");
            account.switchMode(); keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', true, false));
            require(actions.getLast() == AccountPanel.Action.SWITCH, "Esc returns registration to sign-in");
            account.toggleServer(); keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', true, false));
            require(actions.getLast() == AccountPanel.Action.SERVER, "Esc returns from endpoint form");
        }
        int[] back = {0};
        try (var navigation = new NavigationInput(input, () -> back[0]++)) {
            var press = new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', true, false);
            navigation.onKeyEvent(press); navigation.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', false, true));
            navigation.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE, '\0', false, false));
            require(press.isConsumed() && back[0] == 1, "Back handles Esc once without default Exit");
        }
        var friends = new FriendsPanel(assets); friends.show(); friends.click(friends.searchCenter());
        for (char c : "captain".toCharArray()) friends.inputCharacter(c);
        friends.updateCaret(.6f); require(!friends.caretVisible(), "Search caret blinks off");
        friends.updateCaret(.5f); require(friends.caretVisible(), "Search caret blinks on");
        try (var keys = new FriendsInput(input, friends, a -> { })) {
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, false));
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, true));
            require(friends.query().equals("capta"), "Search supports held Backspace");
        }
        var chat = new ChatPanel(assets); chat.setMode(ChatPanel.Mode.WAITING);
        chat.setContext(new ChatContext(UUID.randomUUID(), "ABC234", ChatScope.WAITING_ROOM, true)); chat.focus();
        for (char c : "Hello".toCharArray()) chat.character(c);
        chat.updateCaret(.6f); require(!chat.caretVisible(), "Chat caret blinks off");
        chat.updateCaret(.5f); require(chat.caretVisible(), "Chat caret blinks on");
        try (var keys = new ChatInput(input, chat, a -> { })) {
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, false));
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, true));
            require(chat.draft().equals("Hel"), "Chat supports held Backspace");
        }
        var room = new PvpRoomPanel(assets); room.setConnection(true, false); room.showChoice();
        require(room.enabled(PvpRoomPanel.Action.SHOW_CREATE) && !room.enabled(PvpRoomPanel.Action.CREATE_ROOM)
                && !room.enabled(PvpRoomPanel.Action.TOGGLE_MODE), "PvP choice only opens Create or Join; no settings or creation yet");
        room.showCreate();
        require(room.enabled(PvpRoomPanel.Action.CREATE_ROOM), "Settings have explicit Create confirmation");
        room.changeShipCount(-1); room.changeShipCount(-1); room.changeShipCount(-1);
        require(room.selectedRules().shipCount() == 3 && !room.enabled(PvpRoomPanel.Action.FEWER_SHIPS), "Minimum three ships");
        room.changeShipCount(1); room.changeShipCount(1); room.changeShipCount(1);
        require(room.selectedRules().shipCount() == 5 && !room.enabled(PvpRoomPanel.Action.MORE_SHIPS), "Maximum five ships");
        room.toggleTurnRule();
        require(room.selectedRules().turnRule() == com.battleship.shared.game.TurnRule.HIT_CONTINUES, "Hit-again option is selectable");
        for (int[] size : new int[][] {{960, 600}, {1280, 800}, {1920, 1080}}) {
            room.resize(size[0], size[1]);
            for (var action : new PvpRoomPanel.Action[] {PvpRoomPanel.Action.MODE_BASIC, PvpRoomPanel.Action.FLEET_FOUR,
                    PvpRoomPanel.Action.RULE_HIT_CONTINUES, PvpRoomPanel.Action.CREATE_ROOM, PvpRoomPanel.Action.BACK})
                require(room.actionAt(room.buttonCenter(action)).orElseThrow() == action, "Settings hit areas scale with the panel");
        }
        require(room.selectedMode() == com.battleship.shared.game.GameMode.CLASSIC, "Classic remains the default");
        require(room.actionAt(room.buttonCenter(PvpRoomPanel.Action.MODE_DESIGNER)).orElseThrow() == PvpRoomPanel.Action.MODE_DESIGNER, "Mode selection hit target");
        room.toggleMode(); require(room.selectedMode() == com.battleship.shared.game.GameMode.DESIGNER, "Room creator can choose designer");
        require(Arrays.stream(PvpRoomPanel.Action.values()).noneMatch(action -> action.name().startsWith("DEPLOY_")),
                "Room setup has no initial-fleet setting");
        room.selectOption(PvpRoomPanel.Action.REPORT_HIDE);
        require(room.selectedOptions().hideSunkInfo(),"Hidden sinking information is selectable independently of room rules");
        room.showJoin();
        room.pasteRoomCode(" abc234 "); require(room.roomCode().equals("ABC234"),"Pasted room code is normalized and replaces old entry");
        room.pasteRoomCode("invalid clipboard"); require(room.roomCode().equals("ABC234"),"Invalid paste cannot corrupt a valid code");
        for(int i=0;i<6;i++) room.backspace();
        for (char c : "ABC234".toCharArray()) room.inputCharacter(c);
        room.updateCaret(.6f); require(!room.caretVisible(), "Join caret blinks off");
        room.updateCaret(.5f); require(room.caretVisible(), "Join caret blinks on");
        try (var keys = new RoomCodeInput(input, room, a -> { },new ClipboardService(value->{},()->" abc234 "))) {
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', true, false));
            for (int i = 0; i < 2; i++) keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_BACK, '\0', false, true));
            require(room.roomCode().equals("ABC"), "Join code supports held Backspace");
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL,'\0',true,false));
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_V,'v',true,false));
            keys.onKeyEvent(new KeyInputEvent(KeyInput.KEY_LCONTROL,'\0',false,false));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while(!room.roomCode().equals("ABC234")&&System.nanoTime()<deadline) { Thread.sleep(10); keys.beginInput(); }
            require(room.roomCode().equals("ABC234"),"Ctrl+V asynchronously pastes full code through the input listener");
        }
        require(Arrays.stream(PvpRoomPanel.Action.values()).noneMatch(a -> a.name().equals("CONNECT") || a.name().equals("DISCONNECT")), "No transport buttons in PvP");
        room.showRoom(new RoomSnapshot("ABC234", List.of(new RoomPlayer("a", "Admiral", false)), RoomPhase.WAITING), "a");
        for (int[] size : new int[][] {{960, 600}, {1280, 800}, {1920, 1080}}) {
            room.resize(size[0], size[1]); require(room.actionAt(room.buttonCenter(PvpRoomPanel.Action.COPY_CODE)).orElseThrow() == PvpRoomPanel.Action.COPY_CODE, "Copy target scales");
        }
        var notice = new ConnectionNoticePanel(assets); notice.sync("Server closed the connection", false);
        try (var modal = new ConnectionNoticeInput(input, notice, a -> { })) {
            var key = new KeyInputEvent(KeyInput.KEY_F, 'f', true, false); modal.onKeyEvent(key);
            var move = new MouseMotionEvent(1, 1, 3, 3, 1, 1); modal.onMouseMotionEvent(move);
            require(key.isConsumed() && move.isConsumed(), "Connection modal blocks gameplay and typing underneath");
            require(notice.actionAt(notice.buttonCenter(ConnectionNoticePanel.Action.RECONNECT)).isPresent(), "Reconnect offered in notice");
            notice.sync("Server closed the connection", true);
            require(notice.actionAt(notice.buttonCenter(ConnectionNoticePanel.Action.RECONNECT)).isEmpty(), "No duplicate reconnect while connecting");
            notice.sync(null, false); require(!notice.visible(), "Successful connection hides notice");
        }
        var clipboard = new Clipboard("UI test only"); var status = new AtomicReference<String>(); var worker = new AtomicReference<Thread>();
        try (var service = new ClipboardService(value -> { worker.set(Thread.currentThread()); clipboard.setContents(new StringSelection(value), null); })) {
            service.copy("ABC234", status::set); awaitClipboard(service, status);
            require(clipboard.getData(DataFlavor.stringFlavor).equals("ABC234") && status.get().equals("Room code copied."), "Clipboard receives exact room code");
            require(worker.get() != Thread.currentThread(), "Clipboard work is outside the render thread");
        }
        status.set(null);
        try (var service = new ClipboardService(value -> { throw new IllegalStateException("busy"); })) {
            service.copy("ABC234", status::set); awaitClipboard(service, status);
            require(status.get().contains("Could not access clipboard"), "Clipboard error is reported");
        }
        var mesh = UiShapes.chamfered(100, 40, 9); var points = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position);
        for (int i = 1; i < 9; i++) {
            float x = points.get(i * 3), y = points.get(i * 3 + 1);
            require(!(x == 0 || x == 100) || y >= 9 && y <= 31, "Fill excludes protruding square corners");
        }
        var parent=new com.jme3.scene.Node();
        UiShapes.frame(parent,0,0,100,40,9,2,new com.battleship.frontend.graphics.Materials(assets).flat(com.jme3.math.ColorRGBA.White),1);
        var border=(com.jme3.scene.Geometry)parent.getChild(0);var vertices=border.getMesh().getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position);
        for(int i=0;i<vertices.limit();i+=3) require(vertices.get(i)>=0&&vertices.get(i)<=100&&vertices.get(i+1)>=0&&vertices.get(i+1)<=40,"Joined frame vertices stay within the panel bounds");
        var confirm=new LeaveConfirmationPanel(assets);int[] leaves={0};confirm.resize(1280,800);
        try(var modal=new com.battleship.frontend.input.LeaveConfirmationInput(input,confirm)) {
            confirm.show(()->leaves[0]++);var key=new KeyInputEvent(KeyInput.KEY_T,'t',true,false);modal.onKeyEvent(key);
            require(key.isConsumed()&&leaves[0]==0,"Leave modal blocks gameplay without leaving early");
            confirm.click(confirm.buttonCenter(false));require(!confirm.visible()&&leaves[0]==0,"Cancel keeps the room");
            confirm.show(()->leaves[0]++);modal.onKeyEvent(new KeyInputEvent(KeyInput.KEY_ESCAPE,'\0',true,false));
            require(!confirm.visible()&&leaves[0]==0,"Esc cancels the leave dialog");
            confirm.show(()->leaves[0]++);confirm.click(confirm.buttonCenter(true));confirm.confirm();
            require(leaves[0]==1&&!confirm.visible(),"Confirm leaves exactly once");
        }
        System.out.println("PASS: caret blink, repeated Backspace, Esc, removed transport buttons, copy targets/clipboard, connection modal and chamfered fills.");
    }
    private static void awaitClipboard(ClipboardService service, AtomicReference<String> result) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (result.get() == null) { service.update(); if (System.nanoTime() > deadline) throw new AssertionError("Clipboard callback timed out"); Thread.sleep(5); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
