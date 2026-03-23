package com.github.darksoulq.auto_transport.item;

import nl.mtvehicles.core.events.VehicleEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public class CustomItemListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        CustomItem item = CustomItemRegistry.getFromStack(event.getItem());
        if (item != null) item.onInteract(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        CustomItem item = CustomItemRegistry.getFromStack(event.getPlayer().getInventory().getItem(event.getHand()));
        if (item != null) item.onInteractEntity(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrop(PlayerDropItemEvent event) {
        CustomItem item = CustomItemRegistry.getFromStack(event.getItemDrop().getItemStack());
        if (item != null) item.onDrop(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHold(PlayerItemHeldEvent event) {
        ItemStack prev = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        CustomItem item = CustomItemRegistry.getFromStack(prev);
        if (item != null) item.onHold(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        CustomItem item = CustomItemRegistry.getFromStack(hand);
        if (item != null) item.onVehicleEnter(event);
    }
}