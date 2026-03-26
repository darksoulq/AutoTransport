package com.github.darksoulq.auto_transport.multiblock;

import com.MT.xxxtrigger50xxx.MineItems;
import com.MT.xxxtrigger50xxx.MineMain;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplay;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplayManager;
import com.github.darksoulq.auto_transport.device.display.DisplayModel;
import com.github.darksoulq.auto_transport.device.display.ModelPart;
import com.github.darksoulq.auto_transport.device.display.ModelState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MultiblockPreviewManager {

    private static final Map<UUID, List<Display>> activePreviews = new HashMap<>();
    private static final Map<UUID, Location> lastPreviewLocations = new HashMap<>();
    private static final Map<UUID, BlockFace> lastPreviewFacings = new HashMap<>();
    private static int taskId = -1;

    public static void start() {
        if (taskId != -1) return;

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MineMain.getPlugin(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                handlePlayerPreview(p);
            }
        }, 0L, 2L);
    }

    public static void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
            for (UUID uuid : new ArrayList<>(activePreviews.keySet())) {
                clearPreview(uuid);
            }
        }
    }

    private static void handlePlayerPreview(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand.getType().isAir() || !hand.hasItemMeta()) {
            clearPreview(p.getUniqueId());
            return;
        }

        String deviceName;
        try {
            deviceName = MineItems.getMTItemKey(hand);
        } catch (Exception e) {
            clearPreview(p.getUniqueId());
            return;
        }

        if (deviceName == null || !CustomDeviceRegistry.multiblockDummies.containsKey(deviceName)) {
            clearPreview(p.getUniqueId());
            return;
        }

        RayTraceResult ray = p.rayTraceBlocks(5.0);
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            clearPreview(p.getUniqueId());
            return;
        }

        Location coreLoc = ray.getHitBlock().getLocation().add(ray.getHitBlockFace().getDirection());
        BlockFace facing = RotationUtil.getPlacementFacing(p);
        PlacementContext ctx = new PlacementContext(p, coreLoc, facing, hand);

        if (lastPreviewLocations.containsKey(p.getUniqueId())) {
            Location lastLoc = lastPreviewLocations.get(p.getUniqueId());
            BlockFace lastFace = lastPreviewFacings.get(p.getUniqueId());
            if (lastLoc.equals(coreLoc) && lastFace == facing) {
                updateGlowColors(p.getUniqueId(), CustomDeviceRegistry.multiblockDummies.get(deviceName), ctx);
                return;
            }
        }

        buildPreview(p.getUniqueId(), CustomDeviceRegistry.multiblockDummies.get(deviceName), ctx);
        lastPreviewLocations.put(p.getUniqueId(), coreLoc);
        lastPreviewFacings.put(p.getUniqueId(), facing);
    }

    private static void buildPreview(UUID uuid, MultiblockDevice dummy, PlacementContext ctx) {
        clearPreview(uuid);
        List<Display> displays = new ArrayList<>();
        boolean isValid = MultiblockManager.isValidPlacement(dummy, ctx);
        Color glowColor = isValid ? Color.LIME : Color.RED;

        Location coreLoc = ctx.coreLocation;
        BlockFace facing = ctx.facing;

        BlockData coreData = dummy.getMaterial().createBlockData();
        if (coreData instanceof Directional dir) {
            dir.setFacing(facing);
        } else {
            coreData = RotationUtil.rotateBlockData(coreData, facing);
        }

        BlockDisplay coreDisplay = (BlockDisplay) coreLoc.getWorld().spawnEntity(coreLoc, EntityType.BLOCK_DISPLAY);
        coreDisplay.setBlock(coreData);
        coreDisplay.setGlowing(true);
        coreDisplay.setGlowColorOverride(glowColor);
        displays.add(coreDisplay);

        for (Map.Entry<Vector, BlockData> entry : dummy.getStructureMap(ctx).entrySet()) {
            Vector rotated = RotationUtil.rotateVector(entry.getKey(), facing);
            Location target = coreLoc.clone().add(rotated);
            BlockData rotatedData = RotationUtil.rotateBlockData(entry.getValue(), facing);

            BlockDisplay display = (BlockDisplay) target.getWorld().spawnEntity(target, EntityType.BLOCK_DISPLAY);
            display.setBlock(rotatedData);
            display.setGlowing(true);
            display.setGlowColorOverride(glowColor);
            displays.add(display);
        }

        if (dummy instanceof ComplexDisplay cd) {
            DisplayModel model = cd.getDisplayModel(ctx);
            if (model != null) {
                ModelState state = model.getState(cd.getActiveState());
                if (state != null) {
                    for (ModelPart part : state.getParts()) {
                        Display display = null;
                        Vector rotatedOffset = RotationUtil.rotateVector(part.getVisual().getOffset(), facing);
                        Location target = coreLoc.clone().add(0.5, 0.0, 0.5).add(rotatedOffset);
                        target.setYaw(RotationUtil.getYawFromFace(facing));

                        if (part.getVisual().getItem() != null) {
                            ItemDisplay itemD = (ItemDisplay) target.getWorld().spawnEntity(target, EntityType.ITEM_DISPLAY);
                            itemD.setItemStack(part.getVisual().getItem());
                            display = itemD;
                        } else if (part.getVisual().getBlock() != null) {
                            BlockDisplay blockD = (BlockDisplay) target.getWorld().spawnEntity(target, EntityType.BLOCK_DISPLAY);
                            blockD.setBlock(RotationUtil.rotateBlockData(part.getVisual().getBlock(), facing));
                            display = blockD;
                        }

                        if (display != null) {
                            ComplexDisplayManager.applyTransform(display, part.getVisual(), null, facing, 0, coreLoc);
                            display.setGlowing(true);
                            display.setGlowColorOverride(glowColor);
                            display.setBrightness(new Display.Brightness(15, 15));
                            displays.add(display);
                        }
                    }
                }
            }
        }

        activePreviews.put(uuid, displays);
    }

    private static void updateGlowColors(UUID uuid, MultiblockDevice dummy, PlacementContext ctx) {
        List<Display> displays = activePreviews.get(uuid);
        if (displays == null) return;

        boolean isValid = MultiblockManager.isValidPlacement(dummy, ctx);
        Color glowColor = isValid ? Color.LIME : Color.RED;

        for (Display display : displays) {
            if (display.isValid() && !Objects.equals(display.getGlowColorOverride(), glowColor)) {
                display.setGlowColorOverride(glowColor);
            }
        }
    }

    public static void clearPreview(UUID uuid) {
        List<Display> displays = activePreviews.remove(uuid);
        if (displays != null) {
            for (Display d : displays) {
                if (d.isValid()) d.remove();
            }
        }
        lastPreviewLocations.remove(uuid);
        lastPreviewFacings.remove(uuid);
    }
}