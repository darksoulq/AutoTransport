package com.github.darksoulq.auto_transport.multiblock;

import com.MT.triggersUtility.TUMaths;
import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.MineMain;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiblockManager {

    private static final NamespacedKey PDC_KEY = new NamespacedKey(MineMain.getPlugin(), "multiblock_mapping");
    private static final NamespacedKey FACING_KEY = new NamespacedKey(MineMain.getPlugin(), "multiblock_facing");

    private static final Map<Location, Location> secondaryToCore = new ConcurrentHashMap<>();
    private static final Map<Location, BlockFace> coreToFacing = new ConcurrentHashMap<>();
    private static Field allDevicesField;

    static {
        try {
            allDevicesField = Device.class.getDeclaredField("allDevices");
            allDevicesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectDevice(Location secondary, Device core) {
        try {
            HashMap<Location, Device> allDevices = (HashMap<Location, Device>) allDevicesField.get(null);
            allDevices.put(secondary, core);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void removeInjectedDevice(Location secondary) {
        try {
            HashMap<Location, Device> allDevices = (HashMap<Location, Device>) allDevicesField.get(null);
            allDevices.remove(secondary);
        } catch (Exception ignored) {}
    }

    public static void registerSecondaryBlocks(MultiblockDevice device) {
        Location core = device.getLocationREADONLY();
        BlockFace facing = device.getFacing();

        for (Vector rel : device.getStructureMap(null).keySet()) {
            Vector rotated = RotationUtil.rotateVector(rel, facing);
            Location target = core.clone().add(rotated);
            secondaryToCore.put(target, core);
            injectDevice(target, device);
        }
    }

    public static BlockFace getStoredFacing(Location coreLoc) {
        if (coreLoc == null) return null;
        BlockFace face = coreToFacing.get(coreLoc);
        if (face != null) return face;

        if (coreLoc.isWorldLoaded()) {
            loadChunk(coreLoc.getChunk());
            return coreToFacing.get(coreLoc);
        }

        return null;
    }

    public static void loadChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        if (pdc.has(FACING_KEY, PersistentDataType.STRING)) {
            String fData = pdc.get(FACING_KEY, PersistentDataType.STRING);
            if (fData != null && !fData.isEmpty()) {
                for (String entry : fData.split(";")) {
                    String[] parts = entry.split("=");
                    if (parts.length == 2) {
                        Location core = TUMaths.getStringLocation(parts[0]);
                        BlockFace face = BlockFace.valueOf(parts[1]);
                        if (core != null) {
                            coreToFacing.put(core, face);
                        }
                    }
                }
            }
        }

        if (pdc.has(PDC_KEY, PersistentDataType.STRING)) {
            String data = pdc.get(PDC_KEY, PersistentDataType.STRING);
            if (data != null && !data.isEmpty()) {
                for (String entry : data.split(";")) {
                    String[] parts = entry.split("=");
                    if (parts.length == 2) {
                        Location sec = TUMaths.getStringLocation(parts[0]);
                        Location core = TUMaths.getStringLocation(parts[1]);
                        if (sec != null && core != null) {
                            secondaryToCore.put(sec, core);
                            Bukkit.getScheduler().runTaskLater(MineMain.getPlugin(), () -> {
                                Device coreDev = Device.getDevice(core);
                                if (coreDev != null) {
                                    injectDevice(sec, coreDev);
                                }
                            }, 5L);
                        }
                    }
                }
            }
        }
    }

    public static void saveChunk(Chunk chunk) {
        StringBuilder sb = new StringBuilder();
        boolean hasData = false;
        int cx = chunk.getX();
        int cz = chunk.getZ();
        String worldName = chunk.getWorld().getName();

        for (Map.Entry<Location, Location> entry : secondaryToCore.entrySet()) {
            Location loc = entry.getKey();
            if ((loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz && loc.getWorld().getName().equals(worldName)) {
                sb.append(TUMaths.locationString(loc)).append("=")
                    .append(TUMaths.locationString(entry.getValue())).append(";");
                hasData = true;
            }
        }

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (hasData) {
            pdc.set(PDC_KEY, PersistentDataType.STRING, sb.toString());
        } else {
            pdc.remove(PDC_KEY);
        }

        StringBuilder fsb = new StringBuilder();
        boolean hasFData = false;

        for (Map.Entry<Location, BlockFace> entry : coreToFacing.entrySet()) {
            Location loc = entry.getKey();
            if ((loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz && loc.getWorld().getName().equals(worldName)) {
                fsb.append(TUMaths.locationString(loc)).append("=")
                    .append(entry.getValue().name()).append(";");
                hasFData = true;
            }
        }

        if (hasFData) {
            pdc.set(FACING_KEY, PersistentDataType.STRING, fsb.toString());
        } else {
            pdc.remove(FACING_KEY);
        }
    }

    public static boolean isSecondaryBlock(Location loc) {
        if (loc == null) return false;
        if (secondaryToCore.containsKey(loc)) return true;

        if (loc.isWorldLoaded()) {
            loadChunk(loc.getChunk());
            return secondaryToCore.containsKey(loc);
        }
        return false;
    }

    public static Device getCoreDevice(Location loc) {
        if (loc == null) return null;
        Location coreLoc = secondaryToCore.get(loc);

        if (coreLoc == null && loc.isWorldLoaded()) {
            loadChunk(loc.getChunk());
            coreLoc = secondaryToCore.get(loc);
        }

        return coreLoc != null ? Device.getDevice(coreLoc) : null;
    }

    public static boolean isValidPlacement(MultiblockDevice device, PlacementContext context) {
        Location core = context != null ? context.coreLocation : device.getLocationREADONLY();
        BlockFace facing = context != null ? context.facing : device.getFacing();

        for (Map.Entry<Vector, BlockData> entry : device.getStructureMap(context).entrySet()) {
            Vector rotated = RotationUtil.rotateVector(entry.getKey(), facing);
            Location target = core.clone().add(rotated);

            if (!target.getBlock().getType().isAir() && !target.getBlock().isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    public static boolean validateAndBuild(MultiblockDevice device, PlacementContext context) {
        Location core = context.coreLocation;
        BlockFace facing = context.facing;

        if (!isValidPlacement(device, context)) {
            return false;
        }

        coreToFacing.put(core, facing);

        BlockData coreData = device.getMaterial().createBlockData();
        if (coreData instanceof Directional dir) {
            dir.setFacing(facing);
            core.getBlock().setBlockData(coreData);
        } else {
            coreData = RotationUtil.rotateBlockData(coreData, facing);
            core.getBlock().setBlockData(coreData);
        }

        for (Map.Entry<Vector, BlockData> entry : device.getStructureMap(context).entrySet()) {
            Vector rotated = RotationUtil.rotateVector(entry.getKey(), facing);
            Location target = core.clone().add(rotated);
            BlockData rotatedData = RotationUtil.rotateBlockData(entry.getValue(), facing);

            target.getBlock().setBlockData(rotatedData);
            secondaryToCore.put(target, core);
            injectDevice(target, device);
        }

        saveChunk(core.getChunk());
        return true;
    }

    public static void removeSecondary(Location loc) {
        secondaryToCore.remove(loc);
        removeInjectedDevice(loc);
        saveChunk(loc.getChunk());
    }

    public static void removeStructure(MultiblockDevice device) {
        Location core = device.getLocationREADONLY();
        BlockFace facing = device.getFacing();

        core.getBlock().setType(Material.AIR);
        coreToFacing.remove(core);

        for (Vector rel : device.getStructureMap(null).keySet()) {
            Vector rotated = RotationUtil.rotateVector(rel, facing);
            Location target = core.clone().add(rotated);

            secondaryToCore.remove(target);
            removeInjectedDevice(target);
            target.getBlock().setType(Material.AIR);
            saveChunk(target.getChunk());
        }

        saveChunk(core.getChunk());
    }
}