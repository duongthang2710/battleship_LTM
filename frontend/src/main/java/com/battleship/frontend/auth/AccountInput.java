package com.battleship.frontend.auth;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListenerAdapter;
import com.jme3.input.event.KeyInputEvent;
import java.util.function.Consumer;

public final class AccountInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final AccountPanel panel;
    private final Consumer<AccountPanel.Action> action;
    public AccountInput(InputManager input, AccountPanel panel, Consumer<AccountPanel.Action> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!panel.visible() || !event.isPressed()) return;
        switch (event.getKeyCode()) {
            case KeyInput.KEY_TAB -> { if (!event.isRepeating()) panel.nextField(); }
            case KeyInput.KEY_BACK -> panel.backspace();
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> {
                if (!event.isRepeating() && panel.enabled(AccountPanel.Action.SUBMIT)) action.accept(AccountPanel.Action.SUBMIT);
            }
            case KeyInput.KEY_ESCAPE -> { if (!event.isRepeating()) action.accept(AccountPanel.Action.BACK); }
            default -> panel.inputCharacter(event.getKeyChar());
        }
        event.setConsumed();
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
