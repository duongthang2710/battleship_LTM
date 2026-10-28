package com.battleship.frontend.ui;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.chat.*;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.input.PointerInput;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.settings.*;
import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.chat.*;
import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.event.*;
import com.jme3.math.*;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.*;
import com.jme3.system.*;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.texture.Image;
import com.jme3.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** Real GLFW window/cursor callbacks; no backend, authentication or database is touched. */
public final class UiInteractionChecks extends SimpleApplication {
    private MainMenu menu;
    private ChatPanel chat;
    private BattleScene scene;
    private DisplaySettings display;
    private PointerInput pointer;
    private ChatInput chatInput;
    private MainMenu.Action clicked;
    private int phase, frame, sizeIndex, clickIndex, width, height;
    private long handle;
    private Vector2f target;
    private final List<MainMenu.Action> actions = List.of(MainMenu.Action.values());

    public static void main(String[] args) {
        var app = new UiInteractionChecks();
        var settings = new AppSettings(true); settings.setResolution(1280, 800);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32); settings.setAudioRenderer(null);
        settings.setResizable(true); settings.setSamples(4); settings.setGammaCorrection(true); settings.setFrameRate(30);
        app.setSettings(settings); app.setShowSettings(false); app.start();
    }
    @Override public void simpleInitApp() {
        flyCam.setEnabled(false); setDisplayFps(false); setDisplayStatView(false); setPauseOnLostFocus(false);
        handle = ((LwjglWindow) getContext()).getWindowHandle();
        display = new DisplaySettings(this);
        scene = new BattleScene(assetManager); rootNode.attachChild(scene.root());
        var orbit = new OrbitCamera(cam); scene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, scene.keyLight());
        menu = new MainMenu(assetManager); guiNode.attachChild(menu.root());
        menu.friendsUpdated(true); menu.friendsUpdated(false);
        chat = new ChatPanel(assetManager); guiNode.attachChild(chat.root()); chat.setMode(ChatPanel.Mode.WAITING);
        var context = new ChatContext(UUID.randomUUID(), "UITEST", ChatScope.WAITING_ROOM, true); chat.setContext(context);
        var sender = new AccountProfile(UUID.randomUUID(), "ui_test", "Nguyễn Trường");
        for (int i = 0; i < 35; i++) chat.append(new ChatMessage(context.contextId(), i, sender, "Message " + i + " – kiểm tra thanh cuộn.", Instant.now().toString()));
        chatInput = new ChatInput(inputManager, chat, a -> { if (a == ChatPanel.Action.TOGGLE) chat.toggle(); });
        pointer = new PointerInput(inputManager, orbit, p -> clicked = menu.actionAt(p).orElse(null));
        resizePanels();
    }
    private void resizePanels() {
        width = guiViewPort.getCamera().getWidth(); height = guiViewPort.getCamera().getHeight(); menu.resize(width, height); chat.resize(width, height);
    }
    @Override public void reshape(int w, int h) {
        super.reshape(w, h);
        if (menu != null && w > 0 && h > 0) resizePanels();
    }
    @Override public void simpleUpdate(float delta) {
        scene.update(delta, cam);
        if (width != guiViewPort.getCamera().getWidth() || height != guiViewPort.getCamera().getHeight()) resizePanels();
        if (++frame < 8) return;
        frame = 0;
        switch (phase) {
            case 0 -> {
                System.out.printf("Display %d: window=%s framebuffer=%dx%d gui=%dx%d%n", sizeIndex, display.windowSize(), width, height, guiViewPort.getCamera().getWidth(), guiViewPort.getCamera().getHeight());
                clickIndex = 0; phase = 1;
            }
            case 1 -> {
                var action = actions.get(clickIndex); target = renderedLabelCenter(menu.root(), action.name().replace('_', ' '));
                require(menu.actionAt(target).orElse(null) == action, "Rendered menu label is inside its actual hit rectangle: " + action);
                clicked = null; move(target); phase = 2;
            }
            case 2 -> {
                require(inputManager.getCursorPosition().distance(target) < 2, "Native cursor maps to UI pixels after resize: " + inputManager.getCursorPosition() + " vs " + target);
                mouseButton(true); mouseButton(false); phase = 3;
            }
            case 3 -> {
                require(clicked == actions.get(clickIndex), "Actual GLFW button path reaches displayed menu item: " + actions.get(clickIndex) + " got " + clicked);
                phase = ++clickIndex < actions.size() ? 1 : 4;
            }
            case 4 -> {
                checkChat(); save("window-" + sizeIndex); phase = 5;
            }
            case 5 -> {
                if (++sizeIndex == 4) { System.out.println("PASS: native menu clicks across window/fullscreen/maximize, scrollbar/wheel/drag capture and chat anchoring."); stop(); return; }
                if (sizeIndex == 1) display.apply(new ClientPreferences(1440, 900, true, 1, 1));
                else if (sizeIndex == 2) display.apply(new ClientPreferences(960, 600, false, 1, 1));
                else GLFW.glfwMaximizeWindow(handle);
                phase = 0;
            }
        }
    }
    private void checkChat() {
        chat.latest(); int last = chat.firstVisibleLine(); require(last > 0, "Chat has scrollable history");
        var thumb = chat.scrollThumbCenter();
        // Same-frame drag, deliberately delivered movement first as GLFW does.
        chatInput.beginInput();
        var motion = new MouseMotionEvent((int) thumb.x, (int) (thumb.y + height), 0, height, 0, 0); motion.setTime(2);
        var press = new MouseButtonEvent(0, true, (int) thumb.x, (int) thumb.y); press.setTime(1);
        var release = new MouseButtonEvent(0, false, (int) thumb.x, (int) (thumb.y + height)); release.setTime(3);
        chatInput.onMouseMotionEvent(motion); chatInput.onMouseButtonEvent(press); chatInput.onMouseButtonEvent(release); chatInput.endInput();
        require(chat.firstVisibleLine() == 0 && motion.isConsumed() && release.isConsumed(), "Thumb drag reaches top outside panel and consumes input");
        String anchor = chat.firstVisibleText();
        var sender = chat.messages().getFirst().sender(); var contextId = chat.messages().getFirst().contextId();
        chat.append(new ChatMessage(contextId, 99, sender, "New message while reading history", Instant.now().toString()));
        require(chat.firstVisibleText().equals(anchor), "New messages preserve scroll anchor");
        var center = chat.listCenter();
        var wheel = new MouseMotionEvent((int) center.x, (int) center.y, 0, 0, -1, -1);
        chatInput.beginInput(); chatInput.onMouseMotionEvent(wheel); chatInput.endInput();
        require(chat.firstVisibleLine() == 3 && wheel.isConsumed(), "Wheel scrolls chat without orbit/zoom");
        chat.latest(); require(chat.atBottom(), "Latest reaches bottom");
        chat.setMode(ChatPanel.Mode.BATTLE); chat.toggle();
        require(chat.atBottom(), "Battle layout keeps bottom");
        thumb = chat.scrollThumbCenter(); require(chat.beginScrollDrag(thumb), "Battle scrollbar starts drag");
        chat.dragScroll(new Vector2f(thumb.x, thumb.y + height)); chat.endScrollDrag();
        require(chat.firstVisibleLine() == 0, "Battle scrollbar scrolls to oldest");
        chat.setMode(ChatPanel.Mode.WAITING); chat.latest();
    }
    private Vector2f renderedLabelCenter(Node node, String caption) {
        for (Spatial child : node.getChildren()) {
            if (child instanceof BitmapText text && text.getText().equals(caption)) {
                var p = text.localToWorld(new Vector3f(text.getLineWidth() / 2, -text.getHeight() / 2, 0), null);
                return new Vector2f(p.x, p.y);
            }
            if (child instanceof Node nested) {
                var result = findLabel(nested, caption); if (result != null) return result;
            }
        }
        throw new AssertionError("Missing label " + caption);
    }
    private Vector2f findLabel(Node node, String caption) {
        for (Spatial child : node.getChildren()) {
            if (child instanceof BitmapText text && text.getText().equals(caption)) {
                var p = text.localToWorld(new Vector3f(text.getLineWidth() / 2, -text.getHeight() / 2, 0), null); return new Vector2f(p.x, p.y);
            }
            if (child instanceof Node nested) { var found = findLabel(nested, caption); if (found != null) return found; }
        }
        return null;
    }
    private void move(Vector2f pixels) {
        try (var stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1); var h = stack.mallocInt(1); GLFW.glfwGetWindowSize(handle, w, h);
            var callback = GLFW.glfwSetCursorPosCallback(handle, null); GLFW.glfwSetCursorPosCallback(handle, callback);
            require(callback != null, "Native cursor callback installed");
            callback.invoke(handle, pixels.x * w.get(0) / guiViewPort.getCamera().getWidth(), h.get(0) - pixels.y * h.get(0) / guiViewPort.getCamera().getHeight());
        }
    }
    private void mouseButton(boolean down) {
        var callback = GLFW.glfwSetMouseButtonCallback(handle, null); GLFW.glfwSetMouseButtonCallback(handle, callback);
        require(callback != null, "Native mouse callback installed"); callback.invoke(handle, 0, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }
    private void save(String name) {
        // Capture after the next rendered frame, not during layout mutation.
        screenshot = name;
    }
    private String screenshot;
    @Override public void simpleRender(RenderManager renderer) {
        if (screenshot == null) return;
        try {
            int w = guiViewPort.getCamera().getWidth(), h = guiViewPort.getCamera().getHeight();
            var buffer = BufferUtils.createByteBuffer(w * h * 4); renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            var image = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer, image);
            var folder = Path.of("build/ui-review"); Files.createDirectories(folder); ImageIO.write(image, "png", folder.resolve(screenshot + ".png").toFile()); screenshot = null;
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void handleError(String message, Throwable error) { if (error != null) error.printStackTrace(); System.exit(1); }
    @Override public void destroy() { if (pointer != null) pointer.close(); if (chatInput != null) chatInput.close(); super.destroy(); }
}
