package com.battleship.frontend.settings;

import com.battleship.frontend.camera.OrbitCamera;
import com.jme3.renderer.Camera;
import java.io.IOException;
import java.nio.file.*;

public final class SettingsChecks {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("battleship-settings-");
        try {
            SettingsStore store = new SettingsStore(directory.resolve("profile/settings.properties"));
            require(store.load().preferences().equals(ClientPreferences.defaults()), "Missing file uses defaults");
            var saved = new ClientPreferences(1280, 720, true, 1.5f, .75f);
            store.save(saved);
            require(new SettingsStore(store.path()).load().preferences().equals(saved), "Fresh store restores every preference");
            require(store.load().preferences().forStartup().equals(new ClientPreferences(1280, 720, false, 1.5f, .75f)),
                    "A saved fullscreen profile starts Windowed while preserving size and sensitivities");
            store.save(ClientPreferences.defaults()); store.save(saved);
            require(store.load().preferences().equals(saved), "Repeated atomic replacement keeps the latest settings");
            try (var files = Files.list(store.path().getParent())) {
                require(files.count() == 1, "No temporary files remain after saving");
            }
            for (String invalid : new String[] {"rotation=NaN", "zoom=0", "volume=NaN", "volume=2", "muted=perhaps", "width=1", "fullscreen=perhaps", "width=\\uZZZZ"}) {
                Files.writeString(store.path(), invalid);
                var loaded = store.load();
                require(loaded.preferences().equals(ClientPreferences.defaults()) && !loaded.message().isBlank(), "Invalid file has a visible fallback: " + invalid);
            }
            Files.writeString(store.path(), "x".repeat(9000));
            require(!store.load().message().isBlank(), "Oversize file rejected");
            store.save(saved);
            Path blocked = directory.resolve("not-a-directory"); Files.writeString(blocked, "keep");
            try { new SettingsStore(blocked.resolve("settings.properties")).save(saved); throw new AssertionError("Save must fail"); }
            catch (IOException expected) { require(Files.readString(blocked).equals("keep"), "Failed save preserves existing files"); }
            Camera normal = new Camera(1440, 900), slow = new Camera(1440, 900), fast = new Camera(1440, 900);
            OrbitCamera a = new OrbitCamera(normal), b = new OrbitCamera(slow), c = new OrbitCamera(fast);
            b.setSensitivity(.5f, .5f); c.setSensitivity(2, 2);
            a.orbit(40, 0); b.orbit(80, 0); c.orbit(20, 0);
            require(normal.getLocation().distance(slow.getLocation()) < .0001f && normal.getLocation().distance(fast.getLocation()) < .0001f, "Orbit sensitivity changes actual camera motion");
            a.reset(); b.reset(); c.reset();
            a.zoom(2); b.zoom(4); c.zoom(1);
            require(normal.getLocation().distance(slow.getLocation()) < .0001f && normal.getLocation().distance(fast.getLocation()) < .0001f, "Zoom sensitivity changes actual camera distance");
            try { b.setSensitivity(Float.NaN, 1); throw new AssertionError("NaN must fail"); } catch (IllegalArgumentException expected) { }
            var audio = new ClientPreferences(1280, 720, true, 1.5f, .75f, .3f, true);
            store.save(audio);
            require(store.load().preferences().equals(audio) && store.load().preferences().forStartup().muted()
                    && store.load().preferences().forStartup().volume() == .3f, "Saved volume/mute survive reload and Windowed startup");
            checkDisplayShortcut();
            System.out.println("PASS: Settings defaults, persistence, atomic overwrite, malformed/oversize files, save failure and actual orbit/zoom sensitivity.");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static void checkDisplayShortcut() {
        var mouse = new com.jme3.input.dummy.DummyMouseInput(); var keys = new com.jme3.input.dummy.DummyKeyInput();
        mouse.initialize(); keys.initialize(); var input = new com.jme3.input.InputManager(mouse, keys, null, null);
        try (var shortcut = new com.battleship.frontend.input.DisplayModeInput(input)) {
            shortcut.onKeyEvent(new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_LMENU, '\0', true, false));
            var enter = new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_RETURN, '\n', true, false);
            shortcut.onKeyEvent(enter); require(enter.isConsumed() && shortcut.pollToggle() && !shortcut.pollToggle(), "Alt+Enter queues exactly one display toggle without submitting a form");
            var repeat = new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_RETURN, '\n', false, true);
            shortcut.onKeyEvent(repeat); require(repeat.isConsumed() && !shortcut.pollToggle(), "Held Enter cannot repeatedly toggle display");
            shortcut.onKeyEvent(new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_LMENU, '\0', false, false));
            var release = new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_RETURN, '\n', false, false);
            shortcut.onKeyEvent(release); require(release.isConsumed(), "Captured Enter release cannot reach a form");
            var plain = new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_RETURN, '\n', true, false);
            shortcut.onKeyEvent(plain); require(!plain.isConsumed() && !shortcut.pollToggle(), "Plain Enter still submits normally");
            shortcut.onKeyEvent(new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_LMENU, '\0', true, false));
            shortcut.reset();
            var afterFocus = new com.jme3.input.event.KeyInputEvent(com.jme3.input.KeyInput.KEY_RETURN, '\n', true, false);
            shortcut.onKeyEvent(afterFocus);
            require(!afterFocus.isConsumed() && !shortcut.pollToggle(), "Losing focus clears Alt even if its key release was not received");
        }
        System.out.println("PASS: global Alt+Enter capture, repeat guard and ordinary Enter preserved.");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
