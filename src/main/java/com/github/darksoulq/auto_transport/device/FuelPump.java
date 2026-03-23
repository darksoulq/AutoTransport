package com.github.darksoulq.auto_transport.device;

import com.github.darksoulq.auto_transport.device.display.Cable;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplay;
import com.github.darksoulq.auto_transport.device.display.ComplexDisplayManager;
import com.github.darksoulq.auto_transport.device.display.DisplayCullingManager;
import com.github.darksoulq.auto_transport.device.display.DisplayModel;
import com.github.darksoulq.auto_transport.device.display.ModelState;
import com.github.darksoulq.auto_transport.device.display.VisualPart;
import com.github.darksoulq.auto_transport.device.items.FuelNozzleItem;
import com.github.darksoulq.auto_transport.device.traits.InteractiveDevice;
import com.github.darksoulq.auto_transport.item.CustomItemRegistry;
import com.github.darksoulq.auto_transport.multiblock.CustomDeviceRegistry;
import com.github.darksoulq.auto_transport.multiblock.MultiblockDevice;
import com.github.darksoulq.auto_transport.multiblock.RotationUtil;
import com.MT.xxxtrigger50xxx.Devices.Device.LiquidType;
import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import com.MT.xxxtrigger50xxx.MineItems;
import com.MT.xxxtrigger50xxx.Recipes.MTRecipe.RecipeType;
import com.MT.xxxtrigger50xxx.MineUtil;
import com.MT.triggersUtility.TUMaths;
import com.MT.triggersUtility.TUItems;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FuelPump extends MultiblockDevice implements ComplexDisplay, InteractiveDevice {

    private static final long serialVersionUID = 1L;
    public static final String CLASS_NAME = "Fuel Pump";

    private static float FUEL_RATIO = 1;
    private String connectedVehicle = null;
    private UUID holdingPlayer = null;

    private double fuelCost = 1.02;
    private double sessionVolume = 0.0;

    private transient List<Display> activeDisplays = new ArrayList<>();
    private transient TextDisplay infoDisplay = null;
    private transient Cable cable = new Cable(8);
    private transient long lastInteract = 0;

    private static final DisplayModel MODEL;

    static {
        VisualPart base = VisualPart.block(Material.RED_CONCRETE.createBlockData())
            .offset(0.0, 1.0, 0.0)
            .rotation(0, 0, 0)
            .scale(1.0, 1.0, 1.0);

        VisualPart head = VisualPart.block(Material.IRON_BLOCK.createBlockData())
            .offset(0.0, 2.0, 0.0)
            .rotation(0, 0, 0)
            .scale(0.8, 0.5, 0.8);

        MODEL = DisplayModel.create()
            .withState(ModelState.create("IDLE")
                .add("base", base)
                .add("head", head))
            .withState(ModelState.create("PUMPING")
                .add("base", base)
                .add("head", head));
    }

    public FuelPump(Location loc) {
        super(loc);
        this.setMaterial("RED_CONCRETE");
        this.deviceName = CLASS_NAME;

        DeviceSettings.configure(this)
            .power(0, 5, 0)
            .processing(5, 0, 0)
            .capacity(LiquidType.PETROL, 1000)
            .uiCustom(new int[0], new int[0], new int[0], true)
            .apply();
    }

    @Override
    public Map<Vector, BlockData> getStructureMap() {
        Map<Vector, BlockData> structure = new HashMap<>();
        structure.put(new Vector(0, 1, 0), Material.RED_CONCRETE.createBlockData());
        structure.put(new Vector(0, 2, 0), Material.STONE_PRESSURE_PLATE.createBlockData());
        return structure;
    }

    @Override
    public DisplayModel getDisplayModel() {
        return MODEL;
    }

    @Override
    public String getActiveState() {
        return connectedVehicle != null && isConsumingPower() ? "PUMPING" : "IDLE";
    }

    @Override
    public void setSpawnedDisplays(List<Display> displays) {
        this.activeDisplays = displays;
    }

    @Override
    public List<Display> getSpawnedDisplays() {
        return activeDisplays;
    }

    @Override
    public void tickAnimations() {
        if (cable == null) cable = new Cable(8);
        drawCables();
    }

    @Override
    public void visualUpdate(boolean playerNearby) {
        super.visualUpdate(playerNearby);
        ComplexDisplayManager.sync(this, this, playerNearby);

        if (playerNearby) {
            updateTextDisplay();
        } else {
            removeTextDisplay();
            if (cable != null) cable.remove();
        }
    }

    private void drawCables() {
        Location start = getLocationREADONLY().clone().add(0.5, 1.4, 0.5);
        Vector offset = RotationUtil.rotateVector(new Vector(0.35, 0, 0.45), getFacing());
        start.add(offset);

        Location end = null;

        if (holdingPlayer != null) {
            Player p = Bukkit.getPlayer(holdingPlayer);
            if (p != null && p.isOnline() && p.getLocation().distanceSquared(getLocationREADONLY()) <= 100.0) {
                end = p.getLocation().add(0, 1.0, 0);
            } else {
                dropNozzle(p, true);
            }
        } else if (connectedVehicle != null) {
            Vehicle vehicle = VehicleUtils.getVehicle(connectedVehicle);
            if (vehicle != null) {
                Location vLoc = VehicleUtils.getLocation(connectedVehicle);
                if (vLoc != null && vLoc.distanceSquared(getLocationREADONLY()) <= 144.0) {
                    end = vLoc.add(0, 0.5, 0);
                } else {
                    connectedVehicle = null;
                }
            } else {
                connectedVehicle = null;
            }
        }

        if (end != null) {
            cable.update(start, end, 0.25);
        } else {
            cable.remove();
        }
    }

    private void updateTextDisplay() {
        if (infoDisplay == null || !infoDisplay.isValid()) {
            Location textLoc = getLocationREADONLY().clone().add(0.5, 1.55, 0.5);
            Vector offset = RotationUtil.rotateVector(new Vector(0, 0, 0.41), getFacing());
            textLoc.add(offset);

            textLoc.setYaw(RotationUtil.getYawFromFace(getFacing()));
            textLoc.setPitch(0f);

            infoDisplay = textLoc.getWorld().spawn(textLoc, TextDisplay.class);
            infoDisplay.setBillboard(Display.Billboard.FIXED);
            infoDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(255, 15, 15, 15));
            infoDisplay.setCustomNameVisible(false);
            DisplayCullingManager.track(infoDisplay);

            Transformation transform = infoDisplay.getTransformation();
            transform.getScale().set(0.18f, 0.18f, 0.18f);
            infoDisplay.setTransformation(transform);
        }

        double totalCost = sessionVolume * fuelCost;

        String text = ChatColor.WHITE + "TOTAL SALE\n" +
            ChatColor.GREEN + "$" + String.format("%.2f", totalCost) + "\n\n" +
            ChatColor.WHITE + "VOLUME (L)\n" +
            ChatColor.YELLOW + String.format("%.2f", sessionVolume) + "\n\n" +
            ChatColor.WHITE + "PRICE/L\n" +
            ChatColor.GRAY + "$" + String.format("%.2f", fuelCost);

        infoDisplay.setText(text);
    }

    @Override
    public void removeTextDisplay() {
        if (infoDisplay != null && infoDisplay.isValid()) {
            DisplayCullingManager.untrack(infoDisplay);
            infoDisplay.remove();
            infoDisplay = null;
        }
        super.removeTextDisplay();
    }

    @Override
    public boolean onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;

        Player player = event.getPlayer();

        if (player.isSneaking()) {
            if (System.currentTimeMillis() - lastInteract < 150) return true;
            lastInteract = System.currentTimeMillis();

            if (holdingPlayer != null && holdingPlayer.equals(player.getUniqueId())) {
                dropNozzle(player, true);
                return true;
            }

            if (connectedVehicle != null) {
                connectedVehicle = null;
                if (cable != null) cable.remove();
                MineUtil.sendMessage(player, ChatColor.YELLOW + "Vehicle disconnected.");
                return true;
            }

            if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                MineUtil.sendMessage(player, ChatColor.RED + "You need an empty hand to grab the nozzle.");
                return true;
            }

            sessionVolume = 0.0;
            holdingPlayer = player.getUniqueId();
            ItemStack nozzle = CustomItemRegistry.get(FuelNozzleItem.ID).create(getLocationREADONLY());
            player.getInventory().setItemInMainHand(nozzle);

            MineUtil.sendMessage(player, ChatColor.GREEN + "You grabbed the fuel nozzle. Right-click a vehicle to connect.");
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    public void handleVehicleConnection(Player player, String licensePlate) {
        if (holdingPlayer == null || !holdingPlayer.equals(player.getUniqueId())) return;

        Vehicle vehicle = VehicleUtils.getVehicle(licensePlate);
        if (vehicle == null || !vehicle.isFuelEnabled()) {
            MineUtil.sendMessage(player, ChatColor.RED + "This vehicle does not accept fuel.");
            return;
        }

        connectedVehicle = licensePlate;
        dropNozzle(player, true);
        MineUtil.sendMessage(player, ChatColor.GREEN + "Vehicle connected to pump!");
        player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 1.5f);
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

    public UUID getHoldingPlayer() {
        return holdingPlayer;
    }

    public void dropNozzle(Player player, boolean clearItem) {
        holdingPlayer = null;
        if (player != null && player.isOnline() && clearItem) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (CustomItemRegistry.isCustomItem(hand, FuelNozzleItem.ID)) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            MineUtil.sendMessage(player, ChatColor.YELLOW + "You dropped the fuel nozzle.");
        }
        if (cable != null) cable.remove();
    }

    @Override
    public void action() {
        if (connectedVehicle == null) return;

        Vehicle vehicle = VehicleUtils.getVehicle(connectedVehicle);
        if (vehicle == null) {
            connectedVehicle = null;
            if (cable != null) cable.remove();
            return;
        }

        double currentFuel = vehicle.getCurrentFuel() != null ? vehicle.getCurrentFuel() : vehicle.getFuel();
        if (currentFuel >= 100.0) {
            setFailReason("Vehicle Full");
            return;
        }

        int pumpAmount = 1;
        if (!hasLiquid(LiquidType.PETROL, pumpAmount)) {
            setFailReason("Out of Petrol");
            return;
        }

        if (getGrid() == null || !getGrid().hasPower(getActionPower())) {
            setFailReason("Not Enough Power");
            return;
        }

        getGrid().consumePower(this, getActionPower());
        setConsumingPower(true);

        removeLiquid(LiquidType.PETROL, pumpAmount);
        sessionVolume += pumpAmount;

        double newFuel = Math.min(100.0, currentFuel + pumpAmount);
        VehicleUtils.setFuel(connectedVehicle, newFuel);
        setFailReason("None");

        if (newFuel >= 100.0) {
            connectedVehicle = null;
            if (cable != null) cable.remove();
            getLocation().getWorld().playSound(getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1.0f, 1.0f);
        } else if (TUMaths.rollRange(0, 2) == 0) {
            getLocation().getWorld().playSound(getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.2f, 1.0f);
        }
    }

    @Override
    public void cleanup() {
        if (holdingPlayer != null) {
            dropNozzle(Bukkit.getPlayer(holdingPlayer), true);
        }
        if (cable != null) cable.remove();
        removeTextDisplay();
        ComplexDisplayManager.clear(this);
    }

    @Override
    public ArrayList<String> stackDescription() {
        ArrayList<String> desc = new ArrayList<>();
        desc.add("- A 3-block tall fuel pump.");
        desc.add("- Sneak-Right-Click to grab nozzle.");
        desc.add("- Right-Click an MTVehicle to connect.");
        return desc;
    }

    @Override
    public void updateUI() {
        if (getInventory() == null) recreateInv();

        ItemStack bg = TUItems.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < getInventory().getSize(); i++) {
            getInventory().setItem(i, bg);
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Stored Petrol: " + getStoredLiquid(LiquidType.PETROL) + " / " + getMaxLiquid(LiquidType.PETROL) + " L");
        lore.add(ChatColor.WHITE + "Price Per Liter: $" + fuelCost);

        ItemStack info = TUItems.createItem(Material.LAVA_BUCKET, ChatColor.GOLD + "Pump Status", lore);
        getInventory().setItem(13, info);
    }

    public static void addDevice() {
        List<ItemStack> recipe = new ArrayList<>();
        recipe.add(MineItems.getIndustrialItem("Iron Plate", 10));
        recipe.add(MineItems.getIndustrialItem("Liquid Pipe", 5));
        recipe.add(MineItems.getIndustrialItem("Electronic Circuit", 2));

        CustomDeviceRegistry.builder(CLASS_NAME, FuelPump.class, Material.RED_CONCRETE, () -> new FuelPump(null))
            .withDescription("A 3-block tall fuel pump. Sneak-Right-Click to interact.")
            .withItemMenu(ItemMenu.Category.LOGISTICS)
            .withRecipe(recipe, RecipeType.ADVANCED)
            .register();
    }
}