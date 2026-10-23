package com.battleship.frontend.pvp;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListenerAdapter;
import com.jme3.input.event.KeyInputEvent;
import java.util.function.Consumer;

public final class RoomCodeInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final PvpRoomPanel panel;
    private final Consumer<PvpRoomPanel.Action> action;

    public RoomCodeInput(InputManager input, PvpRoomPanel panel, Consumer<PvpRoomPanel.Action> action) {
        this.input = input;
        this.panel = panel;
        this.action = action;
        input.addRawInputListener(this);
    }

    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!panel.acceptsRoomCode() || !event.isPressed()) return;
        if (event.getKeyCode() == KeyInput.KEY_BACK) {
            panel.backspace();
        } else if (event.getKeyCode() == KeyInput.KEY_RETURN || event.getKeyCode() == KeyInput.KEY_NUMPADENTER) {
            if (!event.isRepeating() && panel.enabled(PvpRoomPanel.Action.JOIN_ROOM)) {
                action.accept(PvpRoomPanel.Action.JOIN_ROOM);
            }
        } else {
            panel.inputCharacter(event.getKeyChar());
        }
        event.setConsumed();
    }

    @Override public void close() { input.removeRawInputListener(this); }
}
