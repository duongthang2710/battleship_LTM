package com.battleship.frontend.input;

import com.battleship.frontend.camera.OrbitCamera;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListenerAdapter;
import com.jme3.input.event.InputEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector2f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class PointerInput extends RawInputListenerAdapter implements AutoCloseable {
    public interface DragHandler {
        boolean begin(Vector2f cursor);
        void drag(Vector2f cursor);
        void end(Vector2f cursor);
        void cancel();
    }

    private static final float DRAG_THRESHOLD = 6;
    private final InputManager input;
    private final OrbitCamera orbit;
    private final Consumer<Vector2f> click;
    private final DragHandler dragHandler;
    private final List<InputEvent> events = new ArrayList<>();
    private boolean pressed;
    private boolean dragging;
    private boolean shipDragging;
    private Vector2f pressPosition;
    private float pendingX;
    private float pendingY;

    public PointerInput(InputManager input, OrbitCamera orbit, Consumer<Vector2f> click) {
        this(input, orbit, click, null);
    }

    public PointerInput(InputManager input, OrbitCamera orbit, Consumer<Vector2f> click,
                        DragHandler dragHandler) {
        this.input = input;
        this.orbit = orbit;
        this.click = click;
        this.dragHandler = dragHandler;
        input.addRawInputListener(this);
    }

    @Override public void beginInput() { events.clear(); }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { events.add(event); }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) { events.add(event); }
    @Override public void onKeyEvent(KeyInputEvent event) { events.add(event); }

    @Override public void endInput() {
        // GLFW drains movement before buttons; restore their actual order within a frame.
        events.sort(Comparator.comparingLong(InputEvent::getTime));
        for (InputEvent event : events) {
            if (event instanceof MouseButtonEvent button && button.getButtonIndex() == MouseInput.BUTTON_LEFT) {
                if (button.isPressed()) {
                    pressed = true;
                    dragging = false;
                    shipDragging = false;
                    pressPosition = new Vector2f(button.getX(), button.getY());
                    pendingX = pendingY = 0;
                } else {
                    boolean wasClick = pressed && !dragging;
                    if (shipDragging) dragHandler.end(new Vector2f(button.getX(), button.getY()));
                    resetPointer();
                    if (wasClick) click.accept(new Vector2f(button.getX(), button.getY()));
                }
            } else if (event instanceof MouseMotionEvent motion) {
                if (motion.getDeltaWheel() != 0) orbit.zoom(motion.getDeltaWheel());
                if (!pressed) continue;
                if (dragging) {
                    if (shipDragging) dragHandler.drag(new Vector2f(motion.getX(), motion.getY()));
                    else orbit.orbit(motion.getDX(), motion.getDY());
                } else {
                    pendingX += motion.getDX();
                    pendingY += motion.getDY();
                    if (pendingX * pendingX + pendingY * pendingY >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                        dragging = true;
                        shipDragging = dragHandler != null && dragHandler.begin(pressPosition);
                        if (shipDragging) dragHandler.drag(new Vector2f(motion.getX(), motion.getY()));
                        else orbit.orbit(pendingX, pendingY);
                    }
                }
            } else if (event instanceof KeyInputEvent key && key.getKeyCode() == KeyInput.KEY_R
                    && key.isPressed() && !key.isRepeating()) {
                cancelDrag();
                orbit.reset();
            }
        }
    }

    private void cancelDrag() {
        if (shipDragging) dragHandler.cancel();
        resetPointer();
    }

    private void resetPointer() {
        pressed = dragging = shipDragging = false;
        input.setCursorVisible(true);
    }

    @Override public void close() {
        input.removeRawInputListener(this);
        cancelDrag();
    }
}
