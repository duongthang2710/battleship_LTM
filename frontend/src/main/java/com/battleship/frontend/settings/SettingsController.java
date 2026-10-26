package com.battleship.frontend.settings;

import com.battleship.frontend.camera.OrbitCamera;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

public final class SettingsController implements AutoCloseable {
    private final SettingsStore store = new SettingsStore();
    private final SettingsPanel panel;
    private final OrbitCamera orbit;
    private final DisplaySettings display;
    private final Runnable back;
    private final ExecutorService files = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("settings-file").factory());
    private final ConcurrentLinkedQueue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private ClientPreferences current = ClientPreferences.defaults();
    private List<ClientPreferences.Resolution> sizes = ClientPreferences.RESOLUTIONS;
    private boolean ready, saving, displayAvailable;
    private String status = "Loading settings...";

    public SettingsController(SettingsPanel panel, OrbitCamera orbit, DisplaySettings display, Runnable back) {
        this.panel = panel; this.orbit = orbit; this.display = display; this.back = back;
        files.submit(() -> { var loaded = store.load(); completed.add(() -> loaded(loaded)); });
    }
    private void loaded(SettingsStore.Loaded loaded) {
        current = loaded.preferences(); status = loaded.message();
        try {
            if (display != null) {
                sizes = display.resolutions(); current = display.fit(current); display.apply(current); displayAvailable = true;
            }
        } catch (RuntimeException exception) { status = "Display settings unavailable: " + exception.getMessage(); }
        orbit.setSensitivity(current.rotation(), current.zoom());
        ready = true; refresh();
    }
    public ClientPreferences current() { return current; }
    public boolean ready() { return ready; }
    public boolean saving() { return saving; }
    public SettingsStore store() { return store; }
    public void show() { panel.show(); refresh(); }
    public void hide() { panel.hide(); }
    public void update() { Runnable event; while ((event = completed.poll()) != null) event.run(); }
    private void refresh() { panel.setData(current, sizes, displayAvailable, !ready || saving, status); }
    public void handle(SettingsPanel.Action action) {
        if (!panel.visible() || !panel.enabled(action)) return;
        switch (action) {
            case BACK -> { hide(); back.run(); }
            case RESET -> refresh();
            case SAVE -> save();
            default -> panel.edit(action);
        }
    }
    private void apply(ClientPreferences value) {
        if (displayAvailable) display.apply(value);
        orbit.setSensitivity(value.rotation(), value.zoom());
    }
    private void save() {
        ClientPreferences next = panel.draft(), previous = current;
        try { apply(next); }
        catch (RuntimeException exception) {
            try { apply(previous); } catch (RuntimeException ignored) { }
            panel.message("Could not apply display: " + exception.getMessage(), false); return;
        }
        saving = true; panel.message("Saving settings...", true);
        files.submit(() -> {
            try {
                store.save(next);
                completed.add(() -> { current = next; saving = false; status = "Settings saved. They will be restored next launch."; refresh(); });
            } catch (IOException | SecurityException exception) {
                completed.add(() -> {
                    saving = false;
                    status = "Could not save settings. Check folder permissions; previous values were kept.";
                    try { apply(previous); }
                    catch (RuntimeException rollback) { status = "Could not save settings or restore display. Reopen Settings to retry."; }
                    refresh();
                });
            }
        });
    }
    @Override public void close() { files.shutdown(); }
}
