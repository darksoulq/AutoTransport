package com.github.darksoulq.auto_transport.device.display;

public class ModelPart {

    private final String id;
    private final VisualPart visual;
    private final Animation animation;

    public ModelPart(String id, VisualPart visual, Animation animation) {
        this.id = id;
        this.visual = visual;
        this.animation = animation;
    }

    public String getId() { return id; }
    public VisualPart getVisual() { return visual; }
    public Animation getAnimation() { return animation; }
}