package com.battleship.frontend.audio;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import java.io.BufferedInputStream;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.logging.Logger;
import javax.sound.sampled.*;

/** Streams one looping WAV on its own worker; long scores are never cached in RAM. */
final class AudioLoop implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(AudioLoop.class.getName());
    private final AssetManager assets;
    private final DoubleSupplier gain;
    private final Object changed = new Object();
    private final Thread thread;
    private volatile String requested;
    private volatile boolean closed;
    private volatile SourceDataLine output;

    AudioLoop(AssetManager assets, DoubleSupplier gain, String name) {
        this.assets = assets; this.gain = gain;
        thread = Thread.ofVirtual().name("audio-loop-" + name).start(this::run);
    }
    void select(String file) {
        if (!Objects.equals(requested, file)) { requested = file; wake(); }
    }
    void wake() { synchronized (changed) { changed.notifyAll(); } }
    int playing() {
        SourceDataLine line = output;
        return !closed && line != null && line.isRunning() ? 1 : 0;
    }
    private AudioInputStream open(String file) throws Exception {
        var asset = assets.locateAsset(new AssetKey<>("sounds/" + file + ".wav"));
        if (asset == null) throw new IllegalStateException("Missing loop " + file);
        return AudioSystem.getAudioInputStream(new BufferedInputStream(asset.openStream()));
    }
    private void awaitChange() throws InterruptedException { synchronized (changed) { changed.wait(100); } }
    private void run() {
        while (!closed) {
            String file = requested;
            try {
                if (file == null || gain.getAsDouble() <= 0) { awaitChange(); continue; }
                stream(file);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); return;
            } catch (Exception failure) {
                if (!closed) LOG.fine("Background audio unavailable: " + failure.getMessage());
                // Retry only after a scene or mute change; no spinning on machines without audio.
                try {
                    while (!closed && Objects.equals(file, requested) && gain.getAsDouble() > 0) awaitChange();
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    private void stream(String file) throws Exception {
        AudioInputStream input = open(file);
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(input.getFormat());
            line.open(input.getFormat(), 8192);
            output = line;
            line.start();
            byte[] buffer = new byte[4096];
            float level = 0;
            while (!closed) {
                boolean same = Objects.equals(file, requested);
                float target = same ? (float)gain.getAsDouble() : 0;
                if (same && target <= 0) {
                    line.stop(); line.flush();
                    while (!closed && Objects.equals(file, requested) && gain.getAsDouble() <= 0) awaitChange();
                    if (closed || !Objects.equals(file, requested)) break;
                    level = 0; line.start(); continue;
                }
                if (!same && level < .001f) break;
                int count = input.read(buffer);
                if (count < 0) { input.close(); input = open(file); continue; }
                float nextLevel = level + (target - level) * .35f;
                // Ramp within each PCM chunk to avoid discontinuities on scene/volume changes.
                int samples = count / 2;
                for (int i = 0; i < samples; i++) {
                    int offset = i * 2;
                    int sample = (short)((buffer[offset] & 255) | (buffer[offset + 1] << 8));
                    float multiplier = level + (nextLevel - level) * (i + 1) / samples;
                    int value = Math.round(sample * multiplier);
                    buffer[offset] = (byte)value; buffer[offset + 1] = (byte)(value >> 8);
                }
                level = nextLevel;
                int offset = 0;
                while (!closed && offset < count) offset += line.write(buffer, offset, count - offset);
            }
        } finally {
            input.close();
            if (line != null) { line.stop(); line.flush(); line.close(); }
            output = null;
        }
    }
    @Override public void close() {
        closed = true; wake();
        SourceDataLine line = output;
        if (line != null) line.close();
        thread.interrupt();
    }
}
