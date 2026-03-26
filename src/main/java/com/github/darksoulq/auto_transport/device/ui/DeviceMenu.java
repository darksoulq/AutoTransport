package com.github.darksoulq.auto_transport.device.ui;

import com.MT.xxxtrigger50xxx.Devices.Device;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public abstract class DeviceMenu {

    protected final Device device;
    private final Map<Integer, SlotAction> actions = new HashMap<>();
    private SlotAction fallbackAction = null;

    public DeviceMenu(Device device) {
        this.device = device;
        setup();
    }

    public abstract void setup();

    public abstract void render();

    protected void bind(int slot, SlotAction action) {
        actions.put(slot, action);
    }

    protected void bindFallback(SlotAction action) {
        this.fallbackAction = action;
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(device.getInventory())) {
            SlotAction action = actions.get(event.getSlot());
            
            if (action != null) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    action.onClick(event, player);
                }
            } else if (fallbackAction != null) {
                if (event.getWhoClicked() instanceof Player player) {
                    fallbackAction.onClick(event, player);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @FunctionalInterface
    public interface SlotAction {
        void onClick(InventoryClickEvent event, Player player);
    }
}