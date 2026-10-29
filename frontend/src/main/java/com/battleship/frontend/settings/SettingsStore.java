package com.battleship.frontend.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class SettingsStore {
    public record Loaded(ClientPreferences preferences, String message) {}
    private final Path path;

    public SettingsStore() {
        this(Path.of(System.getProperty("battleship.settingsFile",
                Path.of(System.getProperty("user.home"), ".battleship", "settings.properties").toString())));
    }
    public SettingsStore(Path path) { this.path = path.toAbsolutePath().normalize(); }
    public Path path() { return path; }
    public Loaded load() {
        try {
            if (!Files.exists(path)) return new Loaded(ClientPreferences.defaults(), "");
            if (Files.size(path) > 8192) throw new IOException("Settings file is too large");
            Properties values = new Properties();
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
            String fullscreen = values.getProperty("fullscreen", "false");
            if (!fullscreen.equals("true") && !fullscreen.equals("false")) throw new IllegalArgumentException("Invalid fullscreen setting");
            return new Loaded(new ClientPreferences(Integer.parseInt(values.getProperty("width", "1440")),
                    Integer.parseInt(values.getProperty("height", "900")), Boolean.parseBoolean(fullscreen),
                    Float.parseFloat(values.getProperty("rotation", "1")), Float.parseFloat(values.getProperty("zoom", "1")),
                    Float.parseFloat(values.getProperty("volume", "0.75")), parseMuted(values)), "");
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            return new Loaded(ClientPreferences.defaults(), "Could not read valid settings. Defaults are active; save to replace the file.");
        }
    }
    private boolean parseMuted(Properties values) {
        String muted = values.getProperty("muted", "false");
        if (!muted.equals("true") && !muted.equals("false")) throw new IllegalArgumentException("Invalid audio setting");
        return Boolean.parseBoolean(muted);
    }
    public void save(ClientPreferences value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "settings-", ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("width", Integer.toString(value.width())); values.setProperty("height", Integer.toString(value.height()));
            values.setProperty("fullscreen", Boolean.toString(value.fullscreen()));
            values.setProperty("rotation", Float.toString(value.rotation())); values.setProperty("zoom", Float.toString(value.zoom()));
            values.setProperty("volume", Float.toString(value.volume())); values.setProperty("muted", Boolean.toString(value.muted()));
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { values.store(writer, "Battleship desktop preferences"); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
