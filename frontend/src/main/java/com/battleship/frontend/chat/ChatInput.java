package com.battleship.frontend.chat;

import com.jme3.input.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;
import java.util.*;
import java.util.function.Consumer;

/** Registered ahead of gameplay input. Typing and scrolling inside chat cannot fire, place or orbit. */
public final class ChatInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final ChatPanel panel;
    private final Consumer<ChatPanel.Action> action;
    private final Set<Integer> capturedKeys = new HashSet<>();
    private final List<InputEvent> mouseEvents = new ArrayList<>();
    private boolean pressed, scrollbar;

    public ChatInput(InputManager input, ChatPanel panel, Consumer<ChatPanel.Action> action) {
        this.input = input; this.panel = panel; this.action = action; input.addRawInputListener(this);
    }
    @Override public void beginInput() { mouseEvents.clear(); }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) { mouseEvents.add(event); }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { mouseEvents.add(event); }
    @Override public void endInput() {
        // GLFW queues movement before buttons; preserve drag order within the same frame.
        mouseEvents.sort(Comparator.comparingLong(InputEvent::getTime));
        for (InputEvent event : mouseEvents) {
            if (event instanceof MouseButtonEvent button) handleButton(button);
            else if (event instanceof MouseMotionEvent motion) handleMotion(motion);
        }
    }
    private void handleButton(MouseButtonEvent event) {
        if (event.getButtonIndex() != MouseInput.BUTTON_LEFT) return;
        Vector2f point = new Vector2f(event.getX(), event.getY());
        if (event.isPressed()) {
            pressed = panel.contains(point);
            scrollbar = pressed && panel.beginScrollDrag(point);
            if (!pressed) panel.blur();
        }
        if (!pressed) return;
        event.setConsumed();
        if (!event.isPressed()) {
            if (scrollbar) { panel.dragScroll(point); panel.endScrollDrag(); }
            else panel.click(point).ifPresent(action);
            pressed = scrollbar = false;
        }
    }
    private void handleMotion(MouseMotionEvent event) {
        if (pressed) {
            if (scrollbar) panel.dragScroll(new Vector2f(event.getX(), event.getY()));
            event.setConsumed();
        }
        if (panel.contains(new Vector2f(event.getX(), event.getY())) && event.getDeltaWheel() != 0) {
            panel.scroll(event.getDeltaWheel() > 0 ? -3 : 3); event.setConsumed();
        }
    }
    @Override public void onKeyEvent(KeyInputEvent event) {
        if (!event.isPressed() && !event.isRepeating()) { if (capturedKeys.remove(event.getKeyCode())) event.setConsumed(); return; }
        if (!panel.visible()) return;
        boolean enter = event.getKeyCode() == KeyInput.KEY_RETURN || event.getKeyCode() == KeyInput.KEY_NUMPADENTER;
        if (!panel.focused() && !(enter && panel.writable())) return;
        capturedKeys.add(event.getKeyCode()); event.setConsumed();
        if (!panel.focused()) { if (!event.isRepeating()) panel.focus(); return; }
        switch (event.getKeyCode()) {
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> { if (!event.isRepeating()) action.accept(ChatPanel.Action.SEND); }
            case KeyInput.KEY_ESCAPE -> panel.blur();
            case KeyInput.KEY_BACK -> panel.backspace();
            case KeyInput.KEY_PGUP -> panel.scroll(-6);
            case KeyInput.KEY_PGDN -> panel.scroll(6);
            case KeyInput.KEY_TAB -> { }
            default -> panel.character(event.getKeyChar());
        }
    }
    @Override public void close() { panel.endScrollDrag(); input.removeRawInputListener(this); }
}
