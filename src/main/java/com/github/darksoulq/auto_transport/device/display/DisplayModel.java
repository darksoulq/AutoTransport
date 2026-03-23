package com.github.darksoulq.auto_transport.device.display;

import java.util.HashMap;
import java.util.Map;

public class DisplayModel {

    private final Map<String, ModelState> states = new HashMap<>();

    public static DisplayModel create() {
        return new DisplayModel();
    }

    public DisplayModel withState(ModelState state) {
        states.put(state.getName(), state);
        return this;
    }

    public ModelState getState(String name) {
        return states.get(name);
    }
}