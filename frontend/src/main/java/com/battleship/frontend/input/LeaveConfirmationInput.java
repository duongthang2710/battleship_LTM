package com.battleship.frontend.input;

import com.battleship.frontend.ui.LeaveConfirmationPanel;
import com.jme3.input.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;

/** Consume modal input before chat, placement, shooting and orbit controls. */
public final class LeaveConfirmationInput extends RawInputListenerAdapter implements AutoCloseable {
    private boolean pressed;
    private final InputManager input;
    private final LeaveConfirmationPanel panel;
    public LeaveConfirmationInput(InputManager input,LeaveConfirmationPanel panel) {
        this.input=input;this.panel=panel;input.addRawInputListener(this);
    }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if(!panel.visible()) return;event.setConsumed();
        if(event.isPressed()&&!event.isRepeating()&&event.getKeyCode()==KeyInput.KEY_ESCAPE) panel.cancel();
    }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { if(panel.visible()) event.setConsumed(); }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) {
        if(!panel.visible()) return;event.setConsumed();
        if(event.getButtonIndex()!=MouseInput.BUTTON_LEFT) return;
        if(event.isPressed()) pressed=true;
        else { boolean click=pressed;pressed=false;if(click) panel.click(new Vector2f(event.getX(),event.getY())); }
    }
    @Override public void close() { panel.cancel();input.removeRawInputListener(this); }
}
