package com.github.darksoulq.auto_transport.multiblock;

import com.MT.xxxtrigger50xxx.Devices.Device;
import com.MT.xxxtrigger50xxx.Guide.ItemMenu;
import com.MT.xxxtrigger50xxx.MineItems;
import com.MT.xxxtrigger50xxx.MineMain;
import com.MT.xxxtrigger50xxx.MinetorioListener;
import com.MT.xxxtrigger50xxx.Recipes.MTRecipe;
import com.MT.xxxtrigger50xxx.Recipes.RecipeUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CustomDeviceRegistry {

    public static final Map<String, MultiblockDevice> multiblockDummies = new HashMap<>();

    public static Builder builder(String name, Class<? extends Device> deviceClass, Material material, Supplier<Device> instanceProvider) {
        return new Builder(name, deviceClass, material, instanceProvider);
    }

    public static class Builder {
        private final String name;
        private final Class<? extends Device> deviceClass;
        private final Material material;
        private final Supplier<Device> instanceProvider;

        private String model = "none";
        private String description = "";
        private ItemMenu.Category category = null;
        private List<ItemStack> recipeIngredients = null;
        private MTRecipe.RecipeType recipeType = null;

        private Builder(String name, Class<? extends Device> deviceClass, Material material, Supplier<Device> instanceProvider) {
            this.name = name;
            this.deviceClass = deviceClass;
            this.material = material;
            this.instanceProvider = instanceProvider;
        }

        public Builder withModel(String model) {
            this.model = model;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withItemMenu(ItemMenu.Category category) {
            this.category = category;
            return this;
        }

        public Builder withRecipe(List<ItemStack> ingredients, MTRecipe.RecipeType type) {
            this.recipeIngredients = ingredients;
            this.recipeType = type;
            return this;
        }

        public void register() {
            MineItems.itemNames.put(name, name);
            MineItems.itemDescs.put(name, description);
            MineItems.itemModelData.put(name, model);
            MineItems.itemMaterial.put(name, material);
            MineItems.devValueForItems.put(name, "none");
            MineItems.pdcKey.put(name, new NamespacedKey(MineMain.getPlugin(), MineItems.getPDCString(name)));

            MinetorioListener.deviceClasses.put(name, deviceClass);

            Device dummyInstance = instanceProvider.get();
            ItemStack deviceStack = dummyInstance.getDeviceStack();

            if (dummyInstance instanceof MultiblockDevice) {
                multiblockDummies.put(name, (MultiblockDevice) dummyInstance);
            }

            if (category != null) {
                ItemMenu.registerDeviceStack(name, category, deviceStack, false);
            }

            if (recipeIngredients != null && !recipeIngredients.isEmpty() && recipeType != null) {
                RecipeUtils.addDefaultRecipe(deviceStack, new ArrayList<>(recipeIngredients), recipeType, true);
            }
        }
    }
}