package com.battleship.frontend.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListenerAdapter;
import com.jme3.input.event.KeyInputEvent;

/** Registered before UI listeners so Alt+Enter never submits a form. */
public final class DisplayModeInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private boolean leftAlt, rightAlt, requested, enterCaptured;

    public DisplayModeInput(InputManager input) { this.input = input; input.addRawInputListener(this); }

    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!event.isRepeating()) {
            if (event.getKeyCode() == KeyInput.KEY_LMENU) leftAlt = event.isPressed();
            if (event.getKeyCode() == KeyInput.KEY_RMENU) rightAlt = event.isPressed();
        }
        if (event.getKeyCode() != KeyInput.KEY_RETURN && event.getKeyCode() != KeyInput.KEY_NUMPADENTER) return;
        if (event.isPressed() && !event.isRepeating() && (leftAlt || rightAlt)) {
            requested = true; enterCaptured = true;
        }
        if (enterCaptured) {
            event.setConsumed();
            if (!event.isPressed() && !event.isRepeating()) enterCaptured = false;
        }
    }

    public boolean pollToggle() { boolean result = requested; requested = false; return result; }
    public void reset() { leftAlt = rightAlt = requested = enterCaptured = false; }
    @Override public void close() { input.removeRawInputListener(this); }
}
