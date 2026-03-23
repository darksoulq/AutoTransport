package com.github.darksoulq.auto_transport.item;

import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import nl.mtvehicles.core.events.VehicleEnterEvent;

public interface CustomItem {
    String getId();
    ItemStack create(Object... context);
    
    default void onInteract(PlayerInteractEvent event) {}
    default void onInteractEntity(PlayerInteractEntityEvent event) {}
    default void onDrop(PlayerDropItemEvent event) {}
    default void onHold(PlayerItemHeldEvent event) {}
    default void onVehicleEnter(VehicleEnterEvent event) {}
}