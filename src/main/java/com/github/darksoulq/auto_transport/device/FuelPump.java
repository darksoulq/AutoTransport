package com.github.darksoulq.auto_transport.device;

import com.github.darksoulq.auto_transport.device.display.DisplayModel;
import com.github.darksoulq.auto_transport.device.display.ModelState;
import com.github.darksoulq.auto_transport.device.display.VisualPart;
import com.github.darksoulq.auto_transport.device.ui.DeviceMenu;
import com.github.darksoulq.auto_transport.device.ui.FuelPumpMenu;
import com.github.darksoulq.auto_transport.multiblock.CustomDeviceRegistry;
import com.github.darksoulq.auto_transport.multiblock.PlacementContext;
import com.MT.xxxtrigger50xxx.Devices.Device.LiquidType;
import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import com.MT.xxxtrigger50xxx.MineItems;
import com.MT.xxxtrigger50xxx.Recipes.MTRecipe.RecipeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FuelPump extends AbstractPump {

    private static final long serialVersionUID = 1L;
    public static final String CLASS_NAME = "Fuel Pump";
    private static final double FUEL_RATIO = 1.0;

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
                .withState(ModelState.create("PUMPING_0")
                        .add("base", base)
                        .add("head", head));
    }

    public FuelPump(Location loc) {
        super(loc);
        this.deviceName = CLASS_NAME;
        this.setMaterial("RED_CONCRETE");

        DeviceSettings.configure(this)
                .power(0, 5, 0)
                .processing(5, 0, 0)
                .capacity(LiquidType.PETROL, 1000)
                .uiCustom(new int[0], new int[0], new int[0], true)
                .apply();
    }

    @Override
    public double getFuelRatio() {
        return FUEL_RATIO;
    }

    @Override
    public LiquidType getLiquidType() {
        return LiquidType.PETROL;
    }

    @Override
    public void setupNozzles() {
        nozzles.add(new PumpNozzle(new Vector(0.35, 0, 0.45), new Vector(0, 0, 0.62), 0.15f));
    }

    @Override
    public DisplayModel getNozzleDisplayModel(int index, PlacementContext context) {
        return null;
    }

    @Override
    public Map<Vector, BlockData> getStructureMap(PlacementContext context) {
        Map<Vector, BlockData> structure = new HashMap<>();
        structure.put(new Vector(0, 1, 0), Material.RED_CONCRETE.createBlockData());
        structure.put(new Vector(0, 2, 0), Material.STONE_PRESSURE_PLATE.createBlockData());
        return structure;
    }

    @Override
    public DisplayModel getDisplayModel(PlacementContext context) {
        return MODEL;
    }

    @Override
    protected DeviceMenu createMenu() {
        return new FuelPumpMenu(this);
    }

    @Override
    public ArrayList<String> stackDescription() {
        ArrayList<String> desc = new ArrayList<>();
        desc.add("- A 3-block tall fuel pump.");
        desc.add("- Sneak-Right-Click to grab nozzle.");
        desc.add("- Right-Click an MTVehicle to connect.");
        return desc;
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