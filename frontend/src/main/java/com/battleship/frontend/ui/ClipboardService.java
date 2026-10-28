package com.battleship.frontend.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Clipboard access can initialize native desktop APIs; keep it off the render thread. */
public final class ClipboardService implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("ui-clipboard").factory());
    private final ConcurrentLinkedQueue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private final Consumer<String> write;
    public ClipboardService() { this(value -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null)); }
    ClipboardService(Consumer<String> write) { this.write = write; }
    public void copy(String value, Consumer<String> result) {
        worker.execute(() -> {
            String message;
            try { write.accept(value); message = "Room code copied."; }
            catch (RuntimeException exception) { message = "Could not access clipboard. Room code: " + value; }
            String status = message; completed.add(() -> result.accept(status));
        });
    }
    public void update() { Runnable event; while ((event = completed.poll()) != null) event.run(); }
    @Override public void close() { worker.shutdownNow(); completed.clear(); }
}
