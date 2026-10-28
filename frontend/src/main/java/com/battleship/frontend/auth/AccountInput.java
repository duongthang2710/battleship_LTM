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
        if (!panel.visible() || (!event.isPressed() && !event.isRepeating())) return;
        switch (event.getKeyCode()) {
            case KeyInput.KEY_TAB -> { if (!event.isRepeating()) panel.nextField(); }
            case KeyInput.KEY_BACK -> panel.backspace();
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> {
                if (!event.isRepeating()) {
                    AccountPanel.Action submit = panel.editingServer() ? AccountPanel.Action.CONNECT : AccountPanel.Action.SUBMIT;
                    if (panel.enabled(submit)) action.accept(submit);
                }
            }
            case KeyInput.KEY_ESCAPE -> {
                if (!event.isRepeating()) {
                    if (panel.editingServer()) action.accept(AccountPanel.Action.SERVER);
                    else if (panel.registering()) action.accept(AccountPanel.Action.SWITCH);
                    else if (panel.enabled(AccountPanel.Action.BACK)) action.accept(AccountPanel.Action.BACK);
                }
            }
            default -> panel.inputCharacter(event.getKeyChar());
        }
        event.setConsumed();
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
