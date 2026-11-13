package com.github.darksoulq.auto_transport.choice;

import com.github.darksoulq.mt_multiblocks.choice.ComponentChoice;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.io.Serial;

public class AnySolidChoice implements ComponentChoice {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient BlockData displayCache;
    private transient BlockState nmsDisplayCache;

    public AnySolidChoice() {
        initCache();
    }

    private void initCache() {
        this.displayCache = Bukkit.createBlockData(Material.GLASS);
        this.nmsDisplayCache = ((CraftBlockData) this.displayCache).getState();
    }

    @Override
    public boolean matches(BlockData blockData, BlockFace facing) {
        return blockData.getMaterial().isSolid() && blockData.getMaterial() != Material.BARRIER;
    }

    @Override
    public BlockData getDisplayData(BlockFace facing) {
        if (displayCache == null) initCache();
        return displayCache;
    }

    @Override
    public BlockState getNMSDisplayData(BlockFace facing) {
        if (nmsDisplayCache == null) initCache();
        return nmsDisplayCache;
    }
}