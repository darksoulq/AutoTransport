package com.github.darksoulq.auto_transport.device.traits;

import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public interface InteractiveDevice {
    default boolean onInteract(PlayerInteractEvent event) { return false; }
    default boolean onEntityInteract(PlayerInteractEntityEvent event) { return false; }
}