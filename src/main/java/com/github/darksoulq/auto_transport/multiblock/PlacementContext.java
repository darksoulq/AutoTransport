package com.github.darksoulq.auto_transport.multiblock;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlacementContext {
    public final Player player;
    public final Location coreLocation;
    public final BlockFace facing;
    public final ItemStack item;

    public PlacementContext(Player player, Location coreLocation, BlockFace facing, ItemStack item) {
        this.player = player;
        this.coreLocation = coreLocation;
        this.facing = facing;
        this.item = item;
    }
}