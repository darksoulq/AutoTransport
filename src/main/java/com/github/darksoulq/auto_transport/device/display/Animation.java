package com.github.darksoulq.auto_transport.device.display;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Animation {

    private boolean loop = false;
    private boolean playing = true;
    private final List<Keyframe> frames = new ArrayList<>();

    public static Animation create() {
        return new Animation();
    }

    public Animation loop() {
        this.loop = true;
        return this;
    }

    public Animation play() {
        this.playing = true;
        return this;
    }

    public Animation pause() {
        this.playing = false;
        return this;
    }

    public Animation setPlaying(boolean playing) {
        this.playing = playing;
        return this;
    }

    public boolean isPlaying() {
        return playing;
    }

    public Animation keyframe(int durationTicks, Vector translation, Vector rotation, Vector scale) {
        this.frames.add(new Keyframe(translation, rotation, scale, durationTicks));
        return this;
    }

    public Animation keyframeRot(int durationTicks, double rx, double ry, double rz) {
        return keyframe(durationTicks, new Vector(0, 0, 0), new Vector(rx, ry, rz), new Vector(1, 1, 1));
    }

    public boolean isLoop() {
        return loop;
    }

    public List<Keyframe> getFrames() {
        return frames;
    }
}