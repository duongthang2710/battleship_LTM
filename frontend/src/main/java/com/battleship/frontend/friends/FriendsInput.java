package com.battleship.frontend.friends;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListenerAdapter;
import com.jme3.input.event.KeyInputEvent;
import java.util.function.Consumer;

public final class FriendsInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final FriendsPanel panel;
    private final Consumer<FriendsPanel.Command> action;

    public FriendsInput(InputManager input, FriendsPanel panel, Consumer<FriendsPanel.Command> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!panel.visible() || (!event.isPressed() && !event.isRepeating())) return;
        if (event.getKeyCode() == KeyInput.KEY_ESCAPE && !event.isRepeating()) {
            action.accept(new FriendsPanel.Command(panel.confirming() ? FriendsPanel.Action.CANCEL_REMOVE : FriendsPanel.Action.BACK, null));
            event.setConsumed(); return;
        }
        if (!panel.focused()) return;
        switch (event.getKeyCode()) {
            case KeyInput.KEY_BACK -> panel.backspace();
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> {
                if (!event.isRepeating()) action.accept(new FriendsPanel.Command(FriendsPanel.Action.SEARCH, null));
            }
            default -> panel.inputCharacter(event.getKeyChar());
        }
        event.setConsumed();
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
