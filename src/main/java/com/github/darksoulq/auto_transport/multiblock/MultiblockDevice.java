package com.github.darksoulq.auto_transport.multiblock;

import com.MT.xxxtrigger50xxx.Devices.Device;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.util.Vector;

import java.util.Map;

public abstract class MultiblockDevice extends Device {

    public MultiblockDevice(Location loc) {
        super(loc);
    }

    public abstract Map<Vector, BlockData> getStructureMap();

    @Override
    public void postCreate(boolean isPlaced) {
        super.postCreate(isPlaced);
        if (!isPlaced) {
            MultiblockManager.registerSecondaryBlocks(this);
        }
    }

    @Override
    public BlockFace getFacing() {
        BlockData data = this.getLocationREADONLY().getBlock().getBlockData();
        if (data instanceof Directional dir) {
            return dir.getFacing();
        }
        BlockFace stored = MultiblockManager.getStoredFacing(this.getLocationREADONLY());
        return stored != null ? stored : BlockFace.NORTH;
    }
}