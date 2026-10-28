package com.battleship.frontend.network;

import com.jme3.input.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;
import java.util.function.Consumer;

public final class ConnectionNoticeInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final ConnectionNoticePanel panel;
    private final Consumer<ConnectionNoticePanel.Action> action;
    private boolean pressed;
    public ConnectionNoticeInput(InputManager input, ConnectionNoticePanel panel, Consumer<ConnectionNoticePanel.Action> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) {
        if (!panel.visible() && !pressed) return;
        event.setConsumed();
        if (event.getButtonIndex() != MouseInput.BUTTON_LEFT) return;
        if (event.isPressed()) pressed = true;
        else if (pressed) { pressed = false; panel.actionAt(new Vector2f(event.getX(), event.getY())).ifPresent(action); }
    }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { if (panel.visible() || pressed) event.setConsumed(); }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!panel.visible()) return;
        event.setConsumed();
        if (!event.isPressed() || event.isRepeating()) return;
        if (event.getKeyCode() == KeyInput.KEY_ESCAPE) action.accept(ConnectionNoticePanel.Action.DISMISS);
        else if (!panel.busy() && (event.getKeyCode() == KeyInput.KEY_RETURN || event.getKeyCode() == KeyInput.KEY_NUMPADENTER)) action.accept(ConnectionNoticePanel.Action.RECONNECT);
    }
    @Override public void close() { input.removeRawInputListener(this); }
}
