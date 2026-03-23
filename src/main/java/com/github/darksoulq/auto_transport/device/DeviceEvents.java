package com.github.darksoulq.auto_transport.device;

import com.github.darksoulq.auto_transport.device.display.ComplexDisplay;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplayManager;
import com.github.darksoulq.auto_transport.device.display.DisplayCullingManager;
import com.github.darksoulq.auto_transport.device.traits.InteractiveDevice;
import com.github.darksoulq.auto_transport.multiblock.MultiblockDevice;
import com.github.darksoulq.auto_transport.multiblock.MultiblockManager;
import com.github.darksoulq.auto_transport.multiblock.MultiblockPreviewManager;
import com.github.darksoulq.auto_transport.multiblock.RotationUtil;
import com.MT.xxxtrigger50xxx.API.MTEvents.BreakDeviceEvent;
import com.MT.xxxtrigger50xxx.API.MTEvents.PlaceDeviceEvent;
import com.MT.xxxtrigger50xxx.Devices.Device;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class DeviceEvents implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDevicePlace(PlaceDeviceEvent event) {
        if (event.getDevice() instanceof MultiblockDevice mbDevice) {
            BlockFace facing = RotationUtil.getPlacementFacing(event.getPlayer());
            if (!MultiblockManager.validateAndBuild(mbDevice, facing)) {
                event.setCancelled(true);
                event.setReason("Not enough space to assemble multiblock structure.");
                return;
            } else {
                MultiblockPreviewManager.clearPreview(event.getPlayer().getUniqueId());
            }
        }
        
        if (event.getDevice() instanceof ComplexDisplay cd) {
            ComplexDisplayManager.sync(cd, (Device) cd, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeviceBreak(BreakDeviceEvent event) {
        if (event.getDevice() instanceof ComplexDisplay cd) {
            ComplexDisplayManager.clear(cd);
        }
        if (event.getDevice() instanceof MultiblockDevice mb) {
            MultiblockManager.removeStructure(mb);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (MultiblockManager.isSecondaryBlock(event.getBlock().getLocation())) {
            Device core = MultiblockManager.getCoreDevice(event.getBlock().getLocation());
            if (core != null) {
                event.setCancelled(true);
                
                BreakDeviceEvent breakEvent = new BreakDeviceEvent(core, event.getPlayer());
                Bukkit.getPluginManager().callEvent(breakEvent);

                if (!breakEvent.isCancelled()) {
                    core.onDeviceRemove();
                    core.cleanup();
                    core.iconCleanup();
                    core.removeDevice();
                    core.dropInventory();
                    core.getInventory().clear();
                    event.getBlock().getWorld().dropItemNaturally(core.getLocationREADONLY().add(0.5, 0.5, 0.5), core.getDeviceStack());

                    if (core instanceof ComplexDisplay cd) {
                        ComplexDisplayManager.clear(cd);
                    }
                    if (core instanceof MultiblockDevice mb) {
                        MultiblockManager.removeStructure(mb);
                    }
                    core.getLocationREADONLY().getBlock().setType(Material.AIR);
                }
            } else {
                event.getBlock().setType(Material.AIR);
                MultiblockManager.removeSecondary(event.getBlock().getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null) {
                boolean isSecondary = MultiblockManager.isSecondaryBlock(event.getClickedBlock().getLocation());
                Device core = MultiblockManager.getCoreDevice(event.getClickedBlock().getLocation());
                if (core == null) core = Device.getDevice(event.getClickedBlock().getLocation());

                if (core != null) {
                    if (core instanceof InteractiveDevice interactive) {
                        if (interactive.onInteract(event)) {
                            event.setCancelled(true);
                            return;
                        }
                    }

                    if (isSecondary) {
                        event.setCancelled(true);
                        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                            if (core.isUseUI() && !event.getPlayer().isSneaking()) {
                                core.updateUI();
                                core.openDeviceInventory(event.getPlayer());
                            } else if (!event.getPlayer().isSneaking()) {
                                core.onRightClick(event);
                            }
                        } else if (!event.getPlayer().isSneaking()) {
                            core.onLeftClick(event);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperSearch(HopperInventorySearchEvent event) {
        if (event.getSearchBlock() != null && MultiblockManager.isSecondaryBlock(event.getSearchBlock().getLocation())) {
            Device core = MultiblockManager.getCoreDevice(event.getSearchBlock().getLocation());
            if (core != null && core.getInventory() != null) {
                event.setInventory(core.getInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event.getDestination().getLocation() != null && MultiblockManager.isSecondaryBlock(event.getDestination().getLocation())) {
            Device core = MultiblockManager.getCoreDevice(event.getDestination().getLocation());
            if (core != null && core.getInventory() != null) {
                HashMap<Integer, ItemStack> leftover = core.getInventory().addItem(event.getItem());
                if (leftover.isEmpty()) {
                    event.setItem(new ItemStack(Material.AIR));
                } else {
                    event.setItem(leftover.values().iterator().next());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (MultiblockManager.isSecondaryBlock(block.getLocation())) {
                Device core = MultiblockManager.getCoreDevice(block.getLocation());
                if (core != null) {
                    core.dropInventory();
                    block.getWorld().dropItemNaturally(core.getLocation().add(0.5, 0.0, 0.5), core.getDeviceStack());
                    core.removeDevice();
                    core.getLocationREADONLY().getBlock().setType(Material.AIR);

                    if (core instanceof ComplexDisplay cd) {
                        ComplexDisplayManager.clear(cd);
                    }
                    if (core instanceof MultiblockDevice mb) {
                        MultiblockManager.removeStructure(mb);
                    }
                }
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        MultiblockManager.loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        MultiblockManager.saveChunk(event.getChunk());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MultiblockPreviewManager.clearPreview(event.getPlayer().getUniqueId());
        DisplayCullingManager.clearPlayer(event.getPlayer().getUniqueId());
    }
}