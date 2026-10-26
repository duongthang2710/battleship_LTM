package com.battleship.frontend.pvp;

import com.jme3.input.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;
import java.util.function.Consumer;

/** Registered before screen inputs so notification clicks never reach the board underneath. */
public final class GameInviteInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final GameInvitePanel panel;
    private final Consumer<GameInvitePanel.Action> action;
    private boolean pressed;

    public GameInviteInput(InputManager input, GameInvitePanel panel, Consumer<GameInvitePanel.Action> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) {
        if (event.getButtonIndex() != MouseInput.BUTTON_LEFT) return;
        Vector2f point = new Vector2f(event.getX(), event.getY());
        if (event.isPressed()) pressed = panel.contains(point);
        if (!pressed) return;
        event.setConsumed();
        if (!event.isPressed()) { pressed = false; panel.actionAt(point).ifPresent(action); }
    }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { if (pressed) event.setConsumed(); }
    @Override public void close() { input.removeRawInputListener(this); }
}
