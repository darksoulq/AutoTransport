package com.github.darksoulq.auto_transport.device.display;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Keyframe {

    private final Vector translation;
    private final Vector rotation;
    private final Vector scale;
    private final int durationTicks;

    public Keyframe(Vector translation, Vector rotation, Vector scale, int durationTicks) {
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
        this.durationTicks = durationTicks;
    }

    public Vector getTranslation() { return translation; }
    public Vector getRotation() { return rotation; }
    public Vector getScale() { return scale; }
    public int getDurationTicks() { return durationTicks; }
}