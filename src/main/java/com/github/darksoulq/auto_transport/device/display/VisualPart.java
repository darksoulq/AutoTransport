package com.github.darksoulq.auto_transport.device.display;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class VisualPart {

    private ItemStack item;
    private BlockData block;
    private Vector offset = new Vector(0, 0, 0);
    private Vector rotationDegrees = new Vector(0, 0, 0);
    private Vector scale = new Vector(1, 1, 1);

    private VisualPart() {}

    public static VisualPart item(ItemStack item) {
        VisualPart part = new VisualPart();
        part.item = item;
        return part;
    }

    public static VisualPart block(BlockData block) {
        VisualPart part = new VisualPart();
        part.block = block;
        return part;
    }

    public VisualPart offset(double x, double y, double z) {
        this.offset = new Vector(x, y, z);
        return this;
    }

    public VisualPart rotation(double x, double y, double z) {
        this.rotationDegrees = new Vector(x, y, z);
        return this;
    }

    public VisualPart scale(double x, double y, double z) {
        this.scale = new Vector(x, y, z);
        return this;
    }

    public ItemStack getItem() { return item; }
    public BlockData getBlock() { return block; }
    public Vector getOffset() { return offset; }
    public Vector getRotationDegrees() { return rotationDegrees; }
    public Vector getScale() { return scale; }
}