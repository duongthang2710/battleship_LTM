package com.battleship.frontend.input;

import com.battleship.frontend.game.GameController;
import com.jme3.input.*;
import com.jme3.input.event.*;
import com.jme3.math.Vector2f;
import java.util.*;

/** Consumes strokes on the designer grid before the orbit/ship drag listener. */
public final class ShipDrawingInput extends RawInputListenerAdapter implements AutoCloseable {
    private final InputManager input;
    private final GameController game;
    private final List<InputEvent> events = new ArrayList<>();
    private int capturedButton = -1;
    public ShipDrawingInput(InputManager input, GameController game) { this.input = input; this.game = game; input.addRawInputListener(this); }
    @Override public void beginInput() { events.clear(); }
    @Override public void onMouseButtonEvent(MouseButtonEvent event) { events.add(event); }
    @Override public void onMouseMotionEvent(MouseMotionEvent event) { events.add(event); }
    @Override public void endInput() {
        events.sort(Comparator.comparingLong(InputEvent::getTime));
        for (var event : events) {
            if (event instanceof MouseButtonEvent button) {
                if (button.isPressed() && capturedButton < 0 && (button.getButtonIndex() == MouseInput.BUTTON_LEFT || button.getButtonIndex() == MouseInput.BUTTON_RIGHT)) {
                    if (game.beginDrawing(new Vector2f(button.getX(), button.getY()), button.getButtonIndex() == MouseInput.BUTTON_RIGHT)) capturedButton = button.getButtonIndex();
                }
                if (capturedButton >= 0) {
                    event.setConsumed();
                    if (!button.isPressed() && button.getButtonIndex() == capturedButton) {
                        game.drawAt(new Vector2f(button.getX(), button.getY()), capturedButton == MouseInput.BUTTON_RIGHT);
                        game.endDrawing(); capturedButton = -1;
                    }
                }
            } else if (event instanceof MouseMotionEvent motion && capturedButton >= 0) {
                event.setConsumed(); game.drawAt(new Vector2f(motion.getX(), motion.getY()), capturedButton == MouseInput.BUTTON_RIGHT);
            }
        }
    }
    @Override public void close() { input.removeRawInputListener(this); game.cancelDrawingStroke(); }
}
