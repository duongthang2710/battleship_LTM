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
    private final com.battleship.frontend.ui.ClipboardService clipboard;
    private boolean control, command, shift;

    public RoomCodeInput(InputManager input, PvpRoomPanel panel, Consumer<PvpRoomPanel.Action> action) {
        this(input,panel,action,new com.battleship.frontend.ui.ClipboardService());
    }
    public RoomCodeInput(InputManager input, PvpRoomPanel panel, Consumer<PvpRoomPanel.Action> action,
                         com.battleship.frontend.ui.ClipboardService clipboard) {
        this.clipboard=clipboard;
        this.input = input;
        this.panel = panel;
        this.action = action;
        input.addRawInputListener(this);
    }

    @Override public void onKeyEvent(KeyInputEvent event) {
        boolean down=event.isPressed();
        switch(event.getKeyCode()) {
            case KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL -> control=down;
            case KeyInput.KEY_LMETA, KeyInput.KEY_RMETA -> command=down;
            case KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT -> shift=down;
        }
        if (!panel.acceptsRoomCode() || (!event.isPressed() && !event.isRepeating())) return;
        if ((control || command) && event.getKeyCode()==KeyInput.KEY_V || shift && event.getKeyCode()==KeyInput.KEY_INSERT) {
            if(!event.isRepeating()) clipboard.paste(panel::pasteRoomCode,panel::setStatus);
        } else if(control || command) {
            // Modifier shortcuts must not insert their key characters into the code.
        } else if (event.getKeyCode() == KeyInput.KEY_BACK) {
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

    @Override public void beginInput() { clipboard.update(); }
    @Override public void close() { clipboard.close(); input.removeRawInputListener(this); }
}
