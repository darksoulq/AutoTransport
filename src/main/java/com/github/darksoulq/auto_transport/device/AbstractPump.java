package com.github.darksoulq.auto_transport.device;

import com.github.darksoulq.auto_transport.device.display.Cable;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplay;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplayManager;
import com.github.darksoulq.auto_transport.device.display.DisplayModel;
import com.github.darksoulq.auto_transport.device.display.ManagedTextDisplay;
import com.github.darksoulq.auto_transport.device.items.FuelNozzleItem;
import com.github.darksoulq.auto_transport.device.traits.InteractiveDevice;
import com.github.darksoulq.auto_transport.device.ui.DeviceMenu;
import com.github.darksoulq.auto_transport.item.CustomItemRegistry;
import com.github.darksoulq.auto_transport.multiblock.MultiblockDevice;
import com.github.darksoulq.auto_transport.multiblock.PlacementContext;
import com.github.darksoulq.auto_transport.multiblock.RotationUtil;
import com.github.darksoulq.auto_transport.util.VaultHook;
import com.MT.xxxtrigger50xxx.Devices.Device.LiquidType;
import com.MT.xxxtrigger50xxx.MineUtil;
import com.MT.triggersUtility.TUMaths;
import net.milkbowl.vault.economy.Economy;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public abstract class AbstractPump extends MultiblockDevice implements ComplexDisplay, InteractiveDevice {

    protected double fuelCost = 1.00;
    protected double storedMoney = 0.0;

    protected transient List<PumpNozzle> nozzles = new ArrayList<>();
    protected transient List<Display> activeDisplays = new ArrayList<>();
    protected transient long lastInteract = 0;
    protected transient boolean visualsInitialized = false;
    protected transient DeviceMenu menu;

    public class PumpNozzle implements ComplexDisplay {
        public int index;
        public String connectedVehicle = null;
        public UUID holdingPlayer = null;
        public UUID fuelingPlayer = null;
        public double sessionVolume = 0.0;
        public Cable cable;
        public ManagedTextDisplay mtd;
        public final Vector cableLocalOffset;
        public final Vector textLocalOffset;
        public final float textScale;
        private transient List<Display> spawnedDisplays = new ArrayList<>();

        public PumpNozzle(Vector cableLocalOffset, Vector textLocalOffset, float textScale) {
            this.cableLocalOffset = cableLocalOffset;
            this.textLocalOffset = textLocalOffset;
            this.textScale = textScale;
        }

        public boolean isInUse() {
            return holdingPlayer != null || connectedVehicle != null;
        }

        public void removeDisplays() {
            if (cable != null) cable.remove();
            if (mtd != null) mtd.remove();
        }

        @Override
        public DisplayModel getDisplayModel(PlacementContext context) {
            return getNozzleDisplayModel(index, context);
        }

        @Override
        public String getActiveState() {
            return isInUse() ? "IN-USE" : "IDLE";
        }

        @Override
        public void setSpawnedDisplays(List<Display> displays) {
            this.spawnedDisplays = displays;
        }

        @Override
        public List<Display> getSpawnedDisplays() {
            return this.spawnedDisplays;
        }
    }

    public AbstractPump(Location loc) {
        super(loc);
    }

    public double getFuelCost() {
        return fuelCost;
    }

    public void setFuelCost(double fuelCost) {
        this.fuelCost = fuelCost;
    }

    public double getStoredMoney() {
        return storedMoney;
    }

    public void setStoredMoney(double storedMoney) {
        this.storedMoney = storedMoney;
    }

    public abstract double getFuelRatio();
    public abstract LiquidType getLiquidType();
    public abstract void setupNozzles();
    public abstract DisplayModel getNozzleDisplayModel(int index, PlacementContext context);
    protected abstract DeviceMenu createMenu();

    @Override
    public void openDeviceInventory(Player player) {
        if (player.isSneaking()) return;
        super.openDeviceInventory(player);
    }

    @Override
    public void updateUI() {
        if (getInventory() == null) recreateInv();
        if (menu == null) menu = createMenu();
        if (menu != null) menu.render();
    }

    @Override
    public void onUIClick(InventoryClickEvent event) {
        if (menu != null) menu.handleClick(event);
    }

    @Override
    public String getActiveState() {
        if (nozzles == null) return "IDLE";

        List<Integer> inUseIndices = new ArrayList<>();
        for (PumpNozzle nozzle : nozzles) {
            if (nozzle.isInUse()) {
                inUseIndices.add(nozzle.index);
            }
        }

        if (inUseIndices.isEmpty()) {
            return "IDLE";
        }

        Collections.sort(inUseIndices);
        StringBuilder state = new StringBuilder("IN-USE");
        for (int index : inUseIndices) {
            state.append("_").append(index);
        }
        return state.toString();
    }

    @Override
    public void setSpawnedDisplays(List<Display> displays) {
        this.activeDisplays = displays;
    }

    @Override
    public List<Display> getSpawnedDisplays() {
        if (activeDisplays == null) activeDisplays = new ArrayList<>();
        return activeDisplays;
    }

    @Override
    public void tickAnimations() {
        if (!visualsInitialized) initializeVisuals();
        drawCables();
    }

    @Override
    public void visualUpdate(boolean playerNearby) {
        super.visualUpdate(playerNearby);
        ComplexDisplayManager.sync(this, this, playerNearby);

        if (!visualsInitialized) initializeVisuals();

        if (nozzles != null) {
            for (PumpNozzle nozzle : nozzles) {
                ComplexDisplayManager.sync(nozzle, this, playerNearby);
            }
        }

        if (playerNearby) {
            updateTextDisplay();
        } else {
            if (nozzles != null) {
                for (PumpNozzle nozzle : nozzles) {
                    nozzle.removeDisplays();
                }
            }
        }
    }

    private void initializeVisuals() {
        if (getLocationREADONLY() != null) {
            if (nozzles == null) nozzles = new ArrayList<>();
            nozzles.clear();
            setupNozzles();
            int idx = 0;
            for (PumpNozzle nozzle : nozzles) {
                nozzle.index = idx;
                String id = getLocationREADONLY().getBlockX() + "_" + getLocationREADONLY().getBlockY() + "_" + getLocationREADONLY().getBlockZ() + "_" + idx;
                nozzle.mtd = new ManagedTextDisplay(id);
                nozzle.cable = new Cable(id, 8);
                nozzle.cable.clearGhosts(getLocationREADONLY());
                idx++;
            }
            visualsInitialized = true;
        }
    }

    private void drawCables() {
        if (nozzles == null) return;
        for (PumpNozzle nozzle : nozzles) {
            Location start = getLocationREADONLY().clone().add(0.55, 1.4, 0.5);
            Vector offset = RotationUtil.rotateVector(nozzle.cableLocalOffset, getFacing());
            start.add(offset);

            Location end = null;

            if (nozzle.holdingPlayer != null) {
                Player p = Bukkit.getPlayer(nozzle.holdingPlayer);
                if (p != null && p.isOnline() && p.getLocation().distanceSquared(getLocationREADONLY()) <= 100.0) {
                    end = p.getLocation().add(0, 1.0, 0);
                } else {
                    dropNozzle(p, true);
                }
            } else if (nozzle.connectedVehicle != null) {
                Vehicle vehicle = VehicleUtils.getVehicle(nozzle.connectedVehicle);
                if (vehicle != null) {
                    Location vLoc = VehicleUtils.getLocation(nozzle.connectedVehicle);
                    if (vLoc != null && vLoc.distanceSquared(getLocationREADONLY()) <= 144.0) {
                        end = vLoc.add(0, 0.5, 0);
                    } else {
                        notifyStop(nozzle.fuelingPlayer, ChatColor.YELLOW + "Vehicle moved too far. Fueling stopped.");
                        resetConnection(nozzle);
                    }
                } else {
                    resetConnection(nozzle);
                }
            }

            if (end != null) {
                nozzle.cable.update(start, end, 0.25);
            } else {
                nozzle.cable.remove();
            }
        }
    }

    private void updateTextDisplay() {
        if (nozzles == null) return;
        for (PumpNozzle nozzle : nozzles) {
            Location textLoc = getLocationREADONLY().clone().add(0.55, 1.35, 0.5);
            Vector offset = RotationUtil.rotateVector(nozzle.textLocalOffset, getFacing());
            textLoc.add(offset);

            textLoc.setYaw(RotationUtil.getYawFromFace(getFacing()));
            textLoc.setPitch(0f);

            Transformation transform = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(nozzle.textScale, nozzle.textScale, nozzle.textScale), new Quaternionf());

            double totalCost = nozzle.sessionVolume * fuelCost;

            String statusStr = ChatColor.GREEN + "READY";
            if (nozzle.connectedVehicle != null) statusStr = ChatColor.AQUA + "PUMPING";
            else if (nozzle.holdingPlayer != null) statusStr = ChatColor.YELLOW + "IN USE";
            else if (getStoredLiquid(getLiquidType()) <= 0) statusStr = ChatColor.RED + "EMPTY";

            String text = ChatColor.GRAY + "COST $\n" +
                ChatColor.WHITE + String.format("%05.2f", totalCost) + "\n\n" +
                ChatColor.GRAY + "LITERS\n" +
                ChatColor.WHITE + String.format("%05.2f", nozzle.sessionVolume) + "\n\n" +
                statusStr;

            nozzle.mtd.update(textLoc, text, transform, org.bukkit.Color.fromARGB(255, 10, 10, 10));
        }
    }

    @Override
    public boolean onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;

        Player player = event.getPlayer();

        if (player.isSneaking()) {
            if (System.currentTimeMillis() - lastInteract < 150) return true;
            lastInteract = System.currentTimeMillis();

            if (nozzles == null) return false;

            for (PumpNozzle nozzle : nozzles) {
                if (nozzle.holdingPlayer != null && nozzle.holdingPlayer.equals(player.getUniqueId())) {
                    dropNozzle(player, true);
                    return true;
                }
            }

            for (PumpNozzle nozzle : nozzles) {
                if (nozzle.connectedVehicle != null && nozzle.fuelingPlayer != null && nozzle.fuelingPlayer.equals(player.getUniqueId())) {
                    notifyStop(nozzle.fuelingPlayer, ChatColor.YELLOW + "Vehicle disconnected.");
                    resetConnection(nozzle);
                    return true;
                }
            }

            if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                MineUtil.sendMessage(player, ChatColor.RED + "You need an empty hand to grab a nozzle.");
                return true;
            }

            if (getStoredLiquid(getLiquidType()) <= 0) {
                MineUtil.sendMessage(player, ChatColor.RED + "The pump is out of fuel.");
                return true;
            }

            PumpNozzle availableNozzle = null;
            for (PumpNozzle nozzle : nozzles) {
                if (nozzle.holdingPlayer == null && nozzle.connectedVehicle == null) {
                    availableNozzle = nozzle;
                    break;
                }
            }

            if (availableNozzle == null) {
                MineUtil.sendMessage(player, ChatColor.RED + "All nozzles on this pump are currently in use.");
                return true;
            }

            availableNozzle.sessionVolume = 0.0;
            availableNozzle.holdingPlayer = player.getUniqueId();
            ItemStack nozzleItem = CustomItemRegistry.get(FuelNozzleItem.ID).create(getLocationREADONLY());
            player.getInventory().setItemInMainHand(nozzleItem);

            updateTextDisplay();
            ComplexDisplayManager.sync(this, this, true);

            MineUtil.sendMessage(player, ChatColor.GREEN + "You grabbed the fuel nozzle. Right-click a vehicle to connect.");
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    public void handleVehicleConnection(Player player, String licensePlate) {
        if (nozzles == null) return;
        for (PumpNozzle nozzle : nozzles) {
            if (nozzle.holdingPlayer != null && nozzle.holdingPlayer.equals(player.getUniqueId())) {
                Vehicle vehicle = VehicleUtils.getVehicle(licensePlate);
                if (vehicle == null || !vehicle.isFuelEnabled()) {
                    MineUtil.sendMessage(player, ChatColor.RED + "This vehicle does not accept fuel.");
                    return;
                }

                double currentFuel = vehicle.getCurrentFuel() != null ? vehicle.getCurrentFuel() : vehicle.getFuel();
                if (currentFuel >= 100.0) {
                    MineUtil.sendMessage(player, ChatColor.RED + "This vehicle's tank is already full.");
                    return;
                }

                nozzle.fuelingPlayer = nozzle.holdingPlayer;
                nozzle.connectedVehicle = licensePlate;
                dropNozzle(player, true);
                MineUtil.sendMessage(player, ChatColor.GREEN + "Vehicle connected to pump!");
                player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 1.5f);
                return;
            }
        }
    }

    public void handleVehicleConnection(Player player, Entity clickedEntity) {
        if (!VehicleUtils.isVehicle(clickedEntity)) {
            MineUtil.sendMessage(player, ChatColor.RED + "That is not a valid vehicle.");
            return;
        }

        String plate = VehicleUtils.getLicensePlate(clickedEntity);
        if (plate == null) return;

        handleVehicleConnection(player, plate);
    }

    public void dropNozzle(Player player, boolean clearItem) {
        if (nozzles == null) return;
        for (PumpNozzle nozzle : nozzles) {
            if (nozzle.holdingPlayer != null && nozzle.holdingPlayer.equals(player.getUniqueId())) {
                nozzle.holdingPlayer = null;
                if (player.isOnline() && clearItem) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (CustomItemRegistry.isCustomItem(hand, FuelNozzleItem.ID)) {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    }
                    MineUtil.sendMessage(player, ChatColor.YELLOW + "You dropped the fuel nozzle.");
                }
                if (nozzle.cable != null) nozzle.cable.remove();
                updateTextDisplay();
                ComplexDisplayManager.sync(this, this, true);
                break;
            }
        }
    }

    protected void notifyStop(UUID uuid, String message) {
        if (uuid != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                MineUtil.sendMessage(p, message);
            }
        }
    }

    protected void resetConnection(PumpNozzle nozzle) {
        nozzle.connectedVehicle = null;
        nozzle.fuelingPlayer = null;
        if (nozzle.cable != null) nozzle.cable.remove();
        updateTextDisplay();
        ComplexDisplayManager.sync(this, this, true);
    }

    @Override
    public void action() {
        if (nozzles == null) return;
        boolean powerConsumed = false;

        for (PumpNozzle nozzle : nozzles) {
            if (nozzle.connectedVehicle == null) continue;
            if (nozzle.fuelingPlayer == null) {
                resetConnection(nozzle);
                continue;
            }

            Vehicle vehicle = VehicleUtils.getVehicle(nozzle.connectedVehicle);
            if (vehicle == null) {
                resetConnection(nozzle);
                continue;
            }

            double currentFuel = vehicle.getCurrentFuel() != null ? vehicle.getCurrentFuel() : vehicle.getFuel();
            if (currentFuel >= 100.0) {
                setFailReason("Vehicle Full");
                notifyStop(nozzle.fuelingPlayer, ChatColor.GREEN + "Vehicle tank is full! Fueling stopped.");
                resetConnection(nozzle);
                getLocation().getWorld().playSound(getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1.0f, 1.0f);
                continue;
            }

            int pumpAmount = 1;
            if (!hasLiquid(getLiquidType(), pumpAmount)) {
                setFailReason("Out of Liquid");
                notifyStop(nozzle.fuelingPlayer, ChatColor.RED + "The pump is out of fuel! Fueling stopped.");
                resetConnection(nozzle);
                continue;
            }

            if (getGrid() == null || !getGrid().hasPower(getActionPower())) {
                setFailReason("Not Enough Power");
                notifyStop(nozzle.fuelingPlayer, ChatColor.RED + "The pump lost power! Fueling stopped.");
                resetConnection(nozzle);
                continue;
            }

            Economy econ = VaultHook.getEconomy();
            if (econ != null && fuelCost > 0) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(nozzle.fuelingPlayer);
                if (econ.getBalance(op) < fuelCost) {
                    setFailReason("Insufficient Funds");
                    notifyStop(nozzle.fuelingPlayer, ChatColor.RED + "You have run out of money! Fueling stopped.");
                    resetConnection(nozzle);
                    continue;
                }
                econ.withdrawPlayer(op, fuelCost);
                storedMoney += fuelCost;
            }

            if (!powerConsumed) {
                getGrid().consumePower(this, getActionPower());
                setConsumingPower(true);
                powerConsumed = true;
            }

            removeLiquid(getLiquidType(), pumpAmount);
            nozzle.sessionVolume += pumpAmount;

            double fuelToAdd = pumpAmount * getFuelRatio();
            double newFuel = Math.min(100.0, currentFuel + fuelToAdd);

            VehicleUtils.setFuel(nozzle.connectedVehicle, newFuel);
            setFailReason("None");

            if (newFuel >= 100.0) {
                notifyStop(nozzle.fuelingPlayer, ChatColor.GREEN + "Vehicle tank is full! Fueling stopped.");
                resetConnection(nozzle);
                getLocation().getWorld().playSound(getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1.0f, 1.0f);
            } else if (TUMaths.rollRange(0, 2) == 0) {
                getLocation().getWorld().playSound(getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.2f, 1.0f);
            }
        }
    }

    @Override
    public void cleanup() {
        if (nozzles != null) {
            for (PumpNozzle nozzle : nozzles) {
                if (nozzle.holdingPlayer != null) {
                    dropNozzle(Bukkit.getPlayer(nozzle.holdingPlayer), true);
                }
                nozzle.removeDisplays();
                ComplexDisplayManager.clear(nozzle);
            }
        }
        ComplexDisplayManager.clear(this);
    }
}