package com.battleship.frontend.input;

import com.jme3.input.*;
import com.jme3.input.event.KeyInputEvent;

/** Registered after modal/text input, before screen-specific shortcuts. */
public final class NavigationInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final Runnable back;
    public NavigationInput(InputManager input, Runnable back) { this.input = input; this.back = back; input.addRawInputListener(this); }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (event.getKeyCode() != KeyInput.KEY_ESCAPE) return;
        event.setConsumed();
        if (event.isPressed() && !event.isRepeating()) back.run();
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
