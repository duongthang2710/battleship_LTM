package com.battleship.frontend.game;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.input.PointerInput;
import com.battleship.frontend.game.PlacementHud.Action;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector2f;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameInput implements ActionListener, AutoCloseable {
    private final InputManager input;
    private final GameController controller;
    private final PointerInput pointer;
    private final Map<String, Action> actions = new LinkedHashMap<>();

    public GameInput(InputManager input, GameController controller, OrbitCamera orbit) {
        this.input = input;
        this.controller = controller;
        pointer = new PointerInput(input, orbit, controller::primaryClick, new PointerInput.DragHandler() {
            @Override public boolean begin(Vector2f cursor) {
                return controller.beginShipDrag(cursor);
            }
            @Override public void drag(Vector2f cursor) { controller.dragShip(cursor); }
            @Override public void end(Vector2f cursor) { controller.endShipDrag(cursor); }
            @Override public void cancel() { controller.cancelShipDrag(); }
        });
        bind(Action.SELECT_CARRIER, KeyInput.KEY_1);
        bind(Action.SELECT_BATTLESHIP, KeyInput.KEY_2);
        bind(Action.SELECT_CRUISER, KeyInput.KEY_3);
        bind(Action.SELECT_SUBMARINE, KeyInput.KEY_4);
        bind(Action.SELECT_DESTROYER, KeyInput.KEY_5);
        bind(Action.ROTATE, KeyInput.KEY_T);
        bind(Action.REMOVE, KeyInput.KEY_DELETE);
        bind(Action.RESET, KeyInput.KEY_C);
        bind(Action.RANDOM, KeyInput.KEY_F);
        bind(Action.READY, KeyInput.KEY_RETURN);
        bind(Action.TOGGLE_BOARD, KeyInput.KEY_TAB);
    }

    private void bind(Action action, int key) {
        String name = "Gameplay" + action;
        actions.put(name, action);
        input.addMapping(name, new KeyTrigger(key));
        input.addListener(this, name);
    }

    @Override public void onAction(String name, boolean pressed, float delta) {
        if (!pressed) return;
        if (actions.containsKey(name)) controller.perform(actions.get(name));
    }

    @Override public void close() {
        input.removeListener(this);
        pointer.close();
        for (String name : actions.keySet()) input.deleteMapping(name);
    }
}
