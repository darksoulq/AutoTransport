package com.github.darksoulq.auto_transport.device.display;

import java.util.ArrayList;
import java.util.List;

public class ModelState {

    private final String name;
    private final List<ModelPart> parts = new ArrayList<>();

    private ModelState(String name) {
        this.name = name;
    }

    public static ModelState create(String name) {
        return new ModelState(name);
    }

    public ModelState add(String id, VisualPart visual) {
        this.parts.add(new ModelPart(id, visual, null));
        return this;
    }

    public ModelState add(String id, VisualPart visual, Animation animation) {
        this.parts.add(new ModelPart(id, visual, animation));
        return this;
    }

    public String getName() { return name; }
    public List<ModelPart> getParts() { return parts; }

    public ModelPart getPart(String id) {
        for (ModelPart p : parts) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }
}