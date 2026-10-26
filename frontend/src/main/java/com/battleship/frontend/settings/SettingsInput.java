package com.battleship.frontend.settings;

import com.jme3.input.*;
import com.jme3.input.event.KeyInputEvent;
import java.util.function.Consumer;

public final class SettingsInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final SettingsPanel panel;
    private final Consumer<SettingsPanel.Action> action;
    public SettingsInput(InputManager input, SettingsPanel panel, Consumer<SettingsPanel.Action> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (panel.visible() && event.getKeyCode() == KeyInput.KEY_ESCAPE) {
            event.setConsumed();
            if (event.isPressed() && !event.isRepeating() && panel.enabled(SettingsPanel.Action.BACK)) action.accept(SettingsPanel.Action.BACK);
        }
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
