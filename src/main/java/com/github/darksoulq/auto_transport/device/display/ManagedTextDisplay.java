package com.github.darksoulq.auto_transport.device.display;

import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

public class ManagedTextDisplay {
    private TextDisplay display;
    private final NamespacedKey key;

    public ManagedTextDisplay(String id) {
        this.key = new NamespacedKey(MineMain.getPlugin(), "mtd_" + id);
    }

    public void update(Location loc, String text, Transformation trans, org.bukkit.Color bgColor) {
        if (display == null || !display.isValid()) {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    if (display == null) {
                        display = td;
                    } else {
                        td.remove();
                    }
                }
            }
            
            if (display == null || !display.isValid()) {
                display = loc.getWorld().spawn(loc, TextDisplay.class);
                display.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                display.setBillboard(Display.Billboard.FIXED);
                display.setCustomNameVisible(false);
                DisplayCullingManager.track(display);
            }
        }

        if (!display.getLocation().equals(loc)) display.teleport(loc);
        if (!text.equals(display.getText())) display.setText(text);
        if (bgColor != null && !bgColor.equals(display.getBackgroundColor())) display.setBackgroundColor(bgColor);
        if (!display.getTransformation().equals(trans)) display.setTransformation(trans);
    }

    public void remove() {
        if (display != null && display.isValid()) {
            DisplayCullingManager.untrack(display);
            display.remove();
            display = null;
        }
    }
}