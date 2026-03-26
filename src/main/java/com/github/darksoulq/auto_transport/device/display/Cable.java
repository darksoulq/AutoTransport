package com.github.darksoulq.auto_transport.device.display;

import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Cable {

    private final List<BlockDisplay> segments = new ArrayList<>();
    private final int resolution;
    private final NamespacedKey key;

    public Cable(String id, int resolution) {
        this.key = new NamespacedKey(MineMain.getPlugin(), "cbl_" + id);
        this.resolution = resolution;
    }

    public void clearGhosts(Location center) {
        for (Entity e : center.getWorld().getNearbyEntities(center, 15.0, 15.0, 15.0)) {
            if (e instanceof BlockDisplay bd && bd.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                bd.remove();
            }
        }
    }

    public void update(Location start, Location end, double sag) {
        if (start == null || end == null || !start.getWorld().equals(end.getWorld())) {
            remove();
            return;
        }

        while (segments.size() < resolution) {
            BlockDisplay display = start.getWorld().spawn(start, BlockDisplay.class);
            display.setBlock(Material.BLACK_CONCRETE.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            DisplayCullingManager.track(display);
            segments.add(display);
        }

        Vector p0 = start.toVector();
        Vector p2 = end.toVector();
        Vector p1 = p0.clone().add(p2).multiply(0.5);
        p1.setY(p1.getY() - (p0.distance(p2) * sag));

        for (double t = 0.2; t <= 0.8; t += 0.2) {
            Vector test = getBezierPoint(p0, p1, p2, t);
            Location testLoc = test.toLocation(start.getWorld());
            if (testLoc.getBlock().getType().isSolid()) {
                p1.setY(Math.max(p1.getY(), testLoc.getBlockY() + 1.5));
            }
        }

        Vector prev = p0;
        for (int i = 0; i < resolution; i++) {
            double t = (double) (i + 1) / resolution;
            Vector curr = getBezierPoint(p0, p1, p2, t);

            Location loc = prev.toLocation(start.getWorld());
            Vector dir = curr.clone().subtract(prev);
            double len = dir.length();
            if (len < 0.01) len = 0.01;
            loc.setDirection(dir);

            BlockDisplay display = segments.get(i);

            Transformation trans = display.getTransformation();
            trans.getScale().set(0.04f, 0.04f, (float) len);
            trans.getTranslation().set(-0.02f, -0.02f, 0f);

            display.setInterpolationDelay(0);
            display.setInterpolationDuration(3);
            display.setTransformation(trans);

            display.setTeleportDuration(3);
            display.teleport(loc);

            prev = curr;
        }
    }

    private Vector getBezierPoint(Vector p0, Vector p1, Vector p2, double t) {
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        Vector point = p0.clone().multiply(uu);
        point.add(p1.clone().multiply(2 * u * t));
        point.add(p2.clone().multiply(tt));
        return point;
    }

    public void remove() {
        for (BlockDisplay display : segments) {
            if (display.isValid()) {
                DisplayCullingManager.untrack(display);
                display.remove();
            }
        }
        segments.clear();
    }
}